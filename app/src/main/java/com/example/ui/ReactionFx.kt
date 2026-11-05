package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen reaction effects. Each burst runs its own Animatable to completion (keyed on a
 * unique id), so a recomposition, a new message or list scrolling can no longer cut it short.
 */
object ReactionFx {
    enum class Style { FLOAT, BURST, RAIN, HEARTS, FIREWORK, BOUNCE }
    data class Burst(val id: Long, val emoji: String, val style: Style)

    private val _bursts = MutableStateFlow<List<Burst>>(emptyList())
    val bursts: StateFlow<List<Burst>> = _bursts.asStateFlow()
    private var seq = 0L

    fun styleFor(emoji: String): Style = when {
        emoji.contains("❤") || emoji.contains("💖") || emoji.contains("😍") || emoji.contains("🥰") || emoji.contains("💕") -> Style.HEARTS
        emoji.contains("🎉") || emoji.contains("🥳") || emoji.contains("🎊") -> Style.RAIN
        emoji.contains("🔥") || emoji.contains("🚀") || emoji.contains("✨") -> Style.FIREWORK
        emoji.contains("😂") || emoji.contains("🤣") || emoji.contains("😆") -> Style.BOUNCE
        emoji.contains("👍") || emoji.contains("👏") || emoji.contains("💯") -> Style.BURST
        else -> Style.FLOAT
    }

    fun fire(emoji: String, style: Style = styleFor(emoji)) {
        if (emoji.isBlank() || SmartPrefs.reduceMotion.value) return
        val b = Burst(++seq, emoji, style)
        _bursts.value = (_bursts.value + b).takeLast(4)
    }

    internal fun done(id: Long) { _bursts.value = _bursts.value.filterNot { it.id == id } }
}

/** Place once at the top of a screen's root Box. */
@Composable
fun ReactionFxOverlay(modifier: Modifier = Modifier) {
    val bursts by ReactionFx.bursts.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { SmartPrefs.load(ctx) }
    Box(modifier.fillMaxSize()) {
        bursts.forEach { b -> key(b.id) { ReactionBurstLayer(b) } }
    }
}

private data class Particle(val x: Float, val delay: Float, val drift: Float, val scale: Float, val spin: Float, val angle: Float)

