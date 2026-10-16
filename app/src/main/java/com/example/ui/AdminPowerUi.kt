package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Server-side admin actions. Firestore writes rely on the admin rules; auth changes go through the Worker. */
object AdminPower {
    private val users get() = FirebaseFirestore.getInstance().collection("users")

    suspend fun setVerified(uid: String, on: Boolean) {
        users.document(uid).update(mapOf("role" to if (on) "moderator" else "user")).awaitTask()
        AdminAudit.log(if (on) "user_verify" else "user_unverify", uid)
    }

    suspend fun setPremium(uid: String, plan: String) {
        val now = System.currentTimeMillis()
        val fields: Map<String, Any> = when (plan) {
            "revoke" -> mapOf("isPremium" to false, "premiumPlan" to "", "premiumUntil" to 0L, "premiumSource" to "admin_revoked")
            "lifetime" -> mapOf("isPremium" to true, "premiumPlan" to "lifetime", "premiumUntil" to 0L, "premiumApprovedAt" to now, "premiumSource" to "admin_gift")
            "yearly" -> mapOf("isPremium" to true, "premiumPlan" to "yearly", "premiumUntil" to now + 365L * 86_400_000L, "premiumApprovedAt" to now, "premiumSource" to "admin_gift")
            else -> mapOf("isPremium" to true, "premiumPlan" to "monthly", "premiumUntil" to now + 30L * 86_400_000L, "premiumApprovedAt" to now, "premiumSource" to "admin_gift")
        }
        users.document(uid).update(fields).awaitTask()
        AdminAudit.log("user_premium_$plan", uid)
    }

    suspend fun updateProfile(uid: String, fields: Map<String, Any>) {
        users.document(uid).update(fields).awaitTask()
        AdminAudit.log("user_profile_edit", "$uid ${fields.keys.joinToString()}")
    }

    suspend fun deleteAllPosts(uid: String): Int {
        val db = FirebaseFirestore.getInstance()
        val docs = db.collection("posts").whereEqualTo("senderId", uid).get().awaitTask().documents
        docs.chunked(400).forEach { chunk -> db.runBatch { b -> chunk.forEach { b.delete(it.reference) } }.awaitTask() }
        val stories = db.collection("stories").whereEqualTo("senderId", uid).get().awaitTask().documents
        stories.chunked(400).forEach { chunk -> db.runBatch { b -> chunk.forEach { b.delete(it.reference) } }.awaitTask() }
        AdminAudit.log("user_delete_content", "$uid posts=${docs.size} stories=${stories.size}")
        return docs.size + stories.size
    }

    suspend fun worker(path: String, body: JSONObject): Pair<Boolean, String> = runCatching {
        val (code, json) = convoWorkerPost("database", path, body)
        val ok = code in 200..299 && json.optBoolean("success", true) && !json.has("error")
        ok to (if (ok) json.toString().take(160) else json.optString("error").ifBlank { "HTTP $code" } + if (code == 404) " — redeploy the Worker (v4.3.3)" else "")
    }.getOrElse { false to (it.localizedMessage ?: "Request failed") }

    suspend fun push(tokens: List<String>, title: String, body: String): Pair<Boolean, String> {
        val clean = tokens.filter { it.length > 20 }.distinct()
        if (clean.isEmpty()) return false to "No device tokens"
        var accepted = 0
        var lastError = ""
        clean.chunked(500).forEach { chunk ->
            val (ok, msg) = runCatching {
                val (code, json) = convoWorkerPost("push", "/admin/push", JSONObject().put("tokens", JSONArray(chunk)).put("title", title).put("body", body))
                accepted += json.optInt("acceptedByFcm")
                (code in 200..299) to json.optString("error")
            }.getOrElse { false to (it.localizedMessage ?: "failed") }
            if (!ok) lastError = msg
        }
        AdminAudit.log("admin_push", "targets=${clean.size} accepted=$accepted title=${title.take(60)}")
        return (accepted > 0) to (if (accepted > 0) "FCM accepted $accepted of ${clean.size} devices" else lastError.ifBlank { "No device accepted the push" })
    }
}

@Composable
private fun PowerChip(label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = tint.copy(alpha = .12f), modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
        }
    }
}

