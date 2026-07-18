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
    val createdAt: Long = 0L,
    val friends: List<String> = emptyList(),
    val bio: String = "",
    val coverImageUrl: String = ""
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
    val voiceDurationSec: Int? = null,
    val seenByRecipient: Boolean = false,
    val reactions: Map<String, String> = emptyMap()
)

data class Story(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderProfilePic: String = "",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val reactions: Map<String, String> = emptyMap(), // userId to reaction symbol (like, love, etc)
    val comments: List<StoryComment> = emptyList(),
    val viewers: List<String> = emptyList()
)

data class StoryComment(
    val commentId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

data class Post(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderProfilePic: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val audioUrl: String = "",
    val videoUrl: String = "",
    val timestamp: Long = 0L,
    val reactions: Map<String, String> = emptyMap(), // userId -> reaction type
    val comments: List<PostComment> = emptyList(),
    val viewsCount: Int = 0,
    val isPrivate: Boolean = false,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val taggedUserIds: List<String> = emptyList(),
    val feeling: String = ""
)

data class PostComment(
    val commentId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

data class Group(
    val id: String = "",
    val name: String = "",
    val profileUrl: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val lastMessage: String = "",
    val createdBy: String = ""
)

data class GroupMessage(
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val imageUrl: String? = null,
    val voiceUrl: String? = null,
    val voiceDurationSec: Int? = null
)
