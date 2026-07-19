package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Refreshed FCM Token: $token")
        // If a user is currently authenticated, update their token in Firestore
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null) {
            FirebaseFirestore.getInstance().collection("users")
                .document(currentUid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM_SERVICE", "Successfully updated FCM token in Firestore on token refresh.")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM_SERVICE", "Failed to update FCM token: ${e.message}")
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_SERVICE", "Message received from: ${remoteMessage.from}")

        // Extract title and body
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Message"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You received a new message"
        val senderId = remoteMessage.data["senderId"] ?: ""
        val notificationType = remoteMessage.data["notificationType"] ?: "message"
        val senderProfileUrl = remoteMessage.data["senderProfileUrl"] ?: ""

        sendNotification(title, body, senderId, notificationType, senderProfileUrl)
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        senderId: String,
        notificationType: String,
        senderProfileUrl: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("senderId", senderId)
            putExtra("notificationType", notificationType)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "firechat_messages_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // standard system icon or app icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(longArrayOf(0, 180, 90, 220))
            .setContentIntent(pendingIntent)

        loadBitmap(senderProfileUrl)?.let { notificationBuilder.setLargeIcon(it) }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FireChat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Messages, reactions, comments, tags and friend requests"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 90, 220)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
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
