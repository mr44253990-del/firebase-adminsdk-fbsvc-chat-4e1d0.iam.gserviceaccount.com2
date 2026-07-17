package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import io.github.jan.tennert.supabase.SupabaseClient
import io.github.jan.tennert.supabase.createSupabaseClient
import io.github.jan.tennert.supabase.postgrest.Postgrest
import io.github.jan.tennert.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)
    
    // ==================== Supabase Client ====================
    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://srfztgcdejfaesrvkarg.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNyZnp0Z2NkZWpmYWVzcnZrYXJnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzQwNjQ2NjAsImV4cCI6MjA0OTY0MDY2MH0.BcH2xwywnUCVG48LYjPOLQ_8-y2InGA"
    ) {
        install(Postgrest)
        install(Storage)
    }

    // ==================== UI State Flows ====================
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

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(true)
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    private val _webhookUrl = MutableStateFlow(sharedPrefs.getString("webhook_url", "") ?: "")
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    // ==================== New Feature States ====================
    private val _storiesState = MutableStateFlow<List<Story>>(emptyList())
    val storiesState: StateFlow<List<Story>> = _storiesState.asStateFlow()

    private val _postsState = MutableStateFlow<List<Post>>(emptyList())
    val postsState: StateFlow<List<Post>> = _postsState.asStateFlow()

    private val _groupsState = MutableStateFlow<List<Group>>(emptyList())
    val groupsState: StateFlow<List<Group>> = _groupsState.asStateFlow()

    private val _notesState = MutableStateFlow<List<Note>>(emptyList())
    val notesState: StateFlow<List<Note>> = _notesState.asStateFlow()

    private val _notificationsState = MutableStateFlow<List<AppNotification>>(emptyList())
    val notificationsState: StateFlow<List<AppNotification>> = _notificationsState.asStateFlow()

    private val _unreadMessageCount = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessageCount: StateFlow<Map<String, Int>> = _unreadMessageCount.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _selectedTheme = MutableStateFlow(sharedPrefs.getString("theme", "system") ?: "system")
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private val _selectedAccentColor = MutableStateFlow(sharedPrefs.getString("accent_color", "#6C63FF") ?: "#6C63FF")
    val selectedAccentColor: StateFlow<String> = _selectedAccentColor.asStateFlow()

    // ==================== Firebase References ====================
    private var activeChatListener: ValueEventListener? = null
    private var activeChatId: String? = null
    private var onlineStatusListener: ValueEventListener? = null

    private val database: FirebaseDatabase
        get() = try {
            FirebaseDatabase.getInstance("https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    // ==================== Initialization ====================
    init {
        checkFirebaseConfiguration()
    }

    private fun checkFirebaseConfiguration() {
        try {
            val firebaseAuth = FirebaseAuth.getInstance()
            listenToGlobalConfig()
            if (firebaseAuth.currentUser != null) {
                loadCurrentUserProfile(firebaseAuth.currentUser!!.uid)
                loadAllUsers()
                loadStories()
                loadPosts()
                loadGroups()
                loadNotes()
                loadNotifications()
                updateOnlineStatus(true)
            }
        } catch (e: Exception) {
            Log.e("FirebaseConfig", "Firebase is not configured: ${e.message}")
            _isFirebaseConfigured.value = false
        }
    }

    // ==================== Online Status Management ====================
    fun updateOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userRef = firestore.collection("users").document(uid)
                val updates = mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis()
                )
                userRef.update(updates).await()
                
                // Also update in realtime database for faster updates
                database.getReference("presence").child(uid).apply {
                    if (isOnline) {
                        onDisconnect().removeValue()
                    }
                    setValue(mapOf(
                        "isOnline" to isOnline,
                        "lastSeen" to System.currentTimeMillis()
                    ))
                }
            } catch (e: Exception) {
                Log.e("OnlineStatus", "Failed to update: ${e.message}")
            }
        }
    }

    fun setActivityVisibility(isVisible: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("isActivityVisible", isVisible)
                    .await()
            } catch (e: Exception) {
                Log.e("ActivityVisibility", "Failed: ${e.message}")
            }
        }
    }

    // ==================== Authentication ====================
    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (!_isFirebaseConfigured.value) {
            _authError.value = "Firebase is not configured!"
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
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: ""
                        retrieveFCMTokenAndStore(uid)
                        loadCurrentUserProfile(uid)
                        loadAllUsers()
                        loadStories()
                        loadPosts()
                        loadGroups()
                        loadNotes()
                        loadNotifications()
                        updateOnlineStatus(true)
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

    fun signup(
        email: String,
        name: String,
        dob: String,
        password: String,
        profileImageUri: String = "",
        onSuccess: () -> Unit
    ) {
        if (!_isFirebaseConfigured.value) {
            _authError.value = "Firebase is not configured!"
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
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: ""
                        val cleanName = name.lowercase().replace("\\s".toRegex(), "")
                        val randomSuffix = (1000..9999).random()
                        val generatedUsername = "${cleanName}_$randomSuffix"

                        var profileUrl = profileImageUri
                        
                        // Upload profile image to Supabase if provided
                        if (profileImageUri.isNotBlank()) {
                            try {
                                profileUrl = uploadToSupabase(Uri.parse(profileImageUri), "profiles/$uid/profile")
                            } catch (e: Exception) {
                                Log.e("ImageUpload", "Failed: ${e.message}")
                            }
                        }

                        val newUser = User(
                            uid = uid,
                            name = name,
                            dob = dob,
                            username = generatedUsername,
                            email = email,
                            profileImageUrl = profileUrl,
                            createdAt = System.currentTimeMillis()
                        )

                        firestore.collection("users").document(uid)
                            .set(newUser)
                            .addOnSuccessListener {
                                retrieveFCMTokenAndStore(uid)
                                _currentUserState.value = newUser
                                loadAllUsers()
                                loadStories()
                                loadPosts()
                                loadGroups()
                                loadNotes()
                                loadNotifications()
                                updateOnlineStatus(true)
                                _authLoading.value = false
                                onSuccess()
                            }
                            .addOnFailureListener { e ->
                                _authError.value = e.localizedMessage
                                _authLoading.value = false
                            }
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

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            updateOnlineStatus(false)
            try {
                auth.signOut()
                _currentUserState.value = null
                _usersState.value = emptyList()
                _chatMessagesState.value = emptyList()
                _storiesState.value = emptyList()
                _postsState.value = emptyList()
                _groupsState.value = emptyList()
                _notesState.value = emptyList()
                onComplete()
            } catch (e: Exception) {
                Log.e("Logout", "Error: ${e.message}")
            }
        }
    }

    // ==================== User Profile ====================
    fun selectRecipient(user: User?) {
        _activeRecipientUser.value = user
    }

    fun loadCurrentUserProfile(uid: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("Firestore", "Error loading user: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            _currentUserState.value = snapshot.toObject(User::class.java)
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadProfile", "Error: ${e.message}")
            }
        }
    }

    fun updateUserProfile(
        name: String? = null,
        bio: String? = null,
        profileImageUri: String? = null,
        coverImageUri: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val updates = mutableMapOf<String, Any>()
                
                name?.let { updates["name"] = it }
                bio?.let { updates["bio"] = it }
                
                if (profileImageUri != null && profileImageUri.isNotBlank()) {
                    try {
                        val url = uploadToSupabase(Uri.parse(profileImageUri), "profiles/$uid/profile")
                        updates["profileImageUrl"] = url
                    } catch (e: Exception) {
                        Log.e("ProfileUpdate", "Image upload failed: ${e.message}")
                    }
                }
                
                if (coverImageUri != null && coverImageUri.isNotBlank()) {
                    try {
                        val url = uploadToSupabase(Uri.parse(coverImageUri), "covers/$uid/cover")
                        updates["coverImageUrl"] = url
                    } catch (e: Exception) {
                        Log.e("ProfileUpdate", "Cover upload failed: ${e.message}")
                    }
                }
                
                updates["updatedAt"] = System.currentTimeMillis()
                
                firestore.collection("users").document(uid)
                    .update(updates)
                    .await()
                
                loadCurrentUserProfile(uid)
                _isUploading.value = false
                onSuccess()
            } catch (e: Exception) {
                _isUploading.value = false
                onError(e.message ?: "Update failed")
            }
        }
    }

    fun loadAllUsers() {
        viewModelScope.launch {
            try {
                firestore.collection("users")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("Firestore", "Error loading users: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val users = snapshot.documents
                                .mapNotNull { it.toObject(User::class.java) }
                                .filter { it.uid != auth.currentUser?.uid }
                            _usersState.value = users
                            _filteredUsersState.value = users
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadUsers", "Error: ${e.message}")
            }
        }
    }

    fun searchUsers(query: String) {
        val filtered = if (query.isBlank()) {
            _usersState.value
        } else {
            _usersState.value.filter { user ->
                user.name.contains(query, ignoreCase = true) ||
                user.username.contains(query, ignoreCase = true)
            }
        }
        _filteredUsersState.value = filtered
    }

    fun blockUser(userId: String, block: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userRef = firestore.collection("users").document(uid)
                if (block) {
                    userRef.update("blockedUsers", FieldValue.arrayUnion(userId)).await()
                } else {
                    userRef.update("blockedUsers", FieldValue.arrayRemove(userId)).await()
                }
                loadCurrentUserProfile(uid)
            } catch (e: Exception) {
                Log.e("BlockUser", "Error: ${e.message}")
            }
        }
    }

    // ==================== Chat/Messages ====================
    fun startListeningToChat(recipientUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < recipientUid) "${currentUid}_$recipientUid" else "${recipientUid}_$currentUid"
        
        stopListeningToChat()
        activeChatId = chatId

        activeChatListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val messages = mutableListOf<Message>()
                    for (child in snapshot.children) {
                        child.getValue(Message::class.java)?.let { messages.add(it) }
                    }
                    _chatMessagesState.value = messages.sortedBy { it.timestamp }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatListener", "Error: ${error.message}")
            }
        }

        database.getReference("chats").child(chatId).child("messages")
            .orderByChild("timestamp")
            .addValueEventListener(activeChatListener!!)
    }

    fun stopListeningToChat() {
        activeChatListener?.let {
            activeChatId?.let { id ->
                database.getReference("chats").child(id).child("messages")
                    .removeEventListener(it)
            }
        }
        activeChatListener = null
    }

    fun sendMessage(
        recipient: User,
        text: String,
        replyToMessageId: String = "",
        replyToText: String = "",
        replyToSenderName: String = "",
        imageUrl: String = ""
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentUser = _currentUserState.value ?: return
        val chatId = if (currentUid < recipient.uid) "${currentUid}_${recipient.uid}" else "${recipient.uid}_$currentUid"

        viewModelScope.launch {
            try {
                val messageId = database.getReference("chats").child(chatId).child("messages").push().key ?: return@launch
                val timestamp = System.currentTimeMillis()

                val message = Message(
                    messageId = messageId,
                    senderId = currentUid,
                    senderName = currentUser.name,
                    senderUsername = currentUser.username,
                    senderProfileUrl = currentUser.profileImageUrl,
                    text = text,
                    imageUrl = imageUrl,
                    replyToMessageId = replyToMessageId,
                    replyToText = replyToText,
                    replyToSenderName = replyToSenderName,
                    timestamp = timestamp,
                    chatId = chatId
                )

                database.getReference("chats").child(chatId).child("messages")
                    .child(messageId)
                    .setValue(message)

                // Update last message in chat metadata
                val chatRef = database.getReference("chats").child(chatId)
                chatRef.child("lastMessage").setValue(text)
                chatRef.child("lastMessageTime").setValue(timestamp)
                chatRef.child("lastMessageSenderId").setValue(currentUid)

                // Update unread count
                val unreadPath = "chats/$chatId/unreadCount/$recipient.uid"
                database.getReference(unreadPath).setValue(
                    (database.getReference(unreadCount).get().toString().toIntOrNull() ?: 0) + 1
                )

                // Send notification via webhook/n8n
                sendPushNotification(recipient.fcmToken, currentUser.name, text)

            } catch (e: Exception) {
                Log.e("SendMessage", "Error: ${e.message}")
            }
        }
    }

    fun editMessage(chatId: String, messageId: String, newText: String) {
        viewModelScope.launch {
            try {
                database.getReference("chats").child(chatId).child("messages")
                    .child(messageId)
                    .updateChildren(mapOf(
                        "text" to newText,
                        "isEdited" to true
                    ))
            } catch (e: Exception) {
                Log.e("EditMessage", "Error: ${e.message}")
            }
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            try {
                database.getReference("chats").child(chatId).child("messages")
                    .child(messageId)
                    .updateChildren(mapOf(
                        "text" to "",
                        "isDeleted" to true
                    ))
            } catch (e: Exception) {
                Log.e("DeleteMessage", "Error: ${e.message}")
            }
        }
    }

    fun markMessagesAsRead(chatId: String, senderId: String) {
        val currentUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updates = mapOf("unreadCount/$currentUid" to 0)
                database.getReference("chats").child(chatId).updateChildren(updates)
            } catch (e: Exception) {
                Log.e("MarkRead", "Error: ${e.message}")
            }
        }
    }

    // ==================== Stories ====================
    fun loadStories() {
        viewModelScope.launch {
            try {
                firestore.collection("stories")
                    .whereEqualTo("isActive", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("LoadStories", "Error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val stories = snapshot.documents.mapNotNull { 
                                it.toObject(Story::class.java) 
                            }.filter { 
                                it.expiresAt > System.currentTimeMillis() 
                            }
                            _storiesState.value = stories
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadStories", "Error: ${e.message}")
            }
        }
    }

    fun createStory(imageUri: String, caption: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val user = _currentUserState.value ?: return
        
        viewModelScope.launch {
            _isUploading.value = true
            try {
                var imageUrl = ""
                try {
                    imageUrl = uploadToSupabase(Uri.parse(imageUri), "stories/${uid}_${System.currentTimeMillis()}")
                } catch (e: Exception) {
                    Log.e("StoryUpload", "Failed: ${e.message}")
                }

                val storyId = firestore.collection("stories").document().id
                val now = System.currentTimeMillis()
                val expiresAt = now + (24 * 60 * 60 * 1000) // 24 hours

                val story = Story(
                    storyId = storyId,
                    userId = uid,
                    userName = user.name,
                    userUsername = user.username,
                    userProfileUrl = user.profileImageUrl,
                    imageUrl = imageUrl,
                    caption = caption,
                    createdAt = now,
                    expiresAt = expiresAt,
                    isActive = true
                )

                firestore.collection("stories").document(storyId)
                    .set(story)
                    .await()
                
                _isUploading.value = false
                loadStories()
                onSuccess()
            } catch (e: Exception) {
                _isUploading.value = false
                onError(e.message ?: "Failed to create story")
            }
        }
    }

    fun deleteStory(storyId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                firestore.collection("stories").document(storyId)
                    .update("isActive", false)
                    .await()
                loadStories()
                onSuccess()
            } catch (e: Exception) {
                Log.e("DeleteStory", "Error: ${e.message}")
            }
        }
    }

    fun viewStory(storyId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("stories").document(storyId)
                    .update("views", FieldValue.arrayUnion(uid))
                    .await()
            } catch (e: Exception) {
                Log.e("ViewStory", "Error: ${e.message}")
            }
        }
    }

    fun reactToStory(storyId: String, reaction: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("stories").document(storyId)
                    .update("reactions.$uid", reaction)
                    .await()
            } catch (e: Exception) {
                Log.e("ReactStory", "Error: ${e.message}")
            }
        }
    }

    // ==================== Posts ====================
    fun loadPosts() {
        viewModelScope.launch {
            try {
                firestore.collection("posts")
                    .whereEqualTo("isDeleted", false)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("LoadPosts", "Error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            _postsState.value = snapshot.documents.mapNotNull { 
                                it.toObject(Post::class.java) 
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadPosts", "Error: ${e.message}")
            }
        }
    }

    fun createPost(
        content: String,
        imageUris: List<String> = emptyList(),
        privacy: String = "public",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        val user = _currentUserState.value ?: return

        viewModelScope.launch {
            _isUploading.value = true
            try {
                val imageUrls = mutableListOf<String>()
                imageUris.forEachIndexed { index, uri ->
                    try {
                        val url = uploadToSupabase(
                            Uri.parse(uri), 
                            "posts/$uid/${System.currentTimeMillis()}_$index"
                        )
                        imageUrls.add(url)
                    } catch (e: Exception) {
                        Log.e("PostImageUpload", "Failed: ${e.message}")
                    }
                }

                val postId = firestore.collection("posts").document().id
                val now = System.currentTimeMillis()

                val post = Post(
                    postId = postId,
                    userId = uid,
                    userName = user.name,
                    userUsername = user.username,
                    userProfileUrl = user.profileImageUrl,
                    content = content,
                    imageUrls = imageUrls,
                    privacy = privacy,
                    createdAt = now,
                    updatedAt = now
                )

                firestore.collection("posts").document(postId)
                    .set(post)
                    .await()

                _isUploading.value = false
                loadPosts()
                onSuccess()
            } catch (e: Exception) {
                _isUploading.value = false
                onError(e.message ?: "Failed to create post")
            }
        }
    }

    fun updatePostPrivacy(postId: String, privacy: String) {
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(postId)
                    .update(mapOf(
                        "privacy" to privacy,
                        "updatedAt" to System.currentTimeMillis()
                    ))
                    .await()
                loadPosts()
            } catch (e: Exception) {
                Log.e("UpdatePostPrivacy", "Error: ${e.message}")
            }
        }
    }

    fun editPost(postId: String, newContent: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(postId)
                    .update(mapOf(
                        "content" to newContent,
                        "updatedAt" to System.currentTimeMillis()
                    ))
                    .await()
                loadPosts()
                onSuccess()
            } catch (e: Exception) {
                Log.e("EditPost", "Error: ${e.message}")
            }
        }
    }

    fun deletePost(postId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(postId)
                    .update(mapOf(
                        "isDeleted" to true,
                        "updatedAt" to System.currentTimeMillis()
                    ))
                    .await()
                loadPosts()
                onSuccess()
            } catch (e: Exception) {
                Log.e("DeletePost", "Error: ${e.message}")
            }
        }
    }

    fun reactToPost(postId: String, reaction: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(postId)
                    .update("reactions.$uid", reaction)
                    .await()
            } catch (e: Exception) {
                Log.e("ReactPost", "Error: ${e.message}")
            }
        }
    }

    fun removeReaction(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(postId)
                    .update("reactions.$uid", FieldValue.delete())
                    .await()
            } catch (e: Exception) {
                Log.e("RemoveReaction", "Error: ${e.message}")
            }
        }
    }

    fun viewPost(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(postId)
                    .update("views", FieldValue.arrayUnion(uid))
                    .await()
            } catch (e: Exception) {
                Log.e("ViewPost", "Error: ${e.message}")
            }
        }
    }

    // ==================== Comments ====================
    private val _commentsState = MutableStateFlow<List<Comment>>(emptyList())
    val commentsState: StateFlow<List<Comment>> = _commentsState.asStateFlow()

    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("comments")
                    .whereEqualTo("postId", postId)
                    .whereEqualTo("isDeleted", false)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("LoadComments", "Error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            _commentsState.value = snapshot.documents.mapNotNull {
                                it.toObject(Comment::class.java)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadComments", "Error: ${e.message}")
            }
        }
    }

    fun addComment(postId: String, content: String, replyToCommentId: String = "") {
        val uid = auth.currentUser?.uid ?: return
        val user = _currentUserState.value ?: return

        viewModelScope.launch {
            try {
                val commentId = firestore.collection("comments").document().id
                val replyData = if (replyToCommentId.isNotBlank()) {
                    val comment = _commentsState.value.find { it.commentId == replyToCommentId }
                    Pair(replyToCommentId, comment?.userName ?: "")
                } else {
                    Pair("", "")
                }

                val comment = Comment(
                    commentId = commentId,
                    postId = postId,
                    userId = uid,
                    userName = user.name,
                    userUsername = user.username,
                    userProfileUrl = user.profileImageUrl,
                    content = content,
                    replyToCommentId = replyData.first,
                    replyToUserName = replyData.second,
                    createdAt = System.currentTimeMillis()
                )

                firestore.collection("comments").document(commentId)
                    .set(comment)
                    .await()

                // Update comment count
                firestore.collection("posts").document(postId)
                    .update("commentCount", FieldValue.increment(1))
                    .await()

                loadComments(postId)
            } catch (e: Exception) {
                Log.e("AddComment", "Error: ${e.message}")
            }
        }
    }

    // ==================== Groups ====================
    fun loadGroups() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("groups")
                    .whereArrayContains("memberIds", uid)
                    .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("LoadGroups", "Error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            _groupsState.value = snapshot.documents.mapNotNull {
                                it.toObject(Group::class.java)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadGroups", "Error: ${e.message}")
            }
        }
    }

    fun createGroup(
        name: String,
        description: String,
        imageUri: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        val user = _currentUserState.value ?: return

        viewModelScope.launch {
            _isUploading.value = true
            try {
                var imageUrl = ""
                if (imageUri.isNotBlank()) {
                    try {
                        imageUrl = uploadToSupabase(Uri.parse(imageUri), "groups/${System.currentTimeMillis()}")
                    } catch (e: Exception) {
                        Log.e("GroupImage", "Failed: ${e.message}")
                    }
                }

                val groupId = firestore.collection("groups").document().id
                val group = Group(
                    groupId = groupId,
                    name = name,
                    description = description,
                    imageUrl = imageUrl,
                    adminIds = listOf(uid),
                    memberIds = listOf(uid, user.uid),
                    createdBy = uid,
                    createdAt = System.currentTimeMillis()
                )

                firestore.collection("groups").document(groupId)
                    .set(group)
                    .await()

                _isUploading.value = false
                loadGroups()
                onSuccess()
            } catch (e: Exception) {
                _isUploading.value = false
                onError(e.message ?: "Failed to create group")
            }
        }
    }

    fun addMemberToGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("groups").document(groupId)
                    .update("memberIds", FieldValue.arrayUnion(userId))
                    .await()
                loadGroups()
            } catch (e: Exception) {
                Log.e("AddMember", "Error: ${e.message}")
            }
        }
    }

    fun removeMemberFromGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("groups").document(groupId)
                    .update("memberIds", FieldValue.arrayRemove(userId))
                    .await()
                loadGroups()
            } catch (e: Exception) {
                Log.e("RemoveMember", "Error: ${e.message}")
            }
        }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                firestore.collection("groups").document(groupId)
                    .delete()
                    .await()
                loadGroups()
                onSuccess()
            } catch (e: Exception) {
                Log.e("DeleteGroup", "Error: ${e.message}")
            }
        }
    }

    // ==================== Notes ====================
    fun loadNotes() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("notes")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("isArchived", false)
                    .orderBy("isPinned", Query.Direction.DESCENDING)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("LoadNotes", "Error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            _notesState.value = snapshot.documents.mapNotNull {
                                it.toObject(Note::class.java)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadNotes", "Error: ${e.message}")
            }
        }
    }

    fun createNote(
        title: String,
        content: String,
        color: String = "#6C63FF",
        tags: List<String> = emptyList(),
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val noteId = firestore.collection("notes").document().id
                val now = System.currentTimeMillis()
                val note = Note(
                    noteId = noteId,
                    userId = uid,
                    title = title,
                    content = content,
                    color = color,
                    tags = tags,
                    createdAt = now,
                    updatedAt = now
                )

                firestore.collection("notes").document(noteId)
                    .set(note)
                    .await()

                loadNotes()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create note")
            }
        }
    }

    fun updateNote(noteId: String, title: String, content: String, color: String = "#6C63FF") {
        viewModelScope.launch {
            try {
                firestore.collection("notes").document(noteId)
                    .update(mapOf(
                        "title" to title,
                        "content" to content,
                        "color" to color,
                        "updatedAt" to System.currentTimeMillis()
                    ))
                    .await()
                loadNotes()
            } catch (e: Exception) {
                Log.e("UpdateNote", "Error: ${e.message}")
            }
        }
    }

    fun togglePinNote(noteId: String, isPinned: Boolean) {
        viewModelScope.launch {
            try {
                firestore.collection("notes").document(noteId)
                    .update("isPinned", isPinned)
                    .await()
                loadNotes()
            } catch (e: Exception) {
                Log.e("TogglePin", "Error: ${e.message}")
            }
        }
    }

    fun archiveNote(noteId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("notes").document(noteId)
                    .update("isArchived", true)
                    .await()
                loadNotes()
            } catch (e: Exception) {
                Log.e("ArchiveNote", "Error: ${e.message}")
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("notes").document(noteId)
                    .delete()
                    .await()
                loadNotes()
            } catch (e: Exception) {
                Log.e("DeleteNote", "Error: ${e.message}")
            }
        }
    }

    // ==================== Notifications ====================
    fun loadNotifications() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("notifications")
                    .whereEqualTo("toUserId", uid)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("LoadNotifications", "Error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            _notificationsState.value = snapshot.documents.mapNotNull {
                                it.toObject(AppNotification::class.java)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("LoadNotifications", "Error: ${e.message}")
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("notifications").document(notificationId)
                    .update("isRead", true)
                    .await()
            } catch (e: Exception) {
                Log.e("MarkRead", "Error: ${e.message}")
            }
        }
    }

    fun getUnreadNotificationCount(): Int {
        return _notificationsState.value.count { !it.isRead }
    }

    // ==================== Settings ====================
    fun updateTheme(theme: String) {
        _selectedTheme.value = theme
        sharedPrefs.edit().putString("theme", theme).apply()
        
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("theme", theme)
                    .await()
            } catch (e: Exception) {
                Log.e("UpdateTheme", "Error: ${e.message}")
            }
        }
    }

    fun updateAccentColor(color: String) {
        _selectedAccentColor.value = color
        sharedPrefs.edit().putString("accent_color", color).apply()
        
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("accentColor", color)
                    .await()
            } catch (e: Exception) {
                Log.e("UpdateAccent", "Error: ${e.message}")
            }
        }
    }

    // ==================== Webhook/Notification ====================
    private fun listenToGlobalConfig() {
        try {
            firestore.collection("config").document("app_settings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirestoreConfig", "Error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val url = snapshot.getString("webhookUrl") ?: snapshot.getString("workerUrl")
                        if (!url.isNullOrBlank()) {
                            _webhookUrl.value = url
                            sharedPrefs.edit().putString("webhook_url", url).apply()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("FirestoreConfig", "Error: ${e.message}")
        }
    }

    fun updateWebhookUrl(url: String) {
        _webhookUrl.value = url
        sharedPrefs.edit().putString("webhook_url", url).apply()
        
        try {
            firestore.collection("config").document("app_settings")
                .set(mapOf("webhookUrl" to url, "workerUrl" to url), SetOptions.merge())
        } catch (e: Exception) {
            Log.e("UpdateWebhook", "Error: ${e.message}")
        }
    }

    private fun sendPushNotification(fcmToken: String, title: String, body: String) {
        val webhookUrl = _webhookUrl.value
        if (webhookUrl.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Send to n8n webhook for FCM notification
                // Implementation depends on your n8n workflow
                Log.d("PushNotification", "Would send to: $webhookUrl, Title: $title")
            } catch (e: Exception) {
                Log.e("PushNotification", "Error: ${e.message}")
            }
        }
    }

    private fun retrieveFCMTokenAndStore(uid: String) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                firestore.collection("users").document(uid)
                    .update("fcmToken", token)
            }
            .addOnFailureListener { e ->
                Log.e("FCMToken", "Failed: ${e.message}")
            }
    }

    // ==================== Supabase Image Upload ====================
    private suspend fun uploadToSupabase(uri: Uri, path: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Could not read file")
                inputStream.close()
                
                val fileName = "${UUID.randomUUID()}.jpg"
                val bucket = supabase.storage.from("firechat-media")
                
                bucket.upload("$path/$fileName", bytes, upsert = true)
                
                bucket.publicUrl("$path/$fileName")
            } catch (e: Exception) {
                Log.e("SupabaseUpload", "Error: ${e.message}")
                throw e
            }
        }
    }

    // ==================== Utility ====================
    fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> "${diff / 604_800_000}w ago"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningToChat()
        updateOnlineStatus(false)
    }
}
