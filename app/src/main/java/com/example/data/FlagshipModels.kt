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

data class PremiumRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userImageUrl: String = "",
    val plan: String = "monthly",
    val paymentMethod: String = "bkash",
    val transactionId: String = "",
    val amount: Int = 0,
    val status: String = "pending",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L,
    val reviewedBy: String = "",
    val adminNote: String = ""
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
    val assistantEnabled: Boolean = true,
    val aiModel: String = "mistral-small-latest",
    val aiDisplayName: String = "FireChat Assistant",
    val aiSystemPrompt: String = "Help users manage FireChat safely and clearly.",
    val premiumEnabled: Boolean = true,
    val premiumPaymentNumber: String = "01755070708",
    val premiumMonthlyPrice: Int = 199,
    val premiumYearlyPrice: Int = 1499,
    val premiumLifetimePrice: Int = 3999,
    val premiumBkashEnabled: Boolean = true,
    val premiumNagadEnabled: Boolean = true,
    val premiumRocketEnabled: Boolean = true,
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
)
