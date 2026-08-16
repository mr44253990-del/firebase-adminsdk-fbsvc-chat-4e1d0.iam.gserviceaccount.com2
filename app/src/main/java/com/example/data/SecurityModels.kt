package com.example.data

data class DeviceSession(
    val sessionId: String = "",
    val deviceName: String = "Android device",
    val androidVersion: String = "",
    val appVersion: String = "",
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val active: Boolean = true,
    val maskedIp: String = "",
    val city: String = "Unknown",
    val region: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class UserReport(
    val id: String = "",
    val reporterId: String = "",
    val reporterEmail: String = "",
    val targetUid: String = "",
    val category: String = "other",
    val description: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L
)
