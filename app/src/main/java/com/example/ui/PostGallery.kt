package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.Post
import kotlinx.coroutines.delay
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

/**
 * While a multi-photo post is the one in view, its photos turn by themselves (like a photo
 * slideshow in TikTok), so it is obvious there is more than one. Touching the photos stops the
 * slideshow for that post; the user is then in control.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoPeekPager(pager: PagerState, count: Int, active: Boolean) {
    var touched by remember { mutableStateOf(false) }
    val dragged by pager.interactionSource.collectIsDraggedAsStateCompat()
    LaunchedEffect(dragged) { if (dragged) touched = true }
    LaunchedEffect(active, touched, count) {
        if (!active || touched || count < 2) return@LaunchedEffect
        delay(1400)
        while (true) {
            val next = (pager.currentPage + 1) % count
            runCatching { pager.animateScrollToPage(next, animationSpec = tween(650, easing = FastOutSlowInEasing)) }
            delay(2600)
        }
    }
}

@Composable
private fun androidx.compose.foundation.interaction.InteractionSource.collectIsDraggedAsStateCompat(): State<Boolean> {
    val dragged = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.DragInteraction.Start -> dragged.value = true
                is androidx.compose.foundation.interaction.DragInteraction.Stop, is androidx.compose.foundation.interaction.DragInteraction.Cancel -> dragged.value = false
            }
        }
    }
    return dragged
}

/** Page dots with the current one stretched, shown under / over a photo carousel. */
@Composable
fun GalleryDots(count: Int, current: Int, modifier: Modifier = Modifier, light: Boolean = true) {
    if (count < 2) return
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = if (light) .42f else 0f)).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count.coerceAtMost(10)) { i ->
            val on = i == current.coerceAtMost(9)
            Box(Modifier.size(width = if (on) 16.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = .45f)))
        }
    }
}

/**
 * Full-screen photo viewer for a post: swipe sideways between the photos, pinch to zoom,
 * double-tap to zoom in / out, with the author, title and caption over the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostGalleryViewer(post: Post, images: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)), pageCount = { images.size })
    var chrome by remember { mutableStateOf(true) }
    var captionOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offset by remember(page) { mutableStateOf(Offset.Zero) }
                // Leaving a page resets its zoom.
                LaunchedEffect(pager.currentPage) { if (pager.currentPage != page) { scale = 1f; offset = Offset.Zero } }
                AsyncImage(
                    model = images[page], contentDescription = "Photo ${page + 1}", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { chrome = !chrome },
                                onDoubleTap = { if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }
                            )
                        }
                        .then(
                            // Only capture drags while zoomed in, so sideways swipes still change the photo.
                            if (scale > 1.02f) Modifier.pointerInput(page) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (scale <= 1.02f) Offset.Zero else offset + pan
                                }
                            } else Modifier.pointerInput(page) {
                                detectTransformGestures { _, _, zoom, _ -> if (zoom > 1.03f) scale = (scale * zoom).coerceIn(1f, 6f) }
                            }
                        )
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                )
            }
            if (chrome) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    AsyncImage(post.senderProfilePic.ifBlank { null }, null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f)))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(post.senderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(SmartTime.ago(post.timestamp), color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                    }
                    if (images.size > 1) Text(
                        "${pager.currentPage + 1} / ${images.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .16f)).padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val downloading = remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            val url = images[pager.currentPage]
                            if (!url.startsWith("http")) { Toast.makeText(context, "Photo is already saved", Toast.LENGTH_SHORT).show(); return@IconButton }
                            downloading.value = true
                            val request = DownloadManager.Request(Uri.parse(url))
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Convo_${System.currentTimeMillis()}.jpg")
                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                            downloading.value = false
                            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape).size(38.dp)
                    ) { if (downloading.value) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Download, "Download", modifier = Modifier.size(19.dp), tint = Color.White) }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                        .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GalleryDots(images.size, pager.currentPage, light = false)
                    if (post.title.isNotBlank() || post.text.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().clickable { captionOpen = !captionOpen }) {
                            if (post.title.isNotBlank()) Text(post.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = if (captionOpen) 4 else 1, overflow = TextOverflow.Ellipsis)
                            if (post.text.isNotBlank()) Text(post.text, color = Color.White.copy(alpha = .9f), fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = if (captionOpen) 14 else 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${post.reactions.size} reactions  ·  ${post.comments.size} comments  ·  swipe for more, pinch to zoom", color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
            }
        }
    }
}
