package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * "Find this device" locate: the admin can ask an active device to flash its flashlight and
 * vibrate for a few seconds so the owner can find a misplaced phone. Runs entirely from a
 * data-only FCM push; no location is ever read, stored or transmitted.
 *
 * The device owner stays in control: Profile settings can turn the permission off at any time.
 */
object SecurityLocate {
    private const val PREFS = "convo_security"
    private const val KEY_ENABLED = "locate_enabled"
    private const val NOTIFICATION_ID = 910_007
    private val handler = Handler(Looper.getMainLooper())

    fun allowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setAllowed(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Flashes the torch and vibrates for [durationMs]; auto-off runs on the main handler. */
    fun start(context: Context, durationMs: Long = 6000L) {
        val app = context.applicationContext
        if (!allowed(app)) return
        vibratePattern(app, durationMs)
        flashTorch(app, durationMs)
        showHeadsUp(app, durationMs)
    }

    private fun vibratePattern(context: Context, durationMs: Long) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val pattern = buildList {
                add(0L)
                var t = 0L
                while (t < durationMs) { add(260L); t += 260; add(200L); t += 200 }
            }.toLongArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
            }
        }
    }

    /** setTorchMode works without opening a camera and needs no extra permission. */
    private fun flashTorch(context: Context, durationMs: Long) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        runCatching {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            manager.setTorchMode(cameraId, true)
            handler.postDelayed({
                runCatching { manager.setTorchMode(cameraId, false) }
            }, durationMs)
        }
    }

    private fun showHeadsUp(context: Context, durationMs: Long) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "convo_locate_v1"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Find this device", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "The admin can make this phone flash and vibrate so you can find it"
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Convo Chat - Finding this device")
                .setContentText("Flashlight and vibration for ${durationMs / 1000} seconds. Your location is never shared.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            handler.postDelayed({ manager.cancel(NOTIFICATION_ID) }, durationMs + 1500)
        }
    }
}
