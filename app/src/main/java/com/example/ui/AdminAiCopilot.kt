package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FlagshipConfig
import com.example.data.User
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin AI Copilot (v5.0) — the admin types what they want in plain Bangla or English and the
 * copilot does it: "maintenance mode on for 30 minutes", "turn reels off", "ban @user",
 * "give @user premium 30 days", "send everyone: update is ready", "find rakib's e-mail",
 * "how many users are online?".
 *
 * Safety model:
 *  - Only a fixed whitelist of actions exists ([AdminAction]); the language model can only
 *    choose from it and can never run arbitrary code or write arbitrary database paths.
 *  - Every action is executed with the admin's own Firebase session, so the existing security
 *    rules and Worker admin checks still decide what is allowed.
 *  - Reversible switches can run instantly; actions that affect people (ban, sign-out, premium,
 *    broadcast) always ask for a confirmation tap first.
 *  - Everything is written to the admin audit log.
 */
sealed class AdminAction(val title: String, val detail: String, val needsConfirm: Boolean) {
    class SetFlags(title: String, detail: String, val fields: Map<String, Any>, confirm: Boolean = false) : AdminAction(title, detail, confirm)
    class Ban(val user: User, val banned: Boolean) : AdminAction(if (banned) "Ban ${user.name}" else "Unban ${user.name}", "@${user.username}", true)
    class Premium(val user: User, val days: Int) : AdminAction(if (days > 0) "Give ${user.name} Premium for $days days" else "Remove Premium from ${user.name}", "@${user.username}", true)
    class Broadcast(val heading: String, val body: String) : AdminAction("Send a push to every user", "$heading — $body", true)
    class ShowUsers(val users: List<User>, heading: String) : AdminAction(heading, "", false)
    class Report(val text: String) : AdminAction("Live report", text, false)
}

private data class CopilotLine(
    val role: String,
    val text: String,
    val actions: List<AdminAction> = emptyList(),
    val id: Long = System.nanoTime()
)

/** Switches the copilot understands, with the words (English + Bangla) that select them. */
private val COPILOT_FLAGS: List<Triple<String, String, List<String>>> = listOf(
    Triple("maintenanceMode", "Maintenance mode", listOf("maintenance", "maintain", "মেইনটেন", "মেইনটেইন", "মেনটেন", "রক্ষণাবেক্ষণ")),
    Triple("reelsEnabled", "Reels", listOf("reels", "reel", "রিল")),
    Triple("assistantEnabled", "AI assistant", listOf("assistant", "ai chat", "chatbot", "এআই", "অ্যাসিস্ট্যান্ট")),
    Triple("locationSharingEnabled", "Location sharing", listOf("location", "লোকেশন")),
    Triple("typingSoundEnabled", "Typing sound", listOf("typing sound", "টাইপিং")),
    Triple("premiumEnabled", "Premium", listOf("premium system", "premium feature", "প্রিমিয়াম সিস্টেম")),
    Triple("premiumUpsellEnabled", "Premium upsell banners", listOf("upsell", "premium banner", "premium ad")),
    Triple("tiktokModeEnabled", "TikTok mode", listOf("tiktok", "টিকটক")),
    Triple("phoneAuthEnabled", "Phone sign-in", listOf("phone login", "phone sign", "phone auth", "ফোন লগইন")),
    Triple("aiWebSearchEnabled", "AI web search", listOf("web search", "ওয়েব সার্চ")),
    Triple("aiStreamingEnabled", "AI live answers", listOf("streaming", "live answer")),
    Triple("smartReplyEnabled", "Smart replies", listOf("smart reply", "স্মার্ট রিপ্লাই")),
    Triple("autoSaveReceivedFilesEnabled", "Media auto-save", listOf("auto save", "auto-save", "autosave", "অটো সেভ")),
    Triple("messageReminderEnabled", "Unread message reminders", listOf("reminder", "রিমাইন্ডার")),
    Triple("contactsDiscoveryEnabled", "Contacts discovery", listOf("contacts", "contact discovery", "কন্টাক্ট")),
    Triple("noticeEnabled", "Notice banner", listOf("notice", "নোটিশ")),
    Triple("updateEnabled", "Update campaign", listOf("update campaign", "apk update", "আপডেট ক্যাম্পেইন")),
    Triple("mandatoryUpdate", "Forced update", listOf("force update", "mandatory update", "বাধ্যতামূলক আপডেট")),
    Triple("funCampaignEnabled", "Friday fun campaign", listOf("friday", "fun campaign", "শুক্রবার")),
    Triple("analyticsEnabled", "Analytics", listOf("analytics", "অ্যানালিটিক্স"))
)
private val ON_WORDS = listOf("enable", "start", "turn on", "activate", "চালু", "অন ", "অন করো", "শুরু")
private val OFF_WORDS = listOf("disable", "stop", "turn off", "deactivate", "বন্ধ", "অফ", "শেষ করো")

