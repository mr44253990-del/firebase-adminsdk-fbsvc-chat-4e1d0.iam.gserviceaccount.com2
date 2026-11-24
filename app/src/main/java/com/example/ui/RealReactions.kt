package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hand-drawn, animated reaction faces (v5.3) — the familiar six: Like, Love, Haha, Wow, Sad and
 * Angry. Everything is vector-drawn on a Canvas, so the faces stay sharp at any size and each
 * one has its own small motion (the thumb nods, the heart beats, Haha laughs, Wow gasps, Sad
 * drops a tear, Angry shakes).
 */
fun reactionLabel(emoji: String): String = when (emoji) {
    "👍" -> "Like"; "❤️" -> "Love"; "😂" -> "Haha"; "😮" -> "Wow"; "😢" -> "Sad"; "😡" -> "Angry"; else -> "Reacted"
}

fun reactionTint(emoji: String): Color = when (emoji) {
    "👍" -> Color(0xFF2D86FF); "❤️" -> Color(0xFFF33E58); "😡" -> Color(0xFFE9710F); else -> Color(0xFFF7B125)
}

private val FACE_YELLOW = listOf(Color(0xFFFFE680), Color(0xFFFFC83D), Color(0xFFF5A300))
private val INK = Color(0xFF3B2A12)

@Composable
fun RealReaction(emoji: String, size: Dp, animate: Boolean = true, modifier: Modifier = Modifier) {
    if (emoji !in setOf("👍", "❤️", "😂", "😮", "😢", "😡")) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) { Text(emoji, fontSize = (size.value * .72f).sp) }
        return
    }
    val t = if (animate) {
        val motion = rememberInfiniteTransition(label = "real_reaction")
        motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t").value
    } else 0f
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        val wave = sin(t * 2f * PI.toFloat())
        when (emoji) {
            "👍" -> {
                disc(listOf(Color(0xFF5AB0FF), Color(0xFF2D86FF), Color(0xFF1463E6)), c, r)
                rotate(-10f * wave, c) { thumb(c, r) }
            }
            "❤️" -> {
                disc(listOf(Color(0xFFFF7A8A), Color(0xFFF33E58), Color(0xFFD81B45)), c, r)
                val beat = 1f + .12f * (if (wave > 0f) wave else 0f)
                heart(c, r * .52f * beat, Color.White)
            }
            "😂" -> {
                translate(top = -r * .06f * (if (wave > 0) wave else -wave)) {
                    rotate(6f * wave, c) {
                        disc(FACE_YELLOW, c, r)
                        // Squeezed-shut happy eyes.
                        arcStroke(c + Offset(-r * .38f, -r * .18f), r * .20f, 200f, 140f, INK, r * .09f)
                        arcStroke(c + Offset(r * .38f, -r * .18f), r * .20f, 200f, 140f, INK, r * .09f)
                        // Wide open laughing mouth with teeth and tongue.
                        val mouth = Rect(c.x - r * .52f, c.y - r * .28f, c.x + r * .52f, c.y + r * .68f)
                        drawArc(Color(0xFF5A1A12), 0f, 180f, true, mouth.topLeft, mouth.size)
                        drawRoundRect(Color.White, Offset(c.x - r * .40f, c.y + r * .20f), Size(r * .80f, r * .12f), CornerRadius(r * .05f))
                        drawArc(Color(0xFFFF6B81), 20f, 140f, true, Offset(c.x - r * .30f, c.y + r * .30f), Size(r * .60f, r * .40f))
                        // Tears of joy.
                        drawCircle(Color(0xFF5BC8FF), r * .10f, c + Offset(-r * .70f, r * .02f + r * .05f * wave))
                        drawCircle(Color(0xFF5BC8FF), r * .10f, c + Offset(r * .70f, r * .02f - r * .05f * wave))
                    }
                }
            }
            "😮" -> {
                disc(FACE_YELLOW, c, r)
                val gasp = 1f + .14f * wave
                drawOval(INK, Offset(c.x - r * .40f - r * .08f, c.y - r * .42f), Size(r * .17f, r * .26f))
                drawOval(INK, Offset(c.x + r * .40f - r * .09f, c.y - r * .42f), Size(r * .17f, r * .26f))
                arcStroke(c + Offset(-r * .36f, -r * .58f - r * .04f * wave), r * .16f, 200f, 140f, INK, r * .07f)
                arcStroke(c + Offset(r * .36f, -r * .58f - r * .04f * wave), r * .16f, 200f, 140f, INK, r * .07f)
                drawOval(Color(0xFF5A1A12), Offset(c.x - r * .22f * gasp, c.y + r * .08f), Size(r * .44f * gasp, r * .56f * gasp))
            }
            "😢" -> {
                disc(FACE_YELLOW, c, r)
                drawOval(INK, Offset(c.x - r * .44f, c.y - r * .22f), Size(r * .16f, r * .22f))
                drawOval(INK, Offset(c.x + r * .28f, c.y - r * .22f), Size(r * .16f, r * .22f))
                // Worried brows.
                drawLine(INK, c + Offset(-r * .56f, -r * .30f), c + Offset(-r * .26f, -r * .44f), r * .08f, StrokeCap.Round)
                drawLine(INK, c + Offset(r * .56f, -r * .30f), c + Offset(r * .26f, -r * .44f), r * .08f, StrokeCap.Round)
                arcStroke(c + Offset(0f, r * .58f), r * .26f, 205f, 130f, INK, r * .08f)
                // Falling tear.
                val fall = (t * 1.3f) % 1f
                val tear = c + Offset(-r * .36f, r * .06f + r * .62f * fall)
                drawCircle(Color(0xFF4DB8FF).copy(alpha = 1f - fall * .7f), r * .12f, tear)
                drawPath(Path().apply { moveTo(tear.x, tear.y - r * .24f); lineTo(tear.x - r * .10f, tear.y - r * .05f); lineTo(tear.x + r * .10f, tear.y - r * .05f); close() }, Color(0xFF4DB8FF).copy(alpha = 1f - fall * .7f))
            }
            "😡" -> {
                translate(left = r * .05f * sin(t * 12f * PI.toFloat())) {
                    disc(listOf(Color(0xFFFFB347), Color(0xFFF2681A), Color(0xFFD93A1B)), c, r)
                    drawLine(INK, c + Offset(-r * .62f, -r * .36f), c + Offset(-r * .18f, -r * .16f), r * .10f, StrokeCap.Round)
                    drawLine(INK, c + Offset(r * .62f, -r * .36f), c + Offset(r * .18f, -r * .16f), r * .10f, StrokeCap.Round)
                    drawOval(INK, Offset(c.x - r * .44f, c.y - r * .12f), Size(r * .18f, r * .16f))
                    drawOval(INK, Offset(c.x + r * .26f, c.y - r * .12f), Size(r * .18f, r * .16f))
                    drawRoundRect(INK, Offset(c.x - r * .26f, c.y + r * .34f), Size(r * .52f, r * .09f), CornerRadius(r * .05f))
                }
            }
        }
    }
}

