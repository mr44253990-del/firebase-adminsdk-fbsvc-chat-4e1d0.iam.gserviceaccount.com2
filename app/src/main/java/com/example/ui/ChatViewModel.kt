package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.*
import com.example.call.CallEngine
import com.example.security.PrivacyPreferences
import com.example.security.TextBackupManager
import com.example.security.AccountCredentialVault
import com.example.service.scheduleMessage
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class InAppNotificationData(
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

data class GatewayHealth(
    val checking: Boolean = false,
    val configured: Boolean = false,
    val message: String = "Not checked",
    val projectId: String = "",
    val version: String = "",
    val r2Configured: Boolean = false,
    val turnConfigured: Boolean = false,
    val sfuConfigured: Boolean = false
)

data class R2MediaResult(
    val publicUrl: String,
    val key: String,
    val expiresAt: Long,
    val kind: String
)

data class ActiveUploadState(
    val fileName: String = "",
    val percent: Int = 0,
    val etaSeconds: Long = 0L,
    val active: Boolean = false
)

data class AdminReelImportState(
    val importing: Boolean = false,
    val processed: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

private class ProgressBytesRequestBody(
    private val bytes: ByteArray,
    private val type: MediaType?,
    private val progress: (Int, Long) -> Unit
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        val started = System.currentTimeMillis()
        var offset = 0
        val chunk = 64 * 1024
        while (offset < bytes.size) {
            val count = minOf(chunk, bytes.size - offset)
            sink.write(bytes, offset, count); offset += count
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            val rate = offset * 1000.0 / elapsed
            val remaining = if (rate > 0) ((bytes.size - offset) / rate).toLong() else 0L
            progress((offset * 100L / bytes.size).toInt(), remaining)
        }
    }
}

private class ProgressUriRequestBody(
    private val context: Context, private val uri: Uri, private val length: Long,
    private val type: MediaType?, private val progress: (Int, Long) -> Unit
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = length.takeIf { it >= 0 } ?: -1
    override fun writeTo(sink: BufferedSink) {
        val started = System.currentTimeMillis(); var written = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                sink.write(buffer, 0, count); written += count
                if (length > 0) {
                    val rate = written * 1000.0 / (System.currentTimeMillis() - started).coerceAtLeast(1)
                    progress((written * 100 / length).toInt(), if (rate > 0) ((length - written) / rate).toLong() else 0)
                }
            }
        } ?: error("Could not open selected file")
    }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)
    private val localUploadFiles = ConcurrentHashMap<String, String>()

    private fun outgoingExpiryAt(sentAt: Long = System.currentTimeMillis()): Long {
        val seconds = PrivacyPreferences.settings.value.disappearingMessageSeconds
        return if (seconds > 0) sentAt + seconds * 1000L else 0L
    }

    private fun isVisibleMessage(expiresAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        expiresAt <= 0L || expiresAt > now

    private fun restoreTextBackup(uid: String) {
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = TextBackupManager.restoreLatest(getApplication(), uid)
            if (result.restored) {
                Log.i("TEXT_BACKUP", "Restored ${result.itemCount} cached text records")
            } else if (result.reason != "no backup found") {
                Log.w("TEXT_BACKUP", "Restore skipped: ${result.reason}")
            }
        }
    }

    fun exportTextBackup(onComplete: (TextBackupManager.Result) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = TextBackupManager.export(getApplication(), uid)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    // Offline Cache DB & Dao
    private val appDb = AppDatabase.getDatabase(application)
    private val cacheDao = appDb.cacheDao()

    // Themes
    private val _themeState = MutableStateFlow(sharedPrefs.getString("app_theme", "default") ?: "default")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    private val _notificationSoundsEnabled = MutableStateFlow(sharedPrefs.getBoolean("notification_sounds", true))
    val notificationSoundsEnabled: StateFlow<Boolean> = _notificationSoundsEnabled.asStateFlow()
    private val _typingSoundsEnabled = MutableStateFlow(sharedPrefs.getBoolean("typing_sounds", true))
    val typingSoundsEnabled: StateFlow<Boolean> = _typingSoundsEnabled.asStateFlow()
    private val _mutedUserIds = MutableStateFlow(sharedPrefs.getStringSet("muted_users", emptySet())?.toSet() ?: emptySet())
    val mutedUserIds: StateFlow<Set<String>> = _mutedUserIds.asStateFlow()
    private val _savedPostIds = MutableStateFlow(sharedPrefs.getStringSet("saved_posts", emptySet())?.toSet() ?: emptySet())
    val savedPostIds: StateFlow<Set<String>> = _savedPostIds.asStateFlow()

    fun toggleSavedPost(postId: String) {
        val updated = _savedPostIds.value.toMutableSet().apply { if (!add(postId)) remove(postId) }.toSet()
        _savedPostIds.value = updated
        sharedPrefs.edit().putStringSet("saved_posts", updated).apply()
    }

    // Network tracking (defaults to true for maximum compatibility on emulators)
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Stories state
    private val _storiesState = MutableStateFlow<List<Story>>(emptyList())
    val storiesState: StateFlow<List<Story>> = _storiesState.asStateFlow()

    // Social Feed Posts state
    private val _postsState = MutableStateFlow<List<Post>>(emptyList())
    val postsState: StateFlow<List<Post>> = _postsState.asStateFlow()

    // Groups state
    private val _groupsState = MutableStateFlow<List<Group>>(emptyList())
    val groupsState: StateFlow<List<Group>> = _groupsState.asStateFlow()

    // Active Group state
    private val _activeGroup = MutableStateFlow<Group?>(null)
    val activeGroup: StateFlow<Group?> = _activeGroup.asStateFlow()

    // Group Messages state
    private val _groupMessagesState = MutableStateFlow<List<GroupMessage>>(emptyList())
    val groupMessagesState: StateFlow<List<GroupMessage>> = _groupMessagesState.asStateFlow()
    private val _groupVoiceRecorders = MutableStateFlow<Set<String>>(emptySet())
    val groupVoiceRecorders: StateFlow<Set<String>> = _groupVoiceRecorders.asStateFlow()

    // Admin state
    private val _userIsAdmin = MutableStateFlow(false)
    val userIsAdmin: StateFlow<Boolean> = _userIsAdmin.asStateFlow()

    // Observables for UI
    private val _currentUserState = MutableStateFlow<User?>(null)
    val currentUserState: StateFlow<User?> = _currentUserState.asStateFlow()

    private val _activeRecipientUser = MutableStateFlow<User?>(null)
    val activeRecipientUser: StateFlow<User?> = _activeRecipientUser.asStateFlow()

    private val _usersState = MutableStateFlow<List<User>>(emptyList())
    val usersState: StateFlow<List<User>> = _usersState.asStateFlow()

    private val _filteredUsersState = MutableStateFlow<List<User>>(emptyList())
    val filteredUsersState: StateFlow<List<User>> = _filteredUsersState.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<List<Message>>(emptyList())
    val chatMessagesState: StateFlow<List<Message>> = _chatMessagesState.asStateFlow()

    /** Local-only delivery state for optimistic sends; server message schema remains unchanged. */
    private val _messageDeliveryStates = MutableStateFlow<Map<String, String>>(emptyMap())
    val messageDeliveryStates: StateFlow<Map<String, String>> = _messageDeliveryStates.asStateFlow()

    private val _unreadCountsState = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsState: StateFlow<Map<String, Int>> = _unreadCountsState.asStateFlow()

    private val _conversationUserIds = MutableStateFlow<Set<String>>(emptySet())
    val conversationUserIds: StateFlow<Set<String>> = _conversationUserIds.asStateFlow()

    private val _isRecipientTyping = MutableStateFlow(false)
    val isRecipientTyping: StateFlow<Boolean> = _isRecipientTyping.asStateFlow()
    private val _isRecipientVoiceRecording = MutableStateFlow(false)
    val isRecipientVoiceRecording: StateFlow<Boolean> = _isRecipientVoiceRecording.asStateFlow()
    private val _recipientUploadState = MutableStateFlow<ActiveUploadState?>(null)
    val recipientUploadState: StateFlow<ActiveUploadState?> = _recipientUploadState.asStateFlow()
    private val _chatTheme = MutableStateFlow("Aurora")
    val chatTheme: StateFlow<String> = _chatTheme.asStateFlow()

    private val _inAppNotification = MutableStateFlow<InAppNotificationData?>(null)
    val inAppNotification: StateFlow<InAppNotificationData?> = _inAppNotification.asStateFlow()

    private val _activityNotifications = MutableStateFlow<List<ActivityNotification>>(emptyList())
    val activityNotifications: StateFlow<List<ActivityNotification>> = _activityNotifications.asStateFlow()
    private val _openActivityCenterSignal = MutableStateFlow(0L)
    val openActivityCenterSignal: StateFlow<Long> = _openActivityCenterSignal.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests.asStateFlow()
    private val _sentFriendRequestIds = MutableStateFlow<Set<String>>(emptySet())
    val sentFriendRequestIds: StateFlow<Set<String>> = _sentFriendRequestIds.asStateFlow()

    private val _messageRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val messageRequests: StateFlow<List<MessageRequest>> = _messageRequests.asStateFlow()
    private val _pendingMessageRequestRecipients = MutableStateFlow<Set<String>>(emptySet())
    val pendingMessageRequestRecipients: StateFlow<Set<String>> = _pendingMessageRequestRecipients.asStateFlow()

    private val _selectedProfile = MutableStateFlow<User?>(null)
    val selectedProfile: StateFlow<User?> = _selectedProfile.asStateFlow()

    private val _flagshipConfig = MutableStateFlow(FlagshipConfig())
    val flagshipConfig: StateFlow<FlagshipConfig> = _flagshipConfig.asStateFlow()
    private val _featureRequests = MutableStateFlow<List<FeatureRequest>>(emptyList())
    val featureRequests: StateFlow<List<FeatureRequest>> = _featureRequests.asStateFlow()
    private val _premiumRequests = MutableStateFlow<List<PremiumRequest>>(emptyList())
    val premiumRequests: StateFlow<List<PremiumRequest>> = _premiumRequests.asStateFlow()
    private val _deviceSessions = MutableStateFlow<List<DeviceSession>>(emptyList())
    val deviceSessions: StateFlow<List<DeviceSession>> = _deviceSessions.asStateFlow()
    private val _adminReports = MutableStateFlow<List<UserReport>>(emptyList())
    val adminReports: StateFlow<List<UserReport>> = _adminReports.asStateFlow()
    private val _accountBanned = MutableStateFlow(false)
    val accountBanned: StateFlow<Boolean> = _accountBanned.asStateFlow()
    private val _rememberedAccounts = MutableStateFlow(loadRememberedAccounts())
    val rememberedAccounts: StateFlow<List<RememberedAccount>> = _rememberedAccounts.asStateFlow()
    private val _adminReelImportState = MutableStateFlow(AdminReelImportState())
    val adminReelImportState: StateFlow<AdminReelImportState> = _adminReelImportState.asStateFlow()

    private fun loadRememberedAccounts(): List<RememberedAccount> = runCatching {
        val array = JSONArray(sharedPrefs.getString("remembered_accounts", "[]"))
        (0 until array.length())
            .map { array.getJSONObject(it) }
            .map {
                RememberedAccount(
                    it.optString("uid"), it.optString("name"), it.optString("email"),
                    it.optString("photoUrl"), it.optString("provider", "password"),
                    it.optLong("lastUsedAt"), it.optInt("unread")
                )
            }
            .sortedByDescending { it.lastUsedAt }
    }.getOrDefault(emptyList())
    private fun rememberAccount(user:User){
        val auth=FirebaseAuth.getInstance().currentUser?:return;val provider=if(auth.providerData.any{it.providerId=="google.com"})"google" else "password"
        val item=RememberedAccount(user.uid,user.name,auth.email.orEmpty(),user.profileImageUrl,provider,System.currentTimeMillis(),0)
        val updated=(listOf(item)+_rememberedAccounts.value.filterNot{it.uid==item.uid}).take(8);_rememberedAccounts.value=updated
        val a=JSONArray();updated.forEach{a.put(JSONObject().put("uid",it.uid).put("name",it.name).put("email",it.email).put("photoUrl",it.photoUrl).put("provider",it.provider).put("lastUsedAt",it.lastUsedAt).put("unread",it.unread))};sharedPrefs.edit().putString("remembered_accounts",a.toString()).apply()
    }
    fun forgetRememberedAccount(uid: String) {
        val removed = _rememberedAccounts.value.firstOrNull { it.uid == uid }
        removed?.email?.takeIf { it.isNotBlank() }?.let { AccountCredentialVault.remove(getApplication(), it) }
        _rememberedAccounts.value = _rememberedAccounts.value.filterNot { it.uid == uid }
        val a = JSONArray()
        _rememberedAccounts.value.forEach {
            a.put(JSONObject().put("uid", it.uid).put("name", it.name).put("email", it.email)
                .put("photoUrl", it.photoUrl).put("provider", it.provider)
                .put("lastUsedAt", it.lastUsedAt).put("unread", it.unread))
        }
        sharedPrefs.edit().putString("remembered_accounts", a.toString()).apply()
    }

    fun loginRememberedAccount(account: RememberedAccount, onSuccess: () -> Unit) {
        if (account.provider.equals("google", ignoreCase = true)) {
            _authError.value = "Select this Google account to continue"
            return
        }
        val password = AccountCredentialVault.load(getApplication(), account.email)
        if (password.isNullOrBlank()) {
            _authError.value = "For security, enter this account password once"
            return
        }
        login(account.email, password, onSuccess)
    }

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(true)
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    private val _currentTabState = MutableStateFlow(0)
    val currentTabState: StateFlow<Int> = _currentTabState.asStateFlow()

    private val _selectedReelPostId = MutableStateFlow<String?>(null)
    val selectedReelPostId: StateFlow<String?> = _selectedReelPostId.asStateFlow()
    private var pendingComposerMode: String = "post"
    private var pendingExternalShare: IncomingSharePayload? = null

    fun prepareComposer(mode: String) { pendingComposerMode = mode }
    fun consumeComposerMode(): String = pendingComposerMode.also { pendingComposerMode = "post" }
    fun prepareExternalShare(payload: IncomingSharePayload) { pendingExternalShare = payload }
    fun consumeExternalShare(): IncomingSharePayload? = pendingExternalShare.also { pendingExternalShare = null }

    fun setCurrentTab(tab: Int) { _currentTabState.value = tab }

    fun openReel(postId: String? = null) {
        _selectedReelPostId.value = postId
        _currentTabState.value = 6
    }

    private val defaultWebhookUrl = "https://solitary-hill-dcdc.mr44253990.workers.dev/"

    private val storedGatewayUrl = sharedPrefs.getString("webhook_url", null).orEmpty()
    private val _webhookUrl = MutableStateFlow(
        storedGatewayUrl.takeIf { it.startsWith("https://") && !it.contains("n8n", ignoreCase = true) }
            ?: defaultWebhookUrl
    )
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private val _gatewayHealth = MutableStateFlow(GatewayHealth())
    val gatewayHealth: StateFlow<GatewayHealth> = _gatewayHealth.asStateFlow()

    private var activeChatListener: ValueEventListener? = null
    private var activeChatId: String? = null
    private var activeTypingListener: ValueEventListener? = null
    private var activeTypingChatId: String? = null
    private var activeTypingRecipientUid: String? = null
    private var activeVoiceRecordingListener: ValueEventListener? = null
    private var activeVoiceRecordingChatId: String? = null
    private var activeVoiceRecordingRecipientUid: String? = null
    private var activeUploadListener: ValueEventListener? = null
    private var activeUploadChatId: String? = null
    private var activeUploadRecipientUid: String? = null
    private var activeGroupVoiceListener: ValueEventListener? = null
    private var activeGroupVoiceId: String? = null
    private var activeChatThemeListener: ValueEventListener? = null
    private var activeReceiptListener: ValueEventListener? = null
    private var globalNotificationListener: ValueEventListener? = null
    // A single host/re-entry must not fan out duplicate notifications for the same room.
    private val dispatchedGroupCallRooms = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var presenceListener: ValueEventListener? = null
    private var activityNotificationListener: ListenerRegistration? = null
    private var currentUserProfileListener: ListenerRegistration? = null
    private var flagshipListener: ListenerRegistration? = null
    private var flagshipRtdbListener: ValueEventListener? = null
    private var flagshipRtdbRef: com.google.firebase.database.DatabaseReference? = null
    private var featureRequestListener: ListenerRegistration? = null
    private var premiumRequestListener: ListenerRegistration? = null
    private var friendRequestListener: ListenerRegistration? = null
    private var sentFriendRequestListener: ListenerRegistration? = null
    private var messageRequestListener: ListenerRegistration? = null
    private var sentMessageRequestListener: ListenerRegistration? = null
    private var notificationCacheJob: kotlinx.coroutines.Job? = null
    private var conversationIdsJob: kotlinx.coroutines.Job? = null

    private fun getDatabaseInstance(): FirebaseDatabase {
        return try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            try {
                FirebaseDatabase.getInstance("https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app")
            } catch (ex: Exception) {
                Log.e("DATABASE", "Failed to get default database or URL database: ${ex.message}")
                throw ex
            }
        }
    }

    init {
        checkFirebaseConfiguration()
        listenFlagshipControl()
        registerNetworkCallback(application)
    }

    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        setNetworkStatus(true)
                    }
                    override fun onLost(network: Network) {
                        setNetworkStatus(false)
                    }
                }
                cm.registerNetworkCallback(request, callback)
                networkCallback = callback
            } catch (e: Exception) {
                Log.e("NETWORK_CALLBACK", "Error registering network callback: ${e.message}")
            }
        }
    }

    fun selectRecipient(user: User?) {
        _activeRecipientUser.value = user
        if (user != null) {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid != null) {
                getDatabaseInstance().getReference("unread_counts")
                    .child(currentUid).child(user.uid).setValue(0)
            }
            startListeningToChat(user.uid)
            startListeningToChatTheme()
        } else {
            stopListeningToChat()
            stopListeningToTyping()
        }
    }

    fun updateWebhookUrl(url: String) {
        _webhookUrl.value = url
        sharedPrefs.edit().putString("webhook_url", url).apply()
        
        try {
            getDatabaseInstance().getReference("config")
                .child("app_settings")
                .setValue(mapOf("webhookUrl" to url, "workerUrl" to url))
                .addOnSuccessListener {
                    Log.d("RTDB_CONFIG", "Successfully saved webhook URL to RTDB: $url")
                }
                .addOnFailureListener { e ->
                    Log.e("RTDB_CONFIG", "Failed to save webhook URL to RTDB: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("RTDB_CONFIG", "Error updating global config in RTDB: ${e.message}")
        }
        testFcmGateway(url)
    }

    fun testFcmGateway(url: String = _webhookUrl.value) {
        if (!url.startsWith("https://")) {
            _gatewayHealth.value = GatewayHealth(message = "A valid HTTPS Worker URL is required")
            return
        }
        _gatewayHealth.value = GatewayHealth(checking = true, message = "Checking gateway…")
        val request = Request.Builder().url(url).get().build()
        OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
            .newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _gatewayHealth.value = GatewayHealth(message = "Gateway unreachable: ${e.localizedMessage}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                        val configured = it.isSuccessful && json.optBoolean("serviceAccountConfigured", false)
                        _gatewayHealth.value = GatewayHealth(
                            configured = configured,
                            message = when {
                                configured -> "Direct FCM gateway is ready"
                                it.code == 405 -> "Worker is online but outdated—deploy the new worker file"
                                else -> json.optString("error", "Worker secret is missing or invalid")
                            },
                            projectId = json.optString("projectId"),
                            version = json.optString("version"),
                            r2Configured = json.optBoolean("r2Configured"),
                            turnConfigured = json.optBoolean("turnConfigured"),
                            sfuConfigured = json.optBoolean("sfuConfigured")
                        )
                    }
                }
            })
    }

    fun startAudioCall(recipient: User, onReady: (String) -> Unit) = startCall(recipient, false, onReady)
    fun startVideoCall(recipient: User, onReady: (String) -> Unit) = startCall(recipient, true, onReady)

    fun endCall(recipient: User, callId: String) {
        CallEngine.end()
        val caller = getCurrentUserOrFallback() ?: return
        withUserFcmToken(recipient.uid, recipient.fcmToken) { token ->
            triggerFcmGatewayNotification(
                gatewayUrl = _webhookUrl.value, targetToken = token,
                senderName = caller.name, messageBody = "Call ended",
                senderId = caller.uid, senderProfileUrl = caller.profileImageUrl,
                notificationType = "call_cancelled", targetId = callId
            )
        }
    }

    private fun startCall(recipient: User, video: Boolean, onReady: (String) -> Unit) {
        val caller = getCurrentUserOrFallback() ?: return
        try {
        CallEngine.startOutgoing(getApplication(), recipient.uid, recipient.name, recipient.profileImageUrl, video = video) { callId ->
            withUserFcmToken(recipient.uid, recipient.fcmToken) { token ->
                triggerFcmGatewayNotification(
                    gatewayUrl = _webhookUrl.value,
                    targetToken = token,
                    senderName = caller.name,
                    messageBody = if (video) "Incoming Convo Chat video call" else "Incoming Convo Chat audio call",
                    senderId = caller.uid,
                    senderProfileUrl = caller.profileImageUrl,
                    notificationType = if (video) "incoming_video_call" else "incoming_call",
                    targetId = callId
                )
            }
            val chatId = listOf(caller.uid, recipient.uid).sorted().joinToString("_")
            val historyMessage = Message(
                messageId = "call_$callId", senderId = caller.uid, senderName = caller.name,
                senderUsername = caller.username, text = if (video) "📹 Convo Chat video call" else "📞 Convo Chat audio call",
                timestamp = System.currentTimeMillis(), deliveredToRecipient = false
            )
            getDatabaseInstance().getReference("chats").child(chatId).child("messages")
                .child(historyMessage.messageId).setValue(historyMessage)
            viewModelScope.launch(Dispatchers.IO) {
                cacheDao.insertMessage(CachedMessage.fromMessage(historyMessage, chatId))
            }
            onReady(callId)
            viewModelScope.launch {
                kotlinx.coroutines.delay(30_000)
                if (CallEngine.state.value.callId == callId && CallEngine.state.value.status == "ringing") {
                    CallEngine.timeout()
                }
            }
        }
        } catch (error: Throwable) {
            Log.e("CALL_ENGINE", "Call launch failed", error)
            CallEngine.reportFailure("Could not start call: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun askAssistant(prompt: String, memory: List<String>, onResult: (String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onResult("Please sign in again.")
        if (!PrivacyPreferences.settings.value.allowAiProcessing) return onResult("AI processing is disabled in Privacy & Security. Enable it before sending private chat content to the assistant.")
        if (!_flagshipConfig.value.assistantEnabled) return onResult("Assistant is currently disabled by the admin.")
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val token = tokenResult.token ?: return@addOnSuccessListener onResult("Could not authenticate assistant.")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val payload = JSONObject().apply {
                        put("message", prompt.take(4000))
                        put("model", _flagshipConfig.value.aiModel)
                        put("systemPrompt", _flagshipConfig.value.aiSystemPrompt.take(2000))
                        put("memory", JSONArray(memory.takeLast(20)))
                    }
                    val request = Request.Builder().url(_webhookUrl.value.trimEnd('/') + "/ai/chat")
                        .header("Authorization", "Bearer $token")
                        .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
                    OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        val text = if (response.isSuccessful) JSONObject(body).optString("reply")
                        else JSONObject(body.ifBlank { "{}" }).optString("error", "Assistant unavailable (${response.code})")
                        withContext(Dispatchers.Main) { onResult(text.ifBlank { "I could not generate a response." }) }
                    }
                } catch (error: Throwable) {
                    withContext(Dispatchers.Main) { onResult(error.localizedMessage ?: "Assistant connection failed") }
                }
            }
        }.addOnFailureListener { onResult(it.localizedMessage ?: "Assistant authentication failed") }
    }

    fun askAssistantStreaming(prompt: String, memory: List<String>, onDelta: (String) -> Unit, onDone: (String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onDone("Please sign in again")
        if (!PrivacyPreferences.settings.value.allowAiProcessing) return onDone("AI processing is disabled in Privacy & Security")
        if (!_flagshipConfig.value.assistantEnabled) return onDone("Assistant is disabled")
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val token = tokenResult.token ?: return@addOnSuccessListener onDone("Could not authenticate")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val payload = JSONObject().apply { put("message", prompt.take(4000)); put("model", _flagshipConfig.value.aiModel); put("systemPrompt", _flagshipConfig.value.aiSystemPrompt.take(2000)); put("memory", JSONArray(memory.takeLast(20))); put("stream", true) }
                    val request = Request.Builder().url(_webhookUrl.value.trimEnd('/') + "/ai/chat").header("Authorization", "Bearer $token").post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
                    OkHttpClient.Builder().callTimeout(2, TimeUnit.MINUTES).build().newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use withContext(Dispatchers.Main) { onDone("Assistant unavailable (${response.code})") }
                        response.body?.charStream()?.buffered()?.useLines { lines ->
                            lines.forEach { line ->
                                if (line.startsWith("data: ") && !line.endsWith("[DONE]")) {
                                    val delta = runCatching { JSONObject(line.removePrefix("data: ")).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty() }.getOrDefault("")
                                    if (delta.isNotEmpty()) withContext(Dispatchers.Main) { onDelta(delta) }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) { onDone(null) }
                    }
                } catch (error: Throwable) { withContext(Dispatchers.Main) { onDone(error.localizedMessage ?: "Streaming failed") } }
            }
        }.addOnFailureListener { onDone(it.localizedMessage ?: "Authentication failed") }
    }

    private fun workerSecurityPost(path: String, payload: JSONObject, onResult: (Boolean, JSONObject) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onResult(false, JSONObject().put("error", "Not signed in"))
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val token = tokenResult.token ?: return@addOnSuccessListener onResult(false, JSONObject().put("error", "Missing token"))
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(_webhookUrl.value.trimEnd('/') + path).header("Authorization", "Bearer $token").post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
                    OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                        val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                        withContext(Dispatchers.Main) { onResult(response.isSuccessful, json) }
                    }
                } catch (e: Throwable) { withContext(Dispatchers.Main) { onResult(false, JSONObject().put("error", e.localizedMessage ?: "Request failed")) } }
            }
        }.addOnFailureListener { onResult(false, JSONObject().put("error", it.localizedMessage ?: "Authentication failed")) }
    }

    fun syncPresenceToWorker(active: Boolean = true, foreground: Boolean = true) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        workerSecurityPost(
            "/presence/heartbeat",
            JSONObject()
                .put("active", active)
                .put("foreground", foreground)
                .put("onlineUntil", if (active) System.currentTimeMillis() + 5 * 60 * 1000L else 0L)
                .put("appVersion", BuildConfig.VERSION_NAME)
        ) { _, _ -> }
    }

    fun registerDeviceSession() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        val prefs = getApplication<Application>().getSharedPreferences("convo_device_session", Context.MODE_PRIVATE)
        val id = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).putLong("firstSeen", System.currentTimeMillis()).apply() }
        val localSession = DeviceSession(id, "${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE}", BuildConfig.VERSION_NAME, prefs.getLong("firstSeen", System.currentTimeMillis()), System.currentTimeMillis(), true, "Protected", "Waiting for secure sync")
        if (_deviceSessions.value.none { it.sessionId == id }) _deviceSessions.value = listOf(localSession) + _deviceSessions.value
        workerSecurityPost("/sessions/register", JSONObject().put("sessionId", id).put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}").put("androidVersion", "Android ${Build.VERSION.RELEASE}").put("appVersion", BuildConfig.VERSION_NAME).put("firstSeenAt", prefs.getLong("firstSeen", System.currentTimeMillis()))) { ok, _ -> if (ok) refreshDeviceSessions() }
    }

    fun refreshDeviceSessions() = workerSecurityPost("/sessions/list", JSONObject()) { ok, json ->
        if (!ok) return@workerSecurityPost
        val array = json.optJSONArray("sessions") ?: JSONArray()
        _deviceSessions.value = (0 until array.length()).map { i -> array.optJSONObject(i) ?: JSONObject() }.map { item -> DeviceSession(item.optString("sessionId"), item.optString("deviceName", "Android device"), item.optString("androidVersion"), item.optString("appVersion"), item.optLong("firstSeenAt"), item.optLong("lastSeenAt"), item.optBoolean("active", true), item.optString("maskedIp"), item.optString("city", "Unknown"), item.optString("region"), item.optString("country"), item.optDouble("latitude"), item.optDouble("longitude")) }
    }

    fun deleteDeviceSession(sessionId: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        if (sessionId.isBlank()) return onComplete(false, "Invalid device session")
        workerSecurityPost("/sessions/delete", JSONObject().put("sessionId", sessionId)) { ok, json ->
            if (ok) _deviceSessions.value = _deviceSessions.value.filterNot { it.sessionId == sessionId }
            onComplete(ok, if (ok) "Device history deleted" else json.optString("error", "Could not delete device history"))
        }
    }

    fun clearDeviceSessions(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        workerSecurityPost("/sessions/clear", JSONObject()) { ok, json ->
            if (ok) _deviceSessions.value = emptyList()
            onComplete(ok, if (ok) "All device history deleted from the database" else json.optString("error", "Could not clear device history"))
        }
    }

    fun logoutAllDevices(onComplete: (Boolean, String) -> Unit) = workerSecurityPost("/sessions/revoke-all", JSONObject()) { ok, json ->
        onComplete(ok, if (ok) "All device sessions revoked" else json.optString("error", "Could not revoke sessions"))
        if (ok) logout {}
    }

    fun submitUserReport(targetUid: String, category: String, description: String, onComplete: (Boolean, String) -> Unit) = workerSecurityPost("/reports/create", JSONObject().put("targetUid", targetUid).put("category", category).put("description", description)) { ok, json -> onComplete(ok, if (ok) "Report sent to ADMIN RAKIB" else json.optString("error", "Report failed")) }

    fun loadAdminReports() = workerSecurityPost("/admin/reports/list", JSONObject()) { ok, json ->
        if (!ok) return@workerSecurityPost
        val a = json.optJSONArray("reports") ?: JSONArray(); _adminReports.value = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { UserReport(it.optString("id"), it.optString("reporterId"), it.optString("reporterEmail"), it.optString("targetUid"), it.optString("category"), it.optString("description"), it.optString("status", "pending"), it.optLong("createdAt")) }
    }

    fun setUserBanned(uid: String, banned: Boolean, reason: String = "", onComplete: (Boolean, String) -> Unit) = workerSecurityPost(if (banned) "/admin/user/ban" else "/admin/user/unban", JSONObject().put("uid", uid).put("reason", reason)) { ok, json -> onComplete(ok, if (ok) if (banned) "Account banned" else "Account restored" else json.optString("error", "Action failed")) }

    fun sendAdminTestNotification() {
        val admin = getCurrentUserOrFallback() ?: run {
            _gatewayHealth.value = GatewayHealth(message = "Admin session is not authenticated")
            return
        }
        withUserFcmToken(admin.uid, admin.fcmToken) { token ->
            triggerFcmGatewayNotification(
                gatewayUrl = _webhookUrl.value,
                targetToken = token,
                senderName = "Convo Chat Diagnostics",
                messageBody = "Direct FCM gateway test completed successfully",
                senderId = admin.uid,
                senderProfileUrl = admin.profileImageUrl,
                notificationType = "gateway_test",
                targetId = "test_${System.currentTimeMillis()}"
            )
        }
    }

    private fun listenFlagshipControl() {
        flagshipListener?.remove()
        flagshipListener = FirebaseFirestore.getInstance().collection("app_config").document("flagship")
            .addSnapshotListener { document, error ->
                if (error != null) Log.e("FLAGSHIP", "Firestore config listener failed: ${error.message}")
                else document?.toObject(FlagshipConfig::class.java)?.let { incoming ->
                    val legacyBrand = "Fire" + "Chat"
                    val normalized = incoming.copy(
                        aiDisplayName = incoming.aiDisplayName.replace(legacyBrand, "Convo Chat"),
                        aiSystemPrompt = incoming.aiSystemPrompt.replace(legacyBrand, "Convo Chat")
                    )
                    if (normalized.updatedAt >= _flagshipConfig.value.updatedAt) _flagshipConfig.value = normalized
                }
            }
        val ref = getDatabaseInstance().getReference("config").child("flagship")
        flagshipRtdbListener?.let { flagshipRtdbRef?.removeEventListener(it) }
        flagshipRtdbRef = ref
        flagshipRtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(FlagshipConfig::class.java)?.let { incoming ->
                    val legacyBrand = "Fire" + "Chat"
                    val normalized = incoming.copy(
                        aiDisplayName = incoming.aiDisplayName.replace(legacyBrand, "Convo Chat"),
                        aiSystemPrompt = incoming.aiSystemPrompt.replace(legacyBrand, "Convo Chat")
                    )
                    if (normalized.updatedAt >= _flagshipConfig.value.updatedAt) _flagshipConfig.value = normalized
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("FLAGSHIP", "RTDB config failed: ${error.message}") }
        }.also { ref.addValueEventListener(it) }
        listenFeatureRequestsIfAdmin()
    }

    private fun listenFeatureRequestsIfAdmin() {
        val admin = FirebaseAuth.getInstance().currentUser?.email?.lowercase()?.trim()?.trimEnd('.') == "mr4425390@gmail.com"
        featureRequestListener?.remove(); featureRequestListener = null
        premiumRequestListener?.remove(); premiumRequestListener = null
        if (!admin) { _featureRequests.value = emptyList(); _premiumRequests.value = emptyList(); return }
        featureRequestListener = FirebaseFirestore.getInstance().collection("feature_requests")
            .addSnapshotListener { snapshot, _ ->
                _featureRequests.value = snapshot?.documents?.mapNotNull { it.toObject(FeatureRequest::class.java) }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
            }
        premiumRequestListener = FirebaseFirestore.getInstance().collection("premium_requests")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                _premiumRequests.value = snapshot?.documents?.mapNotNull { it.toObject(PremiumRequest::class.java) }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
            }
    }

    fun submitPremiumRequest(plan: String, method: String, transactionId: String, paymentProofUrl: String = "", onComplete: (Boolean, String) -> Unit) {
        val user = getCurrentUserOrFallback() ?: return onComplete(false, "Please sign in again")
        val config = _flagshipConfig.value
        val normalizedPlan = plan.lowercase().takeIf {
            (it == "monthly" && config.premiumMonthlyEnabled) || (it == "yearly" && config.premiumYearlyEnabled) || (it == "lifetime" && config.premiumLifetimeEnabled)
        } ?: return onComplete(false, "Plan is unavailable")
        val normalizedMethod = method.lowercase().takeIf {
            (it == "bkash" && config.premiumBkashEnabled) || (it == "nagad" && config.premiumNagadEnabled) || (it == "rocket" && config.premiumRocketEnabled)
        } ?: return onComplete(false, "Payment method is unavailable")
        val cleanTx = transactionId.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "").take(40)
        if (cleanTx.length < 6) return onComplete(false, "Enter a valid transaction ID")
        val amount = when (normalizedPlan) { "monthly" -> config.premiumMonthlyPrice; "yearly" -> config.premiumYearlyPrice; else -> config.premiumLifetimePrice }
        val requestKey = "${normalizedMethod}_${cleanTx}".lowercase()
        val ref = FirebaseFirestore.getInstance().collection("premium_requests").document(requestKey)
        val request = PremiumRequest(
            id = ref.id, userId = user.uid, userName = user.name,
            userEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty(), userImageUrl = user.profileImageUrl,
            plan = normalizedPlan, paymentMethod = normalizedMethod, transactionId = cleanTx,
            paymentProofUrl = paymentProofUrl.takeIf { it.startsWith("https://") }.orEmpty(),
            amount = amount, status = "pending", createdAt = System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().runTransaction { transaction ->
            if (transaction.get(ref).exists()) error("This transaction ID was already submitted")
            transaction.set(ref, request)
        }.addOnSuccessListener { onComplete(true, "Premium request submitted") }
            .addOnFailureListener { onComplete(false, it.localizedMessage ?: "Request failed") }
    }

    fun reviewPremiumRequest(request: PremiumRequest, approve: Boolean, note: String = "", onComplete: (Boolean) -> Unit = {}) {
        val admin = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)
        if (admin.email?.lowercase()?.trim()?.trimEnd('.') != "mr4425390@gmail.com") return onComplete(false)
        val now = System.currentTimeMillis()
        val until = when (request.plan) {
            "monthly" -> now + 30L * 24 * 60 * 60 * 1000
            "yearly" -> now + 365L * 24 * 60 * 60 * 1000
            else -> Long.MAX_VALUE
        }
        val firestore = FirebaseFirestore.getInstance(); val batch = firestore.batch()
        batch.update(firestore.collection("premium_requests").document(request.id), mapOf("status" to if (approve) "approved" else "rejected", "reviewedAt" to now, "reviewedBy" to admin.uid, "adminNote" to note.take(500)))
        if (approve) batch.update(firestore.collection("users").document(request.userId), mapOf("isPremium" to true, "premiumPlan" to request.plan, "premiumUntil" to until, "premiumApprovedAt" to now))
        batch.commit().addOnSuccessListener {
            createActivityNotification(request.userId, if (approve) "premium_approved" else "premium_rejected", request.id, if (approve) "আপনার Convo Chat Premium চালু হয়েছে 🎉" else "Premium request was not approved")
            onComplete(true)
        }.addOnFailureListener { onComplete(false) }
    }

    fun grantPremiumGift(userIds: List<String>, days: Int, onComplete: (Boolean) -> Unit = {}) {
        val admin = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)
        if (admin.email?.lowercase()?.trim()?.trimEnd('.') != "mr4425390@gmail.com" || days !in 1..365 || userIds.isEmpty()) return onComplete(false)
        val now = System.currentTimeMillis(); val firestore = FirebaseFirestore.getInstance(); val batch = firestore.batch()
        userIds.distinct().take(100).forEach { uid -> batch.update(firestore.collection("users").document(uid), mapOf("isPremium" to true, "premiumPlan" to "gift", "premiumUntil" to now + days * 86_400_000L, "premiumApprovedAt" to now, "premiumSource" to "admin_gift")) }
        batch.commit().addOnSuccessListener { userIds.distinct().take(100).forEach { createActivityNotification(it, "premium_gift", "gift_$now", "ADMIN RAKIB gifted you $days days of Convo Chat Premium 🎉") }; onComplete(true) }.addOnFailureListener { onComplete(false) }
    }

    fun revokePremium(uid: String, onComplete: (Boolean) -> Unit = {}) {
        val admin = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)
        if (admin.email?.lowercase()?.trim()?.trimEnd('.') != "mr4425390@gmail.com") return onComplete(false)
        FirebaseFirestore.getInstance().collection("users").document(uid).update(mapOf("isPremium" to false, "premiumPlan" to "", "premiumUntil" to 0L, "premiumSource" to "revoked"))
            .addOnSuccessListener { createActivityNotification(uid, "premium_revoked", uid, "Your Premium access was cancelled by ADMIN RAKIB"); onComplete(true) }.addOnFailureListener { onComplete(false) }
    }

    fun submitFeatureRequest(title: String, description: String, onComplete: (Boolean) -> Unit = {}) {
        val user = getCurrentUserOrFallback() ?: return onComplete(false)
        if (title.isBlank() || description.isBlank()) return onComplete(false)
        val ref = FirebaseFirestore.getInstance().collection("feature_requests").document()
        ref.set(FeatureRequest(ref.id, user.uid, user.name, title.trim(), description.trim(), "pending", "", System.currentTimeMillis(), System.currentTimeMillis()))
            .addOnSuccessListener { onComplete(true) }.addOnFailureListener { onComplete(false) }
    }

    fun updateFeatureRequest(requestId: String, status: String, adminNote: String = "") {
        if (FirebaseAuth.getInstance().currentUser?.email?.lowercase()?.trim()?.trimEnd('.') != "mr4425390@gmail.com") return
        val ref = FirebaseFirestore.getInstance().collection("feature_requests").document(requestId)
        ref.update(mapOf("status" to status, "adminNote" to adminNote, "updatedAt" to System.currentTimeMillis()))
            .addOnSuccessListener {
                ref.get().addOnSuccessListener { doc ->
                    createActivityNotification(doc.getString("requesterId") ?: "", "feature_request_$status", requestId, "Your feature request is now $status")
                }
            }
    }

    fun publishFlagshipConfig(config: FlagshipConfig, onComplete: (Boolean) -> Unit = {}) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)
        if (user.email?.lowercase()?.trim()?.trimEnd('.') != "mr4425390@gmail.com") return onComplete(false)
        if (config.updateEnabled && !config.apkUrl.startsWith("https://")) return onComplete(false)

        val now = System.currentTimeMillis()
        // Publishing an enabled update always starts a new campaign, even when
        // the APK versionCode is intentionally unchanged. Non-admin users must
        // complete this campaign once; admins always retain emergency access.
        val published = if (config.updateEnabled) {
            config.copy(
                mandatoryUpdate = true,
                updateId = UUID.randomUUID().toString(),
                updatePublishedAt = now,
                updatedAt = now,
                updatedBy = user.uid
            )
        } else {
            config.copy(mandatoryUpdate = false, updatedAt = now, updatedBy = user.uid)
        }
        FirebaseFirestore.getInstance().collection("app_config").document("flagship")
            .set(published)
            .addOnSuccessListener {
                getDatabaseInstance().getReference("config").child("flagship").setValue(published)
                    .addOnFailureListener { Log.e("FLAGSHIP", "RTDB mirror failed: ${it.message}") }
                if (published.updateEnabled) _usersState.value.forEach { target ->
                    createActivityNotification(target.uid, "app_update", published.updateId, "Required Convo Chat ${published.versionName} update is available")
                }
                onComplete(true)
            }.addOnFailureListener { onComplete(false) }
    }

    fun updateFlagshipFields(fields: Map<String, Any>, onComplete: (Boolean) -> Unit = {}) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)
        if (user.email?.lowercase()?.trim()?.trimEnd('.') != "mr4425390@gmail.com") return onComplete(false)
        val updates = fields + mapOf("updatedAt" to System.currentTimeMillis(), "updatedBy" to user.uid)
        FirebaseFirestore.getInstance().collection("app_config").document("flagship").update(updates)
            .addOnSuccessListener { getDatabaseInstance().getReference("config").child("flagship").updateChildren(updates); onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    private fun listenToGlobalConfig() {
        try {
            val configRef = getDatabaseInstance().getReference("config").child("app_settings")
            configRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val configured = (snapshot.child("workerUrl").value as? String)
                            ?: (snapshot.child("webhookUrl").value as? String)
                        val url = configured?.takeIf {
                            it.startsWith("https://") && !it.contains("n8n", ignoreCase = true)
                        } ?: defaultWebhookUrl
                        _webhookUrl.value = url
                        sharedPrefs.edit().putString("webhook_url", url).apply()
                        if (configured != url) {
                            configRef.updateChildren(mapOf("webhookUrl" to url, "workerUrl" to url))
                        }
                        Log.d("RTDB_CONFIG", "Loaded direct FCM gateway URL: $url")
                    } else {
                        configRef.setValue(mapOf("webhookUrl" to defaultWebhookUrl, "workerUrl" to defaultWebhookUrl))
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("RTDB_CONFIG", "Listen to RTDB config failed: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("RTDB_CONFIG", "Error subscribing to global RTDB config: ${e.message}")
        }
    }

    private fun checkFirebaseConfiguration() {
        try {
            val auth = FirebaseAuth.getInstance()
            listenToGlobalConfig()
            if (auth.currentUser != null) {
                val uid = auth.currentUser!!.uid
                loadCurrentUserProfile(uid)
                setupPresence(uid)
                loadAllUsers()
                listenToAllPresences()
                listenForInAppNotifications()
                registerDeviceSession()
                restoreTextBackup(uid)
            }
        } catch (e: Exception) {
            Log.e("FirebaseConfig", "Firebase initialization failed: ${e.message}")
            _isFirebaseConfigured.value = false
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (!_isFirebaseConfigured.value) {
            _authError.value = "Firebase is not configured properly."
            return
        }

        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Please fill in all fields"
            return
        }

        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: ""
                        AccountCredentialVault.save(getApplication(), email, password)
                        retrieveFCMTokenAndStore(uid)
                        loadCurrentUserProfile(uid)
                        setupPresence(uid)
                        loadAllUsers()
                        listenToAllPresences()
                        listenForInAppNotifications()
                        registerDeviceSession()
                        restoreTextBackup(uid)
                        _authLoading.value = false
                        onSuccess()
                    }
                    .addOnFailureListener { exception ->
                        _authError.value = exception.localizedMessage ?: "Login failed"
                        _authLoading.value = false
                    }
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Login Error"
                _authLoading.value = false
            }
        }
    }

    fun signInWithGoogleCredential(credential: AuthCredential, onSuccess: () -> Unit) {
        _authLoading.value = true
        _authError.value = null
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val authUser = result.user
                if (authUser == null) {
                    _authLoading.value = false
                    _authError.value = "Google account could not be loaded"
                    return@addOnSuccessListener
                }
                val usernameBase = (authUser.email?.substringBefore("@") ?: authUser.displayName ?: "user")
                    .lowercase().replace("[^a-z0-9_]".toRegex(), "")
                val profile = User(
                    uid = authUser.uid,
                    name = authUser.displayName ?: usernameBase,
                    username = usernameBase,
                    profileImageUrl = authUser.photoUrl?.toString() ?: "",
                    createdAt = authUser.metadata?.creationTimestamp ?: System.currentTimeMillis()
                )
                val now = System.currentTimeMillis()
                val firstGoogleLogin = kotlin.math.abs((authUser.metadata?.lastSignInTimestamp ?: now) - (authUser.metadata?.creationTimestamp ?: 0L)) < 60_000L
                val profileValues = mutableMapOf<String, Any>(
                    "uid" to profile.uid, "name" to profile.name, "username" to profile.username,
                    "profileImageUrl" to profile.profileImageUrl, "createdAt" to profile.createdAt
                ).apply {
                    if (firstGoogleLogin) putAll(mapOf(
                        "isPremium" to true, "premiumPlan" to "trial", "premiumUntil" to now + 14L * 86_400_000L,
                        "premiumApprovedAt" to now, "premiumTrialClaimed" to true, "premiumSource" to "signup_trial"
                    ))
                }
                FirebaseFirestore.getInstance().collection("users").document(authUser.uid)
                    .set(profileValues, SetOptions.merge())
                    .addOnCompleteListener {
                        retrieveFCMTokenAndStore(authUser.uid)
                        loadCurrentUserProfile(authUser.uid)
                        setupPresence(authUser.uid)
                        loadAllUsers()
                        listenToAllPresences()
                        listenForInAppNotifications()
                        registerDeviceSession()
                        restoreTextBackup(authUser.uid)
                        _authLoading.value = false
                        onSuccess()
                    }
            }
            .addOnFailureListener {
                _authLoading.value = false
                _authError.value = it.localizedMessage ?: "Google sign-in failed"
            }
    }

    fun signup(email: String, name: String, dob: String, password: String, profileImageUrl: String, onSuccess: () -> Unit) {
        if (!_isFirebaseConfigured.value) {
            _authError.value = "Firebase is not configured properly."
            return
        }

        if (email.isBlank() || name.isBlank() || dob.isBlank() || password.isBlank()) {
            _authError.value = "Please fill in all fields"
            return
        }

        if (password.length < 6) {
            _authError.value = "Password must be at least 6 characters"
            return
        }

        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: ""
                        val cleanName = name.lowercase().replace("\\s".toRegex(), "")
                        val randomSuffix = (1000..9999).random()
                        val generatedUsername = "${cleanName}_$randomSuffix"

                        val createdAt = System.currentTimeMillis()
                        val newUser = User(
                            uid = uid, name = name, dob = dob, username = generatedUsername,
                            profileImageUrl = profileImageUrl, createdAt = createdAt,
                            isPremium = true, premiumPlan = "trial",
                            premiumUntil = createdAt + 14L * 86_400_000L,
                            premiumApprovedAt = createdAt, premiumTrialClaimed = true,
                            premiumSource = "signup_trial"
                        )

                        FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
                            .set(newUser)
                            .addOnSuccessListener {
                                AccountCredentialVault.save(getApplication(), email, password)
                                retrieveFCMTokenAndStore(uid)
                                _currentUserState.value = newUser
                                setupPresence(uid)
                                loadAllUsers()
                                listenToAllPresences()
                                listenForInAppNotifications()
                                registerDeviceSession()
                                restoreTextBackup(uid)
                                loadStories()
                                loadPosts()
                                loadGroups()
                                listenToUnreadCounts()
                                _authLoading.value = false
                                onSuccess()
                            }
                            .addOnFailureListener { exception ->
                                _authError.value = "Failed to save profile: ${exception.localizedMessage}"
                                _authLoading.value = false
                            }
                    }
                    .addOnFailureListener { exception ->
                        _authError.value = exception.localizedMessage ?: "Signup failed"
                        _authLoading.value = false
                    }
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Signup Error"
                _authLoading.value = false
            }
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (email.isBlank()) {
            onFailure("Please enter your email address")
            return
        }
        viewModelScope.launch {
            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Failed to send reset link") }
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Error sending reset link")
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                // Instantly set offline status in RTDB status node before signing out
                getDatabaseInstance().getReference("status").child(uid)
                    .setValue(mapOf("isOnline" to false, "lastActive" to System.currentTimeMillis()))
            }
            FirebaseAuth.getInstance().signOut()
            currentUserProfileListener?.remove()
            currentUserProfileListener = null
            _currentUserState.value = null
            _usersState.value = emptyList()
            _filteredUsersState.value = emptyList()
            _chatMessagesState.value = emptyList()
            activeChatId = null
            stopListeningToChat()
            stopListeningToTyping()
            onSuccess()
        } catch (e: Exception) {
            Log.e("Auth", "Logout failed: ${e.message}")
        }
    }

    private fun retrieveFCMTokenAndStore(uid: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM_TOKEN", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                val firestore = FirebaseFirestore.getInstance()
                // Recipient routing token is kept on the user profile as requested so an
                // authenticated sender can include it directly in the Worker POST body.
                firestore.collection("users").document(uid)
                    .update(mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to System.currentTimeMillis()))
                    .addOnSuccessListener {
                        _currentUserState.value = _currentUserState.value?.copy(fcmToken = token)
                    }
            }
        } catch (e: Exception) {
            Log.e("FCM_TOKEN", "Messaging not initialized: ${e.message}")
        }
    }

    private fun loadCurrentUserProfile(uid: String) {
        conversationIdsJob?.cancel()
        conversationIdsJob = viewModelScope.launch {
            cacheDao.getConversationChatIds().collect { chatIds ->
                _conversationUserIds.value = chatIds.flatMap { it.split("_") }.filter { it != uid }.toSet()
            }
        }
        val profileRef = FirebaseFirestore.getInstance().collection("users").document(uid)
        currentUserProfileListener?.remove()
        currentUserProfileListener = profileRef.addSnapshotListener { document, error ->
            if (error != null) {
                Log.e("FIRESTORE_PROFILE", "Live profile failed: ${error.message}")
            } else if (document != null && document.exists()) {
                _accountBanned.value = document.getBoolean("banned") == true
                if (_accountBanned.value) return@addSnapshotListener
                runCatching { document.toObject(User::class.java) }.getOrNull()?.let { parsed ->
                    val liveUser = parsed.copy(
                        isPremium = document.getBoolean("isPremium") ?: parsed.isPremium,
                        premiumPlan = document.getString("premiumPlan") ?: parsed.premiumPlan,
                        premiumUntil = document.getLong("premiumUntil") ?: parsed.premiumUntil,
                        premiumApprovedAt = document.getLong("premiumApprovedAt") ?: parsed.premiumApprovedAt
                    )
                    _currentUserState.value = liveUser
                    rememberAccount(liveUser)
                }
            }
        }
        profileRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = try {
                        document.toObject(User::class.java)
                    } catch (e: Exception) {
                        try {
                            User(
                                uid = document.getString("uid") ?: document.id,
                                name = document.getString("name") ?: "",
                                dob = document.getString("dob") ?: "",
                                username = document.getString("username") ?: "",
                                fcmToken = document.getString("fcmToken") ?: "",
                                profileImageUrl = document.getString("profileImageUrl") ?: "",
                                blockedUsers = (document.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                createdAt = document.getLong("createdAt") ?: 0L
                            )
                        } catch (ex: Exception) {
                            null
                        }
                    }
                    _currentUserState.value = user?.copy(
                        isPremium = document.getBoolean("isPremium") ?: user.isPremium,
                        premiumPlan = document.getString("premiumPlan") ?: user.premiumPlan,
                        premiumUntil = document.getLong("premiumUntil") ?: user.premiumUntil,
                        premiumApprovedAt = document.getLong("premiumApprovedAt") ?: user.premiumApprovedAt
                    )
                    listenToActivityCenter(uid)
                    
                    // Identify admin by user email
                    val auth = FirebaseAuth.getInstance()
                    val admin = auth.currentUser?.email?.lowercase()?.trim()?.trimEnd('.') == "mr4425390@gmail.com"
                    _userIsAdmin.value = admin
                    if (admin && user?.role != "moderator") document.reference.update("role", "moderator")
                    listenFeatureRequestsIfAdmin()
                }
                
                // Load other channels unconditionally
                loadStories()
                loadPosts()
                loadGroups()
                listenToUnreadCounts()
            }
            .addOnFailureListener { e ->
                Log.e("FIRESTORE_PROFILE", "Failed to load user profile: ${e.message}")
                // Load channels unconditionally even on failure
                loadStories()
                loadPosts()
                loadGroups()
                listenToUnreadCounts()
            }
    }

    private fun listenToUnreadCounts() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        getDatabaseInstance().getReference("unread_counts").child(currentUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val counts = mutableMapOf<String, Int>()
                    for (child in snapshot.children) {
                        val senderId = child.key ?: continue
                        val count = child.getValue(Int::class.java) ?: 0
                        counts[senderId] = count
                    }
                    _unreadCountsState.value = counts
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun getCurrentUserOrFallback(): User? {
        val currentLocal = _currentUserState.value
        if (currentLocal != null) return currentLocal
        val authUser = FirebaseAuth.getInstance().currentUser ?: return null
        return User(
            uid = authUser.uid,
            name = authUser.displayName ?: authUser.email?.substringBefore("@") ?: "User",
            dob = "",
            username = authUser.email?.substringBefore("@") ?: "user",
            profileImageUrl = authUser.photoUrl?.toString() ?: "",
            createdAt = System.currentTimeMillis()
        )
    }

    private fun loadAllUsers() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE", "Listen failed: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allUsersList = mutableListOf<User>()
                    for (doc in snapshot.documents) {
                        val user = try {
                            doc.toObject(User::class.java)
                        } catch (e: Exception) {
                            try {
                                User(
                                    uid = doc.getString("uid") ?: doc.id,
                                    name = doc.getString("name") ?: "",
                                    dob = doc.getString("dob") ?: "",
                                    username = doc.getString("username") ?: "",
                                    fcmToken = doc.getString("fcmToken") ?: "",
                                    profileImageUrl = doc.getString("profileImageUrl") ?: "",
                                    blockedUsers = (doc.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                    createdAt = doc.getLong("createdAt") ?: 0L
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        val hydratedUser = user?.copy(
                            isPremium = doc.getBoolean("isPremium") ?: user.isPremium,
                            premiumPlan = doc.getString("premiumPlan") ?: user.premiumPlan,
                            premiumUntil = doc.getLong("premiumUntil") ?: user.premiumUntil,
                            premiumApprovedAt = doc.getLong("premiumApprovedAt") ?: user.premiumApprovedAt
                        )
                        if (hydratedUser != null && hydratedUser.uid != currentUid) {
                            allUsersList.add(hydratedUser)
                        }
                    }
                    _usersState.value = allUsersList

                    val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()
                    val filteredList = allUsersList.filter { user ->
                        !myBlocked.contains(user.uid) && !user.blockedUsers.contains(currentUid) && isUserDiscoverable(user, currentUid)
                    }
                    _filteredUsersState.value = filteredList
                }
            }
    }

    fun searchUsers(query: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()
        val allowedUsers = _usersState.value.filter { user ->
            !myBlocked.contains(user.uid) && !user.blockedUsers.contains(currentUid) && isUserDiscoverable(user, currentUid)
        }
        if (query.isBlank()) {
            _filteredUsersState.value = allowedUsers
        } else {
            _filteredUsersState.value = allowedUsers.filter { user ->
                user.username.contains(query, ignoreCase = true) ||
                        user.name.contains(query, ignoreCase = true)
            }
        }
    }

    private fun isUserDiscoverable(user: User, currentUid: String): Boolean {
        if (!user.profileHidden && user.profileVisibility != "friends_only") return true
        val current = _currentUserState.value
        val isFriend = current?.friends?.contains(user.uid) == true || user.friends.contains(currentUid)
        val hasConversation = _conversationUserIds.value.contains(user.uid)
        return isFriend || hasConversation
    }

    // Online Presence handling
    private fun setupPresence(uid: String) {
        val database = getDatabaseInstance()
        val statusRef = database.getReference("status").child(uid)
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val now = System.currentTimeMillis()
                    statusRef.setValue(mapOf("isOnline" to true, "lastActive" to now, "onlineUntil" to now + 5 * 60 * 1000L, "offlineSince" to 0L))
                    statusRef.onDisconnect().setValue(mapOf("isOnline" to false, "lastActive" to now, "onlineUntil" to 0L, "offlineSince" to now))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToAllPresences() {
        val statusRef = getDatabaseInstance().getReference("status")
        presenceListener = statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                data class PresenceSnapshot(val online: Boolean, val lastActive: Long, val onlineUntil: Long, val offlineSince: Long)
                val statusMap = mutableMapOf<String, PresenceSnapshot>()
                val now = System.currentTimeMillis()
                val leaseMs = 5 * 60 * 1000L
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val lastActive = child.child("lastActive").getValue(Long::class.java) ?: 0L
                    val onlineUntil = child.child("onlineUntil").getValue(Long::class.java) ?: (lastActive + leaseMs)
                    val explicitOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false
                    val online = explicitOnline && lastActive > 0L && now <= onlineUntil
                    val recordedOffline = child.child("offlineSince").getValue(Long::class.java) ?: 0L
                    statusMap[uid] = PresenceSnapshot(online, lastActive, onlineUntil, if (online) 0L else (recordedOffline.takeIf { it > 0L } ?: lastActive))
                }

                val updatedUsers = _usersState.value.map { user ->
                    val status = statusMap[user.uid]
                    if (status != null) {
                        user.copy(isOnline = status.online, lastActive = status.lastActive, onlineUntil = status.onlineUntil, offlineSince = status.offlineSince)
                    } else {
                        user.copy(isOnline = false, lastActive = 0L, onlineUntil = 0L, offlineSince = now)
                    }
                }

                // Active contacts rise to the top; stale leases remain visible but are sorted below.
                val sortedUsers = updatedUsers.sortedWith(
                    compareByDescending<User> { it.role == "moderator" }
                        .thenByDescending { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > now) }
                        .thenByDescending { it.isOnline }
                        .thenByDescending { it.lastActive }
                )
                _usersState.value = sortedUsers

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()
                val filteredList = sortedUsers.filter { user ->
                    !myBlocked.contains(user.uid) && !user.blockedUsers.contains(currentUid)
                }
                _filteredUsersState.value = filteredList
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun localizeIncomingMedia(message: Message, chatId: String): Message {
        fun download(url: String?, suffix: String, deleteAfterSave: Boolean): String? {
            if (url.isNullOrBlank() || !url.startsWith("http")) return url
            return try {
                val extension = url.substringBefore('?').substringAfterLast('.', "bin").take(5)
                val directory = File(getApplication<Application>().filesDir, "received_media/$chatId").apply { mkdirs() }
                val file = File(directory, "${message.messageId}_${suffix}.$extension")
                if (!file.exists() || file.length() == 0L) {
                    val response = OkHttpClient().newCall(Request.Builder().url(url).get().build()).execute()
                    response.use {
                        if (!it.isSuccessful) return url
                        val bytes = it.body?.bytes() ?: return url
                        file.writeBytes(bytes)
                    }
                }
                if (file.exists() && file.length() > 0L) {
                    if (deleteAfterSave) deleteSupabaseObject(url)
                    file.toURI().toString()
                } else url
            } catch (e: Exception) {
                Log.w("MEDIA_CACHE", "Incoming media download failed: ${e.message}")
                url
            }
        }
        val originalVoiceUrl = message.voiceUrl?.takeIf { it.startsWith("http") }
        return message.copy(
            imageUrl = download(message.imageUrl, "image", deleteAfterSave = true),
            voiceUrl = download(message.voiceUrl, "voice", deleteAfterSave = false),
            remoteVoiceUrl = originalVoiceUrl ?: message.remoteVoiceUrl
        )
    }

    fun acknowledgeVoicePlayed(messageId: String, remoteVoiceUrl: String?) {
        if (remoteVoiceUrl.isNullOrBlank() || !remoteVoiceUrl.startsWith("http")) return
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteSupabaseObject(remoteVoiceUrl)) {
                cacheDao.clearRemoteVoiceUrl(messageId)
                _chatMessagesState.value = _chatMessagesState.value.map {
                    if (it.messageId == messageId) it.copy(remoteVoiceUrl = null) else it
                }
            }
        }
    }

    private fun deleteSupabaseObject(publicUrl: String): Boolean {
        val marker = "/storage/v1/object/public/"
        val objectPath = publicUrl.substringAfter(marker, "")
        if (objectPath.isBlank()) return false
        return try {
            val request = Request.Builder()
                .url("https://srfztgcdejfaesrvkarg.supabase.co/storage/v1/object/$objectPath")
                .header("apikey", "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA")
                .header("Authorization", "Bearer sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA")
                .delete()
                .build()
            OkHttpClient().newCall(request).execute().use { it.isSuccessful || it.code == 404 }
        } catch (e: Exception) {
            Log.w("SUPABASE_DELETE", "Object cleanup failed: ${e.message}")
            false
        }
    }

    // Chat Message Streams
    fun startListeningToChat(recipientUid: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        stopListeningToChat()

        val sortedUids = listOf(currentUid, recipientUid).sorted()
        val chatId = "${sortedUids[0]}_${sortedUids[1]}"
        activeChatId = chatId

        // Attach each transient interaction listener exactly once. Chat can be opened through
        // deep links, notifications, or restored navigation, so registration must be idempotent.
        startListeningToTyping(recipientUid)
        startListeningToVoiceRecording(recipientUid)
        startListeningToUpload(recipientUid)

        // Load initial local cache quickly (non-blocking)
        viewModelScope.launch {
            try {
                cacheDao.getMessagesForChat(chatId).firstOrNull()?.let { cached ->
                    if (_chatMessagesState.value.isEmpty()) {
                        _chatMessagesState.value = cached.map { it.toMessage() }
                            .filter { isVisibleMessage(it.expiresAt) }
                    }
                }
            } catch (e: Exception) {
                Log.e("CHAT_CACHE", "Failed to load cached messages: ${e.message}")
            }
        }

        val chatRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")

        activeChatListener = chatRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<Message>()
                    for (childSnapshot in snapshot.children) {
                        val message = try {
                            childSnapshot.getValue(Message::class.java)
                        } catch (e: Exception) {
                            try {
                                Message(
                                    messageId = childSnapshot.child("messageId").value as? String ?: childSnapshot.key ?: "",
                                    senderId = childSnapshot.child("senderId").value as? String ?: "",
                                    senderName = childSnapshot.child("senderName").value as? String ?: "",
                                    senderUsername = childSnapshot.child("senderUsername").value as? String ?: "",
                                    text = childSnapshot.child("text").value as? String ?: "",
                                    timestamp = (childSnapshot.child("timestamp").value as? Long) ?: 0L,
                                    edited = childSnapshot.child("edited").value as? Boolean ?: false,
                                    replyToId = childSnapshot.child("replyToId").value as? String,
                                    replyToText = childSnapshot.child("replyToText").value as? String,
                                    replyToSenderName = childSnapshot.child("replyToSenderName").value as? String,
                                    imageUrl = childSnapshot.child("imageUrl").value as? String,
                                    voiceUrl = childSnapshot.child("voiceUrl").value as? String,
                                    voiceDurationSec = (childSnapshot.child("voiceDurationSec").value as? Long)?.toInt(),
                                    remoteVoiceUrl = childSnapshot.child("remoteVoiceUrl").value as? String,
                                    fileUrl = childSnapshot.child("fileUrl").value as? String,
                                    remoteFileUrl = childSnapshot.child("remoteFileUrl").value as? String,
                                    fileName = childSnapshot.child("fileName").value as? String,
                                    fileMimeType = childSnapshot.child("fileMimeType").value as? String,
                                    fileSize = childSnapshot.child("fileSize").value as? Long,
                                    seenByRecipient = childSnapshot.child("seenByRecipient").value as? Boolean ?: false,
                                    deliveredToRecipient = childSnapshot.child("deliveredToRecipient").value as? Boolean ?: false,
                                    expiresAt = (childSnapshot.child("expiresAt").value as? Long) ?: 0L
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        if (message != null && isVisibleMessage(message.expiresAt)) {
                            messages.add(message)
                        }
                    }
                    val merged = (_chatMessagesState.value + messages)
                        .associateBy { it.messageId }.values.sortedBy { it.timestamp }
                    _chatMessagesState.value = merged

                    // Persist before acknowledgement. Incoming RTDB payload is removed only after
                    // Room confirms the durable local copy; a tiny receipt remains for the sender.
                    viewModelScope.launch(Dispatchers.IO) {
                        val localized = messages.map { remote ->
                            if (remote.senderId != currentUid) localizeIncomingMedia(remote, chatId)
                            else _chatMessagesState.value.find { it.messageId == remote.messageId } ?: remote
                        }
                        cacheDao.insertMessages(localized.map { CachedMessage.fromMessage(it, chatId) })
                        _chatMessagesState.value = (_chatMessagesState.value + localized)
                            .associateBy { it.messageId }.values.sortedBy { it.timestamp }
                        localized.filter { it.senderId != currentUid }.forEach { incoming ->
                            getDatabaseInstance().getReference("delivery_receipts")
                                .child(incoming.senderId).child(chatId).child(incoming.messageId)
                                .setValue(mapOf("seen" to true, "seenAt" to System.currentTimeMillis()))
                                .addOnSuccessListener { chatRef.child(incoming.messageId).removeValue() }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        val receiptRef = getDatabaseInstance().getReference("delivery_receipts").child(currentUid).child(chatId)
        activeReceiptListener = receiptRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { receipt ->
                    val messageId = receipt.key ?: return@forEach
                    val seen = receipt.child("seen").getValue(Boolean::class.java) == true
                    val delivered = seen || receipt.child("delivered").getValue(Boolean::class.java) == true
                    viewModelScope.launch(Dispatchers.IO) {
                        if (seen) cacheDao.markMessageSeen(messageId) else if (delivered) cacheDao.markMessageDelivered(messageId)
                    }
                    _chatMessagesState.value = _chatMessagesState.value.map {
                        if (it.messageId == messageId) it.copy(
                            seenByRecipient = it.seenByRecipient || seen,
                            deliveredToRecipient = it.deliveredToRecipient || delivered
                        ) else it
                    }
                    receipt.ref.removeValue()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun stopListeningToChat() {
        stopListeningToTyping()
        val chatId = activeChatId
        if (chatId != null) {
            activeChatListener?.let {
                getDatabaseInstance().getReference("chats").child(chatId).child("messages").removeEventListener(it)
            }
            activeReceiptListener?.let {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                getDatabaseInstance().getReference("delivery_receipts").child(uid).child(chatId).removeEventListener(it)
            }
        }
        _chatMessagesState.value = emptyList()
        activeChatListener = null
        activeReceiptListener = null
        activeChatId = null
    }

    // Typing Status Handling
    fun setTypingState(isTyping: Boolean) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatId = activeChatId ?: return
        val ref = getDatabaseInstance().getReference("typing").child(chatId).child(currentUid)
        if (isTyping) {
            ref.setValue(mapOf("active" to true, "updatedAt" to ServerValue.TIMESTAMP))
            ref.onDisconnect().removeValue()
        } else {
            ref.removeValue()
        }
    }

    /** Writes a separate, short-lived voice-recording signal so recipients can distinguish
     * a voice note from ordinary typing. The message schema remains unchanged. */
    fun setVoiceRecordingState(isRecording: Boolean) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatId = activeChatId ?: return
        val ref = getDatabaseInstance().getReference("voiceRecording").child(chatId).child(currentUid)
        if (isRecording) {
            ref.setValue(mapOf("active" to true, "updatedAt" to ServerValue.TIMESTAMP))
            ref.onDisconnect().removeValue()
        } else {
            ref.removeValue()
        }
    }

    /** Publishes live attachment progress under an ephemeral RTDB path. */
    fun setFileUploadState(fileName: String, percent: Int, etaSeconds: Long, active: Boolean) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val chatId = activeChatId ?: return
        val ref = getDatabaseInstance().getReference("uploads").child(chatId).child(currentUid)
        if (active) {
            ref.setValue(
                mapOf(
                    "fileName" to fileName.take(120),
                    "percent" to percent.coerceIn(0, 100),
                    "etaSeconds" to etaSeconds.coerceAtLeast(0L),
                    "active" to true
                )
            )
        } else {
            ref.removeValue()
        }
    }

    private fun startListeningToTyping(recipientUid: String) {
        stopListeningToTyping()
        val chatId = activeChatId ?: return
        activeTypingChatId = chatId
        activeTypingRecipientUid = recipientUid
        val typingRef = getDatabaseInstance().getReference("typing")
            .child(chatId)
            .child(recipientUid)

        activeTypingListener = typingRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Ignore late callbacks from a previous chat. Firebase can deliver one final
                // callback after removeEventListener; writing that value into the next chat's
                // Compose state was the main source of the typing-screen crash.
                if (activeTypingChatId != chatId || activeTypingRecipientUid != recipientUid || activeChatId != chatId) return
                val isTyping = runCatching {
                    (snapshot.value as? Boolean) ?: run {
                        val active = snapshot.child("active").value as? Boolean ?: false
                        val updatedAt = (snapshot.child("updatedAt").value as? Number)?.toLong()
                        active && updatedAt != null &&
                            (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L) < 15_000L
                    }
                }.getOrDefault(false)
                val wasTyping = _isRecipientTyping.value
                _isRecipientTyping.value = isTyping
                // Play only on the false -> true edge; repeated RTDB callbacks must not
                // re-enter audio/UI work while Compose is recomposing.
                if (isTyping && !wasTyping) runCatching { playTypingSound() }
            }
            override fun onCancelled(error: DatabaseError) {
                if (activeTypingChatId == chatId && activeTypingRecipientUid == recipientUid) {
                    _isRecipientTyping.value = false
                }
            }
        })
    }

    private fun startListeningToUpload(recipientUid: String) {
        stopListeningToUpload()
        val chatId = activeChatId ?: return
        activeUploadChatId = chatId
        activeUploadRecipientUid = recipientUid
        val uploadRef = getDatabaseInstance().getReference("uploads").child(chatId).child(recipientUid)
        activeUploadListener = uploadRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || snapshot.child("active").getValue(Boolean::class.java) != true) {
                    _recipientUploadState.value = null
                    return
                }
                _recipientUploadState.value = ActiveUploadState(
                    fileName = snapshot.child("fileName").getValue(String::class.java).orEmpty(),
                    percent = snapshot.child("percent").getValue(Int::class.java) ?: 0,
                    etaSeconds = snapshot.child("etaSeconds").getValue(Long::class.java) ?: 0L,
                    active = true
                )
            }
            override fun onCancelled(error: DatabaseError) {
                _recipientUploadState.value = null
            }
        })
    }

    private fun stopListeningToUpload() {
        val chatId = activeUploadChatId
        val recipientUid = activeUploadRecipientUid
        activeUploadListener?.let { listener ->
            if (!chatId.isNullOrBlank() && !recipientUid.isNullOrBlank()) {
                getDatabaseInstance().getReference("uploads").child(chatId).child(recipientUid).removeEventListener(listener)
            }
        }
        activeUploadListener = null
        activeUploadChatId = null
        activeUploadRecipientUid = null
        _recipientUploadState.value = null
    }

    private fun startListeningToVoiceRecording(recipientUid: String) {
        stopListeningToVoiceRecording()
        val chatId = activeChatId ?: return
        activeVoiceRecordingChatId = chatId
        activeVoiceRecordingRecipientUid = recipientUid
        val voiceRef = getDatabaseInstance().getReference("voiceRecording")
            .child(chatId)
            .child(recipientUid)
        activeVoiceRecordingListener = voiceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val active = runCatching {
                    snapshot.getValue(Boolean::class.java)
                        ?: run {
                            val isActive = snapshot.child("active").getValue(Boolean::class.java) == true
                            val updatedAt = snapshot.child("updatedAt").getValue(Long::class.java)
                            isActive && updatedAt != null &&
                                (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L) < 20_000L
                        }
                }.getOrDefault(false)
                _isRecipientVoiceRecording.value = active
            }
            override fun onCancelled(error: DatabaseError) {
                _isRecipientVoiceRecording.value = false
            }
        })
    }

    private fun stopListeningToTyping() {
        val chatId = activeTypingChatId
        val recipientUid = activeTypingRecipientUid
        activeTypingListener?.let { listener ->
            if (!chatId.isNullOrBlank() && !recipientUid.isNullOrBlank()) {
                getDatabaseInstance().getReference("typing")
                    .child(chatId)
                    .child(recipientUid)
                    .removeEventListener(listener)
            }
        }
        activeTypingListener = null
        activeTypingChatId = null
        activeTypingRecipientUid = null
        _isRecipientTyping.value = false
        stopListeningToVoiceRecording()
        stopListeningToUpload()
    }

    private fun stopListeningToVoiceRecording() {
        val chatId = activeVoiceRecordingChatId
        val recipientUid = activeVoiceRecordingRecipientUid
        activeVoiceRecordingListener?.let { listener ->
            if (!chatId.isNullOrBlank() && !recipientUid.isNullOrBlank()) {
                getDatabaseInstance().getReference("voiceRecording")
                    .child(chatId)
                    .child(recipientUid)
                    .removeEventListener(listener)
            }
        }
        activeVoiceRecordingListener = null
        activeVoiceRecordingChatId = null
        activeVoiceRecordingRecipientUid = null
        _isRecipientVoiceRecording.value = false
    }

    private fun startListeningToChatTheme() {
        val chatId = activeChatId ?: return
        activeChatThemeListener?.let {
            getDatabaseInstance().getReference("chat_settings").child(chatId).child("theme").removeEventListener(it)
        }
        val ref = getDatabaseInstance().getReference("chat_settings").child(chatId).child("theme")
        activeChatThemeListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _chatTheme.value = snapshot.getValue(String::class.java) ?: "Aurora"
            }
            override fun onCancelled(error: DatabaseError) { _chatTheme.value = "Aurora" }
        })
    }

    fun updateChatTheme(theme: String) {
        val chatId = activeChatId ?: return
        getDatabaseInstance().getReference("chat_settings").child(chatId).child("theme").setValue(theme)
    }

    // Sound effect players
    fun updateSoundPreferences(notificationSounds: Boolean, typingSounds: Boolean) {
        _notificationSoundsEnabled.value = notificationSounds
        _typingSoundsEnabled.value = typingSounds
        sharedPrefs.edit().putBoolean("notification_sounds", notificationSounds)
            .putBoolean("typing_sounds", typingSounds).apply()
    }

    fun playNotificationSound() {
        if (!_notificationSoundsEnabled.value) return
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.e("SOUND", "Error playing beep tone: ${e.message}")
        }
    }

    fun toggleMuteUser(uid: String) {
        val updated = _mutedUserIds.value.toMutableSet().apply { if (!add(uid)) remove(uid) }.toSet()
        _mutedUserIds.value = updated
        sharedPrefs.edit().putStringSet("muted_users", updated).apply()
    }

    fun playTypingSound() {
        if (!_typingSoundsEnabled.value) return
        try {
            android.media.MediaPlayer.create(getApplication(), R.raw.typing_soft)?.apply {
                setVolume(.35f, .35f)
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.e("SOUND", "Error playing typing sound: ${e.message}")
        }
    }

    // Global in-app incoming message notifications (For chats other than the active screen)
    fun listenForInAppNotifications() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notifRef = getDatabaseInstance().getReference("notifications").child(currentUid)

        globalNotificationListener = notifRef.addValueEventListener(object : ValueEventListener {
            private var isFirstLoad = true
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val senderId = snapshot.child("senderId").value as? String ?: ""
                    val senderName = snapshot.child("senderName").value as? String ?: ""
                    val text = snapshot.child("text").value as? String ?: ""
                    val timestamp = snapshot.child("timestamp").value as? Long ?: 0L

                    // Skip first load or notifications from the current active chat
                    if (!isFirstLoad && senderId.isNotBlank() && senderId != activeRecipientUser.value?.uid) {
                        _inAppNotification.value = InAppNotificationData(senderId, senderName, text, timestamp)
                        playNotificationSound()
                    }
                }
                isFirstLoad = false
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun dismissInAppNotification() {
        _inAppNotification.value = null
    }

    /**
     * Firestore is used only as a delivery queue. Every received item is first persisted
     * in Room and only then acknowledged by deleting the remote document.
     */
    private fun listenToActivityCenter(uid: String) {
        activityNotificationListener?.remove()
        friendRequestListener?.remove()
        notificationCacheJob?.cancel()

        notificationCacheJob = viewModelScope.launch {
            cacheDao.getNotifications(uid).collect { cached ->
                _activityNotifications.value = cached.map { it.toModel() }
            }
        }

        val inbox = FirebaseFirestore.getInstance().collection("users")
            .document(uid).collection("notifications")
        activityNotificationListener = inbox.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            snapshot.documents.forEach { doc ->
                val item = try {
                    ActivityNotification(
                        id = doc.id,
                        ownerId = doc.getString("ownerId") ?: uid,
                        actorId = doc.getString("actorId") ?: "",
                        actorName = doc.getString("actorName") ?: "Someone",
                        actorImageUrl = doc.getString("actorImageUrl") ?: "",
                        type = doc.getString("type") ?: "activity",
                        targetId = doc.getString("targetId") ?: "",
                        text = doc.getString("text") ?: "New activity",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                } catch (_: Exception) { null }
                if (item != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            cacheDao.insertNotification(CachedActivityNotification.fromModel(item))
                            doc.reference.delete()
                        } catch (e: Exception) {
                            Log.e("ACTIVITY_CENTER", "Notification cache failed: ${e.message}")
                        }
                    }
                }
            }
        }

        friendRequestListener = FirebaseFirestore.getInstance().collection("friend_requests")
            .whereEqualTo("receiverId", uid)
            .addSnapshotListener { snapshot, _ ->
                _friendRequests.value = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FriendRequest(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            receiverId = uid,
                            senderName = doc.getString("senderName") ?: "User",
                            senderImageUrl = doc.getString("senderImageUrl") ?: "",
                            status = doc.getString("status") ?: "pending",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (_: Exception) { null }
                }?.filter { it.status == "pending" }?.sortedByDescending { it.timestamp } ?: emptyList()
            }

        sentFriendRequestListener?.remove()
        sentFriendRequestListener = FirebaseFirestore.getInstance().collection("friend_requests")
            .whereEqualTo("senderId", uid)
            .addSnapshotListener { snapshot, _ ->
                _sentFriendRequestIds.value = snapshot?.documents
                    ?.filter { (it.getString("status") ?: "pending") == "pending" }
                    ?.map { it.id }?.toSet() ?: emptySet()
            }

        messageRequestListener?.remove()
        messageRequestListener = FirebaseFirestore.getInstance().collection("message_requests")
            .whereEqualTo("receiverId", uid)
            .addSnapshotListener { snapshot, _ ->
                _messageRequests.value = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        MessageRequest(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            receiverId = uid,
                            senderName = doc.getString("senderName") ?: "User",
                            preview = doc.getString("preview") ?: "Message request",
                            status = doc.getString("status") ?: "pending",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (_: Exception) { null }
                }?.filter { it.status == "pending" }?.sortedByDescending { it.timestamp } ?: emptyList()
            }

        sentMessageRequestListener?.remove()
        sentMessageRequestListener = FirebaseFirestore.getInstance().collection("message_requests")
            .whereEqualTo("senderId", uid)
            .addSnapshotListener { snapshot, _ ->
                _pendingMessageRequestRecipients.value = snapshot?.documents
                    ?.filter { (it.getString("status") ?: "pending") == "pending" }
                    ?.mapNotNull { it.getString("receiverId") }?.toSet() ?: emptySet()
            }
    }

    private fun withUserFcmToken(uid: String, knownToken: String = "", onToken: (String) -> Unit) {
        if (knownToken.isNotBlank()) {
            onToken(knownToken)
            return
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val token = document.getString("fcmToken").orEmpty()
                if (token.isBlank()) {
                    Log.w("FCM_GATEWAY", "Recipient $uid has no FCM token")
                    _gatewayHealth.value = GatewayHealth(message = "Recipient has not registered an FCM token yet")
                } else onToken(token)
            }
            .addOnFailureListener {
                Log.e("FCM_GATEWAY", "Could not load recipient token: ${it.message}")
                _gatewayHealth.value = GatewayHealth(message = "Could not load recipient notification token")
            }
    }

    private fun createActivityNotification(ownerId: String, type: String, targetId: String, text: String) {
        val actor = getCurrentUserOrFallback() ?: return
        if (ownerId.isBlank() || ownerId == actor.uid) return
        val ref = FirebaseFirestore.getInstance().collection("users")
            .document(ownerId).collection("notifications").document()
        ref.set(ActivityNotification(
            id = ref.id,
            ownerId = ownerId,
            actorId = actor.uid,
            actorName = actor.name,
            actorImageUrl = actor.profileImageUrl,
            type = type,
            targetId = targetId,
            text = text,
            timestamp = System.currentTimeMillis()
        )).addOnSuccessListener {
            withUserFcmToken(ownerId) { recipientToken ->
                triggerFcmGatewayNotification(
                    gatewayUrl = _webhookUrl.value,
                    targetToken = recipientToken,
                    senderName = actor.name,
                    messageBody = text,
                    senderId = actor.uid,
                    senderProfileUrl = actor.profileImageUrl,
                    notificationType = type,
                    targetId = targetId
                )
            }
        }.addOnFailureListener { Log.e("ACTIVITY_CENTER", "Delivery failed: ${it.message}") }
    }

    fun requestOpenActivityCenter() { _openActivityCenterSignal.value = System.currentTimeMillis() }

    fun markAllActivityRead() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) { cacheDao.markAllNotificationsRead(uid) }
    }

    fun markActivityRead(id: String) {
        viewModelScope.launch(Dispatchers.IO) { cacheDao.markNotificationRead(id) }
    }

    fun selectProfile(user: User?) { _selectedProfile.value = user }

    fun loadUserById(uid: String, onResult: (User?) -> Unit) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            onResult(null)
            return
        }
        FirebaseFirestore.getInstance().collection("users").document(cleanUid).get()
            .addOnSuccessListener { document ->
                val user = if (document.exists()) {
                    runCatching { document.toObject(User::class.java) }.getOrNull()
                } else null
                onResult(user)
            }
            .addOnFailureListener { error ->
                Log.w("PROFILE_LOOKUP", "Could not load scanned profile: ${error.message}")
                onResult(null)
            }
    }

    fun sendFriendRequest(target: User, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val me = getCurrentUserOrFallback() ?: return onResult(false, "Please sign in again")
        if (target.uid == me.uid || me.friends.contains(target.uid)) return onResult(false, "Already connected")
        val requestId = "${me.uid}_${target.uid}"
        val request = FriendRequest(requestId, me.uid, target.uid, me.name, me.profileImageUrl, "pending", System.currentTimeMillis())
        FirebaseFirestore.getInstance().collection("friend_requests").document(requestId).set(request)
            .addOnSuccessListener {
                createActivityNotification(target.uid, "friend_request", requestId, "sent you a friend request")
                onResult(true, "Friend request sent")
            }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Request failed") }
    }

    fun toggleFollow(target: User) {
        val me = getCurrentUserOrFallback() ?: return
        if (target.uid == me.uid) return
        val following = me.following.contains(target.uid)
        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch()
        batch.update(firestore.collection("users").document(me.uid), "following", if (following) FieldValue.arrayRemove(target.uid) else FieldValue.arrayUnion(target.uid))
        batch.update(firestore.collection("users").document(target.uid), mapOf(
            "followers" to if (following) FieldValue.arrayRemove(me.uid) else FieldValue.arrayUnion(me.uid),
            "followerGrowth" to FieldValue.increment(if (following) -1L else 1L)
        ))
        batch.commit().addOnSuccessListener {
            if (!following) createActivityNotification(target.uid, "new_follower", me.uid, "started following you")
        }
    }

    fun cancelFriendRequest(targetUid: String, onComplete: (Boolean) -> Unit = {}) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return onComplete(false)
        FirebaseFirestore.getInstance().collection("friend_requests").document("${myUid}_${targetUid}")
            .delete().addOnSuccessListener { onComplete(true) }.addOnFailureListener { onComplete(false) }
    }

    fun respondToFriendRequest(request: FriendRequest, accept: Boolean) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        if (accept) {
            val batch = firestore.batch()
            batch.update(firestore.collection("users").document(myUid), "friends", FieldValue.arrayUnion(request.senderId))
            batch.update(firestore.collection("users").document(request.senderId), "friends", FieldValue.arrayUnion(myUid))
            batch.delete(firestore.collection("friend_requests").document(request.id))
            batch.commit().addOnSuccessListener {
                _currentUserState.value = _currentUserState.value?.copy(
                    friends = (_currentUserState.value?.friends.orEmpty() + request.senderId).distinct()
                )
                createActivityNotification(request.senderId, "friend_accepted", myUid, "accepted your friend request")
            }
        } else {
            firestore.collection("friend_requests").document(request.id).delete()
        }
    }

    fun respondToMessageRequest(request: MessageRequest, accept: Boolean) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseFirestore.getInstance().collection("message_requests").document(request.id)
        if (!accept) {
            ref.delete()
            return
        }
        val chatId = listOf(myUid, request.senderId).sorted().joinToString("_")
        val messageRef = getDatabaseInstance().getReference("chats").child(chatId).child("messages")
        val messageId = messageRef.push().key ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = messageId,
            senderId = request.senderId,
            senderName = request.senderName,
            text = request.preview,
            timestamp = request.timestamp
        )
        messageRef.child(messageId).setValue(message).addOnSuccessListener {
            ref.delete()
            createActivityNotification(request.senderId, "message_accepted", myUid, "accepted your message request")
        }
    }

    fun replyToMessageRequest(request: MessageRequest, reply: String, onComplete: (Boolean) -> Unit = {}) {
        val me = getCurrentUserOrFallback() ?: return onComplete(false)
        if (reply.isBlank()) return onComplete(false)
        val chatId = listOf(me.uid, request.senderId).sorted().joinToString("_")
        val messagesRef = getDatabaseInstance().getReference("chats").child(chatId).child("messages")
        val originalId = messagesRef.push().key ?: UUID.randomUUID().toString()
        val replyId = messagesRef.push().key ?: UUID.randomUUID().toString()
        val original = Message(messageId = originalId, senderId = request.senderId, senderName = request.senderName, text = request.preview, timestamp = request.timestamp)
        val response = Message(messageId = replyId, senderId = me.uid, senderName = me.name, senderUsername = me.username, text = reply.trim(), timestamp = System.currentTimeMillis())
        messagesRef.updateChildren(mapOf(originalId to original, replyId to response)).addOnSuccessListener {
            FirebaseFirestore.getInstance().collection("message_requests").document(request.id).delete()
            createActivityNotification(request.senderId, "message_accepted", me.uid, "replied to and accepted your message request")
            withUserFcmToken(request.senderId) { token -> triggerFcmGatewayNotification(_webhookUrl.value, token, me.name, reply.trim(), me.uid, me.profileImageUrl, "message", replyId) }
            onComplete(true)
        }.addOnFailureListener { onComplete(false) }
    }

    fun spotlightStory(story: Story, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val me = getCurrentUserOrFallback() ?: return onResult(false, "Sign in again")
        val active = me.isPremium && (me.premiumPlan == "lifetime" || me.premiumUntil > System.currentTimeMillis())
        if (!active || story.senderId != me.uid) return onResult(false, "Premium is required")
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val usedThisWeek = _storiesState.value.any { it.senderId == me.uid && it.spotlightUntil > weekAgo }
        if (usedThisWeek) return onResult(false, "Weekly spotlight already used")
        val until = System.currentTimeMillis() + 24L * 60 * 60 * 1000
        FirebaseFirestore.getInstance().collection("stories").document(story.id).update("spotlightUntil", until)
            .addOnSuccessListener { onResult(true, "Story spotlighted for 24 hours") }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Spotlight failed") }
    }

    fun recordStoryView(story: Story) {
        val me = getCurrentUserOrFallback() ?: return
        val premiumActive = me.isPremium && (me.premiumPlan == "lifetime" || me.premiumUntil > System.currentTimeMillis())
        val anonymous = premiumActive && sharedPrefs.getBoolean("premium_anonymous_story", false)
        if (story.senderId == me.uid || anonymous) return
        val ref = FirebaseFirestore.getInstance().collection("stories").document(story.id)
        ref.update(mapOf(
            "viewers" to FieldValue.arrayUnion(me.uid),
            "viewCounts.${me.uid}" to FieldValue.increment(1)
        )).addOnSuccessListener {
            if (!story.viewers.contains(me.uid)) createActivityNotification(story.senderId, "story_view", story.id, "viewed your story")
        }
    }

    // Sending, editing, and replying to messages
    private fun ensureDirectChatMembership(chatId: String, firstUid: String, secondUid: String) {
        getDatabaseInstance().getReference("chats").child(chatId).child("members")
            .updateChildren(mapOf(firstUid to true, secondUid to true))
    }

    fun sendMessage(
        recipientUser: User,
        text: String,
        imageUrl: String? = null,
        voiceUrl: String? = null,
        voiceDurationSec: Int? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileSize: Long? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSenderName: String? = null,
        messageIdOverride: String? = null
    ) {
        val currentUser = _currentUserState.value ?: return
        val chatId = activeChatId ?: return
        if (text.isBlank() && imageUrl == null && voiceUrl == null && fileUrl == null) return

        val isEstablishedConversation = currentUser.friends.contains(recipientUser.uid) || _chatMessagesState.value.isNotEmpty()
        if (!isEstablishedConversation) {
            val preview = when {
                text.isNotBlank() -> text.trim()
                imageUrl != null -> "📷 Photo request"
                voiceUrl != null -> "🎙️ Voice request"
                fileUrl != null -> "📎 File request"
                else -> "Message request"
            }
            val requestId = "${currentUser.uid}_${recipientUser.uid}"
            FirebaseFirestore.getInstance().collection("message_requests").document(requestId)
                .set(MessageRequest(requestId, currentUser.uid, recipientUser.uid, currentUser.name, preview, "pending", System.currentTimeMillis()))
                .addOnSuccessListener {
                    createActivityNotification(recipientUser.uid, "message_request", requestId, "sent you a message request")
                }
            return
        }

        ensureDirectChatMembership(chatId, currentUser.uid, recipientUser.uid)
        val chatRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")

        val messageId = messageIdOverride ?: chatRef.push().key ?: UUID.randomUUID().toString()
        val sentAt = System.currentTimeMillis()
        _messageDeliveryStates.update { it + (messageId to "pending") }
        val message = Message(
            messageId = messageId,
            senderId = currentUser.uid,
            senderName = currentUser.name,
            senderUsername = currentUser.username,
            text = text,
            timestamp = sentAt,
            edited = false,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSenderName = replyToSenderName,
            imageUrl = imageUrl,
            voiceUrl = voiceUrl,
            voiceDurationSec = voiceDurationSec,
            fileUrl = fileUrl, remoteFileUrl = fileUrl, fileName = fileName,
            fileMimeType = fileMimeType, fileSize = fileSize,
            deliveredToRecipient = false,
            expiresAt = outgoingExpiryAt(sentAt)
        )

        // Sender owns an immediate durable copy; uploaded media points to the app-private file.
        val localMessage = message.copy(
            imageUrl = imageUrl?.let { localUploadFiles[it] ?: it },
            voiceUrl = voiceUrl?.let { localUploadFiles[it] ?: it },
            fileUrl = fileUrl?.let { localUploadFiles[it] ?: it }
        )
        viewModelScope.launch(Dispatchers.IO) {
            cacheDao.insertMessage(CachedMessage.fromMessage(localMessage, chatId))
        }
        _chatMessagesState.value = (_chatMessagesState.value.filterNot { it.messageId == messageId } + localMessage).sortedBy { it.timestamp }

        chatRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                _messageDeliveryStates.update { it + (messageId to "sent") }
                // Update unread count for recipient
                val unreadRef = getDatabaseInstance().getReference("unread_counts")
                    .child(recipientUser.uid).child(currentUser.uid)
                unreadRef.get().addOnSuccessListener { snapshot ->
                    val currentCount = snapshot.getValue(Int::class.java) ?: 0
                    unreadRef.setValue(currentCount + 1)
                }

                // Instantly notify recipient under /notifications RTDB key
                getDatabaseInstance().getReference("notifications").child(recipientUser.uid)
                    .setValue(mapOf(
                        "senderId" to currentUser.uid,
                        "senderName" to currentUser.name,
                        "text" to if (fileUrl != null) "📎 ${fileName ?: "File"}" else if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                        "timestamp" to System.currentTimeMillis()
                    ))

                // Sender resolves the recipient profile token and posts it directly to the
                // authenticated Worker. The Worker performs no Firestore lookup.
                withUserFcmToken(recipientUser.uid, recipientUser.fcmToken) { recipientToken ->
                    triggerFcmGatewayNotification(
                        gatewayUrl = _webhookUrl.value,
                        targetToken = recipientToken,
                        senderName = currentUser.name,
                        messageBody = if (fileUrl != null) "📎 ${fileName ?: "File"}" else if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                        senderId = currentUser.uid,
                        senderProfileUrl = currentUser.profileImageUrl,
                        notificationType = "message",
                        targetId = messageId
                    )
                }
            }
            .addOnFailureListener { error ->
                _messageDeliveryStates.update { it + (messageId to "failed") }
                Log.w("ChatViewModel", "Message send failed: $messageId", error)
            }
    }

    fun scheduleMessage(
        recipientUser: User,
        text: String,
        triggerAt: Long,
        onScheduled: (String) -> Unit = {}
    ) {
        val currentUser = _currentUserState.value ?: return
        val chatId = activeChatId ?: return
        val cleaned = text.trim().take(10000)
        if (cleaned.isBlank() || triggerAt <= System.currentTimeMillis() + 5_000L) return
        val id = getApplication<Application>().scheduleMessage(
            senderId = currentUser.uid,
            senderName = currentUser.name,
            senderUsername = currentUser.username,
            recipientUid = recipientUser.uid,
            chatId = chatId,
            text = cleaned,
            triggerAt = triggerAt,
            expiresAt = outgoingExpiryAt(triggerAt)
        )
        onScheduled(id)
    }

    fun retryMessage(recipientUser: User, message: Message) {
        sendMessage(
            recipientUser = recipientUser,
            text = message.text,
            imageUrl = message.imageUrl?.takeIf { it.startsWith("http") },
            voiceUrl = (message.remoteVoiceUrl ?: message.voiceUrl)?.takeIf { it.startsWith("http") },
            voiceDurationSec = message.voiceDurationSec,
            fileUrl = (message.remoteFileUrl ?: message.fileUrl)?.takeIf { it.startsWith("http") },
            fileName = message.fileName,
            fileMimeType = message.fileMimeType,
            fileSize = message.fileSize,
            replyToId = message.replyToId,
            replyToText = message.replyToText,
            replyToSenderName = message.replyToSenderName,
            messageIdOverride = message.messageId
        )
    }

    fun forwardMessage(target: User, original: Message) {
        val sender = getCurrentUserOrFallback() ?: return
        val chatId = listOf(sender.uid, target.uid).sorted().joinToString("_")
        val ref = getDatabaseInstance().getReference("chats").child(chatId).child("messages")
        val id = ref.push().key ?: UUID.randomUUID().toString()
        val forwardImage = original.imageUrl?.takeIf { it.startsWith("http") }
        val forwardVoice = (original.remoteVoiceUrl ?: original.voiceUrl)?.takeIf { it.startsWith("http") }
        val text = when {
            original.text.isNotBlank() -> original.text
            forwardImage != null -> "📷 Forwarded photo"
            forwardVoice != null -> "🎙️ Forwarded voice note"
            else -> "Forwarded attachment is only available on the original device"
        }
        val message = Message(
            messageId = id, senderId = sender.uid, senderName = sender.name,
            senderUsername = sender.username, text = text, timestamp = System.currentTimeMillis(),
            imageUrl = forwardImage, voiceUrl = forwardVoice, voiceDurationSec = original.voiceDurationSec,
            deliveredToRecipient = false,
            expiresAt = outgoingExpiryAt()
        )
        ref.child(id).setValue(message).addOnSuccessListener {
            viewModelScope.launch(Dispatchers.IO) { cacheDao.insertMessage(CachedMessage.fromMessage(message, chatId)) }
            withUserFcmToken(target.uid, target.fcmToken) { token ->
                triggerFcmGatewayNotification(
                    gatewayUrl = _webhookUrl.value, targetToken = token,
                    senderName = sender.name, messageBody = "Forwarded: $text",
                    senderId = sender.uid, senderProfileUrl = sender.profileImageUrl,
                    notificationType = "message", targetId = id
                )
            }
        }
    }

    fun sendExternalShareToUser(target: User, payload: IncomingSharePayload, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val me=getCurrentUserOrFallback()?:return onComplete(false,"Sign in again")
        fun write(text:String,fileUrl:String?=null,fileName:String?=null,mime:String?=null,size:Long?=null,imageUrl:String?=null){
            val chatId=listOf(me.uid,target.uid).sorted().joinToString("_");val ref=getDatabaseInstance().getReference("chats").child(chatId).child("messages");val id=ref.push().key?:UUID.randomUUID().toString()
            val msg=Message(messageId=id,senderId=me.uid,senderName=me.name,senderUsername=me.username,text=text,timestamp=System.currentTimeMillis(),imageUrl=imageUrl,fileUrl=fileUrl,remoteFileUrl=fileUrl,fileName=fileName,fileMimeType=mime,fileSize=size,deliveredToRecipient=false,expiresAt=outgoingExpiryAt())
            ref.child(id).setValue(msg).addOnSuccessListener{withUserFcmToken(target.uid,target.fcmToken){token->triggerFcmGatewayNotification(_webhookUrl.value,token,me.name,if(fileUrl!=null||imageUrl!=null)"Shared an attachment" else text,me.uid,me.profileImageUrl,"message",id)};onComplete(true,"Sent to ${target.name}")}.addOnFailureListener{onComplete(false,"Send failed")}
        }
        val uri=payload.uris.firstOrNull()
        if(uri==null){write(payload.text);return}
        val resolver=getApplication<Application>().contentResolver;var name="shared_${System.currentTimeMillis()}";var size=-1L
        resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE),null,null,null)?.use{if(it.moveToFirst()){name=it.getString(0)?:name;if(!it.isNull(1))size=it.getLong(1)}}
        val mime=resolver.getType(uri)?:payload.mimeType
        uploadUriToSupabase(uri,name,mime,size,{_,_->},{url->if(mime.startsWith("image/"))write(payload.text,imageUrl=url)else write(payload.text,url,name,mime,size)},{onComplete(false,it)})
    }

    fun sendPostToUser(target: User, post: Post, onComplete: (Boolean) -> Unit = {}) {
        val me = getCurrentUserOrFallback() ?: return onComplete(false)
        val chatId = listOf(me.uid, target.uid).sorted().joinToString("_")
        val ref = getDatabaseInstance().getReference("chats").child(chatId).child("messages")
        val id = ref.push().key ?: UUID.randomUUID().toString()
        val link = "https://solitary-hill-dcdc.mr44253990.workers.dev/post/${post.id}"
        val message = Message(messageId=id,senderId=me.uid,senderName=me.name,senderUsername=me.username,text="📌 ${post.title.ifBlank { "Shared a post" }}\n${post.text.take(220)}\n$link",timestamp=System.currentTimeMillis(),imageUrl=post.imageUrl.takeIf{it.isNotBlank()},deliveredToRecipient=false,expiresAt=outgoingExpiryAt())
        ref.child(id).setValue(message).addOnSuccessListener { withUserFcmToken(target.uid,target.fcmToken){token->triggerFcmGatewayNotification(_webhookUrl.value,token,me.name,"Shared a Convo post",me.uid,me.profileImageUrl,"message",id)};onComplete(true) }.addOnFailureListener{onComplete(false)}
    }

    fun editMessage(recipientUid: String, messageId: String, newText: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sortedUids = listOf(currentUid, recipientUid).sorted()
        val chatId = "${sortedUids[0]}_${sortedUids[1]}"

        val messageRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)

        val cleanedText = newText.trim().take(10000)
        if (cleanedText.isBlank()) return
        messageRef.get().addOnSuccessListener { snapshot ->
            val previousText = snapshot.child("text").getValue(String::class.java).orEmpty()
            val existingHistory = snapshot.child("editHistory").children.mapNotNull { item ->
                val text = item.child("text").getValue(String::class.java) ?: return@mapNotNull null
                MessageEditRecord(
                    text = text,
                    editedAt = item.child("editedAt").getValue(Long::class.java) ?: 0L,
                    editedBy = item.child("editedBy").getValue(String::class.java).orEmpty()
                )
            }.toMutableList()
            if (previousText.isNotBlank() && previousText != cleanedText) {
                existingHistory += MessageEditRecord(
                    text = previousText,
                    editedAt = System.currentTimeMillis(),
                    editedBy = currentUid
                )
            }
            messageRef.updateChildren(
                mapOf(
                    "text" to cleanedText,
                    "edited" to true,
                    "editHistory" to existingHistory.takeLast(20).map { record ->
                        mapOf("text" to record.text, "editedAt" to record.editedAt, "editedBy" to record.editedBy)
                    }
                )
            )
        }
    }

    fun deleteMessage(recipientUid: String, messageId: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sortedUids = listOf(currentUid, recipientUid).sorted()
        val chatId = "${sortedUids[0]}_${sortedUids[1]}"

        val messageRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
        
        messageRef.removeValue()
    }

    fun addReaction(recipientUid: String, messageId: String, reaction: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sortedUids = listOf(currentUid, recipientUid).sorted()
        val chatId = "${sortedUids[0]}_${sortedUids[1]}"

        val messageRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
            .child("reactions")
            
        messageRef.child(currentUid).setValue(reaction)
    }

    // Profile Settings Customization
    fun updateUserProfile(
        name: String,
        dob: String,
        profileImageUrl: String,
        bio: String? = null,
        coverImageUrl: String? = null,
        coverScale: Float = 1f,
        coverOffsetY: Float = 0f,
        profileHidden: Boolean? = null,
        profileVisibility: String? = null,
        activeProfileType: String? = null,
        pronouns: String? = null,
        professionalTitle: String? = null,
        publicContactEmail: String? = null,
        awayReplyEnabled: Boolean? = null,
        awayReplyText: String? = null,
        awayReplyUntil: Long? = null,
        socialLinks: Map<String, String>? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUid)

        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "dob" to dob,
            "profileImageUrl" to profileImageUrl
        ).apply {
            bio?.let { put("bio", it) }
            coverImageUrl?.let { put("coverImageUrl", it) }
            put("coverScale", coverScale.coerceIn(1f, 2f)); put("coverOffsetY", coverOffsetY.coerceIn(-1f, 1f))
            profileHidden?.let { put("profileHidden", it) }
            profileVisibility?.let { put("profileVisibility", it) }
            activeProfileType?.let { put("activeProfileType", it.take(32)) }
            pronouns?.let { put("pronouns", it.take(40)) }
            professionalTitle?.let { put("professionalTitle", it.take(80)) }
            publicContactEmail?.let { put("publicContactEmail", it.take(160)) }
            awayReplyEnabled?.let { put("awayReplyEnabled", it) }
            awayReplyText?.let { put("awayReplyText", it.take(280)) }
            awayReplyUntil?.let { put("awayReplyUntil", it.coerceAtLeast(0L)) }
            socialLinks?.let { links ->
                put("socialLinks", links.filterValues { value -> value.isNotBlank() }.mapValues { (_, value) -> value.take(240) })
            }
        }

        userRef.update(updates)
            .addOnSuccessListener {
                _currentUserState.value = _currentUserState.value?.copy(
                    name = name,
                    dob = dob,
                    profileImageUrl = profileImageUrl,
                    bio = bio ?: _currentUserState.value?.bio.orEmpty(),
                    coverImageUrl = coverImageUrl ?: _currentUserState.value?.coverImageUrl.orEmpty(),
                    coverScale = coverScale.coerceIn(1f, 2f), coverOffsetY = coverOffsetY.coerceIn(-1f, 1f),
                    profileHidden = profileHidden ?: _currentUserState.value?.profileHidden ?: false,
                    profileVisibility = profileVisibility ?: _currentUserState.value?.profileVisibility.orEmpty().ifBlank { "everyone" },
                    activeProfileType = activeProfileType ?: _currentUserState.value?.activeProfileType.orEmpty().ifBlank { "Personal" },
                    pronouns = pronouns ?: _currentUserState.value?.pronouns.orEmpty(),
                    professionalTitle = professionalTitle ?: _currentUserState.value?.professionalTitle.orEmpty(),
                    publicContactEmail = publicContactEmail ?: _currentUserState.value?.publicContactEmail.orEmpty(),
                    awayReplyEnabled = awayReplyEnabled ?: _currentUserState.value?.awayReplyEnabled ?: false,
                    awayReplyText = awayReplyText ?: _currentUserState.value?.awayReplyText.orEmpty(),
                    awayReplyUntil = awayReplyUntil ?: _currentUserState.value?.awayReplyUntil ?: 0L,
                    socialLinks = socialLinks ?: _currentUserState.value?.socialLinks.orEmpty()
                )
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Failed to update profile")
            }
    }

    // Block/Unblock users
    fun blockUser(targetUid: String, onSuccess: () -> Unit) {
        try {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUid)

            val currentBlocked = _currentUserState.value?.blockedUsers?.toMutableList() ?: mutableListOf()
            if (!currentBlocked.contains(targetUid)) {
                currentBlocked.add(targetUid)
            }

            userRef.update("blockedUsers", currentBlocked)
                .addOnSuccessListener {
                    _currentUserState.value = _currentUserState.value?.copy(blockedUsers = currentBlocked)
                    loadAllUsers()
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e("BLOCK_USER", "Failed to block user: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("BLOCK_USER", "Error blocking user: ${e.message}")
        }
    }

    fun unblockUser(targetUid: String, onSuccess: () -> Unit) {
        try {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUid)

            val currentBlocked = _currentUserState.value?.blockedUsers?.toMutableList() ?: mutableListOf()
            currentBlocked.remove(targetUid)

            userRef.update("blockedUsers", currentBlocked)
                .addOnSuccessListener {
                    _currentUserState.value = _currentUserState.value?.copy(blockedUsers = currentBlocked)
                    loadAllUsers()
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e("UNBLOCK_USER", "Failed to unblock user: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("UNBLOCK_USER", "Error unblocking user: ${e.message}")
        }
    }

    private fun cacheOutgoingMedia(publicUrl: String, fileName: String, bytes: ByteArray) {
        try {
            val directory = File(getApplication<Application>().filesDir, "sent_media").apply { mkdirs() }
            val safeName = fileName.substringAfterLast('/').replace("[^A-Za-z0-9._-]".toRegex(), "_")
            val file = File(directory, safeName)
            file.writeBytes(bytes)
            localUploadFiles[publicUrl] = file.toURI().toString()
        } catch (e: Exception) {
            Log.w("MEDIA_CACHE", "Could not preserve outgoing media: ${e.message}")
        }
    }

    fun uploadMediaToR2(
        bytes: ByteArray,
        contentType: String,
        kind: String,
        extension: String,
        tags: String = "",
        onProgress: (Int, Long) -> Unit = { _, _ -> },
        onSuccess: (R2MediaResult) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onFailure("Please sign in again")
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token ?: return@addOnSuccessListener onFailure("Could not authenticate upload")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val uploadKind = when (kind) { "reel" -> "reel"; "thumbnail" -> "thumbnail"; "update" -> "update"; else -> "post" }
                    val url = _webhookUrl.value.trimEnd('/') + "/media/upload?kind=$uploadKind&extension=$extension"
                    val request = Request.Builder().url(url)
                        .header("Authorization", "Bearer $idToken")
                        .header("X-Media-Tags", tags.take(400).replace("[^A-Za-z0-9,# _-]".toRegex(), ""))
                        .post(ProgressBytesRequestBody(bytes, contentType.toMediaTypeOrNull()) { percent, eta ->
                            viewModelScope.launch(Dispatchers.Main) { onProgress(percent, eta) }
                        })
                        .build()
                    OkHttpClient.Builder().callTimeout(3, TimeUnit.MINUTES).build().newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) return@use withContext(Dispatchers.Main) {
                            val message = runCatching {
                                val errorJson = JSONObject(body)
                                listOf(errorJson.optString("error"), errorJson.optString("details")).filter { it.isNotBlank() }.joinToString(": ")
                            }.getOrDefault("").ifBlank { "R2 upload failed (${response.code})" }
                            onFailure(message)
                        }
                        val json = JSONObject(body)
                        val result = R2MediaResult(json.getString("publicUrl"), json.getString("key"), json.getLong("expiresAt"), json.getString("kind"))
                        withContext(Dispatchers.Main) { onSuccess(result) }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onFailure(e.localizedMessage ?: "R2 upload failed") }
                }
            }
        }.addOnFailureListener { onFailure(it.localizedMessage ?: "Upload authentication failed") }
    }

    fun discardR2Media(key: String) = deleteR2Object(key)

    private fun deleteR2Object(key: String, onComplete: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return onComplete?.invoke() ?: Unit
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val token = tokenResult.token ?: return@addOnSuccessListener onComplete?.invoke() ?: Unit
            val payload = JSONObject().put("key", key).toString()
            val request = Request.Builder().url(_webhookUrl.value.trimEnd('/') + "/media/delete")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull())).build()
            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { onComplete?.invoke() }
                override fun onResponse(call: Call, response: Response) { response.close(); onComplete?.invoke() }
            })
        }.addOnFailureListener { onComplete?.invoke() }
    }

    /** Streams arbitrary attachments; there is no app-imposed size cap. */
    fun uploadUriToSupabase(
        uri: Uri, fileName: String, contentType: String, contentLength: Long,
        onProgress: (Int, Long) -> Unit, onSuccess: (String) -> Unit, onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(180)
                val objectName = "${FirebaseAuth.getInstance().currentUser?.uid.orEmpty()}/${System.currentTimeMillis()}_${UUID.randomUUID()}_$safe"
                val encoded = objectName.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val base = "https://srfztgcdejfaesrvkarg.supabase.co/storage/v1/object"
                val body = ProgressUriRequestBody(getApplication(), uri, contentLength, contentType.toMediaTypeOrNull()) { p, eta ->
                    viewModelScope.launch(Dispatchers.Main) { onProgress(p, eta) }
                }
                val request = Request.Builder().url("$base/chat_images/$encoded")
                    .header("apikey", "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA")
                    .header("Authorization", "Bearer sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA")
                    .post(body).build()
                OkHttpClient.Builder().callTimeout(30, TimeUnit.MINUTES).build().newCall(request).execute().use { response ->
                    val result = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("Upload failed (${response.code}): ${result.take(160)}")
                    val publicUrl = "$base/public/chat_images/$encoded"
                    localUploadFiles[publicUrl] = uri.toString()
                    withContext(Dispatchers.Main) { onSuccess(publicUrl) }
                }
            } catch (e: Throwable) { withContext(Dispatchers.Main) { onFailure(e.localizedMessage ?: "File upload failed") } }
        }
    }

    fun acknowledgeFileConsumed(messageId: String, remoteUrl: String?) {
        if (remoteUrl.isNullOrBlank() || !remoteUrl.startsWith("http")) return
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteSupabaseObject(remoteUrl)) {
                cacheDao.clearRemoteFileUrl(messageId)
                _chatMessagesState.value = _chatMessagesState.value.map { if (it.messageId == messageId) it.copy(remoteFileUrl = null) else it }
            }
        }
    }

    // General file upload helper to Supabase Storage via REST
    fun uploadFileToSupabase(
        bucket: String,
        fileName: String,
        fileBytes: ByteArray,
        contentType: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://srfztgcdejfaesrvkarg.supabase.co/storage/v1/object/$bucket/$fileName"
                val client = OkHttpClient()
                val requestBody = fileBytes.toRequestBody(contentType.toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(url)
                    .header("apikey", "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA")
                    .header("Authorization", "Bearer sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val publicUrl = "https://srfztgcdejfaesrvkarg.supabase.co/storage/v1/object/public/$bucket/$fileName"
                        cacheOutgoingMedia(publicUrl, fileName, fileBytes)
                        withContext(Dispatchers.Main) {
                            onSuccess(publicUrl)
                        }
                    } else {
                        val bodyStr = response.body?.string() ?: ""
                        Log.e("SUPABASE_UPLOAD", "Failed code: ${response.code} body: $bodyStr")
                        // If file already exists, return the public url directly
                        if (response.code == 400 && bodyStr.contains("Duplicate")) {
                            val publicUrl = "https://srfztgcdejfaesrvkarg.supabase.co/storage/v1/object/public/$bucket/$fileName"
                            cacheOutgoingMedia(publicUrl, fileName, fileBytes)
                            withContext(Dispatchers.Main) {
                                onSuccess(publicUrl)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onFailure("Upload failed [${response.code}]: $bodyStr")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SUPABASE_UPLOAD", "Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    onFailure(e.localizedMessage ?: "Unknown network upload error")
                }
            }
        }
    }

    private fun triggerFcmGatewayNotification(
        gatewayUrl: String,
        targetToken: String,
        senderName: String,
        messageBody: String,
        senderId: String,
        senderProfileUrl: String = "",
        notificationType: String = "message",
        targetId: String = "",
        extraData: Map<String, String> = emptyMap()
    ) {
        if (gatewayUrl.isBlank() || !gatewayUrl.startsWith("https://")) {
            Log.w("FCM_GATEWAY", "Direct FCM gateway URL is not configured.")
            return
        }

        val authUser = FirebaseAuth.getInstance().currentUser ?: return
        authUser.getIdToken(false).addOnSuccessListener { tokenResult ->
            val callerToken = tokenResult.token ?: return@addOnSuccessListener
            viewModelScope.launch {
                try {
                    val client = OkHttpClient()
                
                // Formats in 12-hour AM/PM Bangladesh local time
                val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Dhaka")
                }
                val formattedTime = sdf.format(Date())

                val jsonObject = JSONObject().apply {
                    put("token", targetToken)
                    put("title", if (notificationType == "message") "Convo Chat • $senderName" else "$senderName • Convo Chat")
                    put("body", messageBody)
                    put("text", messageBody)
                    put("senderId", senderId)
                    put("senderName", senderName)
                    put("senderProfileUrl", senderProfileUrl)
                    put("notificationType", notificationType)
                    put("targetId", targetId)
                    put("timestamp", System.currentTimeMillis())
                    put("formattedTime", formattedTime)
                    extraData.forEach { (key, value) -> put(key, value) }
                }

                val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(gatewayUrl)
                    .header("Authorization", "Bearer $callerToken")
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("FCM_GATEWAY", "Gateway request failed: ${e.message}")
                        _gatewayHealth.value = GatewayHealth(message = "Notification gateway error: ${e.localizedMessage}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val respBody = response.body?.string() ?: ""
                        Log.d("FCM_GATEWAY", "FCM gateway response [${response.code}]: $respBody")
                        if (response.isSuccessful) {
                            _gatewayHealth.value = _gatewayHealth.value.copy(
                                configured = true,
                                message = if (notificationType == "gateway_test") "Test notification accepted by FCM" else "Direct FCM gateway is ready"
                            )
                        } else {
                            val error = try { JSONObject(respBody).optString("error") } catch (_: Exception) { "HTTP ${response.code}" }
                            _gatewayHealth.value = GatewayHealth(message = "FCM delivery failed: $error")
                        }
                    }
                })
                } catch (e: Exception) {
                    Log.e("FCM_GATEWAY", "Exception during FCM gateway request: ${e.message}")
                }
            }
        }.addOnFailureListener { Log.e("FCM_GATEWAY", "Could not authenticate gateway call: ${it.message}") }
    }

    // --- Dynamic Themes & Network ---

    fun hasActivePremiumEntitlement(): Boolean {
        val user = getCurrentUserOrFallback() ?: return false
        return user.isPremium && (user.premiumPlan == "lifetime" || user.premiumUntil > System.currentTimeMillis())
    }

    fun updateTheme(themeName: String): Boolean {
        val premiumTheme = themeName in setOf("Royal Gold", "Cyber Lime", "Rose Quartz", "Liquid Aurora", "Glass Ocean", "Glass Rose", "Obsidian Neon", "Neon Pulse")
        if (premiumTheme && (!_flagshipConfig.value.advancedThemesEnabled || !hasActivePremiumEntitlement())) return false
        _themeState.value = themeName
        sharedPrefs.edit().putString("app_theme", themeName).apply()
        return true
    }

    fun setNetworkStatus(online: Boolean) {
        _isNetworkAvailable.value = online
        if (online) {
            // Automatically synchronize on reconnection
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                loadCurrentUserProfile(uid)
                loadAllUsers()
                loadStories()
                loadPosts()
                loadGroups()
            }
        } else {
            // Collect from offline caches
            viewModelScope.launch {
                cacheDao.getAllStories().collect { cached ->
                    _storiesState.value = cached.map { it.toStory() }
                }
            }
            viewModelScope.launch {
                cacheDao.getAllPosts().collect { cached ->
                    _postsState.value = cached.map { it.toPost() }
                }
            }
            viewModelScope.launch {
                cacheDao.getAllGroups().collect { cached ->
                    _groupsState.value = cached.map { it.toGroup() }
                }
            }
        }
    }

    // --- Stories Logic ---

    fun loadStories() {
        // Quick initial load from local cache
        viewModelScope.launch {
            try {
                cacheDao.getAllStories().firstOrNull()?.let { cached ->
                    if (_storiesState.value.isEmpty()) {
                        val now = System.currentTimeMillis()
                        _storiesState.value = cached.map { it.toStory() }.filter { now - it.timestamp <= 12 * 60 * 60 * 1000 }
                    }
                }
            } catch (e: Exception) {
                Log.e("FIRESTORE_STORIES", "Cache load failed: ${e.message}")
            }
        }

        FirebaseFirestore.getInstance().collection("stories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_STORIES", "Load failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<Story>()
                    val now = System.currentTimeMillis()
                    for (doc in snapshot.documents) {
                        val story = try {
                            val commentsList = (doc.get("comments") as? List<*>)?.mapNotNull { item ->
                                val map = item as? Map<*, *> ?: return@mapNotNull null
                                StoryComment(
                                    commentId = map["commentId"] as? String ?: "",
                                    senderId = map["senderId"] as? String ?: "",
                                    senderName = map["senderName"] as? String ?: "",
                                    text = map["text"] as? String ?: "",
                                    timestamp = (map["timestamp"] as? Long) ?: 0L
                                )
                            } ?: emptyList()

                            Story(
                                id = doc.id,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "",
                                senderProfilePic = doc.getString("senderProfilePic") ?: "",
                                imageUrl = doc.getString("imageUrl") ?: "",
                                videoUrl = doc.getString("videoUrl") ?: "",
                                text = doc.getString("text") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                reactions = (doc.get("reactions") as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap(),
                                comments = commentsList,
                                viewers = (doc.get("viewers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                viewCounts = (doc.get("viewCounts") as? Map<*, *>)?.mapNotNull { (key, value) -> (value as? Number)?.toInt()?.let { key.toString() to it } }?.toMap() ?: emptyMap(),
                                spotlightUntil = doc.getLong("spotlightUntil") ?: 0L
                            )
                        } catch (e: Exception) {
                            null
                        }

                        if (story != null) {
                            // 12 hours check (12h = 12 * 60 * 60 * 1000 ms)
                            if (now - story.timestamp <= 12 * 60 * 60 * 1000) {
                                list.add(story)
                            } else {
                                // Expired story: remove media storage first, then metadata/cache.
                                viewModelScope.launch(Dispatchers.IO) {
                                    listOf(story.imageUrl, story.videoUrl).filter { it.isNotBlank() }.forEach(::deleteSupabaseObject)
                                    cacheDao.deleteStory(story.id)
                                    FirebaseFirestore.getInstance().collection("stories").document(story.id).delete()
                                }
                            }
                        }
                    }
                    // Sort locally
                    list.sortByDescending { it.timestamp }
                    _storiesState.value = list

                    // Cache to Room Database
                    viewModelScope.launch(Dispatchers.IO) {
                        cacheDao.insertStories(list.map { CachedStory.fromStory(it) })
                    }
                }
            }
    }

    fun uploadStory(text: String, imageUrl: String, videoUrl: String, onComplete: () -> Unit) {
        val user = getCurrentUserOrFallback() ?: return
        val storyId = UUID.randomUUID().toString()
        val story = Story(
            id = storyId,
            senderId = user.uid,
            senderName = user.name,
            senderProfilePic = user.profileImageUrl,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance().collection("stories").document(storyId)
            .set(story)
            .addOnSuccessListener {
                onComplete()
                loadStories()
            }
    }

    fun reactToStory(storyId: String, reactionType: String) {
        val user = getCurrentUserOrFallback() ?: return
        if (reactionType == "💖✨" && (!user.isPremium || (user.premiumPlan != "lifetime" && user.premiumUntil <= System.currentTimeMillis()))) return
        val storyRef = FirebaseFirestore.getInstance().collection("stories").document(storyId)
        storyRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val currentReactions = (doc.get("reactions") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }?.toMutableMap() ?: mutableMapOf()
                if (currentReactions[user.uid] == reactionType) {
                    currentReactions.remove(user.uid) // Toggle reaction off
                } else {
                    currentReactions[user.uid] = reactionType
                }
                storyRef.update("reactions", currentReactions).addOnSuccessListener {
                    if (currentReactions[user.uid] == reactionType) {
                        createActivityNotification(
                            doc.getString("senderId") ?: "", "story_reaction", storyId,
                            "reacted $reactionType to your story"
                        )
                    }
                }
            }
        }
    }

    fun commentOnStory(storyId: String, text: String) {
        val user = getCurrentUserOrFallback() ?: return
        val comment = StoryComment(
            commentId = UUID.randomUUID().toString(),
            senderId = user.uid,
            senderName = user.name,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val storyRef = FirebaseFirestore.getInstance().collection("stories").document(storyId)
        storyRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val commentsList = (doc.get("comments") as? List<*>)?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    StoryComment(
                        commentId = map["commentId"] as? String ?: "",
                        senderId = map["senderId"] as? String ?: "",
                        senderName = map["senderName"] as? String ?: "",
                        text = map["text"] as? String ?: "",
                        timestamp = (map["timestamp"] as? Long) ?: 0L
                    )
                }?.toMutableList() ?: mutableListOf()

                commentsList.add(comment)
                storyRef.update("comments", commentsList).addOnSuccessListener {
                    createActivityNotification(
                        doc.getString("senderId") ?: "", "story_comment", storyId,
                        "commented on your story"
                    )
                }
            }
        }
    }

    fun deleteStory(storyId: String) {
        val ref = FirebaseFirestore.getInstance().collection("stories").document(storyId)
        ref.get().addOnSuccessListener { doc ->
            val media = listOfNotNull(doc.getString("imageUrl"), doc.getString("videoUrl")).filter { it.isNotBlank() }
            viewModelScope.launch(Dispatchers.IO) {
                media.forEach(::deleteSupabaseObject)
                cacheDao.deleteStory(storyId)
                ref.delete().addOnSuccessListener { loadStories() }
            }
        }
    }

    // --- Social Posts Logic ---

    private fun isCurrentAdmin(): Boolean = FirebaseAuth.getInstance().currentUser?.email?.lowercase()?.trim()?.trimEnd('.') == "mr4425390@gmail.com"

    private fun normalizeImportUrl(raw: String): String? = runCatching {
        val value = raw.trim()
        val uri = java.net.URI(value)
        require(uri.scheme.equals("https", ignoreCase = true))
        require(!uri.host.isNullOrBlank())
        value.take(2048)
    }.getOrNull()

    private fun sanitizeOEmbedHtml(raw: String): String {
        val iframe = Regex("(?is)<iframe\\b[^>]*\\bsrc\\s*=\\s*[\\\"'](https://[^\\\"']+)[\\\"'][^>]*>\\s*</iframe>")
            .find(raw)?.let { match ->
                val src = match.groupValues[1].replace("&amp;", "&").replace("\"", "&quot;")
                "<iframe src=\"$src\" width=\"100%\" height=\"100%\" frameborder=\"0\" allowfullscreen></iframe>"
            }
        return iframe.orEmpty()
    }

    private fun discoverOEmbed(sourceUrl: String): JSONObject? {
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val page = Request.Builder().url(sourceUrl).header("User-Agent", "ConvoChat/4.1 oEmbed importer").get().build()
        val html = client.newCall(page).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty().take(1_500_000)
        }
        val href = sequenceOf(
            Regex("(?is)<link[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]+type=[\\\"']application/json\\+oembed[\\\"']"),
            Regex("(?is)<link[^>]+type=[\\\"']application/json\\+oembed[\\\"'][^>]+href=[\\\"']([^\\\"']+)[\\\"']")
        ).mapNotNull { it.find(html)?.groupValues?.getOrNull(1) }.firstOrNull() ?: return null
        val endpoint = java.net.URI(sourceUrl).resolve(href).toString()
        val separator = if (endpoint.contains('?')) '&' else '?'
        val requestUrl = endpoint + separator + "url=" + java.net.URLEncoder.encode(sourceUrl, "UTF-8") + "&format=json&maxwidth=1080&maxheight=1920"
        val request = Request.Builder().url(requestUrl).header("User-Agent", "ConvoChat/4.1 oEmbed importer").get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else JSONObject(response.body?.string().orEmpty())
        }
    }

    fun importAdminReelsFromText(text: String) {
        if (!isCurrentAdmin()) {
            _adminReelImportState.value = AdminReelImportState(message = "Only the configured administrator can import Reels.")
            return
        }
        val urls = text.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .mapNotNull(::normalizeImportUrl)
            .distinct()
            .take(500)
            .toList()
        if (urls.isEmpty()) {
            _adminReelImportState.value = AdminReelImportState(message = "No valid HTTPS links were found.")
            return
        }
        _adminReelImportState.value = AdminReelImportState(importing = true, message = "Reading provider metadata…")
        val admin = getCurrentUserOrFallback() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val records = mutableListOf<Pair<String, Map<String, Any?>>>()
            var skipped = 0
            var failed = 0
            urls.forEachIndexed { index, sourceUrl ->
                runCatching {
                    val metadata = discoverOEmbed(sourceUrl) ?: error("No public oEmbed response")
                    val type = metadata.optString("type", "link")
                    val title = metadata.optString("title").ifBlank { sourceUrl }
                    val embedHtml = sanitizeOEmbedHtml(metadata.optString("html"))
                    val thumbnail = metadata.optString("thumbnail_url").takeIf { it.startsWith("https://") }.orEmpty()
                    val directUrl = metadata.optString("url").takeIf { it.startsWith("https://") && type == "video" }.orEmpty()
                    if (embedHtml.isBlank() && directUrl.isBlank() && thumbnail.isBlank()) error("Provider returned no usable media")
                    val docId = "admin_${sha256(sourceUrl).take(40)}"
                    val data = hashMapOf<String, Any?>(
                        "senderId" to admin.uid,
                        "senderName" to (admin.name.ifBlank { "Convo Admin" }),
                        "senderProfilePic" to admin.profileImageUrl,
                        "text" to title,
                        "title" to title,
                        "imageUrl" to thumbnail,
                        "imageUrls" to listOfNotNull(thumbnail.takeIf { it.isNotBlank() }),
                        "videoUrl" to directUrl,
                        "sourceUrl" to sourceUrl,
                        "embedHtml" to embedHtml,
                        "providerName" to metadata.optString("provider_name", "External provider"),
                        "thumbnailUrl" to thumbnail,
                        "isAdminReel" to true,
                        "isReel" to true,
                        "isPrivate" to false,
                        "timestamp" to System.currentTimeMillis(),
                        "viewsCount" to 0,
                        "reactions" to emptyMap<String, String>(),
                        "comments" to emptyList<Map<String, Any>>(),
                        "tags" to listOf("admin", "imported", "reel")
                    )
                    records += docId to data
                }.onFailure { error ->
                    if (error.message?.contains("No public", true) == true || error.message?.contains("no usable", true) == true) skipped++ else failed++
                }
                withContext(Dispatchers.Main) { _adminReelImportState.value = _adminReelImportState.value.copy(processed = index + 1, skipped = skipped, failed = failed, message = "Reading ${index + 1}/${urls.size}…") }
            }
            withContext(Dispatchers.Main) {
                commitImportedAdminReels(records, 0, skipped, failed)
            }
        }
    }

    private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun commitImportedAdminReels(records: List<Pair<String, Map<String, Any?>>>, index: Int, skipped: Int, failed: Int) {
        if (index >= records.size) {
            _adminReelImportState.value = _adminReelImportState.value.copy(importing = false, imported = records.size, skipped = skipped, failed = failed, message = "Imported ${records.size} Reel link(s)." )
            loadPosts()
            return
        }
        val end = minOf(index + 450, records.size)
        val batch = FirebaseFirestore.getInstance().batch()
        records.subList(index, end).forEach { (id, data) ->
            batch.set(FirebaseFirestore.getInstance().collection("posts").document(id), data, SetOptions.merge())
        }
        batch.commit().addOnSuccessListener {
            _adminReelImportState.value = _adminReelImportState.value.copy(imported = end, message = "Saved $end/${records.size} Reel link(s)…")
            commitImportedAdminReels(records, end, skipped, failed)
        }.addOnFailureListener { error ->
            _adminReelImportState.value = _adminReelImportState.value.copy(importing = false, skipped = skipped, failed = failed + (end - index), message = "Import failed: ${error.localizedMessage ?: "database error"}")
        }
    }

    fun loadPosts() {
        // Quick initial load from local cache
        viewModelScope.launch {
            try {
                cacheDao.getAllPosts().firstOrNull()?.let { cached ->
                    if (_postsState.value.isEmpty()) {
                        _postsState.value = cached.map { it.toPost() }
                    }
                }
            } catch (e: Exception) {
                Log.e("FIRESTORE_POSTS", "Cache load failed: ${e.message}")
            }
        }

        FirebaseFirestore.getInstance().collection("posts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_POSTS", "Load failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<Post>()
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()

                    for (doc in snapshot.documents) {
                        try {
                            val commentsList = (doc.get("comments") as? List<*>)?.mapNotNull { item ->
                                val map = item as? Map<*, *> ?: return@mapNotNull null
                                PostComment(
                                    commentId = map["commentId"] as? String ?: "",
                                    senderId = map["senderId"] as? String ?: "",
                                    senderName = map["senderName"] as? String ?: "",
                                    text = map["text"] as? String ?: "",
                                    timestamp = (map["timestamp"] as? Long) ?: 0L,
                                    reactions = (map["reactions"] as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap(),
                                    replyToId = map["replyToId"] as? String ?: "",
                                    replyToName = map["replyToName"] as? String ?: ""
                                )
                            } ?: emptyList()

                            val post = Post(
                                id = doc.id,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "",
                                senderProfilePic = doc.getString("senderProfilePic") ?: "",
                                text = doc.getString("text") ?: "",
                                imageUrl = doc.getString("imageUrl") ?: "",
                                audioUrl = doc.getString("audioUrl") ?: "",
                                videoUrl = doc.getString("videoUrl") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                reactions = (doc.get("reactions") as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap(),
                                comments = commentsList,
                                viewsCount = doc.getLong("viewsCount")?.toInt() ?: 0,
                                isPrivate = doc.getBoolean("isPrivate") ?: false,
                                title = doc.getString("title") ?: "",
                                tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                taggedUserIds = (doc.get("taggedUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                feeling = doc.getString("feeling") ?: "",
                                backgroundStyle = doc.getString("backgroundStyle") ?: "glass",
                                textAnimation = doc.getString("textAnimation") ?: "none",
                                r2ObjectKeys = (doc.get("r2ObjectKeys") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                isReel = doc.getBoolean("isReel") ?: false,
                                expiresAt = doc.getLong("expiresAt") ?: 0L,
                                imageUrls = (doc.get("imageUrls") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                mediaReactions = (doc.get("mediaReactions") as? Map<*, *>)?.entries?.associate { entry ->
                                    entry.key.toString() to ((entry.value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap())
                                } ?: emptyMap(),
                                sourceUrl = doc.getString("sourceUrl") ?: "",
                                embedHtml = doc.getString("embedHtml") ?: "",
                                providerName = doc.getString("providerName") ?: "",
                                thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                                isAdminReel = doc.getBoolean("isAdminReel") ?: false
                            )

                            if (post.expiresAt > 0L && System.currentTimeMillis() >= post.expiresAt) {
                                post.r2ObjectKeys.forEach(::deleteR2Object)
                                viewModelScope.launch(Dispatchers.IO) { cacheDao.deletePost(post.id) }
                                doc.reference.delete()
                            } else if (!myBlocked.contains(post.senderId)) {
                                if (!post.isPrivate || post.senderId == currentUid) list.add(post)
                            }
                        } catch (e: Exception) {
                            Log.e("POST_PARSE", "Error parsing post: ${e.message}")
                        }
                    }
                    // Sort locally
                    list.sortByDescending { it.timestamp }
                    _postsState.value = list

                    // Cache to Room Database
                    viewModelScope.launch(Dispatchers.IO) {
                        cacheDao.insertPosts(list.map { CachedPost.fromPost(it) })
                    }
                }
            }
    }

    fun createPost(
        text: String,
        imageUrl: String,
        audioUrl: String,
        videoUrl: String,
        isPrivate: Boolean,
        onComplete: (String) -> Unit,
        title: String = "",
        tags: List<String> = emptyList(),
        taggedUserIds: List<String> = emptyList(),
        feeling: String = "",
        backgroundStyle: String = "glass",
        textAnimation: String = "none",
        r2ObjectKeys: List<String> = emptyList(),
        isReel: Boolean = false,
        expiresAt: Long = 0L,
        imageUrls: List<String> = emptyList()
    ) {
        val user = getCurrentUserOrFallback() ?: return
        val postId = UUID.randomUUID().toString()
        val post = Post(
            id = postId,
            senderId = user.uid,
            senderName = user.name,
            senderProfilePic = user.profileImageUrl,
            text = text,
            imageUrl = imageUrl,
            audioUrl = "",
            videoUrl = videoUrl,
            timestamp = System.currentTimeMillis(),
            isPrivate = isPrivate,
            title = title,
            tags = tags,
            taggedUserIds = taggedUserIds,
            feeling = feeling,
            backgroundStyle = backgroundStyle,
            textAnimation = textAnimation,
            r2ObjectKeys = r2ObjectKeys,
            isReel = isReel,
            expiresAt = expiresAt,
            imageUrls = imageUrls.ifEmpty { imageUrl.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty() }
        )

        // Webhook shouldn't be called according to instruction ("কোন পোস্ট করলে রিকোয়েস্ট যাবে না")
        FirebaseFirestore.getInstance().collection("posts").document(postId)
            .set(post)
            .addOnSuccessListener {
                taggedUserIds.forEach { taggedUid ->
                    createActivityNotification(taggedUid, "post_tag", postId, "tagged you in a post${if (title.isNotBlank()) ": $title" else ""}")
                }
                if (isReel) user.friends.forEach { friendUid ->
                    createActivityNotification(friendUid, "new_reel", postId, "published a new reel")
                }
                onComplete(postId)
                loadPosts()
            }
    }

    fun editPost(postId: String, text: String, isPrivate: Boolean, title: String, tags: List<String>, onComplete: () -> Unit) {
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.update(mapOf("text" to text, "title" to title, "tags" to tags, "isPrivate" to isPrivate, "editedAt" to System.currentTimeMillis()))
            .addOnSuccessListener {
                onComplete()
                loadPosts()
            }
    }

    fun deletePost(postId: String) {
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.get().addOnSuccessListener { document ->
            val r2Keys = (document.get("r2ObjectKeys") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val legacyMediaUrls = listOfNotNull(
                document.getString("imageUrl"), document.getString("videoUrl"), document.getString("audioUrl")
            ).filter { it.isNotBlank() }
            r2Keys.forEach(::deleteR2Object)
            viewModelScope.launch(Dispatchers.IO) {
                if (r2Keys.isEmpty()) legacyMediaUrls.forEach { url ->
                    deleteSupabaseObject(url)
                    localUploadFiles.remove(url)?.let { localUri ->
                        try { File(java.net.URI(localUri)).delete() } catch (_: Exception) {}
                    }
                }
                cacheDao.deletePost(postId)
                postRef.delete().addOnSuccessListener { loadPosts() }
                    .addOnFailureListener { Log.e("POST_DELETE", "Firestore delete failed: ${it.message}") }
            }
        }.addOnFailureListener { Log.e("POST_DELETE", "Could not load post metadata: ${it.message}") }
    }

    fun reactToPost(postId: String, reactionType: String) {
        val user = getCurrentUserOrFallback() ?: return
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val currentReactions = (doc.get("reactions") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }?.toMutableMap() ?: mutableMapOf()
                if (currentReactions[user.uid] == reactionType) {
                    currentReactions.remove(user.uid)
                } else {
                    currentReactions[user.uid] = reactionType
                }
                postRef.update("reactions", currentReactions).addOnSuccessListener {
                    if (currentReactions[user.uid] == reactionType) {
                        createActivityNotification(
                            doc.getString("senderId") ?: "", "post_reaction", postId,
                            "reacted $reactionType to your post"
                        )
                    }
                }
            }
        }
    }

    fun reactToPostMedia(postId: String, mediaKey: String, reactionType: String = "❤️") {
        val user = getCurrentUserOrFallback() ?: return
        val ref = FirebaseFirestore.getInstance().collection("posts").document(postId)
        ref.get().addOnSuccessListener { doc ->
            val root = (doc.get("mediaReactions") as? Map<*, *>)?.entries?.associate { entry ->
                entry.key.toString() to ((entry.value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }?.toMutableMap() ?: mutableMapOf())
            }?.toMutableMap() ?: mutableMapOf()
            val reactions = root[mediaKey]?.toMutableMap() ?: mutableMapOf()
            if (reactions[user.uid] == reactionType) reactions.remove(user.uid) else reactions[user.uid] = reactionType
            root[mediaKey] = reactions
            ref.update("mediaReactions", root).addOnSuccessListener {
                if (reactions[user.uid] == reactionType) createActivityNotification(doc.getString("senderId") ?: "", "media_reaction", postId, "liked a photo in your post")
            }
        }
    }

    fun commentOnPost(postId: String, text: String, replyToId: String = "", replyToName: String = "") {
        val user = getCurrentUserOrFallback() ?: return
        val comment = PostComment(
            commentId = UUID.randomUUID().toString(), senderId = user.uid, senderName = user.name,
            text = text, timestamp = System.currentTimeMillis(), replyToId = replyToId, replyToName = replyToName
        )
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val commentsList = (doc.get("comments") as? List<*>)?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    PostComment(
                        commentId = map["commentId"] as? String ?: "", senderId = map["senderId"] as? String ?: "",
                        senderName = map["senderName"] as? String ?: "", text = map["text"] as? String ?: "",
                        timestamp = (map["timestamp"] as? Long) ?: 0L,
                        reactions = (map["reactions"] as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap(),
                        replyToId = map["replyToId"] as? String ?: "", replyToName = map["replyToName"] as? String ?: ""
                    )
                }?.toMutableList() ?: mutableListOf()

                commentsList.add(comment)
                postRef.update("comments", commentsList).addOnSuccessListener {
                    createActivityNotification(
                        doc.getString("senderId") ?: "", "post_comment", postId,
                        "commented on your post"
                    )
                }
            }
        }
    }

    fun reactToPostComment(postId: String, commentId: String, reaction: String = "❤️") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseFirestore.getInstance().collection("posts").document(postId)
        FirebaseFirestore.getInstance().runTransaction { tx ->
            val doc = tx.get(ref)
            val comments = (doc.get("comments") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { entry -> entry.key.toString() to entry.value }?.toMutableMap() }?.toMutableList() ?: mutableListOf()
            val index = comments.indexOfFirst { it["commentId"] == commentId }; if (index < 0) return@runTransaction false
            val reactions = (comments[index]["reactions"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }?.toMutableMap() ?: mutableMapOf()
            if (reactions[uid] == reaction) reactions.remove(uid) else reactions[uid] = reaction
            comments[index]["reactions"] = reactions; tx.update(ref, "comments", comments); true
        }
    }

    fun incrementPostViews(postId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
            FirebaseFirestore.getInstance().runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                val viewers = (snapshot.get("viewerIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                if (uid in viewers) return@runTransaction false
                transaction.update(postRef, mapOf("viewsCount" to ((snapshot.getLong("viewsCount") ?: 0L) + 1L), "viewerIds" to (viewers + uid).distinct().takeLast(50_000)))
                true
            }.addOnFailureListener { e -> Log.e("POSTS_VIEWS", "Failed unique view: ${e.message}") }
        } catch (e: Exception) { Log.e("POSTS_VIEWS", "Error incrementing views: ${e.message}") }
    }

    // --- Group Chats Logic ---

    fun loadGroups() {
        // Quick initial load from local cache
        viewModelScope.launch {
            try {
                cacheDao.getAllGroups().firstOrNull()?.let { cached ->
                    if (_groupsState.value.isEmpty()) {
                        _groupsState.value = cached.map { it.toGroup() }
                    }
                }
            } catch (e: Exception) {
                Log.e("FIRESTORE_GROUPS", "Cache load failed: ${e.message}")
            }
        }

        FirebaseFirestore.getInstance().collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_GROUPS", "Load groups failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<Group>()
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                    for (doc in snapshot.documents) {
                        try {
                            val members = (doc.get("members") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            if (members.contains(currentUid)) {
                                val group = Group(
                                    id = doc.id,
                                    name = doc.getString("name") ?: "",
                                    profileUrl = doc.getString("profileUrl") ?: "",
                                    members = members,
                                    createdAt = doc.getLong("createdAt") ?: 0L,
                                    lastMessage = doc.getString("lastMessage") ?: "",
                                    createdBy = doc.getString("createdBy") ?: ""
                                )
                                list.add(group)
                            }
                        } catch (e: Exception) {}
                    }
                    // Sort locally
                    list.sortByDescending { it.createdAt }
                    _groupsState.value = list

                    // Cache to local db
                    viewModelScope.launch(Dispatchers.IO) {
                        cacheDao.insertGroups(list.map { CachedGroup.fromGroup(it) })
                    }
                }
            }
    }

    fun createGroup(name: String, profileUrl: String, members: List<String>, onComplete: (Group) -> Unit) {
        val user = getCurrentUserOrFallback() ?: return
        val groupId = UUID.randomUUID().toString()
        val allMembers = (members + user.uid).distinct()
        
        val group = Group(
            id = groupId,
            name = name,
            profileUrl = profileUrl,
            members = allMembers,
            createdAt = System.currentTimeMillis(),
            lastMessage = "Group created by ${user.name}",
            createdBy = user.uid
        )

        // Webhook shouldn't be called for groups according to instruction ("গ্রুপের ভিতর কোন ওয়েব যাবে না")
        FirebaseFirestore.getInstance().collection("groups").document(groupId)
            .set(group)
            .addOnSuccessListener {
                onComplete(group)
                loadGroups()

                // Add initial system action log message (fire-and-forget)
                val actionMessageId = UUID.randomUUID().toString()
                val systemMsg = GroupMessage(
                    messageId = actionMessageId,
                    groupId = groupId,
                    senderId = "system",
                    senderName = "SYSTEM",
                    text = "🔊 ${user.name} created the group \"$name\"",
                    timestamp = System.currentTimeMillis()
                )
                FirebaseFirestore.getInstance().collection("groups").document(groupId)
                    .collection("messages").document(actionMessageId).set(systemMsg)
            }
    }

    fun addGroupMembers(group: Group, memberIds: List<String>, onComplete: (Boolean) -> Unit = {}) {
        val actor = getCurrentUserOrFallback() ?: return onComplete(false)
        val newMembers = (group.members + memberIds).distinct()
        val ref = FirebaseFirestore.getInstance().collection("groups").document(group.id)
        ref.update("members", newMembers).addOnSuccessListener {
            memberIds.forEach { uid ->
                createActivityNotification(uid, "group_added", group.id, "added you to ${group.name}")
            }
            val messageId = UUID.randomUUID().toString()
            ref.collection("messages").document(messageId).set(
                GroupMessage(
                    messageId = messageId,
                    groupId = group.id,
                    senderId = "system",
                    senderName = "SYSTEM",
                    text = "${actor.name} added ${memberIds.size} member(s)",
                    timestamp = System.currentTimeMillis()
                )
            )
            _activeGroup.value = group.copy(members = newMembers)
            onComplete(true)
        }.addOnFailureListener { onComplete(false) }
    }

    /**
     * Creates the signaling room and sends invitations without mounting the host's
     * WebRTC screen. The host joins later from the notification, matching invitees.
     */
    fun createNotificationOnlyGroupCall(group: Group, video: Boolean, onComplete: (Boolean) -> Unit = {}) {
        val caller = getCurrentUserOrFallback()
        if (caller == null || caller.uid !in group.members) {
            onComplete(false)
            return
        }
        val roomId = UUID.randomUUID().toString()
        val members = group.members.distinct().take(6)
        if (members.isEmpty()) {
            onComplete(false)
            return
        }
        val now = System.currentTimeMillis()
        val roomData = mapOf(
            "roomId" to roomId,
            "groupId" to group.id,
            "groupName" to group.name,
            "hostId" to caller.uid,
            "status" to "active",
            "video" to video,
            "memberIds" to members.associateWith { true },
            "createdAt" to now,
            "expiresAt" to now + 60 * 60 * 1000L
        )
        getDatabaseInstance().getReference("groupCalls").child(roomId).setValue(roomData)
            .addOnSuccessListener {
                inviteGroupMembersToCall(group, video, roomId, includeCaller = true)
                onComplete(true)
            }
            .addOnFailureListener {
                Log.e("GROUP_CALL", "Could not create notification-only room", it)
                onComplete(false)
            }
    }

    fun inviteGroupMembersToCall(
        group: Group,
        video: Boolean,
        roomId: String = "",
        includeCaller: Boolean = false
    ) {
        val caller = getCurrentUserOrFallback() ?: return
        if (caller.uid !in group.members || roomId.isBlank()) return
        val inviteKey = "${caller.uid}:${group.id}:$roomId"
        if (!dispatchedGroupCallRooms.add(inviteKey)) {
            Log.d("GROUP_CALL", "Skipping duplicate invite dispatch for $inviteKey")
            return
        }
        val callType = if (video) "video" else "audio"
        val roster = group.members.distinct().take(6).joinToString(",")
        val recipients = if (includeCaller) {
            group.members.distinct().take(6)
        } else {
            group.members.filterNot { it == caller.uid }.distinct().take(5)
        }
        recipients.forEach { memberUid ->
            withUserFcmToken(memberUid) { token ->
                triggerFcmGatewayNotification(
                    gatewayUrl = _webhookUrl.value,
                    targetToken = token,
                    senderName = caller.name,
                    messageBody = "${caller.name} started a group $callType call in ${group.name}",
                    senderId = caller.uid,
                    senderProfileUrl = caller.profileImageUrl,
                    notificationType = "group_call",
                    targetId = group.id,
                    extraData = mapOf(
                        "roomId" to roomId,
                        "groupId" to group.id,
                        "groupName" to group.name,
                        "memberIds" to roster,
                        "videoCall" to video.toString()
                    )
                )
            }
        }
    }

    fun selectGroup(group: Group?) {
        stopListeningToGroupVoice()
        _activeGroup.value = group
        if (group != null) {
            startListeningToGroupMessages(group.id)
            startListeningToGroupVoice(group.id)
        } else {
            _groupMessagesState.value = emptyList()
            _groupVoiceRecorders.value = emptySet()
        }
    }

    fun setGroupVoiceRecordingState(groupId: String, isRecording: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = getDatabaseInstance().getReference("groupVoiceRecording").child(groupId).child(uid)
        if (isRecording) ref.setValue(true) else ref.removeValue()
    }

    private fun startListeningToGroupVoice(groupId: String) {
        val ref = getDatabaseInstance().getReference("groupVoiceRecording").child(groupId)
        activeGroupVoiceId = groupId
        activeGroupVoiceListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _groupVoiceRecorders.value = snapshot.children.filter { it.getValue(Boolean::class.java) == true }.map { it.key.orEmpty() }.toSet()
            }
            override fun onCancelled(error: DatabaseError) { _groupVoiceRecorders.value = emptySet() }
        })
    }

    private fun stopListeningToGroupVoice() {
        val groupId = activeGroupVoiceId
        activeGroupVoiceListener?.let { listener ->
            if (!groupId.isNullOrBlank()) getDatabaseInstance().getReference("groupVoiceRecording").child(groupId).removeEventListener(listener)
        }
        activeGroupVoiceListener = null
        activeGroupVoiceId = null
        _groupVoiceRecorders.value = emptySet()
    }

    private fun startListeningToGroupMessages(groupId: String) {
        // Quick initial load from local cache
        viewModelScope.launch {
            try {
                cacheDao.getGroupMessages(groupId).firstOrNull()?.let { cached ->
                    if (_groupMessagesState.value.isEmpty()) {
                                                    _groupMessagesState.value = cached.map { it.toGroupMessage() }
                                .filter { isVisibleMessage(it.expiresAt) }

                    }
                }
            } catch (e: Exception) {
                Log.e("FIRESTORE_GROUP_MSGS", "Cache load failed: ${e.message}")
            }
        }

        FirebaseFirestore.getInstance().collection("groups").document(groupId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_GROUP_MSGS", "Listen failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = mutableListOf<GroupMessage>()
                    for (doc in snapshot.documents) {
                        try {
                            val msg = GroupMessage(
                                messageId = doc.id,
                                groupId = groupId,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "",
                                text = doc.getString("text") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                imageUrl = doc.getString("imageUrl"),
                                voiceUrl = doc.getString("voiceUrl"),
                                voiceDurationSec = doc.getLong("voiceDurationSec")?.toInt(),
                                expiresAt = doc.getLong("expiresAt") ?: 0L
                            )
                            if (isVisibleMessage(msg.expiresAt)) messages.add(msg)
                        } catch (e: Exception) {}
                    }
                    // Sort locally
                    messages.sortBy { it.timestamp }
                    _groupMessagesState.value = messages

                    // Cache locally
                    viewModelScope.launch(Dispatchers.IO) {
                        cacheDao.insertGroupMessages(messages.map { CachedGroupMessage.fromGroupMessage(it) })
                    }
                }
            }
    }

    fun sendGroupMessage(groupId: String, text: String, imageUrl: String? = null, voiceUrl: String? = null, voiceDurationSec: Int? = null) {
        val user = getCurrentUserOrFallback() ?: return
        val messageId = UUID.randomUUID().toString()
        val sentAt = System.currentTimeMillis()
        val msg = GroupMessage(
            messageId = messageId,
            groupId = groupId,
            senderId = user.uid,
            senderName = user.name,
            text = text,
            timestamp = sentAt,
            expiresAt = outgoingExpiryAt(sentAt),
            imageUrl = imageUrl,
            voiceUrl = voiceUrl,
            voiceDurationSec = voiceDurationSec
        )

        // Webhooks strictly disabled for groups ("গ্রুপের ভিতর লগ এড করা যাবে এখানেও কোন ওয়েব যাবে না")
        val groupRef = FirebaseFirestore.getInstance().collection("groups").document(groupId)
        groupRef.collection("messages").document(messageId).set(msg)
            .addOnSuccessListener {
                val lastMsgText = if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text
                groupRef.update("lastMessage", "${user.name}: $lastMsgText")
                groupRef.get().addOnSuccessListener { groupDoc ->
                    val members = (groupDoc.get("members") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
                    members.filter { it != user.uid }.forEach { uid ->
                        createActivityNotification(uid, "group_message", groupId, "sent a message in ${groupDoc.getString("name") ?: "your group"}")
                    }
                }
            }
    }

    fun deleteGroupMessage(groupId: String, messageId: String) {
        val groupRef = FirebaseFirestore.getInstance().collection("groups").document(groupId)
        groupRef.collection("messages").document(messageId).delete()
    }

    override fun onCleared() {
        val database = runCatching { getDatabaseInstance() }.getOrNull()
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val chatId = activeChatId

        stopListeningToTyping()
        stopListeningToGroupVoice()
        activeChatListener?.let { listener -> if (database != null && chatId != null) database.getReference("chats").child(chatId).child("messages").removeEventListener(listener) }
        activeReceiptListener?.let { listener -> if (database != null && chatId != null && currentUid != null) database.getReference("delivery_receipts").child(currentUid).child(chatId).removeEventListener(listener) }
        activeChatThemeListener?.let { listener -> if (database != null && chatId != null) database.getReference("chat_settings").child(chatId).child("theme").removeEventListener(listener) }
        globalNotificationListener?.let { listener -> if (database != null && currentUid != null) database.getReference("notifications").child(currentUid).removeEventListener(listener) }
        flagshipRtdbListener?.let { listener -> flagshipRtdbRef?.removeEventListener(listener) }

        activityNotificationListener?.remove()
        currentUserProfileListener?.remove()
        flagshipListener?.remove()
        featureRequestListener?.remove()
        premiumRequestListener?.remove()
        friendRequestListener?.remove()
        sentFriendRequestListener?.remove()
        messageRequestListener?.remove()
        sentMessageRequestListener?.remove()
        notificationCacheJob?.cancel()
        conversationIdsJob?.cancel()

        networkCallback?.let { callback ->
            val connectivity = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivity != null) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        super.onCleared()
    }

    fun getLastMessageFlow(otherUserUid: String): Flow<Message?> {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return flowOf(null)
        val sorted = listOf(currentUid, otherUserUid).sorted()
        val chatId = "${sorted[0]}_${sorted[1]}"
        return cacheDao.getMessagesForChat(chatId).map { cachedList ->
            cachedList.lastOrNull()?.toMessage()
        }
    }
}

// Global network check helper function
private fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val activeNet = cm.activeNetwork ?: return true
        val capabilities = cm.getNetworkCapabilities(activeNet) ?: return true
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true
    }
}