private fun findUsers(users: List<User>, accounts: Map<String, AdminAccountInfo>, needle: String, limit: Int = 6): List<User> {
    val q = needle.trim().removePrefix("@").lowercase()
    if (q.length < 2) return emptyList()
    return users.filter { adminSmartUserMatch(it, accounts[it.uid], q) }
        .sortedByDescending { adminSmartUserScore(it, accounts[it.uid], q) }.take(limit)
}

private fun minutesIn(text: String): Long {
    val m = Regex("(\\d+)\\s*(minute|minutes|min|mins|মিনিট|hour|hours|hr|hrs|ঘন্টা|ঘণ্টা|day|days|দিন)").find(text) ?: return 0L
    val n = m.groupValues[1].toLongOrNull() ?: return 0L
    return when {
        m.groupValues[2].startsWith("h") || m.groupValues[2].startsWith("ঘ") -> n * 60
        m.groupValues[2].startsWith("d") || m.groupValues[2].startsWith("দ") -> n * 1440
        else -> n
    }
}

private fun liveReport(users: List<User>, config: FlagshipConfig): String {
    val now = System.currentTimeMillis()
    val online = users.count { it.isOnline && (it.onlineUntil <= 0L || it.onlineUntil > now) }
    val premium = users.count { it.isPremium && (it.premiumPlan.equals("lifetime", true) || it.premiumUntil > now) }
    val today = users.count { it.createdAt > 0 && now - it.createdAt < 86_400_000L }
    val week = users.count { it.createdAt > 0 && now - it.createdAt < 7L * 86_400_000L }
    val active7 = users.count { it.lastActive > 0 && now - it.lastActive < 7L * 86_400_000L }
    val versions = users.filter { it.appVersion.isNotBlank() }.groupingBy { it.appVersion }.eachCount().entries.sortedByDescending { it.value }.take(4)
        .joinToString("  ·  ") { "${it.key}: ${it.value}" }
    return buildString {
        append("Users: ${users.size}  ·  online now: $online  ·  active in 7 days: $active7\n")
        append("New today: $today  ·  new this week: $week  ·  Premium: $premium\n")
        if (versions.isNotBlank()) append("App versions → $versions\n")
        append("Maintenance: ${if (config.maintenanceMode) "ON" else "off"}  ·  Reels: ${if (config.reelsEnabled) "on" else "OFF"}  ·  AI: ${if (config.assistantEnabled) "on" else "OFF"}  ·  Notice: ${if (config.noticeEnabled) "on" else "off"}")
    }
}

