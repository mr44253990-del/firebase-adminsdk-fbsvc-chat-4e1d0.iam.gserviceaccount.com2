package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ActivityNotification
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/** Visual identity of one kind of activity. */
data class ActivityKind(val key: String, val label: String, val icon: ImageVector, val tint: Color)

private val KIND_REACTION = ActivityKind("reactions", "Reactions", Icons.Default.Favorite, Color(0xFFFF4D7E))
private val KIND_COMMENT = ActivityKind("comments", "Comments", Icons.Default.ChatBubble, Color(0xFF3AA0FF))
private val KIND_SOCIAL = ActivityKind("social", "Friends", Icons.Default.PersonAdd, Color(0xFF19C37D))
private val KIND_VIEW = ActivityKind("views", "Views", Icons.Default.Visibility, Color(0xFFB07CFF))
private val KIND_MENTION = ActivityKind("mentions", "Mentions", Icons.Default.AlternateEmail, Color(0xFFFFB03A))
private val KIND_GROUP = ActivityKind("groups", "Groups", Icons.Default.Groups, Color(0xFF36D8E6))
private val KIND_PREMIUM = ActivityKind("system", "Updates", Icons.Default.WorkspacePremium, Color(0xFFE2A93B))
private val KIND_SYSTEM = ActivityKind("system", "Updates", Icons.Default.Notifications, Color(0xFF8B7BFF))

fun activityKind(type: String): ActivityKind = when {
    type.contains("reaction") || type.contains("like") -> KIND_REACTION
    type.contains("comment") || type.contains("reply") -> KIND_COMMENT
    type.contains("friend") || type.contains("follow") || type.contains("message_accepted") || type.contains("message_request") -> KIND_SOCIAL
    type.contains("view") -> KIND_VIEW
    type.contains("mention") || type.contains("tag") -> KIND_MENTION
    type.contains("group") -> KIND_GROUP
    type.contains("premium") -> KIND_PREMIUM
    else -> KIND_SYSTEM
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun bucketOf(timestamp: Long, today: Long): String = when {
    timestamp >= today -> "Today"
    timestamp >= today - 86_400_000L -> "Yesterday"
    timestamp >= today - 6L * 86_400_000L -> "This week"
    else -> "Earlier"
}

/** Living header: animated aurora, counters and a one-line insight about today. */
@Composable
fun ActivityNovaHeader(notifications: List<ActivityNotification>, pendingRequests: Int, onDismiss: () -> Unit) {
    val today = remember(notifications.size) { startOfToday() }
    val todayItems = remember(notifications, today) { notifications.filter { it.timestamp >= today } }
    val unread = notifications.count { !it.isRead }
    val insight = remember(todayItems) {
        val reactions = todayItems.count { activityKind(it.type) === KIND_REACTION }
        val comments = todayItems.count { activityKind(it.type) === KIND_COMMENT }
        val people = todayItems.map { it.actorId }.filter { it.isNotBlank() }.distinct().size
        when {
            todayItems.isEmpty() -> "A quiet day so far — share something to get things moving ✨"
            reactions >= 5 -> "You are on fire: $reactions reactions from $people people today 🔥"
            comments >= 3 -> "$comments new comments today — people want to talk to you 💬"
            else -> "${todayItems.size} new ${if (todayItems.size == 1) "update" else "updates"} from $people ${if (people == 1) "person" else "people"} today"
        }
    }
    val motion = rememberInfiniteTransition(label = "activity_header")
    val sweep by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "sweep")
    val bell by motion.animateFloat(-10f, 10f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bell")
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1548), Color(0xFF4B36C9), Color(0xFF0F7F94)), start = Offset(sweep * 500f, 0f), end = Offset(900f - sweep * 300f, 500f)))
    ) {
        Canvas(Modifier.matchParentSize()) {
            repeat(14) { i ->
                val seed = (i * 61 % 100) / 100f
                val p = (sweep * 2f + seed) % 1f
                drawCircle(Color.White.copy(alpha = (sin(p * PI.toFloat()) * .35f).coerceAtLeast(0f)), 1.5f + i % 3, Offset(size.width * ((seed * 1.9f + i * .05f) % 1f), size.height * (1f - p)))
            }
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Color.White, modifier = Modifier.graphicsLayer { rotationZ = if (unread > 0) bell else 0f })
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Activity"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(insight, color = Color.White.copy(alpha = .85f), fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderCounter("New", unread, Color(0xFFFF7AA8), Modifier.weight(1f))
                HeaderCounter("Today", todayItems.size, Color(0xFF7EE8F4), Modifier.weight(1f))
                HeaderCounter("Requests", pendingRequests, Color(0xFF8CF0B8), Modifier.weight(1f))
                HeaderCounter("Total", notifications.size, Color(0xFFD3C8FF), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderCounter(label: String, value: Int, tint: Color, modifier: Modifier) {
    val shown by animateFloatAsState(value.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "counter")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .10f)).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(shown.toInt().toString(), color = tint, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
    }
}

