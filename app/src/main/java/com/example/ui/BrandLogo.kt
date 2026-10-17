package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Brand colours of the Convo mark (cyan → blue → violet → pink). */
object ConvoBrand {
    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF3B5BFF)
    val Violet = Color(0xFF8B3DFF)
    val Pink = Color(0xFFFF3D9A)
    val Ink = Color(0xFF1B1446)
    val gradient = listOf(Cyan, Blue, Violet, Pink)
}

/**
 * The Convo logo drawn entirely in code: a gradient "C" ring wrapped around a white chat
 * bubble with three dots. Vector-sharp at any size, no bitmap, works on any background.
 * When [animated] the dots bounce like a typing indicator and the ring shimmers.
 */
@Composable
fun ConvoLogo(modifier: Modifier = Modifier, size: Dp = 96.dp, animated: Boolean = true, bubbleColor: Color = Color.White) {
    val t = rememberInfiniteTransition(label = "convo_logo")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "logo_phase")
    val shimmer by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "logo_shimmer")
    Canvas(modifier.size(size)) {
        drawConvoMark(if (animated) phase else -1f, if (animated) shimmer else 0f, bubbleColor)
    }
}

fun DrawScope.drawConvoMark(dotPhase: Float, shimmerDeg: Float, bubbleColor: Color = Color.White) {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val ringStroke = s * 0.15f
    val ringRadius = s * 0.40f
    val topLeft = Offset(c.x - ringRadius, c.y - ringRadius)
    val ringSize = Size(ringRadius * 2, ringRadius * 2)
    // Soft shadow under the ring
    drawArc(
        color = Color.Black.copy(alpha = .18f), startAngle = 48f, sweepAngle = 264f, useCenter = false,
        topLeft = topLeft + Offset(0f, s * 0.025f), size = ringSize, style = Stroke(ringStroke, cap = StrokeCap.Round)
    )
    // Gradient "C" — open on the right side like the brand mark
    rotate(shimmerDeg * 0.05f, c) {
        drawArc(
            brush = Brush.sweepGradient(
                0.0f to ConvoBrand.Pink, 0.15f to ConvoBrand.Pink, 0.35f to ConvoBrand.Violet,
                0.55f to ConvoBrand.Blue, 0.72f to ConvoBrand.Cyan, 0.86f to ConvoBrand.Violet, 1f to ConvoBrand.Pink,
                center = c
            ),
            startAngle = 48f, sweepAngle = 264f, useCenter = false,
            topLeft = topLeft, size = ringSize, style = Stroke(ringStroke, cap = StrokeCap.Round)
        )
    }
    // Glossy highlight on the upper-left of the ring
    drawArc(
        color = Color.White.copy(alpha = .28f), startAngle = 160f, sweepAngle = 70f, useCenter = false,
        topLeft = topLeft + Offset(ringStroke * .18f, ringStroke * .18f),
        size = Size(ringSize.width - ringStroke * .36f, ringSize.height - ringStroke * .36f),
        style = Stroke(ringStroke * .22f, cap = StrokeCap.Round)
    )
    // Chat bubble with a tail at the lower-right
    val bubbleR = s * 0.21f
    val bc = Offset(c.x + s * 0.01f, c.y - s * 0.01f)
    val tail = Path().apply {
        moveTo(bc.x + bubbleR * 0.25f, bc.y + bubbleR * 0.80f)
        lineTo(bc.x + bubbleR * 0.05f, bc.y + bubbleR * 1.38f)
        lineTo(bc.x + bubbleR * 0.78f, bc.y + bubbleR * 0.55f)
        close()
    }
    drawCircle(Color.Black.copy(alpha = .14f), bubbleR, bc + Offset(0f, s * 0.02f))
    drawPath(tail, bubbleColor)
    drawCircle(bubbleColor, bubbleR, bc)
    // Three dots (typing bounce when animated)
    val dotR = s * 0.032f
    for (i in 0..2) {
        val x = bc.x + (i - 1) * dotR * 3.1f
        val lift = if (dotPhase < 0f) 0f else {
            val local = ((dotPhase - i * 0.16f) % 1f + 1f) % 1f
            if (local < 0.4f) sin(local / 0.4f * PI).toFloat() * dotR * 0.9f else 0f
        }
        drawCircle(ConvoBrand.Ink, dotR, Offset(x, bc.y - lift))
    }
}

