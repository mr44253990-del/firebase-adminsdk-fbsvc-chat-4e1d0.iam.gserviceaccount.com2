package com.example.ui

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.FragmentActivity
import com.example.MainActivity
import com.example.call.CallEngine
import com.example.call.CallState
import com.example.data.Message
import com.example.security.AppLockManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * v4.4 "Pro tools": device-local stores and helpers for saved messages, pinned messages,
 * chat locks, reminders, call history, message effects and launcher shortcuts.
 * Nothing here uploads private content; everything lives in app-private SharedPreferences.
 */
object ProTools {
    /** Full-screen page currently open from anywhere (chat list, settings, shortcuts). */
    private val _page = MutableStateFlow<String?>(null)
    val page: StateFlow<String?> = _page.asStateFlow()
    fun open(page: String) { _page.value = page }
    fun close() { _page.value = null }

    /** Message waiting for a "Remind me" time choice. */
    data class ReminderTarget(val peerUid: String, val peerName: String, val messageId: String, val text: String)
    private val _reminderTarget = MutableStateFlow<ReminderTarget?>(null)
    val reminderTarget: StateFlow<ReminderTarget?> = _reminderTarget.asStateFlow()
    fun askReminder(target: ReminderTarget?) { _reminderTarget.value = target }

    /** Launcher shortcut / deep action routed by MainActivity ("assistant" is a nav route). */
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()
    fun consumeRoute() { _pendingRoute.value = null }

    fun handleAction(action: String?) {
        when (action) {
            null, "" -> Unit
            "assistant" -> _pendingRoute.value = "assistant"
            "saved", "calls", "search", "hub" -> _page.value = action
        }
    }

    internal fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("convo_pro_tools", Context.MODE_PRIVATE)
    internal fun userKey(ctx: Context, base: String): String = base + "_" + (FirebaseAuth.getInstance().currentUser?.uid ?: "signed_out")

    fun messagePreview(m: Message): String = when {
        m.text.isNotBlank() -> if (ConvoStickers.isSticker(m.text)) "Sticker" else m.text
        !m.imageUrl.isNullOrBlank() -> "Photo"
        !m.voiceUrl.isNullOrBlank() -> "Voice message"
        !m.fileUrl.isNullOrBlank() -> "File: " + m.fileName.orEmpty()
        else -> "Message"
    }
}

// ------------------------------------------------------------------------------------------
// Saved / starred messages (#1)
// ------------------------------------------------------------------------------------------
data class SavedMessage(
    val id: String, val peerUid: String, val peerName: String, val senderName: String,
    val text: String, val imageUrl: String, val fileUrl: String, val fileName: String,
    val sentAt: Long, val savedAt: Long
)

object SavedMessages {
    private const val KEY = "saved_v1"
    private const val MAX = 500
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun all(ctx: Context): List<SavedMessage> = runCatching {
        val arr = JSONArray(ProTools.prefs(ctx).getString(ProTools.userKey(ctx, KEY), "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedMessage(
                o.optString("id"), o.optString("peer"), o.optString("peerName"), o.optString("sender"),
                o.optString("text"), o.optString("img"), o.optString("file"), o.optString("fileName"),
                o.optLong("sentAt"), o.optLong("savedAt")
            )
        }
    }.getOrDefault(emptyList())

    fun isSaved(ctx: Context, messageId: String): Boolean = all(ctx).any { it.id == messageId }

    private fun write(ctx: Context, list: List<SavedMessage>) {
        val arr = JSONArray()
        list.take(MAX).forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("peer", s.peerUid); put("peerName", s.peerName); put("sender", s.senderName)
                put("text", s.text.take(4000)); put("img", s.imageUrl); put("file", s.fileUrl); put("fileName", s.fileName)
                put("sentAt", s.sentAt); put("savedAt", s.savedAt)
            })
        }
        ProTools.prefs(ctx).edit().putString(ProTools.userKey(ctx, KEY), arr.toString()).apply()
        _version.value++
    }

    /** Returns true when the message is now saved, false when it was removed. */
    fun toggle(ctx: Context, m: Message, peerUid: String, peerName: String): Boolean {
        val list = all(ctx)
        if (list.any { it.id == m.messageId }) { write(ctx, list.filterNot { it.id == m.messageId }); return false }
        val entry = SavedMessage(
            m.messageId, peerUid, peerName, m.senderName, ProTools.messagePreview(m),
            m.imageUrl.orEmpty().takeIf { it.startsWith("http") }.orEmpty(),
            (m.remoteFileUrl ?: m.fileUrl).orEmpty().takeIf { it.startsWith("http") }.orEmpty(),
            m.fileName.orEmpty(), m.timestamp, System.currentTimeMillis()
        )
        write(ctx, listOf(entry) + list)
        return true
    }

    fun remove(ctx: Context, id: String) = write(ctx, all(ctx).filterNot { it.id == id })
    fun clear(ctx: Context) = write(ctx, emptyList())
}

// ------------------------------------------------------------------------------------------
// Pinned message per chat (#2) — local to this device
// ------------------------------------------------------------------------------------------
data class PinnedMessage(val id: String, val text: String, val senderName: String, val at: Long)

