package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Post
import com.example.data.Story
import com.example.data.User
import com.example.data.UserReport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ActivityBucket(
    val label: String,
    val members: Int,
    val posts: Int,
    val stories: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAnalyticsDashboard(
    users: List<User>,
    posts: List<Post>,
    stories: List<Story>,
    reports: List<UserReport>,
    onRefreshReports: () -> Unit,
    onOpenControlCenter: () -> Unit
) {
    var selectedDays by rememberSaveable { mutableIntStateOf(7) }
    val now = System.currentTimeMillis()
    val cutoff = localDayStart(now) - (selectedDays - 1L) * DAY_MS

    // Admin-imported library items are not ordinary user-created feed activity.
    val visiblePosts = posts.filterNot { it.isAdminReel }
    val periodPosts = visiblePosts.filter { it.timestamp in cutoff..now }
    val periodStories = stories.filter { it.timestamp in cutoff..now }
    val newMembers = users.filter { it.createdAt in cutoff..now }
    val onlineNow = users.count { user ->
        user.onlineUntil > now || (user.isOnline && user.lastActive >= now - 5 * 60_000L)
    }
    val periodInteractions = periodPosts.sumOf { post ->
        post.comments.size + post.reactions.size + post.mediaReactions.values.sumOf { it.size }
    } + periodStories.sumOf { story -> story.comments.size + story.reactions.size }
    val periodViews = periodPosts.sumOf { it.viewsCount.coerceAtLeast(0).toLong() } +
        periodStories.sumOf { story -> maxOf(story.viewers.size, story.viewCounts.values.sum()).toLong() }
    val pendingReports = reports.count { it.status.equals("pending", ignoreCase = true) || it.status.equals("open", ignoreCase = true) }
    val buckets = remember(selectedDays, users, visiblePosts, stories, now) {
        makeActivityBuckets(selectedDays, users, visiblePosts, stories, now)
    }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenControlCenter) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Admin Control Center")
                }
                Column(Modifier.weight(1f)) {
                    Text("Analytics", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text("Admin dashboard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefreshReports) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh safety reports")
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(1.dp))

            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            ),
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset(900f, 600f)
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .74f)) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Community pulse", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "A clear snapshot of members, content and engagement from the records currently loaded in the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .74f)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF20A66A)))
                                Spacer(Modifier.width(6.dp))
                                Text("${formatCount(onlineNow.toLong())} online now", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .74f)) {
                            Text("${formatCount(users.size.toLong())} profiles", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Activity window", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Compare recent community activity", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "7D", 30 to "30D", 90 to "90D").forEach { (days, label) ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { selectedDays = days },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalyticsMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "New members",
                    value = formatCount(newMembers.size.toLong()),
                    detail = "in the last ${selectedDays} days",
                    icon = { color -> Icon(Icons.Default.People, null, tint = color) },
                    accent = MaterialTheme.colorScheme.primary
                )
                AnalyticsMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Posts & reels",
                    value = formatCount(periodPosts.size.toLong()),
                    detail = "${periodPosts.count { it.isReel }} reels",
                    icon = { color -> Icon(Icons.Default.Article, null, tint = color) },
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalyticsMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Stories",
                    value = formatCount(periodStories.size.toLong()),
                    detail = "in selected window",
                    icon = { color -> Icon(Icons.Default.AutoAwesome, null, tint = color) },
                    accent = MaterialTheme.colorScheme.secondary
                )
                AnalyticsMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Interactions",
                    value = formatCount(periodInteractions.toLong()),
                    detail = "reactions + comments",
                    icon = { color -> Icon(Icons.Default.TrendingUp, null, tint = color) },
                    accent = Color(0xFF0D9C83)
                )
            }

            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Activity trend", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Member sign-ups and published content", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    ActivityBarChart(buckets)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ChartLegend("Members", MaterialTheme.colorScheme.primary)
                        ChartLegend("Posts", MaterialTheme.colorScheme.tertiary)
                        ChartLegend("Stories", MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Engagement snapshot", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Content created in the selected window", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat("Views", formatCount(periodViews), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        MiniStat("Reels", formatCount(periodPosts.count { it.isReel }.toLong()), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                        MiniStat("Feed", formatCount(periodPosts.count { !it.isReel }.toLong()), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                    val creatorRows = buildTopCreators(periodPosts, periodStories, users)
                    Text("Most active creators", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (creatorRows.isEmpty()) {
                        Text("No content in this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        creatorRows.take(4).forEachIndexed { index, creator ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(creator.first, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text("${creator.second} published item${if (creator.second == 1) "" else "s"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingReports > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .75f)) {
                        Icon(Icons.Default.Security, null, Modifier.padding(10.dp), tint = if (pendingReports > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Safety queue", fontWeight = FontWeight.Bold)
                        Text(
                            if (pendingReports > 0) "$pendingReports report${if (pendingReports == 1) "" else "s"} need review"
                            else "No pending safety reports",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = onOpenControlCenter) {
                        Icon(Icons.Default.Flag, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Review")
                    }
                }
            }

            Text(
                "Metrics are calculated from the member, post, story and report data currently available to this device. They are not billing, reach or impression counts.",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    detail: String,
    accent: Color,
    icon: @Composable (Color) -> Unit
) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .13f)) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        icon(accent)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.TrendingUp, null, tint = accent.copy(alpha = .65f), modifier = Modifier.size(16.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .09f)).padding(10.dp)) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = color, style = MaterialTheme.typography.titleMedium)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityBarChart(buckets: List<ActivityBucket>) {
    val membersColor = MaterialTheme.colorScheme.primary
    val postsColor = MaterialTheme.colorScheme.tertiary
    val storiesColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    val maxValue = (buckets.maxOfOrNull { maxOf(it.members, it.posts, it.stories) } ?: 0).coerceAtLeast(1)

    Canvas(Modifier.fillMaxWidth().height(132.dp)) {
        val bottom = size.height - 4.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartHeight = bottom - chartTop
        repeat(4) { index ->
            val y = chartTop + chartHeight * index / 3f
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val groupWidth = size.width / buckets.size.coerceAtLeast(1)
        val barWidth = (groupWidth * .17f).coerceAtMost(9.dp.toPx()).coerceAtLeast(2.dp.toPx())
        val gap = barWidth * .32f
        buckets.forEachIndexed { index, bucket ->
            val centerX = groupWidth * (index + .5f)
            val series = listOf(bucket.members to membersColor, bucket.posts to postsColor, bucket.stories to storiesColor)
            val totalWidth = barWidth * 3f + gap * 2f
            series.forEachIndexed { seriesIndex, pair ->
                val value = pair.first
                if (value > 0) {
                    val barHeight = (value.toFloat() / maxValue * chartHeight).coerceAtLeast(3.dp.toPx())
                    val x = centerX - totalWidth / 2f + seriesIndex * (barWidth + gap)
                    drawRoundRect(
                        color = pair.second,
                        topLeft = androidx.compose.ui.geometry.Offset(x, bottom - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        buckets.forEach { bucket ->
            Text(bucket.label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun localDayStart(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun makeActivityBuckets(days: Int, users: List<User>, posts: List<Post>, stories: List<Story>, now: Long): List<ActivityBucket> {
    val (count, spanDays) = when (days) {
        7 -> 7 to 1
        30 -> 10 to 3
        else -> 13 to 7
    }
    val finalBucketStart = localDayStart(now)
    val firstBucketStart = finalBucketStart - (days - 1L) * DAY_MS
    val labelFormat = SimpleDateFormat(if (spanDays == 1) "EEE" else "d MMM", Locale.getDefault())

    return (0 until count).map { index ->
        val from = firstBucketStart + index * spanDays * DAY_MS
        val until = from + spanDays * DAY_MS
        ActivityBucket(
            label = labelFormat.format(Date(from)),
            members = users.count { it.createdAt >= from && it.createdAt < until && it.createdAt > 0L },
            posts = posts.count { it.timestamp >= from && it.timestamp < until && it.timestamp > 0L },
            stories = stories.count { it.timestamp >= from && it.timestamp < until && it.timestamp > 0L }
        )
    }
}

private fun buildTopCreators(posts: List<Post>, stories: List<Story>, users: List<User>): List<Pair<String, Int>> {
    val names = users.associate { it.uid to it.name.ifBlank { it.username.ifBlank { "Member" } } }
    val content = posts.map { it.senderId to it.senderName } + stories.map { it.senderId to it.senderName }
    return content.groupBy { it.first }
        .map { (uid, entries) ->
            val fallback = entries.firstOrNull { it.second.isNotBlank() }?.second ?: "Member"
            (names[uid]?.takeIf { it.isNotBlank() } ?: fallback) to entries.size
        }
        .sortedByDescending { it.second }
}

private fun formatCount(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

private const val DAY_MS = 24L * 60L * 60L * 1000L
