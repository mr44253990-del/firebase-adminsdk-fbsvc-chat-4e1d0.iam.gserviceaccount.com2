package com.example.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.service.CallRingtoneController
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Phase (0..1) of the ringing "ba-dum" vibration loop, so the incoming screen pulses exactly
 * when the phone buzzes. Falls back to the screen's own clock when no vibration is running.
 */
@Composable
fun rememberRingPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val localStart = System.currentTimeMillis()
        while (true) {
            withFrameMillis { _ ->
                val start = CallRingtoneController.startedAt.takeIf { it > 0L } ?: localStart
                val period = CallRingtoneController.VIBRATION_PERIOD_MS
                phase.floatValue = (((System.currentTimeMillis() - start) % period + period) % period) / period.toFloat()
            }
        }
    }
    return phase
}

/** 0..1 intensity of the two buzzes (260 ms, gap 140 ms, 420 ms) inside one 2 s loop. */
private fun buzzEnvelope(phase: Float): Float {
    val ms = phase * CallRingtoneController.VIBRATION_PERIOD_MS
    fun bump(start: Float, length: Float): Float =
        if (ms < start || ms > start + length) 0f else sin(((ms - start) / length) * PI.toFloat())
    return maxOf(bump(0f, 260f), bump(400f, 420f))
}

/** Animated backdrop for incoming calls: drifting light beams, sparkles and buzz-synced shock rings. */
@Composable
fun IncomingCallBackdrop(accent: Color, modifier: Modifier = Modifier) {
    val phase by rememberRingPhase()
    val motion = rememberInfiniteTransition(label = "incoming_backdrop")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "beam_spin")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "sparkle_drift")
    val buzz = buzzEnvelope(phase)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val center = Offset(w / 2f, h * .33f)
        // Slow rotating conic light behind the caller.
        rotate(spin, center) {
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color.Transparent, accent.copy(alpha = .22f), Color.Transparent, Color(0xFF7C4DFF).copy(alpha = .16f), Color.Transparent, Color(0xFF00E5FF).copy(alpha = .14f), Color.Transparent),
                    center
                ),
                radius = h, center = center
            )
        }
        // Soft glow that breathes with the vibration.
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .28f + .22f * buzz), Color.Transparent), center, w * (.55f + .08f * buzz)), w * (.55f + .08f * buzz), center)
        // Rising sparkles.
        repeat(26) { i ->
            val seed = (i * 73 % 100) / 100f
            val x = w * ((seed * 1.7f + i * .037f) % 1f)
            val progress = (drift + seed) % 1f
            val y = h * (1.05f - progress * 1.1f)
            val alpha = (sin(progress * PI.toFloat()) * .55f).coerceAtLeast(0f)
            drawCircle(Color.White.copy(alpha = alpha), radius = 1.5f + (i % 4), center = Offset(x + sin((progress + seed) * 6.28f) * 18f, y))
        }
        // Shock rings released on every buzz.
        repeat(3) { k ->
            val p = (phase + k / 3f) % 1f
            val r = w * (.18f + p * .62f)
            drawCircle(accent.copy(alpha = (1f - p) * .45f), r, center, style = Stroke(width = 3f + 5f * (1f - p)))
        }
    }
}

/** Caller avatar with ripple rings and a heartbeat bump on each vibration. */
@Composable
fun IncomingCallerAvatar(image: String, name: String, accent: Color) {
    val phase by rememberRingPhase()
    val buzz = buzzEnvelope(phase)
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(3) { k ->
                val p = (phase * 1.5f + k / 3f) % 1f
                drawCircle(accent.copy(alpha = (1f - p) * .55f), radius = size.minDimension * (.33f + .17f * p), style = Stroke(width = 4f * (1f - p) + 1f))
            }
        }
        AsyncImage(
            model = image.ifBlank { null }, contentDescription = name,
            error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
            modifier = Modifier.size(138.dp).scale(1f + .07f * buzz).clip(CircleShape)
                .border(4.dp, Brush.sweepGradient(listOf(accent, Color.White.copy(alpha = .9f), accent)), CircleShape)
        )
    }
}

/**
 * Incoming-call controls: Symmetrical Decline on the left, optional Quick Reply in the center,
 * and a pulsating Answer button on the right with tap or swipe-up gesture support.
 */
@Composable
fun IncomingAnswerControls(
    video: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDeclineWithMessage: (() -> Unit)? = null
) {
    val phase by rememberRingPhase()
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val thresholdPx = with(density) { 100.dp.toPx() }
    var lastAcceptAt by remember { mutableLongStateOf(0L) }
    val buzz = buzzEnvelope(phase)

    val progress = (-dragY.value / thresholdPx).coerceIn(0f, 1f)
    fun accept() {
        val now = System.currentTimeMillis()
        if (now - lastAcceptAt < 1500L) return
        lastAcceptAt = now
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        onAccept()
        scope.launch { dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Animated swipe hint
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp).offset(y = (-4 * buzz).dp)
            )
            Text(
                if (progress > .05f) "Release to answer" else "Swipe up or tap to answer",
                color = Color.White.copy(alpha = .85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Symmetrical action row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp)
                        .shadow(14.dp, CircleShape, ambientColor = Color(0xFFEF4444), spotColor = Color(0xFFEF4444))
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFB91C1C))))
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                            onDecline()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Decline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Message Reply button (if handler provided)
            if (onDeclineWithMessage != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                onDeclineWithMessage()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Message, "Reply", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Reply", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Answer button
            Box(
                Modifier.size(width = 84.dp, height = 96.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (-dragY.value >= thresholdPx) accept()
                                else scope.launch { dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                            },
                            onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                scope.launch { dragY.snapTo((dragY.value + amount).coerceIn(-thresholdPx * 1.25f, 0f)) }
                            }
                        )
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset { IntOffset(0, dragY.value.roundToInt()) }
                ) {
                    Box(
                        Modifier.size(72.dp).scale(1f + .08f * progress + .04f * buzz)
                            .shadow(16.dp, CircleShape, ambientColor = Color(0xFF22C55E), spotColor = Color(0xFF22C55E))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { accept() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (video) Icons.Default.Videocam else Icons.Default.Call,
                            "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Answer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
