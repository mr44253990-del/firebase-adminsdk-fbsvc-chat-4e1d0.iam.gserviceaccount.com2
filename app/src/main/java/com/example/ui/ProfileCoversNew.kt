package com.example.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Covers added in 4.3.5, including the new "Convo Signature" default cover. */
val NewCoverPresets = listOf(
    CoverPreset("convo_signature", "Convo Signature", listOf(Color(0xFF140B3A), Color(0xFF5B2EFF), Color(0xFFFF4FD8), Color(0xFF3DD6FF)), "New"),
    CoverPreset("eid_moon", "Eid moon", listOf(Color(0xFF070B24), Color(0xFF1B2A6B), Color(0xFFFFE6A3), Color(0xFFFFD36E)), "New"),
    CoverPreset("golden_bengal", "Golden Bengal", listOf(Color(0xFF8ED1FC), Color(0xFFFFE29A), Color(0xFF2E8B57), Color(0xFFF4C430)), "New"),
    CoverPreset("neon_city", "Neon city", listOf(Color(0xFF05010F), Color(0xFF2B0B4F), Color(0xFFFF2E93), Color(0xFF00E5FF)), "New"),
    CoverPreset("candy_clouds", "Candy clouds", listOf(Color(0xFFFFD1E8), Color(0xFFC7B8FF), Color(0xFFFFFFFF), Color(0xFFFF8CC6)), "New"),
    CoverPreset("glass_geo", "Glass geometry", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF7DF9FF), Color(0xFFB388FF)), "New"),
    CoverPreset("sunrise_sea", "Sunrise sea", listOf(Color(0xFFFFB199), Color(0xFFFF0844), Color(0xFF1E3C72), Color(0xFFFFF1B8)), "New"),
    CoverPreset("spiral_galaxy", "Spiral galaxy", listOf(Color(0xFF02010A), Color(0xFF1D0B3F), Color(0xFFB57CFF), Color(0xFF7FDBFF)), "New")
)

