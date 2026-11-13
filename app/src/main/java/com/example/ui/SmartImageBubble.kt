package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * Natural-size chat image bubble: photos render at their real aspect ratio — small images stay
 * small, big images fill the bubble width (height capped) — instead of one fixed square band.
 * Tap opens the shared full-screen viewer.
 */
@Composable
fun SmartChatImage(
    url: String?,
    maxWidth: Dp,
    corner: Dp = 16.dp,
    onOpen: () -> Unit
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(url).build()
    )
    var measured by remember(url) { mutableStateOf<Size?>(null) }
    LaunchedEffect(painter.intrinsicSize) {
        val intrinsic = painter.intrinsicSize
        if (!intrinsic.isUnspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
            measured = intrinsic
        }
    }

    val target = measured?.let { intrinsic ->
        // Show images at ~their natural on-screen size (px treated as dp), clamped so tiny
        // thumbnails stay tappable and huge photos fit the chat.
        val naturalW = intrinsic.width / density.density
        val naturalH = intrinsic.height / density.density
        val maxW = maxWidth.value
        val maxH = 320f
        var w = naturalW.coerceIn(110f, maxW)
        var h = naturalH * (w / naturalW)
        if (h > maxH) { h = maxH; w = (naturalW * (maxH / naturalH)).coerceIn(110f, maxW) }
        w.toInt() to h.toInt()
    }

    if (target == null) {
        // Loading placeholder keeps the bubble's footprint stable.
        Box(
            Modifier
                .fillMaxWidth(fraction = 0.62f)
                .heightIn(min = 120.dp, max = 200.dp)
                .clip(RoundedCornerShape(corner))
                .background(Color.DarkGray.copy(alpha = .25f))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            Text("…", color = Color.White.copy(alpha = .6f))
        }
    } else {
        val (w, h) = target
        AsyncImage(
            model = url,
            contentDescription = "Image attachment",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = w.dp, height = h.dp)
                .clip(RoundedCornerShape(corner))
                .clickable(onClick = onOpen)
        )
    }
}

/**
 * Shared full-screen image viewer: pinch-zoom (1x–5x), pan while zoomed, double-tap zoom,
 * swipe-to-dismiss and a download button. Used by direct chats and group chats alike.
 */
@Composable
fun FullScreenImageViewer(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1f) onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                            else scale = 2.4f
                        }
                    )
                }
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .5f))
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            IconButton(
                onClick = {
                    runCatching {
                        val request = DownloadManager.Request(Uri.parse(url))
                            .setTitle("Convo Chat image")
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "convo_${System.currentTimeMillis()}.jpg")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        android.widget.Toast.makeText(context, "Saving to Downloads…", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .5f))
            ) { Icon(Icons.Default.Download, "Download", tint = Color.White) }
            Text(
                "Pinch to zoom • double-tap • tap to close",
                color = Color.White.copy(alpha = .55f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
            )
        }
    }
}
