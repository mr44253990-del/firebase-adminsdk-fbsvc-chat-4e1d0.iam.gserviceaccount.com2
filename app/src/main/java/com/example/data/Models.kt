package com.example.data

/** User profile stored in Firestore `users/{uid}`. */
data class User(
    val uid: String = "",
    val name: String = "",
    val dob: String = "",
    val username: String = "",
    val fcmToken: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val theme: String = "aurora",
    val activityStatusEnabled: Boolean = true,
    val blockedUsers: List<String> = emptyList(),
    val createdAt: Long = 0L
)

/** Presence record from RTDB `status/{uid}`. */
data class Presence(
    val online: Boolean = false,
    val lastChanged: Long = 0L
)

/** Chat message – realtime DB. Supports text / image / voice + reply / forward / reactions. */
data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val senderPhoto: String = "",
    val text: String = "",
    val type: String = "text", // text | image | voice
    val mediaUrl: String = "",
    val voiceDuration: Long = 0L,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val forwarded: Boolean = false,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val reactions: Map<String, String> = emptyMap(), // uid -> emoji
    val timestamp: Long = 0L
)

/** Conversation preview for the chat list. */
data class Conversation(
    val chatId: String = "",
    val partnerId: String = "",
    val partnerName: String = "",
    val partnerUsername: String = "",
    val partnerPhoto: String = "",
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastTimestamp: Long = 0L,
    val lastSenderId: String = "",
    val unreadCount: Int = 0,
    val isGroup: Boolean = false
)

/** Group chat metadata stored in Firestore `groups/{id}`. */
data class Group(
    val groupId: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val backgroundUrl: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L
)

/** Feed post stored in Firestore `posts/{id}`. */
data class Post(
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorPhoto: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "none", // none | image | video
    val visibility: String = "public", // public | private
    val likes: Map<String, Boolean> = emptyMap(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    val createdAt: Long = 0L,
    val edited: Boolean = false
)

/** Comment under a post: `posts/{postId}/comments/{id}`. */
data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)

/** Story – expires automatically 12 hours after creation. */
data class Story(
    val storyId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorPhoto: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image", // image | video | text
    val text: String = "",
    val reactions: Map<String, String> = emptyMap(), // uid -> emoji
    val viewers: Map<String, Boolean> = emptyMap(),
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt in 1..now
}

/** Personal quick note stored in Firestore `users/{uid}/notes/{id}`. */
data class Note(
    val noteId: String = "",
    val text: String = "",
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val createdAt: Long = 0L
)

/** In-app notification: `notifications/{uid}/items/{id}`. */
data class AppNotification(
    val id: String = "",
    val type: String = "message", // message | like | comment | story_react | group
    val fromUid: String = "",
    val fromName: String = "",
    val fromPhoto: String = "",
    val text: String = "",
    val refId: String = "",
    val read: Boolean = false,
    val timestamp: Long = 0L
)

/** Typing indicator record from RTDB `typing/{chatId}/{uid}`. */
data class TypingState(
    val typing: Boolean = false
)
