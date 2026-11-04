package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** One permission/setting that incoming calls depend on. */
private data class ReadinessItem(
    val id: String,
    val title: String,
    val why: String,
    val icon: ImageVector,
    val ok: Boolean,
    val open: (Context) -> Unit
)

private const val READINESS_PREFS = "convo_call_readiness"
private const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000

@SuppressLint("BatteryLife")
private fun readinessItems(context: Context): List<ReadinessItem> {
    val pkg = context.packageName
    val nm = context.getSystemService(NotificationManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    fun launch(intent: Intent, fallback: Intent? = null) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { fallback?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    return listOf(
        ReadinessItem(
            "notifications", "Notifications", "Ringing calls and messages need notification access.",
            Icons.Default.NotificationsActive,
            ok = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                nm?.areNotificationsEnabled() != false,
            open = { launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), appDetails) }
        ),
        ReadinessItem(
            "fullscreen", "Full-screen calls", "Shows the incoming call over the lock screen (Android 14+ asks separately).",
            Icons.Default.Fullscreen,
            ok = Build.VERSION.SDK_INT < 34 || nm?.canUseFullScreenIntent() == true,
            open = {
                if (Build.VERSION.SDK_INT >= 34) launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")), appDetails)
            }
        ),
        ReadinessItem(
            "overlay", "Display over other apps", "Lets the call screen open while you use another app, and keeps the mini call bubble.",
            Icons.Default.Layers,
            ok = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context),
            open = { launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")), appDetails) }
        ),
        ReadinessItem(
            "battery", "Unrestricted battery", "Stops Android from delaying call pushes while the phone sleeps.",
            Icons.Default.BatteryChargingFull,
            ok = Build.VERSION.SDK_INT < 23 || pm?.isIgnoringBatteryOptimizations(pkg) == true,
            open = {
                launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
    )
}

/**
 * Start-up checklist for reliable incoming calls. Appears once per app start when something is
 * missing (unless snoozed for 3 days), re-checks every time the user returns from Settings, and
 * closes by itself when everything is ready. Android still controls final delivery.
 */
@Composable
fun CallReadinessDialog(enabled: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(READINESS_PREFS, Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val items = remember(tick) { readinessItems(context) }
    val missing = items.count { !it.ok }
    var dismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val snoozed = remember { System.currentTimeMillis() < prefs.getLong("snooze_until", 0L) }
    if (!enabled || dismissedThisSession || snoozed || missing == 0) return

    AlertDialog(
        onDismissRequest = { dismissedThisSession = true },
        icon = {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF0EA5E9)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhoneInTalk, null, tint = Color.White)
            }
        },
        title = { Text("Get calls even when the app is closed", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$missing setting${if (missing == 1) "" else "s"} still block full-screen calls on the lock screen. Tap each one to fix it:", fontSize = 13.sp)
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (item.ok) Color(0xFF22C55E).copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                            .clickable(enabled = !item.ok) { item.open(context) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (item.ok) Icons.Default.CheckCircle else item.icon, null, tint = if (item.ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (item.ok) "Ready" else item.why, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!item.ok) Text("Fix", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { items.firstOrNull { !it.ok }?.open?.invoke(context) }) { Text("Fix next") }
        },
        dismissButton = {
            TextButton(onClick = {
                prefs.edit().putLong("snooze_until", System.currentTimeMillis() + SNOOZE_MS).apply()
                dismissedThisSession = true
            }) { Text("Remind me later") }
        }
    )
}