/** Deterministic parser — works even when no AI key is configured. Returns null when unsure. */
private fun parseAdminCommand(raw: String, users: List<User>, accounts: Map<String, AdminAccountInfo>, config: FlagshipConfig): Pair<String, List<AdminAction>>? {
    val text = " " + raw.trim() + " "
    val lower = text.lowercase()
    val wantsOn = Regex("\\bon\\b").containsMatchIn(lower) || ON_WORDS.any { lower.contains(it) }
    val wantsOff = Regex("\\boff\\b").containsMatchIn(lower) || OFF_WORDS.any { lower.contains(it) }
    val mention = Regex("@([A-Za-z0-9_.]{2,40})").find(raw)?.groupValues?.get(1)

    // ---- broadcast ---------------------------------------------------------------------
    val broadcastKey = listOf("broadcast", "send everyone", "notify everyone", "notify all", "push to all", "সবাইকে", "সব ইউজার")
    if (broadcastKey.any { lower.contains(it) }) {
        val body = raw.substringAfter(':', "").trim().ifBlank { raw.substringAfter("everyone", "").substringAfter("সবাইকে", "").trim() }
        if (body.length < 3) return "What should the notification say? Example: \"send everyone: New update is ready 🎉\"" to emptyList()
        return "Broadcast prepared — confirm to send it to every device." to listOf(AdminAction.Broadcast("Convo Chat", body.take(240)))
    }

    // ---- notice / announcement -----------------------------------------------------------
    if ((lower.contains("notice") || lower.contains("নোটিশ") || lower.contains("announcement") || lower.contains("ঘোষণা")) && raw.contains(':')) {
        val body = raw.substringAfter(':').trim()
        val title = body.substringBefore('|').trim().take(60)
        val message = body.substringAfter('|', body).trim().take(400)
        return "Notice banner is ready." to listOf(AdminAction.SetFlags("Show notice: $title", message, mapOf("noticeEnabled" to true, "noticeTitle" to title, "noticeBody" to message)))
    }

    // ---- AI identity -----------------------------------------------------------------
    Regex("(?i)(ai name|assistant name|এআই নাম)\\s*[:=]\\s*(.+)").find(raw)?.let { m ->
        val name = m.groupValues[2].trim().take(40)
        return "AI name change is ready." to listOf(AdminAction.SetFlags("Rename the assistant to \"$name\"", "", mapOf("aiDisplayName" to name)))
    }

    // ---- report ------------------------------------------------------------------------
    if (listOf("stats", "report", "status", "how many", "overview", "কতজন", "কত জন", "রিপোর্ট", "অবস্থা", "পরিসংখ্যান").any { lower.contains(it) } && !wantsOn && !wantsOff) {
        return "Here is the live picture." to listOf(AdminAction.Report(liveReport(users, config)))
    }

    // ---- switches ----------------------------------------------------------------------
    if (wantsOn != wantsOff) {
        val hits = COPILOT_FLAGS.filter { (_, _, words) -> words.any { lower.contains(it) } }
        if (hits.isNotEmpty()) {
            val actions = hits.map { (key, label, _) ->
                if (key == "maintenanceMode") {
                    val minutes = if (wantsOn) minutesIn(lower) else 0L
                    val endsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
                    AdminAction.SetFlags(
                        if (wantsOn) "Turn maintenance mode ON" + (if (minutes > 0) " for $minutes min" else "") else "Turn maintenance mode OFF",
                        if (wantsOn) "Users see the maintenance screen" + (if (minutes > 0) " with a countdown; it ends automatically." else " until you turn it off.") else "Everyone can use the app again.",
                        mapOf("maintenanceMode" to wantsOn, "maintenanceEndsAt" to endsAt)
                    )
                } else AdminAction.SetFlags("Turn $label ${if (wantsOn) "ON" else "OFF"}", "", mapOf(key to wantsOn), confirm = key == "mandatoryUpdate" && wantsOn)
            }
            return (if (actions.size == 1) "Understood." else "Understood — ${actions.size} changes.") to actions
        }
    }
    // ---- people ----------------------------------------------------------------------
    val userWords = listOf("unban", "ban ", "ব্যান", "আনব্যান", "premium", "প্রিমিয়াম", "find", "search", "email", "e-mail", "ইমেইল", "খুঁজ", "profile", "প্রোফাইল", "who is", "info")
    if (userWords.any { lower.contains(it) } && !lower.contains("premium system") && !lower.contains("upsell")) {
        val stop = setOf("unban", "ban", "premium", "find", "search", "email", "e-mail", "of", "the", "user", "users", "profile", "give", "remove", "revoke", "for", "days", "day", "please", "show", "me", "who", "is", "info", "about", "to", "from", "and", "his", "her", "get", "account",
            "ব্যান", "আনব্যান", "প্রিমিয়াম", "ইমেইল", "খুঁজে", "খুঁজ", "প্রোফাইল", "দাও", "করো", "কর", "এর", "কে", "দিন", "বের", "ইউজার", "দেখাও", "তথ্য")
        val needle = mention ?: Regex("[\\w.@+-]+@[\\w-]+\\.[\\w.]+").find(raw)?.value
            ?: raw.split(Regex("[^\\p{L}\\p{N}_.]+")).filter { it.length >= 2 && it.lowercase() !in stop && it.toIntOrNull() == null }.joinToString(" ")
        val found = if (needle.isBlank()) emptyList() else findUsers(users, accounts, needle).ifEmpty {
            needle.split(' ').firstOrNull { it.length >= 3 }?.let { findUsers(users, accounts, it) }.orEmpty()
        }
        val isUnban = lower.contains("unban") || lower.contains("আনব্যান")
        val isBan = !isUnban && (lower.contains(" ban ") || lower.contains("ব্যান"))
        val isPremium = lower.contains("premium") || lower.contains("প্রিমিয়াম")
        if (found.isEmpty()) return "I could not find anyone matching \"$needle\". Try the @username, the e-mail or a few letters of the name." to emptyList()
        if (isBan || isUnban) {
            return (if (found.size == 1) "Ready — confirm to continue." else "Several people match. Confirm the right one.") to found.take(3).map { AdminAction.Ban(it, isBan) }
        }
        if (isPremium) {
            val remove = wantsOff || listOf("remove", "revoke", "cancel", "বাতিল", "সরাও").any { lower.contains(it) }
            val days = if (remove) 0 else (minutesIn(lower) / 1440).toInt().takeIf { it in 1..365 } ?: 30
            return "Premium change prepared — confirm to apply." to found.take(3).map { AdminAction.Premium(it, days) }
        }
        return "Found ${found.size} matching ${if (found.size == 1) "profile" else "profiles"}." to listOf(AdminAction.ShowUsers(found, "Profiles from your database"))
    }

    return null
}