/** Logo + "Convo" wordmark + spaced "CHAT" label with gradient rules. */
@Composable
fun ConvoWordmark(textColor: Color, modifier: Modifier = Modifier, logoSize: Dp = 92.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ConvoLogo(size = logoSize)
        Spacer(Modifier.height(10.dp))
        Text(
            "Convo",
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(textColor, textColor.copy(alpha = .86f))),
                fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(26.dp).height(2.dp)) { drawRoundRect(ConvoBrand.Cyan, cornerRadius = CornerRadius(4f)) }
            Spacer(Modifier.width(8.dp))
            Text(
                "CHAT",
                style = TextStyle(brush = Brush.horizontalGradient(listOf(ConvoBrand.Blue, ConvoBrand.Violet, ConvoBrand.Pink)), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            )
            Spacer(Modifier.width(2.dp))
            Canvas(Modifier.width(26.dp).height(2.dp)) { drawRoundRect(ConvoBrand.Pink, cornerRadius = CornerRadius(4f)) }
        }
    }
}

/**
 * Calm animated aurora background for the login screen: slow drifting colour fields,
 * a fine dot grid and a soft vignette. No tilt/3D — just depth through light.
 */
@Composable
fun LoginAuroraBackground(milk: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "aurora_a")
    val breathe by t.animateFloat(.85f, 1.1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "aurora_b")
    val base = if (milk) listOf(Color(0xFFFFFCF7), Color(0xFFF4F1FF), Color(0xFFEFF6FF)) else listOf(Color(0xFF070614), Color(0xFF0D0B26), Color(0xFF120A2A))
    val fields = if (milk) listOf(Color(0xFFB8C4FF), Color(0xFFFFC2DC), Color(0xFFAEE9FF), Color(0xFFFFD9B8))
    else listOf(ConvoBrand.Blue, ConvoBrand.Pink, ConvoBrand.Cyan, ConvoBrand.Violet)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(base))
            val w = size.width
            val h = size.height
            val alpha = if (milk) .55f else .38f
            fields.forEachIndexed { i, col ->
                val ang = a + i * (PI / 2).toFloat()
                val cx = w * (0.5f + 0.34f * cos(ang * (if (i % 2 == 0) 1f else -0.7f)))
                val cy = h * (0.18f + 0.22f * i + 0.06f * sin(ang * 1.3f))
                val r = w * (0.55f + 0.08f * i) * breathe
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
            }
            // Fine dot grid for texture
            val step = 22.dp.toPx()
            val dot = if (milk) Color(0x14000000) else Color(0x12FFFFFF)
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) { drawCircle(dot, 1.1f, Offset(x, y)); x += step }
                y += step
            }
            // Vignette
            drawRect(Brush.radialGradient(listOf(Color.Transparent, (if (milk) Color(0x22FFFFFF) else Color(0x99000000))), Offset(w / 2, h * .42f), maxOf(w, h) * .8f))
        }
    }
}

/** Hero block for the login page: glowing logo with a slow orbit ring and floating sparks. */
@Composable
fun LoginBrandHero(textColor: Color, milk: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "hero")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "hero_spin")
    val glow by t.animateFloat(.55f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hero_glow")
    val float by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hero_float")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(ConvoBrand.Violet.copy(alpha = .45f * glow), Color.Transparent), c, r), r, c)
                rotate(spin, c) {
                    drawCircle(
                        Brush.sweepGradient(listOf(Color.Transparent, ConvoBrand.Cyan.copy(alpha = .9f), Color.Transparent, ConvoBrand.Pink.copy(alpha = .9f), Color.Transparent), c),
                        r * .86f, c, style = Stroke(1.6.dp.toPx())
                    )
                    drawCircle(ConvoBrand.Cyan, 3.dp.toPx(), Offset(c.x, c.y - r * .86f))
                    drawCircle(ConvoBrand.Pink, 2.4.dp.toPx(), Offset(c.x, c.y + r * .86f))
                }
                // Sparks
                for (i in 0 until 6) {
                    val ang = (spin * (if (i % 2 == 0) 1f else -1f) / 180f * PI + i * PI / 3).toFloat()
                    val rr = r * (.64f + (i % 3) * .1f)
                    drawCircle(Color.White.copy(alpha = if (milk) .9f else .55f), (1.2f + i % 2).dp.toPx(), Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr))
                }
            }
            ConvoLogo(Modifier.offset(y = (float * 4).dp), size = 96.dp)
        }
        Text("Convo", style = TextStyle(color = textColor, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(22.dp).height(2.dp)) { drawRoundRect(ConvoBrand.Cyan, cornerRadius = CornerRadius(4f)) }
            Spacer(Modifier.width(8.dp))
            Text("CHAT", style = TextStyle(brush = Brush.horizontalGradient(listOf(ConvoBrand.Blue, ConvoBrand.Violet, ConvoBrand.Pink)), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp))
            Spacer(Modifier.width(2.dp))
            Canvas(Modifier.width(22.dp).height(2.dp)) { drawRoundRect(ConvoBrand.Pink, cornerRadius = CornerRadius(4f)) }
        }
    }
}
