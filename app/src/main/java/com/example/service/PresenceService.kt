package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/** Keeps Firebase presence connected after the UI is backgrounded. Android requires a visible notification. */
class PresenceService : Service() {
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "firechat_presence_v1"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Background presence", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps your online status and incoming calls active"
                setShowBadge(false)
            })
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(this, 90, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(9042, NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("FireChat is active")
            .setContentText("Online status and incoming calls are available")
            .setOngoing(true).setSilent(true).setContentIntent(pending).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { stopSelf(); return START_NOT_STICKY }
        statusRef = FirebaseDatabase.getInstance().getReference("status").child(uid)
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        connectedListener = connectedRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) == true) {
                    val now = System.currentTimeMillis()
                    statusRef?.setValue(mapOf("isOnline" to true, "lastActive" to now, "backgroundPresence" to true))
                    statusRef?.onDisconnect()?.setValue(mapOf("isOnline" to false, "lastActive" to now, "backgroundPresence" to false))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        return START_STICKY
    }

    override fun onDestroy() {
        connectedListener?.let { connectedRef?.removeEventListener(it) }
        statusRef?.setValue(mapOf("isOnline" to false, "lastActive" to System.currentTimeMillis(), "backgroundPresence" to false))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PresenceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
