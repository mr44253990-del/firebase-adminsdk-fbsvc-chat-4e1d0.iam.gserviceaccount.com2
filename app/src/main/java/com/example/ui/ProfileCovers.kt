package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Built-in profile covers. They are drawn, not downloaded, so they look sharp on every screen,
 * cost no storage and work offline. A user's `coverImageUrl` may be "preset:<id>"; users with no
 * cover at all get a stable default chosen from their uid, so no profile looks empty.
 */
data class CoverPreset(val id: String, val name: String, val colors: List<Color>, val category: String = "Classic")

val BaseCoverPresets = listOf(
    CoverPreset("aurora", "Aurora", listOf(Color(0xFF0B1026), Color(0xFF1B2B6B), Color(0xFF00D1B2), Color(0xFF7C4DFF))),
    CoverPreset("sunset", "Sunset hills", listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B), Color(0xFF6A3093), Color(0xFF2B1055))),
    CoverPreset("ocean", "Ocean waves", listOf(Color(0xFF003973), Color(0xFF00A8CC), Color(0xFF7FDBFF), Color(0xFFE0F7FA))),
    CoverPreset("galaxy", "Galaxy", listOf(Color(0xFF07031A), Color(0xFF2A0845), Color(0xFF6441A5), Color(0xFFFF6FD8))),
    CoverPreset("neon", "Neon grid", listOf(Color(0xFF0F0026), Color(0xFF3A0CA3), Color(0xFFF72585), Color(0xFF4CC9F0))),
    CoverPreset("sakura", "Sakura", listOf(Color(0xFFFFDDE1), Color(0xFFFFB7C5), Color(0xFFEE9CA7), Color(0xFFFFFFFF))),
    CoverPreset("mint", "Mint dunes", listOf(Color(0xFF0F9B8E), Color(0xFF38EF7D), Color(0xFFB8F2E6), Color(0xFF06493F))),
    CoverPreset("prism", "Prism", listOf(Color(0xFF12C2E9), Color(0xFFC471ED), Color(0xFFF64F59), Color(0xFF1A1A40))),
    CoverPreset("gold", "Golden hour", listOf(Color(0xFF3E2723), Color(0xFFB8860B), Color(0xFFFFD54F), Color(0xFFFFF3C4))),
    CoverPreset("city", "Midnight city", listOf(Color(0xFF020617), Color(0xFF1E1B4B), Color(0xFF6366F1), Color(0xFFFBBF24))),
    CoverPreset("bokeh", "Bokeh lights", listOf(Color(0xFF1F1C2C), Color(0xFF928DAB), Color(0xFFFFC371), Color(0xFFFF5F6D))),
    CoverPreset("forest", "Misty forest", listOf(Color(0xFF0B3D2E), Color(0xFF2E8B57), Color(0xFFA8E6CF), Color(0xFF052016)))
)

val CoverPresets: List<CoverPreset> by lazy { NewCoverPresets + BaseCoverPresets + ExtraCoverPresets }

fun coverPresetFor(url: String, uid: String): CoverPreset? {
    if (url.startsWith("preset:")) return CoverPresets.firstOrNull { it.id == url.removePrefix("preset:") } ?: CoverPresets.first()
    if (url.isNotBlank()) return null
    // New default: about 1 in 5 profiles without a cover get "Convo Signature"; the rest keep
    // the default they already had, so nobody's profile suddenly changes.
    val hash = abs(uid.hashCode())
    if (hash % 5 == 0) return NewCoverPresets.first()
    return BaseCoverPresets[hash % BaseCoverPresets.size]
}

/** Draws a user's cover: an uploaded photo, a chosen preset, or their automatic default. */
@Composable
fun ProfileCoverArt(url: String, uid: String, modifier: Modifier = Modifier, scale: Float = 1f, offsetY: Float = 0f, animate: Boolean = true) {
    val preset = coverPresetFor(url, uid)
    if (preset == null) {
        AsyncImage(
            url, "Cover photo", contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize().graphicsLayer {
                scaleX = scale.coerceIn(1f, 2f); scaleY = scale.coerceIn(1f, 2f)
                translationY = offsetY.coerceIn(-1f, 1f) * 90f
            }
        )
    } else CoverPresetCanvas(preset, modifier, animate)
}

