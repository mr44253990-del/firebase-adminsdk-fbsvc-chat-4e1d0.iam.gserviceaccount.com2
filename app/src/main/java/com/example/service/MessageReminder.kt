package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity

/**
 * One gentle reminder for a direct message that is still unread after N minutes (admin setting,
 * default 10). Scheduled on the RECIPIENT's device when the push arrives, so it only fires on a
 * phone that is actually online. Opening that chat (or a newer message from the same person)
 * replaces/cancels it, and a message never produces more than one reminder.
 */
object MessageReminder {
    private const val PREFS = "convo_message_reminders"
    const val CHANNEL = "convo_reminders_v1"

    private fun requestCode(senderId: String) = ("reminder_" + senderId).hashCode()

    fun schedule(context: Context, senderId: String, senderName: String, preview: String, sentAt: Long) {
        if (senderId.isBlank()) return
        val flags = context.getSharedPreferences("convo_remote_flags", Context.MODE_PRIVATE)
        if (!flags.getBoolean("reminder_on", true)) return
        val minutes = flags.getInt("reminder_minutes", 10).coerceIn(1, 240)
        val intent = Intent(context, MessageReminderReceiver::class.java).apply {
            putExtra("senderId", senderId); putExtra("senderName", senderName)
            putExtra("preview", preview.take(120)); putExtra("sentAt", sentAt)
        }
        val pending = PendingIntent.getBroadcast(context, requestCode(senderId), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + minutes * 60_000L
        // Inexact + allow-while-idle: no exact-alarm permission needed; Doze may delay it slightly.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarm.set(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Called whenever a chat becomes visible: nothing from this person needs a reminder. */
    fun markChatOpened(context: Context, peerUid: String) {
        if (peerUid.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("open_$peerUid", System.currentTimeMillis()).apply()
        val pending = PendingIntent.getBroadcast(
            context, requestCode(peerUid), Intent(context, MessageReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending); pending.cancel() }
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(("reminder_" + peerUid).hashCode()) }
    }

    internal fun lastOpened(context: Context, peerUid: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("open_$peerUid", 0L)

    internal fun alreadyReminded(context: Context, peerUid: String, sentAt: Long): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getLong("reminded_$peerUid", 0L) >= sentAt) return true
        p.edit().putLong("reminded_$peerUid", sentAt).apply()
        return false
    }
}

class MessageReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val senderId = intent.getStringExtra("senderId").orEmpty()
        val sentAt = intent.getLongExtra("sentAt", 0L)
        if (senderId.isBlank()) return
        val remote = context.getSharedPreferences("convo_remote_flags", Context.MODE_PRIVATE)
        if (!remote.getBoolean("reminder_on", true)) return
        if (ActiveChatState.isViewingPeer(senderId)) return
        if (MessageReminder.lastOpened(context, senderId) >= sentAt) return
        if (context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE).getStringSet("muted_users", emptySet())?.contains(senderId) == true) return
        if (com.example.ui.ConversationPrefs.isMuted(context, senderId)) return
        if (com.example.ui.SmartPrefs.inQuietHours(context)) return
        if (MessageReminder.alreadyReminded(context, senderId, sentAt)) return

        val name = intent.getStringExtra("senderName").orEmpty().ifBlank { "Someone" }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(MessageReminder.CHANNEL, "Convo Chat • Unread reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One reminder when a message stays unread"
                setShowBadge(true)
            })
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("senderId", senderId); putExtra("notificationType", "message"); putExtra("targetId", "")
        }
        val pi = PendingIntent.getActivity(context, ("reminder_open_" + senderId).hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val minutes = ((System.currentTimeMillis() - sentAt) / 60_000L).coerceAtLeast(1)
        val hide = com.example.security.PrivacyPreferences.load(context).hideNotificationContent
        val notification = NotificationCompat.Builder(context, MessageReminder.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setColor(0xFFFF8A3D.toInt())
            .setLargeIcon(ConvoNotificationArt.initialsAvatar(name, senderId))
            .setContentTitle("⏰ Unread message from $name")
            .setContentText(if (hide) "Sent $minutes min ago — tap to open" else intent.getStringExtra("preview").orEmpty().ifBlank { "Sent $minutes min ago" })
            .setSubText("Reminder")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (hide) "" else intent.getStringExtra("preview").orEmpty() + "\n") + "Sent $minutes min ago and not opened yet."
            ))
            .setWhen(sentAt).setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setShortcutId(ConvoNotificationArt.shortcutId(senderId))
            .build()
        manager.notify(("reminder_" + senderId).hashCode(), notification)
    }
}

/** Shared visuals for modern conversation notifications. */
object ConvoNotificationArt {
    private val palette = intArrayOf(0xFF7C5CFC.toInt(), 0xFF00A884.toInt(), 0xFFFF6B6B.toInt(), 0xFF2F80ED.toInt(), 0xFFF2994A.toInt(), 0xFFEB5757.toInt(), 0xFF9B51E0.toInt(), 0xFF27AE60.toInt())

    fun accentFor(key: String): Int = palette[(key.hashCode() and 0x7fffffff) % palette.size]

    fun shortcutId(peerUid: String) = "chat_$peerUid"

    /** Circular coloured avatar with the sender's initials, used when there is no photo. */
    fun initialsAvatar(name: String, key: String, size: Int = 160): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentFor(key) }
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
        p.color = 0xFFFFFFFF.toInt(); p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = size * if (initials.length > 1) .38f else .46f
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(initials, size / 2f, y, p)
        return bmp
    }

    /**
     * Publishes a long-lived conversation shortcut so Android 11+ shows the chat in the
     * "Conversations" section with the person's avatar (and allows priority conversations).
     */
    fun pushConversationShortcut(context: Context, peerUid: String, name: String, avatar: Bitmap?) {
        runCatching {
            // Plain (non-adaptive) bitmap: adaptive icons are zoom-cropped and could hide the face.
            val icon = IconCompat.createWithBitmap(avatar ?: initialsAvatar(name, peerUid))
            val person = Person.Builder().setName(name).setKey(peerUid).setIcon(icon).setImportant(true).build()
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("senderId", peerUid); putExtra("notificationType", "message"); putExtra("targetId", "")
            }
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(peerUid))
                .setShortLabel(name.take(24).ifBlank { "Chat" })
                .setLongLabel(name.ifBlank { "Chat" })
                .setIcon(icon)
                .setPerson(person)
                .setLongLived(true)
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }
}

/** "Mark as read" action on message notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    companion object { const val ACTION_MUTE_HOUR = "com.example.action.MUTE_CHAT_HOUR" }

    override fun onReceive(context: Context, intent: Intent) {
        val senderId = intent.getStringExtra("senderId").orEmpty()
        if (senderId.isBlank()) return
        if (intent.action == ACTION_MUTE_HOUR) {
            com.example.ui.ConversationPrefs.setMuted(context, senderId, System.currentTimeMillis() + 3_600_000L)
            runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(("chat_" + senderId).hashCode()) }
            android.widget.Toast.makeText(context, "Muted for 1 hour", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(("chat_" + senderId).hashCode()) }
        MessageReminder.markChatOpened(context, senderId)
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { me ->
            runCatching { com.google.firebase.database.FirebaseDatabase.getInstance().getReference("unread_counts").child(me).child(senderId).setValue(0) }
        }
    }
}
