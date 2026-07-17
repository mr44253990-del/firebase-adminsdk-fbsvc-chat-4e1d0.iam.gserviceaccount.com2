package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LocalPalette
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextSecondary

/** Avatar with photo or gradient initial, optional online dot + animated story ring. */
@Composable
fun UserAvatar(
    name: String,
    photoUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    online: Boolean? = null,
    hasStory: Boolean = false,
    storySeen: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    val ringBrush = when {
        hasStory && !storySeen -> Brush.linearGradient(palette.storyRing)
        hasStory -> Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
        else -> null
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (ringBrush != null) {
            // rotating story ring
            val infinite = rememberInfiniteTransition(label = "ring")
            val rot by infinite.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(4000, easing = LinearEasing)),
                label = "r"
            )
            Box(
                Modifier
                    .size(size + 8.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, ringBrush, CircleShape)
            )
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.bubbleMine))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (online == true) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 3.6f)
                    .clip(CircleShape)
                    .background(Color(0xFF0B0B18))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}

/** Glassy search field used on Home / Chats. */
@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)),
        placeholder = {
            Text(placeholder, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = GlassWhite,
            unfocusedContainerColor = GlassWhite,
            disabledContainerColor = GlassWhite,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = LocalPalette.current.primary
        )
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

/** "5m ago" style formatting used across feed / chats / notifications. */
fun timeAgo(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.DAY_IN_MILLIS).toString()
    }
}

fun formatClock(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

/**
 * Auto-playing, muted, looping inline video (Instagram style).
 * Caller passes `active` based on feed visibility; player releases on dispose.
 */
@Composable
fun AutoPlayVideo(
    url: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        try {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
            }
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(active) {
        try {
            if (active) player?.play() else player?.pause()
        } catch (_: Exception) {
        }
    }

    DisposableEffect(url) {
        onDispose {
            try {
                player?.release()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier.clickable { onTap?.invoke() }) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}