/** Glossy ball: body gradient, soft floor shadow and a light highlight for a 3-D look. */
private fun DrawScope.disc(colors: List<Color>, c: Offset, r: Float) {
    drawOval(Color.Black.copy(alpha = .14f), Offset(c.x - r * .7f, c.y + r * .86f), Size(r * 1.4f, r * .20f))
    drawCircle(Brush.radialGradient(colors, Offset(c.x - r * .25f, c.y - r * .35f), r * 1.5f), r, c)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent), Offset(c.x - r * .32f, c.y - r * .45f), r * .7f), r * .7f, Offset(c.x - r * .32f, c.y - r * .45f))
}

private fun DrawScope.arcStroke(centre: Offset, radius: Float, start: Float, sweep: Float, color: Color, width: Float) {
    drawArc(color, start, sweep, false, Offset(centre.x - radius, centre.y - radius), Size(radius * 2, radius * 2), style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * .95f)
        cubicTo(c.x - s * 1.55f, c.y - s * .05f, c.x - s * .75f, c.y - s * 1.25f, c.x, c.y - s * .40f)
        cubicTo(c.x + s * .75f, c.y - s * 1.25f, c.x + s * 1.55f, c.y - s * .05f, c.x, c.y + s * .95f)
        close()
    }
    drawPath(path, color)
}

/** White thumbs-up: a rounded fist, a raised thumb and a cuff. */
private fun DrawScope.thumb(c: Offset, r: Float) {
    val white = Color.White
    // Cuff
    drawRoundRect(white, Offset(c.x - r * .56f, c.y - r * .04f), Size(r * .22f, r * .52f), CornerRadius(r * .05f))
    // Fist
    drawRoundRect(white, Offset(c.x - r * .26f, c.y - r * .08f), Size(r * .76f, r * .56f), CornerRadius(r * .16f))
    // Thumb
    val thumb = Path().apply {
        moveTo(c.x - r * .22f, c.y - r * .02f)
        cubicTo(c.x - r * .10f, c.y - r * .22f, c.x - r * .02f, c.y - r * .36f, c.x - r * .02f, c.y - r * .52f)
        cubicTo(c.x - r * .02f, c.y - r * .66f, c.x + r * .22f, c.y - r * .66f, c.x + r * .24f, c.y - r * .46f)
        cubicTo(c.x + r * .25f, c.y - r * .30f, c.x + r * .20f, c.y - r * .16f, c.x + r * .16f, c.y - r * .02f)
        close()
    }
    drawPath(thumb, white)
    // Finger creases
    repeat(2) { i ->
        val y = c.y + r * (.10f + i * .16f)
        drawLine(Color(0xFF2D86FF).copy(alpha = .55f), Offset(c.x + r * .18f, y), Offset(c.x + r * .48f, y), r * .04f, StrokeCap.Round)
    }
}

/** One choice in the reaction tray: pops in, grows when pressed and shows its name. */
@Composable
fun RealReactionChoice(emoji: String, index: Int, onClick: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(45L * index); enter.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f)) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val grow by animateFloatAsState(if (pressed) 1.45f else 1f, spring(dampingRatio = .5f, stiffness = 500f), label = "reaction_grow")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                val s = enter.value.coerceAtLeast(0f) * grow
                scaleX = s; scaleY = s
                translationY = (1f - enter.value) * 30f - (grow - 1f) * 26f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        RealReaction(emoji, 42.dp)
        Spacer(Modifier.height(2.dp))
        Text(reactionLabel(emoji), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = reactionTint(emoji))
    }
}

/** Small overlapping reaction badges for the "who reacted" summary. */
@Composable
fun RealReactionStack(emojis: List<String>, size: Dp = 20.dp) {
    Box(Modifier.size(width = size + (size * .62f) * (emojis.size - 1).coerceAtLeast(0), height = size)) {
        emojis.forEachIndexed { i, emoji ->
            Box(
                Modifier.graphicsLayer { translationX = (size.toPx() * .62f) * i }
                    .size(size)
                    .then(Modifier.background(MaterialTheme.colorScheme.background, androidx.compose.foundation.shape.CircleShape))
                    .then(Modifier.padding(1.5.dp))
            ) { RealReaction(emoji, size - 3.dp, animate = false) }
        }
    }
}
