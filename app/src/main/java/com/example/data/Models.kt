package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

// ==================== User Model ====================
data class User(
    @DocumentId
    val uid: String = "",
    val name: String = "",
    val dob: String = "",
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val coverImageUrl: String = "",
    val fcmToken: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val isActivityVisible: Boolean = true,
    val theme: String = "system",
    val accentColor: String = "#6C63FF",
    val createdAt: Long = 0L,
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList()
)

// ==================== Message Model ====================
data class Message(
    @DocumentId
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val senderProfileUrl: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val voiceUrl: String = "",
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val timestamp: Long = 0L,
    val chatId: String = ""
)

data class Chat(
    @DocumentId
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Map<String, Int> = emptyMap(),
    val createdAt: Long = 0L
)

// ==================== Story Model ====================
data class Story(
    @DocumentId
    val storyId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userUsername: String = "",
    val userProfileUrl: String = "",
    val imageUrl: String = "",
    val caption: String = "",
    val views: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isActive: Boolean = true
)

// ==================== Post Model ====================
data class Post(
    @DocumentId
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userUsername: String = "",
    val userProfileUrl: String = "",
    val content: String = "",
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String = "",
    val privacy: String = "public", // public, friends, private
    val reactions: Map<String, String> = emptyMap(),
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val views: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

// ==================== Comment Model ====================
data class Comment(
    @DocumentId
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userUsername: String = "",
    val userProfileUrl: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val replyToCommentId: String = "",
    val replyToUserName: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val isDeleted: Boolean = false
)

// ==================== Group Model ====================
data class Group(
    @DocumentId
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val backgroundUrl: String = "",
    val adminIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = ""
)

data class GroupMessage(
    @DocumentId
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val senderProfileUrl: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val timestamp: Long = 0L
)

// ==================== Note Model ====================
data class Note(
    @DocumentId
    val noteId: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val color: String = "#6C63FF",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

// ==================== Notification Model ====================
data class AppNotification(
    @DocumentId
    val notificationId: String = "",
    val type: String = "", // message, story, post, comment, reaction, follow, group
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserProfileUrl: String = "",
    val toUserId: String = "",
    val title: String = "",
    val body: String = "",
    val referenceId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp
    val timestamp: Timestamp? = null
)

// ==================== Theme/Setting Models ====================
enum class AppTheme(val value: String, val displayName: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System Default")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val accentColor: String = "#6C63FF",
    val isNotificationsEnabled: Boolean = true,
    val isMessagePreviewEnabled: Boolean = true,
    val isOnlineStatusVisible: Boolean = true
)

// ==================== Reaction Types ====================
object ReactionTypes {
    const val LIKE = "like"
    const val LOVE = "love"
    const val HAHA = "haha"
    const val WOW = "wow"
    const val SAD = "sad"
    const val ANGRY = "angry"
    
    val all = listOf(LIKE, LOVE, HAHA, WOW, SAD, ANGRY)
    
    val emojiMap = mapOf(
        LIKE to "👍",
        LOVE to "❤️",
        HAHA to "😂",
        WOW to "😮",
        SAD to "😢",
        ANGRY to "😠"
    )
}

// ==================== Privacy Options ====================
object PrivacyOptions {
    const val PUBLIC = "public"
    const val FRIENDS = "friends"
    const val PRIVATE = "private"
    
    val all = listOf(PUBLIC, FRIENDS, PRIVATE)
    
    val displayNames = mapOf(
        PUBLIC to "Public",
        FRIENDS to "Friends Only",
        PRIVATE to "Private"
    )
    
    val icons = mapOf(
        PUBLIC to "🌍",
        FRIENDS to "👥",
        PRIVATE to "🔒"
    )
}
