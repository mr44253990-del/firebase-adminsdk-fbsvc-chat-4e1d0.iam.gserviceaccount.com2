package com.example.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoCall
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
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen v4.4 pages presented above the existing navigation graph. */
@Composable
fun ProToolsOverlay(users: List<User>, onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val page by ProTools.page.collectAsState()
    BackHandler(enabled = page != null) { ProTools.close() }
    val reminder by ProTools.reminderTarget.collectAsState()
    val savedVersion by SavedMessages.version.collectAsState()
    val calls by CallHistory.entries.collectAsState()
    var query by remember(page) { mutableStateOf("") }
    var matches by remember(page) { mutableStateOf(emptyList<com.example.data.CachedMessage>()) }
    var searching by remember(page) { mutableStateOf(false) }
    var managedDownloads by remember(page) { mutableStateOf(emptyList<ReceivedFiles.ManagedDownload>()) }

    LaunchedEffect(page) {
        if (page == "calls") CallHistory.ensureStarted(ctx)
        if (page == "storage") managedDownloads = withContext(Dispatchers.IO) { ReceivedFiles.managedDownloads(ctx) }
    }
    LaunchedEffect(page, query) {
        if (page != "search" || query.trim().length < 2) { matches = emptyList(); return@LaunchedEffect }
        searching = true
        val result = withContext(Dispatchers.IO) {
            runCatching { AppDatabase.getDatabase(ctx).cacheDao().searchMessages(query.trim()) }.getOrDefault(emptyList())
        }
        matches = result
        searching = false
    }

    if (page != null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ProTools.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(when (page) { "saved" -> "Saved messages"; "calls" -> "Calls"; "search" -> "Search all chats"; "storage" -> "Storage manager"; else -> "Convo tools" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Private tools stored on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (page == "saved" && savedVersion >= 0) IconButton(onClick = { SavedMessages.clear(ctx) }) { Icon(Icons.Default.DeleteOutline, "Clear saved") }
                }
                HorizontalDivider()
                when (page) {
                    "saved" -> {
                        val list = remember(savedVersion) { SavedMessages.all(ctx) }
                        if (list.isEmpty()) ProEmptyState("No saved messages yet", "Open a message menu and choose Save.")
                        else LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(list, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth().clickable { if (item.peerUid.isNotBlank()) { ProTools.close(); onOpenChat(item.peerUid) } }, shape = RoundedCornerShape(18.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFA000))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.peerName.ifBlank { "Conversation" }, fontWeight = FontWeight.Bold)
                                            Text(item.text.ifBlank { item.fileName.ifBlank { "Saved message" } }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                            Text(DateUtils.getRelativeTimeSpanString(item.sentAt).toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { SavedMessages.remove(ctx, item.id) }) { Icon(Icons.Default.DeleteOutline, "Remove") }
                                    }
                                }
                            }
                        }
                    }
                    "calls" -> {
                        if (calls.isEmpty()) ProEmptyState("No calls yet", "Your recent calls will appear here.")
                        else LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(calls, key = { it.callId }) { call ->
                                Card(Modifier.fillMaxWidth().clickable { if (call.peerUid.isNotBlank()) { ProTools.close(); onOpenChat(call.peerUid) } }, shape = RoundedCornerShape(18.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                            Icon(if (call.video) Icons.Default.VideoCall else Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(call.peerName.ifBlank { "Unknown contact" }, fontWeight = FontWeight.SemiBold)
                                            Text("${if (call.incoming) "Incoming" else "Outgoing"} · ${call.outcome}" + if (call.durationSec > 0) " · ${call.durationSec / 60}:${(call.durationSec % 60).toString().padStart(2, '0')}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(DateUtils.getRelativeTimeSpanString(call.at).toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { CallHistory.remove(ctx, call.callId) }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                    "storage" -> {
                        val total = managedDownloads.sumOf { it.bytes }
                        Column(Modifier.fillMaxSize()) {
                            Card(Modifier.fillMaxWidth().padding(14.dp), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Downloads / ${ReceivedFiles.APP_FOLDER}", fontWeight = FontWeight.Bold)
                                    Text("${managedDownloads.size} files · ${formatProBytes(total)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Only items created by Convo Chat are listed here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (managedDownloads.isEmpty()) ProEmptyState("No downloaded files", "Received attachments saved by the app will appear here.")
                            else LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(managedDownloads, key = { it.id }) { item ->
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                                Text("${formatProBytes(item.bytes)} · ${item.description.removePrefix(ReceivedFiles.APP_FOLDER).trim()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(onClick = {
                                                ReceivedFiles.removeManagedDownload(ctx, item.id)
                                                managedDownloads = managedDownloads.filterNot { it.id == item.id }
                                            }) { Text("Remove") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "search" -> {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(14.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search cached messages and files") })
                        when {
                            query.trim().length < 2 -> ProEmptyState("Search your chats", "Type at least two characters. Search uses messages cached on this device.")
                            searching -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            matches.isEmpty() -> ProEmptyState("No matching messages", "Try another word or file name.")
                            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(matches, key = { it.messageId }) { msg ->
                                    val me = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                                    val peerUid = when {
                                        msg.chatId.startsWith("${me}_") -> msg.chatId.removePrefix("${me}_")
                                        msg.chatId.endsWith("_${me}") -> msg.chatId.removeSuffix("_${me}")
                                        else -> ""
                                    }
                                    val contact = users.firstOrNull { it.uid == peerUid }
                                    Card(Modifier.fillMaxWidth().clickable { if (peerUid.isNotBlank()) { ProTools.close(); onOpenChat(peerUid) } }, shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(contact?.name ?: msg.senderName.ifBlank { "Conversation" }, fontWeight = FontWeight.Bold)
                                            Text(msg.text.ifBlank { msg.fileName.orEmpty().ifBlank { "Attachment" } }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            Text(DateUtils.getRelativeTimeSpanString(msg.timestamp).toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> ProToolsHubPage(onSelect = { ProTools.open(it) })
                }
            }
        }
    }

    reminder?.let { target ->
        var selected by remember(target) { mutableIntStateOf(60) }
        AlertDialog(
            onDismissRequest = { ProTools.askReminder(null) },
            icon = { Icon(Icons.Default.Tune, null) },
            title = { Text("Remind me") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.text.ifBlank { "Message" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    listOf(15, 60, 180, 1440).forEach { minutes ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selected = minutes }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == minutes, { selected = minutes })
                            Text(when (minutes) { 15 -> "In 15 minutes"; 60 -> "In 1 hour"; 180 -> "In 3 hours"; else -> "Tomorrow" })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val at = System.currentTimeMillis() + selected * 60_000L
                    val ok = MessageReminders.schedule(ctx, target, at)
                    Toast.makeText(ctx, if (ok) "Reminder set" else "Could not schedule reminder", Toast.LENGTH_SHORT).show()
                    ProTools.askReminder(null)
                }) { Text("Set reminder") }
            },
            dismissButton = { TextButton(onClick = { ProTools.askReminder(null) }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProToolsHubPage(onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Useful shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ProHubCard("Saved messages", "Keep important messages in one private list", Icons.Default.Star) { onSelect("saved") }
        ProHubCard("Call history", "Recent incoming, outgoing and missed calls", Icons.Default.Call) { onSelect("calls") }
        ProHubCard("Search all chats", "Find cached message text and file names", Icons.Default.Search) { onSelect("search") }
        ProHubCard("Storage manager", "Review and remove files downloaded by Convo Chat", Icons.Default.Storage) { onSelect("storage") }
    }
}

@Composable
private fun ProHubCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun formatProBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
private fun ProEmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
