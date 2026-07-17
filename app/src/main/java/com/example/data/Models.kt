package com.example.data

data class User(
    val uid: String = "",
    val name: String = "",
    val dob: String = "",
    val username: String = "",
    val fcmToken: String = "",
    val profileImageUrl: String = "",
    val isOnline: Boolean = false,
    val lastActive: Long = 0L,
    val blockedUsers: List<String> = emptyList(),
    val createdAt: Long = 0L
)

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val edited: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val imageUrl: String? = null,
    val voiceUrl: String? = null,
    val voiceDurationSec: Int? = null
)
