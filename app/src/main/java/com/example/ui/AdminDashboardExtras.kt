package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FlagshipConfig
import com.example.data.Post
import com.example.data.User
import java.util.Calendar

/** Start of the local day [daysAgo] days back. */
private fun dayStart(daysAgo: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
}.timeInMillis

/**
 * Live dashboard header: animated KPIs, a 7-day activity chart, and one-tap quick actions
 * (maintenance, reels, polls, AI post). Everything updates in real time from the app state.
 */
@Composable
fun AdminLivePulse(
    users: List<User>,
    posts: List<Post>,
    config: FlagshipConfig,
    onToggleMaintenance: (Boolean) -> Unit,
    onToggleReels: (Boolean) -> Unit,
    onOpenSection: (String) -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); now = System.currentTimeMillis() } }
    val today = remember(now) { dayStart(0) }
    val online = users.count { it.isOnline && (it.onlineUntil <= 0L || it.onlineUntil > now) }
    val activeToday = users.count { maxOf(it.lastActive, it.onlineUntil.coerceAtMost(now)) >= today }
    val newToday = users.count { it.createdAt >= today }
    val postsToday = posts.count { it.timestamp >= today }
    var livePolls by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val reg = AppPolls.listen(activeOnly = true) { list -> livePolls = list.count { it.isLive() } }
        onDispose { reg.remove() }
    }
    // Posts per day for the last 7 days (oldest first).
    val week = remember(posts, today) {
        (6 downTo 0).map { daysAgo ->
            val start = dayStart(daysAgo); val end = start + 86_400_000L
            posts.count { it.timestamp in start until end }
        }
    }
    val weekSignups = remember(users, today) {
        (6 downTo 0).map { daysAgo ->
            val start = dayStart(daysAgo); val end = start + 86_400_000L
            users.count { it.createdAt in start until end }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulseKpi("Online now", online, Icons.Default.Circle, Color(0xFF22C55E), live = true)
            PulseKpi("Active today", activeToday, Icons.Default.Bolt, Color(0xFF6366F1))
            PulseKpi("New today", newToday, Icons.Default.PersonAdd, Color(0xFFEC4899))
            PulseKpi("Posts today", postsToday, Icons.Default.DynamicFeed, Color(0xFF0EA5E9))
            PulseKpi("Live polls", livePolls, Icons.Default.HowToVote, Color(0xFF8B5CF6))
        }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last 7 days", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    LegendDot(Color(0xFF0EA5E9), "Posts"); Spacer(Modifier.width(10.dp)); LegendDot(Color(0xFFEC4899), "Sign-ups")
                }
                WeekBars(week, weekSignups)
            }
        }
        Text("Quick actions", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickToggle("Maintenance", if (config.maintenanceMode) "ON" else "Off", Icons.Default.Build, Color(0xFFE28A22), config.maintenanceMode) { onToggleMaintenance(!config.maintenanceMode) }
            QuickToggle("Reels", if (config.reelsEnabled) "Visible" else "Hidden", Icons.Default.SmartDisplay, Color(0xFF1B9A8B), config.reelsEnabled) { onToggleReels(!config.reelsEnabled) }
            QuickToggle("New poll", "Create", Icons.Default.HowToVote, Color(0xFF7C4DFF), false) { onOpenSection("polls") }
            QuickToggle("AI posts", "Schedule", Icons.Default.AutoAwesome, Color(0xFFE0457B), false) { onOpenSection("auto_post") }
            QuickToggle("Users", "${users.size}", Icons.Default.Groups, Color(0xFF4B83BE), false) { onOpenSection("users") }
        }
    }
}

@Composable
private fun PulseKpi(title: String, value: Int, icon: ImageVector, accent: Color, live: Boolean = false) {
    val animated by animateIntAsState(value, tween(900), label = "kpi")
    Column(
        Modifier.width(128.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .20f), accent.copy(alpha = .06f))))
            .border(1.dp, accent.copy(alpha = .25f), RoundedCornerShape(22.dp))
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(if (live) 11.dp else 17.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(animated.toString(), fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 10.sp)
    }
}

@Composable
private fun WeekBars(posts: List<Int>, signups: List<Int>) {
    val max = (posts + signups).maxOrNull()?.coerceAtLeast(1) ?: 1
    val labels = remember {
        (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(cal.time).take(2)
        }
    }
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        posts.indices.forEach { i ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(Modifier.height(88.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val p by animateFloatAsState(posts[i].toFloat() / max, tween(800), label = "bar_p")
                    val s by animateFloatAsState(signups[i].toFloat() / max, tween(800), label = "bar_s")
                    Box(Modifier.width(9.dp).fillMaxHeight(p.coerceAtLeast(.03f)).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0EA5E9)))
                    Box(Modifier.width(9.dp).fillMaxHeight(s.coerceAtLeast(.03f)).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEC4899)))
                }
                Text(labels[i], fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickToggle(title: String, state: String, icon: ImageVector, accent: Color, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(20.dp))
            .background(if (active) accent.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, if (active) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(state, fontSize = 11.sp, color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}
