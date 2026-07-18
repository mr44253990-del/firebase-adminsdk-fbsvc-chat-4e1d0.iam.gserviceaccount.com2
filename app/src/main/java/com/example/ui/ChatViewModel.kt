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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

data class InAppNotificationData(
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)

    // Offline Cache DB & Dao
    private val appDb = AppDatabase.getDatabase(application)
    private val cacheDao = appDb.cacheDao()

    // Themes
    private val _themeState = MutableStateFlow(sharedPrefs.getString("app_theme", "default") ?: "default")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

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

    private val _inAppNotification = MutableStateFlow<InAppNotificationData?>(null)
    val inAppNotification: StateFlow<InAppNotificationData?> = _inAppNotification.asStateFlow()

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

    private val defaultWebhookUrl = "https://rakibul.n8n-host.com/webhook/ra"

    private val _webhookUrl = MutableStateFlow(sharedPrefs.getString("webhook_url", defaultWebhookUrl).orEmpty().ifBlank { defaultWebhookUrl })
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private var activeChatListener: ValueEventListener? = null
    private var activeChatId: String? = null
    private var activeTypingListener: ValueEventListener? = null
    private var globalNotificationListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null

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
    }

    private fun listenToGlobalConfig() {
        try {
            val configRef = getDatabaseInstance().getReference("config").child("app_settings")
            configRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val url = snapshot.child("webhookUrl").value as? String 
                                  ?: snapshot.child("workerUrl").value as? String
                        if (!url.isNullOrBlank()) {
                            _webhookUrl.value = url
                            sharedPrefs.edit().putString("webhook_url", url).apply()
                            Log.d("RTDB_CONFIG", "Loaded webhook URL from RTDB: $url")
                        } else {
                            configRef.setValue(mapOf("webhookUrl" to defaultWebhookUrl, "workerUrl" to defaultWebhookUrl))
                        }
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
                FirebaseFirestore.getInstance().collection("users")
                    .document(uid)
                    .update("fcmToken", token)
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
                                    voiceDurationSec = (childSnapshot.child("voiceDurationSec").value as? Long)?.toInt()
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        if (message != null) {
                            messages.add(message)
                        }
                    }
                    _chatMessagesState.value = messages

                    // Cache to Room local DB
                    viewModelScope.launch(Dispatchers.IO) {
                        cacheDao.insertMessages(messages.map { CachedMessage.fromMessage(it, chatId) })
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun stopListeningToChat() {
        val chatId = activeChatId ?: return
        val listener = activeChatListener ?: return
        getDatabaseInstance().getReference("chats")
            .child(chatId)
            .child("messages")
            .removeEventListener(listener)

        _chatMessagesState.value = emptyList()
        activeChatListener = null
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

    // Sound effect players
    fun playNotificationSound() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.e("SOUND", "Error playing beep tone: ${e.message}")
        }
    }

    fun playTypingSound() {
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
            voiceDurationSec = voiceDurationSec
        )

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

                // Send FCM trigger call through Webhook (always trigger so admin can see it in n8n)
                triggerN8NWebhookNotification(
                    webhookUrl = _webhookUrl.value,
                    targetToken = recipientUser.fcmToken,
                    senderName = currentUser.name,
                    messageBody = if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                    senderId = currentUser.uid
                )
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
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUid)

        val updates = mapOf(
            "name" to name,
            "dob" to dob,
            "profileImageUrl" to profileImageUrl
        )

        userRef.update(updates)
            .addOnSuccessListener {
                _currentUserState.value = _currentUserState.value?.copy(
                    name = name,
                    dob = dob,
                    profileImageUrl = profileImageUrl
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
                        withContext(Dispatchers.Main) {
                            onSuccess(publicUrl)
                        }
                    } else {
                        val bodyStr = response.body?.string() ?: ""
                        Log.e("SUPABASE_UPLOAD", "Failed code: ${response.code} body: $bodyStr")
                        // If file already exists, return the public url directly
                        if (response.code == 400 && bodyStr.contains("Duplicate")) {
                            val publicUrl = "https://srfztgcdejfaesrvkarg.supabase.co/storage/v1/object/public/$bucket/$fileName"
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

    private fun triggerN8NWebhookNotification(
        webhookUrl: String,
        targetToken: String,
        senderName: String,
        messageBody: String,
        senderId: String
    ) {
        if (webhookUrl.isBlank() || !webhookUrl.startsWith("http")) {
            Log.w("WEBHOOK_NOTIF", "Invalid Webhook URL. Cannot trigger push notification.")
            return
        }

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
                    put("title", "New message from $senderName")
                    put("body", messageBody)
                    put("text", messageBody)
                    put("senderId", senderId)
                    put("senderName", senderName)
                    put("timestamp", System.currentTimeMillis())
                    put("formattedTime", formattedTime)
                }

                val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("WEBHOOK_NOTIF", "Failed to contact Webhook: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val respBody = response.body?.string() ?: ""
                        Log.d("WEBHOOK_NOTIF", "Webhook Response [${response.code}]: $respBody")
                    }
                })
            } catch (e: Exception) {
                Log.e("WEBHOOK_NOTIF", "Exception during webhook setup: ${e.message}")
            }
        }
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
                                comments = commentsList
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
                storyRef.update("reactions", currentReactions)
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
                storyRef.update("comments", commentsList)
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
                                isPrivate = doc.getBoolean("isPrivate") ?: false
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

    fun createPost(text: String, imageUrl: String, audioUrl: String, videoUrl: String, isPrivate: Boolean, onComplete: () -> Unit) {
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
            isPrivate = isPrivate
        )

        // Webhook shouldn't be called according to instruction ("কোন পোস্ট করলে রিকোয়েস্ট যাবে না")
        FirebaseFirestore.getInstance().collection("posts").document(postId)
            .set(post)
            .addOnSuccessListener {
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
        FirebaseFirestore.getInstance().collection("posts").document(postId).delete()
            .addOnSuccessListener {
                viewModelScope.launch(Dispatchers.IO) {
                    cacheDao.deletePost(postId)
                }
                loadPosts()
            }
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
                postRef.update("reactions", currentReactions)
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
                postRef.update("comments", commentsList)
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
