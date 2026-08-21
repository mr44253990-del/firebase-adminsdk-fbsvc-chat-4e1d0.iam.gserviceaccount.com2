package com.example.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.Group
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCallScreen(
    group: Group,
    video: Boolean,
    onClose: () -> Unit,
    onMinimize: () -> Unit = {},
    onRoomReady: (String) -> Unit = {},
    joinRoomId: String? = null,
    autoStart: Boolean = true
) {
    val context = LocalContext.current
    val state by GroupCallEngine.state.collectAsState()
    var permissionResult by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var started by remember { mutableStateOf(false) }
    var joinRequested by remember { mutableStateOf(autoStart) }
    var showDiagnostics by remember { mutableStateOf(false) }
    BackHandler(enabled = started && state.status !in listOf("idle", "ended", "failed")) { onMinimize() }
    val localUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val isHost = state.hostId.isNotBlank() && state.hostId == localUid

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> permissionResult = result }

    LaunchedEffect(group.id, joinRequested) {
        if (!joinRequested) return@LaunchedEffect
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }
        val missing = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) permissionResult = needed.associateWith { true }
        else permissionsLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(permissionResult, joinRequested) {
        val result = permissionResult ?: return@LaunchedEffect
        val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!joinRequested || (!started && !audioGranted)) return@LaunchedEffect
        if (!started && audioGranted) {
            started = true
            val startCall: ((Boolean) -> Unit) -> Unit = { ready ->
                if (joinRoomId.isNullOrBlank()) {
                    GroupCallEngine.start(
                        app = context,
                        groupId = group.id,
                        groupName = group.name,
                        memberIds = group.members,
                        video = video && cameraGranted,
                        onReady = ready
                    )
                } else {
                    GroupCallEngine.join(
                        app = context,
                        roomId = joinRoomId,
                        groupId = group.id,
                        groupName = group.name,
                        memberIds = group.members,
                        video = video && cameraGranted,
                        onReady = ready
                    )
                }
            }
            startCall { ok ->
                if (!ok) started = false
                else if (joinRoomId.isNullOrBlank()) {
                    GroupCallEngine.state.value.roomId.takeIf { it.isNotBlank() }?.let(onRoomReady)
                }
            }
        }
    }

    // Do not end the engine when this composable is temporarily removed because
    // the activity is backgrounded, locked, rotated, or recreated. The foreground
    // service keeps the call alive; only an explicit End action should terminate it.

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            GroupCallEngine.startScreenShare(result.data!!)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name) },
                navigationIcon = {
                    IconButton(onClick = { GroupCallEngine.end(); onClose() }) {
                        Icon(Icons.Default.Close, "End call")
                    }
                },
                actions = {
                    if (state.video) {
                        IconButton(onClick = {
                            if (!GroupCallEngine.recoverLocalVideo()) {
                                Toast.makeText(context, "Camera recovery is unavailable right now", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Recover camera")
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = when (state.status) {
                            "connected" -> Color(0xFF1B5E20).copy(alpha = 0.16f)
                            "reconnecting" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            when (state.status) {
                                "connected" -> "Good connection"
                                "reconnecting" -> "Reconnecting"
                                "failed" -> "Call issue"
                                else -> "Connecting"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (state.status) {
                                "connected" -> Color(0xFF1B5E20)
                                "reconnecting", "failed" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                    if (state.captionsEnabled) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(end = 6.dp)) {
                            Text("CC", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                        }
                    }
                    if (state.recording) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Default.FiberManualRecord, "Recording", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Text("REC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                                                Text("${state.participants.size}/6", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))

                }
            )
        },
        bottomBar = {
            if (joinRequested && started) Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = { GroupCallEngine.toggleMute() }) {
                    Icon(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute")
                }
                FilledTonalIconButton(onClick = { GroupCallEngine.toggleCamera() }, enabled = state.video) {
                    Icon(if (state.cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, "Camera")
                }
                FilledTonalIconButton(onClick = { GroupCallEngine.switchCamera() }, enabled = state.video && !state.screenSharing) {
                    Icon(Icons.Default.Cameraswitch, "Switch camera")
                }
                FilledTonalIconButton(onClick = { GroupCallEngine.toggleSpeaker() }) {
                    Icon(if (state.speaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown, "Speaker")
                }
                FilledTonalIconButton(onClick = {
                    if (state.screenSharing) GroupCallEngine.stopScreenShare()
                    else {
                        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(manager.createScreenCaptureIntent())
                    }
                }, enabled = state.video) {
                    Icon(if (state.screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, "Screen share")
                }
                FilledTonalIconButton(onClick = { GroupCallEngine.toggleCaptions() }) {
                    Icon(Icons.Default.ClosedCaption, "Toggle captions", tint = if (state.captionsEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                if (isHost) {
                    FilledTonalIconButton(onClick = { GroupCallEngine.toggleRecordingHook() }) {
                        Icon(Icons.Default.FiberManualRecord, "Toggle recording", tint = if (state.recording) MaterialTheme.colorScheme.error else LocalContentColor.current)
                    }
                }
                FilledTonalIconButton(onClick = { showDiagnostics = !showDiagnostics }) {
                    Icon(Icons.Default.NetworkCheck, "Call diagnostics")
                }
                IconButton(onClick = { GroupCallEngine.end(); onClose() }) {
                    Icon(Icons.Default.CallEnd, "End call", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            if (!joinRequested) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f))
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Group call ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Join ${group.name} when you are ready. No one will be notified again when you re-open this room.")
                        Button(
                            onClick = {
                                permissionResult = null
                                joinRequested = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join Call")
                        }
                    }
                }
            } else if (state.status == "connecting" || state.status == "idle") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Connecting to ${group.name}…", modifier = Modifier.padding(16.dp))
            }
            if (state.status == "reconnecting") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    state.error ?: "Connection interrupted. Reconnecting…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (state.status == "failed") {
                Text(state.error ?: "Group call failed", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Button(
                    onClick = {
                        GroupCallEngine.end()
                        started = false
                        val cameraAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (joinRoomId.isNullOrBlank()) {
                            GroupCallEngine.start(context, group.id, group.name, group.members, video && cameraAllowed) { ok ->
                                started = ok
                                if (ok) GroupCallEngine.state.value.roomId.takeIf { it.isNotBlank() }?.let(onRoomReady)
                            }
                        } else {
                            GroupCallEngine.join(context, joinRoomId, group.id, group.name, group.members, video && cameraAllowed) { ok ->
                                started = ok
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Retry call")
                }
            }
            if (showDiagnostics && state.participants.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Call diagnostics", style = MaterialTheme.typography.titleSmall)
                        state.participants.forEach { participant ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(participant.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                Text(participant.quality.replaceFirstChar { it.uppercase() }, color = qualityColor(participant.quality), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(8.dp))
                                Text(participant.iceState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            val remoteParticipants = state.participants.filter { it.uid != localUid }
            val tileCount = remoteParticipants.size + 1
            var focusedParticipantId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(tileCount, remoteParticipants.map { it.uid }) {
                if (focusedParticipantId != null && focusedParticipantId != "local" && remoteParticipants.none { it.uid == focusedParticipantId }) {
                    focusedParticipantId = null
                }
            }
            val focusedRemote = remoteParticipants.firstOrNull { it.uid == focusedParticipantId }
            val focusedLocal = focusedParticipantId == "local"
            val gridColumns = when {
                tileCount <= 1 -> 1
                tileCount <= 4 -> 2
                else -> 3
            }
            val gridState = rememberLazyGridState()
            if (focusedParticipantId == null || (!focusedLocal && focusedRemote == null)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    item(key = "local-tile") {
                        CallVideoTile(
                            name = "You",
                            connected = state.status == "connected" || state.status == "reconnecting",
                            muted = state.muted,
                            cameraOff = state.cameraOff || !state.video,
                            isHost = false,
                            onMute = {},
                            onRemove = {},
                            onClick = { focusedParticipantId = "local" }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext -> SurfaceViewRenderer(viewContext) },
                                update = { renderer -> GroupCallEngine.attachLocalRenderer(renderer) },
                                onRelease = { GroupCallEngine.detachLocalRenderer(it) }
                            )
                        }
                    }
                    items(remoteParticipants, key = { it.uid }) { participant ->
                        CallVideoTile(
                            name = participant.name,
                            connected = participant.connected,
                            muted = participant.muted || participant.hostMuted,
                            cameraOff = !participant.video,
                            isHost = isHost,
                            onMute = { GroupCallEngine.hostMuteParticipant(participant.uid, !participant.hostMuted) },
                            onRemove = { GroupCallEngine.removeParticipant(participant.uid) },
                            onClick = { focusedParticipantId = participant.uid }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext -> SurfaceViewRenderer(viewContext) },
                                update = { renderer -> GroupCallEngine.attachRemoteRenderer(participant.uid, renderer) },
                                onRelease = { GroupCallEngine.detachRemoteRenderer(participant.uid, it) }
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp)) {
                    if (focusedLocal) {
                        CallVideoTile(
                            modifier = Modifier.fillMaxSize(),
                            name = "You",
                            connected = state.status == "connected" || state.status == "reconnecting",
                            muted = state.muted,
                            cameraOff = state.cameraOff || !state.video,
                            isHost = false,
                            onMute = {},
                            onRemove = {},
                            onClick = { focusedParticipantId = null }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext -> SurfaceViewRenderer(viewContext) },
                                update = { renderer -> GroupCallEngine.attachLocalRenderer(renderer) },
                                onRelease = { GroupCallEngine.detachLocalRenderer(it) }
                            )
                        }
                    } else {
                        val focused = focusedRemote!!
                        CallVideoTile(
                            modifier = Modifier.fillMaxSize(),
                            name = focused.name,
                            connected = focused.connected,
                            muted = focused.muted || focused.hostMuted,
                            cameraOff = !focused.video,
                            isHost = isHost,
                            onMute = { GroupCallEngine.hostMuteParticipant(focused.uid, !focused.hostMuted) },
                            onRemove = { GroupCallEngine.removeParticipant(focused.uid) },
                            onClick = { focusedParticipantId = null }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext -> SurfaceViewRenderer(viewContext) },
                                update = { renderer -> GroupCallEngine.attachRemoteRenderer(focused.uid, renderer) },
                                onRelease = { GroupCallEngine.detachRemoteRenderer(focused.uid, it) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(116.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!focusedLocal) CallVideoTile(
                        modifier = Modifier.width(112.dp).fillMaxHeight(),
                        name = "You",
                        connected = state.status == "connected" || state.status == "reconnecting",
                        muted = state.muted,
                        cameraOff = state.cameraOff || !state.video,
                        isHost = false,
                        onMute = {},
                        onRemove = {},
                        onClick = { focusedParticipantId = "local" }
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext -> SurfaceViewRenderer(viewContext) },
                            update = { renderer -> GroupCallEngine.attachLocalRenderer(renderer) },
                            onRelease = { GroupCallEngine.detachLocalRenderer(it) }
                        )
                    }
                    remoteParticipants.filter { it.uid != focusedParticipantId }.forEach { participant ->
                        CallVideoTile(
                            modifier = Modifier.width(112.dp).fillMaxHeight(),
                            name = participant.name,
                            connected = participant.connected,
                            muted = participant.muted || participant.hostMuted,
                            cameraOff = !participant.video,
                            isHost = isHost,
                            onMute = { GroupCallEngine.hostMuteParticipant(participant.uid, !participant.hostMuted) },
                            onRemove = { GroupCallEngine.removeParticipant(participant.uid) },
                            onClick = { focusedParticipantId = participant.uid }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext -> SurfaceViewRenderer(viewContext) },
                                update = { renderer -> GroupCallEngine.attachRemoteRenderer(participant.uid, renderer) },
                                onRelease = { GroupCallEngine.detachRemoteRenderer(participant.uid, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CallVideoTile(
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
    name: String,
    connected: Boolean,
    muted: Boolean,
    cameraOff: Boolean,
    isHost: Boolean,
    onMute: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    videoContent: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black,
        tonalElevation = 4.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!cameraOff && connected) {
                videoContent()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (connected) "Camera off" else "Connecting…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.60f)
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (muted) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.MicOff, "Muted", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (isHost) {
                Row(Modifier.align(Alignment.TopEnd).padding(5.dp)) {
                    IconButton(onClick = onMute, modifier = Modifier.size(30.dp)) {
                        Icon(if (muted) Icons.Default.Mic else Icons.Default.MicOff, "Toggle mute", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.PersonRemove, "Remove participant", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun qualityColor(quality: String): Color = when (quality) {
    "good" -> Color(0xFF1B5E20)
    "fair" -> Color(0xFF8A6D1D)
    "poor", "offline" -> Color(0xFFB3261E)
    else -> Color.Gray
}
