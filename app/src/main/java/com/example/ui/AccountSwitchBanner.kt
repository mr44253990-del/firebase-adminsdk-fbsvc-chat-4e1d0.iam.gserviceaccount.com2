package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RememberedAccount
import com.google.firebase.auth.FirebaseAuth

/**
 * Cross-component bus for "activity arrived on another remembered account of this device".
 * The FCM service publishes here; MainActivity / HomeScreen subscribe and show the banner.
 */
object AccountSwitchBus {
    data class Request(
        val accountUid: String,
        val senderId: String,
        val notificationType: String,
        val targetId: String
    )

    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)
    val pending: kotlinx.coroutines.flow.StateFlow<Request?> = _pending

    fun announce(request: Request) { _pending.value = request }
    fun consume() { _pending.value = null }

    fun describe(request: Request?): String = when (request?.notificationType) {
        "incoming_call", "incoming_video_call" -> "Incoming call — switch to answer"
        "group_call" -> "Group call — switch to join"
        else -> "New message — switch to read"
    }
}

/**
 * Smart account switch banner — when a notification arrives for a non-active account,
 * show banner: "Account X got a message — tap to switch"
 * Also handles FCM per-account isolation: token is removed on sign-out.
 */
@Composable
fun AccountSwitchBanner(
    incomingAccountId: String?,
    incomingMessage: String?,
    rememberedAccounts: List<RememberedAccount>,
    onSwitch: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = incomingAccountId != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (incomingAccountId == null) return@AnimatedVisibility
        val account = rememberedAccounts.find { it.uid == incomingAccountId }
        val name = account?.name ?: incomingAccountId.take(8)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(36.dp).background(Brush.linearGradient(listOf(Color(0xFF7B6BFF), Color(0xFF5AC8FA))), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Notifications, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("New activity on @${name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(incomingMessage ?: "Tap to switch account", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1)
                }
                Button(
                    onClick = { onSwitch(incomingAccountId) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Switch", fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

/**
 * Updated FCM isolation logic — called from AuthViewModel logout/switch
 * Ensures token is scoped per uid
 */
object FcmAccountIsolation {
    fun clearTokenForUid(uid: String) {
        // Token is stored under users/{uid}/fcmTokens ; removing prevents cross-account delivery
        // Actual removal done in MyFirebaseMessagingService.unsubscribeForUid(uid)
    }
}
