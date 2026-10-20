package com.example.call

import com.example.ui.drawThemeTexture
import com.example.ui.tr
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
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
    onPipCallOver: () -> Unit = {},
    onRoomReady: (String) -> Unit = {},
    joinRoomId: String? = null,
    autoStart: Boolean = true
) {
    val context = LocalContext.current
    val callSkinId by CallSkinPreferences.selectedId.collectAsState()
    val state by GroupCallEngine.state.collectAsState()
    LaunchedEffect(context.applicationContext) { CallSkinPreferences.load(context) }
    val callPalette = CallSkinPreferences.palette(callSkinId)
    var permissionResult by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var started by remember { mutableStateOf(false) }
    var joinRequested by remember { mutableStateOf(autoStart) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showMoreControls by remember { mutableStateOf(false) }
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
            // Returning to a call that is already running (from the floating pill): don't start a new one.
            GroupCallEngine.state.value.let { live -> if (live.groupId == group.id && live.status !in listOf("idle", "ended", "failed")) return@LaunchedEffect }
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

    val inPip by CallPipState.inPip.collectAsState()
    // The call ended while minimized: close the PiP window instead of leaving a frozen frame.
    var groupWasLive by remember { mutableStateOf(false) }
    LaunchedEffect(state.status, inPip) {
        if (state.status !in listOf("idle", "ended", "failed")) groupWasLive = true
        else if (groupWasLive && inPip) { kotlinx.coroutines.delay(700); onPipCallOver() }
    }
    if (inPip && state.status !in listOf("idle", "ended", "failed")) {
        GroupCallPipContent(state, callPalette.accent)
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    var elapsed by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(state.connectedAt, state.status) {
                        while (state.connectedAt > 0L && state.status != "ended") {
                            elapsed = (System.currentTimeMillis() - state.connectedAt) / 1000L
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                    Column {
                        Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(
                            (if (state.connectedAt > 0L) "%02d:%02d".format(elapsed / 60, elapsed % 60) + "  ·  " else "") +
                                "${state.participants.count { it.connected } + 1} in call",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .72f)
                        )
                    }
                },
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

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = callPalette.surface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (joinRequested && started) {
                Surface(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Black.copy(alpha = .46f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Brush.horizontalGradient(listOf(callPalette.accent.copy(alpha = .7f), Color.White.copy(alpha = .12f), callPalette.accent.copy(alpha = .5f))))
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GroupDockButton(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Unmute" else "Mute", state.muted, callPalette.accent) { GroupCallEngine.toggleMute() }
                    if (state.video) GroupDockButton(if (state.cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, "Camera", state.cameraOff, callPalette.accent) { GroupCallEngine.toggleCamera() }
                    GroupDockButton(if (state.speaker) Icons.Default.VolumeUp else Icons.Default.Hearing, if (state.speaker) "Speaker" else "Earpiece", state.speaker, callPalette.accent) { GroupCallEngine.toggleSpeaker() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = { GroupCallEngine.end(); onClose() },
                            modifier = Modifier.size(width = 64.dp, height = 48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.CallEnd, "End call")
                        }
                        Text("End", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                    }
                    Box {
                        GroupDockButton(Icons.Default.MoreHoriz, "More", showMoreControls, callPalette.accent) { showMoreControls = true }
                        DropdownMenu(
                            expanded = showMoreControls,
                            onDismissRequest = { showMoreControls = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (state.speaker) "Use earpiece" else "Use speaker") },
                                leadingIcon = { Icon(if (state.speaker) Icons.Default.Hearing else Icons.Default.VolumeUp, null) },
                                onClick = { GroupCallEngine.toggleSpeaker(); showMoreControls = false }
                            )
                            if (state.video) {
                                DropdownMenuItem(
                                    text = { Text("Switch camera") },
                                    leadingIcon = { Icon(Icons.Default.Cameraswitch, null) },
                                    onClick = { GroupCallEngine.switchCamera(); showMoreControls = false },
                                    enabled = !state.screenSharing
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.screenSharing) "Stop screen share" else "Share screen") },
                                    leadingIcon = { Icon(if (state.screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, null) },
                                    onClick = {
                                        if (state.screenSharing) GroupCallEngine.stopScreenShare()
                                        else {
                                            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                            projectionLauncher.launch(manager.createScreenCaptureIntent())
                                        }
                                        showMoreControls = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (state.captionsEnabled) "Disable captions" else "Enable captions") },
                                leadingIcon = { Icon(Icons.Default.ClosedCaption, null) },
                                onClick = { GroupCallEngine.toggleCaptions(); showMoreControls = false }
                            )
                            if (isHost) {
                                DropdownMenuItem(
                                    text = { Text(if (state.recording) "Stop recording" else "Start recording") },
                                    leadingIcon = { Icon(Icons.Default.FiberManualRecord, null) },
                                    onClick = { GroupCallEngine.toggleRecordingHook(); showMoreControls = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (showDiagnostics) "Hide diagnostics" else "Show diagnostics") },
                                leadingIcon = { Icon(Icons.Default.NetworkCheck, null) },
                                onClick = { showDiagnostics = !showDiagnostics; showMoreControls = false }
                            )
                        }
                    }
                }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(callPalette.background)).drawBehind { drawThemeTexture(callPalette.texture, callPalette.accent, 1.2f) }.padding(padding).padding(8.dp)) {
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
                            Text(tr("Join Call"))
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
                            image = participant.image,
                            accent = callPalette.accent,
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
                            image = focused.image,
                            accent = callPalette.accent,
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
                            image = participant.image,
                            accent = callPalette.accent,
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
private fun GroupDockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = .14f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = if (active) accent.copy(alpha = 1f).let { if (it.luminance() > .7f) Color.Black else it } else Color.White) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f), maxLines = 1)
    }
}

@Composable
private fun CallVideoTile(
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
    name: String,
    image: String = "",
    accent: Color = Color(0xFF7C4DFF),
    connected: Boolean,
    muted: Boolean,
    cameraOff: Boolean,
    isHost: Boolean,
    onMute: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    videoContent: @Composable () -> Unit
) {
    val hue = remember(name) { ((name.hashCode() and 0x7fffffff) % 360).toFloat() }
    val c1 = Color.hsv(hue, .55f, .55f)
    val c2 = Color.hsv((hue + 48f) % 360f, .65f, .28f)
    val pulse = rememberInfiniteTransition(label = "tile")
    val ring by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "ring")
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (connected && !muted) accent.copy(alpha = .85f) else Color.White.copy(alpha = .10f)),
        tonalElevation = 4.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!cameraOff && connected) {
                videoContent()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2))),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (connected && !muted) Box(
                            Modifier.size(70.dp).graphicsLayer { scaleX = ring; scaleY = ring; alpha = 1.6f - ring }
                                .border(2.dp, Color.White.copy(alpha = .55f), androidx.compose.foundation.shape.CircleShape)
                        )
                        Box(
                            modifier = Modifier.size(62.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = .18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            if (image.isNotBlank()) coil.compose.AsyncImage(
                                model = image, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.matchParentSize().clip(androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (connected) (if (muted) "Muted · camera off" else "Camera off") else "Connecting…", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (connected) Color(0xFF4CAF50) else Color(0xFFFFB300)))
                    Spacer(Modifier.width(5.dp))
                    Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (muted) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.MicOff, "Muted", tint = Color(0xFFFF8A80), modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (isHost) {
                Row(Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = .4f), RoundedCornerShape(14.dp))) {
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
