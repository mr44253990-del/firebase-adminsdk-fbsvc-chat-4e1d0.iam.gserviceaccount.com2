package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*

/** Single source of truth for incoming-call sound/vibration; avoids channel caching and double audio. */
object CallRingtoneController {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var activeCallId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null

    @Synchronized
    fun start(context: Context, callId: String) {
        stop(context, activeCallId, cancelNotification = false)
        activeCallId = callId
        runCatching {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 700, 350, 700, 350, 700)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
        else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 1)
        timeout = Runnable { stop(context.applicationContext, callId, cancelNotification = true) }.also { handler.postDelayed(it, 30_000L) }
    }

    @Synchronized
    fun stop(context: Context, callId: String?, cancelNotification: Boolean = true) {
        if (callId != null && activeCallId != null && callId != activeCallId) return
        runCatching { ringtone?.stop() }; ringtone = null
        runCatching { vibrator?.cancel() }; vibrator = null
        timeout?.let(handler::removeCallbacks); timeout = null
        if (cancelNotification && callId != null) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(callId.hashCode())
        }
        activeCallId = null
    }
}
