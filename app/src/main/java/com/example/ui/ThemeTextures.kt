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
        "leaves", "bubbles", "zigzag", "diamonds", "doodle", "seigaiha", "topo", "linen", "grain",
        "clouds", "mountains", "skyline", "snow", "music", "paws", "triangles", "plaid", "marble",
        "crescent", "constellation", "lanterns", "stripes", "galaxy", "sakura", "polka", "circuitry_glow"
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
        else -> drawExtraTexture(texture, w, h, ::c)
    }
}

/** Newer textures (doodles, Japanese waves, topo lines, landscapes…). */
private fun DrawScope.drawExtraTexture(texture: String, w: Float, h: Float, c: (Float) -> Color) {
    val r = Seeded(texture.hashCode().toLong())
    when (texture) {
        "doodle" -> {
            val step = 92f
            var y = 20f; var row = 0
            while (y < h + step) {
                var x = if (row % 2 == 0) 18f else 18f + step / 2
                while (x < w + step) {
                    when (((x / step).toInt() + row) % 5) {
                        0 -> { // heart
                            val s = 9f
                            val p = Path().apply { moveTo(x, y + s * .9f); cubicTo(x - s * 1.6f, y - s * .2f, x - s * .7f, y - s * 1.3f, x, y - s * .4f); cubicTo(x + s * .7f, y - s * 1.3f, x + s * 1.6f, y - s * .2f, x, y + s * .9f) }
                            drawPath(p, c(.10f), style = Stroke(2f))
                        }
                        1 -> { // smiley
                            drawCircle(c(.10f), 10f, Offset(x, y), style = Stroke(2f))
                            drawCircle(c(.12f), 1.6f, Offset(x - 3.5f, y - 2.5f)); drawCircle(c(.12f), 1.6f, Offset(x + 3.5f, y - 2.5f))
                            drawArc(c(.12f), 20f, 140f, false, Offset(x - 6f, y - 4f), Size(12f, 10f), style = Stroke(1.8f))
                        }
                        2 -> { // star
                            val p = Path()
                            for (i in 0 until 10) {
                                val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                                val rr = if (i % 2 == 0) 10f else 4.5f
                                if (i == 0) p.moveTo(x + cos(a) * rr, y + sin(a) * rr) else p.lineTo(x + cos(a) * rr, y + sin(a) * rr)
                            }
                            p.close(); drawPath(p, c(.10f), style = Stroke(1.8f))
                        }
                        3 -> { // chat bubble
                            drawRoundRect(c(.10f), Offset(x - 11f, y - 8f), Size(22f, 15f), androidx.compose.ui.geometry.CornerRadius(6f), style = Stroke(1.8f))
                            drawLine(c(.10f), Offset(x - 4f, y + 7f), Offset(x - 8f, y + 12f), strokeWidth = 1.8f)
                        }
                        else -> { // note
                            drawCircle(c(.10f), 4f, Offset(x - 3f, y + 7f))
                            drawLine(c(.10f), Offset(x + 1f, y + 7f), Offset(x + 1f, y - 9f), strokeWidth = 1.8f)
                            drawLine(c(.10f), Offset(x + 1f, y - 9f), Offset(x + 8f, y - 6f), strokeWidth = 1.8f)
                        }
                    }
                    x += step
                }
                y += step * .72f; row++
            }
        }
        "seigaiha" -> {
            val rad = 34f
            var row = 0; var y = 0f
            while (y < h + rad) {
                var x = if (row % 2 == 0) 0f else rad
                while (x < w + rad) {
                    for (k in 1..4) drawArc(c(.07f), 180f, 180f, false, Offset(x - rad * k / 4f, y - rad * k / 4f), Size(rad * k / 2f, rad * k / 2f), style = Stroke(1.4f))
                    x += rad * 2
                }
                y += rad * .5f; row++
            }
        }
        "topo" -> {
            for (ring in 0 until 3) {
                val cx = w * (.2f + ring * .35f); val cy = h * (.25f + (ring % 2) * .45f)
                for (i in 1..9) {
                    val p = Path()
                    for (a in 0..36) {
                        val ang = a / 36f * 2f * PI.toFloat()
                        val rr = i * 26f * (1f + .18f * sin(ang * 3f + i + ring))
                        val px = cx + cos(ang) * rr; val py = cy + sin(ang) * rr
                        if (a == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                    p.close(); drawPath(p, c(.06f), style = Stroke(1.2f))
                }
            }
        }
        "linen" -> {
            var x = 0f
            while (x < w) { drawLine(c(.035f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f); x += 6f }
            var y = 0f
            while (y < h) { drawLine(c(.03f), Offset(0f, y), Offset(w, y), strokeWidth = 1f); y += 6f }
        }
        "grain" -> repeat(900) { drawCircle(c(.05f + r.f() * .05f), .8f + r.f(), Offset(r.f() * w, r.f() * h)) }
        "clouds" -> repeat(9) {
            val x = r.f() * w; val y = r.f() * h; val s = 26f + r.f() * 30f
            drawCircle(c(.07f), s, Offset(x, y)); drawCircle(c(.07f), s * .8f, Offset(x - s, y + s * .3f)); drawCircle(c(.07f), s * .8f, Offset(x + s, y + s * .3f))
        }
        "mountains" -> for (layer in 0..2) {
            val p = Path(); val base = h * (.62f + layer * .12f)
            p.moveTo(0f, h); p.lineTo(0f, base)
            var x = 0f
            while (x <= w) { p.lineTo(x + w / 10f, base - (40f + r.f() * 90f) * (1f - layer * .2f)); p.lineTo(x + w / 5f, base); x += w / 5f }
            p.lineTo(w, h); p.close(); drawPath(p, c(.05f + layer * .03f))
        }
        "skyline" -> {
            var x = 0f
            while (x < w) {
                val bw = 22f + r.f() * 38f; val bh = h * (.12f + r.f() * .22f)
                drawRect(c(.07f), Offset(x, h - bh), Size(bw, bh))
                var wy = h - bh + 8f
                while (wy < h - 6f) { if (r.f() > .55f) drawRect(c(.12f), Offset(x + 5f, wy), Size(4f, 5f)); wy += 12f }
                x += bw + 4f
            }
        }
        "snow" -> repeat(160) { drawCircle(c(.10f + r.f() * .15f), 1f + r.f() * 2.6f, Offset(r.f() * w, r.f() * h)) }
        "music" -> repeat(26) {
            val x = r.f() * w; val y = r.f() * h
            drawCircle(c(.09f), 5f, Offset(x, y)); drawLine(c(.09f), Offset(x + 4.5f, y), Offset(x + 4.5f, y - 20f), strokeWidth = 2f)
            if (r.f() > .5f) { drawCircle(c(.09f), 5f, Offset(x + 16f, y - 4f)); drawLine(c(.09f), Offset(x + 20.5f, y - 4f), Offset(x + 20.5f, y - 24f), strokeWidth = 2f); drawLine(c(.09f), Offset(x + 4.5f, y - 20f), Offset(x + 20.5f, y - 24f), strokeWidth = 4f) }
        }
        "paws" -> repeat(22) {
            val x = r.f() * w; val y = r.f() * h
            rotate(r.f() * 360f, Offset(x, y)) {
                drawOval(c(.09f), Offset(x - 8f, y - 4f), Size(16f, 13f))
                for (t in 0..3) drawCircle(c(.09f), 3.4f, Offset(x - 9f + t * 6f, y - 9f - if (t == 1 || t == 2) 3f else 0f))
            }
        }
        "triangles" -> {
            val s = 54f
            var row = 0; var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else -s / 2
                while (x < w) {
                    if (r.f() > .45f) {
                        val p = Path().apply { moveTo(x, y + s * .86f); lineTo(x + s / 2, y); lineTo(x + s, y + s * .86f); close() }
                        drawPath(p, c(.03f + r.f() * .06f))
                    }
                    x += s
                }
                y += s * .86f; row++
            }
        }
        "plaid" -> {
            var x = 0f
            while (x < w) { drawRect(c(.05f), Offset(x, 0f), Size(18f, h)); drawLine(c(.07f), Offset(x + 30f, 0f), Offset(x + 30f, h), strokeWidth = 2f); x += 64f }
            var y = 0f
            while (y < h) { drawRect(c(.05f), Offset(0f, y), Size(w, 18f)); drawLine(c(.07f), Offset(0f, y + 30f), Offset(w, y + 30f), strokeWidth = 2f); y += 64f }
        }
        "marble" -> repeat(7) { v ->
            val p = Path(); var x = -20f; var y = r.f() * h
            p.moveTo(x, y)
            while (x < w + 20f) { x += 30f; y += (r.f() - .5f) * 50f; val cy = y + (r.f() - .5f) * 40f; p.cubicTo(x - 20f, cy, x - 10f, cy, x, y) }
            drawPath(p, c(.05f + (v % 3) * .025f), style = Stroke(1f + r.f() * 3f))
        }
        "crescent" -> {
            repeat(70) { drawCircle(c(.10f + r.f() * .18f), .8f + r.f() * 1.6f, Offset(r.f() * w, r.f() * h * .8f)) }
            val cx = w * .78f; val cy = h * .16f; val rad = min(w, h) * .10f
            drawCircle(c(.22f), rad, Offset(cx, cy))
            drawCircle(Color(0xFF0B1030).copy(alpha = (c(.55f).alpha).coerceAtMost(.9f)), rad * .92f, Offset(cx + rad * .42f, cy - rad * .18f))
        }
        "constellation" -> {
            val pts = List(26) { Offset(r.f() * w, r.f() * h) }
            pts.forEachIndexed { i, p -> drawCircle(c(.30f), 1.6f + (i % 3), p); if (i % 4 != 3 && i + 1 < pts.size) drawLine(c(.09f), p, pts[i + 1], strokeWidth = 1f) }
        }
        "lanterns" -> repeat(9) {
            val x = r.f() * w; val y = r.f() * h * .9f; val s = 10f + r.f() * 14f
            drawLine(c(.10f), Offset(x, 0f), Offset(x, y - s), strokeWidth = 1f)
            drawRoundRect(c(.16f), Offset(x - s * .6f, y - s), Size(s * 1.2f, s * 1.8f), androidx.compose.ui.geometry.CornerRadius(s * .5f))
            drawCircle(Color(0xFFFFD36E).copy(alpha = c(.10f).alpha), s * 2.2f, Offset(x, y - s * .1f))
        }
        "stripes" -> { var x = -h; while (x < w) { drawLine(c(.05f), Offset(x, h), Offset(x + h, 0f), strokeWidth = 14f); x += 46f } }
        "galaxy" -> {
            drawCircle(Color(0xFFB57CFF).copy(alpha = c(.10f).alpha), w * .45f, Offset(w * .3f, h * .35f))
            drawCircle(Color(0xFF3DD6FF).copy(alpha = c(.08f).alpha), w * .40f, Offset(w * .8f, h * .7f))
            repeat(180) { drawCircle(c(.08f + r.f() * .30f), .6f + r.f() * 1.8f, Offset(r.f() * w, r.f() * h)) }
        }
        "sakura" -> repeat(30) {
            val center = Offset(r.f() * w, r.f() * h); val s = 5f + r.f() * 8f; val angle = r.f() * 360f
            rotate(angle, center) { repeat(5) { i -> val a = i * 2 * PI / 5; drawCircle(Color(0xFFFFB7D5).copy(alpha = c(.16f).alpha), s * .55f, Offset(center.x + (cos(a) * s * .6f).toFloat(), center.y + (sin(a) * s * .6f).toFloat())) } }
        }
        "polka" -> { var y = 12f; var row = 0; while (y < h) { var x = if (row % 2 == 0) 12f else 34f; while (x < w) { drawCircle(c(.07f), 6f, Offset(x, y)); x += 44f }; y += 38f; row++ } }
        "circuitry_glow" -> repeat(22) {
            var x = r.f() * w; var y = r.f() * h
            val p = Path(); p.moveTo(x, y)
            repeat(4) { if (r.f() > .5f) x += (r.f() - .5f) * 160f else y += (r.f() - .5f) * 160f; p.lineTo(x, y) }
            drawPath(p, c(.10f), style = Stroke(1.6f)); drawCircle(c(.24f), 3f, Offset(x, y))
        }
    }
}
