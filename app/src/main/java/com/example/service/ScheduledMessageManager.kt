package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.UUID

/**
 * Device-local scheduled message queue. The alarm only wakes the app; the receiver
 * writes the same RTDB Message shape used by ChatViewModel. No secrets are stored.
 */
object ScheduledMessageManager {
    private const val PREFS = "scheduled_messages_v1"
    private const val IDS = "ids"
    private const val PREFIX = "message_"

    fun schedule(
        context: Context,
        senderId: String,
        senderName: String,
        senderUsername: String,
        recipientUid: String,
        chatId: String,
        text: String,
        triggerAt: Long,
        expiresAt: Long = 0L
    ): String {
        val id = UUID.randomUUID().toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(IDS, emptySet()).orEmpty().toMutableSet().apply { add(id) }
        prefs.edit()
            .putStringSet(IDS, ids)
            .putString(PREFIX + id + "_senderId", senderId)
            .putString(PREFIX + id + "_senderName", senderName)
            .putString(PREFIX + id + "_senderUsername", senderUsername)
            .putString(PREFIX + id + "_recipientUid", recipientUid)
            .putString(PREFIX + id + "_chatId", chatId)
            .putString(PREFIX + id + "_text", text.trim().take(10000))
            .putLong(PREFIX + id + "_triggerAt", triggerAt)
            .putLong(PREFIX + id + "_expiresAt", expiresAt)
            .apply()

        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ScheduledMessageReceiver.ACTION_SEND_SCHEDULED
            putExtra(ScheduledMessageReceiver.EXTRA_ID, id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        return id
    }

    fun cancel(context: Context, id: String) {
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ScheduledMessageReceiver.ACTION_SEND_SCHEDULED
            putExtra(ScheduledMessageReceiver.EXTRA_ID, id)
        }
        context.getSystemService(AlarmManager::class.java).cancel(
            PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        remove(context, id)
    }

    internal fun remove(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(IDS, emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet(IDS, ids)
            .remove(PREFIX + id + "_senderId")
            .remove(PREFIX + id + "_senderName")
            .remove(PREFIX + id + "_senderUsername")
            .remove(PREFIX + id + "_recipientUid")
            .remove(PREFIX + id + "_chatId")
            .remove(PREFIX + id + "_text")
            .remove(PREFIX + id + "_triggerAt")
            .remove(PREFIX + id + "_expiresAt")
            .apply()
    }

    internal fun read(context: Context, id: String): Map<String, Any> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return mapOf(
            "senderId" to (prefs.getString(PREFIX + id + "_senderId", "") ?: ""),
            "senderName" to (prefs.getString(PREFIX + id + "_senderName", "") ?: ""),
            "senderUsername" to (prefs.getString(PREFIX + id + "_senderUsername", "") ?: ""),
            "recipientUid" to (prefs.getString(PREFIX + id + "_recipientUid", "") ?: ""),
            "chatId" to (prefs.getString(PREFIX + id + "_chatId", "") ?: ""),
            "text" to (prefs.getString(PREFIX + id + "_text", "") ?: ""),
            "triggerAt" to prefs.getLong(PREFIX + id + "_triggerAt", 0L),
            "expiresAt" to prefs.getLong(PREFIX + id + "_expiresAt", 0L)
        )
    }
}

class ScheduledMessageReceiver : android.content.BroadcastReceiver() {
    companion object {
        const val ACTION_SEND_SCHEDULED = "com.ebchat.action.SEND_SCHEDULED_MESSAGE"
        const val EXTRA_ID = "scheduledMessageId"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SEND_SCHEDULED) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        val data = ScheduledMessageManager.read(context, id)
        val senderId = data["senderId"] as String
        val recipientUid = data["recipientUid"] as String
        val chatId = data["chatId"] as String
        val text = data["text"] as String
        if (senderId.isBlank() || recipientUid.isBlank() || chatId.isBlank() || text.isBlank()) {
            ScheduledMessageManager.remove(context, id)
            pending.finish()
            return
        }
        val message = mapOf(
            "messageId" to id,
            "senderId" to senderId,
            "senderName" to (data["senderName"] as String),
            "senderUsername" to (data["senderUsername"] as String),
            "text" to text,
            "timestamp" to System.currentTimeMillis(),
            "edited" to false,
            "deliveredToRecipient" to false,
            "seenByRecipient" to false,
            "expiresAt" to (data["expiresAt"] as Long)
        )
        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
        val chatRef = database.getReference("chats").child(chatId)
        chatRef.child("members").updateChildren(mapOf(senderId to true, recipientUid to true))
        chatRef.child("messages").child(id).setValue(message).addOnCompleteListener {
            if (it.isSuccessful) {
                database.getReference("unread_counts").child(recipientUid).child(senderId)
                    .get().addOnSuccessListener { snapshot ->
                        val count = snapshot.getValue(Int::class.java) ?: 0
                        snapshot.ref.setValue(count + 1)
                    }
                database.getReference("notifications").child(recipientUid).setValue(
                    mapOf("senderId" to senderId, "senderName" to data["senderName"], "text" to text, "timestamp" to System.currentTimeMillis())
                )
            }
            ScheduledMessageManager.remove(context, id)
            pending.finish()
        }
    }
}

fun Context.scheduleMessage(
    senderId: String,
    senderName: String,
    senderUsername: String,
    recipientUid: String,
    chatId: String,
    text: String,
    triggerAt: Long,
    expiresAt: Long = 0L
): String = ScheduledMessageManager.schedule(this, senderId, senderName, senderUsername, recipientUid, chatId, text, triggerAt, expiresAt)