object PinnedMessages {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun get(ctx: Context, peerUid: String): PinnedMessage? = runCatching {
        val raw = ProTools.prefs(ctx).getString("pinmsg_$peerUid", null) ?: return null
        val o = JSONObject(raw)
        PinnedMessage(o.optString("id"), o.optString("text"), o.optString("sender"), o.optLong("at"))
    }.getOrNull()

    fun pin(ctx: Context, peerUid: String, m: Message) {
        val o = JSONObject().apply { put("id", m.messageId); put("text", ProTools.messagePreview(m).take(300)); put("sender", m.senderName); put("at", m.timestamp) }
        ProTools.prefs(ctx).edit().putString("pinmsg_$peerUid", o.toString()).apply()
        _version.value++
    }

    fun unpin(ctx: Context, peerUid: String) {
        ProTools.prefs(ctx).edit().remove("pinmsg_$peerUid").apply()
        _version.value++
    }
}

// ------------------------------------------------------------------------------------------
// Per-chat lock (#11)
// ------------------------------------------------------------------------------------------
object ChatLockSession {
    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())
    val unlocked: StateFlow<Set<String>> = _unlocked.asStateFlow()
    fun unlock(peerUid: String) { _unlocked.value = _unlocked.value + peerUid }
    /** Called when the app leaves the foreground: every locked chat asks again. */
    fun relockAll() { _unlocked.value = emptySet() }
}

object ChatLockAuth {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun deviceSecure(ctx: Context): Boolean {
        val bio = runCatching { BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)
        if (bio) return true
        return runCatching { (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure }.getOrDefault(false)
    }

    /** A chat can only be locked if something can unlock it again. */
    fun canLock(ctx: Context): Boolean = deviceSecure(ctx) || AppLockManager.isEnabled(ctx)

    fun findActivity(ctx: Context): FragmentActivity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    fun prompt(ctx: Context, title: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val activity = findActivity(ctx) ?: return onError("Unlock is not available here")
        if (!deviceSecure(ctx)) return onError("Set a screen lock or the Convo app PIN first")
        runCatching {
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onError(errString.toString()) }
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("Fingerprint, face or screen lock")
                    .setAllowedAuthenticators(AUTH)
                    .build()
            )
        }.onFailure { onError(it.localizedMessage ?: "Unlock failed") }
    }
}

// ------------------------------------------------------------------------------------------
// "Remind me about this message" (#17)
// ------------------------------------------------------------------------------------------
object MessageReminders {
    const val CHANNEL = "convo_pro_reminders_v1"

    fun schedule(ctx: Context, target: ProTools.ReminderTarget, at: Long): Boolean {
        val intent = Intent(ctx, ProReminderReceiver::class.java).apply {
            putExtra("senderId", target.peerUid); putExtra("peerName", target.peerName)
            putExtra("text", target.text.take(200)); putExtra("messageId", target.messageId)
        }
        val code = ("pro_rem_" + target.messageId + at).hashCode()
        val pending = PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching {
            // Inexact while-idle alarm: no exact-alarm permission needed; Doze may delay a few minutes.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarm.set(AlarmManager.RTC_WAKEUP, at, pending)
            true
        }.getOrDefault(false)
    }
}

class ProReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val senderId = intent.getStringExtra("senderId").orEmpty()
        val name = intent.getStringExtra("peerName").orEmpty().ifBlank { "a chat" }
        val text = intent.getStringExtra("text").orEmpty()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(MessageReminders.CHANNEL, "Convo Chat • Message reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set on individual messages"
            })
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (senderId.isNotBlank()) putExtra("senderId", senderId)
        }
        val pi = PendingIntent.getActivity(context, ("pro_rem_open_" + senderId + text).hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hide = com.example.security.PrivacyPreferences.load(context).hideNotificationContent ||
            AppLockManager.isEnabled(context) || ConversationPrefs.isLocked(context, senderId)
        val notification = NotificationCompat.Builder(context, MessageReminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setColor(0xFF7C5CFC.toInt())
            .setContentTitle("Reminder: message from $name")
            .setContentText(if (hide) "Tap to open the conversation" else text.ifBlank { "Tap to open the conversation" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (hide) "Tap to open the conversation" else text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .build()
        runCatching { manager.notify(("pro_rem_" + intent.getStringExtra("messageId").orEmpty()).hashCode(), notification) }
    }
}

// ------------------------------------------------------------------------------------------
// Call history (#37) — recorded on this device from CallEngine state changes
// ------------------------------------------------------------------------------------------
data class CallLogEntry(
    val callId: String, val peerUid: String, val peerName: String, val peerImage: String,
    val video: Boolean, val incoming: Boolean, val outcome: String, val durationSec: Long, val at: Long
)

object CallHistory {
    private const val KEY = "calls_v1"
    private val _entries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val entries: StateFlow<List<CallLogEntry>> = _entries.asStateFlow()
    @Volatile private var collectorStarted = false
    private var ownerUid = ""
    private var appCtx: Context? = null
    private var active: CallState? = null
    private var firstSeenAt = 0L

    @Synchronized fun ensureStarted(ctx: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (ownerUid != uid) {
            ownerUid = uid
            active = null
            _entries.value = if (uid.isBlank()) emptyList() else load(ctx)
        }
        appCtx = ctx.applicationContext
        if (collectorStarted) return
        collectorStarted = true
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            CallEngine.state.collect { st -> onState(st) }
        }
    }

    private val terminal = setOf("ended", "declined", "missed", "failed")

    @Synchronized private fun onState(st: CallState) {
        val live = st.status != "idle" && st.status !in terminal && st.callId.isNotBlank()
        if (live) {
            if (active?.callId != st.callId) firstSeenAt = System.currentTimeMillis()
            active = st
            return
        }
        val last = active ?: return
        if (st.status != "idle" && st.callId.isNotBlank() && st.callId != last.callId) return
        active = null
        val connectedAt = if (st.connectedAt > 0) st.connectedAt else last.connectedAt
        val incoming = last.direction.equals("incoming", true)
        val outcome = when {
            connectedAt > 0 -> "completed"
            st.status == "declined" -> "declined"
            incoming -> "missed"
            st.status == "missed" -> "no answer"
            st.status == "failed" -> "failed"
            else -> "cancelled"
        }
        val entry = CallLogEntry(
            last.callId, last.remoteUid, last.remoteName, last.remoteImage, last.video, incoming, outcome,
            if (connectedAt > 0) ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L) else 0L,
            firstSeenAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
        if (_entries.value.any { it.callId == entry.callId }) return
        val next = (listOf(entry) + _entries.value).take(300)
        _entries.value = next
        appCtx?.let { save(it, next) }
    }

    private fun load(ctx: Context): List<CallLogEntry> = runCatching {
        val arr = JSONArray(ProTools.prefs(ctx).getString(ProTools.userKey(ctx, KEY), "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CallLogEntry(o.optString("id"), o.optString("uid"), o.optString("name"), o.optString("img"), o.optBoolean("video"),
                o.optBoolean("in"), o.optString("outcome"), o.optLong("dur"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun save(ctx: Context, list: List<CallLogEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.callId); put("uid", e.peerUid); put("name", e.peerName); put("img", e.peerImage); put("video", e.video)
                put("in", e.incoming); put("outcome", e.outcome); put("dur", e.durationSec); put("at", e.at)
            })
        }
        ProTools.prefs(ctx).edit().putString(ProTools.userKey(ctx, KEY), arr.toString()).apply()
    }

    fun clear(ctx: Context) { _entries.value = emptyList(); save(ctx, emptyList()) }
    fun remove(ctx: Context, callId: String) { val next = _entries.value.filterNot { it.callId == callId }; _entries.value = next; save(ctx, next) }
}

// ------------------------------------------------------------------------------------------
// Message effects (#18): celebration words trigger a short full-screen effect
// ------------------------------------------------------------------------------------------
object MessageEffects {
    private val celebrate = listOf("happy birthday", "congrats", "congratulations", "শুভ জন্মদিন", "অভিনন্দন", "happy new year", "eid mubarak", "ঈদ মোবারক")
    private val love = listOf("love you", "miss you", "ভালোবাসি", "মিস করছি")
    private val laugh = listOf("hahaha", "lol", "হাহাহা")

    /** Returns the reaction used to drive [ReactionFx], or null when nothing matches. */
    fun effectFor(text: String): String? {
        val t = text.lowercase()
        return when {
            celebrate.any { t.contains(it) } -> "\uD83C\uDF89"
            love.any { t.contains(it) } -> "\u2764\uFE0F"
            laugh.any { t.contains(it) } -> "\uD83D\uDE02"
            else -> null
        }
    }
}

// ------------------------------------------------------------------------------------------
// Launcher shortcuts (#85): long-press the app icon
// ------------------------------------------------------------------------------------------
object ProShortcuts {
    fun publish(ctx: Context) {
        runCatching {
            fun shortcut(id: String, label: String, longLabel: String, icon: Int): ShortcutInfoCompat {
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("convoAction", id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                return ShortcutInfoCompat.Builder(ctx, "pro_$id")
                    .setShortLabel(label).setLongLabel(longLabel)
                    .setIcon(IconCompat.createWithResource(ctx, icon))
                    .setIntent(intent).build()
            }
            val list = listOf(
                shortcut("assistant", "AI assistant", "Ask the Convo AI assistant", android.R.drawable.ic_menu_help),
                shortcut("search", "Search chats", "Search all conversations", android.R.drawable.ic_menu_search),
                shortcut("saved", "Saved", "Saved messages", android.R.drawable.btn_star_big_off),
                shortcut("calls", "Calls", "Call history", android.R.drawable.ic_menu_call)
            )
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtLeast(1)
            // Conversation shortcuts are pushed by notifications; keep room for them.
            ShortcutManagerCompat.addDynamicShortcuts(ctx, list.take((max - 2).coerceIn(1, 4)))
        }
    }
}
