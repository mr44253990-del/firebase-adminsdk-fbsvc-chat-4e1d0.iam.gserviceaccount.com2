package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RememberedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A pending "switch to another remembered account" request, raised by the FCM service when a
 * message/call arrives for an account that is not currently signed in. Collected by HomeScreen
 * (which owns the foreground banner) and consumed by the notification action receiver for the
 * background path.
 */
data class AccountSwitchRequest(
    val targetUid: String,
    val message: String,
    val senderId: String = "",
    val callId: String = "",
    val videoCall: Boolean = false
)

/** Global bus so MyFirebaseMessagingService (no ViewModel access) can request a switch in the UI. */
object AccountSwitchRequestBus {
    private val _requests = MutableStateFlow<AccountSwitchRequest?>(null)
    val requests: StateFlow<AccountSwitchRequest?> = _requests.asStateFlow()
    fun post(request: AccountSwitchRequest) { _requests.value = request }
    fun consume() { _requests.value = null }
}

/**
 * Smart account switch banner — when a notification arrives for a non-active account,
 * show banner: "Account X got a message — tap to switch & receive".
 * Restyled v4: GlassCard + GradientButton + springPress + GlowDot.
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
        val name = account?.name?.ifBlank { null } ?: account?.email?.substringBefore("@") ?: incomingAccountId.take(8)
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            cornerRadius = 18.dp
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GlowDot(color = com.example.ui.theme.AuroraPink, size = 10.dp)
                    Icon(Icons.Default.Notifications, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Switch to @$name",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Text(
                        incomingMessage ?: "New activity — tap to receive",
                        fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
                GradientButton(
                    onClick = { onSwitch(incomingAccountId) },
                    cornerRadius = 12.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.SwapHoriz, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                        Text("Receive", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("✕", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .7f))
                }
            }
        }
    }
}

/**
 * FCM per-account isolation. Called on logout / account switch / forget.
 * Removes this device's routing token from the given uid's Firestore (and RTDB) profile so the
 * Worker stops delivering that account's pushes to this device. The device token itself is shared
 * and stays registered for the still-active account.
 */
object FcmAccountIsolation {
    fun clearTokenForUid(context: Context, uid: String) {
        unsubscribeForUid(context, uid)
    }
}

/**
 * Token unsubscribing per uid: removes the fcmToken routing fields for [uid] so pushes for that
 * account stop arriving on this device. Top-level (in the service package's sibling ui module) so
 * the stub comment in earlier revisions resolves to a real implementation.
 */
fun unsubscribeForUid(context: Context, uid: String) {
    if (uid.isBlank()) return
    runCatching {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
            .document(uid)
            .update(mapOf("fcmToken" to com.google.firebase.firestore.FieldValue.delete(), "fcmTokenUpdatedAt" to System.currentTimeMillis()))
    }
    runCatching {
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid).child("fcmToken").removeValue()
    }
    runCatching {
        // Best-effort topic unsubscribes are handled by FirebaseMessaging; nothing else to clear here.
        com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled
    }
}
