package com.example.ui

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * "Dark liquid glass" design kit (v5.6) — deep black, flowing violet / electric-blue glass
 * ribbons with bright edges, floating glass beads, a glossy app tile and frosted cards.
 * Everything is drawn in code, so it stays sharp on every screen and animates slowly.
 */
val LiquidViolet = Color(0xFF9B4DFF)
val LiquidBlue = Color(0xFF2F7BFF)
val LiquidCyan = Color(0xFF39C8FF)
val LiquidPink = Color(0xFFFF3D9A)
val LiquidInk = Color(0xFF04030B)

/** One glass ribbon: a soft filled body plus a bright rim along its leading edge. */
private fun DrawScope.ribbon(body: Path, edge: Path, fill: Brush, rim: Brush, rimWidth: Float) {
    drawPath(body, fill)
    drawPath(edge, rim, style = Stroke(rimWidth * 5f, cap = StrokeCap.Round), alpha = .16f)
    drawPath(edge, rim, style = Stroke(rimWidth * 2.2f, cap = StrokeCap.Round), alpha = .35f)
    drawPath(edge, rim, style = Stroke(rimWidth, cap = StrokeCap.Round))
}

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    violet: Color = LiquidViolet,
    blue: Color = LiquidBlue,
    /** 1 = solid black base (splash, sign-in); lower lets a screen's own colour show through. */
    baseAlpha: Float = 1f,
    beads: Boolean = true
) {
    val motion = rememberInfiniteTransition(label = "liquid")
    val t by motion.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "liquid_t")
    val bob by motion.animateFloat(-1f, 1f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "liquid_bob")
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        if (w < 2f || h < 2f) return@Canvas
        if (baseAlpha > 0f) drawRect(LiquidInk.copy(alpha = baseAlpha))
        fun sx(v: Float, k: Float) = w * (v + .018f * sin(t + k))
        fun sy(v: Float, k: Float) = h * (v + .014f * sin(t * 1.3f + k))
        val rimW = 1.6.dp.toPx()

        // Deep colour pools behind the glass.
        drawCircle(Brush.radialGradient(listOf(violet.copy(alpha = .30f), Color.Transparent), Offset(w * .08f, h * .10f), w * .9f), w * .9f, Offset(w * .08f, h * .10f))
        drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = .26f), Color.Transparent), Offset(w * .98f, h * .78f), w * .95f), w * .95f, Offset(w * .98f, h * .78f))

        // 1 — top-left fold
        run {
            val edge = Path().apply {
                moveTo(-w * .1f, sy(.30f, 0f))
                cubicTo(sx(.28f, 1f), sy(.27f, 2f), sx(.30f, 3f), sy(.07f, 4f), w * .66f, -h * .04f)
            }
            val body = Path().apply { addPath(edge); lineTo(-w * .1f, -h * .04f); close() }
            ribbon(body, edge,
                Brush.linearGradient(listOf(violet.copy(alpha = .55f), Color(0xFF1B0B45).copy(alpha = .25f), Color.Transparent), Offset(0f, 0f), Offset(w * .6f, h * .3f)),
                Brush.linearGradient(listOf(violet, blue.copy(alpha = .9f), Color.Transparent), Offset(0f, h * .3f), Offset(w * .66f, 0f)), rimW)
        }
        // 2 — right side fold
        run {
            val edge = Path().apply {
                moveTo(w * 1.1f, sy(.33f, 2f))
                cubicTo(sx(.78f, 3f), sy(.38f, 1f), sx(.98f, 5f), sy(.52f, 2f), sx(.72f, 4f), sy(.60f, 3f))
                cubicTo(sx(.60f, 1f), sy(.64f, 5f), sx(.86f, 2f), sy(.70f, 4f), w * 1.1f, sy(.67f, 1f))
            }
            val body = Path().apply { addPath(edge); close() }
            ribbon(body, edge,
                Brush.linearGradient(listOf(Color.Transparent, blue.copy(alpha = .30f), Color(0xFF0A1040).copy(alpha = .5f)), Offset(w * .6f, h * .3f), Offset(w, h * .7f)),
                Brush.linearGradient(listOf(Color.Transparent, LiquidCyan, blue, Color.Transparent), Offset(w, h * .33f), Offset(w * .7f, h * .68f)), rimW)
        }
        // 3 — large bottom wave
        run {
            val edge = Path().apply {
                moveTo(-w * .1f, sy(.74f, 4f))
                cubicTo(sx(.24f, 2f), sy(.67f, 3f), sx(.44f, 1f), sy(.87f, 5f), sx(.74f, 3f), sy(.81f, 2f))
                cubicTo(sx(.92f, 5f), sy(.77f, 1f), sx(1.0f, 2f), sy(.90f, 3f), w * 1.1f, sy(.86f, 4f))
            }
            val body = Path().apply { addPath(edge); lineTo(w * 1.1f, h * 1.1f); lineTo(-w * .1f, h * 1.1f); close() }
            ribbon(body, edge,
                Brush.verticalGradient(listOf(violet.copy(alpha = .50f), Color(0xFF12063A).copy(alpha = .75f), LiquidInk), h * .68f, h),
                Brush.linearGradient(listOf(violet, LiquidPink.copy(alpha = .7f), blue, LiquidCyan), Offset(0f, h * .74f), Offset(w, h * .86f)), rimW)
        }
        // 4 — thin light arcs, bottom-right
        repeat(3) { i ->
            val arc = Path().apply {
                moveTo(w * (.52f + i * .08f), h * 1.04f)
                cubicTo(sx(.70f + i * .05f, i.toFloat()), sy(.96f - i * .03f, i + 1f), sx(.88f, i + 2f), sy(.84f - i * .04f, i.toFloat()), w * 1.08f, sy(.72f - i * .05f, i + 3f))
            }
            drawPath(arc, Brush.linearGradient(listOf(Color.Transparent, blue.copy(alpha = .8f - i * .2f), LiquidCyan.copy(alpha = .6f - i * .15f)), Offset(w * .5f, h), Offset(w, h * .7f)), style = Stroke(rimW * (1.3f - i * .25f), cap = StrokeCap.Round))
        }
        // Floating glass beads.
        if (beads) listOf(Triple(.90f, .12f, 13f), Triple(.08f, .26f, 12f), Triple(.10f, .44f, 17f), Triple(.93f, .17f, 9f)).forEachIndexed { i, (bx, by, br) ->
            val c = Offset(w * bx, h * by + bob * (6f + i * 3f))
            val r = br.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1050), Color(0xFF05030F)), c, r), r, c)
            drawCircle(Brush.sweepGradient(listOf(blue, violet, Color.Transparent, blue), c), r, c, style = Stroke(1.2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = .55f), r * .16f, Offset(c.x - r * .35f, c.y - r * .38f))
        }
    }
}

