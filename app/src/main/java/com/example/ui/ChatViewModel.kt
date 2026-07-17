package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Conversation
import com.example.data.Group
import com.example.data.Message
import com.example.data.Presence
import com.example.data.PresenceManager
import com.example.data.SupabaseManager
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)
    private val http = OkHttpClient()

    // ---------------- State ----------------
    private val _currentUserState = MutableStateFlow<User?>(null)
    val currentUserState: StateFlow<User?> = _currentUserState.asStateFlow()

    private val _activeRecipientUser = MutableStateFlow<User?>(null)
    val activeRecipientUser: StateFlow<User?> = _activeRecipientUser.asStateFlow()

    private val _activeGroup = MutableStateFlow<Group?>(null)
    val activeGroup: StateFlow<Group?> = _activeGroup.asStateFlow()

    fun selectRecipient(user: User?) { _activeRecipientUser.value = user }
    fun selectGroup(group: Group?) { _activeGroup.value = group }

    private val _usersState = MutableStateFlow<List<User>>(emptyList())
    val usersState: StateFlow<List<User>> = _usersState.asStateFlow()

    private val _filteredUsersState = MutableStateFlow<List<User>>(emptyList())
    val filteredUsersState: StateFlow<List<User>> = _filteredUsersState.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<List<Message>>(emptyList())
    val chatMessagesState: StateFlow<List<Message>> = _chatMessagesState.asStateFlow()

    private val _conversationsState = MutableStateFlow<List<Conversation>>(emptyList())
    val conversationsState: StateFlow<List<Conversation>> = _conversationsState.asStateFlow()

    private val _groupsState = MutableStateFlow<List<Group>>(emptyList())
    val groupsState: StateFlow<List<Group>> = _groupsState.asStateFlow()

    private val _totalUnread = MutableStateFlow(0)
    val totalUnread: StateFlow<Int> = _totalUnread.asStateFlow()

    private val _partnerTyping = MutableStateFlow(false)
    val partnerTyping: StateFlow<Boolean> = _partnerTyping.asStateFlow()

    private val _presenceMap = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val presenceMap: StateFlow<Map<String, Presence>> = _presenceMap.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(true)
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    private val defaultWebhookUrl = "https://rakibul.n8n-host.com/webhook-test/1b4faabd-9b19-4f49-8740-454fb0924161"

    private val _webhookUrl = MutableStateFlow(sharedPrefs.getString("webhook_url", defaultWebhookUrl).orEmpty().ifBlank { defaultWebhookUrl })
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private var activeChatListener: ValueEventListener? = null
    private var typingListener: ValueEventListener? = null
    private var activeChatId: String? = null
    private var conversationsListener: ValueEventListener? = null
    private var unreadListener: ValueEventListener? = null
    private var presenceListeners = mutableMapOf<String, ValueEventListener?>()
    private var unreadMap = mutableMapOf<String, Int>()
    private var directConversations = listOf<Conversation>()

    // ---------------- Helpers ----------------
    private fun db(): FirebaseDatabase {
        return try {
            FirebaseDatabase.getInstance("https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    private fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    private fun myUid(): String? = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }

    fun chatIdFor(otherUid: String): String {
        val me = myUid() ?: return ""
        val sorted = listOf(me, otherUid).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    /** Read compressed bytes from a content Uri (avatars / chat images / voice). */
    private suspend fun readBytes(uri: Uri, maxDim: Int = 1280, quality: Int = 82): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                val mime = cr.getType(uri) ?: ""
                if (mime.startsWith("video") || uri.toString().endsWith(".m4a")) {
                    cr.openInputStream(uri)?.use { it.readBytes() }
                } else {
                    val stream = cr.openInputStream(uri) ?: return@withContext null
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream) ?: return@withContext null
                    val scale = if (maxOf(bmp.width, bmp.height) > maxDim) {
                        maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                    } else 1f
                    val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                        bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                    ) else bmp
                    val out = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
            } catch (e: Exception) {
                Log.e("VM", "readBytes failed: ${e.message}")
                null
            }
        }

    suspend fun readRawBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null }
    }

    // ---------------- Theme ----------------
    fun getSavedTheme(): String = sharedPrefs.getString("app_theme", "aurora") ?: "aurora"

    fun saveTheme(themeId: String) {
        sharedPrefs.edit().putString("app_theme", themeId).apply()
        _currentUserState.value = _currentUserState.value?.copy(theme = themeId)
        val uid = myUid() ?: return
        try {
            firestore().collection("users").document(uid)
                .set(mapOf("theme" to themeId), SetOptions.merge())
        } catch (e: Exception) { Log.e("VM", "saveTheme: ${e.message}") }
    }

    // ---------------- Init / config ----------------
    private fun listenToGlobalConfig() {
        try {
            val docRef = firestore().collection("config").document("app_settings")
            docRef.addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val url = snapshot.getString("webhookUrl") ?: snapshot.getString("workerUrl")
                    if (!url.isNullOrBlank()) {
                        _webhookUrl.value = url
                        sharedPrefs.edit().putString("webhook_url", url).apply()
                    } else {
                        docRef.set(mapOf("webhookUrl" to defaultWebhookUrl, "workerUrl" to defaultWebhookUrl))
                    }
                } else if (snapshot != null) {
                    docRef.set(mapOf("webhookUrl" to defaultWebhookUrl, "workerUrl" to defaultWebhookUrl))
                }
            }
        } catch (e: Exception) { Log.e("VM", "config: ${e.message}") }
    }

    init { checkFirebaseConfiguration() }

    fun updateWebhookUrl(url: String) {
        _webhookUrl.value = url
        sharedPrefs.edit().putString("webhook_url", url).apply()
        try {
            firestore().collection("config").document("app_settings")
                .set(mapOf("webhookUrl" to url, "workerUrl" to url))
        } catch (e: Exception) { Log.e("VM", "updateWebhook: ${e.message}") }
    }

    private fun checkFirebaseConfiguration() {
        try {
            val auth = FirebaseAuth.getInstance()
            listenToGlobalConfig()
            if (auth.currentUser != null) {
                loadCurrentUserProfile(auth.currentUser!!.uid)
                loadAllUsers()
                startConversationsListener()
                startGroupsListener()
            }
        } catch (e: Exception) {
            _isFirebaseConfigured.value = false
        }
    }

    // ---------------- Auth ----------------
    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) { _authError.value = "Please fill in all fields"; return }
        _authLoading.value = true
        _authError.value = null
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: ""
                    retrieveFCMTokenAndStore(uid)
                    loadCurrentUserProfile(uid)
                    loadAllUsers()
                    startConversationsListener()
                    startGroupsListener()
                    PresenceManager.goOnline(uid, _currentUserState.value?.activityStatusEnabled ?: true)
                    _authLoading.value = false
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    _authError.value = e.localizedMessage ?: "Login failed"
                    _authLoading.value = false
                }
        } catch (e: Exception) {
            _authError.value = e.localizedMessage ?: "Login error"
            _authLoading.value = false
        }
    }

    /** Signup with optional profile photo -> uploaded to Supabase Storage. */
    fun signup(email: String, name: String, dob: String, password: String, photoUri: Uri?, onSuccess: () -> Unit) {
        if (email.isBlank() || name.isBlank() || dob.isBlank() || password.isBlank()) {
            _authError.value = "Please fill in all fields"; return
        }
        if (password.length < 6) { _authError.value = "Password must be at least 6 characters"; return }
        _authLoading.value = true
        _authError.value = null
        try {
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: ""
                    val cleanName = name.lowercase().replace("\\s".toRegex(), "")
                    val generatedUsername = "${cleanName}_${(1000..9999).random()}"
                    viewModelScope.launch {
                        var photoUrl = ""
                        if (photoUri != null) {
                            val bytes = readBytes(photoUri, maxDim = 720, quality = 85)
                            if (bytes != null) {
                                photoUrl = SupabaseManager.upload(
                                    SupabaseManager.BUCKET_AVATARS, "$uid.jpg", bytes
                                ) ?: ""
                            }
                        }
                        val newUser = User(
                            uid = uid, name = name, dob = dob,
                            username = generatedUsername, photoUrl = photoUrl,
                            theme = getSavedTheme(),
                            createdAt = System.currentTimeMillis()
                        )
                        try {
                            firestore().collection("users").document(uid).set(newUser)
                                .addOnSuccessListener {
                                    _currentUserState.value = newUser
                                    retrieveFCMTokenAndStore(uid)
                                    loadAllUsers()
                                    startConversationsListener()
                                    startGroupsListener()
                                    PresenceManager.goOnline(uid, true)
                                    _authLoading.value = false
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    _authError.value = e.localizedMessage
                                    _authLoading.value = false
                                }
                        } catch (e: Exception) {
                            _authError.value = e.localizedMessage
                            _authLoading.value = false
                        }
                    }
                }
                .addOnFailureListener { e ->
                    _authError.value = e.localizedMessage ?: "Signup failed"
                    _authLoading.value = false
                }
        } catch (e: Exception) {
            _authError.value = e.localizedMessage ?: "Signup error"
            _authLoading.value = false
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (email.isBlank()) { onFailure("Please enter your email address"); return }
        try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Failed to send reset link") }
        } catch (e: Exception) { onFailure(e.localizedMessage ?: "Error") }
    }

    fun logout(onSuccess: () -> Unit) {
        try {
            PresenceManager.goOffline()
            FirebaseAuth.getInstance().signOut()
            _currentUserState.value = null
            _usersState.value = emptyList()
            _filteredUsersState.value = emptyList()
            _chatMessagesState.value = emptyList()
            _conversationsState.value = emptyList()
            _totalUnread.value = 0
            activeChatId = null
            onSuccess()
        } catch (e: Exception) { Log.e("Auth", "Logout failed: ${e.message}") }
    }

    private fun retrieveFCMTokenAndStore(uid: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val token = task.result
                firestore().collection("users").document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        _currentUserState.value = _currentUserState.value?.copy(fcmToken = token)
                    }
            }
        } catch (e: Exception) { Log.e("FCM", "token: ${e.message}") }
    }

    // ---------------- Profile ----------------
    private fun loadCurrentUserProfile(uid: String) {
        try {
            firestore().collection("users").document(uid)
                .addSnapshotListener { document, _ ->
                    if (document != null && document.exists()) {
                        val user = parseUser(document)
                        _currentUserState.value = user
                        if (user != null) {
                            sharedPrefs.edit().putString("app_theme", user.theme).apply()
                            applyUserFilters()
                        }
                    }
                }
        } catch (e: Exception) { Log.e("VM", "profile: ${e.message}") }
    }

    private fun parseUser(doc: com.google.firebase.firestore.DocumentSnapshot): User? {
        return try {
            User(
                uid = doc.getString("uid") ?: doc.id,
                name = doc.getString("name") ?: "",
                dob = doc.getString("dob") ?: "",
                username = doc.getString("username") ?: "",
                fcmToken = doc.getString("fcmToken") ?: "",
                photoUrl = doc.getString("photoUrl") ?: "",
                bio = doc.getString("bio") ?: "",
                theme = doc.getString("theme") ?: "aurora",
                activityStatusEnabled = doc.getBoolean("activityStatusEnabled") ?: true,
                blockedUsers = (doc.get("blockedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) { null }
    }

    /** Edit profile: name, bio and/or new avatar (Supabase). */
    fun updateProfile(name: String, bio: String, photoUri: Uri?, onDone: (Boolean) -> Unit) {
        val uid = myUid() ?: return
        viewModelScope.launch {
            _uploading.value = true
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "bio" to bio
            )
            if (photoUri != null) {
                val bytes = readBytes(photoUri, maxDim = 720, quality = 85)
                if (bytes != null) {
                    val url = SupabaseManager.upload(SupabaseManager.BUCKET_AVATARS, "$uid.jpg", bytes)
                    if (url != null) updates["photoUrl"] = "$url?t=${System.currentTimeMillis()}"
                }
            }
            try {
                firestore().collection("users").document(uid)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener { _uploading.value = false; onDone(true) }
                    .addOnFailureListener { _uploading.value = false; onDone(false) }
            } catch (e: Exception) { _uploading.value = false; onDone(false) }
        }
    }

    fun setActivityStatus(enabled: Boolean) {
        val uid = myUid() ?: return
        _currentUserState.value = _currentUserState.value?.copy(activityStatusEnabled = enabled)
        PresenceManager.setSharing(enabled)
        try {
            firestore().collection("users").document(uid)
                .set(mapOf("activityStatusEnabled" to enabled), SetOptions.merge())
        } catch (e: Exception) { Log.e("VM", "activityStatus: ${e.message}") }
    }

    // ---------------- Users / search / block ----------------
    private fun loadAllUsers() {
        val currentUid = myUid() ?: return
        try {
            firestore().collection("users").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val usersList = snapshot.documents.mapNotNull { parseUser(it) }
                    .filter { it.uid != currentUid }
                _usersState.value = usersList
                applyUserFilters()
                observePresence(usersList.map { it.uid })
            }
        } catch (e: Exception) { Log.e("VM", "loadUsers: ${e.message}") }
    }

    private var searchQuery = ""

    private fun applyUserFilters() {
        val blocked = _currentUserState.value?.blockedUsers ?: emptyList()
        val base = _usersState.value.filter { it.uid !in blocked }
        _filteredUsersState.value = if (searchQuery.isBlank()) base
        else base.filter {
            it.username.contains(searchQuery, true) || it.name.contains(searchQuery, true)
        }
    }

    fun searchUsers(query: String) {
        searchQuery = query
        applyUserFilters()
    }

    private fun observePresence(uids: List<String>) {
        uids.forEach { uid ->
            if (presenceListeners.containsKey(uid)) return@forEach
            val l = PresenceManager.observe(uid) { p ->
                _presenceMap.value = _presenceMap.value + (uid to p)
            }
            presenceListeners[uid] = l
        }
    }

    fun blockUser(uid: String) {
        val me = myUid() ?: return
        val updated = (_currentUserState.value?.blockedUsers ?: emptyList()) + uid
        _currentUserState.value = _currentUserState.value?.copy(blockedUsers = updated.distinct())
        applyUserFilters()
        try {
            firestore().collection("users").document(me)
                .set(mapOf("blockedUsers" to updated.distinct()), SetOptions.merge())
        } catch (e: Exception) { Log.e("VM", "block: ${e.message}") }
    }

    fun unblockUser(uid: String) {
        val me = myUid() ?: return
        val updated = (_currentUserState.value?.blockedUsers ?: emptyList()) - uid
        _currentUserState.value = _currentUserState.value?.copy(blockedUsers = updated)
        applyUserFilters()
        try {
            firestore().collection("users").document(me)
                .set(mapOf("blockedUsers" to updated), SetOptions.merge())
        } catch (e: Exception) { Log.e("VM", "unblock: ${e.message}") }
    }

    // ---------------- Conversations + unread badges ----------------
    private fun startConversationsListener() {
        val uid = myUid() ?: return
        try {
            conversationsListener?.let { db().getReference("userChats").child(uid).removeEventListener(it) }
            conversationsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Conversation>()
                    for (child in snapshot.children) {
                        try {
                            list.add(
                                Conversation(
                                    chatId = child.key ?: "",
                                    partnerId = child.child("partnerId").value as? String ?: "",
                                    partnerName = child.child("partnerName").value as? String ?: "",
                                    partnerUsername = child.child("partnerUsername").value as? String ?: "",
                                    partnerPhoto = child.child("partnerPhoto").value as? String ?: "",
                                    lastMessage = child.child("lastMessage").value as? String ?: "",
                                    lastMessageType = child.child("lastMessageType").value as? String ?: "text",
                                    lastTimestamp = child.child("lastTimestamp").value as? Long ?: 0L,
                                    lastSenderId = child.child("lastSenderId").value as? String ?: ""
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    directConversations = list
                    mergeConversations()
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            db().getReference("userChats").child(uid)
                .addValueEventListener(conversationsListener!!)

            unreadListener?.let { db().getReference("unread").child(uid).removeEventListener(it) }
            unreadListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    unreadMap.clear()
                    var total = 0
                    for (child in snapshot.children) {
                        val c = (child.value as? Long)?.toInt() ?: 0
                        unreadMap[child.key ?: ""] = c
                        total += c
                    }
                    _totalUnread.value = total
                    mergeConversations()
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            db().getReference("unread").child(uid).addValueEventListener(unreadListener!!)
        } catch (e: Exception) { Log.e("VM", "conversations: ${e.message}") }
    }

    private fun mergeConversations() {
        val blocked = _currentUserState.value?.blockedUsers ?: emptyList()
        val me = myUid()
        val groupConvos = _groupsState.value.map { g ->
            Conversation(
                chatId = g.groupId,
                partnerId = g.groupId,
                partnerName = g.name,
                partnerPhoto = g.photoUrl,
                lastMessage = "Group · ${g.memberIds.size} members",
                lastTimestamp = g.createdAt,
                unreadCount = unreadMap[g.groupId] ?: 0,
                isGroup = true
            )
        }
        _conversationsState.value = (directConversations.map {
            it.copy(unreadCount = unreadMap[it.chatId] ?: 0)
        }.filter { it.partnerId !in blocked } + groupConvos)
            .sortedByDescending { it.lastTimestamp }
    }

    fun markChatRead(chatId: String) {
        val uid = myUid() ?: return
        try {
            db().getReference("unread").child(uid).child(chatId).setValue(0)
        } catch (_: Exception) {}
    }

    // ---------------- Chat messages ----------------
    fun startListeningToChat(recipientUid: String) {
        stopListeningToChat()
        val chatId = chatIdFor(recipientUid)
        if (chatId.isBlank()) return
        activeChatId = chatId
        attachMessageListener(db().getReference("chats").child(chatId).child("messages"))
        attachTypingListener(chatId, recipientUid)
        markChatRead(chatId)
    }

    fun startListeningToGroupChat(groupId: String) {
        stopListeningToChat()
        activeChatId = "group_$groupId"
        attachMessageListener(db().getReference("groupChats").child(groupId).child("messages"))
        markChatRead(groupId)
    }

    private fun attachMessageListener(ref: com.google.firebase.database.DatabaseReference) {
        activeChatListener = ref.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<Message>()
                    for (c in snapshot.children) {
                        try {
                            messages.add(parseMessage(c))
                        } catch (_: Exception) {}
                    }
                    _chatMessagesState.value = messages
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun parseMessage(c: DataSnapshot): Message {
        val reactions = mutableMapOf<String, String>()
        c.child("reactions").children.forEach { r ->
            val v = r.value as? String; if (v != null && r.key != null) reactions[r.key!!] = v
        }
        return Message(
            messageId = c.child("messageId").value as? String ?: c.key ?: "",
            senderId = c.child("senderId").value as? String ?: "",
            senderName = c.child("senderName").value as? String ?: "",
            senderUsername = c.child("senderUsername").value as? String ?: "",
            senderPhoto = c.child("senderPhoto").value as? String ?: "",
            text = c.child("text").value as? String ?: "",
            type = c.child("type").value as? String ?: "text",
            mediaUrl = c.child("mediaUrl").value as? String ?: "",
            voiceDuration = c.child("voiceDuration").value as? Long ?: 0L,
            replyToId = c.child("replyToId").value as? String ?: "",
            replyToText = c.child("replyToText").value as? String ?: "",
            replyToSender = c.child("replyToSender").value as? String ?: "",
            forwarded = c.child("forwarded").value as? Boolean ?: false,
            edited = c.child("edited").value as? Boolean ?: false,
            reactions = reactions,
            timestamp = c.child("timestamp").value as? Long ?: 0L
        )
    }

    private fun attachTypingListener(chatId: String, partnerUid: String) {
        try {
            typingListener?.let { db().getReference("typing").child(chatId).removeEventListener(it) }
            typingListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _partnerTyping.value = snapshot.child(partnerUid).getValue(Boolean::class.java) ?: false
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            db().getReference("typing").child(chatId).addValueEventListener(typingListener!!)
        } catch (_: Exception) {}
    }

    fun setTyping(typing: Boolean) {
        val uid = myUid() ?: return
        val chatId = activeChatId ?: return
        if (chatId.startsWith("group_")) return
        try {
            db().getReference("typing").child(chatId).child(uid).setValue(typing)
        } catch (_: Exception) {}
    }

    fun stopListeningToChat() {
        try {
            activeChatListener?.let { l ->
                activeChatId?.let { id ->
                    if (id.startsWith("group_")) {
                        db().getReference("groupChats").child(id.removePrefix("group_")).child("messages").removeEventListener(l)
                    } else {
                        db().getReference("chats").child(id).child("messages").removeEventListener(l)
                    }
                }
            }
            typingListener?.let { l -> activeChatId?.let { id -> db().getReference("typing").child(id).removeEventListener(l) } }
        } catch (_: Exception) {}
        _chatMessagesState.value = emptyList()
        _partnerTyping.value = false
        activeChatListener = null
        typingListener = null
        activeChatId = null
    }

    /** Send a message (direct or group). Handles conversation meta, unread badge, webhook push + in-app notification. */
    fun sendMessage(
        recipientUser: User?,
        group: Group?,
        text: String,
        type: String = "text",
        mediaUrl: String = "",
        voiceDuration: Long = 0L,
        replyTo: Message? = null,
        forwarded: Boolean = false
    ) {
        val currentUser = _currentUserState.value ?: return
        val isGroup = group != null
        val msgRef = if (isGroup) {
            db().getReference("groupChats").child(group!!.groupId).child("messages")
        } else {
            val chatId = activeChatId ?: chatIdFor(recipientUser?.uid ?: return)
            db().getReference("chats").child(chatId).child("messages")
        }
        val messageId = msgRef.push().key ?: UUID.randomUUID().toString()
        val message = Message(
            messageId = messageId,
            senderId = currentUser.uid,
            senderName = currentUser.name,
            senderUsername = currentUser.username,
            senderPhoto = currentUser.photoUrl,
            text = text,
            type = type,
            mediaUrl = mediaUrl,
            voiceDuration = voiceDuration,
            replyToId = replyTo?.messageId ?: "",
            replyToText = replyTo?.text?.take(80) ?: "",
            replyToSender = replyTo?.senderName ?: "",
            forwarded = forwarded,
            timestamp = System.currentTimeMillis()
        )
        msgRef.child(messageId).setValue(message)
        setTyping(false)

        if (!isGroup && recipientUser != null) {
            val chatId = chatIdFor(recipientUser.uid)
            val preview = when (type) {
                "image" -> "📷 Photo"
                "voice" -> "🎤 Voice message"
                else -> text
            }
            val metaForMe = mapOf(
                "partnerId" to recipientUser.uid, "partnerName" to recipientUser.name,
                "partnerUsername" to recipientUser.username, "partnerPhoto" to recipientUser.photoUrl,
                "lastMessage" to preview, "lastMessageType" to type,
                "lastTimestamp" to message.timestamp, "lastSenderId" to currentUser.uid
            )
            val metaForThem = mapOf(
                "partnerId" to currentUser.uid, "partnerName" to currentUser.name,
                "partnerUsername" to currentUser.username, "partnerPhoto" to currentUser.photoUrl,
                "lastMessage" to preview, "lastMessageType" to type,
                "lastTimestamp" to message.timestamp, "lastSenderId" to currentUser.uid
            )
            try {
                db().getReference("userChats").child(currentUser.uid).child(chatId).setValue(metaForMe)
                db().getReference("userChats").child(recipientUser.uid).child(chatId).setValue(metaForThem)
                // unread red badge for recipient
                db().getReference("unread").child(recipientUser.uid).child(chatId)
                    .setValue(ServerValue.increment(1))
            } catch (_: Exception) {}
            // push via webhook (direct messages only — groups stay silent as requested)
            sendPushToWebhook(recipientUser, preview, chatId)
            writeNotification(recipientUser.uid, "message", currentUser, preview, chatId)
        }
    }

    /** Upload + send an image message. */
    fun sendImageMessage(recipientUser: User?, group: Group?, uri: Uri, replyTo: Message?) {
        viewModelScope.launch {
            _uploading.value = true
            val bytes = readBytes(uri, maxDim = 1600, quality = 80)
            val url = if (bytes != null) {
                SupabaseManager.upload(
                    SupabaseManager.BUCKET_POSTS,
                    "chat/${myUid()}/${UUID.randomUUID()}.jpg", bytes
                )
            } else null
            _uploading.value = false
            if (url != null) sendMessage(recipientUser, group, "", "image", url, replyTo = replyTo)
        }
    }

    /** Upload + send a voice message. */
    fun sendVoiceMessage(recipientUser: User?, group: Group?, file: java.io.File, durationMs: Long) {
        viewModelScope.launch {
            _uploading.value = true
            val url = try {
                val bytes = file.readBytes()
                SupabaseManager.upload(
                    SupabaseManager.BUCKET_VOICE,
                    "${myUid()}/${UUID.randomUUID()}.m4a", bytes, "audio/mp4"
                )
            } catch (e: Exception) { null }
            _uploading.value = false
            if (url != null) sendMessage(recipientUser, group, "", "voice", url, voiceDuration)
        }
    }

    fun editMessage(message: Message, newText: String) {
        val chatId = activeChatId ?: return
        val ref = if (chatId.startsWith("group_")) {
            db().getReference("groupChats").child(chatId.removePrefix("group_")).child("messages")
        } else db().getReference("chats").child(chatId).child("messages")
        try {
            ref.child(message.messageId).updateChildren(
                mapOf("text" to newText, "edited" to true)
            )
        } catch (_: Exception) {}
    }

    /** Deletes from RTDB AND Supabase Storage — wiped everywhere. */
    fun deleteMessage(message: Message) {
        val chatId = activeChatId ?: return
        val ref = if (chatId.startsWith("group_")) {
            db().getReference("groupChats").child(chatId.removePrefix("group_")).child("messages")
        } else db().getReference("chats").child(chatId).child("messages")
        try { ref.child(message.messageId).removeValue() } catch (_: Exception) {}
        if (message.mediaUrl.isNotBlank()) {
            viewModelScope.launch { SupabaseManager.deleteByUrl(message.mediaUrl) }
        }
    }

    fun reactToMessage(message: Message, emoji: String) {
        val uid = myUid() ?: return
        val chatId = activeChatId ?: return
        val ref = if (chatId.startsWith("group_")) {
            db().getReference("groupChats").child(chatId.removePrefix("group_")).child("messages")
        } else db().getReference("chats").child(chatId).child("messages")
        try {
            if (message.reactions[uid] == emoji) {
                ref.child(message.messageId).child("reactions").child(uid).removeValue()
            } else {
                ref.child(message.messageId).child("reactions").child(uid).setValue(emoji)
            }
        } catch (_: Exception) {}
    }

    fun forwardMessage(message: Message, target: User) {
        sendMessage(
            recipientUser = target, group = null,
            text = message.text, type = message.type,
            mediaUrl = message.mediaUrl, voiceDuration = message.voiceDuration,
            forwarded = true
        )
    }

    // ---------------- Groups ----------------
    private fun startGroupsListener() {
        val uid = myUid() ?: return
        try {
            firestore().collection("groups")
                .whereArrayContains("memberIds", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    _groupsState.value = snapshot.documents.mapNotNull { parseGroup(it) }
                    mergeConversations()
                }
        } catch (e: Exception) { Log.e("VM", "groups: ${e.message}") }
    }

    private fun parseGroup(doc: com.google.firebase.firestore.DocumentSnapshot): Group? {
        return try {
            Group(
                groupId = doc.id,
                name = doc.getString("name") ?: "Group",
                photoUrl = doc.getString("photoUrl") ?: "",
                backgroundUrl = doc.getString("backgroundUrl") ?: "",
                createdBy = doc.getString("createdBy") ?: "",
                createdByName = doc.getString("createdByName") ?: "",
                memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                memberNames = (doc.get("memberNames") as? Map<*, *>)?.entries
                    ?.mapNotNull { e -> (e.key as? String)?.let { k -> (e.value as? String)?.let { v -> k to v } } }
                    ?.toMap() ?: emptyMap(),
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) { null }
    }

    fun createGroup(name: String, members: List<User>, photoUri: Uri?, onDone: (Group?) -> Unit) {
        val me = _currentUserState.value ?: return
        viewModelScope.launch {
            _uploading.value = true
            val gid = firestore().collection("groups").document().id
            var photoUrl = ""
            if (photoUri != null) {
                val bytes = readBytes(photoUri, maxDim = 720)
                if (bytes != null) {
                    photoUrl = SupabaseManager.upload(SupabaseManager.BUCKET_GROUPS, "$gid.jpg", bytes) ?: ""
                }
            }
            val memberIds = (members.map { it.uid } + me.uid).distinct()
            val memberNames = (members.associate { it.uid to it.name } + (me.uid to me.name))
            val group = Group(
                groupId = gid, name = name, photoUrl = photoUrl,
                createdBy = me.uid, createdByName = me.name,
                memberIds = memberIds, memberNames = memberNames,
                createdAt = System.currentTimeMillis()
            )
            try {
                firestore().collection("groups").document(gid).set(group)
                    .addOnSuccessListener { _uploading.value = false; onDone(group) }
                    .addOnFailureListener { _uploading.value = false; onDone(null) }
            } catch (e: Exception) { _uploading.value = false; onDone(null) }
        }
    }

    fun updateGroupBackground(groupId: String, uri: Uri) {
        viewModelScope.launch {
            val bytes = readBytes(uri, maxDim = 1600, quality = 75) ?: return@launch
            val url = SupabaseManager.upload(SupabaseManager.BUCKET_GROUPS, "bg_$groupId.jpg", bytes) ?: return@launch
            try {
                firestore().collection("groups").document(groupId)
                    .set(mapOf("backgroundUrl" to url), SetOptions.merge())
            } catch (_: Exception) {}
        }
    }

    fun addGroupMembers(groupId: String, newMembers: List<User>) {
        val group = _groupsState.value.firstOrNull { it.groupId == groupId } ?: return
        val ids = (group.memberIds + newMembers.map { it.uid }).distinct()
        val names = group.memberNames + newMembers.associate { it.uid to it.name }
        try {
            firestore().collection("groups").document(groupId)
                .set(mapOf("memberIds" to ids, "memberNames" to names), SetOptions.merge())
        } catch (_: Exception) {}
    }

    // ---------------- Push webhook + notifications ----------------
    private fun sendPushToWebhook(recipient: User, preview: String, chatId: String) {
        val url = _webhookUrl.value
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val me = _currentUserState.value ?: return@launch
                val json = JSONObject().apply {
                    put("title", me.name)
                    put("body", preview)
                    put("senderId", me.uid)
                    put("chatId", chatId)
                    put("recipientToken", recipient.fcmToken)
                    put("recipientUid", recipient.uid)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()
                http.newCall(request).execute().use { }
            } catch (e: Exception) { Log.w("VM", "webhook: ${e.message}") }
        }
    }

    fun writeNotification(toUid: String, type: String, from: User, text: String, refId: String) {
        if (toUid == from.uid) return
        try {
            val item = hashMapOf(
                "type" to type, "fromUid" to from.uid, "fromName" to from.name,
                "fromPhoto" to from.photoUrl, "text" to text, "refId" to refId,
                "read" to false, "timestamp" to System.currentTimeMillis()
            )
            firestore().collection("notifications").document(toUid)
                .collection("items").add(item)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningToChat()
    }
}
