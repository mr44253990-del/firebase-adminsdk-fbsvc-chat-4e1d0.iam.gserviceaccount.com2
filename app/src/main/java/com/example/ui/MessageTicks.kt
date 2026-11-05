package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage

/** Receipt level: 0 = sent (one tick), 1 = delivered (two ticks), 2 = seen (two coloured ticks). */
@Composable
fun MessageTicks(level: Int, tint: Color, seenTint: Color = Color(0xFF34B7F1), modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val second by animateFloatAsState(if (level >= 1) 1f else 0f, tween(260), label = "tick2")
    val color = if (level >= 2) seenTint else tint
    Canvas(modifier.size(width = size * 1.25f, height = size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = h * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun tick(dx: Float, progress: Float) {
            if (progress <= 0f) return
            val p = Path().apply {
                moveTo(dx + w * 0.06f, h * 0.55f)
                lineTo(dx + w * 0.27f, h * 0.76f)
                lineTo(dx + w * 0.27f + (w * 0.42f) * progress, h * 0.76f - (h * 0.52f) * progress)
            }
            drawPath(p, color, style = stroke)
        }
        // When delivered, the first tick shifts left to make room for the second one.
        tick(-w * 0.04f * second, 1f)
        tick(w * 0.24f, second)
    }
}

/** Small "seen by" avatar shown under the latest message the peer has read. */
@Composable
fun SeenAvatar(imageUrl: String, name: String, modifier: Modifier = Modifier, ring: Color = Color(0xFF34B7F1)) {
    Box(
        modifier.size(16.dp).clip(CircleShape).border(1.2.dp, ring, CircleShape).background(ring.copy(alpha = .25f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(imageUrl, "Seen by $name", contentScale = ContentScale.Crop, modifier = Modifier.size(16.dp).clip(CircleShape))
        } else {
            Text(name.take(1).uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Canvas-drawn microphone glyph (no emoji) for voice-message previews and bubbles. */
@Composable
fun MicGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.1f
        // Capsule body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f)
        )
        // Cradle arc
        drawArc(
            color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.5f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        // Stem + base
        drawLine(tint, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.5f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.9f), Offset(w * 0.66f, h * 0.9f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Canvas-drawn photo glyph. */
@Composable
fun PhotoGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(tint, Offset(w * 0.08f, h * 0.18f), androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.66f),
            androidx.compose.ui.geometry.CornerRadius(w * 0.14f, w * 0.14f), style = Stroke(w * 0.09f))
        drawCircle(tint, w * 0.08f, Offset(w * 0.68f, h * 0.38f))
        val p = Path().apply { moveTo(w * 0.16f, h * 0.76f); lineTo(w * 0.4f, h * 0.48f); lineTo(w * 0.58f, h * 0.68f); lineTo(w * 0.68f, h * 0.58f); lineTo(w * 0.84f, h * 0.76f); close() }
        drawPath(p, tint)
    }
}
