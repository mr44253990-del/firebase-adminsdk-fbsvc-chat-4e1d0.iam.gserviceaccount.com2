package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.call.IncomingCallActivity
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL


private data class NotificationStyle(
    val channelId: String,
    val channelName: String,
    val vibration: LongArray,
    val category: String
)

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Refreshed FCM Token: $token")
        // If a user is currently authenticated, update their token in Firestore
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null) {
            FirebaseFirestore.getInstance().collection("users")
                .document(currentUid)
                .update(mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Log.d("FCM_SERVICE", "Successfully updated profile FCM routing token.")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM_SERVICE", "Failed to update FCM token: ${e.message}")
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        markDeviceReachableFromPush()
        Log.d("FCM_SERVICE", "Message received from: ${remoteMessage.from}")

        // Extract title and body
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Message"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You received a new message"
        val senderId = remoteMessage.data["senderId"] ?: ""
        val notificationType = remoteMessage.data["notificationType"] ?: "message"
        val senderProfileUrl = remoteMessage.data["senderProfileUrl"] ?: ""
        val targetId = remoteMessage.data["targetId"] ?: ""
        val senderName = remoteMessage.data["senderName"] ?: title

        if (notificationType == "call_cancelled") {
            if (targetId.isNotBlank()) {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(targetId.hashCode())
                FirebaseDatabase.getInstance().getReference("calls").child(targetId).child("status").setValue("ended")
            }
            return
        }
        if (notificationType == "incoming_call" || notificationType == "incoming_video_call") {
            if (targetId.isNotBlank()) {
                FirebaseDatabase.getInstance().getReference("calls").child(targetId).child("status").setValue("ringing")
            }
            sendIncomingCallNotification(targetId, senderId, senderName, senderProfileUrl, notificationType == "incoming_video_call")
        } else {
            sendNotification(title, body, senderId, notificationType, senderProfileUrl, targetId)
        }
    }

    private fun markDeviceReachableFromPush() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val receivedAt = System.currentTimeMillis()
        val statusRef = FirebaseDatabase.getInstance().getReference("status").child(uid)
        statusRef.updateChildren(
            mapOf(
                "isOnline" to true,
                "lastActive" to receivedAt,
                "lastPushReceivedAt" to receivedAt,
                "onlineSource" to "push"
            )
        )
        statusRef.onDisconnect().updateChildren(
            mapOf(
                "isOnline" to false,
                "lastActive" to ServerValue.TIMESTAMP,
                "onlineSource" to "disconnected"
            )
        )
        // A delivered push means the device is reachable, not that the UI stays open.
        // Keep the online badge briefly and only expire the exact heartbeat that we wrote.
        Handler(Looper.getMainLooper()).postDelayed({
            statusRef.get().addOnSuccessListener { snapshot ->
                val latestPush = snapshot.child("lastPushReceivedAt").getValue(Long::class.java) ?: 0L
                val foreground = snapshot.child("foreground").getValue(Boolean::class.java) ?: false
                if (!foreground && latestPush == receivedAt) {
                    statusRef.updateChildren(
                        mapOf(
                            "isOnline" to false,
                            "lastActive" to System.currentTimeMillis(),
                            "onlineSource" to "push_expired"
                        )
                    )
                }
            }
        }, 60_000L)
    }

    private fun sendIncomingCallNotification(callId: String, callerId: String, callerName: String, callerImage: String, videoCall: Boolean) {
        if (callId.isBlank()) return
        fun callIntent(action: String, requestCode: Int) = PendingIntent.getActivity(
            this, requestCode,
            Intent(this, IncomingCallActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("callId", callId); putExtra("callerId", callerId)
                putExtra("callerName", callerName); putExtra("callerImage", callerImage); putExtra("videoCall", videoCall)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answer = callIntent("com.ebchat.ACCEPT_CALL", (callId + "answer").hashCode())
        val decline = callIntent("com.ebchat.DECLINE_CALL", (callId + "decline").hashCode())
        val fullScreen = callIntent("com.ebchat.SHOW_CALL", (callId + "screen").hashCode())
        val avatar = loadBitmap(callerImage)
        val personBuilder = Person.Builder().setName(callerName).setImportant(true)
        if (avatar != null) personBuilder.setIcon(IconCompat.createWithBitmap(avatar))
        val person = personBuilder.build()
        val channelId = "firechat_calls_v1"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "FireChat Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming FireChat audio calls"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                enableVibration(true); vibrationPattern = longArrayOf(0, 500, 300, 500)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            })
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (videoCall) "Incoming FireChat video call" else "Incoming FireChat audio call")
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).setAutoCancel(false).setTimeoutAfter(30_000)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .setContentIntent(fullScreen)
        if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) builder.setFullScreenIntent(fullScreen, true)
        manager.notify(callId.hashCode(), builder.build())
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        senderId: String,
        notificationType: String,
        senderProfileUrl: String,
        targetId: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("senderId", senderId)
            putExtra("notificationType", notificationType)
            putExtra("targetId", targetId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (targetId + notificationType + System.currentTimeMillis()).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (channelId, channelName, pattern, category) = when (notificationType) {
            "message" -> NotificationStyle("firechat_messages_v2", "Messages", longArrayOf(0, 120, 70, 150), NotificationCompat.CATEGORY_MESSAGE)
            "friend_request", "friend_accepted", "message_request", "message_accepted" ->
                NotificationStyle("firechat_requests_v2", "Requests", longArrayOf(0, 220, 100, 220), NotificationCompat.CATEGORY_SOCIAL)
            else -> NotificationStyle("firechat_activity_v2", "Activity", longArrayOf(0, 100), NotificationCompat.CATEGORY_SOCIAL)
        }

        val notificationId = System.currentTimeMillis().toInt()
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVibrate(pattern)
            .setNumber(1)
            .setContentIntent(pendingIntent)

        if (notificationType == "message" && senderId.isNotBlank()) {
            val replyInput = RemoteInput.Builder(NotificationReplyReceiver.REPLY_KEY).setLabel("Reply to $title").build()
            val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
                putExtra("senderId", senderId); putExtra("notificationId", notificationId)
            }
            val replyPending = PendingIntent.getBroadcast(this, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            notificationBuilder.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyPending).addRemoteInput(replyInput).setAllowGeneratedReplies(true).build())
        }

        loadBitmap(senderProfileUrl)?.let { notificationBuilder.setLargeIcon(it) }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "FireChat • $channelName", NotificationManager.IMPORTANCE_HIGH).apply {
                description = when (channelId) {
                    "firechat_messages_v2" -> "Direct and group chat messages"
                    "firechat_requests_v2" -> "Friend and message requests"
                    else -> "Reactions, comments, tags and story activity"
                }
                enableVibration(true)
                vibrationPattern = pattern
                enableLights(true)
                lightColor = 0xFF8A72FF.toInt()
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun loadBitmap(url: String): Bitmap? {
        if (!url.startsWith("http")) return null
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.doInput = true
            connection.connect()
            connection.inputStream.use(BitmapFactory::decodeStream)
        } catch (e: Exception) {
            Log.w("FCM_SERVICE", "Could not load notification avatar: ${e.message}")
            null
        }
    }
}
