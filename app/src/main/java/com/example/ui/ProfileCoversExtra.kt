package com.example.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Extra drawn covers: anime-style scenes, animated particles and abstract art. All movement is
 * periodic in `phase` (0..1) so the 14 s loop never jumps.
 */
val ExtraCoverPresets = listOf(
    CoverPreset("anime_sky", "Anime summer sky", listOf(Color(0xFF2F80ED), Color(0xFF8EC5FC), Color(0xFFFFFFFF), Color(0xFF3C8D2F)), "Anime"),
    CoverPreset("anime_night", "Anime starry night", listOf(Color(0xFF0B0F2B), Color(0xFF26215C), Color(0xFFFF8FB1), Color(0xFFFFF6C8)), "Anime"),
    CoverPreset("sakura_rain", "Sakura petal rain", listOf(Color(0xFFFFE4EC), Color(0xFFFFB3C7), Color(0xFFF06292), Color(0xFF5D4037)), "Anime"),
    CoverPreset("fuji", "Mount Fuji dawn", listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88), Color(0xFF5B5F97), Color(0xFFFFFFFF)), "Anime"),
    CoverPreset("lanterns", "Sky lanterns", listOf(Color(0xFF140F2D), Color(0xFF3D1C5A), Color(0xFFFFB347), Color(0xFFFFE29A)), "Anime"),
    CoverPreset("great_wave", "Great wave", listOf(Color(0xFFF3E9D2), Color(0xFF1D3557), Color(0xFF457B9D), Color(0xFFFFFFFF)), "Anime"),
    CoverPreset("torii", "Torii sunset", listOf(Color(0xFFFFC371), Color(0xFFFF5F6D), Color(0xFFB71C1C), Color(0xFF2B1B17)), "Anime"),
    CoverPreset("cyber_rain", "Cyberpunk rain", listOf(Color(0xFF05010F), Color(0xFF240046), Color(0xFFFF00C8), Color(0xFF00F5FF)), "Animated"),
    CoverPreset("fireflies", "Fireflies", listOf(Color(0xFF0A1F12), Color(0xFF14382A), Color(0xFFE8FF6B), Color(0xFF041008)), "Animated"),
    CoverPreset("snowfall", "Snowfall", listOf(Color(0xFF1C3B57), Color(0xFF5B86B1), Color(0xFFFFFFFF), Color(0xFFDCEBFA)), "Animated"),
    CoverPreset("matrix", "Code rain", listOf(Color(0xFF000000), Color(0xFF001A00), Color(0xFF00FF66), Color(0xFFB9FFCF)), "Animated"),
    CoverPreset("hearts", "Floating hearts", listOf(Color(0xFFFF9A9E), Color(0xFFFAD0C4), Color(0xFFFF4D6D), Color(0xFFFFFFFF)), "Animated"),
    CoverPreset("planet", "Ringed planet", listOf(Color(0xFF02010A), Color(0xFF1A1446), Color(0xFFFFB86B), Color(0xFF8E7DFF)), "Animated"),
    CoverPreset("comet", "Comet trail", listOf(Color(0xFF000814), Color(0xFF001D3D), Color(0xFF7FDBFF), Color(0xFFFFFFFF)), "Animated"),
    CoverPreset("bubbles", "Rising bubbles", listOf(Color(0xFF00B4DB), Color(0xFF0083B0), Color(0xFFFFFFFF), Color(0xFF004E7A)), "Animated"),
    CoverPreset("liquid", "Liquid blobs", listOf(Color(0xFF7F00FF), Color(0xFFE100FF), Color(0xFF00DBDE), Color(0xFFFC00FF)), "Abstract"),
    CoverPreset("memphis", "Memphis pop", listOf(Color(0xFFFFF4E0), Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFD93D)), "Abstract"),
    CoverPreset("waves_lines", "Line waves", listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF00E5FF), Color(0xFFFF4081)), "Abstract"),
    CoverPreset("dunes", "Desert dunes", listOf(Color(0xFFFFE0B2), Color(0xFFFFB74D), Color(0xFFE65100), Color(0xFF8D3B00)), "Nature"),
    CoverPreset("rainbow", "Rainbow meadow", listOf(Color(0xFF89F7FE), Color(0xFF66A6FF), Color(0xFF7BC74D), Color(0xFF3B7D23)), "Nature")
)

