package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.location.LiveLocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** Device-local "smart" settings: quiet hours and reduced motion. */
object SmartPrefs {
    private const val FILE = "convo_smart_prefs"
    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()
    private var loaded = false

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        _reduceMotion.value = p(ctx).getBoolean("reduce_motion", false)
    }

    fun setReduceMotion(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("reduce_motion", on).apply(); _reduceMotion.value = on }

    fun quietEnabled(ctx: Context) = p(ctx).getBoolean("quiet_on", false)
    fun quietStart(ctx: Context) = p(ctx).getInt("quiet_start", 23)
    fun quietEnd(ctx: Context) = p(ctx).getInt("quiet_end", 7)
    fun setQuiet(ctx: Context, on: Boolean, start: Int, end: Int) {
        p(ctx).edit().putBoolean("quiet_on", on).putInt("quiet_start", start.coerceIn(0, 23)).putInt("quiet_end", end.coerceIn(0, 23)).apply()
    }

    /** Message notifications are silenced inside the window. Calls always ring. */
    fun inQuietHours(ctx: Context, now: Calendar = Calendar.getInstance()): Boolean {
        if (!quietEnabled(ctx)) return false
        val h = now.get(Calendar.HOUR_OF_DAY)
        val s = quietStart(ctx)
        val e = quietEnd(ctx)
        return if (s == e) false else if (s < e) h in s until e else (h >= s || h < e)
    }
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"; h < 12 -> "$h AM"; h == 12 -> "12 PM"; else -> "${h - 12} PM"
}

private fun dirSize(f: File?): Long = if (f == null || !f.exists()) 0L else if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)

private fun formatBytes(b: Long): String = when {
    b >= 1_073_741_824L -> "%.1f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024L -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/** Settings → Smart tools page. */
@Composable
fun SmartSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { SmartPrefs.load(context) }
    val reduceMotion by SmartPrefs.reduceMotion.collectAsState()
    var quietOn by remember { mutableStateOf(SmartPrefs.quietEnabled(context)) }
    var quietStart by remember { mutableIntStateOf(SmartPrefs.quietStart(context)) }
    var quietEnd by remember { mutableIntStateOf(SmartPrefs.quietEnd(context)) }
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var clearing by remember { mutableStateOf(false) }
    val liveShare by LiveLocationService.active.collectAsState()
    val appearance by ChatPreferences.appearance.collectAsState()

    LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { dirSize(context.cacheDir) } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SmartCard(Icons.Default.Bedtime, Color(0xFF5C6BC0), "Quiet hours", if (quietOn) "Messages are silent ${hourLabel(quietStart)} – ${hourLabel(quietEnd)}. Calls still ring." else "Silence message notifications at night") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(quietOn, { quietOn = it; SmartPrefs.setQuiet(context, it, quietStart, quietEnd) })
            }
            if (quietOn) {
                HourStepper("Starts", quietStart) { quietStart = it; SmartPrefs.setQuiet(context, true, it, quietEnd) }
                HourStepper("Ends", quietEnd) { quietEnd = it; SmartPrefs.setQuiet(context, true, quietStart, it) }
            }
        }
        SmartCard(Icons.Default.Animation, Color(0xFFEC407A), "Motion & effects", "Full-screen reaction effects, animated stickers and covers") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reduce motion", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(reduceMotion, { SmartPrefs.setReduceMotion(context, it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Animated reactions & stickers", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(appearance.animateReactions, { v -> ChatPreferences.setAppearance(context, appearance.copy(animateReactions = v)) })
            }
            OutlinedButton(onClick = { ReactionFx.fire("❤️") }, enabled = !reduceMotion) { Text("Preview effect") }
        }
        SmartCard(Icons.Default.LocationOn, Color(0xFF43A047), "Live location", liveShare?.let { "Sharing with ${it.recipientName.ifBlank { "a chat" }} until " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.until)) } ?: "Not sharing your location right now") {
            if (liveShare != null) Button(onClick = { LiveLocationService.stop(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) { Text("Stop sharing") }
            else Text("Share from any chat: + → Location. You can choose 10 minutes up to 8 hours.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SmartCard(Icons.Default.Storage, Color(0xFFFF8F00), "Storage", if (cacheBytes < 0) "Calculating…" else "Temporary files and image cache: ${formatBytes(cacheBytes)}") {
            Button(enabled = !clearing && cacheBytes > 0, onClick = {
                clearing = true
                scope.launch {
                    withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } } }
                    cacheBytes = withContext(Dispatchers.IO) { dirSize(context.cacheDir) }
                    clearing = false
                }
            }) { Text(if (clearing) "Clearing…" else "Clear cache") }
            Text("Your chats, photos in chats and settings are not affected.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((hour + 23) % 24) }) { Icon(Icons.Default.Remove, "Earlier") }
        Text(hourLabel(hour), fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange((hour + 1) % 24) }) { Icon(Icons.Default.Add, "Later") }
    }
}

@Composable
private fun SmartCard(icon: ImageVector, color: Color, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.background(Brush.horizontalGradient(listOf(color.copy(alpha = .14f), Color.Transparent))).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(color, color.copy(alpha = .7f)))), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}