@Composable
private fun ReactionBurstLayer(burst: ReactionFx.Burst) {
    val progress = remember { Animatable(0f) }
    val hero = remember { Animatable(0f) }
    val duration = when (burst.style) { ReactionFx.Style.RAIN -> 2200; ReactionFx.Style.FIREWORK -> 1900; else -> 1700 }
    LaunchedEffect(burst.id) {
        launch { hero.animateTo(1f, spring(dampingRatio = .45f, stiffness = 260f)) }
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
        ReactionFx.done(burst.id)
    }
    val particles = remember(burst.id) {
        val r = Random(burst.id)
        List(if (burst.style == ReactionFx.Style.RAIN) 22 else 14) {
            Particle(r.nextFloat(), r.nextFloat() * .35f, r.nextFloat() * 2f - 1f, .7f + r.nextFloat() * .8f, r.nextFloat() * 2f - 1f, (it / 14f) * 2f * PI.toFloat() + r.nextFloat() * .4f)
        }
    }
    val p = progress.value
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        // Sparkle ring for burst / firework styles
        if (burst.style == ReactionFx.Style.BURST || burst.style == ReactionFx.Style.FIREWORK) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height * .55f)
                val ringR = size.minDimension * .45f * p
                drawCircle(Color.White.copy(alpha = (1f - p) * .35f), ringR, c, style = androidx.compose.ui.graphics.drawscope.Stroke(6f * (1f - p) + 1f))
                for (i in 0 until 16) {
                    val a = i / 16f * 2f * PI.toFloat()
                    val d = ringR * (0.8f + (i % 3) * .12f)
                    drawCircle(
                        listOf(Color(0xFFFFD54F), Color(0xFFFF5F6D), Color(0xFF56CCF2), Color(0xFF38EF7D))[i % 4].copy(alpha = 1f - p),
                        6f * (1f - p) + 1.5f, Offset(c.x + cos(a) * d, c.y + sin(a) * d)
                    )
                }
                if (burst.style == ReactionFx.Style.FIREWORK) {
                    for (i in 0 until 10) {
                        val a = i / 10f * 2f * PI.toFloat() + .3f
                        val len = size.minDimension * .18f * p
                        val start = Offset(c.x + cos(a) * ringR * .5f, c.y + sin(a) * ringR * .5f)
                        drawLine(Color(0xFFFFB300).copy(alpha = 1f - p), start, Offset(start.x + cos(a) * len, start.y + sin(a) * len), strokeWidth = 4f)
                    }
                }
            }
        }
        particles.forEach { pt ->
            val local = ((p - pt.delay) / (1f - pt.delay)).coerceIn(0f, 1f)
            if (local > 0f && local < 1f) {
                val (x, y) = when (burst.style) {
                    ReactionFx.Style.RAIN -> Offset(pt.x * w + pt.drift * 40f * sin(local * 6f), -60f + local * (h + 120f)).let { it.x to it.y }
                    ReactionFx.Style.BURST, ReactionFx.Style.FIREWORK -> {
                        val d = (w.coerceAtMost(h)) * .48f * (1f - (1f - local) * (1f - local))
                        (w / 2 + cos(pt.angle) * d) to (h * .55f + sin(pt.angle) * d + local * local * 120f)
                    }
                    ReactionFx.Style.BOUNCE -> {
                        val bx = pt.x * w
                        val bounce = kotlin.math.abs(sin(local * PI.toFloat() * 3f)) * (1f - local) * h * .35f
                        bx to (h * .9f - bounce - local * 60f)
                    }
                    else -> {
                        val sway = sin(local * PI.toFloat() * 2f + pt.drift * 3f) * 50f * pt.drift
                        (w * (.15f + pt.x * .7f) + sway) to (h * (1.0f - local * 1.05f))
                    }
                }
                val alpha = when { local < .12f -> local / .12f; local > .75f -> (1f - local) / .25f; else -> 1f }
                Text(
                    burst.emoji, fontSize = (26 * pt.scale).sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = x - 30f; translationY = y - 30f
                        this.alpha = alpha.coerceIn(0f, 1f)
                        rotationZ = pt.spin * 40f * local
                        val s = if (burst.style == ReactionFx.Style.HEARTS) 1f + .25f * sin(local * 12f) else 1f
                        scaleX = s; scaleY = s
                    }
                )
            }
        }
        // Hero emoji: pops in the centre, then floats up and fades out.
        val heroAlpha = if (p > .7f) ((1f - p) / .3f) else 1f
        Text(
            burst.emoji, fontSize = 96.sp,
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                val s = hero.value * (1f + .1f * sin(p * 10f))
                scaleX = s; scaleY = s
                translationY = -p * p * h * .25f
                rotationZ = if (burst.style == ReactionFx.Style.BOUNCE) sin(p * 18f) * 14f else sin(p * 6f) * 6f
                alpha = heroAlpha.coerceIn(0f, 1f)
            }
        )
    }
}

/** Big-emoji message entrance that always runs to the end (pop + wiggle). */
@Composable
fun Modifier.jumboEmojiEntrance(messageId: String, fresh: Boolean): Modifier {
    val scale = remember(messageId) { Animatable(if (fresh) 0f else 1f) }
    val wiggle = remember(messageId) { Animatable(0f) }
    LaunchedEffect(messageId) {
        if (fresh) {
            launch { scale.animateTo(1f, spring(dampingRatio = .38f, stiffness = 320f)) }
            wiggle.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value; scaleY = scale.value
        rotationZ = if (wiggle.value in 0.001f..0.999f) sin(wiggle.value * 6f * PI.toFloat()) * 12f * (1f - wiggle.value) else 0f
    }
}

