package com.example.service

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.call.CallEngine

class CallForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ongoing FireChat calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps microphone and camera active while a call is connected"
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra("remoteName") ?: "FireChat user"
        val video = intent?.getBooleanExtra("video", false) == true
        val callId = intent?.getStringExtra("callId").orEmpty()
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val content = PendingIntent.getActivity(this, callId.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val endIntent = PendingIntent.getBroadcast(
            this, (callId + "end").hashCode(),
            Intent(this, CallEndReceiver::class.java).putExtra("callId", callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (video) android.R.drawable.presence_video_online else android.R.drawable.sym_call_outgoing)
            .setContentTitle(if (video) "FireChat video call" else "FireChat audio call")
            .setContentText("Connected with $name")
            .setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setSilent(true)
            .setContentIntent(content)
            .addAction(android.R.drawable.sym_call_missed, "End call", endIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            if (video) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID, notification, type)
        } else startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "firechat_ongoing_call_v1"
        private const val NOTIFICATION_ID = 9044
        fun start(context: Context, callId: String, remoteName: String, video: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra("callId", callId).putExtra("remoteName", remoteName).putExtra("video", video)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, CallForegroundService::class.java)) }
    }
}

class CallEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CallEngine.end()
        CallForegroundService.stop(context)
    }
}
