package com.example.data

/** Firestore notification copied to Room and then removed remotely after acknowledgement. */
data class ActivityNotification(
    val id: String = "",
    val ownerId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val actorImageUrl: String = "",
    val type: String = "",
    val targetId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false
)

data class FriendRequest(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val senderImageUrl: String = "",
    val status: String = "pending",
    val timestamp: Long = 0L
)

data class MessageRequest(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val preview: String = "",
    val status: String = "pending",
    val timestamp: Long = 0L
)
