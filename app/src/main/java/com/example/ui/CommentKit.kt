package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Reactions offered on comments (content emoji, not decoration). */
val COMMENT_REACTIONS = listOf("👍" to "Like", "❤️" to "Love", "😂" to "Haha", "😮" to "Wow", "😢" to "Sad", "😡" to "Angry")

/** Comment text, or a big animated Convo sticker when the comment is a sticker token. */
@Composable
fun CommentBody(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    if (ConvoStickers.isSticker(text)) ConvoStickerView(text.trim(), 72.dp, Modifier.padding(top = 2.dp))
    else Text(text, fontSize = 13.sp, color = color)
}

/**
 * Compact reaction control under a comment: tap = like/unlike, long tap or the face icon =
 * floating reaction bar. Shows the top reactions and the total.
 */
@Composable
fun CommentReactionControl(reactions: Map<String, String>, myUid: String, onReact: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val mine = reactions[myUid]
    val top = remember(reactions) { reactions.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key } }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (mine != null) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent,
                modifier = Modifier.clip(CircleShape).clickable { onReact(mine ?: COMMENT_REACTIONS.first().first) }
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mine != null) Text(mine, fontSize = 13.sp)
                    else Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        COMMENT_REACTIONS.firstOrNull { it.first == mine }?.second ?: "Like",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (mine != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { open = true }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Outlined.EmojiEmotions, "React", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (reactions.isNotEmpty()) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        top.forEach { Text(it, fontSize = 11.sp) }
                        Spacer(Modifier.width(3.dp))
                        Text(reactions.size.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (open) Popup(alignment = Alignment.BottomStart, offset = androidx.compose.ui.unit.IntOffset(0, -96), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
            Surface(shape = RoundedCornerShape(30.dp), tonalElevation = 8.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    COMMENT_REACTIONS.forEachIndexed { i, (emoji, label) ->
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { kotlinx.coroutines.delay(30L * i); shown = true }
                        val scale by animateFloatAsState(if (shown) 1f else .2f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "react_pop")
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp)).clickable { open = false; onReact(emoji) }
                                .background(if (mine == emoji) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(emoji, fontSize = 26.sp, modifier = Modifier.scale(scale))
                            Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Horizontal tray of Convo animated stickers; picking one posts it as the comment. */
@Composable
fun CommentStickerTray(visible: Boolean, onPick: (String) -> Unit) {
    AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Stickers", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ConvoStickers.all, key = { it.token }) { sticker ->
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(sticker.token) }
                                .background(Brush.verticalGradient(sticker.colors.map { it.copy(alpha = .16f) }))
                                .border(1.dp, sticker.colors.first().copy(alpha = .35f), RoundedCornerShape(16.dp))
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ConvoStickerView(sticker.token, 46.dp)
                            Text(sticker.label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Sticker toggle used inside comment composers. */
@Composable
fun CommentStickerToggle(open: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (open) Icons.Filled.EmojiEmotions else Icons.Outlined.EmojiEmotions,
            if (open) "Hide stickers" else "Stickers",
            tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
