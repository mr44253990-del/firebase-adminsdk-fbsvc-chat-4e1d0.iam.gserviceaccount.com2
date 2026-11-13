package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Nova motion kit (v5.0): small shared animations so every main surface moves the same way —
 * springy tab icons, a living "create" button, a page entrance when switching tabs and a glowing
 * edge for floating bars.
 */

/** Bottom-bar icon that pops with a spring and gets a soft glow when its tab is selected. */
@Composable
fun NovaNavIcon(icon: ImageVector, description: String, selected: Boolean) {
    val scale by animateFloatAsState(if (selected) 1.18f else 1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium), label = "nav_scale")
    val lift by animateFloatAsState(if (selected) -2f else 0f, spring(dampingRatio = .6f), label = "nav_lift")
    val glow = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.drawBehind {
        if (selected) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .30f), Color.Transparent), center, size.maxDimension * 1.1f), size.maxDimension * 1.1f)
    }) {
        Icon(icon, contentDescription = description, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationY = lift * density })
    }
}

/** The centre "create" button: breathing gradient core inside a slowly rotating light ring. */
@Composable
fun NovaCreateButton() {
    val motion = rememberInfiniteTransition(label = "create_btn")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "spin")
    val breathe by motion.animateFloat(.96f, 1.05f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(56.dp).graphicsLayer { rotationZ = spin }.clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color(0xFF36D8E6), Color(0xFF674CFF), Color(0xFFFF36BE), Color(0xFFFFB03A), Color(0xFF36D8E6))))
        )
        Box(
            Modifier.size(49.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }.clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF674CFF), Color(0xFFFF36BE))))
                .border(1.dp, Color.White.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, contentDescription = "Create", tint = Color.White, modifier = Modifier.size(27.dp)) }
    }
}

/** Replays a short fade + rise + settle every time [tab] changes (no state is lost). */
fun Modifier.novaTabEntrance(tab: Int): Modifier = composed {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(tab) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = .35f + .65f * progress.value
        translationY = (1f - progress.value) * 26f * density
        val s = .985f + .015f * progress.value
        scaleX = s; scaleY = s
    }
}

/** Thin moving light line along the top edge of a bar. */
fun Modifier.novaGlowEdge(): Modifier = composed {
    val shift by rememberInfiniteTransition(label = "edge").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "edge_shift")
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.tertiary
    drawBehind {
        val x = size.width * (shift * 1.6f - .3f)
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, a.copy(alpha = .9f), b.copy(alpha = .9f), Color.Transparent), startX = x - size.width * .3f, endX = x + size.width * .3f),
            Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 2.dp.toPx()
        )
    }
}