/** Frosted dark-glass surface with a lit rim. */
fun Modifier.liquidGlass(shape: Shape, tint: Color = LiquidViolet, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .09f * strength), Color(0xFF0B0920).copy(alpha = .62f), Color(0xFF07061A).copy(alpha = .78f))))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .38f), tint.copy(alpha = .45f), LiquidBlue.copy(alpha = .30f), Color.White.copy(alpha = .10f))), shape)

/** The glossy rounded app tile with the Convo mark, a floor reflection and an optional orbit ring. */
@Composable
fun LiquidLogoTile(size: Dp = 156.dp, orbit: Boolean = false, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "tile")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(11_000, easing = LinearEasing)), label = "tile_spin")
    val breathe by motion.animateFloat(.97f, 1.03f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tile_breathe")
    val shape = RoundedCornerShape(size * .30f)
    Box(modifier.size(size * 1.55f, size * 1.38f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            // Glow under the tile + soft reflection on the "floor".
            drawCircle(Brush.radialGradient(listOf(LiquidViolet.copy(alpha = .45f), LiquidBlue.copy(alpha = .18f), Color.Transparent), c, this.size.minDimension * .62f), this.size.minDimension * .62f, c)
            drawOval(Brush.radialGradient(listOf(LiquidBlue.copy(alpha = .38f), LiquidViolet.copy(alpha = .12f), Color.Transparent), Offset(c.x, c.y + size.toPx() * .55f), size.toPx() * .50f),
                Offset(c.x - size.toPx() * .50f, c.y + size.toPx() * .51f), Size(size.toPx() * 1.0f, size.toPx() * .08f))
            if (orbit) rotate(-18f, c) {
                // Tilted orbit ring passing behind / in front of the tile.
                drawOval(Brush.sweepGradient(listOf(LiquidViolet, LiquidBlue, LiquidCyan, Color.Transparent, LiquidViolet), c),
                    Offset(c.x - size.toPx() * .74f, c.y - size.toPx() * .30f), Size(size.toPx() * 1.48f, size.toPx() * .60f), style = Stroke(2.2.dp.toPx()))
                val a = spin * (PI.toFloat() / 180f)
                drawCircle(Color.White, 3.dp.toPx(), Offset(c.x + kotlin.math.cos(a) * size.toPx() * .74f, c.y + sin(a) * size.toPx() * .30f))
            }
        }
        Box(
            Modifier.size(size).graphicsLayer { scaleX = breathe; scaleY = breathe }
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF2A1A66), Color(0xFF07061A), Color(0xFF0B1240))))
                .border(2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .85f), LiquidViolet, LiquidBlue, Color.White.copy(alpha = .35f))), shape),
            contentAlignment = Alignment.Center
        ) {
            ConvoLogo(Modifier.padding(size * .14f).fillMaxSize(), size = size * .72f)
            // Specular highlight across the top of the glass.
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = size * .05f).fillMaxWidth(.86f).height(size * .30f)
                    .clip(RoundedCornerShape(size * .24f))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent)))
            )
        }
    }
}

