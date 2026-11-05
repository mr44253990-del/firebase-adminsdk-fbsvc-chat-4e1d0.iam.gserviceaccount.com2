package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Post
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Append-only admin audit trail in Firestore `admin_audit` (admin-only by rules). */
object AdminAudit {
    fun log(action: String, detail: String = "") {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection("admin_audit").add(
                mapOf("action" to action, "detail" to detail.take(500), "at" to System.currentTimeMillis(), "by" to user.uid, "byEmail" to (user.email ?: ""))
            )
        }
    }
}

data class Announcement(val id: String, val title: String, val body: String, val level: String, val active: Boolean, val expiresAt: Long)

private fun levelColors(level: String): List<Color> = when (level) {
    "warning" -> listOf(Color(0xFFFF8F00), Color(0xFFFF5252))
    "success" -> listOf(Color(0xFF00C853), Color(0xFF00B8D4))
    "promo" -> listOf(Color(0xFF7C4DFF), Color(0xFFFF4FD8))
    else -> listOf(Color(0xFF2979FF), Color(0xFF7C4DFF))
}

private fun levelIcon(level: String): ImageVector = when (level) {
    "warning" -> Icons.Default.Warning
    "success" -> Icons.Default.CheckCircle
    "promo" -> Icons.Default.Celebration
    else -> Icons.Default.Campaign
}

/** In-app announcement banner shown to every user (app_config/announcement). Dismissal is per device. */
@Composable
fun AnnouncementBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ann by remember { mutableStateOf<Announcement?>(null) }
    var dismissedId by remember { mutableStateOf(context.getSharedPreferences("convo_announce", Context.MODE_PRIVATE).getString("dismissed", "").orEmpty()) }
    DisposableEffect(Unit) {
        val reg = FirebaseFirestore.getInstance().collection("app_config").document("announcement").addSnapshotListener { snap, _ ->
            ann = if (snap == null || !snap.exists()) null else Announcement(
                snap.getString("id").orEmpty(), snap.getString("title").orEmpty(), snap.getString("body").orEmpty(),
                snap.getString("level") ?: "info", snap.getBoolean("active") ?: false, snap.getLong("expiresAt") ?: 0L
            )
        }
        onDispose { reg.remove() }
    }
    val a = ann
    val visible = a != null && a.active && a.title.isNotBlank() && (a.expiresAt == 0L || a.expiresAt > System.currentTimeMillis()) && a.id != dismissedId
    AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        if (a != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(levelColors(a.level))).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(levelIcon(a.level), null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (a.body.isNotBlank()) Text(a.body, color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                }
                IconButton(onClick = {
                    dismissedId = a.id
                    context.getSharedPreferences("convo_announce", Context.MODE_PRIVATE).edit().putString("dismissed", a.id).apply()
                }) { Icon(Icons.Default.Close, "Dismiss", tint = Color.White) }
            }
        }
    }
}

