package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderSoft
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LocalPalette
import kotlin.math.sin
import kotlin.random.Random

/* ========================================================================
 * GLASS DESIGN SYSTEM — lag-free glassmorphism for every screen.
 * Translucent surfaces + gradient borders + animated bubble backgrounds.
 * All animations run on the Compose render thread (hardware accelerated).
 * ======================================================================== */

/** Full-screen animated gradient background with floating glass bubbles. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    bubbleCount: Int = 14,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val palette = LocalPalette.current

    // Slowly drifting gradient
    val infinite = rememberInfiniteTransition(label = "bg")
    val drift by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    val gradientBrush = remember(palette, drift) {
        Brush.linearGradient(
            colors = palette.gradient,
            start = Offset(0f, drift * 900f),
            end = Offset(900f * (1f - drift) + 300f, 1800f)
        )
    }

    Box(modifier = modifier.fillMaxSize().background(gradientBrush)) {
        FloatingBubbles(count = bubbleCount)
        content()
    }
}

private class BubbleSpec(
    val xFraction: Float,
    val size: Dp,
    val speed: Int,
    val alpha: Float,
    val wobble: Float
)

/** Dreamy floating bubbles – pure Canvas, zero layout cost. */
@Composable
fun FloatingBubbles(count: Int = 14, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val bubbles = remember {
        List(count) {
            BubbleSpec(
                xFraction = Random.nextFloat(),
                size = Random.nextInt(24, 130).dp,
                speed = Random.nextInt(9000, 22000),
                alpha = Random.nextFloat() * 0.10f + 0.05f,
                wobble = Random.nextFloat() * 40f + 10f
            )
        }
    }
    val infinite = rememberInfiniteTransition(label = "bubbles")
    val t by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        bubbles.forEach { b ->
            val progress = (t + b.speed % 7 / 7f) % 1f
            val y = h + b.size.toPx() - progress * (h + b.size.toPx() * 2)
            val x = b.xFraction * w + sin(progress * 6.28f) * b.wobble
            // soft outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = b.alpha),
                        Color.Transparent
                    ),
                    center = Offset(x, y),
                    radius = b.size.toPx()
                ),
                radius = b.size.toPx(),
                center = Offset(x, y)
            )
            // glass rim
            drawCircle(
                color = Color.White.copy(alpha = b.alpha * 0.8f),
                radius = b.size.toPx() * 0.42f,
                center = Offset(x, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }
    }
}

/** The signature glass card — translucent body + luminous gradient border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    alpha: Float = 0.10f,
    borderAlpha: Float = 0.28f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = alpha + 0.06f),
                        Color.White.copy(alpha = alpha)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = borderAlpha),
                        palette.primary.copy(alpha = borderAlpha * 0.7f),
                        Color.White.copy(alpha = borderAlpha * 0.4f)
                    )
                ),
                shape = shape
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null
                ) { onClick() } else Modifier
            )
            .padding(2.dp),
        content = content
    )
}

/** Gradient pill button with press bounce + glow. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    colors: List<Color>? = null
) {
    val palette = LocalPalette.current
    val brushColors = colors ?: palette.bubbleMine
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) Brush.horizontalGradient(brushColors)
                else Brush.horizontalGradient(listOf(Color.Gray, Color.DarkGray))
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading
            ) { onClick() }
            .padding(vertical = 15.dp, horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            PulsingDots()
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/** Three bouncing dots (used for loading + typing indicator). */
@Composable
fun PulsingDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    val infinite = rememberInfiniteTransition(label = "dots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val dy by infinite.animateFloat(
                initialValue = 0f, targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, delayMillis = i * 140, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .offset(y = dy.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Text rendered with the theme gradient. */
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            brush = Brush.horizontalGradient(
                listOf(palette.primary, palette.secondary, palette.storyRing.first())
            ),
            fontWeight = FontWeight.ExtraBold
        )
    )
}

/** Shimmer placeholder for progressive loading (no jank while content loads). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "x"
    )
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.06f)
                        ),
                        start = Offset(translate - 300f, 0f),
                        end = Offset(translate, size.height)
                    )
                )
            }
    )
}

/** Small translucent chip. */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(palette.bubbleMine)
                else Brush.horizontalGradient(
                    listOf(GlassWhite, GlassWhite)
                )
            )
            .border(
                1.dp,
                if (selected) palette.primary.copy(alpha = 0.6f) else GlassBorderSoft,
                RoundedCornerShape(50)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}

/** Pulsing red dot for unread indicators. */
@Composable
fun UnreadDot(modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val infinite = rememberInfiniteTransition(label = "unread")
    val alpha by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "a"
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(com.example.ui.theme.UnreadRed)
    )
}

/** Animated numeric badge (unread counts, notifications). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = count > 0,
        enter = androidx.compose.animation.scaleIn(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 22.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFFF3B5C), Color(0xFFFF6E7F))
                    )
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Entrance animation helper – slides + fades content in with a stagger. */
@Composable
fun AnimateIn(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffectOnce {
        kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(tween(450)) +
                androidx.compose.animation.slideInVertically(
                    tween(450, easing = FastOutSlowInEasing)
                ) { it / 4 },
        modifier = modifier
    ) { content() }
}

@Composable
private fun LaunchedEffectOnce(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