/** Full action grid for one user, shown in the admin quick-profile dialog. */
@Composable
fun AdminUserPowerActions(person: User, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> String>?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var pushOpen by remember { mutableStateOf(false) }
    val verified = person.role == "moderator"
    val premium = person.isPremium && (person.premiumPlan.equals("lifetime", true) || person.premiumUntil > System.currentTimeMillis())

    fun run(label: String, action: suspend () -> String) {
        busy = label
        scope.launch {
            val msg = runCatching { action() }.getOrElse { "Failed: ${it.localizedMessage}" }
            busy = null
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Admin actions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        val actions: List<Triple<String, ImageVector, Pair<Color, () -> Unit>>> = listOf(
            Triple(if (verified) "Remove badge" else "Verify badge", Icons.Default.Verified, Color(0xFF2F80ED) to { run("verify") { AdminPower.setVerified(person.uid, !verified); if (verified) "Badge removed" else "Verified badge granted" } }),
            Triple("Premium 30 days", Icons.Default.WorkspacePremium, Color(0xFFCF9829) to { run("p30") { AdminPower.setPremium(person.uid, "monthly"); "Premium granted for 30 days" } }),
            Triple("Premium lifetime", Icons.Default.Diamond, Color(0xFFB8860B) to { run("plife") { AdminPower.setPremium(person.uid, "lifetime"); "Lifetime Premium granted" } }),
            Triple("Revoke Premium", Icons.Default.MoneyOff, Color(0xFF8D6E63) to { if (premium) run("prev") { AdminPower.setPremium(person.uid, "revoke"); "Premium revoked" } else Toast.makeText(context, "User has no active Premium", Toast.LENGTH_SHORT).show() }),
            Triple("Rename", Icons.Default.Edit, Color(0xFF00897B) to { renameOpen = true }),
            Triple("Reset photo", Icons.Default.HideImage, Color(0xFF6D4C41) to { run("photo") { AdminPower.updateProfile(person.uid, mapOf("profileImageUrl" to "", "coverImageUrl" to "")); "Photo and cover reset" } }),
            Triple("Send push", Icons.Default.NotificationsActive, Color(0xFF7C4DFF) to { pushOpen = true }),
            Triple("Force logout", Icons.Default.Logout, Color(0xFFE65100) to {
                confirm = "Sign ${person.name} out of every device?" to suspend { val (ok, m) = AdminPower.worker("/admin/user/revoke", JSONObject().put("uid", person.uid)); AdminAudit.log("user_force_logout", person.uid); if (ok) "All sessions revoked" else m }
            }),
            Triple("Delete posts", Icons.Default.DeleteSweep, Color(0xFFD32F2F) to {
                confirm = "Delete ALL posts and stories of ${person.name}? This cannot be undone." to suspend { "Deleted ${AdminPower.deleteAllPosts(person.uid)} items" }
            }),
            Triple("Ban", Icons.Default.Block, Color(0xFFC62828) to {
                confirm = "Ban ${person.name}? The account is disabled and signed out." to suspend { val (ok, m) = AdminPower.worker("/admin/user/ban", JSONObject().put("uid", person.uid).put("reason", "Admin action")); AdminAudit.log("user_ban", person.uid); if (ok) "User banned" else m }
            }),
            Triple("Unban", Icons.Default.LockOpen, Color(0xFF2E7D32) to {
                run("unban") { val (ok, m) = AdminPower.worker("/admin/user/unban", JSONObject().put("uid", person.uid)); AdminAudit.log("user_unban", person.uid); if (ok) "User unbanned" else m }
            }),
            Triple("Clear bio", Icons.Default.FormatClear, Color(0xFF546E7A) to { run("bio") { AdminPower.updateProfile(person.uid, mapOf("bio" to "")); "Bio cleared" } })
        )
        actions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, icon, pair) -> PowerChip(label, icon, pair.first, Modifier.weight(1f)) { if (busy == null) pair.second() } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    confirm?.let { (question, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") },
            text = { Text(question) },
            confirmButton = { Button(onClick = { confirm = null; run("confirm") { action() } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Yes, do it") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
    if (renameOpen) {
        var name by remember { mutableStateOf(person.name) }
        var username by remember { mutableStateOf(person.username) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Edit name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(50) }, label = { Text("Display name") }, singleLine = true)
                    OutlinedTextField(username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c in "._" }.take(30) }, label = { Text("Username") }, singleLine = true)
                }
            },
            confirmButton = { Button(onClick = { renameOpen = false; run("rename") { AdminPower.updateProfile(person.uid, mapOf("name" to name.trim(), "username" to username.trim())); "Profile updated" } }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } }
        )
    }
    if (pushOpen) {
        var title by remember { mutableStateOf("Convo Chat") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pushOpen = false },
            title = { Text("Push to ${person.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(body, { body = it.take(400) }, label = { Text("Message") }, minLines = 3)
                }
            },
            confirmButton = { Button(onClick = { pushOpen = false; run("push") { AdminPower.push(listOf(person.fcmToken), title, body).second } }, enabled = body.isNotBlank() && person.fcmToken.isNotBlank()) { Text("Send") } },
            dismissButton = { TextButton(onClick = { pushOpen = false }) { Text("Cancel") } }
        )
    }
}

