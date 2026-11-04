package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

/**
 * Admin-only AI auto-reply. When ON, every message a user sends to the admin is answered by
 * the Worker (server side) with the admin's AI provider, even while the admin is offline.
 * Stored in Firestore app_config/admin_ai_reply (admin-write, signed-in read).
 */
@Composable
fun AdminAiAutoReplyPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val me by viewModel.currentUserState.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var onlyWhenOffline by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("— AI assistant (admin will reply personally soon)") }
    var cooldown by remember { mutableFloatStateOf(20f) }
    var saving by remember { mutableStateOf(false) }
    val doc = remember { FirebaseFirestore.getInstance().collection("app_config").document("admin_ai_reply") }

    LaunchedEffect(Unit) {
        doc.get().addOnSuccessListener { d ->
            enabled = d.getBoolean("enabled") ?: false
            onlyWhenOffline = d.getBoolean("onlyWhenOffline") ?: true
            prompt = d.getString("prompt").orEmpty()
            signature = d.getString("signature") ?: signature
            cooldown = (d.getLong("cooldownSec") ?: 20L).toFloat().coerceIn(5f, 600f)
            loaded = true
        }.addOnFailureListener { loaded = true }
    }

    fun save(newEnabled: Boolean = enabled) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        saving = true
        val data = mapOf(
            "enabled" to newEnabled,
            "adminUid" to uid,
            "adminName" to (me?.name ?: "Admin"),
            "adminUsername" to (me?.username ?: ""),
            "adminPhoto" to (me?.profileImageUrl ?: ""),
            "onlyWhenOffline" to onlyWhenOffline,
            "prompt" to prompt.trim().take(1500),
            "signature" to signature.trim().take(80),
            "cooldownSec" to cooldown.toLong(),
            "updatedAt" to System.currentTimeMillis()
        )
        doc.set(data, SetOptions.merge())
            .addOnSuccessListener {
                saving = false
                AdminAudit.log("admin_ai_reply", if (newEnabled) "AI auto-reply ON" else "AI auto-reply OFF")
                Toast.makeText(context, if (newEnabled) "AI auto-reply is ON" else "AI auto-reply saved (off)", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { saving = false; Toast.makeText(context, "Save failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (enabled) Color(0xFF7C4DFF).copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (enabled) "AI is answering your messages" else "AI auto-reply is off", fontWeight = FontWeight.Bold)
                    Text("Runs on the Worker with your AI provider — works while you are offline.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, { enabled = it; save(it) }, enabled = loaded && !saving)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Only when I'm offline", fontWeight = FontWeight.SemiBold)
                Text("If you are Active, you answer yourself.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(onlyWhenOffline, { onlyWhenOffline = it })
        }
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it.take(1500) },
            label = { Text("Instructions for the AI") },
            placeholder = { Text("e.g. I'm the Convo admin. Help with app questions, payments take 24h, be friendly.") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = signature, onValueChange = { signature = it.take(80) },
            label = { Text("Signature added to each AI reply") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
        )
        Text("Cooldown per user: ${cooldown.toInt()} s", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Slider(cooldown, { cooldown = it }, valueRange = 5f..600f)
        Button(onClick = { save() }, enabled = loaded && !saving, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text(if (saving) "Saving…" else "Save settings")
        }
        Text(
            "Needs: the latest Worker (Server health → Update Worker), an AI provider (AI providers & keys) or the MISTRAL_API_KEY secret. " +
                "Optional Worker secret FIREBASE_DATABASE_URL; otherwise the app sends its database URL.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
