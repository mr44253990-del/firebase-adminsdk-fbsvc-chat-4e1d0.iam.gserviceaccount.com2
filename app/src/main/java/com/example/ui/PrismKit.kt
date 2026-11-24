package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v6.3 "Prism" design kit — aurora mesh + faint grid on deep ink, gradient-rimmed glass
 * surfaces, gradient primary actions. Self-contained so any screen can adopt it piece by piece.
 */
val PrismInk = Color(0xFF05060E)
val PrismViolet = Color(0xFF7C5CFC)
val PrismBlue = Color(0xFF3D8BFF)
val PrismCyan = Color(0xFF35D6C7)
val PrismPink = Color(0xFFFF6FAE)

fun prismAccentBrush(): Brush = Brush.linearGradient(listOf(PrismViolet, PrismBlue, PrismCyan))

/** Aurora mesh: three slowly drifting radial orbs plus a faint grid, drawn (no recomposition). */
@Composable
fun PrismMeshBackground(
    modifier: Modifier = Modifier,
    grid: Boolean = true,
    gridAlpha: Float = 0.05f,
    content: @Composable () -> Unit = {}
) {
    val ink = MaterialTheme.colorScheme.background
    val motion = rememberInfiniteTransition(label = "prism")
    val drift by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(18_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "prism_drift"
    )
    Box(modifier.fillMaxSize().background(ink)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (w < 2f || h < 2f) return@Canvas
            val d = drift
            drawCircle(
                Brush.radialGradient(listOf(PrismViolet.copy(alpha = .30f), Color.Transparent), Offset(w * (.1f + .2f * d), h * .08f), w * .85f),
                w * .85f, Offset(w * (.1f + .2f * d), h * .08f)
            )
            drawCircle(
                Brush.radialGradient(listOf(PrismBlue.copy(alpha = .22f), Color.Transparent), Offset(w * (.95f - .2f * d), h * (.5f + .1f * d)), w * .8f),
                w * .8f, Offset(w * (.95f - .2f * d), h * (.5f + .1f * d))
            )
            drawCircle(
                Brush.radialGradient(listOf(PrismPink.copy(alpha = .16f), Color.Transparent), Offset(w * (.3f + .25f * d), h * (1.0f - .1f * d)), w * .9f),
                w * .9f, Offset(w * (.3f + .25f * d), h * (1.0f - .1f * d))
            )
            if (grid) {
                val step = 54.dp.toPx()
                var x = 0f
                while (x <= w) { drawLine(Color.White.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x, h), 1f); x += step }
                var y = 0f
                while (y <= h) { drawLine(Color.White.copy(alpha = gridAlpha), Offset(0f, y), Offset(w, y), 1f); y += step }
            }
        }
        content()
    }
}

/** Gradient-rimmed frosted surface — the Prism replacement for plain glass cards. */
fun Modifier.prismGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    fillAlpha: Float = 0.07f,
    tint: Color = PrismViolet,
    elevation: Dp = 14.dp
): Modifier = this
    .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .2f))
    .clip(shape)
    .background(Color.White.copy(alpha = fillAlpha))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = .32f), tint.copy(alpha = .40f), PrismBlue.copy(alpha = .25f), Color.White.copy(alpha = .08f))),
        shape
    )

/** Primary action: violet → blue → cyan gradient pill. */
@Composable
fun PrismGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) prismAccentBrush() else Brush.linearGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Round gradient FAB (send / call actions). */
@Composable
fun PrismFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    brush: Brush = prismAccentBrush()
) {
    Box(
        modifier
            .size(size)
            .shadow(12.dp, CircleShape, ambientColor = PrismBlue.copy(alpha = .45f))
            .clip(CircleShape)
            .background(brush)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(size * .45f))
    }
}

/** Frosted top bar with a gradient title — drop-in for page headers. */
@Composable
fun PrismTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .55f))
            .drawBehind { drawLine(Color.White.copy(alpha = .08f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null) navigationIcon()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(brush = prismAccentBrush(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Gradient pill for unread counts. */
@Composable
fun PrismBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(prismAccentBrush(), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
