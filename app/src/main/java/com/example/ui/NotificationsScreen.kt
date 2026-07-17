package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.*

/** Notification hub — who messaged, liked, commented, reacted to your story. */
@Composable
fun NotificationsScreen(
    feedViewModel: FeedViewModel,
    onBack: () -> Unit
) {
    val notifications by feedViewModel.notifications.collectAsState()

    LaunchedEffect(Unit) {
        feedViewModel.start()
        feedViewModel.markAllNotificationsRead()
    }

    GlassBackground(bubbleCount = 8) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                GradientText("Notifications", MaterialTheme.typography.headlineMedium)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
                if (notifications.isEmpty()) {
                    item {
                        EmptyState("🔔", "All caught up!", "Likes, comments and messages will show up here")
                    }
                }
                items(notifications, key = { it.id }) { notif ->
                    val (icon, tint) = when (notif.type) {
                        "like" -> Icons.Default.Favorite to Color(0xFFFF3B5C)
                        "comment" -> Icons.Default.ModeComment to Color(0xFF4CC9F0)
                        "story_react" -> Icons.Default.AutoAwesome to Color(0xFFFFB75E)
                        else -> Icons.Default.ChatBubble to LocalPalette.current.primary
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (notif.read) GlassWhite else LocalPalette.current.primary.copy(alpha = 0.14f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            UserAvatar(name = notif.fromName, photoUrl = notif.fromPhoto, size = 48.dp)
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(50))
                                    .background(tint)
                                    .padding(4.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(notif.fromName)
                                    append(" ")
                                    append(notif.text)
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (notif.read) FontWeight.Normal else FontWeight.SemiBold
                            )
                            Text(timeAgo(notif.timestamp), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        if (!notif.read) UnreadDot(size = 9.dp)
                    }
                }
            }
        }
    }
}
