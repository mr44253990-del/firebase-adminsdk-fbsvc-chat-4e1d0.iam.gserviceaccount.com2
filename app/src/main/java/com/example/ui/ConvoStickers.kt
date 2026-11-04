package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How a sticker moves while it is on screen. */
enum class StickerMotion { Pulse, Bounce, Flicker, Spin, Wiggle, Twinkle, Float }

/**
 * Convo's own animated reaction stickers. They travel as plain text tokens such as
 * `:convo_heart:` so no new database fields are needed; older app versions simply show
 * the readable token, and [convoStickerPreview] gives chat lists and notifications a label.
 */
@Immutable
data class ConvoSticker(
    val token: String,
    val label: String,
    val fallbackEmoji: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val motion: StickerMotion,
    val periodMs: Int = 1400,
    val sparkles: Boolean = false
)

object ConvoStickers {
    val all: List<ConvoSticker> = listOf(
        ConvoSticker(":convo_heart:", "Love", "❤️", Icons.Rounded.Favorite, listOf(Color(0xFFFF8FB8), Color(0xFFFF2D6F), Color(0xFFC2185B)), StickerMotion.Pulse, 1100, sparkles = true),
        ConvoSticker(":convo_like:", "Like", "👍", Icons.Rounded.ThumbUp, listOf(Color(0xFF7DD3FC), Color(0xFF3B82F6), Color(0xFF6D28D9)), StickerMotion.Bounce, 1200),
        ConvoSticker(":convo_fire:", "Fire", "🔥", Icons.Rounded.LocalFireDepartment, listOf(Color(0xFFFFF176), Color(0xFFFF9800), Color(0xFFFF3D00)), StickerMotion.Flicker, 700),
        ConvoSticker(":convo_star:", "Star", "⭐", Icons.Rounded.Star, listOf(Color(0xFFFFF59D), Color(0xFFFFC107), Color(0xFFFF8F00)), StickerMotion.Spin, 2600, sparkles = true),
        ConvoSticker(":convo_laugh:", "Haha", "😂", Icons.Rounded.SentimentVerySatisfied, listOf(Color(0xFFFFF59D), Color(0xFFFFCA28), Color(0xFFFF7043)), StickerMotion.Wiggle, 900),
        ConvoSticker(":convo_wow:", "Wow", "✨", Icons.Rounded.AutoAwesome, listOf(Color(0xFFE9D5FF), Color(0xFFA78BFA), Color(0xFF22D3EE)), StickerMotion.Twinkle, 1300, sparkles = true),
        ConvoSticker(":convo_party:", "Party", "🎉", Icons.Rounded.Celebration, listOf(Color(0xFFFDE68A), Color(0xFFF472B6), Color(0xFF8B5CF6)), StickerMotion.Wiggle, 1000, sparkles = true),
        ConvoSticker(":convo_hug:", "Care", "🤗", Icons.Rounded.VolunteerActivism, listOf(Color(0xFFFBCFE8), Color(0xFFF472B6), Color(0xFFE11D48)), StickerMotion.Float, 1800),
        ConvoSticker(":convo_zap:", "Zap", "⚡", Icons.Rounded.Bolt, listOf(Color(0xFFBEF264), Color(0xFF22C55E), Color(0xFF0EA5E9)), StickerMotion.Flicker, 500),
        ConvoSticker(":convo_crown:", "Winner", "🏆", Icons.Rounded.EmojiEvents, listOf(Color(0xFFFFF7AE), Color(0xFFFFD54F), Color(0xFFB8860B)), StickerMotion.Float, 2000, sparkles = true)
    )

    private val byToken = all.associateBy { it.token }

    fun find(token: String?): ConvoSticker? = token?.trim()?.let { byToken[it] }
    fun isSticker(text: String?): Boolean = find(text) != null
}

/** Readable text for chat lists and notifications. */
fun convoStickerPreview(text: String): String =
    ConvoStickers.find(text)?.let { "${it.fallbackEmoji} ${it.label} sticker" } ?: text

/** Draws an animated sticker; unknown tokens fall back to plain text (normal emoji). */
@Composable
fun ConvoStickerView(token: String, stickerSize: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val sticker = ConvoStickers.find(token)
    if (sticker == null) {
        Text(token, fontSize = (stickerSize.value * .62f).sp, modifier = modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "sticker_${sticker.token}")
    val phaseAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(sticker.periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "sticker_phase"
    )
    val phase = if (animate) phaseAnim else 0f
    val wave = sin(phase * 2f * PI.toFloat())
    Box(modifier.size(stickerSize), contentAlignment = Alignment.Center) {
        // Soft colored halo
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = .45f + .2f * abs(wave); scaleX = .9f + .08f * abs(wave); scaleY = scaleX }
                .background(Brush.radialGradient(listOf(sticker.colors[1].copy(alpha = .55f), Color.Transparent)), CircleShape)
        )
        if (sticker.sparkles) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 5) {
                    val angle = (i / 5f + phase) * 2f * PI.toFloat()
                    val dist = size.minDimension * (.40f + .06f * sin((phase * 4 + i) * PI.toFloat()))
                    val r = size.minDimension * (.025f + .018f * abs(sin((phase * 2 + i * .4f) * PI.toFloat())))
                    drawCircle(
                        color = if (i % 2 == 0) Color.White.copy(alpha = .9f) else sticker.colors[0],
                        radius = r,
                        center = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist)
                    )
                }
            }
        }
        Icon(
            imageVector = sticker.icon,
            contentDescription = sticker.label,
            tint = Color.White,
            modifier = Modifier
                .size(stickerSize * .74f)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    when (sticker.motion) {
                        StickerMotion.Pulse -> { val s = 1f + .11f * abs(wave); scaleX = s; scaleY = s }
                        StickerMotion.Bounce -> { translationY = -stickerSize.toPx() * .07f * abs(wave); rotationZ = -10f * wave }
                        StickerMotion.Flicker -> { scaleY = 1f + .07f * wave; scaleX = 1f - .035f * wave; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f) }
                        StickerMotion.Spin -> { rotationZ = 18f * wave; val s = 1f + .06f * abs(wave); scaleX = s; scaleY = s }
                        StickerMotion.Wiggle -> { rotationZ = 12f * wave; translationX = stickerSize.toPx() * .02f * wave }
                        StickerMotion.Twinkle -> { alpha = .78f + .22f * abs(wave); val s = .94f + .1f * abs(wave); scaleX = s; scaleY = s }
                        StickerMotion.Float -> { translationY = stickerSize.toPx() * .05f * wave }
                    }
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(sticker.colors, start = Offset(0f, 0f), end = Offset(size.width, size.height)),
                        blendMode = BlendMode.SrcAtop
                    )
                    // Glossy highlight keeps the sticker feeling 3D
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = .75f), Color.Transparent), center = Offset(size.width * .34f, size.height * .3f), radius = size.minDimension * .28f),
                        radius = size.minDimension * .28f,
                        center = Offset(size.width * .34f, size.height * .3f),
                        blendMode = BlendMode.SrcAtop
                    )
                }
        )
    }
}
