package com.example.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.User

/**
 * Admin "Smart insights": live segments computed from the loaded user profiles (no extra reads),
 * a 14-day sign-up chart, profile-quality checks, suspicious-account hints and CSV export.
 */
@Composable
fun AdminInsightsPanel(users: List<User>) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    var segment by remember { mutableStateOf("new") }
    fun online(u: User) = u.isOnline && (u.onlineUntil <= 0L || u.onlineUntil > now)
    fun premium(u: User) = u.isPremium && (u.premiumPlan.equals("lifetime", true) || u.premiumUntil > now)
    fun lastSeen(u: User) = maxOf(u.lastActive, u.offlineSince)
    val segments = remember(users, now / 60_000) {
        linkedMapOf(
            "new" to ("New (7 days)" to users.filter { it.createdAt > now - 7 * day }),
            "online" to ("Online now" to users.filter(::online)),
            "active7" to ("Active this week" to users.filter { online(it) || lastSeen(it) > now - 7 * day }),
            "dormant" to ("Dormant 30+ days" to users.filter { !online(it) && lastSeen(it) in 1L until now - 30 * day }),
            "premium" to ("Premium" to users.filter(::premium)),
            "expiring" to ("Premium ends in 7d" to users.filter { premium(it) && !it.premiumPlan.equals("lifetime", true) && it.premiumUntil < now + 7 * day }),
            "nophoto" to ("No profile photo" to users.filter { it.profileImageUrl.isBlank() }),
            "lonely" to ("No friends yet" to users.filter { it.friends.isEmpty() }),
            "suspicious" to ("Check: new + empty" to users.filter { it.createdAt > now - day && it.friends.isEmpty() && it.profileImageUrl.isBlank() && it.bio.isBlank() }),
            "hidden" to ("Hidden profiles" to users.filter { it.profileHidden })
        )
    }
    val dupNames = remember(users) { users.groupBy { it.name.trim().lowercase() }.filter { it.key.isNotBlank() && it.value.size > 1 } }

    // KPI tiles
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InsightTile("Users", users.size.toString(), Icons.Outlined.Groups, Color(0xFF4B83BE), Modifier.weight(1f))
        InsightTile("Online", segments["online"]!!.second.size.toString(), Icons.Outlined.Bolt, Color(0xFF23A55A), Modifier.weight(1f))
        InsightTile("Premium", segments["premium"]!!.second.size.toString(), Icons.Outlined.WorkspacePremium, Color(0xFFCF9829), Modifier.weight(1f))
    }
    val active = segments["active7"]!!.second.size
    val retention = if (users.isEmpty()) 0f else active / users.size.toFloat()
    Text("Weekly active: $active (${(retention * 100).toInt()}%)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    LinearProgressIndicator(progress = { retention }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)))

    // 14-day sign-ups chart
    val buckets = remember(users, now / 3_600_000) {
        val startToday = now - (now % day)
        (13 downTo 0).map { d -> val from = startToday - d * day; users.count { it.createdAt in from until from + day } }
    }
    Text("Sign-ups, last 14 days (${buckets.sum()})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val maxB = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        buckets.forEachIndexed { i, b ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (b > 0) Text(b.toString(), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().height((6 + 60 * b / maxB).dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(if (i == buckets.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .45f))
                )
            }
        }
    }

    // Segments
    Text("Smart segments", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    FlowRowCompat(spacing = 6.dp) {
        segments.forEach { (id, pair) ->
            FilterChip(selected = segment == id, onClick = { segment = id }, label = { Text("${pair.first} · ${pair.second.size}", fontSize = 11.sp) })
        }
    }
    val (segTitle, segUsers) = segments[segment] ?: ("" to emptyList())
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(segTitle, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val csv = buildString {
                        append("uid,name,username,created,lastSeen,friends,premium,online\n")
                        segUsers.forEach { u ->
                            fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
                            append(listOf(q(u.uid), q(u.name), q(u.username), u.createdAt.toString(), lastSeen(u).toString(), u.friends.size.toString(), premium(u).toString(), online(u).toString()).joinToString(",")).append('\n')
                        }
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"; putExtra(Intent.EXTRA_SUBJECT, "Convo users - $segTitle"); putExtra(Intent.EXTRA_TEXT, csv)
                        }, "Export CSV"))
                    }
                }, enabled = segUsers.isNotEmpty()) { Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("CSV") }
            }
            if (segUsers.isEmpty()) Text("Nobody in this segment right now.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            segUsers.sortedByDescending { maxOf(it.createdAt, lastSeen(it)) }.take(25).forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (online(u)) Color(0xFF23A55A) else MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name.ifBlank { "(no name)" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${u.username} · ${u.friends.size} friends · joined ${ago(now - u.createdAt)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            if (segUsers.size > 25) Text("+${segUsers.size - 25} more (export CSV for all)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Quality checks
    Text("Health checks", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    InsightCheck("Duplicate display names", if (dupNames.isEmpty()) "None found" else dupNames.entries.take(5).joinToString { "${it.value.first().name} ×${it.value.size}" }, dupNames.isEmpty())
    InsightCheck("Profiles without a photo", "${segments["nophoto"]!!.second.size} of ${users.size}", segments["nophoto"]!!.second.size * 2 < users.size.coerceAtLeast(1))
    InsightCheck("New empty accounts (24h)", "${segments["suspicious"]!!.second.size} to review", segments["suspicious"]!!.second.isEmpty())
    InsightCheck("Premium ending within 7 days", "${segments["expiring"]!!.second.size} users - consider a reminder", segments["expiring"]!!.second.isEmpty())
}

private fun ago(ms: Long): String {
    if (ms < 0 || ms > 20L * 365 * 86_400_000L) return "-"
    val d = ms / 86_400_000L
    return when { d >= 30 -> "${d / 30}mo ago"; d >= 1 -> "${d}d ago"; else -> "${(ms / 3_600_000L).coerceAtLeast(0)}h ago" }
}

@Composable
private fun InsightTile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = color.copy(alpha = .12f)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp, color = color)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightCheck(title: String, detail: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (ok) Color(0xFF23A55A) else Color(0xFFE28A22), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
