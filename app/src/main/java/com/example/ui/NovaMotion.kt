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
import androidx.compose.foundation.layout.padding
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

/**
 * Home top-bar brand block (v5.2). The look follows who is signed in:
 *  - Admin: teal/violet command ring and an ADMIN chip
 *  - Premium: gold ring that slowly turns, with a shimmering PREMIUM chip
 *  - Everyone else: the brand gradient
 * The second line greets the user by time of day (or shows the offline state).
 */
@Composable
fun NovaBrandTitle(logo: @Composable () -> Unit, role: String, online: Boolean, firstName: String) {
    val motion = rememberInfiniteTransition(label = "brand")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(if (role == "user") 14000 else 6000, easing = LinearEasing)), label = "brand_spin")
    val sheen by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "brand_sheen")
    val ring = when (role) {
        "admin" -> listOf(Color(0xFF19E3C2), Color(0xFF6C5CE7), Color(0xFF36D8E6), Color(0xFF19E3C2))
        "staff" -> listOf(Color(0xFF3AA0FF), Color(0xFF6C5CE7), Color(0xFF3AA0FF))
        "premium" -> listOf(Color(0xFFFFE08A), Color(0xFFFF9D2E), Color(0xFFFFF3C4), Color(0xFFE2A93B), Color(0xFFFFE08A))
        else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
    }
    val titleColors = when (role) {
        "admin", "staff" -> listOf(Color(0xFF19E3C2), Color(0xFF8B7BFF), Color(0xFF36D8E6))
        "premium" -> listOf(Color(0xFFFFC233), Color(0xFFFF8A3D), Color(0xFFFFE08A))
        else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).graphicsLayer { rotationZ = spin }.clip(CircleShape).background(Brush.sweepGradient(ring)))
            Box(Modifier.size(37.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { logo() }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(9.dp))
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    "Convo Chat",
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(titleColors, start = Offset(sheen * 300f, 0f), end = Offset(sheen * 300f + 260f, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Mirror),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        fontSize = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp),
                        letterSpacing = androidx.compose.ui.unit.TextUnit(-0.7f, androidx.compose.ui.unit.TextUnitType.Sp)
                    ),
                    maxLines = 1
                )
                if (role != "user") {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    val chip = when (role) { "admin" -> "ADMIN"; "staff" -> "STAFF"; else -> "PREMIUM" }
                    androidx.compose.material3.Text(
                        chip,
                        color = if (role == "premium") Color(0xFF3A2A06) else Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        fontSize = androidx.compose.ui.unit.TextUnit(8.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
                        modifier = Modifier.clip(CircleShape)
                            .background(Brush.linearGradient(if (role == "premium") listOf(Color(0xFFFFE08A), Color(0xFFFF9D2E)) else listOf(Color(0xFF0FA89A), Color(0xFF6C5CE7)), start = Offset(sheen * 120f, 0f), end = Offset(sheen * 120f + 90f, 40f)))
                            .then(Modifier.androidPaddingChip())
                    )
                }
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (online) Color(0xFF26E86F) else Color(0xFFFF5252)))
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" }
                androidx.compose.material3.Text(
                    if (!online) "Offline · showing saved chats" else greeting + (if (firstName.isNotBlank()) ", $firstName" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Modifier.androidPaddingChip(): Modifier = this.then(androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 2.dp).let { Modifier.padding(it) })