/** "Convo Chat" — white first word, violet-to-blue second word. */
@Composable
fun LiquidWordmark(fontSize: Int = 40, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("Convo ", style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFC9CCE0))), fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp))
        Text("Chat", style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFB04DFF), LiquidViolet, LiquidBlue)), fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp))
    }
}

/** Animated voice waveform flanked by two fading lines (divider under the wordmark, call status). */
@Composable
fun LiquidWaveform(modifier: Modifier = Modifier, bars: Int = 9, lines: Boolean = true, active: Boolean = true) {
    val t by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "wave_t")
    Canvas(modifier.height(30.dp).width(if (lines) 260.dp else 120.dp)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val gap = 6.dp.toPx(); val bw = 3.dp.toPx()
        val span = (bars - 1) * gap
        for (i in 0 until bars) {
            val mid = 1f - kotlin.math.abs(i - (bars - 1) / 2f) / ((bars - 1) / 2f)
            val amp = (.25f + .75f * mid) * (if (active) (.55f + .45f * sin(t + i * .9f)) else .35f)
            val hgt = size.height * amp.coerceIn(.12f, 1f)
            val x = cx - span / 2f + i * gap
            drawRoundRect(
                Brush.verticalGradient(listOf(LiquidViolet, LiquidBlue), cy - hgt / 2f, cy + hgt / 2f),
                Offset(x - bw / 2f, cy - hgt / 2f), Size(bw, hgt), CornerRadius(bw, bw)
            )
        }
        if (lines) {
            val start = cx - span / 2f - 14.dp.toPx(); val end = cx + span / 2f + 14.dp.toPx()
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, LiquidBlue), 0f, start), Offset(0f, cy), Offset(start, cy), 1.4.dp.toPx(), StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(LiquidViolet, Color.Transparent), end, size.width), Offset(end, cy), Offset(size.width, cy), 1.4.dp.toPx(), StrokeCap.Round)
        }
    }
}
