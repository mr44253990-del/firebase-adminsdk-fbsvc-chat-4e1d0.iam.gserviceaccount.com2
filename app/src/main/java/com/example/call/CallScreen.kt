package com.example.call

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import org.webrtc.SurfaceViewRenderer

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else @Suppress("DEPRECATION") {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val callId = intent.getStringExtra("callId").orEmpty()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(callId.hashCode())
        val callerId = intent.getStringExtra("callerId").orEmpty()
        val callerName = intent.getStringExtra("callerName") ?: "FireChat user"
        val callerImage = intent.getStringExtra("callerImage").orEmpty()
        val videoCall = intent.getBooleanExtra("videoCall", false)
        val canRecord = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val canUseMedia = canRecord && (!videoCall || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        when (intent.action) {
            "com.ebchat.DECLINE_CALL" -> { CallEngine.decline(callId); finish(); return }
            "com.ebchat.ACCEPT_CALL" -> if (canUseMedia) CallEngine.acceptIncoming(this, callId, callerId, callerName, callerImage, videoCall)
        }
        setContent {
            MyApplicationTheme {
                CallScreen(
                    callId = callId, remoteUid = callerId, remoteName = callerName,
                    remoteImage = callerImage, incoming = true, video = videoCall,
                    initiallyAccepted = intent.action == "com.ebchat.ACCEPT_CALL" && canUseMedia,
                    onClose = { finish() }
                )
            }
        }
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by CallEngine.state.collectAsState()
    val effectiveVideo = video || state.video
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    DisposableEffect(effectiveVideo) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (effectiveVideo) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    var pendingAccept by remember { mutableStateOf(false) }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results[Manifest.permission.RECORD_AUDIO] == true && (!effectiveVideo || results[Manifest.permission.CAMERA] == true)
        if (granted && pendingAccept) {
            pendingAccept = false; accepted = true
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
            AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).also(CallEngine::attachRemoteRenderer) },
                modifier = Modifier.fillMaxSize(),
                onRelease = CallEngine::detachRenderer
            )
            AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).also(CallEngine::attachLocalRenderer) },
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(18.dp).size(width = 116.dp, height = 170.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(.6f), RoundedCornerShape(22.dp)),
                onRelease = CallEngine::detachRenderer
            )
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(if (effectiveVideo && accepted) .06f else .18f))
            AnimatedVisibility(visible = !effectiveVideo || !accepted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = remoteImage.ifBlank { null }, contentDescription = remoteName,
                        error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                        modifier = Modifier.size(138.dp).clip(CircleShape).border(4.dp, Color.White.copy(.75f), CircleShape)
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(remoteName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            }
            Surface(color = Color.Black.copy(alpha = if (effectiveVideo && accepted) .45f else 0f), shape = CircleShape) {
            Text(
                when {
                    state.error != null -> state.error!!
                    !accepted && incoming -> if (effectiveVideo) "Incoming FireChat video call" else "Incoming FireChat audio call"
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
                    CallCircleButton(Color(0xFFE53935), Icons.Default.CallEnd, "Decline") { CallEngine.decline(callId); onClose() }
                    CallCircleButton(Color(0xFF36C76C), Icons.Default.Call, "Accept") {
                        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        val cameraGranted = !effectiveVideo || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (micGranted && cameraGranted) {
                            accepted = true; CallEngine.acceptIncoming(context, callId, remoteUid, remoteName, remoteImage, effectiveVideo)
                        } else {
                            pendingAccept = true
                            callPermissionLauncher.launch(if (effectiveVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallCircleButton(Color.White.copy(.15f), if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute") { CallEngine.toggleMute() }
                    CallCircleButton(Color(0xFFE53935), Icons.Default.CallEnd, "End") { onEndCall(); onClose() }
                    CallCircleButton(Color.White.copy(.15f), if (state.speaker) Icons.Default.VolumeUp else Icons.Default.Hearing, "Speaker") { CallEngine.toggleSpeaker() }
                    if (effectiveVideo) CallCircleButton(Color.White.copy(.15f), Icons.Default.Cameraswitch, "Flip") { CallEngine.switchCamera() }
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
