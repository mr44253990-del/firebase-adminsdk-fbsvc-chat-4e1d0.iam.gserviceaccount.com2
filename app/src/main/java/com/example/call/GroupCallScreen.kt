package com.example.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onRoomReady: (String) -> Unit = {},
    joinRoomId: String? = null
) {
    val context = LocalContext.current
    val state by GroupCallEngine.state.collectAsState()
    var permissionResult by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var started by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> permissionResult = result }

    LaunchedEffect(group.id) {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }
        val missing = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) permissionResult = needed.associateWith { true }
        else permissionsLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(permissionResult) {
        val result = permissionResult ?: return@LaunchedEffect
        val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
                else GroupCallEngine.state.value.roomId.takeIf { it.isNotBlank() }?.let(onRoomReady)
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
                    Text("${state.participants.size}/4", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = { GroupCallEngine.toggleMute() }) {
                    Icon(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute")
                }
                FilledTonalIconButton(onClick = { GroupCallEngine.toggleCamera() }, enabled = state.video) {
                    Icon(if (state.cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, "Camera")
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
                IconButton(onClick = { GroupCallEngine.end(); onClose() }) {
                    Icon(Icons.Default.CallEnd, "End call", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            if (state.status == "connecting" || state.status == "idle") {
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
            if (state.status != "idle") {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                            factory = { viewContext -> SurfaceViewRenderer(viewContext).also {
                                it.setEnableHardwareScaler(true)
                                it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                GroupCallEngine.attachLocalRenderer(it)
                            } },
                            update = { GroupCallEngine.refreshLocalRenderer() },
                            onRelease = { GroupCallEngine.detachLocalRenderer(it) }
                )
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(state.participants.filter { it.uid != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }, key = { it.uid }) { participant ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(participant.name + if (participant.muted) " (muted)" else "", style = MaterialTheme.typography.labelMedium)
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(190.dp).background(Color.Black, RoundedCornerShape(12.dp)),
                            factory = { viewContext -> SurfaceViewRenderer(viewContext).also {
                                it.setEnableHardwareScaler(true)
                                it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                GroupCallEngine.attachRemoteRenderer(participant.uid, it)
                            } },
                            onRelease = { GroupCallEngine.detachRemoteRenderer(participant.uid, it) }
                        )
                    }
                }
            }
        }
    }
}
