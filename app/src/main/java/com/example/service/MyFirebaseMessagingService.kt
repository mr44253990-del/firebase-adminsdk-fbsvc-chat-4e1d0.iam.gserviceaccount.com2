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
import com.example.data.AppDatabase
import com.example.data.CachedMessage
import com.example.data.Message
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

/** Shared presence lease: five-minute probe cycle plus one minute of delivery jitter. */
const val PRESENCE_LEASE_MS = 6 * 60_000L

private const val SUMMARY_ID = 0x0C0FFEE

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Never log the raw registration token; it is a device-routing credential.
        // If a user is currently authenticated, refresh the Firestore token used by the Worker.
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
        val notificationType = remoteMessage.data["notificationType"] ?: "message"
        markDeviceReachableFromPush(notificationType, remoteMessage.data["probeId"])
        Log.d("FCM_SERVICE", "Message received from: ${remoteMessage.from}")

        // Extract title and body
        val rawTitle = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Message"
        val rawBody = (remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You received a new message")
            .let { body -> com.example.ui.convoStickerPreview(body.trim()).takeIf { com.example.ui.ConvoStickers.isSticker(body) } ?: body }
        val privacy = PrivacyPreferences.load(this)
        val hideContent = AppLockManager.isEnabled(this) || privacy.hideNotificationContent
        val title = if (hideContent) "Convo Chat" else plainNotificationText(rawTitle).ifBlank { "Convo Chat" }
        val senderId = remoteMessage.data["senderId"] ?: ""
        // Presence probes are data-only and intentionally not user-visible. Both the
        // RTDB reachability lease and the Firestore dashboard acknowledgement update.
        // Any wake-up (probe or push) proves this device is reachable: acknowledge delivery of
        // messages that arrived while it was offline so senders see ✓✓ without opening the chat.
        runCatching { DeliveryAck.ackAllPending() }
        if (notificationType == "presence_probe") return
        val sentAt = (remoteMessage.data["sentAt"]?.toLongOrNull() ?: remoteMessage.sentTime.takeIf { it > 0L } ?: System.currentTimeMillis())
            .coerceAtMost(System.currentTimeMillis())
        // Text only: the timestamp is shown by Android via setWhen() and keeps updating
        // ("now", "5m", "1h") instead of being frozen inside the message text.
        val body = if (hideContent) "New notification. Unlock Convo Chat to view it." else plainNotificationText(rawBody).ifBlank { "New message" }
        val targetId = remoteMessage.data["targetId"] ?: ""
        if (notificationType == "message" && senderId.isNotBlank()) {
            FirebaseAuth.getInstance().currentUser?.uid?.let { receiverUid ->
                val chatId = listOf(senderId, receiverUid).sorted().joinToString("_")
                if (targetId.isNotBlank()) {
                    FirebaseDatabase.getInstance().getReference("delivery_receipts")
                        .child(senderId).child(chatId).child(targetId)
                        .updateChildren(mapOf("delivered" to true, "deliveredAt" to System.currentTimeMillis()))
                }
                maybeSendAwayReply(receiverUid, senderId, chatId)
            }
        }
        val muted = getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE).getStringSet("muted_users", emptySet())?.contains(senderId) == true
        if (muted && notificationType == "message") return
        // The user is looking at this exact conversation right now: the message is already
        // on screen, so skip the system notification. Other senders still notify.
        if (notificationType == "message" && ActiveChatState.isViewingPeer(senderId)) return
        if (notificationType == "message" && senderId.isNotBlank() && com.example.ui.ConversationPrefs.isMuted(applicationContext, senderId)) return
        if (notificationType == "group_message" && ActiveChatState.isViewingGroup(targetId)) return
        if ((notificationType == "message" || notificationType == "group_message") && com.example.ui.SmartPrefs.inQuietHours(applicationContext)) return
        val senderProfileUrl = remoteMessage.data["senderProfileUrl"] ?: ""
        val senderName = if (hideContent) "Convo Chat user" else plainNotificationText(remoteMessage.data["senderName"] ?: title).ifBlank { title }

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
            val imageUrl = remoteMessage.data["imageUrl"].orEmpty()
                .takeIf { !hideContent && it.startsWith("https://") && com.example.ui.NotificationExtrasPrefs.richImagesEnabled(applicationContext) }
                .orEmpty()
            sendNotification(title, body, senderId, notificationType, senderProfileUrl, targetId, sentAt, senderName, imageUrl)
        }
    }

    /** Downloads a photo for a rich notification (bounded size/time) and exposes it via FileProvider. */
    private fun notificationImage(url: String): Pair<android.graphics.Bitmap, Uri>? = runCatching {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5_000; readTimeout = 7_000; instanceFollowRedirects = true
        }
        val bytes = conn.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf); if (n < 0) break
                total += n; if (total > 6 * 1024 * 1024) error("image too large")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        conn.disconnect()
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 720 || bounds.outHeight / (sample * 2) >= 720) sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val dir = java.io.File(cacheDir, "notif_images").apply { mkdirs() }
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(20)?.forEach { it.delete() }
        val file = java.io.File(dir, "img_${url.hashCode().toUInt()}.jpg")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
        runCatching { grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        bitmap to uri
    }.getOrNull()

    private fun markDeviceReachableFromPush(notificationType: String, probeId: String?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val receivedAt = System.currentTimeMillis()
        val statusRef = FirebaseDatabase.getInstance().getReference("status").child(uid)
        // A device that answers a push is connected to the internet, so it is shown as
        // Active for the next lease window (5 min probe interval + 1 min delivery jitter),
        // whether the app is open, in the background, or closed. No onDisconnect here:
        // the background FCM process exits within seconds and its socket closing must not
        // flip the user to offline. The lease expiring is what makes a user offline.
        val write = statusRef.updateChildren(
            mapOf(
                "isOnline" to true,
                "lastActive" to receivedAt,
                "lastPushReceivedAt" to receivedAt,
                "onlineUntil" to receivedAt + PRESENCE_LEASE_MS,
                "offlineSince" to 0L,
                "onlineSource" to if (notificationType == "presence_probe") "probe" else "push"
            )
        )

        // Write only acknowledgement fields to Firestore for the admin dashboard.
        val presenceUpdate = mutableMapOf<String, Any>("lastPushReceivedAt" to FieldValue.serverTimestamp())
        if (notificationType == "presence_probe") {
            presenceUpdate["lastProbeReceivedAt"] = FieldValue.serverTimestamp()
            if (!probeId.isNullOrBlank()) presenceUpdate["lastProbeId"] = probeId.take(128)
        }
        val ack = FirebaseFirestore.getInstance().collection("presence").document(uid)
            .set(presenceUpdate, SetOptions.merge())
            .addOnFailureListener { error -> Log.w("FCM_SERVICE", "Presence acknowledgement failed: ${error.message}") }

        // onMessageReceived runs on a worker thread with a ~20 s budget. Wait briefly so the
        // writes actually leave the device before Android freezes the background process.
        runCatching { com.google.android.gms.tasks.Tasks.await(com.google.android.gms.tasks.Tasks.whenAll(write, ack), 12, java.util.concurrent.TimeUnit.SECONDS) }
            .onFailure { Log.w("FCM_SERVICE", "Presence write not confirmed in time: ${it.message}") }
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
        if (Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent()) {
            Log.w("FCM_SERVICE", "Full-screen intent access is disabled; Android will show the call notification fallback")
        }
        // Always attach the explicit full-screen target. Android 14+ enforces the
        // user's special-app-access setting and degrades to a heads-up notification
        // when denied; omitting the intent here would also block permitted devices.
        builder.setFullScreenIntent(fullScreen, true)
        manager.notify(callId.hashCode(), builder.build())
        CallRingtoneController.start(this, callId)
        launchCallUiFromBackground(
            Intent(this, IncomingCallActivity::class.java).apply {
                action = "com.ebchat.SHOW_CALL"
                putExtra("callId", callId); putExtra("callerId", callerId)
                putExtra("callerName", callerName); putExtra("callerImage", callerImage); putExtra("videoCall", videoCall)
            },
            fullScreenAllowed = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
        )
    }

    /**
     * Android only turns a full-screen intent into a full-screen activity when the screen is
     * off or locked; on an unlocked phone it shows a heads-up card. With "Display over other
     * apps" granted, open the incoming-call screen directly so the call is full-screen even
     * while another app is in use. Android 15+ only allows this while the app has a visible
     * overlay window, so a 1 px transparent overlay is attached for the moment of the launch.
     * Best effort: OEM restrictions can still block background activity starts.
     */
    private fun launchCallUiFromBackground(intent: Intent, fullScreenAllowed: Boolean) {
        if (ActiveChatState.appVisible) {
            // The app is on screen: show the full incoming-call screen instead of a small banner.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            }
            return
        }
        val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val lockedOrDark = !power.isInteractive || keyguard.isKeyguardLocked
        if (lockedOrDark && fullScreenAllowed) return // the system full-screen intent handles it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) return
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val anchor = android.view.View(applicationContext)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE
            val params = android.view.WindowManager.LayoutParams(
                1, 1, type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            val attached = runCatching { windowManager.addView(anchor, params) }.isSuccess
            main.postDelayed({
                runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                    .onFailure { Log.w("FCM_SERVICE", "Direct call screen launch blocked: ${it.message}") }
                if (attached) main.postDelayed({ runCatching { windowManager.removeView(anchor) } }, 2500)
            }, 120)
        }
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
        targetId: String,
        sentAt: Long = System.currentTimeMillis(),
        senderName: String = title,
        imageUrl: String = ""
    ) {
        val richImage = if (imageUrl.isNotBlank()) notificationImage(imageUrl) else null
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

        // A user-picked (or admin default) MP3 is played by the app itself through a quiet
        // channel, because Android channel sounds cannot be changed after creation.
        val customSound = notificationType == "message" && com.example.ui.AppSoundStore.activeFile(this, com.example.ui.AppSoundStore.NOTIFICATION) != null
        val (channelId, channelName, pattern, category) = when (notificationType) {
            "message" -> if (customSound) NotificationStyle("convo_messages_custom_v1", "Messages (custom sound)", longArrayOf(0, 120, 70, 150), NotificationCompat.CATEGORY_MESSAGE)
                else NotificationStyle("convo_messages_v1", "Messages", longArrayOf(0, 120, 70, 150), NotificationCompat.CATEGORY_MESSAGE)
            "friend_request", "friend_accepted", "message_request", "message_accepted" ->
                NotificationStyle("convo_requests_v1", "Requests", longArrayOf(0, 220, 100, 220), NotificationCompat.CATEGORY_SOCIAL)
            "admin_broadcast" -> NotificationStyle("convo_announcements_v1", "Announcements", longArrayOf(0, 80, 60, 80), NotificationCompat.CATEGORY_RECOMMENDATION)
            "security_alert" -> NotificationStyle("convo_security_v1", "Security alerts", longArrayOf(0, 300, 120, 300), NotificationCompat.CATEGORY_STATUS)
            else -> NotificationStyle("convo_activity_v1", "Activity", longArrayOf(0, 100), NotificationCompat.CATEGORY_SOCIAL)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isMessage = notificationType == "message" && senderId.isNotBlank()
        // One notification per conversation; new messages are appended to it.
        val notificationId = if (isMessage) ("chat_" + senderId).hashCode() else System.currentTimeMillis().toInt()
        val messageSound = Uri.parse("android.resource://$packageName/${R.raw.mixkit_confirmation_tone_2867}")
        val avatar = if (senderId.isNotBlank() || senderProfileUrl.startsWith("http")) NotificationAvatarCache.load(this, senderId, senderProfileUrl) else null
        val accent = if (isMessage) ConvoNotificationArt.accentFor(senderId) else 0xFF7C5CFC.toInt()
        val avatarBitmap = avatar?.let { circleBitmap(it) } ?: if (isMessage) ConvoNotificationArt.initialsAvatar(senderName.ifBlank { title }, senderId) else null

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(if (notificationType == "security_alert") android.R.drawable.ic_lock_lock else android.R.drawable.stat_notify_chat)
            .setColor(accent)
            .setWhen(sentAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setVibrate(pattern)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(false)

        if (isMessage) {
            val me = androidx.core.app.Person.Builder().setName("You").build()
            val sender = androidx.core.app.Person.Builder()
                .setName(senderName.ifBlank { title })
                .setKey(senderId)
                .setImportant(true)
                .apply { avatarBitmap?.let { setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(it)) } }
                .build()
            // Conversation shortcut: Android 11+ lists this chat under "Conversations" with the avatar.
            ConvoNotificationArt.pushConversationShortcut(this, senderId, senderName.ifBlank { title }, avatarBitmap)
            val previous = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.activeNotifications.firstOrNull { it.id == notificationId }?.notification?.let {
                    NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
                }
            } else null
            val style = NotificationCompat.MessagingStyle(me).setGroupConversation(false)
            previous?.messages?.takeLast(6)?.forEach { style.addMessage(it) }
            val newMessage = NotificationCompat.MessagingStyle.Message(if (richImage != null) "📷 $messageBody" else messageBody, sentAt, sender)
            // Rich notification: the photo itself is shown inside the conversation bubble.
            richImage?.let { (_, uri) -> newMessage.setData("image/jpeg", uri) }
            style.addMessage(newMessage)
            val count = style.messages.size
            notificationBuilder
                .setStyle(style)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setSubText(if (count > 1) "$count new messages" else null)
                .setNumber(count)
                .setGroup("convo_messages")
                .setShortcutId(ConvoNotificationArt.shortcutId(senderId))
                .setLocusId(androidx.core.content.LocusIdCompat(ConvoNotificationArt.shortcutId(senderId)))
            val markRead = PendingIntent.getBroadcast(
                this, ("read_" + senderId).hashCode(),
                Intent(this, NotificationActionReceiver::class.java).putExtra("senderId", senderId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                NotificationCompat.Action.Builder(android.R.drawable.checkbox_on_background, "Mark as read", markRead)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false).build()
            )
        } else {
            notificationBuilder
                .setContentTitle(title)
                .setContentText(messageBody)
                .setStyle(
                    richImage?.let { (bitmap, _) ->
                        NotificationCompat.BigPictureStyle().bigPicture(bitmap).setSummaryText(messageBody)
                    } ?: NotificationCompat.BigTextStyle().bigText(messageBody)
                )
                .setNumber(1)
        }
        if (notificationType == "message") {
            if (customSound) notificationBuilder.setSilent(false).setSound(null) else notificationBuilder.setSound(messageSound)
        } else notificationBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND)

        if (isMessage) {
            // Inline reply with one-tap smart chips (Android shows them under the reply field).
            val replyInput = RemoteInput.Builder(NotificationReplyReceiver.REPLY_KEY)
                .setLabel("Reply to ${senderName.ifBlank { title }}")
                .setChoices(SmartReplies.suggest(messageBody).toTypedArray())
                .build()
            val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
                putExtra("senderId", senderId); putExtra("notificationId", notificationId)
            }
            val replyPending = PendingIntent.getBroadcast(this, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            notificationBuilder.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyPending).addRemoteInput(replyInput).setAllowGeneratedReplies(true).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).build())
            val mute = PendingIntent.getBroadcast(
                this, ("mute_" + senderId).hashCode(),
                Intent(this, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_MUTE_HOUR).putExtra("senderId", senderId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                NotificationCompat.Action.Builder(android.R.drawable.ic_lock_silent_mode, "Mute 1h", mute)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).setShowsUserInterface(false).build()
            )
        }

        avatarBitmap?.let { notificationBuilder.setLargeIcon(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Convo Chat • $channelName", NotificationManager.IMPORTANCE_HIGH).apply {
                description = when (channelId) {
                    "convo_messages_v1" -> "Direct and group chat messages"
                    "convo_messages_custom_v1" -> "Chat messages using your custom notification sound"
                    "convo_requests_v1" -> "Friend and message requests"
                    "convo_announcements_v1" -> "News and announcements from the Convo Chat team"
                    "convo_security_v1" -> "New sign-ins, 2-step verification and account changes"
                    else -> "Reactions, comments, tags and story activity"
                }
                when (channelId) {
                    "convo_messages_v1" -> setSound(messageSound, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    "convo_messages_custom_v1" -> setSound(null, null)
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
        if (isMessage) {
            // Several chats waiting: a compact group summary keeps the shade tidy.
            val waiting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) notificationManager.activeNotifications.count { it.notification.group == "convo_messages" && it.id != SUMMARY_ID } else 0
            if (waiting >= 2) {
                notificationManager.notify(SUMMARY_ID, NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setColor(0xFF7C5CFC.toInt())
                    .setContentTitle("Convo Chat")
                    .setContentText("$waiting conversations waiting")
                    .setStyle(NotificationCompat.InboxStyle().setSummaryText("$waiting chats"))
                    .setGroup("convo_messages").setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setAutoCancel(true).build())
            }
            // One reminder later if this conversation is still not opened.
            MessageReminder.schedule(this, senderId, senderName.ifBlank { title }, messageBody, sentAt)
        }
        if (customSound) com.example.ui.AppSoundStore.playCustom(this, com.example.ui.AppSoundStore.NOTIFICATION)
    }

    private fun circleBitmap(source: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = minOf(source.width, source.height).coerceAtLeast(1)
        val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val left = (source.width - size) / 2f
        val top = (source.height - size) / 2f
        canvas.drawBitmap(source, -left, -top, paint)
        return output
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

                // Same deterministic id as the Worker's server-side away reply → never duplicated.
                val messageId = "away_${receiverUid}_${senderUid}_${now / (6 * 3600 * 1000L)}"
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
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                val cached = Message(
                                    messageId = messageId,
                                    senderId = receiverUid,
                                    senderName = profile.getString("name") ?: "Convo user",
                                    senderUsername = profile.getString("username") ?: "",
                                    text = replyText,
                                    timestamp = now,
                                    deliveredToRecipient = true
                                )
                                AppDatabase.getDatabase(this@MyFirebaseMessagingService).cacheDao()
                                    .insertMessage(CachedMessage.fromMessage(cached, chatId))
                            }.onFailure { error ->
                                Log.w("FCM_SERVICE", "Away reply cache failed: ${error.message}")
                            }
                        }
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

private val notificationEmojiRegex = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]")

/** Notifications are text only: strips emoji and tidies whitespace. */
internal fun plainNotificationText(value: String): String =
    value.replace(notificationEmojiRegex, "").replace(Regex("[ \\t]{2,}"), " ").trim()
