package com.example.data

data class User(
    val uid: String = "",
    val name: String = "",
    val dob: String = "",
    val username: String = "",
    val fcmToken: String = "",
    val createdAt: Long = 0L
)

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)
