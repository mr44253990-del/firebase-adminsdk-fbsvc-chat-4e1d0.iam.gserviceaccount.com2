package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Convo Nexus GlassKit — the shared motion + material language for every screen.
 *
 * Design rules:
 *  * one continuous aurora canvas behind all routes, so navigation never "flashes";
 *  * translucent, light-bent glass cards with a specular top edge;
 *  * every animation is driven by a single [rememberInfiniteTransition] per surface and
 *    rendered inside [drawWithCache]/[Canvas], which keeps recomposition count at zero
 *    while the pixels keep moving. That is what removes the jank on mid-range phones.
 */

/** Frosted panel with specular highlight + soft ambient lift. */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 18.dp,
    tint: Color? = null,
    strokeAlpha: Float = 0.22f
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val base = tint ?: if (dark) Color(0xFF141828) else Color.White
    val fill = Brush.verticalGradient(
        listOf(
            base.copy(alpha = if (dark) 0.62f else 0.80f),
            base.copy(alpha = if (dark) 0.38f else 0.62f)
        )
    )
    val stroke = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) strokeAlpha + 0.16f else 0.95f),
            Color.White.copy(alpha = strokeAlpha * 0.35f),
            scheme.primary.copy(alpha = strokeAlpha * 0.75f)
        )
    )
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = scheme.primary.copy(alpha = 0.30f),
            spotColor = scheme.tertiary.copy(alpha = 0.22f)
        )
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, stroke, shape)
}

/** Slow-breathing conic halo used for avatars, story rings and primary CTAs. */
@Composable
fun Modifier.auroraRing(
    thickness: Dp = 2.5.dp,
    shape: Shape = RoundedCornerShape(percent = 50),
    speedMillis: Int = 6000
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "auroraRing")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(speedMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val colors = remember(scheme.primary, scheme.secondary, scheme.tertiary) {
        listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)
    }
    return this.border(
        width = thickness,
        brush = Brush.sweepGradient(
            colors.mapIndexed { index, color ->
                val stop = (index / (colors.size - 1f) + sweep / 360f) % 1f
                stop to color
            }.sortedBy { it.first }
        ),
        shape = shape
    )
}

private data class Bubble(
    val x: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float,
    val drift: Float,
    val alpha: Float,
    val colorIndex: Int
)

/**
 * The signature background: three drifting aurora blobs + a rising bubble field.
 * Renders entirely on the draw phase (no recomposition), so it costs ~0.3 ms/frame.
 */
