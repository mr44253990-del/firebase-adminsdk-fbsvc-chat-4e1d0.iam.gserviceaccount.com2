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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.security.TextBackupManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Permission Center (v5.2). Shown once right after sign-in — before Home — so the app no longer
 * interrupts the first minutes with one system pop-up after another. Every permission has its own
 * card that explains in one line what it unlocks; all of them can be skipped. Anything skipped is
 * simply asked again at the moment a feature really needs it (first call, first photo, …).
 * It can be reopened any time from Settings → Permission manager.
 */
object PermissionCenter {
    private const val PREFS = "convo_permission_center"

    /** True when the page should appear after sign-in (never completed/skipped on this install). */
    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("done", false)) return false
        return permissionEntries(context).any { !it.granted && it.essential }
    }

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()
    }
}

internal data class PermissionEntry(
    val id: String,
    val title: String,
    val why: String,
    val icon: ImageVector,
    val tint: Color,
    val granted: Boolean,
    /** Runtime permissions to request; empty means it is a system settings page. */
    val runtime: List<String> = emptyList(),
    val settings: ((Context) -> Unit)? = null,
    val essential: Boolean = false,
    val group: String = "Recommended"
)

private fun has(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@SuppressLint("BatteryLife")
internal fun permissionEntries(context: Context): List<PermissionEntry> {
    val pkg = context.packageName
    val nm = context.getSystemService(NotificationManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    fun launch(intent: Intent, fallback: Intent? = null) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { fallback?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    val notificationsOk = (Build.VERSION.SDK_INT < 33 || has(context, Manifest.permission.POST_NOTIFICATIONS)) && nm?.areNotificationsEnabled() != false
    return buildList {
        add(PermissionEntry("notifications", "Notifications", "Know the moment a message or call arrives.", Icons.Default.NotificationsActive, Color(0xFFFFB03A), notificationsOk,
            runtime = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
            settings = { launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), details) }, essential = true, group = "Essential"))
        add(PermissionEntry("microphone", "Microphone", "Voice messages and audio / video calls.", Icons.Default.Mic, Color(0xFF19C37D), has(context, Manifest.permission.RECORD_AUDIO),
            runtime = listOf(Manifest.permission.RECORD_AUDIO), settings = { launch(details) }, essential = true, group = "Essential"))
        add(PermissionEntry("camera", "Camera", "Video calls, photos, stories and QR scanning.", Icons.Default.PhotoCamera, Color(0xFF3AA0FF), has(context, Manifest.permission.CAMERA),
            runtime = listOf(Manifest.permission.CAMERA), settings = { launch(details) }, essential = true, group = "Essential"))
        if (Build.VERSION.SDK_INT >= 34) add(PermissionEntry("fullscreen", "Full-screen calls", "Shows an incoming call over the lock screen.", Icons.Default.Fullscreen, Color(0xFFB07CFF), nm?.canUseFullScreenIntent() == true,
            settings = { launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")), details) }, group = "Calls that always ring"))
        if (Build.VERSION.SDK_INT >= 23) {
            add(PermissionEntry("overlay", "Display over other apps", "Floating call bubble that follows you into any app.", Icons.Default.PictureInPicture, Color(0xFF36D8E6), Settings.canDrawOverlays(context),
                settings = { launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")), details) }, group = "Calls that always ring"))
            add(PermissionEntry("battery", "Unrestricted battery", "Stops Android delaying calls and messages while the phone sleeps.", Icons.Default.BatteryChargingFull, Color(0xFF7EE8A6), pm?.isIgnoringBatteryOptimizations(pkg) == true,
                settings = { launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, group = "Calls that always ring"))
        }
        add(PermissionEntry("contacts", "Contacts", "Find friends who already use Convo Chat.", Icons.Default.Contacts, Color(0xFFFF6FB5), has(context, Manifest.permission.READ_CONTACTS),
            runtime = listOf(Manifest.permission.READ_CONTACTS), settings = { launch(details) }, group = "Optional"))
        add(PermissionEntry("location", "Location", "Share where you are in a chat — only when you choose to.", Icons.Default.LocationOn, Color(0xFFFF8A3D),
            has(context, Manifest.permission.ACCESS_FINE_LOCATION) || has(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            runtime = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), settings = { launch(details) }, group = "Optional"))
        add(PermissionEntry("backup", "Chat backup folder", "One backup file that stays on the phone even if the app is removed.", Icons.Default.Backup, Color(0xFF8B7BFF),
            TextBackupManager.folderUri(context) != null, group = "Optional"))
    }
}

@Composable
fun PermissionCenterScreen(firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    // Re-check whenever the user returns from a system dialog or the Settings app.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val entries = remember(tick) { permissionEntries(context) }
    val granted = entries.count { it.granted }
    val progress by animateFloatAsState(if (entries.isEmpty()) 1f else granted / entries.size.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "perm_progress")
    // A runtime permission that was denied twice can only be changed in Settings.
    var deniedOnce by remember { mutableStateOf(setOf<String>()) }
    var pendingId by remember { mutableStateOf("") }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.none { it } && pendingId.isNotBlank()) deniedOnce = deniedOnce + pendingId
        if (pendingId == "all") deniedOnce = deniedOnce + entries.filter { e -> e.runtime.isNotEmpty() && e.runtime.none { result[it] == true } && !e.granted }.map { it.id }
        pendingId = ""; tick++
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { TextBackupManager.setFolder(context, uri); android.widget.Toast.makeText(context, "Backup folder connected", android.widget.Toast.LENGTH_SHORT).show() }
        tick++
    }
    fun request(entry: PermissionEntry) {
        when {
            entry.id == "backup" -> runCatching { folderLauncher.launch(null) }
            entry.runtime.isNotEmpty() && entry.id !in deniedOnce -> { pendingId = entry.id; runtimeLauncher.launch(entry.runtime.toTypedArray()) }
            else -> entry.settings?.invoke(context)
        }
    }
    val finish = { PermissionCenter.markDone(context); onDone() }
    androidx.activity.compose.BackHandler(enabled = true) { finish() }

    val motion = rememberInfiniteTransition(label = "perm_bg")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val pulse by motion.animateFloat(.94f, 1.06f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    Box(Modifier.fillMaxSize().background(Color(0xFF070712))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (w > 1f && h > 1f) {
                val r = w * .85f
                val a = Offset(w * (.05f + .5f * drift), h * .08f); val b = Offset(w * (.95f - .5f * drift), h * .55f); val c = Offset(w * .4f, h * (1f - .1f * drift))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8B7BFF).copy(alpha = .36f), Color.Transparent), a, r), r, a)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF36D8E6).copy(alpha = .22f), Color.Transparent), b, r * .8f), r * .8f, b)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF6FB5).copy(alpha = .16f), Color.Transparent), c, r * .7f), r * .7f, c)
                repeat(22) { i ->
                    val seed = (i * 61 % 100) / 100f
                    val p = (drift * 1.5f + seed) % 1f
                    drawCircle(Color.White.copy(alpha = (sin(p * PI.toFloat()) * .4f).coerceAtLeast(0f)), 1.3f + i % 3, Offset(w * ((seed * 2.1f + i * .04f) % 1f), h * (1.02f - p)))
                }
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 620.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (firstRun) "Step 2 of 2" else "Permission manager", color = Color.White.copy(alpha = .6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = finish) { Text(if (firstRun) "Skip for now" else "Close", color = Color(0xFF9FE8FF), fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // ---- Hero: shield inside a progress ring with orbiting permission dots --------
                Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f - 10f
                        drawCircle(Color.White.copy(alpha = .08f), radius, style = Stroke(6.dp.toPx()))
                        drawArc(
                            Brush.sweepGradient(listOf(Color(0xFF36D8E6), Color(0xFF8B7BFF), Color(0xFFFF6FB5), Color(0xFF36D8E6))),
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                        )
                        entries.forEachIndexed { i, e ->
                            val angle = (spin + i * (360f / entries.size)) * (PI.toFloat() / 180f)
                            val p = Offset(center.x + cos(angle) * (radius - 22f), center.y + sin(angle) * (radius - 22f))
                            drawCircle(if (e.granted) e.tint else Color.White.copy(alpha = .22f), if (e.granted) 5.5f else 3.5f, p)
                        }
                    }
                    Box(
                        Modifier.size(96.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF14A3B8))))
                            .border(1.5.dp, Color.White.copy(alpha = .4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(if (granted == entries.size) Icons.Default.VerifiedUser else Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(46.dp)) }
                }
                Text("$granted of ${entries.size} ready", color = Color(0xFF9FE8FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (firstRun) "Set up Convo Chat" else "Your permissions", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center)
                Text(
                    "Allow what you want now. Everything is optional — anything you skip is asked again only when a feature needs it.",
                    color = Color.White.copy(alpha = .7f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(8.dp))
                val essentialsMissing = entries.filter { it.essential && !it.granted && it.runtime.isNotEmpty() && it.id !in deniedOnce }
                if (essentialsMissing.isNotEmpty()) Button(
                    onClick = { pendingId = "all"; runtimeLauncher.launch(essentialsMissing.flatMap { it.runtime }.distinct().toTypedArray()) },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7), contentColor = Color.White)
                ) {
                    Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(8.dp))
                    Text("Allow the ${essentialsMissing.size} essentials in one go", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                var index = 0
                entries.groupBy { it.group }.forEach { (group, list) ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.uppercase(), color = Color.White.copy(alpha = .55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent))))
                    }
                    list.forEach { entry -> PermissionCard(entry, index++, needsSettings = entry.id in deniedOnce || entry.runtime.isEmpty()) { request(entry) } }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Convo Chat never uses the camera, microphone or location in the background without showing it.", color = Color.White.copy(alpha = .5f), fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = finish,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (granted == entries.size) Color(0xFF19C37D) else Color.White.copy(alpha = .14f), contentColor = Color.White)
            ) {
                Text(if (granted == entries.size) "All set — continue" else if (firstRun) "Continue to Convo Chat" else "Done", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun PermissionCard(entry: PermissionEntry, index: Int, needsSettings: Boolean, onClick: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60L * index); enter.animateTo(1f, spring(dampingRatio = .72f, stiffness = 260f)) }
    val border by animateColorAsState(if (entry.granted) Color(0xFF19C37D).copy(alpha = .7f) else entry.tint.copy(alpha = .45f), tween(400), label = "perm_border")
    val check by animateFloatAsState(if (entry.granted) 1f else 0f, spring(dampingRatio = .5f, stiffness = 300f), label = "perm_check")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * 40f; scaleX = .95f + .05f * enter.value; scaleY = .95f + .05f * enter.value }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf((if (entry.granted) Color(0xFF19C37D) else entry.tint).copy(alpha = .16f), Color.White.copy(alpha = .04f))))
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .clickable(enabled = !entry.granted, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(entry.tint.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
            Icon(entry.icon, null, tint = entry.tint, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(entry.why, color = Color.White.copy(alpha = .68f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (entry.granted) Box(
            Modifier.size(34.dp).graphicsLayer { scaleX = check; scaleY = check }.clip(CircleShape).background(Color(0xFF19C37D)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Check, "Allowed", tint = Color.White, modifier = Modifier.size(20.dp)) }
        else Text(
            when { entry.id == "backup" -> "Choose"; needsSettings -> "Open"; else -> "Allow" },
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(CircleShape).background(entry.tint.copy(alpha = .55f)).padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
