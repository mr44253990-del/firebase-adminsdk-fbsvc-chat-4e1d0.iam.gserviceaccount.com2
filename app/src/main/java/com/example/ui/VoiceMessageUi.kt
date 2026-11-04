package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val PLAYBACK_SPEEDS = listOf(1f, 1.5f, 2f)

/**
 * Voice note player: tap play/pause, tap or drag the waveform to seek, change speed
 * (1x / 1.5x / 2x) and save the clip to Downloads. The waveform is generated from the
 * clip URL so it stays the same every time the message is shown.
 */
@Composable
fun VoicePlayerBubble(
    voiceUrl: String,
    durationSec: Int,
    onPlaybackStarted: () -> Unit = {},
    accent: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    title: String = "Voice message"
) {
    val context = LocalContext.current
    var player by remember(voiceUrl) { mutableStateOf<MediaPlayer?>(null) }
    var preparing by remember(voiceUrl) { mutableStateOf(false) }
    var isPlaying by remember(voiceUrl) { mutableStateOf(false) }
    var positionMs by remember(voiceUrl) { mutableIntStateOf(0) }
    var totalMs by remember(voiceUrl) { mutableIntStateOf((durationSec.coerceAtLeast(0)) * 1000) }
    var speedIndex by remember(voiceUrl) { mutableIntStateOf(0) }
    var pendingSeek by remember(voiceUrl) { mutableFloatStateOf(-1f) }
    var acknowledged by remember(voiceUrl) { mutableStateOf(false) }
    var downloaded by remember(voiceUrl) { mutableStateOf(false) }
    val bars = remember(voiceUrl) {
        val random = Random(voiceUrl.hashCode())
        List(34) { i ->
            val envelope = 0.55f + 0.45f * kotlin.math.sin(i / 34f * Math.PI.toFloat())
            (0.22f + random.nextFloat() * 0.78f) * envelope
        }
    }

    DisposableEffect(voiceUrl) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            player?.let { p -> runCatching { positionMs = p.currentPosition } }
            delay(60)
        }
    }

    fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(PLAYBACK_SPEEDS[speedIndex]) }
    }

    fun startOrToggle() {
        val current = player
        try {
            when {
                current != null && isPlaying -> { current.pause(); isPlaying = false }
                current != null -> { current.start(); applySpeed(current); isPlaying = true }
                preparing -> Unit
                else -> {
                    preparing = true
                    val mp = MediaPlayer()
                    if (voiceUrl.startsWith("content://")) mp.setDataSource(context, Uri.parse(voiceUrl)) else mp.setDataSource(voiceUrl)
                    mp.setOnPreparedListener {
                        preparing = false
                        if (it.duration > 0) totalMs = it.duration
                        if (pendingSeek >= 0f) { it.seekTo((pendingSeek * totalMs).toInt()); pendingSeek = -1f }
                        it.start(); applySpeed(it); isPlaying = true
                        if (!acknowledged) { acknowledged = true; onPlaybackStarted() }
                    }
                    mp.setOnCompletionListener { isPlaying = false; positionMs = 0; runCatching { it.seekTo(0) } }
                    mp.setOnErrorListener { _, _, _ ->
                        preparing = false; isPlaying = false
                        Toast.makeText(context, "This voice message cannot be played", Toast.LENGTH_SHORT).show()
                        true
                    }
                    player = mp
                    mp.prepareAsync()
                }
            }
        } catch (_: Exception) {
            preparing = false
            Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show()
        }
    }

    fun seekTo(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        val p = player
        if (p != null && !preparing && totalMs > 0) {
            val target = (f * totalMs).toInt()
            runCatching { p.seekTo(target) }
            positionMs = target
        } else {
            pendingSeek = f
            positionMs = (f * totalMs).toInt()
        }
    }

    fun download() {
        if (!voiceUrl.startsWith("http")) {
            Toast.makeText(context, "This voice message is already saved on this phone", Toast.LENGTH_SHORT).show()
            downloaded = true
            return
        }
        runCatching {
            val ext = voiceUrl.substringBefore('?').substringAfterLast('.', "m4a").take(4).ifBlank { "m4a" }
            val request = DownloadManager.Request(Uri.parse(voiceUrl))
                .setTitle("Convo voice message")
                .setMimeType("audio/*")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Convo_voice_${System.currentTimeMillis()}.$ext")
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            downloaded = true
            Toast.makeText(context, "Saving voice message to Downloads", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Could not start the download", Toast.LENGTH_SHORT).show() }
    }

    val progress = if (totalMs > 0) (positionMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val onAccent = if (accent.luminance() > .6f) Color(0xFF1B1530) else Color.White
    val pulse = rememberInfiniteTransition(label = "voice_pulse")
    val ring by pulse.animateFloat(1f, 1.28f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "voice_ring")

    Row(
        Modifier
            .widthIn(min = 230.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .20f), contentColor.copy(alpha = .06f))))
            .border(1.dp, accent.copy(alpha = .30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                Box(
                    Modifier.size(42.dp).graphicsLayer { scaleX = ring; scaleY = ring; alpha = 1.3f - ring }
                        .background(accent.copy(alpha = .45f), CircleShape)
                )
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .72f))))
                    .clickable { startOrToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (preparing) CircularProgressIndicator(Modifier.size(20.dp), color = onAccent, strokeWidth = 2.dp)
                else Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = onAccent, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth().height(30.dp)
                    .pointerInput(voiceUrl) { detectTapGestures { offset -> seekTo(offset.x / size.width) } }
                    .pointerInput(voiceUrl) {
                        detectHorizontalDragGestures { change, _ -> change.consume(); seekTo(change.position.x / size.width) }
                    }
            ) {
                val gap = 2.dp.toPx()
                val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1.5f)
                bars.forEachIndexed { i, amplitude ->
                    val x = i * (barWidth + gap)
                    val h = (size.height * amplitude).coerceAtLeast(3.dp.toPx())
                    val played = (x + barWidth / 2) / size.width <= progress
                    drawRoundRect(
                        color = if (played) accent else contentColor.copy(alpha = .32f),
                        topLeft = Offset(x, (size.height - h) / 2),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2)
                    )
                }
                if (progress > 0f) {
                    drawCircle(accent, radius = 4.dp.toPx(), center = Offset(size.width * progress, size.height / 2))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    if (positionMs > 0 || isPlaying) "${formatClip(positionMs)} / ${formatClip(totalMs)}" else if (totalMs > 0) formatClip(totalMs) else title,
                    color = contentColor.copy(alpha = .75f), fontSize = 10.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .22f))
                        .clickable {
                            speedIndex = (speedIndex + 1) % PLAYBACK_SPEEDS.size
                            player?.let { if (isPlaying) applySpeed(it) }
                        }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    val speed = PLAYBACK_SPEEDS[speedIndex]
                    Text(if (speed == 1f) "1x" else if (speed == 2f) "2x" else "1.5x", color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(contentColor.copy(alpha = .10f)).clickable { download() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (downloaded) Icons.Default.DownloadDone else Icons.Default.Download, "Download voice message", tint = if (downloaded) accent else contentColor.copy(alpha = .85f), modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatClip(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