/** Turns the model's JSON plan into whitelisted actions (unknown keys are dropped). */
private fun actionsFromAiJson(json: JSONObject, users: List<User>, accounts: Map<String, AdminAccountInfo>, config: FlagshipConfig): List<AdminAction> {
    val out = mutableListOf<AdminAction>()
    val array = json.optJSONArray("actions") ?: return out
    val allowed = COPILOT_FLAGS.associate { it.first to it.second }
    for (i in 0 until array.length()) {
        val a = array.optJSONObject(i) ?: continue
        when (a.optString("type")) {
            "set_flag" -> {
                val key = a.optString("key"); val label = allowed[key] ?: continue
                val value = a.optBoolean("value")
                if (key == "maintenanceMode") {
                    val minutes = a.optLong("minutes", 0L).coerceIn(0L, 10_080L)
                    out += AdminAction.SetFlags("Turn maintenance mode ${if (value) "ON" else "OFF"}" + (if (value && minutes > 0) " for $minutes min" else ""), "",
                        mapOf("maintenanceMode" to value, "maintenanceEndsAt" to (if (value && minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L)))
                } else out += AdminAction.SetFlags("Turn $label ${if (value) "ON" else "OFF"}", "", mapOf(key to value), confirm = key == "mandatoryUpdate" && value)
            }
            "notice" -> out += AdminAction.SetFlags("Show notice: ${a.optString("title").take(60)}", a.optString("body").take(400),
                mapOf("noticeEnabled" to true, "noticeTitle" to a.optString("title").take(60), "noticeBody" to a.optString("body").take(400)))
            "broadcast" -> a.optString("body").takeIf { it.length >= 3 }?.let { out += AdminAction.Broadcast(a.optString("title").ifBlank { "Convo Chat" }.take(60), it.take(240)) }
            "ban", "unban" -> findUsers(users, accounts, a.optString("user"), 1).firstOrNull()?.let { out += AdminAction.Ban(it, a.optString("type") == "ban") }
            "premium" -> findUsers(users, accounts, a.optString("user"), 1).firstOrNull()?.let { out += AdminAction.Premium(it, a.optInt("days", 30).coerceIn(0, 365)) }
            "find_user" -> findUsers(users, accounts, a.optString("user")).takeIf { it.isNotEmpty() }?.let { out += AdminAction.ShowUsers(it, "Profiles from your database") }
            "report" -> out += AdminAction.Report(liveReport(users, config))
        }
    }
    return out
}

@Composable
fun AdminAiCopilotPanel(viewModel: ChatViewModel, onOpenUser: (User) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by viewModel.flagshipConfig.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val accounts by AdminAccounts.accounts.collectAsState()
    var lines by remember { mutableStateOf(listOf(CopilotLine("ai", "I am your admin copilot. Tell me what to do in Bangla or English — I can switch features, start maintenance, send a broadcast, manage Premium, ban accounts and pull any profile (with e-mail) from your database."))) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var instant by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(setOf<Long>()) }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, busy) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1 + if (busy) 1 else 0) }
    LaunchedEffect(users.size) { if (users.isNotEmpty()) AdminAccounts.ensure(users.take(300).map { it.uid }) }

    fun say(text: String, actions: List<AdminAction> = emptyList()) { lines = lines + CopilotLine("ai", text, actions) }

    fun run(action: AdminAction, key: Long) {
        done = done + key
        when (action) {
            is AdminAction.SetFlags -> viewModel.updateFlagshipFields(action.fields) { ok ->
                AdminAudit.log("copilot_flags", action.fields.entries.joinToString { "${it.key}=${it.value}" })
                say(if (ok) "✅ Done — ${action.title.replaceFirstChar { it.lowercase() }}." else "❌ That did not save. Only the main admin account can change app settings.")
            }
            is AdminAction.Ban -> viewModel.setUserBanned(action.user.uid, action.banned, "Admin copilot") { ok, message ->
                AdminAudit.log(if (action.banned) "copilot_ban" else "copilot_unban", action.user.uid)
                say(if (ok) "✅ ${action.user.name} is now ${if (action.banned) "banned and signed out" else "unbanned"}." else "❌ ${message.ifBlank { "The server refused this action." }}")
            }
            is AdminAction.Premium -> {
                val finish: (Boolean) -> Unit = { ok ->
                    AdminAudit.log("copilot_premium", "${action.user.uid} days=${action.days}")
                    say(if (ok) "✅ Premium ${if (action.days > 0) "granted for ${action.days} days to" else "removed from"} ${action.user.name}." else "❌ Premium change failed.")
                }
                if (action.days > 0) viewModel.grantPremiumGift(listOf(action.user.uid), action.days, finish) else viewModel.revokePremium(action.user.uid, finish)
            }
            is AdminAction.Broadcast -> scope.launch {
                val tokens = users.map { it.fcmToken }
                val (ok, message) = AdminPower.push(tokens, action.heading, action.body)
                say(if (ok) "✅ Broadcast sent. $message" else "❌ Broadcast failed: $message")
            }
            is AdminAction.ShowUsers, is AdminAction.Report -> Unit
        }
    }

    fun submit(text: String = input) {
        val prompt = text.trim()
        if (prompt.isBlank() || busy) return
        input = ""
        lines = lines + CopilotLine("me", prompt)
        val parsed = parseAdminCommand(prompt, users, accounts, config)
        if (parsed != null) {
            val line = CopilotLine("ai", parsed.first, parsed.second)
            lines = lines + line
            if (instant) parsed.second.forEachIndexed { i, action -> if (!action.needsConfirm && action is AdminAction.SetFlags) run(action, line.id + i) }
            return
        }
        // Not a known phrase: let the model translate it into the whitelisted action schema.
        busy = true
        val schema = """You are the admin copilot of the Convo Chat app. Convert the admin's request into JSON only, no prose outside JSON:
{"reply":"short friendly answer in the admin's language","actions":[ ... ]}
Allowed actions (use only these):
{"type":"set_flag","key":"<one of: ${COPILOT_FLAGS.joinToString(",") { it.first }}>","value":true|false,"minutes":0}
{"type":"notice","title":"...","body":"..."}
{"type":"broadcast","title":"...","body":"..."}
{"type":"ban","user":"username or name"} {"type":"unban","user":"..."}
{"type":"premium","user":"...","days":30}  (days 0 removes premium)
{"type":"find_user","user":"name, username or e-mail"}
{"type":"report"}
If the request is only a question, answer it in "reply" and use an empty actions array. Never invent other action types."""
        val state = "Current app state:\n" + liveReport(users, config)
        viewModel.askAssistant(prompt, listOf(schema, state)) { reply ->
            busy = false
            val jsonText = reply.substringAfter("```json", reply).substringBefore("```").let { t -> t.indexOf('{').takeIf { it >= 0 }?.let { s -> t.substring(s, t.lastIndexOf('}') + 1) } }
            val json = jsonText?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json == null) { say(reply.replace("**", "").trim().ifBlank { "I did not understand that. Try: \"maintenance on for 30 minutes\"." }); return@askAssistant }
            val actions = actionsFromAiJson(json, users, accounts, config)
            val line = CopilotLine("ai", json.optString("reply").ifBlank { if (actions.isEmpty()) "I could not turn that into an action." else "Here is my plan." }, actions)
            lines = lines + line
            if (instant) actions.forEachIndexed { i, action -> if (!action.needsConfirm && action is AdminAction.SetFlags) run(action, line.id + i) }
        }
    }

    val motion = rememberInfiniteTransition(label = "copilot")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "drift")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B0A1F), Color(0xFF1A1446), Color(0xFF07242E)), start = Offset(drift * 400f, 0f), end = Offset(900f - drift * 300f, 1400f)))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFF8B7BFF).copy(alpha = .7f), Color(0xFF36D8E6).copy(alpha = .5f), Color(0xFFFF6FB5).copy(alpha = .5f))), RoundedCornerShape(28.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6), Color(0xFFFF6FB5), Color(0xFF8B7BFF)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Admin AI Copilot", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(if (busy) "Thinking…" else "Ready · ${users.size} profiles · ${accounts.size} e-mails in memory", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = instant, onCheckedChange = { instant = it })
                Text("Instant", color = Color.White.copy(alpha = .7f), fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                "Maintenance on for 30 minutes", "Maintenance off", "Live report", "Turn reels off", "Turn reels on",
                "Notice: Update | New version is ready", "Send everyone: Update is ready 🎉", "Find user rakib", "AI assistant off"
            ).forEach { sample ->
                Text(
                    sample, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .10f)).border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
                        .clickable { submit(sample) }.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 430.dp), state = listState, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(lines, key = { it.id }) { line ->
                if (line.role == "me") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(line.text, color = Color.White, fontSize = 13.5.sp, modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3AA0FF)))).padding(horizontal = 13.dp, vertical = 9.dp))
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(line.text, color = Color.White.copy(alpha = .94f), fontSize = 13.5.sp, modifier = Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 13.dp, vertical = 9.dp))
                    line.actions.forEachIndexed { index, action ->
                        val key = line.id + index
                        when (action) {
                            is AdminAction.Report -> Text(action.text, color = Color(0xFFB9F5DC), fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF19C37D).copy(alpha = .10f)).border(1.dp, Color(0xFF19C37D).copy(alpha = .3f), RoundedCornerShape(16.dp)).padding(12.dp))
                            is AdminAction.ShowUsers -> action.users.forEach { person ->
                                val account = accounts[person.uid]
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .07f)).clickable { onOpenUser(person) }.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(person.profileImageUrl.ifBlank { null }, person.name, Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF8B7BFF).copy(alpha = .4f)))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(person.name.ifBlank { "Unnamed" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("@${person.username}" + (account?.email?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""), color = Color(0xFF9FE8FF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${if (person.isOnline) "Online" else "Offline"} · ${if (person.isPremium) "Premium" else "Free"} · joined ${if (person.createdAt > 0) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(person.createdAt)) else "?"} · ${person.followers.size} followers" + (person.appVersion.takeIf { it.isNotBlank() }?.let { " · v$it" } ?: ""),
                                            color = Color.White.copy(alpha = .6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = {
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("User", "${person.name} @${person.username} ${account?.email.orEmpty()} UID ${person.uid}"))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(16.dp)) }
                                }
                            }
                            else -> {
                                val finished = key in done
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background((if (action.needsConfirm) Color(0xFFFF8A3D) else Color(0xFF8B7BFF)).copy(alpha = .12f))
                                        .border(1.dp, (if (action.needsConfirm) Color(0xFFFF8A3D) else Color(0xFF8B7BFF)).copy(alpha = .4f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (action.needsConfirm) Icons.Default.Shield else Icons.Default.Bolt, null, tint = if (action.needsConfirm) Color(0xFFFFB37A) else Color(0xFFB8AEFF), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(action.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        if (action.detail.isNotBlank()) Text(action.detail, color = Color.White.copy(alpha = .65f), fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (finished) Text("Sent", color = Color(0xFF7EF0B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    else Button(
                                        onClick = { run(action, key) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = if (action.needsConfirm) Color(0xFFFF7A2F) else Color(0xFF6C5CE7), contentColor = Color.White)
                                    ) { Text(if (action.needsConfirm) "Confirm" else "Run", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
            if (busy) item(key = "busy") { Text("● ● ●", color = Color(0xFF9FE8FF), fontSize = 12.sp, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .08f)).padding(horizontal = 14.dp, vertical = 8.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .09f))
                .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(24.dp)).padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input, onValueChange = { input = it.take(500) },
                placeholder = { Text("e.g. মেইনটেন মোড ৩০ মিনিটের জন্য অন করো", color = Color.White.copy(alpha = .45f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f), maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF36D8E6)
                )
            )
            IconButton(
                onClick = { submit() }, enabled = input.isNotBlank() && !busy,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6))))
            ) { Icon(Icons.Default.ArrowUpward, "Send", tint = Color.White) }
        }
        Text("Switches run instantly when Instant is on. Bans, Premium changes and broadcasts always ask you to confirm. Every action is saved in the audit log.", color = Color.White.copy(alpha = .5f), fontSize = 10.sp)
    }
}
