package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.Message
import com.google.firebase.auth.FirebaseAuth
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
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY_KEY)?.toString()?.trim().orEmpty()
        val recipientUid = intent.getStringExtra("senderId").orEmpty()
        if (reply.isBlank() || recipientUid.isBlank()) return
        val pending = goAsync()
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) { pending.finish(); return }
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("users").document(authUser.uid).get().addOnSuccessListener { meDoc ->
            firestore.collection("users").document(recipientUid).get().addOnSuccessListener { recipientDoc ->
                val chatId = listOf(authUser.uid, recipientUid).sorted().joinToString("_")
                val messageId = UUID.randomUUID().toString()
                val senderName = meDoc.getString("name") ?: authUser.displayName ?: "User"
                val message = Message(
                    messageId = messageId, senderId = authUser.uid, senderName = senderName,
                    senderUsername = meDoc.getString("username") ?: "", text = reply,
                    timestamp = System.currentTimeMillis()
                )
                FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages").child(messageId)
                    .setValue(message).addOnCompleteListener {
                        FirebaseDatabase.getInstance().getReference("notifications").child(recipientUid).setValue(
                            mapOf("senderId" to authUser.uid, "senderName" to senderName, "text" to reply, "timestamp" to System.currentTimeMillis())
                        )
                        sendPush(authUser.uid, senderName, meDoc.getString("profileImageUrl").orEmpty(), recipientDoc.getString("fcmToken").orEmpty(), recipientUid, messageId, reply) { pending.finish() }
                        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("notificationId", 0))
                    }
            }.addOnFailureListener { pending.finish() }
        }.addOnFailureListener { pending.finish() }
    }

    private fun sendPush(senderId: String, senderName: String, image: String, token: String, recipientUid: String, messageId: String, text: String, done: () -> Unit) {
        if (token.isBlank()) return done()
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener done()
            val payload = JSONObject().apply {
                put("token", token); put("title", "New message from $senderName"); put("body", text)
                put("senderId", senderId); put("senderName", senderName); put("senderProfileUrl", image)
                put("notificationType", "message"); put("targetId", messageId)
            }
            val request = Request.Builder().url("https://solitary-hill-dcdc.mr44253990.workers.dev/")
                .header("Authorization", "Bearer $idToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = done()
                override fun onResponse(call: Call, response: Response) { response.close(); done() }
            })
        }?.addOnFailureListener { done() } ?: done()
    }

    companion object { const val REPLY_KEY = "firechat_inline_reply" }
}
