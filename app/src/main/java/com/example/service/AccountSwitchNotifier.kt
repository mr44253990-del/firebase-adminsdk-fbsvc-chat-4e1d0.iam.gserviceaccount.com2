package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import org.json.JSONArray

/**
 * Several Convo accounts can be saved on one phone, and they all share one FCM token. A push
 * meant for a saved account that is NOT the active one must never be shown as if it belonged
 * to the active account. Instead the user sees "Switch account": one tap signs in to the
 * right account and opens the chat (or the ringing call).
 */
object AccountSwitchNotifier {
    private const val CHANNEL = "convo_account_switch_v1"

    data class SavedAccount(val uid: String, val name: String, val email: String)

    fun savedAccounts(context: Context): List<SavedAccount> = runCatching {
        val raw = context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE).getString("remembered_accounts", "[]")
        val array = JSONArray(raw)
        List(array.length()) { i -> array.getJSONObject(i).let { SavedAccount(it.optString("uid"), it.optString("name"), it.optString("email")) } }
            .filter { it.uid.isNotBlank() }
    }.getOrDefault(emptyList())

    /**
     * Who the push was addressed to. New senders add `recipientUid`; the `chatId` pair
     * ("uidA_uidB") is the fallback that already passes through older Workers.
     */
    fun intendedRecipient(data: Map<String, String>): String {
        data["recipientUid"]?.takeIf { it.isNotBlank() }?.let { return it }
        val sender = data["senderId"].orEmpty()
        val pair = data["chatId"].orEmpty().split("_")
        if (sender.isBlank() || pair.size != 2 || sender !in pair) return ""
        return pair.firstOrNull { it != sender }.orEmpty()
    }

    fun show(context: Context, account: SavedAccount, data: Map<String, String>, notificationType: String, senderName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Other accounts on this phone", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Messages and calls for your other saved Convo Chat accounts"
                    enableVibration(true)
                }
            )
        }
        val isCall = notificationType == "incoming_call" || notificationType == "incoming_video_call"
        val isGroupCall = notificationType == "group_call"
        val video = notificationType == "incoming_video_call" || data["videoCall"].toBoolean()
        val accountLabel = account.name.ifBlank { account.email.substringBefore('@') }.ifBlank { "your other account" }
        val notificationId = ("switch_" + account.uid + (if (isCall || isGroupCall) "_call" else "_msg")).hashCode()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("switchAccountUid", account.uid)
            putExtra("switchNotificationId", notificationId)
            putExtra("senderId", data["senderId"].orEmpty())
            putExtra("notificationType", if (isCall || isGroupCall) notificationType else "message")
            if (isCall || isGroupCall) {
                putExtra("switchForCall", true)
                putExtra("switchGroupCall", isGroupCall)
                putExtra("callId", data["targetId"].orEmpty())
                putExtra("callerId", data["senderId"].orEmpty())
                putExtra("callerName", senderName)
                putExtra("callerImage", data["senderProfileUrl"].orEmpty())
                putExtra("videoCall", video)
                putExtra("roomId", data["roomId"].orEmpty())
                putExtra("groupId", data["groupId"] ?: data["targetId"].orEmpty())
                putExtra("groupName", data["groupName"].orEmpty())
                putExtra("memberIds", data["memberIds"].orEmpty())
            }
        }
        val pending = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = when {
            isCall || isGroupCall -> "Incoming ${if (video) "video" else "audio"} call for $accountLabel"
            else -> "New message for $accountLabel"
        }
        val body = when {
            isCall -> "$senderName is calling. Switch account to receive the call."
            isGroupCall -> "$senderName started a group call. Switch account to join."
            notificationType == "message" || notificationType == "group_message" -> "Someone sent a message. Switch account to read it."
            else -> "New activity on this account. Switch account to view it."
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (isCall || isGroupCall) android.R.drawable.sym_call_incoming else android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF7C5CFC.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isCall || isGroupCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_menu_rotate, if (isCall || isGroupCall) "Switch & answer" else "Switch account", pending)
        if (isCall || isGroupCall) builder.setTimeoutAfter(45_000).setFullScreenIntent(pending, false)
        runCatching { manager.notify(notificationId, builder.build()) }
    }
}
