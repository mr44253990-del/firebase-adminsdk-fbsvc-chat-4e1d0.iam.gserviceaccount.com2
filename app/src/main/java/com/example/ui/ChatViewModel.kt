package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Message
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
            FirebaseDatabase.getInstance("https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            Log.e("DATABASE", "Failed to get database with URL, trying default: ${e.message}")
            try {
                FirebaseDatabase.getInstance()
            } catch (ex: Exception) {
                Log.e("DATABASE", "Failed to get default database instance: ${ex.message}")
                throw ex
            }
        }
    }

    init {
        checkFirebaseConfiguration()
    }

    fun selectRecipient(user: User?) {
        _activeRecipientUser.value = user
        if (user != null) {
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

    fun signup(email: String, name: String, dob: String, password: String, onSuccess: () -> Unit) {
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
                if (document.exists()) {
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
                }
            }
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
                    val usersList = mutableListOf<User>()
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
                            // Filter blocked users
                            val myBlocked = _currentUserState.value?.blockedUsers ?: emptyList()
                            if (!myBlocked.contains(user.uid) && !user.blockedUsers.contains(currentUid)) {
                                usersList.add(user)
                            }
                        }
                    }
                    _usersState.value = usersList
                    _filteredUsersState.value = usersList
                }
            }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _filteredUsersState.value = _usersState.value
        } else {
            _filteredUsersState.value = _usersState.value.filter { user ->
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
                _filteredUsersState.value = sortedUsers
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
                // Instantly notify recipient under /notifications RTDB key
                getDatabaseInstance().getReference("notifications").child(recipientUser.uid)
                    .setValue(mapOf(
                        "senderId" to currentUser.uid,
                        "senderName" to currentUser.name,
                        "text" to if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                        "timestamp" to System.currentTimeMillis()
                    ))

                // Send FCM trigger call through Webhook
                if (recipientUser.fcmToken.isNotBlank()) {
                    triggerN8NWebhookNotification(
                        webhookUrl = _webhookUrl.value,
                        targetToken = recipientUser.fcmToken,
                        senderName = currentUser.name,
                        messageBody = if (voiceUrl != null) "🎙️ Voice message" else if (imageUrl != null) "📷 Image attachment" else text,
                        senderId = currentUser.uid
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
    }

    fun unblockUser(targetUid: String, onSuccess: () -> Unit) {
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
}
