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
                        autoStart = false,
                        onClose = { finish() }
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
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp).clip(CircleShape))
            Spacer(Modifier.height(22.dp))
            Text("Incoming group call", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(groupName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("$callerName invited you to a ${if (video) "video" else "audio"} call", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(38.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecline, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Decline group call", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
                }
                Button(onClick = onAccept, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Join call")
                }
            }
        }
    }
}
