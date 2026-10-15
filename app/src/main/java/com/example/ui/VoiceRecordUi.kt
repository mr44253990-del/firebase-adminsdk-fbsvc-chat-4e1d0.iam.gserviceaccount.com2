package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

private class EarlyRelease(val change: PointerInputChange?)

/**
 * Live gesture state shared between the mic button and the recording pill, so the pill can
 * animate "slide up to send / slide down to cancel" without every caller wiring extra state.
 * [fraction] is -1 (fully up = send) .. 0 (resting) .. 1 (fully down = cancel).
 */
object VoiceGesture {
    var fraction by mutableFloatStateOf(0f)
    var sendArmed by mutableStateOf(false)
    var cancelArmed by mutableStateOf(false)
    fun reset() { fraction = 0f; sendArmed = false; cancelArmed = false }
}

/**
 * Messenger-style voice button:
 *  - press and hold to record, release to send;
 *  - slide UP past the threshold to send right away;
 *  - slide DOWN past the threshold and release (or drag all the way) to cancel;
 *  - a quick tap only shows a hint, so voice notes are never sent by accident.
 * Recording state lives in the caller; this component only reports gestures.
 */
@Composable
fun HoldToRecordButton(
    isRecording: Boolean,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onHint: () -> Unit,
    onDragChange: (offsetY: Float, cancelArmed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cancelThresholdPx = with(density) { 64.dp.toPx() }
    val maxTravelPx = with(density) { 110.dp.toPx() }

    val latestPermission = rememberUpdatedState(permissionGranted)
    val latestRequest = rememberUpdatedState(onRequestPermission)
    val latestStart = rememberUpdatedState(onStart)
    val latestSend = rememberUpdatedState(onSend)
    val latestCancel = rememberUpdatedState(onCancel)
    val latestHint = rememberUpdatedState(onHint)
    val latestDrag = rememberUpdatedState(onDragChange)

    var dragY by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var sendArmed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isRecording) 1.45f else 1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMediumLow), label = "mic_scale")
    val animatedY by animateFloatAsState(if (isRecording) dragY else 0f, spring(stiffness = Spring.StiffnessHigh), label = "mic_drag")

    val bg = when {
        isRecording && armed -> Brush.linearGradient(listOf(Color(0xFF6B7280), Color(0xFF374151)))
        isRecording && sendArmed -> Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))
        isRecording -> Brush.linearGradient(listOf(Color(0xFFFF5A5F), Color(0xFFE53935)))
        else -> Brush.linearGradient(listOf(accent.copy(alpha = .18f), accent.copy(alpha = .10f)))
    }

    // The gesture is tracked on an untransformed outer box; only the inner visual moves and
    // scales, so pointer coordinates stay stable while the button follows the finger.
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = if (isRecording) "Release or slide up to send, slide down to cancel" else "Hold to record a voice message" }
            .testTag("voice_record_button")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (!latestPermission.value) {
                        latestRequest.value()
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    val early = withTimeoutOrNull(220L) { EarlyRelease(waitForUpOrCancellation()) }
                    if (early != null) {
                        if (early.change != null) latestHint.value()
                        return@awaitEachGesture
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val startedAt = System.currentTimeMillis()
                    dragY = 0f
                    armed = false
                    sendArmed = false
                    VoiceGesture.reset()
                    latestDrag.value(0f, false)
                    latestStart.value()
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: continue
                            if (!change.pressed) {
                                change.consume()
                                finished = true
                                val tooShort = System.currentTimeMillis() - startedAt < 700L
                                when {
                                    armed -> latestCancel.value()
                                    tooShort -> { latestCancel.value(); latestHint.value() }
                                    else -> latestSend.value()
                                }
                                break
                            }
                            val dy = (change.position.y - down.position.y).coerceIn(-maxTravelPx, maxTravelPx)
                            dragY = dy
                            VoiceGesture.fraction = (dy / maxTravelPx).coerceIn(-1f, 1f)
                            // Down = cancel (armed, committed on release or at full travel).
                            val nowArmed = dy > cancelThresholdPx
                            if (nowArmed != armed) {
                                armed = nowArmed
                                VoiceGesture.cancelArmed = nowArmed
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            // Up = send immediately once the finger crosses the threshold.
                            val nowSend = dy < -cancelThresholdPx
                            if (nowSend != sendArmed) {
                                sendArmed = nowSend
                                VoiceGesture.sendArmed = nowSend
                            }
                            latestDrag.value(dy, armed)
                            change.consume()
                            if (nowSend) {
                                finished = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (System.currentTimeMillis() - startedAt < 700L) { latestCancel.value(); latestHint.value() } else latestSend.value()
                                waitForUpOrCancellation()
                                break
                            }
                            if (dy >= maxTravelPx * .98f) {
                                finished = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestCancel.value()
                                waitForUpOrCancellation()
                                break
                            }
                        }
                    } catch (e: CancellationException) {
                        if (!finished) latestCancel.value()
                        throw e
                    } finally {
                        dragY = 0f
                        armed = false
                        sendArmed = false
                        VoiceGesture.reset()
                        latestDrag.value(0f, false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = animatedY
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, if (isRecording) Color.White.copy(.35f) else accent.copy(.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isRecording && armed -> Icons.Default.Delete
                    isRecording && sendArmed -> Icons.AutoMirrored.Filled.Send
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = if (isRecording) Color.White else accent,
                modifier = Modifier.size(if (isRecording) 20.dp else 23.dp)
            )
        }
    }
}

