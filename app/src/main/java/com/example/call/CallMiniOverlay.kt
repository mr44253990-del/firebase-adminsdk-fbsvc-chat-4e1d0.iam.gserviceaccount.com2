package com.example.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import kotlin.math.roundToInt
import org.webrtc.SurfaceViewRenderer

/** Network level from WebRTC stats: 3 good, 2 fair, 1 weak, 0 unknown. */
@Composable
fun rememberCallSignalLevel(connected: Boolean): Pair<Int, Int?> {
    var level by remember { mutableIntStateOf(0) }
    var rtt by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(connected) {
        while (connected) {
            CallEngine.sampleQuality { r, loss ->
                rtt = r
                level = when {
                    r == null && loss == null -> 0
                    (loss ?: 0.0) > 8.0 || (r ?: 0) > 450 -> 1
                    (loss ?: 0.0) > 3.0 || (r ?: 0) > 220 -> 2
                    else -> 3
                }
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    return level to rtt
}

@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    val color = when (level) { 3 -> Color(0xFF42E695); 2 -> Color(0xFFFFD166); 1 -> Color(0xFFFF4D5E); else -> Color.White.copy(alpha = .55f) }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (bar in 1..4) {
            val lit = when (level) { 3 -> 4; 2 -> 3; 1 -> 1; else -> 0 } >= bar
            Box(Modifier.width(3.5.dp).height((3 + bar * 3).dp).clip(RoundedCornerShape(2.dp)).background(if (lit) color else Color.White.copy(alpha = .22f)))
        }
    }
}

/**
 * Floating "call in progress" bubble shown inside the app after the user leaves the call
 * screen. Shows the other person's photo, the live timer, network bars (red "Low signal"
 * when the connection is weak), mute state and an End button. Draggable; tap to return.
 */
@Composable
fun CallMiniOverlay(state: CallState, onExpand: () -> Unit, onEnd: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val callSkinId by CallSkinPreferences.selectedId.collectAsState()
    LaunchedEffect(context.applicationContext) { CallSkinPreferences.load(context) }
    val callPalette = CallSkinPreferences.palette(callSkinId)
    var seconds by remember(state.callId) { mutableLongStateOf(0L) }
    var dragX by remember(state.callId) { mutableFloatStateOf(0f) }
    var dragY by remember(state.callId) { mutableFloatStateOf(0f) }
    val connected = state.status == "connected"
    val (level, _) = rememberCallSignalLevel(connected)
    val weak = connected && level == 1
    val pulse = rememberInfiniteTransition(label = "mini_call_pulse")
    val ring by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "mini_ring")
    LaunchedEffect(state.connectedAt, state.status) {
        while (state.status == "connected" && state.connectedAt > 0) {
            seconds = (System.currentTimeMillis() - state.connectedAt) / 1000
            kotlinx.coroutines.delay(1000)
        }
    }
    val statusText = when (state.status) {
        "connected" -> "%02d:%02d".format(seconds / 60, seconds % 60)
        "calling" -> "Calling…"
        "ringing" -> "Ringing…"
        "reconnecting" -> "Reconnecting…"
        else -> "Connecting…"
    }
    val shape = RoundedCornerShape(if (state.video) 26.dp else 40.dp)
    Box(
        Modifier
            .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
            .pointerInput(state.callId) { detectDragGestures { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y } }
            .clip(shape)
            .background(Brush.linearGradient(listOf(callPalette.surface.copy(alpha = .96f), callPalette.accent.copy(alpha = .55f), callPalette.surface.copy(alpha = .96f))))
            .border(1.5.dp, if (weak) Color(0xFFFF4D5E) else callPalette.accent.copy(.85f), shape)
            .clickable(onClick = onExpand)
    ) {
        if (state.video && !state.remoteCameraOff) {
            Box(Modifier.size(width = 150.dp, height = 210.dp).background(Color.Black)) {
                AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).also(CallEngine::attachRemoteRenderer) }, modifier = Modifier.fillMaxSize(), onRelease = CallEngine::detachRenderer)
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(.5f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignalBars(level)
                    Spacer(Modifier.width(5.dp))
                    Text(if (weak) "Low signal" else statusText, color = if (weak) Color(0xFFFF6B78) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.7f)))).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.remoteName, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE53935)).clickable(onClick = onEnd), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CallEnd, "End", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            Row(Modifier.width(272.dp).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(54.dp).scale(if (connected) ring else 1f).clip(CircleShape).background(callPalette.accent.copy(alpha = .28f)))
                    AsyncImage(
                        state.remoteImage.ifBlank { null }, state.remoteName,
                        error = painterResource(R.drawable.img_app_logo), placeholder = painterResource(R.drawable.img_app_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(CircleShape).border(2.dp, Color.White.copy(.8f), CircleShape)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.remoteName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connected) { SignalBars(level); Spacer(Modifier.width(6.dp)) }
                        Text(
                            if (weak) "Low signal · $statusText" else statusText,
                            color = if (weak) Color(0xFFFF6B78) else Color.White.copy(.8f),
                            fontSize = 11.sp, fontWeight = if (weak) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = if (state.muted) .9f else .18f)).clickable { CallEngine.toggleMute() }, contentAlignment = Alignment.Center) {
                    Icon(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Unmute" else "Mute", tint = if (state.muted) Color(0xFF111827) else Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFE53935)))).clickable(onClick = onEnd), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CallEnd, "End", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
