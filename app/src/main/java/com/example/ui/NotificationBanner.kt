package com.example.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
 * Dynamic-island style in-app message banner: pops in as a compact pill (avatar + name), then
 * morphs into a glass card with the message, a live relative time, a countdown ring and quick
 * actions. Swipe up or tap × to dismiss, tap to open the chat. Auto-dismisses after 9 s.
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
    var expanded by remember(data.timestamp, data.senderId) { mutableStateOf(false) }
    val countdown = remember(data.timestamp, data.senderId) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(data.timestamp, data.senderId) {
        delay(260L)
        expanded = true
        countdown.animateTo(0f, androidx.compose.animation.core.tween(8_700, easing = androidx.compose.animation.core.LinearEasing))
        onDismiss()
    }
    val name = plainNotificationText(sender?.name?.ifBlank { null } ?: data.senderName).ifBlank { "New message" }
    val text = plainNotificationText(data.text).ifBlank { "Sent you a message" }
    val accent = MaterialTheme.colorScheme.primary
    val width by androidx.compose.animation.core.animateDpAsState(
        if (expanded) 520.dp else 210.dp,
        androidx.compose.animation.core.spring(dampingRatio = .72f, stiffness = 380f), label = "islandWidth"
    )
    val corner by androidx.compose.animation.core.animateDpAsState(if (expanded) 28.dp else 40.dp, label = "islandCorner")
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .widthIn(max = width)
                .then(if (expanded) Modifier.fillMaxWidth() else Modifier)
                .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = .78f, stiffness = 420f))
                .pointerInput(data.timestamp) {
                    detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -12f) onDismiss() }
                }
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(corner),
            color = Color(0xFF111118).copy(alpha = .94f),
            contentColor = Color.White,
            shadowElevation = 18.dp
        ) {
            Row(
                Modifier
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = .30f), Color.Transparent, accent.copy(alpha = .12f))))
                    .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = .55f), Color.White.copy(alpha = .08f))), RoundedCornerShape(corner))
                    .padding(horizontal = if (expanded) 14.dp else 8.dp, vertical = if (expanded) 12.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with an animated countdown ring.
                Box(Modifier.size(if (expanded) 48.dp else 30.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        drawArc(Color.White.copy(alpha = .12f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        drawArc(accent, -90f, 360f * countdown.value, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(accent.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(), fontWeight = FontWeight.Black, fontSize = if (expanded) 17.sp else 12.sp, color = Color.White)
                        val image = sender?.profileImageUrl.orEmpty()
                        if (image.isNotBlank()) AsyncImage(image, name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                    }
                }
                Spacer(Modifier.width(if (expanded) 12.dp else 8.dp))
                if (!expanded) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34C759)))
                    Spacer(Modifier.width(6.dp))
                } else {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            Text(relativeTimeLabel(data.timestamp, now), fontSize = 11.sp, color = Color.White.copy(alpha = .6f), maxLines = 1)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = .86f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(onClick = onOpen, shape = RoundedCornerShape(50), color = accent, contentColor = Color.White) {
                                Text("Reply", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(onClick = onDismiss, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .12f), contentColor = Color.White) {
                                Text("Later", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = .8f))
                    }
                }
            }
        }
    }
}
