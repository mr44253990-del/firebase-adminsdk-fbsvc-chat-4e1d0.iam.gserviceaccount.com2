package com.example.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Privacy controls are opt-in and stored locally until server-side sync is explicitly enabled. */
data class PrivacySettings(
    val showOnlineStatus: Boolean = true,
    val showLastSeen: Boolean = true,
    val showTypingStatus: Boolean = true,
    val sendReadReceipts: Boolean = true,
    val allowProfileDiscovery: Boolean = true,
    val allowLinkPreviewFetch: Boolean = true,
    val allowAiProcessing: Boolean = false,
    val hideNotificationContent: Boolean = true,
    val preventScreenshotOnSensitiveScreens: Boolean = false,
    /** 0 = persistent; otherwise outgoing messages expire after this many seconds. */
    val disappearingMessageSeconds: Int = 0
)

object PrivacyPreferences {
    private const val PREFS = "convo_privacy_settings"
    private val _settings = MutableStateFlow(PrivacySettings())
    val settings: StateFlow<PrivacySettings> = _settings

    fun load(context: Context): PrivacySettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PrivacySettings(
            showOnlineStatus = p.getBoolean("showOnlineStatus", true),
            showLastSeen = p.getBoolean("showLastSeen", true),
            showTypingStatus = p.getBoolean("showTypingStatus", true),
            sendReadReceipts = p.getBoolean("sendReadReceipts", true),
            allowProfileDiscovery = p.getBoolean("allowProfileDiscovery", true),
            allowLinkPreviewFetch = p.getBoolean("allowLinkPreviewFetch", true),
            allowAiProcessing = p.getBoolean("allowAiProcessing", false),
            hideNotificationContent = p.getBoolean("hideNotificationContent", true),
            preventScreenshotOnSensitiveScreens = p.getBoolean("preventScreenshotOnSensitiveScreens", false),
            disappearingMessageSeconds = p.getInt("disappearingMessageSeconds", 0)
        ).also { _settings.value = it }
    }

    fun initialize(context: Context) = load(context)

    fun update(context: Context, transform: (PrivacySettings) -> PrivacySettings): PrivacySettings {
        val next = transform(_settings.value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("showOnlineStatus", next.showOnlineStatus)
            .putBoolean("showLastSeen", next.showLastSeen)
            .putBoolean("showTypingStatus", next.showTypingStatus)
            .putBoolean("sendReadReceipts", next.sendReadReceipts)
            .putBoolean("allowProfileDiscovery", next.allowProfileDiscovery)
            .putBoolean("allowLinkPreviewFetch", next.allowLinkPreviewFetch)
            .putBoolean("allowAiProcessing", next.allowAiProcessing)
            .putBoolean("hideNotificationContent", next.hideNotificationContent)
            .putBoolean("preventScreenshotOnSensitiveScreens", next.preventScreenshotOnSensitiveScreens)
            .putInt("disappearingMessageSeconds", next.disappearingMessageSeconds)
            .apply()
        _settings.value = next
        return next
    }
}
