package com.example.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@androidx.media3.common.util.UnstableApi
@Composable
fun SharedCachedVideo(
    ownerId: String,
    videoUrl: String,
    thumbnailUrl: String = "",
    active: Boolean,
    playWhenReady: Boolean = active,
    sound: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var buffering by remember(ownerId, videoUrl) { mutableStateOf(active) }
    var failed by remember(ownerId, videoUrl) { mutableStateOf(false) }
    val player = remember(ownerId, videoUrl, active, playWhenReady, sound) {
        if (active) VideoPlayerManager.acquire(context, ownerId, videoUrl, playWhenReady, sound) else null
    }
    DisposableEffect(player, active) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            if (active) VideoPlayerManager.detach(ownerId)
        }
    }
    LaunchedEffect(active, playWhenReady, videoUrl) {
        if (active) VideoPlayerManager.acquire(context, ownerId, videoUrl, playWhenReady, sound)
        else VideoPlayerManager.pause(ownerId)
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!thumbnailUrl.isBlank() && (buffering || !active || failed)) {
            AsyncImage(thumbnailUrl, "Video thumbnail", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)))
        }
        if (active && player != null) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (buffering && active && !failed) CircularProgressIndicator(color = Color.White)
    }
}
