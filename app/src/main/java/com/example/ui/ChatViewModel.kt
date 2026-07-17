package com.example.ui

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)

    // Observables for UI
    private val _currentUserState = MutableStateFlow<User?>(null)
    val currentUserState: StateFlow<User?> = _currentUserState.asStateFlow()

    private val _activeRecipientUser = MutableStateFlow<User?>(null)
    val activeRecipientUser: StateFlow<User?> = _activeRecipientUser.asStateFlow()

    fun selectRecipient(user: User?) {
        _activeRecipientUser.value = user
    }

    private val _usersState = MutableStateFlow<List<User>>(emptyList())
    val usersState: StateFlow<List<User>> = _usersState.asStateFlow()

    private val _filteredUsersState = MutableStateFlow<List<User>>(emptyList())
    val filteredUsersState: StateFlow<List<User>> = _filteredUsersState.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<List<Message>>(emptyList())
    val chatMessagesState: StateFlow<List<Message>> = _chatMessagesState.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(true)
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    private val _workerUrl = MutableStateFlow(sharedPrefs.getString("worker_url", "https://your-cloudflare-worker.workers.dev") ?: "")
    val workerUrl: StateFlow<String> = _workerUrl.asStateFlow()

    private var activeChatListener: ValueEventListener? = null
    private var activeChatId: String? = null

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

    private fun listenToGlobalConfig() {
        try {
            FirebaseFirestore.getInstance().collection("config")
                .document("app_settings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FIRESTORE_CONFIG", "Listen to global config failed: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val url = snapshot.getString("workerUrl")
                        if (!url.isNullOrBlank()) {
                            _workerUrl.value = url
                            sharedPrefs.edit().putString("worker_url", url).apply()
                            Log.d("FIRESTORE_CONFIG", "Loaded worker URL from Firestore: $url")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("FIRESTORE_CONFIG", "Error subscribing to global config: ${e.message}")
        }
    }

    init {
        checkFirebaseConfiguration()
    }

    fun updateWorkerUrl(url: String) {
        _workerUrl.value = url
        sharedPrefs.edit().putString("worker_url", url).apply()
        
        try {
            FirebaseFirestore.getInstance().collection("config")
                .document("app_settings")
                .set(mapOf("workerUrl" to url))
                .addOnSuccessListener {
                    Log.d("FIRESTORE_CONFIG", "Successfully saved worker URL to Firestore.")
                }
                .addOnFailureListener { e ->
                    Log.e("FIRESTORE_CONFIG", "Failed to save worker URL to Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("FIRESTORE_CONFIG", "Error updating global config in Firestore: ${e.message}")
        }
    }

    private fun checkFirebaseConfiguration() {
        try {
            val auth = FirebaseAuth.getInstance()
            listenToGlobalConfig()
            if (auth.currentUser != null) {
                loadCurrentUserProfile(auth.currentUser!!.uid)
                loadAllUsers()
            }
        } catch (e: Exception) {
            Log.e("FirebaseConfig", "Firebase is not initialized properly. Google Services JSON might be missing or invalid: ${e.message}")
            _isFirebaseConfigured.value = false
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (!_isFirebaseConfigured.value) {
            _authError.value = "Firebase is not configured! Please upload a valid google-services.json file."
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
                        loadAllUsers()
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

    fun signup(email: String, name: String, dob: String, onSuccess: () -> Unit) {
        if (!_isFirebaseConfigured.value) {
            _authError.value = "Firebase is not configured! Please upload a valid google-services.json file."
            return
        }

        if (email.isBlank() || name.isBlank() || dob.isBlank()) {
            _authError.value = "Please fill in all fields"
            return
        }

        // Standard default password for quick signups on this prototype or generate a simple password.
        // Or we can let them input one. Since DOB is private, let's generate a temporary password
        // or ask them for password. Let's use DOB without dashes as a neat automatic password,
        // or a default password "123456" for simpler prototyping, or DOB + name.
        // Let's use a safe automatic password of 6+ characters: e.g. "pass_" + dob.replace("-", "")
        val autoPassword = "pass_" + dob.replace("-", "").take(6).padEnd(6, 'x')

        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, autoPassword)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: ""
                        
                        // Generate a unique username: name in lowercase without spaces + 4 random digits
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

                        // Save user in Firestore
                        FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
                            .set(newUser)
                            .addOnSuccessListener {
                                retrieveFCMTokenAndStore(uid)
                                _currentUserState.value = newUser
                                loadAllUsers()
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

    fun logout(onSuccess: () -> Unit) {
        try {
            FirebaseAuth.getInstance().signOut()
            _currentUserState.value = null
            _usersState.value = emptyList()
            _filteredUsersState.value = emptyList()
            _chatMessagesState.value = emptyList()
            activeChatId = null
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
                Log.d("FCM_TOKEN", "FCM Token: $token")
                
                // Save FCM token under the User Document in Firestore
                FirebaseFirestore.getInstance().collection("users")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d("FCM_TOKEN", "Successfully updated token in Firestore.")
                        _currentUserState.value = _currentUserState.value?.copy(fcmToken = token)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM_TOKEN", "Failed to update token: ${e.message}")
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
                        Log.e("FIRESTORE", "Error parsing current user profile with toObject, trying manual: ${e.message}")
                        try {
                            User(
                                uid = document.getString("uid") ?: document.id,
                                name = document.getString("name") ?: "",
                                dob = document.getString("dob") ?: "",
                                username = document.getString("username") ?: "",
                                fcmToken = document.getString("fcmToken") ?: "",
                                createdAt = document.getLong("createdAt") ?: 0L
                            )
                        } catch (ex: Exception) {
                            Log.e("FIRESTORE", "Manual parse also failed: ${ex.message}")
                            null
                        }
                    }
                    _currentUserState.value = user
                    // Refresh FCM token just in case
                    retrieveFCMTokenAndStore(uid)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FIRESTORE", "Error fetching user profile: ${e.message}")
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
                            Log.e("FIRESTORE", "Error parsing list user doc ${doc.id} with toObject: ${e.message}")
                            try {
                                User(
                                    uid = doc.getString("uid") ?: doc.id,
                                    name = doc.getString("name") ?: "",
                                    dob = doc.getString("dob") ?: "",
                                    username = doc.getString("username") ?: "",
                                    fcmToken = doc.getString("fcmToken") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: 0L
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        if (user != null && user.uid != currentUid) {
                            usersList.add(user)
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

    fun startListeningToChat(recipientUid: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Remove existing listener if any
        stopListeningToChat()

        // Generate deterministic chat ID (concatenated sorted UIDs)
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
                            Log.e("RTDB_CHAT", "Error parsing message with getValue, trying manual: ${e.message}")
                            try {
                                Message(
                                    messageId = childSnapshot.child("messageId").value as? String ?: childSnapshot.key ?: "",
                                    senderId = childSnapshot.child("senderId").value as? String ?: "",
                                    senderName = childSnapshot.child("senderName").value as? String ?: "",
                                    senderUsername = childSnapshot.child("senderUsername").value as? String ?: "",
                                    text = childSnapshot.child("text").value as? String ?: "",
                                    timestamp = (childSnapshot.child("timestamp").value as? Long) ?: 0L
                                )
                            } catch (ex: Exception) {
                                Log.e("RTDB_CHAT", "Manual parse of message also failed: ${ex.message}")
                                null
                            }
                        }
                        if (message != null) {
                            messages.add(message)
                        }
                    }
                    _chatMessagesState.value = messages
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("RTDB_CHAT", "Failed to load messages: ${error.message}")
                }
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
        activeChatId = null
    }

    fun sendMessage(recipientUser: User, text: String) {
        val currentUser = _currentUserState.value ?: return
        val chatId = activeChatId ?: return
        if (text.isBlank()) return

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
            timestamp = System.currentTimeMillis()
        )

        chatRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                Log.d("RTDB_CHAT", "Message sent successfully.")
                // Send FCM trigger call through Cloudflare Worker
                if (recipientUser.fcmToken.isNotBlank()) {
                    triggerCloudflareWorkerNotification(
                        workerUrl = _workerUrl.value,
                        targetToken = recipientUser.fcmToken,
                        senderName = currentUser.name,
                        messageBody = text,
                        senderId = currentUser.uid
                    )
                } else {
                    Log.d("WORKER_NOTIF", "Recipient has no FCM token. Notification skipped.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("RTDB_CHAT", "Failed to write message: ${e.message}")
            }
    }

    private fun triggerCloudflareWorkerNotification(
        workerUrl: String,
        targetToken: String,
        senderName: String,
        messageBody: String,
        senderId: String
    ) {
        if (workerUrl.isBlank() || !workerUrl.startsWith("http")) {
            Log.w("WORKER_NOTIF", "Invalid Cloudflare Worker URL. Set a correct URL in Profile settings.")
            return
        }

        viewModelScope.launch {
            try {
                val client = OkHttpClient()
                val jsonObject = JSONObject().apply {
                    put("token", targetToken)
                    put("title", "Message from $senderName")
                    put("body", messageBody)
                    put("senderId", senderId)
                }

                val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(workerUrl)
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("WORKER_NOTIF", "Failed to contact Cloudflare Worker: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val respBody = response.body?.string() ?: ""
                        Log.d("WORKER_NOTIF", "Worker Response [${response.code}]: $respBody")
                    }
                })
            } catch (e: Exception) {
                Log.e("WORKER_NOTIF", "Exception during worker request setup: ${e.message}")
            }
        }
    }
}
