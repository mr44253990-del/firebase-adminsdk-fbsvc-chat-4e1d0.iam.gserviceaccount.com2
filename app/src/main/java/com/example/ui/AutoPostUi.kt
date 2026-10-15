package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin settings for server-side automatic AI posts. Settings live in Firestore
 * `app_config/auto_post`; the Supabase Edge Function `auto-ai-post` (run by Supabase Cron every
 * 10 minutes) reads them and publishes as the admin, even when every phone is closed.
 */
@Composable
fun AdminAutoPostPanel() {
    val context = LocalContext.current
    val docRef = remember { FirebaseFirestore.getInstance().collection("app_config").document("auto_post") }
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var perDay by remember { mutableStateOf("3") }
    var postType by remember { mutableStateOf("mixed") }
    var language by remember { mutableStateOf("Bengali") }
    var prompt by remember { mutableStateOf("") }
    var useEmoji by remember { mutableStateOf(true) }
    var styled by remember { mutableStateOf(true) }
    var startHour by remember { mutableIntStateOf(8) }
    var endHour by remember { mutableIntStateOf(23) }
    var status by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val reg = docRef.addSnapshotListener { snap, _ ->
            val data = snap?.data.orEmpty()
            status = data
            if (!loaded) {
                enabled = data["enabled"] as? Boolean ?: false
                perDay = ((data["postsPerDay"] as? Number)?.toInt() ?: 3).toString()
                postType = data["postType"] as? String ?: "mixed"
                language = data["language"] as? String ?: "Bengali"
                prompt = data["prompt"] as? String ?: ""
                useEmoji = data["useEmoji"] as? Boolean ?: true
                styled = data["styledBackground"] as? Boolean ?: true
                startHour = (data["startHour"] as? Number)?.toInt() ?: 8
                endHour = (data["endHour"] as? Number)?.toInt() ?: 23
                loaded = true
            }
        }
        onDispose { reg.remove() }
    }

    fun save(extra: Map<String, Any> = emptyMap(), message: String = "Auto post settings saved") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        saving = true
        val data = mapOf(
            "enabled" to enabled,
            "postsPerDay" to (perDay.toIntOrNull() ?: 3).coerceIn(1, 48),
            "postType" to postType,
            "language" to language,
            "prompt" to prompt.trim().take(1500),
            "useEmoji" to useEmoji,
            "styledBackground" to styled,
            "startHour" to startHour,
            "endHour" to endHour,
            "tzOffsetMinutes" to (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000),
            "adminUid" to uid,
            "updatedAt" to System.currentTimeMillis(),
            // Saving re-plans the schedule: the next post goes out on the next cron tick.
            "nextRunAt" to 0L
        ) + extra
        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener { saving = false; Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { saving = false; Toast.makeText(context, it.message ?: "Save failed", Toast.LENGTH_LONG).show() }
    }

    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdminAutoSwitch("Auto AI posts", "Post automatically from your admin account", enabled) { enabled = it }
        Text("Posts per day", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1", "2", "3", "5", "8", "12", "24").forEach { value ->
                FilterChip(selected = perDay == value, onClick = { perDay = value }, label = { Text(value) })
            }
        }
        Text("Post type", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "mixed" to "🎲 Mixed", "status" to "👋 Status", "tip" to "💡 Tips", "quote" to "✨ Quotes",
                "question" to "💬 Questions", "fun_fact" to "🧠 Fun facts", "motivation" to "🔥 Motivation", "poll" to "🗳️ Polls"
            ).forEach { (id, label) -> FilterChip(selected = postType == id, onClick = { postType = id }, label = { Text(label) }) }
        }
        Text("Language", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Bengali", "English", "Banglish").forEach { lang ->
                FilterChip(selected = language == lang, onClick = { language = lang }, label = { Text(lang) })
            }
        }
        OutlinedTextField(
            prompt, { prompt = it.take(1500) },
            label = { Text("Prompt / guidance for the AI") },
            placeholder = { Text("e.g. Friendly morning greetings, tech tips for students, ask fun questions about food…") },
            minLines = 3, modifier = Modifier.fillMaxWidth()
        )
        AdminAutoSwitch("Use emoji", "Add 2–4 fitting emoji to each post", useEmoji) { useEmoji = it }
        AdminAutoSwitch("Colorful background", "Short text posts use a random gradient canvas", styled) { styled = it }
        Text("Posting hours: ${hourLabel(startHour)} – ${hourLabel(endHour)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Start", fontSize = 12.sp)
        Slider(value = startHour.toFloat(), onValueChange = { startHour = it.toInt() }, valueRange = 0f..23f, steps = 22)
        Text("End", fontSize = 12.sp)
        Slider(value = endHour.toFloat(), onValueChange = { endHour = it.toInt() }, valueRange = 0f..23f, steps = 22)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save() }, enabled = !saving, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save")
            }
            OutlinedButton(
                onClick = { save(mapOf("runNowRequestedAt" to System.currentTimeMillis()), "Queued — the server posts within ~10 minutes") },
                enabled = !saving, modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(6.dp)); Text("Post one now") }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Server status", fontWeight = FontWeight.Bold)
                }
                val lastRun = (status["lastRunAt"] as? Number)?.toLong() ?: 0L
                val nextRun = (status["nextRunAt"] as? Number)?.toLong() ?: 0L
                val lastError = status["lastError"] as? String ?: ""
                Text("Last post: " + if (lastRun > 0) fmt.format(Date(lastRun)) else "never", fontSize = 12.sp)
                if (enabled && nextRun > 0) Text("Next post: about ${fmt.format(Date(nextRun))}", fontSize = 12.sp)
                Text("Total auto posts: ${(status["totalPosts"] as? Number)?.toLong() ?: 0}", fontSize = 12.sp)
                (status["lastPostPreview"] as? String)?.takeIf { it.isNotBlank() }?.let { Text("“$it”", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (lastError.isNotBlank()) Text("Last error: $lastError", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                if (lastRun == 0L) Text(
                    "Setup once: deploy the Supabase function with ./deploy-auto-ai-post.sh <project-ref>, then run supabase/sql/auto_post_cron.sql. Until then nothing is posted.",
                    fontSize = 11.sp, color = Color(0xFFB7791F)
                )
            }
        }
    }
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

@Composable
private fun AdminAutoSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
