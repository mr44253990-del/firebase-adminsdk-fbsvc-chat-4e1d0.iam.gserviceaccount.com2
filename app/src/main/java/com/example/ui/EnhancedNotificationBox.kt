package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ActivityNotification
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Notification Box — glassmorphism, grouped by time, swipe actions
 */
@Composable
fun EnhancedNotificationBox(
    notifications: List<ActivityNotification>,
    onMarkRead: (String) -> Unit,
    onOpen: (ActivityNotification) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        // Header with gradient
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF7B6BFF), Color(0xFF5AC8FA))))
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Notifications", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("${notifications.count { !it.isRead }} new • ${notifications.size} total", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = onClearAll, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark all read", fontSize = 12.sp)
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("All caught up!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("You have no new notifications", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val grouped = notifications.groupBy { timeBucket(it.timestamp) }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (bucket, list) ->
                    item { Text(bucket, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                    items(list, key = { it.id }) { notif ->
                        NotificationCard(notif, onMarkRead, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notif: ActivityNotification, onMarkRead: (String) -> Unit, onOpen: (ActivityNotification) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (!notif.isRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!notif.isRead) 3.dp else 0.dp),
        onClick = { onOpen(notif) }
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(
                when {
                    notif.type.contains("call", true) -> Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)))
                    notif.type.contains("message", true) -> Brush.linearGradient(listOf(Color(0xFF7B6BFF), Color(0xFF5AC8FA)))
                    notif.type.contains("friend", true) -> Brush.linearGradient(listOf(Color(0xFFFFA000), Color(0xFFFF6D00)))
                    else -> Brush.linearGradient(listOf(Color(0xFF78909C), Color(0xFF455A64)))
                }
            ), contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        notif.type.contains("call", true) -> Icons.Default.Call
                        notif.type.contains("message", true) -> Icons.Default.ChatBubble
                        else -> Icons.Default.Notifications
                    }, null, tint = Color.White, modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(notif.actorName.ifBlank { notif.type }, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Text(notif.text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text(formatTime(notif.timestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            if (!notif.isRead) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

private fun timeBucket(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "Today"
        diff < 86400_000 -> "Today"
        diff < 172800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(ts))
    }
}
private fun formatTime(ts: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

@Composable
fun SmartMutedChatMenu(
    chatName: String,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onClearChat: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(chatName, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            MenuAction(Icons.Default.NotificationsOff, if (isMuted) "Unmute" else "Mute", if (isMuted) "Turn on notifications" else "Silence notifications") { onMuteToggle(); onDismiss() }
            MenuAction(Icons.Default.PushPin, "Pin chat", "Keep on top") { onPin(); onDismiss() }
            MenuAction(Icons.Default.MarkChatRead, "Mark as read", null) { onMarkRead(); onDismiss() }
            MenuAction(Icons.Default.CleaningServices, "Clear chat", "Delete messages for you") { onClearChat(); onDismiss() }
            Divider(Modifier.padding(vertical = 8.dp))
            MenuAction(Icons.Default.Block, "Block", "They won't be able to message you", tint = MaterialTheme.colorScheme.error) { onBlock(); onDismiss() }
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable
private fun MenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = tint); if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