/** Filter chips with live counts. */
@Composable
fun ActivityFilterBar(notifications: List<ActivityNotification>, selected: String, onSelect: (String) -> Unit) {
    val counts = remember(notifications) { notifications.groupingBy { activityKind(it.type).key }.eachCount() }
    val kinds = listOf(KIND_REACTION, KIND_COMMENT, KIND_SOCIAL, KIND_MENTION, KIND_GROUP, KIND_VIEW, KIND_SYSTEM).filter { (counts[it.key] ?: 0) > 0 }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FilterPill("All", notifications.size, MaterialTheme.colorScheme.primary, Icons.Default.AutoAwesome, selected == "all") { onSelect("all") }
        FilterPill("Unread", notifications.count { !it.isRead }, Color(0xFFFF4D7E), Icons.Default.MarkEmailUnread, selected == "unread") { onSelect("unread") }
        kinds.forEach { kind -> FilterPill(kind.label, counts[kind.key] ?: 0, kind.tint, kind.icon, selected == kind.key) { onSelect(kind.key) } }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, tint: Color, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(if (on) tint.copy(alpha = .20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
            .border(1.dp, if (on) tint.copy(alpha = .8f) else Color.Transparent, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
        if (count > 0) { Spacer(Modifier.width(5.dp)); Text(count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint) }
    }
}

fun filterActivity(notifications: List<ActivityNotification>, filter: String): List<ActivityNotification> = when (filter) {
    "all" -> notifications
    "unread" -> notifications.filter { !it.isRead }
    else -> notifications.filter { activityKind(it.type).key == filter }
}

/** Time-grouped, type-coloured activity rows with a staggered entrance. */
fun LazyListScope.activitySmartItems(notifications: List<ActivityNotification>) {
    val today = startOfToday()
    val sorted = notifications.sortedByDescending { it.timestamp }
    var lastBucket = ""
    val rows = mutableListOf<Any>()
    sorted.forEach { n ->
        val bucket = bucketOf(n.timestamp, today)
        if (bucket != lastBucket) { rows += bucket; lastBucket = bucket }
        rows += n
    }
    if (rows.isEmpty()) item(key = "activity_empty_filter") {
        Text("Nothing in this filter yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(24.dp), fontSize = 13.sp)
    }
    itemsIndexed(rows, key = { _, row -> if (row is ActivityNotification) "activity_${row.id}" else "bucket_$row" }) { index, row ->
        if (row is String) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(7.dp))
                Text(row, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = .6.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .35f), Color.Transparent))))
            }
        } else if (row is ActivityNotification) ActivitySmartRow(row, index)
    }
}

@Composable
private fun ActivitySmartRow(item: ActivityNotification, index: Int) {
    val kind = activityKind(item.type)
    var entered by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f, tween(380, delayMillis = (index.coerceAtMost(10)) * 35, easing = FastOutSlowInEasing), label = "row_in")
    val now = rememberTickingNow()
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = progress; translationY = (1f - progress) * 28f; scaleX = .96f + .04f * progress; scaleY = .96f + .04f * progress }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(kind.tint.copy(alpha = if (item.isRead) .06f else .16f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .40f))))
            .border(1.dp, kind.tint.copy(alpha = if (item.isRead) .10f else .45f), RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = item.actorImageUrl.ifBlank { null }, contentDescription = item.actorName,
                error = painterResource(R.drawable.img_app_logo),
                modifier = Modifier.size(48.dp).clip(CircleShape).border(2.dp, kind.tint.copy(alpha = .7f), CircleShape)
            )
            Box(Modifier.size(20.dp).clip(CircleShape).background(kind.tint).border(2.dp, MaterialTheme.colorScheme.background, CircleShape), contentAlignment = Alignment.Center) {
                Icon(kind.icon, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.actorName.ifBlank { "Convo Chat" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = kind.tint)
                Text("  ·  " + relativeTimeLabel(item.timestamp, now), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
            }
        }
        if (!item.isRead) Box(Modifier.size(9.dp).clip(CircleShape).background(kind.tint))
    }
}
