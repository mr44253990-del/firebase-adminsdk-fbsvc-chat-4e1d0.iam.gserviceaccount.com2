package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.CachedMessage
import com.example.data.Message
import com.example.data.RememberedAccount
import com.example.security.AccountCredentialVault
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Multi-account "Switch & receive" notification action: sign in as the remembered account,
        // then open the chat. Runs before the reply guard because no reply text exists here.
        if (intent.action == ACTION_SWITCH_ACCOUNT) {
            val pending = goAsync()
            val switchUid = intent.getStringExtra("switchUid").orEmpty()
            val senderId = intent.getStringExtra("senderId").orEmpty()
            val notificationId = intent.getIntExtra("notificationId", 0)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { switchToAccount(context, switchUid) }
                NotificationManagerCompat.from(context).cancel(notificationId)
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("senderId", senderId)
                    })
                }
                pending.finish()
            }
            return
        }
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_KEY)?.toString()?.trim().orEmpty()
        val recipientUid = intent.getStringExtra("senderId").orEmpty()
        if (reply.isBlank() || recipientUid.isBlank()) return
        val pending = goAsync()
        var authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) { pending.finish(); return }
        // Multi-account: if this notification belonged to a non-active account, switch to that
        // account first so the inline reply posts to the RIGHT chat (and never to the wrong one).
        val accountUid = intent.getStringExtra("accountUid").orEmpty()
        val notificationId = intent.getIntExtra("notificationId", 0)
        if (accountUid.isNotBlank() && accountUid != authUser.uid) {
            val switched = kotlinx.coroutines.runBlocking { try { switchToAccount(context, accountUid) } catch (_: Throwable) { false } }
            val nowUser = FirebaseAuth.getInstance().currentUser
            if (!switched || nowUser == null || nowUser.uid != accountUid) {
                NotificationManagerCompat.from(context).cancel(notificationId)
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("senderId", recipientUid)
                    })
                }
                pending.finish()
                return
            }
            authUser = nowUser
        }
        val chatId = listOf(authUser.uid, recipientUid).sorted().joinToString("_")
        val messageId = UUID.randomUUID().toString()
        val sentAt = System.currentTimeMillis()
        val immediateName = authUser.displayName ?: authUser.email?.substringBefore("@") ?: "User"
        val immediate = Message(messageId=messageId,senderId=authUser.uid,senderName=immediateName,text=reply,timestamp=sentAt)
        showReplyInNotification(context, notificationId, reply, sentAt)
        FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages").child(messageId).setValue(immediate).addOnCompleteListener { saved ->
            if (!saved.isSuccessful) { pending.finish(); return@addOnCompleteListener }
            // Notification replies bypass ChatViewModel, so persist an equivalent local copy.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    AppDatabase.getDatabase(context).cacheDao()
                        .insertMessage(CachedMessage.fromMessage(immediate, chatId))
                }
            }
            // Replying means the conversation was read: clear my unread badge, mark their
            // messages seen, and bump the peer's unread counter exactly like an in-app send.
            FirebaseDatabase.getInstance().getReference("unread_counts").child(authUser.uid).child(recipientUid).setValue(0)
            FirebaseDatabase.getInstance().getReference("unread_counts").child(recipientUid).child(authUser.uid)
                .setValue(com.google.firebase.database.ServerValue.increment(1))
            DeliveryAck.ackFrom(recipientUid, seen = true)
            MessageReminder.markChatOpened(context, recipientUid)
            FirebaseDatabase.getInstance().getReference("notifications").child(recipientUid).setValue(mapOf("senderId" to authUser.uid,"senderName" to immediateName,"text" to reply,"timestamp" to sentAt))
            val firestore=FirebaseFirestore.getInstance()
            firestore.collection("users").document(authUser.uid).get().addOnCompleteListener { meTask ->
                val me=meTask.result; val finalName=me?.getString("name") ?: immediateName
                firestore.collection("users").document(recipientUid).get().addOnCompleteListener { recipientTask ->
                    val recipient=recipientTask.result
                    sendPush(authUser.uid,finalName,me?.getString("profileImageUrl").orEmpty(),recipient?.getString("fcmToken").orEmpty(),recipientUid,messageId,reply){pending.finish()}
                }
            }
        }
    }

    private fun sendPush(senderId: String, senderName: String, image: String, token: String, recipientUid: String, messageId: String, text: String, done: () -> Unit) {
        if (token.isBlank()) return done()
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener done()
            val payload = JSONObject().apply {
                put("token", token); put("title", "Convo Chat • $senderName"); put("body", text)
                put("senderId", senderId); put("senderName", senderName); put("senderProfileUrl", image)
                put("notificationType", "message"); put("targetId", messageId)
            }
            val request = Request.Builder().url(com.example.data.WorkerRouter.resolve("push", com.example.data.WorkerRouter.primary.value.ifBlank { "https://convo-chat-companion.mr4425390.workers.dev" }) + "/")
                .header("Authorization", "Bearer $idToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = done()
                override fun onResponse(call: Call, response: Response) { response.close(); done() }
            })
        }?.addOnFailureListener { done() } ?: done()
    }

    /** Keeps the conversation notification and appends "You: …" (like Messages/WhatsApp) instead of closing it. */
    private fun showReplyInNotification(context: Context, notificationId: Int, reply: String, sentAt: Long) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val active = manager.activeNotifications.firstOrNull { it.id == notificationId }?.notification
            val style = active?.let { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            if (active == null || style == null) { NotificationManagerCompat.from(context).cancel(notificationId); return }
            style.addMessage(androidx.core.app.NotificationCompat.MessagingStyle.Message(reply, sentAt, null as androidx.core.app.Person?))
            val rebuilt = androidx.core.app.NotificationCompat.Builder(context, active)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setSubText("Sent ✓")
                .setTimeoutAfter(8_000L)
                .build()
            manager.notify(notificationId, rebuilt)
        }.onFailure { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    companion object {
        const val REPLY_KEY = "firechat_inline_reply"
        const val ACTION_SWITCH_ACCOUNT = "com.ebchat.SWITCH_ACCOUNT_RECEIVE"
    }
}

/**
 * Signs in as a remembered (non-active) account using the credential vault. Used by both the
 * notification "Switch & receive" action and inline replies so messages always post as the right
 * account. Returns true only if the active Firebase user afterwards matches [uid].
 */
private suspend fun switchToAccount(context: Context, uid: String): Boolean = withContext(Dispatchers.IO) {
    if (uid.isBlank()) return@withContext false
    val account = RememberedAccount.findByUid(context, uid) ?: return@withContext false
    if (!account.provider.equals("password", ignoreCase = true)) return@withContext false
    val password = AccountCredentialVault.load(context, account.email) ?: return@withContext false
    if (password.isBlank()) return@withContext false
    val ok = runCatching {
        val task = FirebaseAuth.getInstance().signInWithEmailAndPassword(account.email, password)
        com.google.android.gms.tasks.Tasks.await(task, 15, java.util.concurrent.TimeUnit.SECONDS)
        true
    }.getOrDefault(false)
    val switched = ok && FirebaseAuth.getInstance().currentUser?.uid == uid
    if (switched) ActiveChatState.setActiveUid(uid)
    switched
}
