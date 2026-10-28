package com.example.call

import android.app.KeyguardManager
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.data.Group
import com.example.service.CallRingtoneController
import com.example.ui.theme.MyApplicationTheme

class IncomingGroupCallActivity : ComponentActivity() {
    private var accepted by mutableStateOf(false)

    private val roomId get() = intent.getStringExtra("roomId").orEmpty()
    private val groupId get() = intent.getStringExtra("groupId").orEmpty()
    private val groupName get() = intent.getStringExtra("groupName") ?: "Convo Chat group"
    private val callerName get() = intent.getStringExtra("callerName") ?: "A group member"
    private val videoCall get() = intent.getBooleanExtra("videoCall", false)
    private val memberIds get() = intent.getStringExtra("memberIds").orEmpty().split(',').filter { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.requestDismissKeyguard(this, null)
        } else @Suppress("DEPRECATION") {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        when (intent.action) {
            "com.ebchat.DECLINE_GROUP_CALL" -> {
                CallRingtoneController.stop(this, roomId)
                finish()
                return
            }
            "com.ebchat.JOIN_GROUP_CALL" -> {
                CallRingtoneController.stop(this, roomId)
                accepted = true
            }
            else -> accepted = false
        }
        render()
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
        val active = accepted && GroupCallEngine.state.value.status !in listOf("idle", "ended", "failed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && active && GroupCallEngine.state.value.video && !isInPictureInPictureMode) minimizeCallToPip()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            "com.ebchat.DECLINE_GROUP_CALL" -> {
                CallRingtoneController.stop(this, roomId)
                finish()
                return
            }
            "com.ebchat.JOIN_GROUP_CALL" -> {
                CallRingtoneController.stop(this, roomId)
                accepted = true
            }
            else -> accepted = false
        }
        render()
    }

    private fun render() {
        setContent {
            MyApplicationTheme(themeType = getSharedPreferences("firechat_prefs", MODE_PRIVATE).getString("app_theme", "default") ?: "default") {
                if (accepted) {
                    GroupCallScreen(
                        group = Group(id = groupId, name = groupName, members = memberIds),
                        video = videoCall,
                        joinRoomId = roomId,
                        // Accepted notification actions must enter the call directly;
                        // the passive Join Call card is only for group-chat banners.
                        autoStart = true,
                        onClose = { GroupCallEngine.end(); finish() },
                        onMinimize = { if (GroupCallEngine.state.value.video) minimizeCallToPip() else moveTaskToBack(true) },
                        onPipCallOver = { CallPipState.set(false); finish() }
                    )
                } else {
                    IncomingGroupCallPrompt(
                        groupName = groupName,
                        callerName = callerName,
                        video = videoCall,
                        onAccept = { CallRingtoneController.stop(this, roomId); accepted = true; render() },
                        onDecline = { CallRingtoneController.stop(this, roomId); finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingGroupCallPrompt(
    groupName: String,
    callerName: String,
    video: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val accent = Color(0xFF7C9CFF)
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E1330), Color(0xFF1C1F4A), Color(0xFF071A2B)))),
        contentAlignment = Alignment.Center
    ) {
        IncomingCallBackdrop(accent)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(.16f))
            Box(Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, Color(0xFF00E5FF)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(78.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(groupName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("$callerName invited you to a ${if (video) "video" else "audio"} group call", color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            IncomingAnswerControls(video = video, onAccept = onAccept, onDecline = onDecline)
            Spacer(Modifier.height(12.dp))
        }
    }
}
