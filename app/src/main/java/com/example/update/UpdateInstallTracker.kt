package com.example.update

import android.content.Context
import com.example.BuildConfig
import com.example.data.FlagshipConfig

/**
 * Tracks a published update campaign independently from versionCode.
 *
 * This is important when an administrator republishes a correctly signed APK
 * without changing its version. Android keeps this app's preferences during an
 * in-place install, while PackageManager updates lastUpdateTime.
 */
object UpdateInstallTracker {
    private const val PREFS = "firechat_update_install_state"
    private const val ACKNOWLEDGED_ID = "acknowledged_update_id"
    private const val PENDING_ID = "pending_update_id"
    private const val BEFORE_INSTALL_TIME = "before_install_time"

    fun campaignId(config: FlagshipConfig): String = config.updateId.ifBlank {
        listOf(
            config.updatePublishedAt.takeIf { it > 0L } ?: config.updatedAt,
            config.apkR2Key.ifBlank { config.apkUrl },
            config.latestVersionCode
        ).joinToString(":")
    }

    fun isInstalled(context: Context, config: FlagshipConfig): Boolean {
        if (!config.updateEnabled) return true
        val id = campaignId(config)
        if (id.isBlank() || id == "0::${config.latestVersionCode}") return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(ACKNOWLEDGED_ID, null) == id) return true

        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return false

        // A newer build always satisfies an older campaign.
        if (BuildConfig.VERSION_CODE > config.latestVersionCode) {
            acknowledge(context, id)
            return true
        }

        val pendingId = prefs.getString(PENDING_ID, null)
        val beforeInstall = prefs.getLong(BEFORE_INSTALL_TIME, Long.MAX_VALUE)
        val completedPendingInstall = pendingId == id && packageInfo.lastUpdateTime > beforeInstall

        // Handles a fresh install of the published APK on a new device, where
        // there was no previous FireChat process to save a pending marker.
        val publishedAt = config.updatePublishedAt.takeIf { it > 0L } ?: config.updatedAt
        val installedAfterPublish = BuildConfig.VERSION_CODE >= config.latestVersionCode &&
            publishedAt > 0L && packageInfo.lastUpdateTime >= publishedAt

        if (completedPendingInstall || installedAfterPublish) {
            acknowledge(context, id)
            return true
        }
        return false
    }

    fun markInstallStarted(context: Context, config: FlagshipConfig) {
        val currentUpdateTime = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(System.currentTimeMillis())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PENDING_ID, campaignId(config))
            .putLong(BEFORE_INSTALL_TIME, currentUpdateTime)
            .apply()
    }

    private fun acknowledge(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACKNOWLEDGED_ID, id)
            .remove(PENDING_ID)
            .remove(BEFORE_INSTALL_TIME)
            .apply()
    }
}
