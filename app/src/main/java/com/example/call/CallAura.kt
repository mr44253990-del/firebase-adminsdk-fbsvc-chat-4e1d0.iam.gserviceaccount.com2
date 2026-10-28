package com.example.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated caller avatar for 1:1 calls (coded, no emoji/assets):
 *  - "calling"/"ringing": sonar ripples + rotating comet arc + orbiting satellites.
 *  - "connected": a calm breathing halo with a circular voice-wave equalizer ring.
 *  - anything else (connecting/reconnecting): a dashed spinner ring.
 */
@Composable
fun CallAvatarAura(image: String, name: String, accent: Color, status: String, muted: Boolean = false, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aura")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "spin")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave")
    val breathe by t.animateFloat(.97f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "breathe")
    val ringing = status == "calling" || status == "ringing"
    val connected = status == "connected"
    Box(modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(230.dp)) {
            val c = center
            val avatarR = 69.dp.toPx()
            when {
                ringing -> {
                    repeat(3) { k ->
                        val p = (wave + k / 3f) % 1f
                        drawCircle(accent.copy(alpha = (1f - p) * .45f), radius = avatarR + (size.minDimension / 2 - avatarR) * p, center = c, style = Stroke(width = 2.dp.toPx() * (1.4f - p)))
                    }
                    rotate(spin, c) {
                        val r = avatarR + 12.dp.toPx()
                        drawArc(
                            Brush.sweepGradient(listOf(Color.Transparent, accent.copy(alpha = .2f), accent, Color.White), center = c),
                            startAngle = 0f, sweepAngle = 300f, useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    repeat(3) { k ->
                        val a = Math.toRadians((spin * 1.4 + k * 120.0) % 360.0)
                        val r = avatarR + 26.dp.toPx()
                        drawCircle(if (k == 0) Color.White else accent, radius = (4 - k).dp.toPx(), center = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()))
                    }
                }
                connected -> {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .35f), Color.Transparent), center = c, radius = avatarR * 1.7f * breathe), radius = avatarR * 1.7f * breathe, center = c)
                    val bars = 48
                    for (i in 0 until bars) {
                        val ang = 2 * PI * i / bars - PI / 2
                        val amp = if (muted) .12f else (.35f + .65f * ((sin(ang * 3 + wave * 2 * PI) * sin(ang * 5 - wave * 4 * PI) + 1) / 2).toFloat())
                        val r0 = avatarR + 10.dp.toPx()
                        val r1 = r0 + 6.dp.toPx() + 18.dp.toPx() * amp
                        drawLine(
                            accent.copy(alpha = .45f + .5f * amp),
                            Offset(c.x + (r0 * cos(ang)).toFloat(), c.y + (r0 * sin(ang)).toFloat()),
                            Offset(c.x + (r1 * cos(ang)).toFloat(), c.y + (r1 * sin(ang)).toFloat()),
                            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                }
                else -> rotate(spin * 1.5f, c) {
                    val r = avatarR + 12.dp.toPx()
                    for (i in 0 until 12) drawArc(
                        accent.copy(alpha = .25f + .75f * i / 12f), startAngle = i * 30f, sweepAngle = 16f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        AsyncImage(
            model = image.ifBlank { null }, contentDescription = name,
            error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
            modifier = Modifier.size(138.dp).scale(if (connected) breathe else 1f).clip(CircleShape)
                .border(3.dp, Brush.sweepGradient(listOf(accent, Color.White.copy(alpha = .9f), accent)), CircleShape)
        )
    }
}