/** Admin: compose and publish an announcement to every user. */
@Composable
fun AdminAnnouncementPanel() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("info") }
    var hours by remember { mutableIntStateOf(24) }
    var current by remember { mutableStateOf<Announcement?>(null) }
    var busy by remember { mutableStateOf(false) }
    val doc = remember { FirebaseFirestore.getInstance().collection("app_config").document("announcement") }
    DisposableEffect(Unit) {
        val reg = doc.addSnapshotListener { snap, _ ->
            current = if (snap == null || !snap.exists()) null else Announcement(snap.getString("id").orEmpty(), snap.getString("title").orEmpty(), snap.getString("body").orEmpty(), snap.getString("level") ?: "info", snap.getBoolean("active") ?: false, snap.getLong("expiresAt") ?: 0L)
        }
        onDispose { reg.remove() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Show a banner at the top of everyone's app — maintenance notices, new features, events.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        current?.takeIf { it.active && (it.expiresAt == 0L || it.expiresAt > System.currentTimeMillis()) }?.let { live ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Live now: ${live.title}", fontWeight = FontWeight.Bold)
                    Text(if (live.expiresAt == 0L) "No end time" else "Ends " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(live.expiresAt)), fontSize = 12.sp)
                    TextButton(onClick = {
                        doc.update("active", false).addOnSuccessListener { AdminAudit.log("announcement_stop", live.title); Toast.makeText(context, "Announcement stopped", Toast.LENGTH_SHORT).show() }
                    }) { Text("Stop announcement") }
                }
            }
        }
        OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it.take(240) }, label = { Text("Message (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        FlowRowCompat {
            listOf("info" to "Info", "success" to "Success", "warning" to "Warning", "promo" to "Promo").forEach { (k, l) ->
                FilterChip(selected = level == k, onClick = { level = k }, label = { Text(l) }, leadingIcon = { Icon(levelIcon(k), null, Modifier.size(16.dp)) })
            }
        }
        FlowRowCompat {
            listOf(1 to "1 h", 6 to "6 h", 24 to "1 day", 72 to "3 days", 168 to "1 week", 0 to "No end").forEach { (h, l) ->
                FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(l) })
            }
        }
        if (title.isNotBlank()) {
            Text("Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(levelColors(level))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(levelIcon(level), null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); if (body.isNotBlank()) Text(body, color = Color.White.copy(.9f), fontSize = 12.sp) }
            }
        }
        Button(enabled = !busy && title.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true
            val id = "a" + System.currentTimeMillis()
            doc.set(mapOf("id" to id, "title" to title.trim(), "body" to body.trim(), "level" to level, "active" to true,
                "expiresAt" to (if (hours == 0) 0L else System.currentTimeMillis() + hours * 3_600_000L), "createdAt" to System.currentTimeMillis()))
                .addOnSuccessListener { busy = false; AdminAudit.log("announcement_publish", title); Toast.makeText(context, "Announcement published", Toast.LENGTH_SHORT).show(); title = ""; body = "" }
                .addOnFailureListener { busy = false; Toast.makeText(context, it.localizedMessage ?: "Publish failed", Toast.LENGTH_LONG).show() }
        }) { Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "Publishing…" else "Publish to everyone") }
    }
}

private data class AuditEntry(val action: String, val detail: String, val at: Long, val by: String)

/** Admin: recent audit log entries. */
@Composable
fun AdminAuditPanel() {
    var entries by remember { mutableStateOf<List<AuditEntry>?>(null) }
    var error by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val reg = FirebaseFirestore.getInstance().collection("admin_audit").orderBy("at", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, e ->
                if (e != null) { error = e.localizedMessage ?: "Cannot load audit log"; return@addSnapshotListener }
                entries = snap?.documents?.map { AuditEntry(it.getString("action").orEmpty(), it.getString("detail").orEmpty(), it.getLong("at") ?: 0L, it.getString("byEmail").orEmpty().ifBlank { it.getString("by").orEmpty() }) }.orEmpty()
            }
        onDispose { reg.remove() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Every admin change (feature flags, worker routing, AI providers, announcements) is recorded here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error.isNotBlank()) Text(error + "\nDeploy the updated firestore.rules (admin_audit).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        val list = entries
        when {
            list == null && error.isBlank() -> CircularProgressIndicator(Modifier.size(24.dp))
            list.isNullOrEmpty() -> Text("No admin actions recorded yet.", fontSize = 12.sp)
            else -> list.forEach { en ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(en.action.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                            if (en.detail.isNotBlank()) Text(en.detail, fontSize = 12.sp, maxLines = 3)
                            Text(SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(en.at)) + " · " + en.by, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** "Download my data": profile + own posts as JSON through the share sheet. */
fun exportMyData(context: Context, user: User?, posts: List<Post>) {
    if (user == null) { Toast.makeText(context, "Sign in first", Toast.LENGTH_SHORT).show(); return }
    val json = JSONObject().apply {
        put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
        put("profile", JSONObject().apply {
            put("uid", user.uid); put("name", user.name); put("username", user.username)
            put("profileImageUrl", user.profileImageUrl)
        })
        put("posts", JSONArray().apply {
            posts.filter { it.senderId == user.uid }.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("title", p.title); put("text", p.text); put("timestamp", p.timestamp)
                    put("imageUrls", JSONArray(p.imageUrls.ifEmpty { listOfNotNull(p.imageUrl.takeIf { it.isNotBlank() }) }))
                    put("videoUrl", p.videoUrl); put("reactions", p.reactions.size); put("comments", p.comments.size); put("views", p.viewsCount)
                })
            }
        })
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "My Convo data")
        putExtra(Intent.EXTRA_TEXT, json.toString(2))
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Export my data")) }
}
