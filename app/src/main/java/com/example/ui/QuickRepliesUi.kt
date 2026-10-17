package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Saved quick replies. Stored on the server (Firestore user_private/{uid}/settings/quick_replies,
 * owner-only) with an on-device copy, so they follow you to a new phone and work offline.
 * In any chat type "/" to see them; one tap sends.
 */
object QuickReplyStore {
    private const val PREFS = "convo_quick_replies"
    val defaults = listOf("On my way! 🚗", "I'll call you back soon 📞", "Thanks a lot 🙏", "Okay 👍", "আসছি 🚶", "পরে কথা বলি 🙂", "ধন্যবাদ ❤️")
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()
    private var loadedFor: String? = null

    private fun doc(uid: String) = FirebaseFirestore.getInstance()
        .collection("user_private").document(uid).collection("settings").document("quick_replies")

    fun load(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (loadedFor == uid) return
        loadedFor = uid
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val local = prefs.getString("items_$uid", null)?.let { raw ->
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
        }
        _items.value = local ?: defaults
        doc(uid).get().addOnSuccessListener { d ->
            @Suppress("UNCHECKED_CAST")
            val remote = (d.get("items") as? List<Any?>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            if (remote != null) {
                _items.value = remote
                prefs.edit().putString("items_$uid", JSONArray(remote).toString()).apply()
            }
        }
    }

    fun save(context: Context, list: List<String>, onDone: (Boolean) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone(false)
        val clean = list.map { it.trim().take(300) }.filter { it.isNotBlank() }.distinct().take(30)
        _items.value = clean
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("items_$uid", JSONArray(clean).toString()).apply()
        doc(uid).set(mapOf("items" to clean, "updatedAt" to System.currentTimeMillis()))
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }
}

/** Shown above the composer while the text starts with "/". */
@Composable
fun QuickReplyBar(query: String, accent: Color, onSend: (String) -> Unit, onManage: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { QuickReplyStore.load(context) }
    val items by QuickReplyStore.items.collectAsState()
    val q = query.removePrefix("/").trim().lowercase()
    val shown = if (q.isBlank()) items else items.filter { it.lowercase().contains(q) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Quick replies · tap to send"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.weight(1f))
                Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onManage).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (shown.isEmpty()) {
                Text("No quick reply matches. Tap Manage to add one.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shown.forEach { reply ->
                        Surface(
                            onClick = { onSend(reply) },
                            shape = RoundedCornerShape(50),
                            color = accent.copy(alpha = .12f)
                        ) {
                            Text(reply, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickReplyManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { QuickReplyStore.load(context) }
    val saved by QuickReplyStore.items.collectAsState()
    val list = remember(saved) { mutableStateListOf<String>().apply { addAll(saved) } }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bolt, null) },
        title = { Text("Quick replies") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved to your account. In a chat type / to use them.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it.take(300) }, Modifier.weight(1f), placeholder = { Text("New quick reply") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    IconButton(onClick = { if (draft.isNotBlank()) { list.add(0, draft.trim()); draft = "" } }) { Icon(Icons.Default.AddCircle, "Add") }
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    itemsIndexed(list) { index, item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { if (index > 0) { val v = list.removeAt(index); list.add(index - 1, v) } }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Move up", Modifier.size(18.dp)) }
                            IconButton(onClick = { list.removeAt(index) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.DeleteOutline, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = {
                QuickReplyStore.save(context, list.toList()) { ok -> status = if (ok) "Saved to your account" else "Saved on this phone (server rules not deployed yet)" ; if (ok) onDismiss() }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
