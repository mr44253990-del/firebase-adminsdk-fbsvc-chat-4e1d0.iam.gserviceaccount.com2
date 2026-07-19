package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
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
    val version: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)
    private val localUploadFiles = ConcurrentHashMap<String, String>()

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

    // Network tracking (defaults to true for maximum compatibility on emulators)
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

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

    private val _unreadCountsState = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsState: StateFlow<Map<String, Int>> = _unreadCountsState.asStateFlow()

    private val _isRecipientTyping = MutableStateFlow(false)
    val isRecipientTyping: StateFlow<Boolean> = _isRecipientTyping.asStateFlow()

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

    private val _selectedProfile = MutableStateFlow<User?>(null)
    val selectedProfile: StateFlow<User?> = _selectedProfile.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(true)
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    private val _currentTabState = MutableStateFlow(0)
    val currentTabState: StateFlow<Int> = _currentTabState.asStateFlow()

    fun setCurrentTab(tab: Int) {
        _currentTabState.value = tab
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
    private var activeChatThemeListener: ValueEventListener? = null
    private var activeReceiptListener: ValueEventListener? = null
    private var globalNotificationListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null
    private var activityNotificationListener: ListenerRegistration? = null
    private var friendRequestListener: ListenerRegistration? = null
    private var sentFriendRequestListener: ListenerRegistration? = null
    private var messageRequestListener: ListenerRegistration? = null
    private var notificationCacheJob: kotlinx.coroutines.Job? = null

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
        registerNetworkCallback(application)
    }

    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        setNetworkStatus(true)
                    }
                    override fun onLost(network: Network) {
                        setNetworkStatus(false)
                    }
                })
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
            startListeningToTyping(user.uid)
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
                            version = json.optString("version")
                        )
                    }
                }
            })
    }

    fun sendAdminTestNotification() {
        val admin = getCurrentUserOrFallback() ?: run {
            _gatewayHealth.value = GatewayHealth(message = "Admin session is not authenticated")
            return
        }
        withUserFcmToken(admin.uid, admin.fcmToken) { token ->
            triggerFcmGatewayNotification(
                gatewayUrl = _webhookUrl.value,
                targetToken = token,
                senderName = "FireChat Diagnostics",
                messageBody = "Direct FCM gateway test completed successfully",
                senderId = admin.uid,
                senderProfileUrl = admin.profileImageUrl,
                notificationType = "gateway_test",
                targetId = "test_${System.currentTimeMillis()}"
            )
        }
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
                        retrieveFCMTokenAndStore(uid)
                        loadCurrentUserProfile(uid)
                        setupPresence(uid)
                        loadAllUsers()
                        listenToAllPresences()
                        listenForInAppNotifications()
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
                FirebaseFirestore.getInstance().collection("users").document(authUser.uid)
                    .set(mapOf(
                        "uid" to profile.uid,
                        "name" to profile.name,
                        "username" to profile.username,
                        "profileImageUrl" to profile.profileImageUrl,
                        "createdAt" to profile.createdAt
                    ), SetOptions.merge())
                    .addOnCompleteListener {
                        retrieveFCMTokenAndStore(authUser.uid)
                        loadCurrentUserProfile(authUser.uid)
                        setupPresence(authUser.uid)
                        loadAllUsers()
                        listenToAllPresences()
                        listenForInAppNotifications()
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

                        val newUser = User(
                            uid = uid,
                            name = name,
                            dob = dob,
                            username = generatedUsername,
                            profileImageUrl = profileImageUrl,
                            createdAt = System.currentTimeMillis()
                        )

                        FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
                            .set(newUser)
                            .addOnSuccessListener {
                                retrieveFCMTokenAndStore(uid)
                                _currentUserState.value = newUser
                                setupPresence(uid)
                                loadAllUsers()
                                listenToAllPresences()
                                listenForInAppNotifications()
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
        FirebaseFirestore.getInstance().collection("users")
            .document(uid)
            .get()
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
                    _currentUserState.value = user
                    listenToActivityCenter(uid)
                    
                    // Identify admin by user email
                    val auth = FirebaseAuth.getInstance()
                    _userIsAdmin.value = auth.currentUser?.email == "mr4425390@gmail.com"
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
                        if (user != null && user.uid != currentUid) {
                            allUsersList.add(user)
                        }
                    }
                    _usersState.value = allUsersList

                    val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()
                    val filteredList = allUsersList.filter { user ->
                        !myBlocked.contains(user.uid) && !user.blockedUsers.contains(currentUid)
                    }
                    _filteredUsersState.value = filteredList
                }
            }
    }

    fun searchUsers(query: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()
        val allowedUsers = _usersState.value.filter { user ->
            !myBlocked.contains(user.uid) && !user.blockedUsers.contains(currentUid)
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

    // Online Presence handling
    private fun setupPresence(uid: String) {
        val database = getDatabaseInstance()
        val statusRef = database.getReference("status").child(uid)
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    statusRef.setValue(mapOf("isOnline" to true, "lastActive" to System.currentTimeMillis()))
                    statusRef.onDisconnect().setValue(mapOf("isOnline" to false, "lastActive" to System.currentTimeMillis()))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToAllPresences() {
        val statusRef = getDatabaseInstance().getReference("status")
        presenceListener = statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val statusMap = mutableMapOf<String, Pair<Boolean, Long>>()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val isOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false
                    val lastActive = child.child("lastActive").getValue(Long::class.java) ?: 0L
                    statusMap[uid] = Pair(isOnline, lastActive)
                }

                val updatedUsers = _usersState.value.map { user ->
                    val status = statusMap[user.uid]
                    if (status != null) {
                        user.copy(isOnline = status.first, lastActive = status.second)
                    } else {
                        user.copy(isOnline = false, lastActive = 0L)
                    }
                }

                // Sort active (online) users at the very top, sorted by activity time descending
                val sortedUsers = updatedUsers.sortedWith(
                    compareByDescending<User> { it.isOnline }.thenByDescending { it.lastActive }
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
        fun download(url: String?, suffix: String): String? {
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
                    deleteSupabaseObject(url)
                    file.toURI().toString()
                } else url
            } catch (e: Exception) {
                Log.w("MEDIA_CACHE", "Incoming media download failed: ${e.message}")
                url
            }
        }
        return message.copy(
            imageUrl = download(message.imageUrl, "image"),
            voiceUrl = download(message.voiceUrl, "voice")
        )
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

        // Load initial local cache quickly (non-blocking)
        viewModelScope.launch {
            try {
                cacheDao.getMessagesForChat(chatId).firstOrNull()?.let { cached ->
                    if (_chatMessagesState.value.isEmpty()) {
                        _chatMessagesState.value = cached.map { it.toMessage() }
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
                                    seenByRecipient = childSnapshot.child("seenByRecipient").value as? Boolean ?: false,
                                    deliveredToRecipient = childSnapshot.child("deliveredToRecipient").value as? Boolean ?: false
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        if (message != null) {
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
                    viewModelScope.launch(Dispatchers.IO) { cacheDao.markMessageSeen(messageId) }
                    _chatMessagesState.value = _chatMessagesState.value.map {
                        if (it.messageId == messageId) it.copy(seenByRecipient = true, deliveredToRecipient = true) else it
                    }
                    receipt.ref.removeValue()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun stopListeningToChat() {
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

        getDatabaseInstance().getReference("typing")
            .child(chatId)
            .child(currentUid)
            .setValue(isTyping)
    }

    private fun startListeningToTyping(recipientUid: String) {
        val chatId = activeChatId ?: return
        val typingRef = getDatabaseInstance().getReference("typing")
            .child(chatId)
            .child(recipientUid)

        activeTypingListener = typingRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = snapshot.getValue(Boolean::class.java) ?: false
                _isRecipientTyping.value = isTyping
                if (isTyping) {
                    playTypingSound()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun stopListeningToTyping() {
        val chatId = activeChatId ?: return
        val recipientUid = activeRecipientUser.value?.uid ?: return
        val listener = activeTypingListener ?: return

        getDatabaseInstance().getReference("typing")
            .child(chatId)
            .child(recipientUid)
            .removeEventListener(listener)
        activeTypingListener = null
        _isRecipientTyping.value = false
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

    fun playTypingSound() {
        if (!_typingSoundsEnabled.value) return
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_SYSTEM, 35)
            toneG.startTone(ToneGenerator.TONE_PROP_PROMPT, 50)
        } catch (e: Exception) {
            Log.e("SOUND", "Error playing typing click: ${e.message}")
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

    fun recordStoryView(story: Story) {
        val me = getCurrentUserOrFallback() ?: return
        if (story.senderId == me.uid || story.viewers.contains(me.uid)) return
        val ref = FirebaseFirestore.getInstance().collection("stories").document(story.id)
        ref.update("viewers", FieldValue.arrayUnion(me.uid)).addOnSuccessListener {
            createActivityNotification(story.senderId, "story_view", story.id, "viewed your story")
        }
    }

    // Sending, editing, and replying to messages
    fun sendMessage(
        recipientUser: User,
        text: String,
        imageUrl: String? = null,
        voiceUrl: String? = null,
        voiceDurationSec: Int? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSenderName: String? = null
    ) {
        val currentUser = _currentUserState.value ?: return
        val chatId = activeChatId ?: return
        if (text.isBlank() && imageUrl == null && voiceUrl == null) return

        val isEstablishedConversation = currentUser.friends.contains(recipientUser.uid) || _chatMessagesState.value.isNotEmpty()
        if (!isEstablishedConversation) {
            val preview = when {
                text.isNotBlank() -> text.trim()
                imageUrl != null -> "📷 Photo request"
                voiceUrl != null -> "🎙️ Voice request"
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

        val chatRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")

        val messageId = chatRef.push().key ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = messageId,
            senderId = currentUser.uid,
            senderName = currentUser.name,
            senderUsername = currentUser.username,
            text = text,
            timestamp = System.currentTimeMillis(),
            edited = false,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSenderName = replyToSenderName,
            imageUrl = imageUrl,
            voiceUrl = voiceUrl,
            voiceDurationSec = voiceDurationSec,
            deliveredToRecipient = recipientUser.isOnline
        )

        // Sender owns an immediate durable copy; uploaded media points to the app-private file.
        val localMessage = message.copy(
            imageUrl = imageUrl?.let { localUploadFiles[it] ?: it },
            voiceUrl = voiceUrl?.let { localUploadFiles[it] ?: it }
        )
        viewModelScope.launch(Dispatchers.IO) {
            cacheDao.insertMessage(CachedMessage.fromMessage(localMessage, chatId))
        }
        _chatMessagesState.value = (_chatMessagesState.value + localMessage).distinctBy { it.messageId }.sortedBy { it.timestamp }

        chatRef.child(messageId).setValue(message)
            .addOnSuccessListener {
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
                        "text" to if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                        "timestamp" to System.currentTimeMillis()
                    ))

                // Sender resolves the recipient profile token and posts it directly to the
                // authenticated Worker. The Worker performs no Firestore lookup.
                withUserFcmToken(recipientUser.uid, recipientUser.fcmToken) { recipientToken ->
                    triggerFcmGatewayNotification(
                        gatewayUrl = _webhookUrl.value,
                        targetToken = recipientToken,
                        senderName = currentUser.name,
                        messageBody = if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                        senderId = currentUser.uid,
                        senderProfileUrl = currentUser.profileImageUrl,
                        notificationType = "message",
                        targetId = messageId
                    )
                }
            }
    }

    fun editMessage(recipientUid: String, messageId: String, newText: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sortedUids = listOf(currentUid, recipientUid).sorted()
        val chatId = "${sortedUids[0]}_${sortedUids[1]}"

        val messageRef = getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)

        messageRef.child("text").setValue(newText)
        messageRef.child("edited").setValue(true)
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
        }

        userRef.update(updates)
            .addOnSuccessListener {
                _currentUserState.value = _currentUserState.value?.copy(
                    name = name,
                    dob = dob,
                    profileImageUrl = profileImageUrl,
                    bio = bio ?: _currentUserState.value?.bio.orEmpty(),
                    coverImageUrl = coverImageUrl ?: _currentUserState.value?.coverImageUrl.orEmpty()
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
        targetId: String = ""
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
                    put("title", if (notificationType == "message") "New message from $senderName" else "$senderName • FireChat")
                    put("body", messageBody)
                    put("text", messageBody)
                    put("senderId", senderId)
                    put("senderName", senderName)
                    put("senderProfileUrl", senderProfileUrl)
                    put("notificationType", notificationType)
                    put("targetId", targetId)
                    put("timestamp", System.currentTimeMillis())
                    put("formattedTime", formattedTime)
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

    fun updateTheme(themeName: String) {
        _themeState.value = themeName
        sharedPrefs.edit().putString("app_theme", themeName).apply()
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
                                viewers = (doc.get("viewers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            )
                        } catch (e: Exception) {
                            null
                        }

                        if (story != null) {
                            // 12 hours check (12h = 12 * 60 * 60 * 1000 ms)
                            if (now - story.timestamp <= 12 * 60 * 60 * 1000) {
                                list.add(story)
                            } else {
                                // Expired story: Auto-remove from DB to clean up
                                FirebaseFirestore.getInstance().collection("stories").document(story.id).delete()
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
        FirebaseFirestore.getInstance().collection("stories").document(storyId).delete()
            .addOnSuccessListener {
                viewModelScope.launch(Dispatchers.IO) {
                    cacheDao.deleteStory(storyId)
                }
                loadStories()
            }
    }

    // --- Social Posts Logic ---

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
                                    timestamp = (map["timestamp"] as? Long) ?: 0L
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
                                feeling = doc.getString("feeling") ?: ""
                            )

                            // Apply privacy filters & blocked lists filters
                            if (!myBlocked.contains(post.senderId)) {
                                if (!post.isPrivate || post.senderId == currentUid) {
                                    list.add(post)
                                }
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
        onComplete: () -> Unit,
        title: String = "",
        tags: List<String> = emptyList(),
        taggedUserIds: List<String> = emptyList(),
        feeling: String = ""
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
            audioUrl = audioUrl,
            videoUrl = videoUrl,
            timestamp = System.currentTimeMillis(),
            isPrivate = isPrivate,
            title = title,
            tags = tags,
            taggedUserIds = taggedUserIds,
            feeling = feeling
        )

        // Webhook shouldn't be called according to instruction ("কোন পোস্ট করলে রিকোয়েস্ট যাবে না")
        FirebaseFirestore.getInstance().collection("posts").document(postId)
            .set(post)
            .addOnSuccessListener {
                taggedUserIds.forEach { taggedUid ->
                    createActivityNotification(taggedUid, "post_tag", postId, "tagged you in a post${if (title.isNotBlank()) ": $title" else ""}")
                }
                onComplete()
                loadPosts()
            }
    }

    fun editPost(postId: String, text: String, isPrivate: Boolean, onComplete: () -> Unit) {
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.update(mapOf("text" to text, "isPrivate" to isPrivate))
            .addOnSuccessListener {
                onComplete()
                loadPosts()
            }
    }

    fun deletePost(postId: String) {
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.get().addOnSuccessListener { document ->
            val mediaUrls = listOfNotNull(
                document.getString("imageUrl"),
                document.getString("videoUrl"),
                document.getString("audioUrl")
            ).filter { it.isNotBlank() }
            viewModelScope.launch(Dispatchers.IO) {
                mediaUrls.forEach { url ->
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

    fun commentOnPost(postId: String, text: String) {
        val user = getCurrentUserOrFallback() ?: return
        val comment = PostComment(
            commentId = UUID.randomUUID().toString(),
            senderId = user.uid,
            senderName = user.name,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
        postRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val commentsList = (doc.get("comments") as? List<*>)?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    PostComment(
                        commentId = map["commentId"] as? String ?: "",
                        senderId = map["senderId"] as? String ?: "",
                        senderName = map["senderName"] as? String ?: "",
                        text = map["text"] as? String ?: "",
                        timestamp = (map["timestamp"] as? Long) ?: 0L
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

    fun incrementPostViews(postId: String) {
        try {
            val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
            FirebaseFirestore.getInstance().runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                val currentViews = snapshot.getLong("viewsCount") ?: 0L
                transaction.update(postRef, "viewsCount", currentViews + 1)
            }.addOnSuccessListener {
                Log.d("POSTS_VIEWS", "Post $postId views incremented.")
            }.addOnFailureListener { e ->
                Log.e("POSTS_VIEWS", "Failed to increment view: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("POSTS_VIEWS", "Error incrementing views: ${e.message}")
        }
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

    fun selectGroup(group: Group?) {
        _activeGroup.value = group
        if (group != null) {
            startListeningToGroupMessages(group.id)
        } else {
            _groupMessagesState.value = emptyList()
        }
    }

    private fun startListeningToGroupMessages(groupId: String) {
        // Quick initial load from local cache
        viewModelScope.launch {
            try {
                cacheDao.getGroupMessages(groupId).firstOrNull()?.let { cached ->
                    if (_groupMessagesState.value.isEmpty()) {
                        _groupMessagesState.value = cached.map { it.toGroupMessage() }
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
                                voiceDurationSec = doc.getLong("voiceDurationSec")?.toInt()
                            )
                            messages.add(msg)
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
        val msg = GroupMessage(
            messageId = messageId,
            groupId = groupId,
            senderId = user.uid,
            senderName = user.name,
            text = text,
            timestamp = System.currentTimeMillis(),
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
