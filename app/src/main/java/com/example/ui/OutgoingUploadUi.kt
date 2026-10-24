package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale

/** Placeholder bubble for a photo / voice / file that is still uploading. */
@Composable
fun OutgoingUploadBubble(
    upload: OutgoingUpload,
    accent: Color,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val progress by animateFloatAsState(upload.percent / 100f, tween(300), label = "upload_progress")
    val danger = Color(0xFFE53935)
    val statusText = when {
        upload.failed -> "Not sent · ${upload.error.take(70).ifBlank { "network error" }}"
        upload.percent >= 100 -> "Finishing…"
        upload.percent <= 0 -> "Preparing · ${uploadFormatBytes(upload.totalBytes)}"
        else -> buildString {
            append("${upload.percent}%")
            if (upload.etaSeconds >= 0) append(" · ${uploadFormatEta(upload.etaSeconds)} left")
            if (upload.totalBytes > 0) append(" · ${uploadFormatBytes(upload.totalBytes)}")
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
            shadowElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .border(1.dp, Brush.linearGradient(listOf(accent.copy(.7f), accent.copy(.15f))), RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
        ) {
            Column(Modifier.padding(8.dp)) {
                when (upload.kind) {
                    "photo" -> Box(Modifier.size(220.dp, 170.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(.12f)), contentAlignment = Alignment.Center) {
                        if (!upload.preview.isNullOrBlank()) {
                            AsyncImage(upload.preview, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        }
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (upload.failed) .45f else .32f)))
                        ProgressRing(progress, accent, upload.failed, upload.percent, onRetry)
                    }
                    "voice" -> Row(Modifier.width(230.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            ProgressRing(progress, accent, upload.failed, upload.percent, onRetry, small = true)
                        }
                        Spacer(Modifier.width(8.dp))
                        UploadWave(accent, upload.failed, Modifier.weight(1f).height(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("%d:%02d".format(Locale.US, upload.durationSec / 60, upload.durationSec % 60), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Row(Modifier.width(240.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            ProgressRing(progress, accent, upload.failed, upload.percent, onRetry, small = true, icon = Icons.Default.InsertDriveFile)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(upload.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = if (upload.failed) danger else accent,
                                trackColor = accent.copy(alpha = .15f)
                            )
                        }
                    }
                }
                Row(Modifier.padding(start = 4.dp, top = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!upload.failed) {
                        CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = accent)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.ErrorOutline, null, tint = danger, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        statusText,
                        fontSize = 11.sp,
                        color = if (upload.failed) danger else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    if (upload.failed) {
                        Text("Retry", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Icon(
                        Icons.Default.Close, "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onCancel).padding(3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    accent: Color,
    failed: Boolean,
    percent: Int,
    onRetry: () -> Unit,
    small: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val size = if (small) 40.dp else 64.dp
    val spin = rememberInfiniteTransition(label = "ring_spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring_angle")
    Box(
        Modifier.size(size).clip(CircleShape).background(if (small) accent.copy(alpha = .14f) else Color.Black.copy(alpha = .35f))
            .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize().padding(3.dp)) {
            val stroke = Stroke(width = (if (small) 3.dp else 4.dp).toPx(), cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = if (small) .0f else .25f), 0f, 360f, false, style = stroke)
            if (failed) drawArc(Color(0xFFE53935), 0f, 360f, false, style = stroke)
            else if (percent <= 0) drawArc(accent, angle, 70f, false, style = stroke)
            else drawArc(accent, -90f, 360f * progress, false, style = stroke)
        }
        when {
            failed -> Icon(Icons.Default.Refresh, "Retry", tint = if (small) Color(0xFFE53935) else Color.White, modifier = Modifier.size(if (small) 18.dp else 26.dp))
            icon != null -> Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            small -> Icon(Icons.Default.Mic, null, tint = accent, modifier = Modifier.size(18.dp))
            else -> Text("$percent%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun UploadWave(accent: Color, failed: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "wave_phase")
    Canvas(modifier) {
        val bars = 22
        val gap = size.width / bars
        for (i in 0 until bars) {
            val base = ((i * 37) % 11) / 11f
            val wobble = if (failed) 0f else (kotlin.math.sin((phase * 2 * Math.PI + i * .5)).toFloat() + 1f) / 2f
            val h = size.height * (.25f + .55f * base * (.6f + .4f * wobble))
            val x = i * gap + gap / 2
            drawLine(if (failed) Color(0xFFE53935).copy(.6f) else accent.copy(alpha = .75f), androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2), androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2), strokeWidth = gap * .5f, cap = StrokeCap.Round)
        }
    }
}

private fun uploadFormatEta(sec: Long): String = when {
    sec < 1 -> "<1s"
    sec < 60 -> "~${sec}s"
    sec < 3600 -> "~${sec / 60}m ${sec % 60}s"
    else -> "~${sec / 3600}h ${(sec % 3600) / 60}m"
}

private fun uploadFormatBytes(b: Long): String = when {
    b <= 0 -> ""
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
}
