package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private val ConsoleInk = Color(0xFF0A0A1A)
private val ConsoleGreen = Color(0xFF42E695)
private val ConsoleAmber = Color(0xFFFFC24B)

private data class ConsoleLine(val fromUser: Boolean, val text: String)

/**
 * Admin AI Console — the admin talks to the app in plain Bangla or English and the console
 * executes real actions: maintenance mode, announcements, feature toggles, stats, user
 * lookup, verify/premium/ban/force-logout and device locate. Free-form questions fall
 * through to the AI assistant with a live app-state summary as context.
 */
@Composable
fun AdminAiConsoleScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val users by viewModel.usersState.collectAsState()
    val flagship by viewModel.flagshipConfig.collectAsState()
    val emails by viewModel.adminAuthEmails.collectAsState()
    var lines by remember { mutableStateOf(listOf(ConsoleLine(false, "Admin console ready. Try: \"maintenance on 30\", \"announce Dinner at 9pm\", \"find rakib\", \"stats\", \"help\"."))) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadAdminAuthEmails() }
    LaunchedEffect(lines.size, busy) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    fun reply(text: String) { lines = lines + ConsoleLine(false, text) }

    fun findUser(query: String) = users.firstOrNull { it.username.equals(query, true) }
        ?: users.firstOrNull { it.name.contains(query, ignoreCase = true) }
        ?: users.firstOrNull { it.username.contains(query.removePrefix("@"), ignoreCase = true) }

    fun submit(text: String) {
        val command = text.trim()
        if (command.isBlank() || busy) return
        input = ""
        lines = lines + ConsoleLine(true, command)
        val q = command.lowercase()
        busy = true

        when {
            // ---- help ----
            q == "help" || q == "?" || q.contains("কী কী করতে পারো") -> {
                reply(
                    "Commands (Bangla or English):\n" +
                        "• maintenance on / off / on 30 (minutes)\n" +
                        "• announce <message>\n" +
                        "• reels on/off • ai posts on/off • assistant on/off • web search on/off\n" +
                        "• stats\n" +
                        "• find <name or @username>\n" +
                        "• verify <@username> • premium <@username> • revoke premium <@username>\n" +
                        "• ban <@username> • unban <@username> • logout <@username>\n" +
                        "• locate <@username> (flash + vibrate the device)\n" +
                        "Anything else is answered by AI with live app context."
                )
                busy = false
            }

            // ---- maintenance mode ----
            q.contains("maintenance") || q.contains("মেইনটেন্যান্স") || q.contains("মেইনটেনেন্স") -> {
                val turnOn = !(q.contains("off") || q.contains("বন্ধ"))
                val minutes = Regex("(\\d{1,4})\\s*(min|minute|মিনিট)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 60
                viewModel.updateFlagshipFields(
                    mapOf(
                        "maintenanceMode" to turnOn,
                        "maintenanceTitle" to if (turnOn) "Scheduled maintenance" else "",
                        "maintenanceEndsAt" to if (turnOn) System.currentTimeMillis() + minutes * 60_000L else 0L,
                        "noticeEnabled" to turnOn,
                        "noticeTitle" to if (turnOn) "We'll be right back" else "",
                        "noticeBody" to if (turnOn) "The admin is performing maintenance. Please check back in ~$minutes minutes." else ""
                    )
                ) { ok ->
                    reply(if (ok) "Maintenance mode ${if (turnOn) "ON for $minutes minutes — all non-admin users now see the countdown" else "turned OFF"}." else "Could not update maintenance mode.")
                    busy = false
                }
            }

            // ---- announcement ----
            q.startsWith("announce") || q.contains("ঘোষণা দাও") || q.contains("announcement") -> {
                val body = command.substringAfter("announce").substringAfter("ঘোষণা দাও").trim().ifBlank { command.substringAfter("announcement").trim() }
                if (body.isBlank()) { reply("Usage: announce <message>"); busy = false }
                else viewModel.publishAnnouncementAdmin("Notice from admin", body) { ok, msg ->
                    reply(if (ok) "Published: every user now sees the announcement banner." else msg)
                    busy = false
                }
            }

            // ---- feature toggles ----
            (q.contains("reels") || q.contains("ai post") || q.contains("assistant") || q.contains("web search")) && (q.contains("on") || q.contains("off") || q.contains("চালু") || q.contains("বন্ধ")) -> {
                val turnOn = !(q.contains("off") || q.contains("বন্ধ"))
                val field = when {
                    q.contains("reels") -> "reelsEnabled"
                    q.contains("ai post") || q.contains("ai পোস্ট") -> "autoPostEnabled"
                    q.contains("assistant") -> "assistantEnabled"
                    else -> "aiWebSearchEnabled"
                }
                viewModel.updateFlagshipFields(mapOf(field to turnOn)) { ok ->
                    reply(if (ok) "$field is now ${if (turnOn) "ON" else "OFF"}." else "Could not update the flag.")
                    busy = false
                }
            }

            // ---- stats ----
            q == "stats" || q.contains("কত ইউজার") || q.contains("user count") || q.contains("app stats") -> {
                val online = users.count { it.isOnline }
                val premium = users.count { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > System.currentTimeMillis()) }
                reply(
                    "Live app stats:\n• Registered profiles visible: ${users.size + 1}\n• Online right now: $online\n• Active Premium: $premium\n• Maintenance mode: ${if (flagship.maintenanceMode) "ON" else "off"}\n• Assistant: ${if (flagship.assistantEnabled) "on" else "off"} • Reels: ${if (flagship.reelsEnabled) "on" else "off"}"
                )
                busy = false
            }

            // ---- find user ----
            q.startsWith("find") || q.startsWith("search") || q.contains("খুঁজে দাও") -> {
                val query = command.substringAfter("find").substringAfter("search").substringAfter("খুঁজে দাও").trim().removePrefix("@")
                val found = findUser(query)
                if (found == null) reply("No user matched \"$query\".")
                else {
                    val email = emails[found.uid].orEmpty().ifBlank { "(no e-mail loaded)" }
                    reply(
                        "Found: ${found.name} (@${found.username})\n• Online: ${if (found.isOnline) "yes" else "no"}\n• Followers: ${found.followers.size} • Friends: ${found.friends.size}\n• Premium: ${if (found.isPremium) "yes" else "no"} • Role: ${found.role}\n• E-mail: $email\n• UID: ${found.uid}\n\nUse: verify/premium/ban/logout/locate @${found.username}"
                    )
                }
                busy = false
            }

            // ---- per-user admin actions ----
            listOf("verify", "unverify", "premium", "revoke premium", "ban", "unban", "logout", "locate").any { q.startsWith(it) } -> {
                val action = listOf("revoke premium", "unverify", "verify", "premium", "unban", "ban", "logout", "locate").first { q.startsWith(it) }
                val query = command.substringAfter(action).trim().removePrefix("@")
                val target = findUser(query)
                if (target == null) {
                    reply("No user matched \"$query\". Try: find <name>.")
                    busy = false
                } else if (action == "locate") {
                    viewModel.adminLocateUser(target) { ok, msg ->
                        reply(if (ok) "Locate signal sent to ${target.name}: $msg" else msg)
                        busy = false
                    }
                } else {
                    scope.launch {
                        val message = when (action) {
                            "verify" -> { AdminPower.setVerified(target.uid, true); "Verified badge granted to ${target.name}." }
                            "unverify" -> { AdminPower.setVerified(target.uid, false); "Verified badge removed from ${target.name}." }
                            "premium" -> { AdminPower.setPremium(target.uid, "monthly"); "Premium (30 days) granted to ${target.name}." }
                            "revoke premium" -> { AdminPower.setPremium(target.uid, "revoke"); "Premium revoked for ${target.name}." }
                            "ban" -> { val (ok, m) = AdminPower.worker("/admin/user/ban", JSONObject().put("uid", target.uid).put("reason", "Admin AI console")); AdminAudit.log("user_ban", target.uid); if (ok) "${target.name} banned." else m }
                            "unban" -> { val (ok, m) = AdminPower.worker("/admin/user/unban", JSONObject().put("uid", target.uid)); AdminAudit.log("user_unban", target.uid); if (ok) "${target.name} unbanned." else m }
                            else -> { AdminPower.updateProfile(target.uid, mapOf("forceLogoutAt" to System.currentTimeMillis())); val (ok, m) = AdminPower.worker("/admin/user/revoke", JSONObject().put("uid", target.uid)); if (ok) "All sessions of ${target.name} revoked." else m }
                        }
                        reply(message)
                        busy = false
                    }
                }
            }

            // ---- anything else: ask the AI with admin context ----
            else -> {
                val adminContext = "admin_context: total_profiles=${users.size + 1}, online=${users.count { it.isOnline }}, premium=${users.count { it.isPremium }}, maintenance=${flagship.maintenanceMode}, assistant=${flagship.assistantEnabled}, reels=${flagship.reelsEnabled}. You are answering the APP ADMIN. Be concise and practical."
                viewModel.askAssistant(command, listOf(adminContext)) { result ->
                    reply(result)
                    busy = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(ConsoleInk)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(ConsoleGreen, Color(0xFF7C5CFF), ConsoleAmber, ConsoleGreen))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.AdminPanelSettings, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Admin AI Console", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Runs real actions — maintenance, announcements, users, locate", color = Color.White.copy(alpha = .55f), fontSize = 11.sp, maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("stats", "help", "maintenance on", "maintenance off").forEach { chip ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = .08f),
                    modifier = Modifier.clickable { submit(chip) }
                ) {
                    Text(chip, color = ConsoleGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(lines) { line ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        horizontalAlignment = if (line.fromUser) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            if (line.fromUser) "ADMIN" else "CONSOLE",
                            color = if (line.fromUser) ConsoleAmber else ConsoleGreen,
                            fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .widthIn(max = 330.dp)
                                .clip(RoundedCornerShape(if (line.fromUser) 18.dp else 4.dp, 18.dp, 18.dp, 18.dp))
                                .background(if (line.fromUser) Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFB24CFF))) else Brush.linearGradient(listOf(Color.White.copy(alpha = .09f), Color.White.copy(alpha = .06f))))
                                .padding(horizontal = 13.dp, vertical = 9.dp)
                        ) {
                            Text(line.text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
            if (busy) item {
                Text("Working…", color = ConsoleGreen, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(500) },
                placeholder = { Text("Type a command or ask anything…", color = Color.White.copy(alpha = .4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = ConsoleGreen, unfocusedBorderColor = Color.White.copy(alpha = .18f),
                    cursorColor = ConsoleGreen
                )
            )
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(
                        if (busy) Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .12f)))
                        else Brush.linearGradient(listOf(Color(0xFF7C5CFF), ConsoleGreen))
                    )
                    .clickable(enabled = !busy) { submit(input) },
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.ArrowUpward, "Run", tint = Color.White)
            }
        }
    }
}