/** Recording status shown in place of the text field while the mic is held. */
@Composable
fun VoiceRecordingPill(
    startedAtMillis: Long,
    cancelArmed: Boolean,
    modifier: Modifier = Modifier
) {
    var elapsed by remember(startedAtMillis) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
            delay(250L)
        }
    }
    val pulse = rememberInfiniteTransition(label = "rec_pulse")
    val dotAlpha by pulse.animateFloat(1f, .25f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "rec_dot")
    val arrowShift by pulse.animateFloat(-3f, 3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "rec_arrow")
    val seconds = elapsed / 1000
    val cancel = cancelArmed || VoiceGesture.cancelArmed
    val send = VoiceGesture.sendArmed
    val fraction by animateFloatAsState(VoiceGesture.fraction, spring(stiffness = Spring.StiffnessMediumLow), label = "rec_fraction")
    val upTint = Color(0xFF10B981)
    val downTint = Color(0xFFE53935)
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .94f)
    val container = when {
        cancel -> MaterialTheme.colorScheme.errorContainer
        send -> upTint.copy(alpha = .22f)
        fraction > 0f -> androidx.compose.ui.graphics.lerp(base, MaterialTheme.colorScheme.errorContainer, fraction.coerceIn(0f, 1f))
        fraction < 0f -> androidx.compose.ui.graphics.lerp(base, upTint.copy(alpha = .22f), (-fraction).coerceIn(0f, 1f))
        else -> base
    }
    val onContainer = if (cancel) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer { scaleX = 1f - abs(fraction) * .03f; scaleY = 1f - abs(fraction) * .03f }
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .border(1.dp, Brush.horizontalGradient(listOf(downTint.copy(.35f + .4f * fraction.coerceAtLeast(0f)), upTint.copy(.25f + .5f * (-fraction).coerceAtLeast(0f)))), RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(downTint.copy(alpha = if (cancel) 1f else dotAlpha)))
        Spacer(Modifier.width(8.dp))
        Text("%d:%02d".format(seconds / 60, seconds % 60), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = onContainer)
        Spacer(Modifier.width(10.dp))
        VoiceLevelBars(active = !cancel, modifier = Modifier.weight(1f).height(22.dp))
        Spacer(Modifier.width(8.dp))
        when {
            cancel -> {
                Icon(Icons.Default.Delete, null, tint = downTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Release to cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onContainer)
            }
            send -> {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = upTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sending…", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = upTint)
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { translationY = -arrowShift.coerceAtLeast(0f) }) {
                        Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(13.dp), tint = upTint)
                        Text("Send", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = upTint)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { translationY = arrowShift.coerceAtLeast(0f) }) {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(13.dp), tint = downTint)
                        Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = downTint)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLevelBars(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "bars_phase")
    val color = MaterialTheme.colorScheme.primary
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(9) { i ->
            val wave = if (active) (kotlin.math.sin((phase + i / 9f) * 2 * Math.PI).toFloat() + 1f) / 2f else .15f
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(.25f + .75f * wave)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (active) .85f else .35f))
            )
        }
    }
}
