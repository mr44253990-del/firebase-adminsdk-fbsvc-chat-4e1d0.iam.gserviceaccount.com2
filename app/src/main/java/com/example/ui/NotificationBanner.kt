package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.User
import com.example.service.plainNotificationText
import kotlinx.coroutines.delay

/** "now", "1 min ago", "5 min ago", "2 hr ago", "3 days ago". */
fun relativeTimeLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "now"
    val minutes = ((now - timestamp).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min ago"
        minutes < 1440 -> "${minutes / 60} hr ago"
        minutes < 2880 -> "yesterday"
        else -> "${minutes / 1440} days ago"
    }
}

/** Ticks so relative labels ("5 min ago") keep updating while visible. */
@Composable
fun rememberTickingNow(periodMillis: Long = 20_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            delay(periodMillis)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Text-only in-app message banner with avatar, live relative time, swipe-up / auto dismiss.
 */
@Composable
fun InAppMessageBanner(
    data: InAppNotificationData,
    sender: User?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = rememberTickingNow(15_000L)
    LaunchedEffect(data.timestamp, data.senderId) {
        delay(9_000L)
        onDismiss()
    }
    val name = plainNotificationText(sender?.name?.ifBlank { null } ?: data.senderName).ifBlank { "New message" }
    val text = plainNotificationText(data.text).ifBlank { "Sent you a message" }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(data.timestamp) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -12f) onDismiss() }
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 14.dp
    ) {
        Row(
            Modifier
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .10f), Color.Transparent)))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(26.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                val image = sender?.profileImageUrl.orEmpty()
                if (image.isNotBlank()) AsyncImage(image, name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text("•  ${relativeTimeLabel(data.timestamp, now)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Tap to reply", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
            }
        }
    }
}
