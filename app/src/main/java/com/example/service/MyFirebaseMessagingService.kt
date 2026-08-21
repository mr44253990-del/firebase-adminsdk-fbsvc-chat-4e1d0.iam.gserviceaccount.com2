package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.security.AppLockManager
import com.example.security.PrivacyPreferences
import com.example.call.IncomingCallActivity
import com.example.call.IncomingGroupCallActivity
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
        val rawTitle = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Message"
        val rawBody = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You received a new message"
        val privacy = PrivacyPreferences.load(this)
        val hideContent = AppLockManager.isEnabled(this) || privacy.hideNotificationContent
        val title = if (hideContent) "Convo Chat" else rawTitle
        val senderId = remoteMessage.data["senderId"] ?: ""
        val notificationType = remoteMessage.data["notificationType"] ?: "message"
        // Presence probes are data-only and are intentionally not user-visible.
        // markDeviceReachableFromPush() above has already renewed the RTDB lease.
        if (notificationType == "presence_probe") return
        val sentAt = remoteMessage.data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        val minutesLate = ((System.currentTimeMillis() - sentAt).coerceAtLeast(0L) / 60_000L)
        val age = when { minutesLate < 1 -> "now"; minutesLate < 60 -> "$minutesLate min ago"; minutesLate < 1440 -> "${minutesLate / 60} hr ago"; else -> "${minutesLate / 1440} day ago" }
        val body = if (hideContent) "New notification received — unlock Convo Chat to view" else if (notificationType == "message") "$rawBody • $age" else rawBody
        val targetId = remoteMessage.data["targetId"] ?: ""
        if (notificationType == "message" && senderId.isNotBlank()) {
            FirebaseAuth.getInstance().currentUser?.uid?.let { receiverUid ->
                val chatId = listOf(senderId, receiverUid).sorted().joinToString("_")
                if (targetId.isNotBlank()) {
                    FirebaseDatabase.getInstance().getReference("delivery_receipts")
                        .child(senderId).child(chatId).child(targetId)
                        .setValue(mapOf("delivered" to true, "deliveredAt" to System.currentTimeMillis()))
                }
                maybeSendAwayReply(receiverUid, senderId, chatId)
            }
        }
        val muted = getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE).getStringSet("muted_users", emptySet())?.contains(senderId) == true
        if (muted && notificationType == "message") return
        val senderProfileUrl = remoteMessage.data["senderProfileUrl"] ?: ""
        val senderName = if (hideContent) "Convo Chat user" else remoteMessage.data["senderName"] ?: title

        if (notificationType == "call_cancelled") {
            if (targetId.isNotBlank()) {
                CallRingtoneController.stop(this, targetId, cancelNotification = true)
                FirebaseDatabase.getInstance().getReference("calls").child(targetId).child("status").setValue("ended")
            }
            return
        }
        if (notificationType == "incoming_call" || notificationType == "incoming_video_call") {
            if (targetId.isNotBlank()) {
                FirebaseDatabase.getInstance().getReference("calls").child(targetId).child("status").setValue("ringing")
            }
            sendIncomingCallNotification(targetId, senderId, senderName, senderProfileUrl, notificationType == "incoming_video_call")
        } else if (notificationType == "group_call") {
            sendIncomingGroupCallNotification(
                roomId = remoteMessage.data["roomId"].orEmpty(),
                groupId = remoteMessage.data["groupId"] ?: targetId,
                groupName = remoteMessage.data["groupName"] ?: "Convo Chat group",
                memberIds = remoteMessage.data["memberIds"].orEmpty(),
                callerId = senderId,
                callerName = senderName,
                callerImage = senderProfileUrl,
                videoCall = remoteMessage.data["videoCall"].toBoolean()
            )
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
                "onlineUntil" to receivedAt + 5 * 60_000L,
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
        // UI lease evaluation expires at five minutes; the local boolean is cleared
        // slightly later so a scheduled five-minute probe has delivery jitter room.
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
        }, 6 * 60_000L)
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
        // v3 is intentionally silent at channel level. CallRingtoneController owns
        // ringtone/vibration so full-screen launch cannot stop it and accept cannot double-play it.
        val channelId = "convo_calls_v4"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Convo Chat Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen incoming Convo Chat calls"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            })
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (videoCall) "Incoming Convo Chat video call" else "Incoming Convo Chat audio call")
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSilent(true)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(false).setShowWhen(true).setTimeoutAfter(45_000)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .setContentIntent(fullScreen)
        if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) builder.setFullScreenIntent(fullScreen, true)
        manager.notify(callId.hashCode(), builder.build())
        CallRingtoneController.start(this, callId)
    }

    private fun sendIncomingGroupCallNotification(
        roomId: String,
        groupId: String,
        groupName: String,
        memberIds: String,
        callerId: String,
        callerName: String,
        callerImage: String,
        videoCall: Boolean
    ) {
        if (roomId.isBlank() || groupId.isBlank()) {
            Log.w("FCM_SERVICE", "Ignoring incomplete group-call invite")
            return
        }
        fun callIntent(action: String, requestCode: Int) = PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, IncomingGroupCallActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("roomId", roomId)
                putExtra("groupId", groupId)
                putExtra("groupName", groupName)
                putExtra("memberIds", memberIds)
                putExtra("callerId", callerId)
                putExtra("callerName", callerName)
                putExtra("callerImage", callerImage)
                putExtra("videoCall", videoCall)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answer = callIntent("com.ebchat.JOIN_GROUP_CALL", (roomId + "join").hashCode())
        val decline = callIntent("com.ebchat.DECLINE_GROUP_CALL", (roomId + "decline").hashCode())
        val fullScreen = callIntent("com.ebchat.SHOW_GROUP_CALL", (roomId + "screen").hashCode())
        val person = Person.Builder().setName(callerName).setImportant(true).build()
        val channelId = "convo_calls_v4"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Convo Chat Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen incoming Convo Chat calls"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            })
        }
        // Use the same stable key as CallRingtoneController so Join/Decline/timeout cancel the notification.
        val notificationId = roomId.hashCode()
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (videoCall) "Incoming group video call" else "Incoming group audio call")
            .setContentText("$callerName invited you to $groupName")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(30_000)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .setContentIntent(fullScreen)
        // The OS still enforces USE_FULL_SCREEN_INTENT on Android 14+; setting the
        // intent unconditionally lets permitted devices launch it from the lock screen,
        // while non-permitted devices retain the public CallStyle notification fallback.
        builder.setFullScreenIntent(fullScreen, true)
        manager.notify(notificationId, builder.build())
        CallRingtoneController.start(this, roomId)
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
            "message" -> NotificationStyle("convo_messages_v1", "Messages", longArrayOf(0, 120, 70, 150), NotificationCompat.CATEGORY_MESSAGE)
            "friend_request", "friend_accepted", "message_request", "message_accepted" ->
                NotificationStyle("convo_requests_v1", "Requests", longArrayOf(0, 220, 100, 220), NotificationCompat.CATEGORY_SOCIAL)
            else -> NotificationStyle("convo_activity_v1", "Activity", longArrayOf(0, 100), NotificationCompat.CATEGORY_SOCIAL)
        }

        val notificationId = System.currentTimeMillis().toInt()
        val messageSound = Uri.parse("android.resource://$packageName/${R.raw.mixkit_confirmation_tone_2867}")
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setVibrate(pattern)
            .setNumber(1)
            .setContentIntent(pendingIntent)
        if (notificationType == "message") notificationBuilder.setSound(messageSound)
        else notificationBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND)

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
            val channel = NotificationChannel(channelId, "Convo Chat • $channelName", NotificationManager.IMPORTANCE_HIGH).apply {
                description = when (channelId) {
                    "convo_messages_v1" -> "Direct and group chat messages"
                    "convo_requests_v1" -> "Friend and message requests"
                    else -> "Reactions, comments, tags and story activity"
                }
                if (channelId == "convo_messages_v1") {
                    setSound(messageSound, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
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

    private fun maybeSendAwayReply(receiverUid: String, senderUid: String, chatId: String) {
        if (receiverUid.isBlank() || senderUid.isBlank() || receiverUid == senderUid) return
        val prefs = getSharedPreferences("convo_automation", Context.MODE_PRIVATE)
        val throttleKey = "away_reply_last_$senderUid"
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(throttleKey, 0L) < 6 * 60 * 60 * 1000L) return

        FirebaseFirestore.getInstance().collection("users").document(receiverUid).get()
            .addOnSuccessListener { profile ->
                val enabled = profile.getBoolean("awayReplyEnabled") == true
                val replyText = profile.getString("awayReplyText").orEmpty().trim()
                val activeUntil = profile.getLong("awayReplyUntil") ?: 0L
                if (!enabled || replyText.isBlank() || (activeUntil > 0L && activeUntil <= now)) return@addOnSuccessListener

                val messageId = "away_${receiverUid}_${senderUid}_$now"
                val message = mapOf(
                    "messageId" to messageId,
                    "senderId" to receiverUid,
                    "senderName" to (profile.getString("name") ?: "Convo user"),
                    "senderUsername" to (profile.getString("username") ?: ""),
                    "text" to replyText,
                    "timestamp" to now,
                    "edited" to false,
                    "deliveredToRecipient" to true,
                    "seenByRecipient" to false,
                    "autoReply" to true
                )
                FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages").child(messageId)
                    .setValue(message)
                    .addOnSuccessListener {
                        prefs.edit().putLong(throttleKey, now).apply()
                        FirebaseDatabase.getInstance().getReference("notifications").child(senderUid)
                            .setValue(mapOf(
                                "senderId" to receiverUid,
                                "senderName" to (profile.getString("name") ?: "Convo user"),
                                "text" to replyText,
                                "timestamp" to now,
                                "autoReply" to true
                            ))
                    }
                    .addOnFailureListener { error -> Log.w("FCM_SERVICE", "Away reply failed: ${error.message}") }
            }
            .addOnFailureListener { error -> Log.w("FCM_SERVICE", "Away reply profile lookup failed: ${error.message}") }
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
