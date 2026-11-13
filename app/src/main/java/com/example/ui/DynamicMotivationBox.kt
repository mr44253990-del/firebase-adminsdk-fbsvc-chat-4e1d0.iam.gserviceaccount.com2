package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraPink
import kotlinx.coroutines.delay
import java.util.Calendar

private val morningQuotes = listOf(
    "শুভ সকাল! আজকের দিনটা আপনার হতে পারে সেরা দিন।",
    "Every morning is a new page — write something great.",
    "নতুন দিন, নতুন সুযোগ। চালিয়ে যান!"
)
private val dayQuotes = listOf(
    "Stay consistent — small steps win the race.",
    "ধৈর্য ধরুন, আপনি ভালোই করছেন।",
    "Focus on progress, not perfection."
)
private val eveningQuotes = listOf(
    "শুভ সন্ধ্যা! আজকের সেরা মুহূর্তটা মনে রাখুন।",
    "Rest well — tomorrow needs your best self.",
    "আজকে যা শিখলেন, তাই আগামীকালের শক্তি।"
)

/** Picks a quote pool by the hour of day. */
private fun quotesForNow(): List<String> = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> morningQuotes
    in 12..16 -> dayQuotes
    else -> eveningQuotes
}

/**
 * v4 smart home top strip: combines EnhancedNotificationBox (latest unread activity) and
 * DynamicMotivationBox (time-of-day quote) into ONE rotating GlassCard. Both data sources are
 * kept: when there is an unread notification it shows first, then rotates to a motivation quote
 * every 7 s. Tap cycles instantly; the × dismisses for this session.
 */
@Composable
fun SmartHomeTopStrip(
    notifications: List<com.example.data.ActivityNotification>,
    onOpenNotifications: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pool = remember { quotesForNow() }
    var quoteIndex by remember { mutableIntStateOf((System.currentTimeMillis() % pool.size).toInt()) }
    var showNotif by remember { mutableStateOf(notifications.any { !it.isRead }) }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(7_000)
            showNotif = !showNotif
            if (!showNotif) quoteIndex = (quoteIndex + 1) % pool.size
        }
    }
    val latest = notifications.firstOrNull { !it.isRead } ?: notifications.firstOrNull()
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            cornerRadius = 18.dp
        ) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (latest != null && showNotif) onOpenNotifications() else { showNotif = !showNotif; quoteIndex = (quoteIndex + 1) % pool.size }
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlowDot(color = if (showNotif && latest != null) AuroraPink else AuroraCyan, size = 10.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (showNotif && latest != null) "Activity" else "Daily spark",
                        color = AuroraCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    androidx.compose.animation.AnimatedContent(
                        targetState = if (showNotif && latest != null) "${latest.actorName.ifBlank { "Someone" }}: ${latest.text}" else pool[quoteIndex],
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "strip_content"
                    ) { text ->
                        Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                }
                if (notifications.any { !it.isRead }) {
                    Text("${notifications.count { !it.isRead }}", color = AuroraPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(24.dp).clip(CircleShape).clickable { visible = false; onDismiss() }.padding(4.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * DynamicMotivationBox — a rotating, dismissible banner on the home feed.
 * Cycles a time-of-day-aware quote every 12 s; tapping refreshes instantly.
 * Pair it with EnhancedNotificationBox for a unified "smart top strip".
 */
@Composable
fun DynamicMotivationBox(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pool = remember { quotesForNow() }
    var index by remember { mutableIntStateOf((System.currentTimeMillis() % pool.size).toInt()) }
    var show by remember(visible) { mutableStateOf(visible) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(12_000)
            index = (index + 1) % pool.size
        }
    }

    AnimatedVisibility(
        visible = show,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7C5CFF).copy(alpha = .28f), Color(0xFF29D3FF).copy(alpha = .22f), Color(0xFFFF4FD8).copy(alpha = .20f)), start = Offset.Zero, end = Offset(800f, 200f)))
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(18.dp))
                .clickable { index = (index + 1) % pool.size }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8)))).padding(7.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily spark", color = Color(0xFF29D3FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text(pool[index], color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Campaign, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(24.dp).clip(CircleShape).clickable { show = false; onDismiss() }.padding(4.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}