private fun frac(x: Float) = x - kotlin.math.floor(x)

/** Returns true when the preset was handled here. */
fun DrawScope.drawExtraCover(preset: CoverPreset, phase: Float): Boolean {
    val c = preset.colors
    val w = size.width
    val h = size.height
    val t = phase * 2f * PI.toFloat()
    when (preset.id) {
        "anime_sky" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], Color(0xFFE0F2FF))))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .9f), Color.Transparent), center = Offset(w * .85f, h * .15f), radius = h * .5f), h * .5f, Offset(w * .85f, h * .15f))
            for (i in 0 until 4) {
                val cx = frac(i * .29f + phase) * (w + 300f) - 150f
                val cy = h * (.22f + (i % 2) * .18f)
                cloud(cx, cy, 50f + i * 12f, c[2])
            }
            hills(c[3].copy(alpha = .8f), h * .82f, 1.2f, 0f)
            hills(Color(0xFF2B6A22), h * .9f, 2f, 1.5f)
        }
        "anime_night" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], Color(0xFF6A3F8F))))
            stars(Color.White, 80, .7f, phase)
            drawCircle(c[3], h * .13f, Offset(w * .78f, h * .25f))
            drawCircle(c[1], h * .12f, Offset(w * .74f, h * .23f))
            // shooting star
            val sp = frac(phase * 2f)
            if (sp < .35f) {
                val k = sp / .35f
                val sx = w * (.1f + k * .6f); val sy = h * (.08f + k * .25f)
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White), Offset(sx - 80f, sy - 34f), Offset(sx, sy)), Offset(sx - 80f, sy - 34f), Offset(sx, sy), strokeWidth = 3f)
            }
            var x = 0f; var i = 0
            while (x < w) {
                val bw = w * (.06f + (i * 5 % 4) * .015f); val bh = h * (.18f + (i * 7 % 6) * .05f)
                drawRect(Color(0xFF120A26), Offset(x, h - bh), Size(bw, bh))
                if (i % 2 == 0) drawRect(c[2].copy(alpha = .7f), Offset(x + bw * .3f, h - bh + 8f), Size(4f, 6f))
                x += bw + 2f; i++
            }
        }
        "sakura_rain" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], Color(0xFFFFF6F9))))
            // branch
            val branch = Path().apply { moveTo(-10f, h * .2f); cubicTo(w * .2f, h * .1f, w * .35f, h * .25f, w * .55f, h * .05f) }
            drawPath(branch, c[3], style = Stroke(9f))
            repeat(14) { i ->
                val bx = w * (i / 14f) * .55f; val by = h * (.08f + abs(sin(i * 1.3f)) * .18f)
                drawCircle(c[1], 12f + (i % 3) * 4f, Offset(bx, by)); drawCircle(c[2].copy(alpha = .6f), 4f, Offset(bx, by))
            }
            repeat(26) { i ->
                val sx = frac(i * .137f + phase * (1 + i % 3)) * w
                val sy = frac(i * .071f + phase * 2f) * (h + 40f) - 20f
                rotate((phase * 360f * (1 + i % 2)) + i * 30f, Offset(sx, sy)) {
                    drawOval(c[1].copy(alpha = .85f), Offset(sx - 6f, sy - 3.5f), Size(12f, 7f))
                }
            }
        }
        "fuji" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], c[2])))
            drawCircle(Color(0xFFFFE8A3).copy(alpha = .85f), h * .2f, Offset(w * .5f, h * .52f + sin(t) * 3f))
            val m = Path().apply { moveTo(w * .1f, h); lineTo(w * .42f, h * .38f); lineTo(w * .58f, h * .38f); lineTo(w * .9f, h); close() }
            drawPath(m, Brush.verticalGradient(listOf(Color(0xFF3E3F73), Color(0xFF22224A)), startY = h * .38f, endY = h))
            val snow = Path().apply { moveTo(w * .42f, h * .38f); lineTo(w * .58f, h * .38f); lineTo(w * .65f, h * .52f); lineTo(w * .57f, h * .48f); lineTo(w * .5f, h * .54f); lineTo(w * .43f, h * .48f); lineTo(w * .35f, h * .52f); close() }
            drawPath(snow, c[3])
            for (i in 0 until 3) {
                val y = h * (.3f + i * .08f); val off = frac(phase + i * .3f) * w
                drawRoundRect(Color.White.copy(alpha = .35f), Offset(off - w * .3f, y), Size(w * .3f, 10f), androidx.compose.ui.geometry.CornerRadius(5f))
            }
        }
        "lanterns" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], Color(0xFF6B2D5C))))
            stars(Color.White, 40, .6f, phase)
            repeat(16) { i ->
                val lx = w * frac(i * .173f) + sin(t + i) * 10f
                val ly = h * (1.1f - frac(i * .09f + phase)) * 1.1f
                val s = 8f + (i % 4) * 4f
                drawCircle(Brush.radialGradient(listOf(c[2].copy(alpha = .55f), Color.Transparent), center = Offset(lx, ly), radius = s * 3f), s * 3f, Offset(lx, ly))
                drawRoundRect(Brush.verticalGradient(listOf(c[3], c[2]), startY = ly - s, endY = ly + s), Offset(lx - s * .7f, ly - s), Size(s * 1.4f, s * 2f), androidx.compose.ui.geometry.CornerRadius(s * .5f))
            }
        }
        "great_wave" -> {
            drawRect(c[0])
            drawCircle(Color(0xFFE63946).copy(alpha = .85f), h * .14f, Offset(w * .8f, h * .25f))
            for (layer in 0 until 4) {
                val p = Path(); val base = h * (.5f + layer * .13f)
                p.moveTo(0f, h)
                var x = 0f
                while (x <= w) { p.lineTo(x, base + sin(x / w * 8f + t + layer) * h * .06f); x += w / 50f }
                p.lineTo(w, h); p.close()
                drawPath(p, if (layer % 2 == 0) c[1] else c[2])
                x = 0f
                while (x <= w) { drawCircle(c[3].copy(alpha = .8f), 4f, Offset(x, base + sin(x / w * 8f + t + layer) * h * .06f - 3f)); x += w / 16f }
            }
        }
        "torii" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], Color(0xFF6A1B4D))))
            drawCircle(Color(0xFFFFF3B0), h * .18f, Offset(w * .5f, h * .6f))
            drawRect(Color(0xFF3B1F2B).copy(alpha = .7f), Offset(0f, h * .74f), Size(w, h * .26f))
            for (i in 0 until 6) {
                val y = h * .78f + i * 8f
                drawLine(Color(0xFFFFC371).copy(alpha = .35f + .3f * abs(sin(t + i))), Offset(w * .3f, y), Offset(w * .7f, y), strokeWidth = 2f)
            }
            val gate = c[2]
            drawRect(gate, Offset(w * .36f, h * .32f), Size(10f, h * .42f))
            drawRect(gate, Offset(w * .64f - 10f, h * .32f), Size(10f, h * .42f))
            drawRoundRect(c[3], Offset(w * .3f, h * .28f), Size(w * .4f, 14f), androidx.compose.ui.geometry.CornerRadius(6f))
            drawRect(gate, Offset(w * .33f, h * .37f), Size(w * .34f, 8f))
        }
        "cyber_rain" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            var x = 0f; var i = 0
            while (x < w) {
                val bw = w * (.07f + (i * 3 % 4) * .02f); val bh = h * (.35f + (i * 7 % 5) * .1f)
                drawRect(Color(0xFF0D0221), Offset(x, h - bh), Size(bw, bh))
                drawLine(if (i % 2 == 0) c[2] else c[3], Offset(x, h - bh), Offset(x + bw, h - bh), strokeWidth = 2f, alpha = .6f + .4f * abs(sin(t * 2 + i)))
                x += bw + 4f; i++
            }
            repeat(60) { k ->
                val rx = frac(k * .113f) * w
                val ry = frac(k * .057f + phase * 4f) * h
                drawLine(c[3].copy(alpha = .35f), Offset(rx, ry), Offset(rx - 3f, ry + 16f), strokeWidth = 1.4f)
            }
        }
        "fireflies" -> {
            drawRect(Brush.verticalGradient(listOf(c[1], c[0], c[3])))
            hills(Color(0xFF04140B), h * .8f, 1.4f, 0f)
            repeat(28) { i ->
                val fx = w * frac(i * .131f) + sin(t * (1 + i % 3) + i) * 22f
                val fy = h * (.25f + frac(i * .077f) * .6f) + cos(t * (1 + i % 2) + i) * 14f
                val glow = (sin(t * 3 + i) + 1f) / 2f
                drawCircle(Brush.radialGradient(listOf(c[2].copy(alpha = .6f * glow), Color.Transparent), center = Offset(fx, fy), radius = 16f), 16f, Offset(fx, fy))
                drawCircle(c[2].copy(alpha = .4f + .6f * glow), 2.5f, Offset(fx, fy))
            }
        }
        "snowfall" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], c[3])))
            hills(Color.White.copy(alpha = .9f), h * .82f, 1.1f, .4f)
            repeat(50) { i ->
                val sx = frac(i * .097f) * w + sin(t + i) * 12f
                val sy = frac(i * .061f + phase * (1 + i % 3)) * h
                drawCircle(c[2].copy(alpha = .8f), 1.5f + (i % 3) * 1.2f, Offset(sx, sy))
            }
        }
        "matrix" -> {
            drawRect(c[0])
            val cols = 26
            for (col in 0 until cols) {
                val x = w * col / cols
                val head = frac(col * .37f + phase * (1 + col % 3)) * (h + 120f)
                for (k in 0 until 10) {
                    val y = head - k * 14f
                    if (y in 0f..h) drawRect((if (k == 0) c[3] else c[2]).copy(alpha = 1f - k / 10f), Offset(x, y), Size(6f, 9f))
                }
            }
        }
        "hearts" -> {
            drawRect(Brush.linearGradient(listOf(c[0], c[1])))
            repeat(18) { i ->
                val hx = w * frac(i * .19f) + sin(t + i) * 16f
                val hy = h * (1.1f - frac(i * .083f + phase)) * 1.1f
                heart(hx, hy, 9f + (i % 4) * 5f, (if (i % 3 == 0) c[3] else c[2]).copy(alpha = .75f))
            }
        }
        "planet" -> {
            drawRect(Brush.radialGradient(listOf(c[1], c[0]), center = Offset(w * .6f, h * .5f), radius = w * .8f))
            stars(Color.White, 70, 1f, phase)
            val pc = Offset(w * .62f, h * .52f); val r = h * .26f
            drawOval(c[3].copy(alpha = .5f), Offset(pc.x - r * 2f, pc.y - r * .35f), Size(r * 4f, r * .7f), style = Stroke(5f))
            drawCircle(Brush.linearGradient(listOf(c[2], Color(0xFFE76F51)), Offset(pc.x - r, pc.y - r), Offset(pc.x + r, pc.y + r)), r, pc)
            for (b in 0 until 3) drawLine(Color.White.copy(alpha = .15f), Offset(pc.x - r * .9f, pc.y - r * .3f + b * r * .3f), Offset(pc.x + r * .9f, pc.y - r * .3f + b * r * .3f), strokeWidth = 6f)
            val moonA = t
            drawCircle(Color(0xFFDDE1FF), r * .12f, Offset(pc.x + cos(moonA) * r * 1.7f, pc.y + sin(moonA) * r * .45f))
        }
        "comet" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            stars(Color.White, 60, 1f, phase)
            val k = frac(phase * 1.5f)
            val hx = -w * .2f + k * w * 1.4f; val hy = h * (.75f - k * .5f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, c[2], c[3]), Offset(hx - 160f, hy + 60f), Offset(hx, hy)), Offset(hx - 160f, hy + 60f), Offset(hx, hy), strokeWidth = 7f)
            drawCircle(c[3], 7f, Offset(hx, hy))
        }
        "bubbles" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], c[3])))
            repeat(22) { i ->
                val bx = w * frac(i * .151f) + sin(t * 2 + i) * 10f
                val by = h * (1.1f - frac(i * .07f + phase * (1 + i % 2)))
                val r = 5f + (i % 5) * 4f
                drawCircle(c[2].copy(alpha = .4f), r, Offset(bx, by), style = Stroke(2f))
                drawCircle(c[2].copy(alpha = .6f), r * .25f, Offset(bx - r * .35f, by - r * .35f))
            }
        }
        "liquid" -> {
            drawRect(Brush.linearGradient(listOf(c[0], c[1])))
            for (i in 0 until 4) {
                val bx = w * (.2f + i * .22f) + sin(t + i * 1.7f) * w * .08f
                val by = h * (.5f + cos(t * 1.3f + i) * .25f)
                drawCircle(Brush.radialGradient(listOf(c[2 + i % 2].copy(alpha = .75f), Color.Transparent), center = Offset(bx, by), radius = h * .5f), h * .5f, Offset(bx, by))
            }
        }
        "memphis" -> {
            drawRect(c[0])
            for (i in 0 until 14) {
                val x = w * frac(i * .21f + phase * .1f * (i % 2)); val y = h * frac(i * .37f)
                when (i % 4) {
                    0 -> drawCircle(c[1], 14f, Offset(x, y))
                    1 -> drawRect(c[2], Offset(x, y), Size(22f, 22f))
                    2 -> { val p = Path().apply { moveTo(x, y); lineTo(x + 26f, y); lineTo(x + 13f, y - 22f); close() }; drawPath(p, c[3]) }
                    else -> { val p = Path(); p.moveTo(x, y); for (k in 1..5) p.lineTo(x + k * 10f, y + if (k % 2 == 0) 0f else 10f); drawPath(p, Color(0xFF222222), style = Stroke(4f)) }
                }
            }
        }
        "waves_lines" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            for (l in 0 until 14) {
                val p = Path(); val base = h * (.15f + l * .055f)
                p.moveTo(0f, base); var x = 0f
                while (x <= w) { p.lineTo(x, base + sin(x / w * 5f + t + l * .35f) * h * .06f); x += w / 60f }
                drawPath(p, (if (l % 2 == 0) c[2] else c[3]).copy(alpha = .25f + l * .04f), style = Stroke(2f))
            }
        }
        "dunes" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), c[0], c[1])))
            drawCircle(Color(0xFFFFF59D), h * .12f, Offset(w * .25f, h * .25f))
            hills(c[1], h * .62f, .8f, t * .1f)
            hills(c[2], h * .75f, 1.1f, 1f)
            hills(c[3], h * .9f, 1.5f, 2f)
        }
        "rainbow" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            val rc = listOf(Color(0xFFFF5252), Color(0xFFFFAB40), Color(0xFFFFFF00), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFF7C4DFF))
            rc.forEachIndexed { i, col ->
                val r = h * (.9f - i * .06f)
                drawArc(col.copy(alpha = .55f + .15f * sin(t)), 180f, 180f, false, Offset(w * .5f - r, h * .95f - r), Size(r * 2, r * 2), style = Stroke(h * .055f))
            }
            hills(c[2], h * .82f, 1.2f, 0f)
            hills(c[3], h * .92f, 1.8f, 1f)
        }
        else -> return false
    }
    return true
}