@Composable
fun CoverPresetCanvas(preset: CoverPreset, modifier: Modifier = Modifier, animate: Boolean = true) {
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "cover")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "cover_phase")
        value
    } else 0.25f
    Canvas(modifier.fillMaxSize()) { drawCover(preset, phase) }
}

private fun DrawScope.drawCover(preset: CoverPreset, phase: Float) {
    if (drawExtraCover(preset, phase)) return
    val c = preset.colors
    val w = size.width
    val h = size.height
    val t = phase * 2f * PI.toFloat()
    when (preset.id) {
        "aurora" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            repeat(3) { band ->
                val path = Path()
                val base = h * (0.35f + band * 0.12f)
                path.moveTo(0f, base)
                var x = 0f
                while (x <= w) {
                    path.lineTo(x, base + sin(x / w * 6f + t + band) * h * 0.08f)
                    x += w / 40f
                }
                path.lineTo(w, base + h * 0.25f); path.lineTo(0f, base + h * 0.25f); path.close()
                drawPath(path, Brush.verticalGradient(listOf((if (band % 2 == 0) c[2] else c[3]).copy(alpha = .55f), Color.Transparent), startY = base - h * .1f, endY = base + h * .25f))
            }
            stars(Color.White, 40, 0.55f, phase)
        }
        "sunset" -> {
            drawRect(Brush.verticalGradient(listOf(c[3], c[2], c[0], c[1])))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1B8), c[1].copy(alpha = 0f)), center = Offset(w * .68f, h * .55f), radius = h * .45f), radius = h * .45f, center = Offset(w * .68f, h * .55f))
            drawCircle(Color(0xFFFFE29A), radius = h * .16f, center = Offset(w * .68f, h * .58f + sin(t) * 4f))
            hills(c[2].copy(alpha = .85f), h * .70f, 0.9f, 0f)
            hills(c[3], h * .82f, 1.4f, 1.3f)
        }
        "ocean" -> {
            drawRect(Brush.verticalGradient(listOf(c[3], c[2], c[1], c[0])))
            repeat(4) { i ->
                val path = Path()
                val base = h * (0.45f + i * 0.14f)
                path.moveTo(0f, base)
                var x = 0f
                while (x <= w) { path.lineTo(x, base + sin(x / w * (5f + i) + t * (1 + i * .3f)) * h * .035f); x += w / 48f }
                path.lineTo(w, h); path.lineTo(0f, h); path.close()
                drawPath(path, c[0].copy(alpha = .25f + i * .15f))
            }
        }
        "galaxy" -> {
            drawRect(Brush.radialGradient(listOf(c[2], c[1], c[0]), center = Offset(w * .5f, h * .5f), radius = w * .7f))
            drawCircle(Brush.radialGradient(listOf(c[3].copy(alpha = .55f), Color.Transparent), center = Offset(w * .3f, h * .4f), radius = w * .3f), radius = w * .3f, center = Offset(w * .3f, h * .4f))
            stars(Color.White, 90, 1f, phase)
        }
        "neon" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1])))
            drawCircle(Brush.verticalGradient(listOf(c[2], Color(0xFFFFB86B))), radius = h * .22f, center = Offset(w / 2, h * .5f))
            val horizon = h * .6f
            drawRect(c[0], topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
            val lines = 9
            for (i in 0..lines) {
                val y = horizon + (h - horizon) * ((i + phase * 1f) % (lines + 1) / lines.toFloat()).let { it * it }
                drawLine(c[3].copy(alpha = .7f), Offset(0f, y), Offset(w, y), strokeWidth = 2f)
            }
            for (i in -8..8) drawLine(c[2].copy(alpha = .6f), Offset(w / 2, horizon), Offset(w / 2 + i * w / 6, h), strokeWidth = 2f)
        }
        "sakura" -> {
            drawRect(Brush.linearGradient(listOf(c[3], c[0], c[1])))
            repeat(22) { i ->
                val seed = i * 37.17f
                val x = ((seed * 13f) % w + phase * w * .3f) % w
                val y = ((seed * 7f) % h + phase * h) % h
                drawCircle(c[2].copy(alpha = .35f + (i % 3) * .15f), radius = 6f + (i % 5) * 4f, center = Offset(x, y))
            }
        }
        "mint" -> {
            drawRect(Brush.verticalGradient(listOf(c[2], c[1], c[0])))
            hills(c[0].copy(alpha = .55f), h * .55f, 1.1f, t * .2f)
            hills(c[0].copy(alpha = .8f), h * .7f, 1.6f, 1f + t * .3f)
            hills(c[3], h * .85f, 2.1f, 2f)
        }
        "prism" -> {
            drawRect(c[3])
            for (i in 0 until 7) {
                val path = Path()
                val x0 = w * (i / 6f) + sin(t + i) * 20f
                path.moveTo(x0, 0f); path.lineTo(x0 + w * .35f, h); path.lineTo(x0 - w * .15f, h); path.close()
                drawPath(path, Brush.linearGradient(listOf(c[i % 3].copy(alpha = .55f), Color.Transparent)))
            }
        }
        "gold" -> {
            drawRect(Brush.verticalGradient(listOf(c[3], c[2], c[1], c[0])))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .8f), Color.Transparent), center = Offset(w * .2f, h * .3f), radius = h * .6f), radius = h * .6f, center = Offset(w * .2f, h * .3f))
            hills(c[0].copy(alpha = .7f), h * .78f, 1.2f, .5f)
        }
        "city" -> {
            drawRect(Brush.verticalGradient(listOf(c[0], c[1], c[2])))
            stars(Color.White, 35, .5f, phase)
            drawCircle(Color(0xFFFFF7D6), radius = h * .08f, center = Offset(w * .82f, h * .22f))
            var x = 0f
            var i = 0
            while (x < w) {
                val bw = w * (0.05f + (i * 7 % 5) * 0.012f)
                val bh = h * (0.25f + (i * 11 % 7) * 0.06f)
                drawRect(c[0], topLeft = Offset(x, h - bh), size = Size(bw, bh))
                for (row in 0 until (bh / 18f).toInt()) for (col in 0 until (bw / 12f).toInt()) {
                    if ((row * 3 + col + i) % 4 == 0) drawRect(c[3].copy(alpha = .75f), topLeft = Offset(x + 4f + col * 12f, h - bh + 6f + row * 18f), size = Size(5f, 7f))
                }
                x += bw + 3f; i++
            }
        }
        "bokeh" -> {
            drawRect(Brush.linearGradient(listOf(c[0], c[1])))
            repeat(18) { i ->
                val x = (i * 97f) % w
                val y = (i * 53f + sin(t + i) * 12f) % h
                drawCircle((if (i % 2 == 0) c[2] else c[3]).copy(alpha = .22f), radius = 18f + (i % 6) * 10f, center = Offset(x, y))
            }
        }
        else -> { // forest
            drawRect(Brush.verticalGradient(listOf(c[2], c[1], c[0])))
            for (layer in 0..2) {
                val color = c[3].copy(alpha = .35f + layer * .3f)
                var x = -20f + layer * 15f
                while (x < w + 30f) {
                    val th = h * (0.35f + layer * .12f) + (x * 13 % 30)
                    val path = Path()
                    path.moveTo(x, h); path.lineTo(x + 22f, h - th); path.lineTo(x + 44f, h); path.close()
                    drawPath(path, color)
                    x += 30f + layer * 6f
                }
            }
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .25f), Color.Transparent), startY = h * .4f, endY = h * .8f))
        }
    }
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

/** Built-in cover picker with categories (Classic, Anime, Animated, Abstract, Nature). */
@Composable
fun CoverPresetPicker(selectedUrl: String, onSelect: (String) -> Unit) {
    val categories = remember { listOf("All") + CoverPresets.map { it.category }.distinct() }
    var category by remember { mutableStateOf("All") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Built-in covers · ${CoverPresets.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                val on = cat == category
                Text(
                    cat, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { category = cat }.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        val shown = CoverPresets.filter { category == "All" || it.category == category }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { preset ->
                val selected = selectedUrl == "preset:${preset.id}"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(14.dp))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .25f), RoundedCornerShape(14.dp))
                            .clickable { onSelect("preset:${preset.id}") }
                    ) {
                        CoverPresetCanvas(preset, Modifier.fillMaxSize(), animate = selected)
                        if (preset.category == "Animated" || preset.category == "Anime") {
                            Text(if (preset.category == "Anime") "ANIME" else "LIVE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(preset.name, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