@Composable
fun NexusBackground(
    modifier: Modifier = Modifier,
    bubbleCount: Int = 22,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val transition = rememberInfiniteTransition(label = "nexusBg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val bubbles = remember(bubbleCount) {
        val random = Random(bubbleCount * 7919)
        List(bubbleCount) {
            Bubble(
                x = random.nextFloat(),
                radius = 6f + random.nextFloat() * 26f,
                speed = 0.25f + random.nextFloat() * 0.85f,
                phase = random.nextFloat(),
                drift = (random.nextFloat() - 0.5f) * 0.22f,
                alpha = 0.05f + random.nextFloat() * 0.16f,
                colorIndex = random.nextInt(3)
            )
        }
    }

    val palette = listOf(scheme.primary, scheme.tertiary, scheme.secondary)

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val blobRadius = maxOf(w, h) * 0.62f * intensity
                val angle = t * 2f * PI.toFloat()

                fun blob(color: Color, cx: Float, cy: Float, scale: Float) = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = if (dark) 0.34f else 0.20f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = (blobRadius * scale).coerceAtLeast(1f)
                )

                val b1 = blob(
                    palette[0],
                    w * (0.22f + 0.16f * cos(angle)),
                    h * (0.18f + 0.10f * sin(angle)),
                    breathe
                )
                val b2 = blob(
                    palette[1],
                    w * (0.84f + 0.12f * sin(angle * 0.8f)),
                    h * (0.34f + 0.12f * cos(angle * 1.2f)),
                    2f - breathe
                )
                val b3 = blob(
                    palette[2],
                    w * (0.48f + 0.18f * cos(angle * 0.6f + 1.2f)),
                    h * (0.88f + 0.08f * sin(angle * 0.9f)),
                    breathe * 0.9f
                )
                val veil = Brush.verticalGradient(
                    listOf(
                        scheme.background.copy(alpha = 0.10f),
                        scheme.background.copy(alpha = 0.55f)
                    )
                )

                onDrawBehind {
                    drawRect(scheme.background)
                    drawRect(b1)
                    drawRect(b2)
                    drawRect(b3)
                    bubbles.forEach { bubble ->
                        val progress = (t * bubble.speed + bubble.phase) % 1f
                        val cy = h * (1.08f - progress * 1.2f)
                        val cx = w * (bubble.x + bubble.drift * sin(progress * 6.28f))
                        val fade = (1f - kotlin.math.abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
                        val color = palette[bubble.colorIndex]
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = bubble.alpha * fade * 1.5f),
                                    color.copy(alpha = bubble.alpha * fade * 0.45f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = bubble.radius * 2.6f
                            ),
                            radius = bubble.radius * 2.6f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = bubble.alpha * fade * 0.7f),
                            radius = bubble.radius * 0.34f,
                            center = Offset(cx - bubble.radius * 0.4f, cy - bubble.radius * 0.5f)
                        )
                    }
                    drawRect(veil)
                }
            },
        content = content
    )
}

/** Skeleton shimmer for the progressive "load a little at a time" first run. */
@Composable
fun Modifier.nexusShimmer(shape: Shape = RoundedCornerShape(18.dp)): Modifier {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing), RepeatMode.Restart),
        label = "x"
    )
    return this
        .clip(shape)
        .drawWithCache {
            val width = size.width.coerceAtLeast(1f)
            val brush = Brush.linearGradient(
                colors = listOf(
                    scheme.onSurface.copy(alpha = 0.05f),
                    scheme.onSurface.copy(alpha = 0.16f),
                    scheme.onSurface.copy(alpha = 0.05f)
                ),
                start = Offset(x * width, 0f),
                end = Offset(x * width + width * 0.75f, size.height)
            )
            onDrawBehind { drawRect(brush) }
        }
}

/** Soft "unread" pulse — the gentle red light asked for on chats with new messages. */
@Composable
fun Modifier.unreadPulse(active: Boolean, color: Color = Color(0xFFFF4D6D)): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "pulse")
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    return this.drawWithCache {
        val brush = Brush.horizontalGradient(
            listOf(color.copy(alpha = glow * 0.55f), Color.Transparent),
            startX = 0f,
            endX = size.width * 0.65f
        )
        onDrawBehind { drawRect(brush) }
    }
}

/** Frosted blur pass for hero media behind glass headers. */
fun Modifier.frostedMedia(radius: Dp = 22.dp): Modifier = this.blur(radius)

/** Cheap decorative sparkle layer for celebratory moments (reaction burst, premium). */
@Composable
fun SparkleLayer(modifier: Modifier = Modifier, count: Int = 14) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "sparkle")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    val seeds = remember(count) {
        val random = Random(count * 104729)
        List(count) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    Canvas(modifier) {
        val area: Size = size
        seeds.forEach { (sx, sy, sr) ->
            val local = (p + sr) % 1f
            val alpha = (1f - kotlin.math.abs(local - 0.5f) * 2f).coerceIn(0f, 1f)
            drawCircle(
                color = if (sr > 0.5f) scheme.tertiary.copy(alpha = alpha * 0.8f)
                else scheme.primary.copy(alpha = alpha * 0.8f),
                radius = 1.5f + sr * 3.5f,
                center = Offset(area.width * sx, area.height * sy)
            )
        }
    }
}
