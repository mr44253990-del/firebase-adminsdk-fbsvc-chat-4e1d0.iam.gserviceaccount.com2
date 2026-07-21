package com.example.data

data class FeatureRequest(
    val id: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "pending",
    val adminNote: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FlagshipConfig(
    val updateEnabled: Boolean = false,
    val mandatoryUpdate: Boolean = false,
    val latestVersionCode: Int = 1,
    val minimumVersionCode: Int = 1,
    val versionName: String = "1.0",
    val apkUrl: String = "",
    val apkR2Key: String = "",
    // A new immutable ID is generated for every published APK campaign. It
    // allows a same-version signed reinstall to be required exactly once.
    val updateId: String = "",
    val updatePublishedAt: Long = 0L,
    val releaseNotes: String = "",
    val noticeEnabled: Boolean = false,
    val noticeTitle: String = "",
    val noticeBody: String = "",
    val maintenanceMode: Boolean = false,
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
)