private fun DrawScope.cloud(x: Float, y: Float, r: Float, color: Color) {
    drawCircle(color, r, Offset(x, y))
    drawCircle(color, r * .8f, Offset(x - r, y + r * .25f))
    drawCircle(color, r * .85f, Offset(x + r, y + r * .2f))
    drawCircle(color, r * .7f, Offset(x + r * .4f, y - r * .5f))
    drawRect(color, Offset(x - r * 1.6f, y + r * .2f), Size(r * 3.3f, r * .7f))
}

private fun DrawScope.heart(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y + s * .9f)
        cubicTo(x - s * 1.6f, y - s * .2f, x - s * .7f, y - s * 1.3f, x, y - s * .4f)
        cubicTo(x + s * .7f, y - s * 1.3f, x + s * 1.6f, y - s * .2f, x, y + s * .9f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.stars(color: Color, count: Int, heightFraction: Float, phase: Float) {
    repeat(count) { i ->
        val x = (i * 71.3f * 13f) % size.width
        val y = (i * 37.9f * 7f) % (size.height * heightFraction)
        val twinkle = (sin((phase * 2 * PI + i).toFloat()) + 1f) / 2f
        drawCircle(color.copy(alpha = .25f + .6f * twinkle), radius = 1.2f + (i % 3), center = Offset(x, y))
    }
}

private fun DrawScope.hills(color: Color, base: Float, frequency: Float, shift: Float) {
    val path = Path()
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width) {
        path.lineTo(x, base + sin(x / size.width * PI.toFloat() * frequency * 2f + shift) * size.height * .06f)
        x += size.width / 40f
    }
    path.lineTo(size.width, size.height); path.close()
    drawPath(path, color)
}