/** Global power tools: broadcast push, live switches and quick stats. */
@Composable
fun AdminPowerToolsPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val users by viewModel.usersState.collectAsState()
    val cfg by viewModel.flagshipConfig.collectAsState()
    var title by remember { mutableStateOf("Convo Chat") }
    var body by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("all") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var typingSound by remember(cfg.typingSoundEnabled) { mutableStateOf(cfg.typingSoundEnabled) }
    var reminders by remember(cfg.messageReminderEnabled) { mutableStateOf(cfg.messageReminderEnabled) }
    var reminderMinutes by remember(cfg.messageReminderMinutes) { mutableStateOf(cfg.messageReminderMinutes.toFloat()) }
    val now = System.currentTimeMillis()
    val online = users.count { it.isOnline && (it.onlineUntil <= 0L || it.onlineUntil > now) }
    val premium = users.count { it.isPremium && (it.premiumPlan.equals("lifetime", true) || it.premiumUntil > now) }
    val withToken = users.count { it.fcmToken.length > 20 }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Users" to users.size, "Online" to online, "Premium" to premium, "Push-ready" to withToken).forEach { (label, value) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f)).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$value", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("Broadcast push notification", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "Everyone", "online" to "Online now", "premium" to "Premium", "free" to "Free users").forEach { (id, label) ->
                FilterChip(selected = audience == id, onClick = { audience = id }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it.take(400) }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val targets = users.filter { u ->
                val isPrem = u.isPremium && (u.premiumPlan.equals("lifetime", true) || u.premiumUntil > now)
                when (audience) {
                    "online" -> u.isOnline && (u.onlineUntil <= 0L || u.onlineUntil > now)
                    "premium" -> isPrem
                    "free" -> !isPrem
                    else -> true
                }
            }.map { it.fcmToken }
            sending = true
            scope.launch {
                val (_, msg) = AdminPower.push(targets, title, body)
                result = msg; sending = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }, enabled = !sending && body.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Campaign, null)
            Spacer(Modifier.width(6.dp)); Text("Send broadcast")
        }
        if (result.isNotBlank()) Text(result, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()
        Text("Live switches", fontWeight = FontWeight.Bold)
        PowerSwitch("Typing sound", "Looping sound while the chat partner is typing (users can still mute it in Settings).", typingSound) { typingSound = it }
        PowerSwitch("Unread message reminder", "One reminder on the recipient's phone if a message stays unread.", reminders) { reminders = it }
        if (reminders) {
            Text("Remind after ${reminderMinutes.toInt()} minutes", fontSize = 12.sp)
            Slider(reminderMinutes, { reminderMinutes = it }, valueRange = 2f..60f, steps = 28)
        }
        Button(onClick = {
            viewModel.updateFlagshipFields(mapOf(
                "typingSoundEnabled" to typingSound,
                "messageReminderEnabled" to reminders,
                "messageReminderMinutes" to reminderMinutes.toInt()
            )) { ok -> Toast.makeText(context, if (ok) "Live switches saved for everyone" else "Save failed", Toast.LENGTH_SHORT).show() }
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save switches") }
        Text("Per-user actions (verify, Premium, rename, force logout, delete posts, ban, push) are in User management → Quick profile.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PowerSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
