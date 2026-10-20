package com.example.call

import com.example.ui.ThemeTextureLayer
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    companion object { @Volatile var alive: Boolean = false }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = true
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
                    // Back = stay inside Convo Chat with the floating call bubble.
                    onMinimize = { returnToAppWithCallBubble() },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun returnToAppWithCallBubble() {
        val active = CallEngine.state.value.status !in listOf("idle", "ended", "declined", "missed", "failed")
        if (!active) { finish(); return }
        val keyguard = getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguard?.isKeyguardLocked == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { openMainAndFinish() }
            })
            return
        }
        openMainAndFinish()
    }

    private var leavingToApp = false

    private fun openMainAndFinish() {
        leavingToApp = true
        runCatching {
            startActivity(Intent(this, com.example.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
        }
        finish()
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CallPipState.set(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val active = CallEngine.state.value.status !in listOf("idle", "ended", "declined", "missed", "failed")
        // PiP only for live video calls; audio calls continue in the background with the ongoing notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && active && !leavingToApp && CallEngine.state.value.video && CallEngine.state.value.status in listOf("connected", "reconnecting") && !isInPictureInPictureMode) minimizeCallToPip()
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
    val callSkinId by CallSkinPreferences.selectedId.collectAsState()
    val state by CallEngine.state.collectAsState()
    LaunchedEffect(context.applicationContext) { CallSkinPreferences.load(context) }
    val callPalette = CallSkinPreferences.palette(callSkinId)
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
    val activeForLock = state.status !in listOf("idle", "ended", "declined", "missed", "failed")
    DisposableEffect(activeForLock) {
        val activity = view.context as? Activity
        // Locking the phone during a call keeps the full call screen on top of the lock screen.
        if (activeForLock && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity?.setShowWhenLocked(true); activity?.setTurnScreenOn(true)
        }
        onDispose {
            if (activity != null && activity !is IncomingCallActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(false); activity.setTurnScreenOn(false)
            }
        }
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
    // Minimize (mini bubble / PiP) only while a call is really running; otherwise just leave.
    val canMinimize = accepted && activeForLock
    BackHandler(enabled = true) { if (canMinimize) onMinimize() else if (!activeForLock) onClose() }
    var pendingAccept by remember { mutableStateOf(false) }
    var showMoreControls by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    // Video calls: one tap hides/shows every control, double tap flips the camera.
    var controlsVisible by remember { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }
    // Comfort options: fill/fit remote video, hide or drag the self-view to any corner.
    var fillVideo by remember { mutableStateOf(true) }
    var showSelfView by remember { mutableStateOf(true) }
    var selfOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val inPip by CallPipState.inPip.collectAsState()
    val screenShareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (!CallEngine.startScreenShare(result.data!!)) android.widget.Toast.makeText(context, "Could not start screen sharing", android.widget.Toast.LENGTH_LONG).show()
            else {
                android.widget.Toast.makeText(context, "Screen sharing is on — Convo moved to the background", android.widget.Toast.LENGTH_SHORT).show()
                // Leave the app so the other person sees your home screen / other apps right away.
                view.postDelayed({ (view.context as? Activity)?.moveTaskToBack(true) }, 700)
            }
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
    // Close the call UI whenever the call is over, including when the engine resets straight to "idle"
    // (remote hang-up during PiP) so a frozen frame never stays on screen.
    var wasLive by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status !in listOf("idle", "ended", "declined", "missed", "failed")) wasLive = true
        if (state.status in listOf("ended", "declined", "missed", "failed") || (state.status == "idle" && wasLive)) {
            kotlinx.coroutines.delay(900)
            CallPipState.set(false)
            onClose()
        }
    }
    if (inPip && accepted) {
        // Picture-in-Picture: only the call — never a shrunken copy of the whole screen.
        PrivateCallPipContent(state, callPalette.accent)
        return
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(callPalette.background)),
        contentAlignment = Alignment.Center
    ) {
        ThemeTextureLayer(callPalette.texture, callPalette.accent, 1.3f)
        if (remoteImage.isNotBlank() && !effectiveVideo) {
            AsyncImage(remoteImage, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(70.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(callPalette.background.map { it.copy(alpha = .72f) })))
        }
        if (!accepted && incoming) IncomingCallBackdrop(callPalette.accent)
        else if (!effectiveVideo) CallAuroraBackground(callPalette.accent)
        if (effectiveVideo && accepted) {
            if (state.remoteCameraOff) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(callPalette.background)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(remoteImage.ifBlank { null }, remoteName, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp).clip(CircleShape).border(3.dp, callPalette.accent.copy(.82f), CircleShape))
                        Spacer(Modifier.height(14.dp)); Text("$remoteName turned off the camera", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).also {
                    it.setEnableHardwareScaler(true)
                    it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    CallEngine.attachRemoteRenderer(it)
                } },
                update = { it.setScalingType(if (fillVideo) org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL else org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT) },
                modifier = Modifier.fillMaxSize(),
                onRelease = CallEngine::detachRenderer
            )
            if (!state.cameraOff && !state.screenSharing && showSelfView) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val conf = androidx.compose.ui.platform.LocalConfiguration.current
                // Drag the self-view anywhere; it snaps to the nearest corner when released.
                val maxX = with(density) { (conf.screenWidthDp.dp - 104.dp - 28.dp).toPx() }.coerceAtLeast(0f)
                val maxY = with(density) { (conf.screenHeightDp.dp - 148.dp - 220.dp).toPx() }.coerceAtLeast(0f)
                var dragging by remember { mutableStateOf(false) }
                val animatedSelf by animateOffsetAsState(selfOffset, spring(dampingRatio = .72f, stiffness = 420f), label = "self_view")
                AndroidView(
                    factory = { ctx -> SurfaceViewRenderer(ctx).also {
                        it.setEnableHardwareScaler(true)
                        it.setZOrderMediaOverlay(true)
                        it.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        CallEngine.attachLocalRenderer(it)
                    } },
                    modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(14.dp)
                        .offset { (if (dragging) selfOffset else animatedSelf).let { androidx.compose.ui.unit.IntOffset(it.x.toInt(), it.y.toInt()) } }
                        .pointerInput(maxX, maxY) {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    selfOffset = androidx.compose.ui.geometry.Offset(if (selfOffset.x < -maxX / 2f) -maxX else 0f, if (selfOffset.y > maxY / 2f) maxY else 0f)
                                },
                                onDragCancel = { dragging = false }
                            ) { change, drag ->
                                change.consume()
                                selfOffset = androidx.compose.ui.geometry.Offset((selfOffset.x + drag.x).coerceIn(-maxX, 0f), (selfOffset.y + drag.y).coerceIn(0f, maxY))
                            }
                        }
                        .size(width = 104.dp, height = 148.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(.6f), RoundedCornerShape(26.dp)),
                    onRelease = CallEngine::detachRenderer
                )
            }
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; if (!controlsVisible) showFilters = false },
                        onDoubleTap = {
                            if (!CallEngine.state.value.screenSharing && !CallEngine.state.value.cameraOff) {
                                CallEngine.switchCamera()
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                    )
                }
            )
            if (controlsVisible && !state.screenSharing && !state.cameraOff) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlassCallButton(Icons.Default.Cameraswitch, if (state.frontCamera) "Back cam" else "Selfie", accent = callPalette.accent, size = 52) {
                        CallEngine.switchCamera(); view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    if (!state.frontCamera) GlassCallButton(if (state.torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, "Light", active = state.torchOn, accent = callPalette.accent, size = 46) { CallEngine.toggleTorch() }
                    GlassCallButton(Icons.Default.AutoFixHigh, "Filters", active = showFilters || state.filter != CallFilter.NONE, accent = callPalette.accent, size = 46) { showFilters = !showFilters }
                }
            }
        }
        if (canMinimize && (controlsVisible || !effectiveVideo)) IconButton(
            onClick = onMinimize,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).background(Color.Black.copy(.42f), CircleShape)
        ) { Icon(Icons.Default.KeyboardArrowDown, "Minimize call", tint = Color.White, modifier = Modifier.size(30.dp)) }
        if (accepted) CallReactionsLayer(active = state.status == "connected" || state.status == "reconnecting")
        if (accepted && (controlsVisible || !effectiveVideo)) CallQualityPill(
            connected = state.status == "connected",
            accent = callPalette.accent,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 18.dp)
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(if (effectiveVideo && accepted) .06f else .18f))
            AnimatedVisibility(visible = !effectiveVideo || !accepted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!accepted && incoming) IncomingCallerAvatar(remoteImage, remoteName, callPalette.accent)
                    else CallAvatarAura(remoteImage, remoteName, callPalette.accent, state.status, state.muted)
                    Spacer(Modifier.height(6.dp))
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
                IncomingAnswerControls(
                    video = effectiveVideo,
                    onDecline = { CallRingtoneController.stop(context, callId); CallEngine.decline(callId); onClose() },
                    onAccept = {
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
                )
            } else if (controlsVisible || !effectiveVideo) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnimatedVisibility(visible = showFilters && effectiveVideo && !state.screenSharing) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CallFilter.entries.forEach { f ->
                                val selected = state.filter == f
                                Column(
                                    Modifier.clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) Color.White else Color.Black.copy(alpha = .38f))
                                        .border(1.dp, if (selected) callPalette.accent else Color.White.copy(alpha = .25f), RoundedCornerShape(18.dp))
                                        .clickable { CallEngine.setFilter(f) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(f.emoji, style = MaterialTheme.typography.titleMedium)
                                    Text(f.label, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = showMoreControls,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 3 },
                        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 }
                    ) {
                        val tiles = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> {
                            add(Triple(if (state.speaker) Icons.Default.Hearing else Icons.Default.VolumeUp, if (state.speaker) "Earpiece" else "Speaker") { CallEngine.toggleSpeaker() })
                            add(Triple(Icons.Default.PersonAdd, "Add people") { onAddParticipant(); showMoreControls = false })
                            add(Triple(if (state.screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, if (state.screenSharing) "Stop share" else "Share screen") {
                                if (state.screenSharing) CallEngine.stopScreenShare()
                                else {
                                    val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    screenShareLauncher.launch(manager.createScreenCaptureIntent())
                                }
                                showMoreControls = false
                            })
                            if (effectiveVideo) {
                                add(Triple(Icons.Default.Cameraswitch, "Flip camera") { if (!state.screenSharing) CallEngine.switchCamera() })
                                add(Triple(if (state.cameraOff) Icons.Default.Videocam else Icons.Default.VideocamOff, if (state.cameraOff) "Camera on" else "Camera off") { CallEngine.toggleCamera() })
                                add(Triple(if (fillVideo) Icons.Default.FitScreen else Icons.Default.Fullscreen, if (fillVideo) "Fit video" else "Fill screen") { fillVideo = !fillVideo })
                                add(Triple(if (showSelfView) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showSelfView) "Hide me" else "Show me") { showSelfView = !showSelfView })
                                add(Triple(Icons.Default.AutoFixHigh, "Filters") { showFilters = !showFilters; showMoreControls = false })
                                if (state.status == "connected") add(Triple(Icons.Default.PictureInPictureAlt, "Mini view") { showMoreControls = false; onMinimize() })
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(30.dp))
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(.52f), Color.Black.copy(.38f))))
                                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.32f), callPalette.accent.copy(.22f))), RoundedCornerShape(30.dp))
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            tiles.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    row.forEach { (icon, label, action) ->
                                        Column(
                                            Modifier.width(76.dp).clip(RoundedCornerShape(18.dp)).clickable { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); action() }.padding(vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color.White.copy(.20f), callPalette.accent.copy(.20f)))).border(1.dp, Color.White.copy(.25f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
                                            Spacer(Modifier.height(5.dp))
                                            Text(label, color = Color.White.copy(.9f), fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
                                }
                            }
                        }
                    }
                    AnimatedVisibility(visible = showReactions && state.status == "connected") {
                        CallReactionBar(callPalette.accent, onPick = { token ->
                            if (!CallEngine.sendReaction(token)) view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                        })
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(36.dp))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.Black.copy(.28f))))
                            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.45f), callPalette.accent.copy(.25f))), RoundedCornerShape(36.dp))
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassCallButton(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Unmute" else "Mute", active = state.muted, accent = callPalette.accent) { CallEngine.toggleMute() }
                        GlassCallButton(if (state.speaker) Icons.Default.VolumeUp else Icons.Default.Hearing, if (state.speaker) "Speaker" else "Earpiece", active = state.speaker, accent = callPalette.accent) { CallEngine.toggleSpeaker() }
                        if (effectiveVideo) GlassCallButton(if (state.cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, "Camera", active = state.cameraOff, accent = callPalette.accent) { CallEngine.toggleCamera() }
                        GlassCallButton(Icons.Default.EmojiEmotions, "React", active = showReactions, accent = callPalette.accent) { showReactions = !showReactions }
                        GlassCallButton(Icons.Default.CallEnd, "End", danger = true, size = 56) { onEndCall(); onClose() }
                        GlassCallButton(if (showMoreControls) Icons.Default.Close else Icons.Default.GridView, "More", active = showMoreControls, accent = callPalette.accent) { showMoreControls = !showMoreControls }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (controlsVisible || !effectiveVideo) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = callPalette.accent.copy(.7f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text("Encrypted media (WebRTC DTLS-SRTP) • Cloudflare TURN", color = callPalette.accent.copy(.6f), style = MaterialTheme.typography.labelSmall)
            }
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
