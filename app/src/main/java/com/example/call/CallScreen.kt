package com.example.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import com.example.service.CallRingtoneController
import org.webrtc.SurfaceViewRenderer

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            }
            (getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.requestDismissKeyguard(this, null)
        } else @Suppress("DEPRECATION") {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        val callId = intent.getStringExtra("callId").orEmpty()
        val callerId = intent.getStringExtra("callerId").orEmpty()
        val callerName = intent.getStringExtra("callerName") ?: "Convo Chat user"
        val callerImage = intent.getStringExtra("callerImage").orEmpty()
        val videoCall = intent.getBooleanExtra("videoCall", false)
        val canRecord = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val canUseMedia = canRecord && (!videoCall || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        when (intent.action) {
            "com.ebchat.DECLINE_CALL" -> {
                CallRingtoneController.stop(this, callId)
                CallEngine.decline(callId); finish(); return
            }
            "com.ebchat.ACCEPT_CALL" -> {
                CallRingtoneController.stop(this, callId)
                if (canUseMedia) CallEngine.acceptIncoming(this, callId, callerId, callerName, callerImage, videoCall)
            }
        }
        setContent {
            MyApplicationTheme {
                CallScreen(
                    callId = callId, remoteUid = callerId, remoteName = callerName,
                    remoteImage = callerImage, incoming = true, video = videoCall,
                    initiallyAccepted = intent.action == "com.ebchat.ACCEPT_CALL" && canUseMedia,
                    onMinimize = { minimizeCallToPip() },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun minimizeCallToPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(9, 16))
                        .build()
                )
            }.onFailure { finish() }
        } else finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val active = CallEngine.state.value.status !in listOf("idle", "ended", "declined", "missed", "failed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && active && !isInPictureInPictureMode) minimizeCallToPip()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
fun CallScreen(
    callId: String,
    remoteUid: String,
    remoteName: String,
    remoteImage: String,
    incoming: Boolean,
    video: Boolean = false,
    initiallyAccepted: Boolean = false,
    onEndCall: () -> Unit = { CallEngine.end() },
    onMinimize: () -> Unit = {},
    onClose: () -> Unit,
    onAddParticipant: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by CallEngine.state.collectAsState()
    val effectiveVideo = video || state.video
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val callMotion = rememberInfiniteTransition(label = "call_liquid_motion")
    val callPulse by callMotion.animateFloat(.94f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "call_pulse")
    LaunchedEffect(effectiveVideo, state.screenSharing) {
        val window = (view.context as? Activity)?.window
        if (state.screenSharing) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (effectiveVideo) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(state.connectedAt, state.status) {
        while (state.status == "connected" && state.connectedAt > 0L) {
            elapsedSeconds = (System.currentTimeMillis() - state.connectedAt) / 1000L
            kotlinx.coroutines.delay(1000)
        }
    }
    var accepted by remember { mutableStateOf(initiallyAccepted || !incoming) }
    BackHandler(enabled = true) { if (accepted) onMinimize() }
    var pendingAccept by remember { mutableStateOf(false) }
    var showMoreControls by remember { mutableStateOf(false) }
    val screenShareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (!CallEngine.startScreenShare(result.data!!)) android.widget.Toast.makeText(context, "Could not start screen sharing", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results[Manifest.permission.RECORD_AUDIO] == true && (!effectiveVideo || results[Manifest.permission.CAMERA] == true)
        if (granted && pendingAccept) {
            pendingAccept = false; accepted = true
            CallRingtoneController.stop(context, callId)
            CallEngine.acceptIncoming(context, callId, remoteUid, remoteName, remoteImage, effectiveVideo)
        } else pendingAccept = false
    }
    LaunchedEffect(state.status) { if (state.status in listOf("ended", "declined", "missed", "failed")) kotlinx.coroutines.delay(900).also { onClose() } }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080A12), Color(0xFF28204B), Color(0xFF071B26)))),
        contentAlignment = Alignment.Center
    ) {
        if (remoteImage.isNotBlank() && !effectiveVideo) AsyncImage(remoteImage, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(70.dp))
        if (effectiveVideo && accepted) {
            if (state.remoteCameraOff) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090A13), Color(0xFF26213F)))), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(remoteImage.ifBlank { null }, remoteName, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp).clip(CircleShape).border(3.dp, Color.White.copy(.75f), CircleShape))
                        Spacer(Modifier.height(14.dp)); Text("$remoteName turned off the camera", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).also {
                    it.setEnableHardwareScaler(true)
                    it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    CallEngine.attachRemoteRenderer(it)
                } },
                modifier = Modifier.fillMaxSize(),
                onRelease = CallEngine::detachRenderer
            )
            if (!state.cameraOff && !state.screenSharing) AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).also {
                    it.setEnableHardwareScaler(true)
                    it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    CallEngine.attachLocalRenderer(it)
                } },
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(14.dp).size(width = 104.dp, height = 148.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(.6f), RoundedCornerShape(26.dp)),
                onRelease = CallEngine::detachRenderer
            )
        }
        if (accepted) IconButton(
            onClick = onMinimize,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).background(Color.Black.copy(.42f), CircleShape)
        ) { Icon(Icons.Default.KeyboardArrowDown, "Minimize call", tint = Color.White, modifier = Modifier.size(30.dp)) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(if (effectiveVideo && accepted) .06f else .18f))
            AnimatedVisibility(visible = !effectiveVideo || !accepted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = remoteImage.ifBlank { null }, contentDescription = remoteName,
                        error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                        modifier = Modifier.size(138.dp).scale(if (!accepted && incoming) callPulse else 1f).clip(CircleShape).border(4.dp, Brush.sweepGradient(listOf(Color(0xFF66E5FF),Color(0xFF9A6CFF),Color(0xFFFF5BAD),Color(0xFF66E5FF))), CircleShape)
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(remoteName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            }
            Surface(color = Color.Black.copy(alpha = if (effectiveVideo && accepted) .45f else 0f), shape = CircleShape) {
            Text(
                when {
                    state.error != null -> state.error!!
                    !accepted && incoming -> if (effectiveVideo) "Incoming Convo Chat video call" else "Incoming Convo Chat audio call"
                    state.status == "connected" -> "Connected • %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
                    state.status == "calling" -> "Calling…"
                    state.status == "ringing" -> "Ringing…"
                    state.status == "reconnecting" -> "Reconnecting…"
                    else -> "Connecting securely…"
                }, color = Color.White.copy(.82f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
            }
            Spacer(Modifier.weight(1f))
            if (!accepted && incoming) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallCircleButton(Color(0xFFE53935), Icons.Default.CallEnd, "Decline") { CallRingtoneController.stop(context, callId); CallEngine.decline(callId); onClose() }
                    CallCircleButton(Color(0xFF36C76C), Icons.Default.Call, "Accept") {
                        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        val cameraGranted = !effectiveVideo || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (micGranted && cameraGranted) {
                            CallRingtoneController.stop(context, callId)
                            accepted = true; CallEngine.acceptIncoming(context, callId, remoteUid, remoteName, remoteImage, effectiveVideo)
                        } else {
                            pendingAccept = true
                            callPermissionLauncher.launch(if (effectiveVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        CallCircleButton(Color.White.copy(.15f), if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Unmute" else "Mute") { CallEngine.toggleMute() }
                        CallCircleButton(Color(0xFFE53935), Icons.Default.CallEnd, "End") { onEndCall(); onClose() }
                        Box {
                            CallCircleButton(Color.White.copy(.15f), Icons.Default.MoreVert, "More") { showMoreControls = true }
                            DropdownMenu(
                                expanded = showMoreControls,
                                onDismissRequest = { showMoreControls = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (state.speaker) "Use earpiece" else "Use speaker") },
                                    leadingIcon = { Icon(if (state.speaker) Icons.Default.Hearing else Icons.Default.VolumeUp, null) },
                                    onClick = { CallEngine.toggleSpeaker(); showMoreControls = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add participant") },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                    onClick = { onAddParticipant(); showMoreControls = false }
                                )
                                if (effectiveVideo) {
                                    DropdownMenuItem(
                                        text = { Text(if (state.cameraOff) "Turn camera on" else "Turn camera off") },
                                        leadingIcon = { Icon(if (state.cameraOff) Icons.Default.Videocam else Icons.Default.VideocamOff, null) },
                                        onClick = { CallEngine.toggleCamera(); showMoreControls = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Flip camera") },
                                        leadingIcon = { Icon(Icons.Default.Cameraswitch, null) },
                                        onClick = { CallEngine.switchCamera(); showMoreControls = false },
                                        enabled = !state.screenSharing
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (state.screenSharing) "Stop screen share" else "Share screen") },
                                    leadingIcon = { Icon(if (state.screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, null) },
                                    onClick = {
                                        if (state.screenSharing) CallEngine.stopScreenShare()
                                        else {
                                            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                            screenShareLauncher.launch(manager.createScreenCaptureIntent())
                                        }
                                        showMoreControls = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Text("Secured with WebRTC • Cloudflare TURN", color = Color.White.copy(.4f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CallCircleButton(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(66.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = color)) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp)); Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
