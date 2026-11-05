package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*

/** Single source of truth for incoming-call sound/vibration; avoids channel caching and double audio. */
object CallRingtoneController {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var activeCallId: String? = null
    /** Wall-clock start of the current vibration loop (0 when idle); used to phase-lock UI animation. */
    @Volatile var startedAt: Long = 0L
        private set
    val VIBRATION_PATTERN = longArrayOf(0, 260, 140, 420, 1180)
    const val VIBRATION_PERIOD_MS = 2000L
    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null

    @Synchronized
    fun start(context: Context, callId: String) {
        stop(context, activeCallId, cancelNotification = false)
        activeCallId = callId
        runCatching {
            val customUri = context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE).getString("premium_ringtone_uri", null)
                ?.takeIf { it.startsWith("content:") || it.startsWith("file:") }?.let(Uri::parse)
            val ringtoneUri = customUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = (RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)
                ?: RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)))?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        // "Ba-dum" heartbeat repeating every 2 s; the incoming-call screen animates in sync with it.
        val pattern = VIBRATION_PATTERN
        startedAt = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        timeout = Runnable { stop(context.applicationContext, callId, cancelNotification = true) }.also { handler.postDelayed(it, 30_000L) }
    }

    @Synchronized
    fun stop(context: Context, callId: String?, cancelNotification: Boolean = true) {
        if (callId != null && activeCallId != null && callId != activeCallId) return
        runCatching { ringtone?.stop() }; ringtone = null
        runCatching { vibrator?.cancel() }; vibrator = null
        startedAt = 0L
        timeout?.let(handler::removeCallbacks); timeout = null
        if (cancelNotification && callId != null) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(callId.hashCode())
        }
        activeCallId = null
    }
}
