package com.example.service

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.call.CallEngine
import com.example.call.GroupCallEngine

class CallForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ongoing Convo Chat calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps microphone and camera active while a call is connected"
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra("remoteName") ?: "Convo Chat user"
        val video = intent?.getBooleanExtra("video", false) == true
        val callId = intent?.getStringExtra("callId").orEmpty()
        val screenShare = intent?.getBooleanExtra("screenShare", false) == true
        val groupId = intent?.getStringExtra("groupId").orEmpty()
        val roomId = intent?.getStringExtra("roomId").orEmpty()
        val groupName = intent?.getStringExtra("groupName").orEmpty()
        val memberIds = intent?.getStringArrayListExtra("memberIds").orEmpty()
        val openIntent = Intent(this, com.example.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (groupId.isNotBlank() && roomId.isNotBlank()) {
                setAction("com.ebchat.OPEN_ACTIVE_GROUP_CALL")
                putExtra("groupId", groupId)
                putExtra("roomId", roomId)
                putExtra("groupName", groupName.ifBlank { name })
                putExtra("memberIds", ArrayList(memberIds))
                putExtra("videoCall", video)
            }
        }
        val content = PendingIntent.getActivity(this, callId.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val endIntent = PendingIntent.getBroadcast(
            this, (callId + "end").hashCode(),
            Intent(this, CallEndReceiver::class.java).putExtra("callId", callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (video) android.R.drawable.presence_video_online else android.R.drawable.sym_call_outgoing)
            .setContentTitle(if (video) "Convo Chat video call" else "Convo Chat audio call")
            .setContentText("Connected with $name")
            .setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setSilent(true)
            .setContentIntent(content)
            .addAction(android.R.drawable.sym_call_missed, "End call", endIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            if (video && !screenShare) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (screenShare && Build.VERSION.SDK_INT >= 29) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(NOTIFICATION_ID, notification, type)
        } else startForeground(NOTIFICATION_ID, notification)
        // Keep the foreground call service eligible for restart if Android recreates
        // the process while the user is backgrounded or the screen is locked.
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "firechat_ongoing_call_v1"
        private const val NOTIFICATION_ID = 9044
        fun start(
            context: Context,
            callId: String,
            remoteName: String,
            video: Boolean,
            screenShare: Boolean = false,
            groupId: String = "",
            roomId: String = "",
            groupName: String = "",
            memberIds: List<String> = emptyList()
        ) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra("callId", callId)
                .putExtra("remoteName", remoteName)
                .putExtra("video", video)
                .putExtra("screenShare", screenShare)
                .putExtra("groupId", groupId)
                .putExtra("roomId", roomId)
                .putExtra("groupName", groupName)
                .putStringArrayListExtra("memberIds", ArrayList(memberIds))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, CallForegroundService::class.java)) }
    }
}

class CallEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent?.getStringExtra("callId").orEmpty().startsWith("group:")) GroupCallEngine.end() else CallEngine.end()
        CallForegroundService.stop(context)
    }
}
