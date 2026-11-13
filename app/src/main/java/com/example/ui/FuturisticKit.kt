package com.example.ui

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraPink
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.Ink950
import kotlin.math.sin

/**
 * FuturisticKit — shared "2050" design primitives for the v4 mega-update.
 * Every screen being redesigned SHOULD reuse these (AuroraBackground, GlassCard,
 * GradientButton, ShimmerBox, NeonText, springPress) so the whole app stays
 * visually consistent. Pure Compose + no new Gradle dependencies. Animations
 * are throttled (rememberInfiniteTransition, single Canvas pass) to avoid
 * recomposition storms.
 */
object FuturisticGradients {
    val AuroraHorizontal = Brush.horizontalGradient(listOf(AuroraViolet, AuroraCyan, AuroraPink))
    val AuroraVertical = Brush.verticalGradient(listOf(AuroraViolet, AuroraCyan))
    val AuroraDiagonal = Brush.linearGradient(
        colors = listOf(AuroraViolet, AuroraCyan, AuroraPink),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    val GlassFill = Brush.verticalGradient(
        colors = listOf(Color(0x26FFFFFF), Color(0x0DFFFFFF))
    )
}

/** Animated aurora/particle background. Cheap: two drifting radial blobs + static starfield drawn once per frame via infinite transition. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink950)
            .drawBehind {
                val w = size.width
                val h = size.height
                val dx = (drift - 0.5f) * w * 0.24f
                // Violet blob top-left
                drawCircle(
                    brush = Brush.radialGradient(listOf(AuroraViolet.copy(alpha = 0.34f * pulse), Color.Transparent)),
                    radius = w * 0.62f,
                    center = Offset(w * 0.18f + dx, h * 0.16f)
                )
                // Cyan blob bottom-right
                drawCircle(
                    brush = Brush.radialGradient(listOf(AuroraCyan.copy(alpha = 0.26f * pulse), Color.Transparent)),
                    radius = w * 0.58f,
                    center = Offset(w * 0.86f - dx, h * 0.82f)
                )
                // Pink accent blob
                drawCircle(
                    brush = Brush.radialGradient(listOf(AuroraPink.copy(alpha = 0.16f * pulse), Color.Transparent)),
                    radius = w * 0.4f,
                    center = Offset(w * 0.55f + dx * 0.5f, h * 0.5f)
                )
                // Static starfield (deterministic, no per-frame allocation)
                val seed = 7
                var s = seed
                for (i in 0 until 46) {
                    s = (s * 1103515245 + 12345) and 0x7fffffff
                    val x = (s % 1000) / 1000f * w
                    s = (s * 1103515245 + 12345) and 0x7fffffff
                    val y = (s % 1000) / 1000f * h
                    val tw = 0.25f + 0.5f * (0.5f + 0.5f * sin((drift * 6.28318f * 2f) + i)).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.10f * tw), radius = 1.1f, center = Offset(x, y))
                }
            }
    ) { content() }
}

/** Glassmorphism card with animated gradient border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    borderWidth: Dp = 1.2.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "glassBorder")
    val hue by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "hue"
    )
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            AuroraViolet.copy(alpha = 0.75f),
            AuroraCyan.copy(alpha = 0.75f),
            AuroraPink.copy(alpha = 0.75f),
            AuroraViolet.copy(alpha = 0.75f)
        ),
        start = Offset(hue * 400f, hue * 200f),
        end = Offset(hue * 400f + 300f, hue * 200f + 300f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(FuturisticGradients.GlassFill)
            .border(borderWidth, borderBrush, shape)
            .padding(16.dp)
    ) { content() }
}

/** Spring-scale press modifier with haptic feedback. Apply to clickable surfaces. */
fun Modifier.springPress(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "springPress"
    )
    val view = LocalView.current
    this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                if (enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            }
        )
}

/** Primary gradient button with glow. Wrap your own Row/Text content. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .drawBehind {
                if (enabled) {
                    // soft glow under the button
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(AuroraViolet.copy(alpha = 0.35f), AuroraCyan.copy(alpha = 0.35f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx() + 6f),
                        alpha = 0.6f
                    )
                }
            }
            .clip(shape)
            .background(
                if (enabled) FuturisticGradients.AuroraHorizontal
                else Brush.horizontalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF2A2D3A)))
            )
            .springPress(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Text with neon glow shadow. */
@Composable
fun NeonText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    glow: Color = AuroraCyan
) {
    Text(
        text = text,
        style = style.copy(
            color = color,
            shadow = androidx.compose.ui.graphics.Shadow(color = glow.copy(alpha = 0.65f), blurRadius = 18f)
        ),
        modifier = modifier
    )
}

/** Shimmer skeleton placeholder (for loading states). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "translate"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawBehind {
                val base = Color(0xFF1A1D29)
                drawRect(base)
                val highlight = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x33FFFFFF), Color.Transparent),
                    start = Offset(translate * size.width, 0f),
                    end = Offset((translate + 0.5f) * size.width, size.height)
                )
                drawRect(highlight)
            }
    )
}

/** Small glowing dot (online / live / recording indicator). */
@Composable
fun GlowDot(color: Color = AuroraCyan, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glowDot")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.35f * alpha), radius = this.size.width * 1.1f)
                drawCircle(color, radius = this.size.width / 2f)
            }
    )
}

/** Thin animated top "talking" call bar indicator (mic activity). */
@Composable
fun TalkingBar(modifier: Modifier = Modifier, active: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "talking")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Box(
        modifier = modifier
            .size(width = 120.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                val w = size.width
                val activeBrush = Brush.horizontalGradient(
                    colors = listOf(AuroraCyan, AuroraPink),
                    startX = phase * w,
                    endX = phase * w + w * 0.5f
                )
                if (active) drawRect(activeBrush) else drawRect(Color(0xFF3A3D4A))
            }
    )
}
