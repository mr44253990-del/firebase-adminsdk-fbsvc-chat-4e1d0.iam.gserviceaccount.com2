package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private data class AdminAiLine(val role: String, val text: String)

/**
 * AdminAiControlScreen — admin → Admin AI.
 *
 * A natural-language control surface for the admin: plain commands are parsed locally and
 * executed as verified writes to Firestore config. Supported actions:
 *   - "maintenance mode on [message]" / "off"  → config/global.maintenanceMode + message
 *   - "broadcast <text>"                        → adminBroadcasts/<push> (picked up by the worker)
 *   - "enable/disable ai"                       → config/global.assistantEnabled
 *   - "enable/disable web search"               → config/global.aiWebSearchEnabled
 * Every executed action is echoed back with the exact field that changed.
 */
@Composable
fun AdminAiControlScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val lines = remember { mutableStateListOf<AdminAiLine>(AdminAiLine("assistant", "Hi Admin. I can toggle maintenance mode, broadcast announcements, enable/disable AI features, and more. Try a command or tap a chip below.")) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

    fun say(text: String) { lines.add(AdminAiLine("assistant", text)) }

    fun runCommand(raw: String) {
        val q = raw.trim().lowercase()
        if (q.isBlank()) return
        lines.add(AdminAiLine("user", raw))
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { executeAdminCommand(q, raw) }
            busy = false
            say(result)
        }
        input = ""
    }

    val quickActions = listOf(
        "Maintenance mode ON" to Icons.Default.Construction,
        "Maintenance mode OFF" to Icons.Default.Bolt,
        "Broadcast: app update available" to Icons.Default.Campaign,
        "Disable AI assistant" to Icons.Default.SmartToy
    )

    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF070616), Color(0xFF120B2E), Color(0xFF070616)), start = Offset.Zero, end = Offset(900f, 1500f)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8)))).padding(8.dp)) {
                    Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Admin AI", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) { GlowDot(color = Color(0xFF29D3FF), size = 6.dp); Spacer(Modifier.width(6.dp)) }
                        Text(if (busy) "Executing…" else "Voice of the server — tap or type a command", color = if (busy) Color(0xFF29D3FF) else Color.White.copy(alpha = .6f), fontSize = 11.sp)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickActions.forEach { (label, icon) ->
                    Row(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = .10f)).border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
                            .clickable { runCommand(label) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = Color(0xFF29D3FF), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lines, key = { it.hashCode() * (lines.indexOf(it) + 1) }) { line ->
                    val isUser = line.role == "user"
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (isUser) .78f else .9f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isUser) Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8)))
                                    else Brush.linearGradient(listOf(Color.White.copy(alpha = .10f), Color.White.copy(alpha = .06f)))
                                )
                                .border(1.dp, if (isUser) Color.Transparent else Color.White.copy(alpha = .14f), RoundedCornerShape(18.dp))
                                .padding(12.dp)
                        ) { Text(line.text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp) }
                    }
                }
                if (busy) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF29D3FF), strokeWidth = 2.5.dp)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.ime + androidx.compose.foundation.layout.WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = .09f))
                    .border(1.dp, Brush.linearGradient(listOf(Color(0xFF29D3FF).copy(alpha = .6f), Color(0xFFFF4FD8).copy(alpha = .5f))), RoundedCornerShape(26.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (input.isEmpty()) Text("e.g. maintenance mode on, broadcast hello…", color = Color.White.copy(alpha = .45f), fontSize = 13.sp)
                    BasicTextField(
                        value = input, onValueChange = { input = it.take(500) }, enabled = !busy,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFF29D3FF)),
                        modifier = Modifier.fillMaxWidth(), maxLines = 3
                    )
                }
                Spacer(Modifier.width(6.dp))
                val canSend = input.isNotBlank() && !busy
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (canSend) Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8))) else Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .12f))))
                        .clickable(enabled = canSend) { runCommand(input) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ArrowUpward, "Run", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

/** Parses and executes one admin command. Returns a human-readable confirmation. */
private suspend fun executeAdminCommand(q: String, raw: String): String = runCatching {
    val db = FirebaseFirestore.getInstance()
    val configRef = db.collection("config").document("global")
    when {
        ("maintenance" in q || "maintain" in q) && "off" in q -> {
            configRef.set(mapOf("maintenanceMode" to false, "maintenanceMessage" to "", "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ Maintenance mode is now OFF. Users can use the app normally."
        }
        ("maintenance" in q || "maintain" in q) -> {
            val msg = raw.substringAfter("on", "").trim().ifBlank { "App is under quick maintenance — back in a few minutes." }
            configRef.set(mapOf("maintenanceMode" to true, "maintenanceMessage" to msg, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ Maintenance mode ON. All users will see: \"$msg\""
        }
        q.startsWith("broadcast") || "announce" in q -> {
            val text = raw.substringAfter(":").trim().ifBlank { raw.substringAfter("broadcast").trim().ifBlank { "Hello from Convo Chat admin!" } }
            val id = db.collection("adminBroadcasts").document().id
            db.collection("adminBroadcasts").document(id).set(mapOf("text" to text, "sentAt" to System.currentTimeMillis(), "type" to "admin_broadcast")).await()
            "✅ Broadcast queued ($id): \"$text\" — the FCM worker will deliver it to all users."
        }
        ("disable" in q || "turn off" in q) && "ai" in q -> {
            configRef.set(mapOf("assistantEnabled" to false, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ AI assistant disabled for all users."
        }
        ("enable" in q || "turn on" in q) && "ai" in q -> {
            configRef.set(mapOf("assistantEnabled" to true, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ AI assistant enabled for all users."
        }
        ("disable" in q || "turn off" in q) && "web search" in q -> {
            configRef.set(mapOf("aiWebSearchEnabled" to false, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ AI web search disabled."
        }
        ("enable" in q || "turn on" in q) && "web search" in q -> {
            configRef.set(mapOf("aiWebSearchEnabled" to true, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ AI web search enabled."
        }
        "status" in q || "what is" in q -> {
            val snap = configRef.get().await()
            val mm = snap.getBoolean("maintenanceMode") == true
            val ai = snap.getBoolean("assistantEnabled") ?: true
            "📊 Current state — maintenance: ${if (mm) "ON" else "off"}, AI assistant: ${if (ai) "enabled" else "disabled"}."
        }
        ("ban" in q) && ("user" in q || "uid" in q || "account" in q) -> {
            val uid = raw.trim().split(Regex("\\s+")).firstOrNull { it.length >= 10 && !it.lowercase().let { t -> t in setOf("ban","user","uid","account","please","the","for") } }
                ?: return@runCatching "⚠️ Could not find a UID. Say: ban user <uid>"
            val now = System.currentTimeMillis()
            db.collection("users").document(uid).set(mapOf("banned" to true, "bannedAt" to now, "bannedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "admin"), "forceLogoutAt" to now), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ User $uid banned and force-logged out."
        }
        ("wipe" in q) && ("post" in q) -> {
            val uid = raw.trim().split(Regex("\\s+")).firstOrNull { it.length >= 10 && !it.lowercase().let { t -> t in setOf("wipe","posts","post","user","for","the","please") } }
                ?: return@runCatching "⚠️ Could not find a target UID. Say: wipe posts for <uid>"
            val id = db.collection("adminCommands").document().id
            db.collection("adminCommands").document(id).set(mapOf("type" to "wipe_user_posts", "targetUid" to uid, "requestedAt" to System.currentTimeMillis(), "requestedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "admin"))).await()
            "✅ Wipe queued ($id) for user $uid — the worker will delete their posts."
        }
        (("force" in q || "kick" in q) && ("logout" in q || "signout" in q || "sign out" in q)) || ("log out" in q && "user" in q) -> {
            val uid = raw.trim().split(Regex("\\s+")).firstOrNull { it.length >= 10 && !it.lowercase().let { t -> t in setOf("force","logout","log","out","kick","user","sign","please","the") } }
                ?: return@runCatching "⚠️ Could not find a target UID. Say: force logout <uid>"
            db.collection("users").document(uid).set(mapOf("forceLogoutAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ Force-logout stamp written for $uid — client signs out on next resume."
        }
        ("min" in q) && ("version" in q) -> {
            val code = Regex("\\d+").find(raw)?.value?.toIntOrNull()
            if (code == null || code <= 0) return@runCatching "⚠️ Specify a numeric versionCode. Say: set min app version 42"
            db.collection("config").document("appConfig").set(mapOf("minVersionCode" to code, "minVersionEnforced" to true, "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge()).await()
            "✅ Min app version set to $code (enforced). Clients below are blocked on start."
        }
        else -> "I can run: maintenance on/off, broadcast <text>, enable/disable ai/web search, ban user <uid>, wipe posts for <uid>, force logout <uid>, set min app version <n>, status."
    }
}.getOrElse { "⚠️ Command failed: ${it.message}" }
