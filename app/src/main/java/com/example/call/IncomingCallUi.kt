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
import androidx.compose.ui.graphics.graphicsLayer
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
 * Incoming-call controls, rebuilt on a strict three-slot grid so nothing floats out of place:
 *
 *   [ Decline ]   [ Message ]   [ Answer ]
 *
 * Decline and Answer are the same size and sit on the same baseline with their labels directly
 * underneath. Answer can be tapped or dragged up; a glass track above it shows the swipe.
 * The quick "Message" reply lives in the centre slot (the slot stays empty when unavailable,
 * which keeps the two main buttons perfectly symmetric).
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
    val thresholdPx = with(density) { 96.dp.toPx() }
    var lastAcceptAt by remember { mutableLongStateOf(0L) }
    val buzz = buzzEnvelope(phase)
    var lastTickCycle by remember { mutableIntStateOf(-1) }
    LaunchedEffect(phase < .05f) {
        if (phase < .05f) {
            val cycle = (System.currentTimeMillis() / CallRingtoneController.VIBRATION_PERIOD_MS).toInt()
            if (cycle != lastTickCycle) { lastTickCycle = cycle; view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
        }
    }
    val progress = (-dragY.value / thresholdPx).coerceIn(0f, 1f)
    fun accept() {
        val now = System.currentTimeMillis()
        if (now - lastAcceptAt < 1500L) return
        lastAcceptAt = now
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        onAccept()
        // If a permission prompt appears instead, the button springs back so the user can retry.
        scope.launch { dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }
    val buttonSize = 74.dp
    val slotHeight = 214.dp

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (progress > .05f) "Release to answer" else "Tap or swipe up to answer",
            color = Color.White.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = .3.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(slotHeight),
            verticalAlignment = Alignment.Bottom
        ) {
            // ---- Decline -------------------------------------------------------------
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                CallSlotButton(
                    size = buttonSize, label = "Decline", alpha = 1f - progress * .7f,
                    colors = listOf(Color(0xFFFF6B6B), Color(0xFFD92D3A)), glow = Color(0xFFFF4D5E),
                    icon = Icons.Default.CallEnd, onClick = onDecline
                )
            }
            // ---- Quick message (centre) ---------------------------------------------
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                if (onDeclineWithMessage != null) {
                    CallSlotButton(
                        size = 54.dp, label = "Message", alpha = 1f - progress * .7f,
                        colors = listOf(Color.White.copy(alpha = .26f), Color.White.copy(alpha = .12f)), glow = Color.Transparent,
                        icon = Icons.Default.Message, onClick = onDeclineWithMessage, iconSize = 24.dp, bottomInset = 10.dp
                    )
                }
            }
            // ---- Answer (tap or swipe up) -------------------------------------------
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (-dragY.value >= thresholdPx) accept()
                                else scope.launch { dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                            },
                            onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                scope.launch { dragY.snapTo((dragY.value + amount).coerceIn(-thresholdPx * 1.1f, 0f)) }
                            }
                        )
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Glass swipe track with animated chevrons, centred exactly above the button.
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
                        .size(width = buttonSize + 10.dp, height = 184.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .03f), Color(0xFF2EE86F).copy(alpha = .10f + .18f * progress))))
                        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .05f), Color(0xFF5BF29A).copy(alpha = .45f))), CircleShape),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 14.dp)) {
                        repeat(3) { i ->
                            val local = ((phase * 2f) - i * .18f).mod(1f)
                            Icon(
                                Icons.Default.KeyboardArrowUp, null,
                                tint = Color.White.copy(alpha = (sin(local * PI.toFloat()) * (1f - progress)).coerceIn(0f, 1f) * .9f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                val hop = if (progress > 0f) 0f else -8f * buzz
                val wiggle = if (progress > 0f) 0f else 14f * sin(phase * 2f * PI.toFloat() * 6f) * buzz
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .offset { IntOffset(0, (dragY.value + with(density) { hop.dp.toPx() }).roundToInt()) }
                            .size(buttonSize).scale(1f + .08f * progress + .04f * buzz)
                            .shadow(18.dp, CircleShape, ambientColor = Color(0xFF2EE86F), spotColor = Color(0xFF2EE86F))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF5BF29A), Color(0xFF16A34A))))
                            .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .12f))), CircleShape)
                            .clickable { accept() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (video) Icons.Default.Videocam else Icons.Default.Call, "Answer", tint = Color.White, modifier = Modifier.size(32.dp).rotate(wiggle))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Answer", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CallSlotButton(
    size: androidx.compose.ui.unit.Dp,
    label: String,
    alpha: Float,
    colors: List<Color>,
    glow: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 30.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val a = alpha.coerceIn(.3f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = bottomInset).graphicsLayerAlpha(a)) {
        Box(
            Modifier.size(size)
                .then(if (glow != Color.Transparent) Modifier.shadow(14.dp, CircleShape, ambientColor = glow, spotColor = glow) else Modifier)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .6f), Color.White.copy(alpha = .08f))), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(iconSize)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.graphicsLayerAlpha(value: Float): Modifier = this.then(Modifier.graphicsLayer { alpha = value })

/** Small glass chip above the caller: what kind of call this is, and that it is encrypted. */
@Composable
fun IncomingCallTypeChip(video: Boolean, accent: Color) {
    val phase by rememberRingPhase()
    Row(
        Modifier.clip(CircleShape)
            .background(Color.White.copy(alpha = .10f))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = .8f), Color.White.copy(alpha = .18f))), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).scale(1f + .5f * buzzEnvelope(phase)).clip(CircleShape).background(Color(0xFF2EE86F)))
        Spacer(Modifier.width(8.dp))
        Icon(if (video) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (video) "Convo video call" else "Convo audio call", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(12.dp))
    }
}
