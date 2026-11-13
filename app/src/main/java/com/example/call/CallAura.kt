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
import kotlin.math.sqrt
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis

/**
 * Samples microphone amplitude OFF the main composition: a background coroutine continuously
 * reads a small AudioRecord buffer into a @Volatile float, and a LaunchedEffect loop with
 * withFrameMillis throttles reads to ~30 fps into a mutableState. Never collect flows or
 * block inside draw/drawBehind/canvas lambdas — they only read the finished state value.
 */
@Composable
fun rememberMicAmplitude(enabled: Boolean): State<Float> {
    val amplitude = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) { amplitude.floatValue = 0f; return@LaunchedEffect }
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return@LaunchedEffect
        @Volatile var latest = 0f
        val sampler = launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            val buffer = ShortArray(128)
            try {
                val minBuf = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf.coerceAtLeast(buffer.size * 2))
                if (recorder.state == AudioRecord.STATE_INITIALIZED) recorder.startRecording()
                while (true) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) sum += buffer[i] * buffer[i]
                        latest = (sqrt(sum / read) / 32768f).coerceIn(0f, 1f)
                    }
                }
            } catch (_: Throwable) { latest = 0f }
            finally { runCatching { recorder?.stop(); recorder?.release() } }
        }
        try {
            var lastSample = 0L
            while (true) {
                withFrameMillis { now ->
                    if (now - lastSample >= 33L) { // ~30 fps throttle
                        lastSample = now
                        // Smooth so the aura moves fluidly instead of jittering per sample.
                        amplitude.floatValue = amplitude.floatValue * 0.55f + latest * 0.45f
                    }
                }
            }
        } finally { sampler.cancel() }
    }
    return amplitude
}

/**
 * Animated caller avatar for 1:1 calls (coded, no emoji/assets):
 *  - "calling"/"ringing": sonar ripples + rotating comet arc + orbiting satellites.
 *  - "connected": a calm breathing halo with a circular voice-wave equalizer ring.
 *  - anything else (connecting/reconnecting): a dashed spinner ring.
 */
@Composable
fun CallAvatarAura(image: String, name: String, accent: Color, status: String, muted: Boolean = false, amplitude: Float = 0f, modifier: Modifier = Modifier) {
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
                    // Aura radius + alpha breathe with the throttled mic amplitude (never sampled here).
                    val drive = if (muted) 0f else amplitude.coerceIn(0f, 1f)
                    val haloR = avatarR * (1.55f + 0.35f * drive) * breathe
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .28f + .32f * drive), Color.Transparent), center = c, radius = haloR), radius = haloR, center = c)
                    val bars = 48
                    for (i in 0 until bars) {
                        val ang = 2 * PI * i / bars - PI / 2
                        val synth = (.35f + .65f * ((sin(ang * 3 + wave * 2 * PI) * sin(ang * 5 - wave * 4 * PI) + 1) / 2).toFloat())
                        val amp = if (muted) .12f else (synth * (0.45f + 0.55f * drive))
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
