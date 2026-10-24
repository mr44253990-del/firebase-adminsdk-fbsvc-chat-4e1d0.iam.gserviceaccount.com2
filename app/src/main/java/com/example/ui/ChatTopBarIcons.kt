package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Segmented call pill for the chat top bar: video | audio, each with its own gradient. */
@Composable
fun ChatCallPill(onVideo: () -> Unit, onAudio: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.fillMaxHeight().width(44.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF2F80ED), Color(0xFF56CCF2))))
                .clickable(onClick = onVideo)
                .semantics { contentDescription = "Video call" },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = .35f)))
        Box(
            Modifier.fillMaxHeight().width(44.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))))
                .clickable(onClick = onAudio)
                .semantics { contentDescription = "Audio call" },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

/** Canvas-coded theme button: a slowly turning colour orb with a sparkle. */
@Composable
fun ChatThemeOrb(onClick: () -> Unit, accent: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orb_angle")
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Chat theme" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(26.dp).rotate(angle)) {
            val r = size.minDimension / 2
            drawCircle(
                Brush.sweepGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFFC371), Color(0xFF38EF7D), Color(0xFF56CCF2), Color(0xFFB06AB3), Color(0xFFFF5F6D))),
                r
            )
            drawCircle(Color.White.copy(alpha = .9f), r * .46f)
            drawCircle(accent, r * .30f)
        }
        Canvas(Modifier.size(26.dp)) {
            val s = size.minDimension
            val c = Offset(s * .86f, s * .14f)
            val l = s * .16f
            drawLine(Color.White, Offset(c.x - l, c.y), Offset(c.x + l, c.y), strokeWidth = 2.2f)
            drawLine(Color.White, Offset(c.x, c.y - l), Offset(c.x, c.y + l), strokeWidth = 2.2f)
            drawArc(accent.copy(alpha = .55f), -40f, 80f, false, Offset(-2f, -2f), Size(s + 4f, s + 4f), style = Stroke(1.6f))
        }
    }
}
