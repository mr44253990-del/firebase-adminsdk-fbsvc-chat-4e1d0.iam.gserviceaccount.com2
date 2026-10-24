package com.example.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.SurfaceViewRenderer

/**
 * Picture-in-Picture state shared by every activity that can host a call.
 * While [inPip] is true the call UI shows ONLY the call (remote video for video calls,
 * a compact caller card for audio calls) instead of a shrunken copy of the whole app.
 */
object CallPipState {
    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()
    fun set(value: Boolean) { _inPip.value = value }
}

private fun formatElapsed(connectedAt: Long): String {
    if (connectedAt <= 0L) return ""
    val s = ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun rememberTicker(connectedAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return remember(now, connectedAt) { formatElapsed(connectedAt) }
}

/** 1:1 call in PiP: remote video only (video call) or a compact caller card (audio call). */
@Composable
fun PrivateCallPipContent(state: CallState, accent: Color = Color(0xFF7C6CFF)) {
    val elapsed = rememberTicker(state.connectedAt)
    val showVideo = state.video && !state.remoteCameraOff
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showVideo) {
            AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).also {
                    it.setEnableHardwareScaler(true)
                    it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    CallEngine.attachRemoteRenderer(it)
                } },
                modifier = Modifier.fillMaxSize(),
                onRelease = CallEngine::detachRenderer
            )
            Row(
                Modifier.align(Alignment.BottomCenter).padding(6.dp).background(Color.Black.copy(alpha = .5f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.muted) { Icon(Icons.Default.MicOff, null, tint = Color.White, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(3.dp)) }
                Text(elapsed.ifBlank { state.status.replaceFirstChar { it.uppercase() } }, color = Color.White, fontSize = 10.sp)
            }
        } else {
            AudioPipCard(state.remoteName, state.remoteImage, elapsed.ifBlank { state.status.replaceFirstChar { it.uppercase() } }, state.muted, accent)
        }
    }
}

/** Group call in PiP: group name, participant faces and the timer. */
@Composable
fun GroupCallPipContent(state: GroupCallState, accent: Color = Color(0xFF26C6DA)) {
    val elapsed = rememberTicker(state.connectedAt)
    val pulse by rememberInfiniteTransition(label = "gpip").animateFloat(1f, 1.06f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "gp")
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101530), Color(0xFF1C2D55), Color(0xFF0B1022)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(8.dp)) {
            val faces = state.participants.take(3)
            if (faces.isEmpty()) Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(42.dp).scale(pulse))
            else Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                faces.forEach { p ->
                    AsyncImage(
                        p.image.ifBlank { null }, p.name, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).scale(pulse).clip(CircleShape).border(2.dp, accent, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(state.groupName.ifBlank { "Group call" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${state.participants.size + 1} in call${if (elapsed.isNotBlank()) " · $elapsed" else ""}", color = Color.White.copy(alpha = .78f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun AudioPipCard(name: String, image: String, status: String, muted: Boolean, accent: Color) {
    val pulse by rememberInfiniteTransition(label = "apip").animateFloat(1f, 1.08f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "ap")
    Box(Modifier.fillMaxSize()) {
        if (image.isNotBlank()) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC0F0C29), Color(0xEE302B63), Color(0xF524243E)))))
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).scale(pulse).background(accent.copy(alpha = .25f), CircleShape))
                AsyncImage(
                    image.ifBlank { null }, name, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = .85f), CircleShape)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(name.ifBlank { "Convo call" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (muted) { Icon(Icons.Default.MicOff, null, tint = Color.White, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(3.dp)) }
                Text(status, color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
            }
        }
    }
}
