package com.example.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight vector textures shared by chat themes and call skins. Every pattern is drawn
 * from a fixed seed so it is stable between frames and costs no image downloads.
 */
object ThemeTextures {
    val all = listOf(
        "none", "glow", "paper", "grid", "petals", "rain", "stars", "waves", "bokeh", "hex",
        "dots", "diagonal", "hearts", "circuit", "confetti", "rings", "sparkle", "aurora",
        "leaves", "bubbles", "zigzag", "diamonds"
    )
}

private class Seeded(seed: Long) {
    private val random = java.util.Random(seed)
    fun f() = random.nextFloat()
}

fun DrawScope.drawThemeTexture(texture: String, tint: Color = Color.White, intensity: Float = 1f) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val k = intensity.coerceIn(0f, 2f)
    fun c(alpha: Float) = tint.copy(alpha = (alpha * k).coerceIn(0f, 1f))
    when (texture) {
        "glow" -> {
            drawCircle(Color(0xFF8BE9FD).copy(alpha = .10f * k), radius = w * .34f, center = Offset(w * .12f, h * .16f))
            drawCircle(Color(0xFFFF79C6).copy(alpha = .08f * k), radius = w * .28f, center = Offset(w * .90f, h * .72f))
        }
        "paper" -> for (i in 0..18) {
            val y = h * (i / 18f)
            drawLine(Color(0xFF7B4B2A).copy(alpha = .07f * k), Offset(0f, y), Offset(w, y + 18f), strokeWidth = 1.2f)
        }
        "grid" -> {
            val step = 42f
            var x = 0f
            while (x < w) { drawLine(c(.05f), Offset(x, 0f), Offset(x, h), 1f); x += step }
            var y = 0f
            while (y < h) { drawLine(c(.05f), Offset(0f, y), Offset(w, y), 1f); y += step }
        }
        "petals" -> {
            val r = Seeded(7)
            repeat(14) {
                val cx = w * r.f(); val cy = h * r.f(); val s = 14f + r.f() * 20f
                drawCircle(Color(0xFFFFC3D7).copy(alpha = .16f * k), radius = s, center = Offset(cx, cy))
                drawCircle(Color(0xFFFFE6EF).copy(alpha = .18f * k), radius = s * .35f, center = Offset(cx + s * .5f, cy - s * .3f))
            }
        }
        "rain" -> {
            val r = Seeded(11)
            repeat(70) {
                val x = w * r.f(); val y = h * r.f(); val len = 18f + r.f() * 30f
                drawLine(c(.09f), Offset(x, y), Offset(x - len * .35f, y + len), strokeWidth = 1.3f)
            }
        }
        "stars" -> {
            val r = Seeded(3)
            repeat(80) {
                val radius = .7f + r.f() * 1.9f
                drawCircle(c(.20f + r.f() * .45f), radius = radius, center = Offset(w * r.f(), h * r.f()))
            }
        }
        "sparkle" -> {
            val r = Seeded(5)
            repeat(26) {
                val cx = w * r.f(); val cy = h * r.f(); val s = 4f + r.f() * 9f
                drawLine(c(.30f), Offset(cx - s, cy), Offset(cx + s, cy), 1.4f)
                drawLine(c(.30f), Offset(cx, cy - s), Offset(cx, cy + s), 1.4f)
                drawCircle(c(.45f), radius = 1.6f, center = Offset(cx, cy))
            }
        }
        "waves" -> for (row in 0..9) {
            val baseY = h * (row / 9f)
            val path = Path().apply {
                moveTo(0f, baseY)
                var x = 0f
                while (x <= w) {
                    lineTo(x, baseY + sin((x / w) * 4f * PI.toFloat() + row) * 14f)
                    x += 12f
                }
            }
            drawPath(path, c(.07f), style = Stroke(width = 1.6f))
        }
        "bokeh" -> {
            val r = Seeded(19)
            repeat(18) {
                val radius = 18f + r.f() * min(w, h) * .11f
                drawCircle(c(.05f + r.f() * .07f), radius = radius, center = Offset(w * r.f(), h * r.f()))
            }
        }
        "bubbles" -> {
            val r = Seeded(23)
            repeat(22) {
                val radius = 8f + r.f() * 26f
                drawCircle(c(.14f), radius = radius, center = Offset(w * r.f(), h * r.f()), style = Stroke(width = 1.5f))
            }
        }
        "hex" -> {
            val s = 30f
            val dx = s * 1.732f
            var row = 0
            var y = 0f
            while (y < h + s) {
                var x = if (row % 2 == 0) 0f else dx / 2f
                while (x < w + dx) {
                    val path = Path()
                    for (i in 0..5) {
                        val a = (PI / 3 * i + PI / 6).toFloat()
                        val px = x + s * cos(a); val py = y + s * sin(a)
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, c(.06f), style = Stroke(width = 1.2f))
                    x += dx
                }
                y += s * 1.5f; row++
            }
        }
        "dots" -> {
            val step = 28f
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) { drawCircle(c(.08f), radius = 1.8f, center = Offset(x, y)); x += step }
                y += step
            }
        }
        "diagonal" -> {
            var x = -h
            while (x < w) { drawLine(c(.05f), Offset(x, 0f), Offset(x + h, h), 1.4f); x += 34f }
        }
        "zigzag" -> for (row in 0..14) {
            val y = h * row / 14f
            val path = Path().apply {
                moveTo(0f, y)
                var x = 0f
                var up = true
                while (x <= w) { x += 22f; lineTo(x, if (up) y - 10f else y); up = !up }
            }
            drawPath(path, c(.07f), style = Stroke(width = 1.4f))
        }
        "diamonds" -> {
            val s = 36f
            var y = 0f
            while (y < h + s) {
                var x = 0f
                while (x < w + s) {
                    val path = Path().apply {
                        moveTo(x, y - s / 2); lineTo(x + s / 2, y); lineTo(x, y + s / 2); lineTo(x - s / 2, y); close()
                    }
                    drawPath(path, c(.06f), style = Stroke(width = 1.1f))
                    x += s
                }
                y += s
            }
        }
        "hearts" -> {
            val r = Seeded(29)
            repeat(16) {
                val cx = w * r.f(); val cy = h * r.f(); val s = 8f + r.f() * 14f
                val path = Path().apply {
                    moveTo(cx, cy + s * .9f)
                    cubicTo(cx - s * 1.4f, cy - s * .1f, cx - s * .6f, cy - s * 1.1f, cx, cy - s * .35f)
                    cubicTo(cx + s * .6f, cy - s * 1.1f, cx + s * 1.4f, cy - s * .1f, cx, cy + s * .9f)
                    close()
                }
                drawPath(path, Color(0xFFFF8FB1).copy(alpha = .14f * k))
            }
        }
        "leaves" -> {
            val r = Seeded(31)
            repeat(18) {
                val cx = w * r.f(); val cy = h * r.f(); val s = 10f + r.f() * 16f
                rotate(degrees = r.f() * 360f, pivot = Offset(cx, cy)) {
                    drawOval(c(.10f), topLeft = Offset(cx - s, cy - s * .42f), size = Size(s * 2, s * .84f))
                    drawLine(c(.14f), Offset(cx - s, cy), Offset(cx + s, cy), 1f)
                }
            }
        }
        "circuit" -> {
            val r = Seeded(37)
            repeat(26) {
                val x = w * r.f(); val y = h * r.f(); val len = 30f + r.f() * 80f
                val horizontal = r.f() > .5f
                val end = if (horizontal) Offset(x + len, y) else Offset(x, y + len)
                drawLine(c(.10f), Offset(x, y), end, 1.4f)
                drawCircle(c(.18f), radius = 3f, center = end)
                drawCircle(c(.12f), radius = 2f, center = Offset(x, y))
            }
        }
        "confetti" -> {
            val r = Seeded(41)
            val palette = listOf(Color(0xFFFFD166), Color(0xFF06D6A0), Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFFB388FF))
            repeat(46) { i ->
                val cx = w * r.f(); val cy = h * r.f()
                rotate(degrees = r.f() * 180f, pivot = Offset(cx, cy)) {
                    drawRect(palette[i % palette.size].copy(alpha = .22f * k), topLeft = Offset(cx - 5f, cy - 2f), size = Size(10f, 4f))
                }
            }
        }
        "rings" -> {
            val center = Offset(w * .82f, h * .12f)
            for (i in 1..12) drawCircle(c(.06f), radius = i * min(w, h) * .09f, center = center, style = Stroke(width = 1.3f))
        }
        "aurora" -> {
            drawCircle(Color(0xFF7CFFCB).copy(alpha = .09f * k), radius = w * .55f, center = Offset(w * .15f, h * .10f))
            drawCircle(Color(0xFF9B7CFF).copy(alpha = .09f * k), radius = w * .50f, center = Offset(w * .95f, h * .45f))
            drawCircle(Color(0xFFFF7CC8).copy(alpha = .07f * k), radius = w * .45f, center = Offset(w * .30f, h * .95f))
        }
    }
}
