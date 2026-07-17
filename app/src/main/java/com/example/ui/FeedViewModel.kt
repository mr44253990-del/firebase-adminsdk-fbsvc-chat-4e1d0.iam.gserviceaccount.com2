package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppNotification
import com.example.data.Comment
import com.example.data.Note
import com.example.data.Post
import com.example.data.Story
import com.example.data.SupabaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Feed engine — Facebook/Instagram style home feed:
 * posts (like / comment / views / privacy), stories (12h auto-expiry),
 * in-app notifications and personal notes. Everything cached offline by Firestore.
 */
class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private fun fs(): FirebaseFirestore = FirebaseFirestore.getInstance()
    private fun myUid(): String? = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _myPosts = MutableStateFlow<List<Post>>(emptyList())
    val myPosts: StateFlow<List<Post>> = _myPosts.asStateFlow()

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _feedLoading = MutableStateFlow(true)
    val feedLoading: StateFlow<Boolean> = _feedLoading.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val viewedThisSession = mutableSetOf<String>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        listenPosts()
        listenStories()
        listenNotifications()
        listenNotes()
    }

    // ---------------- Posts ----------------
    private fun listenPosts() {
        val uid = myUid() ?: return
        try {
            fs().collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) { _feedLoading.value = false; return@addSnapshotListener }
                    val all = snapshot.documents.mapNotNull { parsePost(it) }
                    _posts.value = all.filter { it.visibility == "public" || it.authorId == uid }
                    _myPosts.value = all.filter { it.authorId == uid }
                    _feedLoading.value = false
                }
        } catch (e: Exception) { _feedLoading.value = false }
    }

    private fun parsePost(doc: com.google.firebase.firestore.DocumentSnapshot): Post? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val likes = (doc.get("likes") as? Map<String, Boolean>) ?: emptyMap()
            Post(
                postId = doc.id,
                authorId = doc.getString("authorId") ?: "",
                authorName = doc.getString("authorName") ?: "",
                authorUsername = doc.getString("authorUsername") ?: "",
                authorPhoto = doc.getString("authorPhoto") ?: "",
                text = doc.getString("text") ?: "",
                mediaUrl = doc.getString("mediaUrl") ?: "",
                mediaType = doc.getString("mediaType") ?: "none",
                visibility = doc.getString("visibility") ?: "public",
                likes = likes,
                likeCount = (doc.getLong("likeCount") ?: 0L).toInt(),
                commentCount = (doc.getLong("commentCount") ?: 0L).toInt(),
                viewCount = (doc.getLong("viewCount") ?: 0L).toInt(),
                createdAt = doc.getLong("createdAt") ?: 0L,
                edited = doc.getBoolean("edited") ?: false
            )
        } catch (e: Exception) { null }
    }

    fun createPost(text: String, mediaUri: Uri?, isVideo: Boolean, visibility: String, author: com.example.data.User?, onDone: (Boolean) -> Unit) {
        val me = author ?: return
        if (text.isBlank() && mediaUri == null) { onDone(false); return }
        viewModelScope.launch {
            _uploading.value = true
            var mediaUrl = ""
            var mediaType = "none"
            if (mediaUri != null) {
                val bytes = readMediaBytes(mediaUri, isVideo)
                if (bytes != null) {
                    val ext = if (isVideo) "mp4" else "jpg"
                    val mime = if (isVideo) "video/mp4" else "image/jpeg"
                    mediaUrl = SupabaseManager.upload(
                        SupabaseManager.BUCKET_POSTS,
                        "${me.uid}/${UUID.randomUUID()}.$ext", bytes, mime
                    ) ?: ""
                    mediaType = if (isVideo) "video" else "image"
                }
            }
            val post = hashMapOf(
                "authorId" to me.uid, "authorName" to me.name,
                "authorUsername" to me.username, "authorPhoto" to me.photoUrl,
                "text" to text, "mediaUrl" to mediaUrl, "mediaType" to mediaType,
                "visibility" to visibility, "likes" to emptyMap<String, Boolean>(),
                "likeCount" to 0, "commentCount" to 0, "viewCount" to 0,
                "createdAt" to System.currentTimeMillis(), "edited" to false
            )
            try {
                fs().collection("posts").add(post)
                    .addOnSuccessListener { _uploading.value = false; onDone(true) }
                    .addOnFailureListener { _uploading.value = false; onDone(false) }
            } catch (e: Exception) { _uploading.value = false; onDone(false) }
        }
    }

    private suspend fun readMediaBytes(uri: Uri, isVideo: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val cr = getApplication<Application>().contentResolver
            if (isVideo) {
                cr.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val bmp = android.graphics.BitmapFactory.decodeStream(
                    cr.openInputStream(uri)
                ) ?: return@withContext null
                val maxDim = 1600
                val scale = if (maxOf(bmp.width, bmp.height) > maxDim)
                    maxDim.toFloat() / maxOf(bmp.width, bmp.height) else 1f
                val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                ) else bmp
                val out = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
        } catch (e: Exception) { null }
    }

    fun toggleLike(post: Post, currentUser: com.example.data.User?) {
        val uid = myUid() ?: return
        val me = currentUser ?: return
        try {
            val ref = fs().collection("posts").document(post.postId)
            if (post.likes.containsKey(uid)) {
                ref.update(
                    mapOf("likes.$uid" to FieldValue.delete(), "likeCount" to FieldValue.increment(-1))
                )
            } else {
                ref.update(
                    mapOf("likes.$uid" to true, "likeCount" to FieldValue.increment(1))
                )
                if (post.authorId != uid) {
                    writeFeedNotification(post.authorId, "like", me, "liked your post", post.postId)
                }
            }
        } catch (_: Exception) {}
    }

    fun incrementView(post: Post) {
        if (post.postId in viewedThisSession) return
        viewedThisSession.add(post.postId)
        try {
            fs().collection("posts").document(post.postId)
                .update("viewCount", FieldValue.increment(1))
        } catch (_: Exception) {}
    }

    /** Edit text and/or visibility — can switch public<->private anytime. */
    fun editPost(post: Post, newText: String, newVisibility: String) {
        try {
            fs().collection("posts").document(post.postId).update(
                mapOf("text" to newText, "visibility" to newVisibility, "edited" to true)
            )
        } catch (_: Exception) {}
    }

    /** Deletes post doc + comments + media from Supabase Storage — gone everywhere. */
    fun deletePost(post: Post) {
        viewModelScope.launch {
            try {
                val comments = fs().collection("posts").document(post.postId)
                    .collection("comments").get().await()
                comments.documents.forEach { it.reference.delete() }
                fs().collection("posts").document(post.postId).delete()
            } catch (_: Exception) {}
            if (post.mediaUrl.isNotBlank()) SupabaseManager.deleteByUrl(post.mediaUrl)
        }
    }

    // ---------------- Comments ----------------
    fun listenComments(postId: String) {
        try {
            fs().collection("posts").document(postId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    _comments.value = snapshot.documents.mapNotNull { d ->
                        try {
                            Comment(
                                commentId = d.id, postId = postId,
                                authorId = d.getString("authorId") ?: "",
                                authorName = d.getString("authorName") ?: "",
                                authorPhoto = d.getString("authorPhoto") ?: "",
                                text = d.getString("text") ?: "",
                                createdAt = d.getLong("createdAt") ?: 0L
                            )
                        } catch (e: Exception) { null }
                    }
                }
        } catch (_: Exception) {}
    }

    fun addComment(post: Post, text: String, author: com.example.data.User?) {
        val me = author ?: return
        if (text.isBlank()) return
        try {
            val comment = hashMapOf(
                "authorId" to me.uid, "authorName" to me.name,
                "authorPhoto" to me.photoUrl, "text" to text,
                "createdAt" to System.currentTimeMillis()
            )
            fs().collection("posts").document(post.postId).collection("comments").add(comment)
            fs().collection("posts").document(post.postId)
                .update("commentCount", FieldValue.increment(1))
            if (post.authorId != me.uid) {
                writeFeedNotification(post.authorId, "comment", me, "commented: ${text.take(60)}", post.postId)
            }
        } catch (_: Exception) {}
    }

    // ---------------- Stories (12h auto-expiry) ----------------
    private fun listenStories() {
        try {
            fs().collection("stories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val now = System.currentTimeMillis()
                    val active = mutableListOf<Story>()
                    snapshot.documents.forEach { d ->
                        val s = parseStory(d)
                        if (s != null) {
                            if (s.isExpired(now)) {
                                // auto-purge expired stories from db + storage
                                d.reference.delete()
                                if (s.mediaUrl.isNotBlank()) {
                                    viewModelScope.launch { SupabaseManager.deleteByUrl(s.mediaUrl) }
                                }
                            } else active.add(s)
                        }
                    }
                    _stories.value = active
                }
        } catch (_: Exception) {}
    }

    private fun parseStory(doc: com.google.firebase.firestore.DocumentSnapshot): Story? {
        return try {
            @Suppress("UNCHECKED_CAST")
            Story(
                storyId = doc.id,
                authorId = doc.getString("authorId") ?: "",
                authorName = doc.getString("authorName") ?: "",
                authorUsername = doc.getString("authorUsername") ?: "",
                authorPhoto = doc.getString("authorPhoto") ?: "",
                mediaUrl = doc.getString("mediaUrl") ?: "",
                mediaType = doc.getString("mediaType") ?: "image",
                text = doc.getString("text") ?: "",
                reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap(),
                viewers = (doc.get("viewers") as? Map<String, Boolean>) ?: emptyMap(),
                createdAt = doc.getLong("createdAt") ?: 0L,
                expiresAt = doc.getLong("expiresAt") ?: 0L
            )
        } catch (e: Exception) { null }
    }

    /** Add story — disappears automatically after 12 hours. */
    fun addStory(mediaUri: Uri?, isVideo: Boolean, text: String, author: com.example.data.User?, onDone: (Boolean) -> Unit) {
        val me = author ?: return
        viewModelScope.launch {
            _uploading.value = true
            var mediaUrl = ""
            var mediaType = "text"
            if (mediaUri != null) {
                val bytes = readMediaBytes(mediaUri, isVideo)
                if (bytes != null) {
                    val ext = if (isVideo) "mp4" else "jpg"
                    val mime = if (isVideo) "video/mp4" else "image/jpeg"
                    mediaUrl = SupabaseManager.upload(
                        SupabaseManager.BUCKET_STORIES,
                        "${me.uid}/${UUID.randomUUID()}.$ext", bytes, mime
                    ) ?: ""
                    mediaType = if (isVideo) "video" else "image"
                }
            }
            val now = System.currentTimeMillis()
            val story = hashMapOf(
                "authorId" to me.uid, "authorName" to me.name,
                "authorUsername" to me.username, "authorPhoto" to me.photoUrl,
                "mediaUrl" to mediaUrl, "mediaType" to mediaType, "text" to text,
                "reactions" to emptyMap<String, String>(),
                "viewers" to emptyMap<String, Boolean>(),
                "createdAt" to now, "expiresAt" to now + 12L * 60L * 60L * 1000L
            )
            try {
                fs().collection("stories").add(story)
                    .addOnSuccessListener { _uploading.value = false; onDone(true) }
                    .addOnFailureListener { _uploading.value = false; onDone(false) }
            } catch (e: Exception) { _uploading.value = false; onDone(false) }
        }
    }

    fun markStoryViewed(story: Story) {
        val uid = myUid() ?: return
        if (story.viewers.containsKey(uid)) return
        try {
            fs().collection("stories").document(story.storyId)
                .update("viewers.$uid", true)
        } catch (_: Exception) {}
    }

    fun reactToStory(story: Story, emoji: String, currentUser: com.example.data.User?) {
        val uid = myUid() ?: return
        val me = currentUser ?: return
        try {
            fs().collection("stories").document(story.storyId)
                .update("reactions.$uid", emoji)
            if (story.authorId != uid) {
                writeFeedNotification(story.authorId, "story_react", me, "reacted $emoji to your story", story.storyId)
            }
        } catch (_: Exception) {}
    }

    /** Delete own story — removes from Firestore AND Supabase Storage instantly. */
    fun deleteStory(story: Story) {
        viewModelScope.launch {
            try { fs().collection("stories").document(story.storyId).delete() } catch (_: Exception) {}
            if (story.mediaUrl.isNotBlank()) SupabaseManager.deleteByUrl(story.mediaUrl)
        }
    }

    // ---------------- Notifications ----------------
    private fun listenNotifications() {
        val uid = myUid() ?: return
        try {
            fs().collection("notifications").document(uid).collection("items")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(80)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val items = snapshot.documents.mapNotNull { d ->
                        try {
                            AppNotification(
                                id = d.id,
                                type = d.getString("type") ?: "message",
                                fromUid = d.getString("fromUid") ?: "",
                                fromName = d.getString("fromName") ?: "",
                                fromPhoto = d.getString("fromPhoto") ?: "",
                                text = d.getString("text") ?: "",
                                refId = d.getString("refId") ?: "",
                                read = d.getBoolean("read") ?: false,
                                timestamp = d.getLong("timestamp") ?: 0L
                            )
                        } catch (e: Exception) { null }
                    }
                    _notifications.value = items
                    _unreadNotifications.value = items.count { !it.read }
                }
        } catch (_: Exception) {}
    }

    fun markAllNotificationsRead() {
        val uid = myUid() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = fs().collection("notifications").document(uid).collection("items")
                    .whereEqualTo("read", false).get().await()
                items.documents.forEach { it.reference.update("read", true) }
            } catch (_: Exception) {}
        }
    }

    private fun writeFeedNotification(toUid: String, type: String, from: com.example.data.User, text: String, refId: String) {
        if (toUid == from.uid) return
        try {
            val item = hashMapOf(
                "type" to type, "fromUid" to from.uid, "fromName" to from.name,
                "fromPhoto" to from.photoUrl, "text" to text, "refId" to refId,
                "read" to false, "timestamp" to System.currentTimeMillis()
            )
            fs().collection("notifications").document(toUid).collection("items").add(item)
        } catch (_: Exception) {}
    }

    // ---------------- Notes ----------------
    private fun listenNotes() {
        val uid = myUid() ?: return
        try {
            fs().collection("users").document(uid).collection("notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    _notes.value = snapshot.documents.mapNotNull { d ->
                        try {
                            Note(
                                noteId = d.id,
                                text = d.getString("text") ?: "",
                                colorIndex = (d.getLong("colorIndex") ?: 0L).toInt(),
                                pinned = d.getBoolean("pinned") ?: false,
                                createdAt = d.getLong("createdAt") ?: 0L
                            )
                        } catch (e: Exception) { null }
                    }
                }
        } catch (_: Exception) {}
    }

    fun addNote(text: String, colorIndex: Int) {
        val uid = myUid() ?: return
        if (text.isBlank()) return
        try {
            fs().collection("users").document(uid).collection("notes").add(
                hashMapOf(
                    "text" to text, "colorIndex" to colorIndex, "pinned" to false,
                    "createdAt" to System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    fun deleteNote(note: Note) {
        val uid = myUid() ?: return
        try {
            fs().collection("users").document(uid).collection("notes")
                .document(note.noteId).delete()
        } catch (_: Exception) {}
    }

    fun togglePinNote(note: Note) {
        val uid = myUid() ?: return
        try {
            fs().collection("users").document(uid).collection("notes")
                .document(note.noteId).set(mapOf("pinned" to !note.pinned), SetOptions.merge())
        } catch (_: Exception) {}
    }
}

// Small await() helper so we don't need kotlinx-coroutines-play-services
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) {} }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        addOnCanceledListener { cont.cancel() }
    }