/** Returns true when the preset was drawn here. All motion is periodic in [phase]. */
fun DrawScope.drawNewCover(preset: CoverPreset, phase: Float): Boolean {
    val c = preset.colors
    val w = size.width
    val h = size.height
    val t = phase * 2f * PI.toFloat()
    when (preset.id) {
        "convo_signature" -> {
            drawRect(Brush.linearGradient(listOf(c[0], c[1].copy(alpha = .9f), c[0]), Offset.Zero, Offset(w, h)))
            val orbs = listOf(Triple(.18f, .30f, c[2]), Triple(.78f, .25f, c[3]), Triple(.55f, .85f, c[1]))
            orbs.forEachIndexed { i, (fx, fy, col) ->
                val cx = w * fx + cos(t + i * 2.1f) * w * .06f
                val cy = h * fy + sin(t + i * 1.7f) * h * .10f
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .55f), Color.Transparent), Offset(cx, cy), h * .75f), h * .75f, Offset(cx, cy))
            }
            // Liquid glass ribbon.
            val path = Path(); path.moveTo(0f, h * .62f)
            var x = 0f
            while (x <= w) { path.lineTo(x, h * .62f + sin(x / w * 5f + t) * h * .08f); x += w / 48f }
            path.lineTo(w, h); path.lineTo(0f, h); path.close()
            drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .02f)), startY = h * .5f, endY = h))
            repeat(26) { i ->
                val sx = ((i * 97.3f) % w); val sy = ((i * 53.1f) % (h * .6f))
                val tw = (sin(t * 2 + i) + 1f) / 2f
                drawCircle(Color.White.copy(alpha = .15f + .5f * tw), 1.2f + (i % 3), Offset(sx, sy))
            }
        }
        "eid_moon" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], Color(0xFF3A1C71))))
            repeat(60) { i ->
                val tw = (sin(t * 2 + i * 1.3f) + 1f) / 2f
                drawCircle(Color.White.copy(alpha = .2f + .6f * tw), 1f + (i % 3) * .7f, Offset((i * 83.7f) % w, (i * 41.9f) % (h * .7f)))
            }
            val mx = w * .78f; val my = h * .32f; val r = h * .22f
            drawCircle(Brush.radialGradient(listOf(c[2].copy(alpha = .35f), Color.Transparent), Offset(mx, my), r * 2.4f), r * 2.4f, Offset(mx, my))
            drawCircle(c[2], r, Offset(mx, my))
            drawCircle(c[1], r * .9f, Offset(mx + r * .42f, my - r * .18f))
            // Mosque silhouette.
            val base = h * .86f
            val sil = Color(0xFF05071A)
            drawRect(sil, Offset(0f, base), Size(w, h - base))
            drawCircle(sil, h * .14f, Offset(w * .35f, base))
            drawRect(sil, Offset(w * .35f - h * .14f, base - h * .02f), Size(h * .28f, h * .1f))
            listOf(.18f, .52f).forEach { fx ->
                drawRect(sil, Offset(w * fx - 6f, base - h * .32f), Size(12f, h * .32f))
                drawCircle(sil, 9f, Offset(w * fx, base - h * .32f))
            }
            repeat(5) { i ->
                val lx = w * (.08f + i * .2f); val ly = h * (.12f + (i % 2) * .08f) + sin(t + i) * 6f
                drawLine(c[3].copy(alpha = .5f), Offset(lx, 0f), Offset(lx, ly), 1.5f)
                drawRoundRect(c[3].copy(alpha = .85f), Offset(lx - 7f, ly), Size(14f, 22f), CornerRadius(7f))
            }
        }
        "golden_bengal" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            drawCircle(Color(0xFFE53935).copy(alpha = .9f), h * .16f, Offset(w * .72f, h * .36f + sin(t) * 4f))
            for (layer in 0..2) {
                val base = h * (.62f + layer * .1f)
                val path = Path(); path.moveTo(0f, h)
                var x = 0f
                while (x <= w) { path.lineTo(x, base + sin(x / w * (3f + layer) + layer + t * .5f) * h * .03f); x += w / 40f }
                path.lineTo(w, h); path.close()
                drawPath(path, androidx.compose.ui.graphics.lerp(c[2], c[3], layer / 2.5f))
            }
            repeat(40) { i ->
                val gx = (i * 37.7f) % w; val gy = h * .78f + (i % 5) * h * .04f
                val sway = sin(t * 2 + i) * 5f
                drawLine(Color(0xFFFFE082).copy(alpha = .8f), Offset(gx, gy), Offset(gx + sway, gy - h * .07f), 2f)
            }
            repeat(3) { i ->
                val bx = ((phase + i * .33f) % 1f) * (w + 80f) - 40f; val by = h * (.18f + i * .07f)
                drawLine(Color(0xFF263238), Offset(bx - 8f, by), Offset(bx, by + 4f), 2f)
                drawLine(Color(0xFF263238), Offset(bx, by + 4f), Offset(bx + 8f, by), 2f)
            }
        }
        "neon_city" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], c[2].copy(alpha = .6f))))
            drawCircle(Brush.radialGradient(listOf(c[2].copy(alpha = .8f), Color.Transparent), Offset(w * .5f, h * .62f), h * .5f), h * .5f, Offset(w * .5f, h * .62f))
            var bx = 0f; var i = 0
            while (bx < w) {
                val bw = 30f + (i * 37 % 50); val bh = h * (.25f + ((i * 53) % 40) / 100f)
                drawRect(Color(0xFF0A0418), Offset(bx, h - bh), Size(bw, bh))
                repeat(((bh / 18f).toInt()).coerceAtMost(12)) { r ->
                    if ((i + r) % 3 != 0) drawRect((if ((i + r) % 2 == 0) c[3] else c[2]).copy(alpha = .35f + .3f * ((sin(t + i + r) + 1f) / 2f)), Offset(bx + 6f, h - bh + 8f + r * 18f), Size(bw - 12f, 6f))
                }
                bx += bw + 4f; i++
            }
            repeat(30) { k -> val rx = (k * 61.3f) % w; val ry = ((k * 29.1f + phase * h * 2f) % h); drawLine(c[3].copy(alpha = .25f), Offset(rx, ry), Offset(rx - 4f, ry + 16f), 1.2f) }
        }
        "candy_clouds" -> {
            drawRect(Brush.linearGradient(listOf(c[0], c[1]), Offset.Zero, Offset(w, h)))
            repeat(5) { i ->
                val cx = ((i * .23f + phase * (.5f + i * .1f)) % 1f) * (w + 300f) - 150f
                val cy = h * (.2f + (i % 3) * .25f)
                val r = 40f + i * 10f
                val col = (if (i % 2 == 0) c[2] else c[3]).copy(alpha = .75f)
                drawCircle(col, r, Offset(cx, cy)); drawCircle(col, r * .8f, Offset(cx - r, cy + r * .25f)); drawCircle(col, r * .85f, Offset(cx + r, cy + r * .2f))
            }
        }
        "glass_geo" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            repeat(9) { i ->
                val cx = w * ((i * .137f + .05f) % 1f); val cy = h * ((i * .291f + .1f) % 1f)
                val s = h * (.18f + (i % 3) * .08f)
                val p = Path()
                val rot = t * (if (i % 2 == 0) .3f else -.3f) + i
                repeat(6) { k -> val a = rot + k * PI.toFloat() / 3f; val px = cx + cos(a) * s; val py = cy + sin(a) * s; if (k == 0) p.moveTo(px, py) else p.lineTo(px, py) }
                p.close()
                val col = if (i % 2 == 0) c[2] else c[3]
                drawPath(p, col.copy(alpha = .10f))
                drawPath(p, col.copy(alpha = .45f), style = Stroke(1.5f))
            }
        }
        "sunrise_sea" -> {
            drawRect(Brush.verticalGradient(listOf(c[1], c[0], c[3]), endY = h * .6f))
            drawCircle(c[3], h * .18f, Offset(w * .5f, h * .6f - sin(t) * 6f))
            drawRect(Brush.verticalGradient(listOf(c[2].copy(alpha = .8f), c[2]), startY = h * .6f), Offset(0f, h * .6f), Size(w, h * .4f))
            repeat(10) { i ->
                val y = h * (.63f + i * .035f); val len = w * (.15f - i * .01f).coerceAtLeast(.03f)
                val x = w * .5f + sin(t + i) * 10f
                drawLine(c[3].copy(alpha = .6f - i * .05f), Offset(x - len, y), Offset(x + len, y), 3f)
            }
        }
        "spiral_galaxy" -> {
            drawRect(c[0])
            val cx = w * .5f; val cy = h * .5f
            for (arm in 0..1) for (k in 0 until 140) {
                val a = k * .09f + arm * PI.toFloat() + t * .25f
                val r = k * h * .006f
                val px = cx + cos(a) * r * 1.6f; val py = cy + sin(a) * r * .8f
                drawCircle((if (k % 2 == 0) c[2] else c[3]).copy(alpha = (.8f - k / 200f).coerceAtLeast(.1f)), 1.2f + (k % 3), Offset(px, py))
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .9f), c[2].copy(alpha = .3f), Color.Transparent), Offset(cx, cy), h * .22f), h * .22f, Offset(cx, cy))
        }
        else -> return false
    }
    return true
}
