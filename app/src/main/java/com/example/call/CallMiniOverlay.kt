package com.example.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallMiniOverlay(state: CallState, onExpand: () -> Unit, onEnd: () -> Unit) {
    var seconds by remember(state.callId) { mutableLongStateOf(0L) }
    var dragX by remember(state.callId) { mutableFloatStateOf(0f) }
    var dragY by remember(state.callId) { mutableFloatStateOf(0f) }
    LaunchedEffect(state.connectedAt, state.status) {
        while (state.status == "connected" && state.connectedAt > 0) {
            seconds = (System.currentTimeMillis() - state.connectedAt) / 1000
            kotlinx.coroutines.delay(1000)
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp), tonalElevation = 10.dp, shadowElevation = 12.dp,
        color = Color(0xEE121421),
        modifier = Modifier.offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
            .width(if (state.video) 158.dp else 270.dp).height(if (state.video) 208.dp else 76.dp)
            .pointerInput(state.callId) { detectDragGestures { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y } }
            .border(1.dp, Color(0xFF8B72FF).copy(.7f), RoundedCornerShape(28.dp)).clickable(onClick = onExpand)
    ) {
        if (state.video) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).also(CallEngine::attachRemoteRenderer) }, modifier = Modifier.fillMaxSize(), onRelease = CallEngine::detachRenderer)
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(.55f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.remoteName} • %02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEnd, modifier = Modifier.size(34.dp).background(Color(0xFFE53935), CircleShape)) { Icon(Icons.Default.CallEnd, "End", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(state.remoteImage.ifBlank { null }, state.remoteName, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
                Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(state.remoteName, color = Color.White, maxLines = 1); Text("%02d:%02d • tap to return".format(seconds / 60, seconds % 60), color = Color.White.copy(.68f), style = MaterialTheme.typography.labelSmall) }
                IconButton(onClick = onEnd, modifier = Modifier.background(Color(0xFFE53935), CircleShape)) { Icon(Icons.Default.CallEnd, "End", tint = Color.White) }
            }
        }
    }
}
