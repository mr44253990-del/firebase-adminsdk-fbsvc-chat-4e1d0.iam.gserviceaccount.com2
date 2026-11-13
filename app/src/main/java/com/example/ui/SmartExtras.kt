package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Group
import com.example.data.GroupMessage
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Group polls. The poll travels as a normal text message in a readable layout, so older app
// versions simply show the text. Votes live on the group record (members may update it).
// ---------------------------------------------------------------------------------------------

data class GroupPoll(val question: String, val options: List<String>)

object GroupPolls {
    private const val HEADER = "📊 POLL"

    fun compose(question: String, options: List<String>): String =
        buildString {
            append(HEADER).append('\n').append(question.trim())
            options.map { it.trim() }.filter { it.isNotBlank() }.forEach { append("\n• ").append(it) }
        }

    fun parse(text: String): GroupPoll? {
        if (!text.startsWith(HEADER)) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 4) return null
        val options = lines.drop(2).filter { it.startsWith("• ") }.map { it.removePrefix("• ").trim() }
        return if (options.size >= 2) GroupPoll(lines[1], options) else null
    }
}

@Composable
fun GroupPollCard(poll: GroupPoll, message: GroupMessage, group: Group, myUid: String, onVote: (Int) -> Unit) {
    val votes = group.pollVotes[message.messageId].orEmpty()
    val mine = votes[myUid]?.toInt()
    val total = votes.size
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).widthIn(max = 520.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .16f), MaterialTheme.colorScheme.surface.copy(alpha = .9f))))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f), RoundedCornerShape(24.dp))
            .padding(14.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Poll, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Poll by ${message.senderName.ifBlank { "a member" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        poll.options.forEachIndexed { index, option ->
            val count = votes.values.count { it.toInt() == index }
            val share by animateFloatAsState(if (total == 0) 0f else count / total.toFloat(), tween(500, easing = FastOutSlowInEasing), label = "poll_bar")
            val chosen = mine == index
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
                    .border(1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onVote(index) }
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary.copy(alpha = if (chosen) .38f else .20f)))
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (chosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(option, Modifier.weight(1f), fontSize = 13.5.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                    if (total > 0) Text("${(share * 100).toInt()}% · $count", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(if (total == 0) "No votes yet — tap an option" else "$total ${if (total == 1) "vote" else "votes"} · tap to change your vote", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Poll builder used inside Smart group tools. */
@Composable
fun GroupPollBuilder(onCreate: (String, List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(question, { question = it.take(140) }, placeholder = { Text("Ask the group something…") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
        options.forEachIndexed { i, value ->
            OutlinedTextField(value, { v -> options = options.toMutableList().also { it[i] = v.take(60) } }, placeholder = { Text("Option ${i + 1}") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.size < 6) TextButton(onClick = { options = options + "" }) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text("Option") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                onClick = { onCreate(question, options.filter { it.isNotBlank() }); question = ""; options = listOf("", "") },
                shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.Default.Poll, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send poll") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// AI writing tools for any composer (group chats get the same help private chats have).
// ---------------------------------------------------------------------------------------------

@Composable
fun AiWritingToolsRow(text: String, viewModel: ChatViewModel, onResult: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = { open = !open }, enabled = !busy,
            label = { Text(if (busy) "Working…" else "Writing tools") },
            leadingIcon = { Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp)) }
        )
        if (open && !busy) listOf(
            "Polish" to "Improve clarity and grammar while preserving the meaning",
            "Shorten" to "Make this message shorter while preserving key details",
            "Friendly" to "Rewrite this message in a warm, natural, friendly tone",
            "Formal" to "Rewrite this message in a polite, professional tone",
            "বাংলা" to "Translate this message into natural Bangla",
            "English" to "Translate this message into natural English"
        ).forEach { (label, instruction) ->
            SuggestionChip(onClick = {
                busy = true
                viewModel.askAssistant("$instruction. Return only the rewritten message. Do not add quotes or explanations. Draft:\n\n${text.take(3000)}", emptyList()) { result ->
                    busy = false; open = false
                    val cleaned = result.trim().trim('"')
                    if (cleaned.isNotBlank() && !cleaned.contains("disabled", true) && !cleaned.contains("not configured", true)) onResult(cleaned)
                }
            }, label = { Text(label) })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Connection banner: tells the user at once when the phone is offline and when it is back.
// ---------------------------------------------------------------------------------------------

@Composable
fun ConnectivityBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var online by remember { mutableStateOf(true) }
    var justRestored by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        fun current(): Boolean = runCatching {
            val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        online = current()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { if (!online) justRestored = true; online = true }
            override fun onLost(network: Network) { online = current() }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    LaunchedEffect(justRestored) { if (justRestored) { delay(2500); justRestored = false } }
    AnimatedVisibility(visible = !online || justRestored, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp).clip(CircleShape)
                .background(if (online) Color(0xFF14915F) else Color(0xFF2A2A33))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (online) Icons.Default.Wifi else Icons.Default.WifiOff, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (online) "Back online" else "No internet — messages will be sent when you are back", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Smart typing indicator: looks like the other person's own bubble, with a travelling wave and a
// line that changes the longer they keep writing.
// ---------------------------------------------------------------------------------------------

@Composable
fun SmartTypingIndicator(name: String) {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "smart_typing")
    val wave by motion.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "wave")
    val glow by motion.animateFloat(.35f, .9f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    val first = name.trim().substringBefore(' ').ifBlank { "Someone" }
    val phrase = when {
        seconds < 5 -> "$first is typing"
        seconds < 14 -> "$first is still typing"
        seconds < 30 -> "$first is writing a longer message"
        else -> "$first is taking their time"
    }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.Bottom) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(primary, tertiary)))
                .border(2.dp, primary.copy(alpha = glow * .6f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(first.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.width(7.dp))
        Column {
            Row(
                Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .92f))
                    .border(1.dp, Brush.horizontalGradient(listOf(primary.copy(alpha = glow * .7f), tertiary.copy(alpha = .15f))), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(3) { i ->
                    val phase = ((wave - i * .16f) % 1f + 1f) % 1f
                    val lift = kotlin.math.sin(phase * 2f * Math.PI).toFloat().coerceAtLeast(0f)
                    Box(
                        Modifier.size(8.dp).graphicsLayerLift(lift).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(primary, tertiary)))
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(phrase + ".".repeat(1 + (seconds % 3)), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private fun Modifier.graphicsLayerLift(lift: Float): Modifier =
    this.then(Modifier.graphicsLayer { translationY = -lift * 7f * density; val s = 1f + lift * .25f; scaleX = s; scaleY = s; alpha = .55f + .45f * lift })

// ---------------------------------------------------------------------------------------------
// Occasional Premium promo: a small non-blocking card, at most once every few days.
// ---------------------------------------------------------------------------------------------

@Composable
fun PremiumPromoToast(eligible: Boolean, userKey: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("convo_premium_promo", Context.MODE_PRIVATE) }
    var visible by remember(userKey) { mutableStateOf(false) }
    val perks = remember {
        listOf(
            "Verified badge next to your name" to Icons.Default.Verified,
            "Priority Convo AI and all 12 Studio tools" to Icons.Default.AutoAwesome,
            "Exclusive themes, call looks and ringtone" to Icons.Default.Palette,
            "Story insights and anonymous preview" to Icons.Default.Insights,
            "Priority support straight from the admin" to Icons.Default.SupportAgent
        )
    }
    var perk by remember { mutableIntStateOf((System.currentTimeMillis() / 86_400_000L % perks.size).toInt()) }
    LaunchedEffect(eligible, userKey) {
        if (!eligible || userKey.isBlank()) { visible = false; return@LaunchedEffect }
        val opens = prefs.getInt("opens_$userKey", 0) + 1
        prefs.edit().putInt("opens_$userKey", opens).apply()
        val last = prefs.getLong("last_$userKey", 0L)
        // Not on the first few launches, and never more often than every three days.
        if (opens < 3 || System.currentTimeMillis() - last < 3L * 86_400_000L) return@LaunchedEffect
        delay(20_000)
        prefs.edit().putLong("last_$userKey", System.currentTimeMillis()).apply()
        visible = true
        repeat(4) { delay(3500); perk = (perk + 1) % perks.size }
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(), modifier = modifier) {
        val sheen by androidx.compose.animation.core.rememberInfiniteTransition(label = "promo").animateFloat(
            0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "sheen"
        )
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().widthIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A2A06), Color(0xFF8A5A00), Color(0xFF5B2A86)), start = androidx.compose.ui.geometry.Offset(sheen * 500f, 0f), end = androidx.compose.ui.geometry.Offset(sheen * 500f + 600f, 300f)))
                .border(1.dp, Brush.horizontalGradient(listOf(Color(0xFFFFD36E), Color(0xFFFF8A3D), Color(0xFFB07CFF))), RoundedCornerShape(24.dp))
                .clickable { visible = false; onOpen() }
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFC233)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFF3A2A06), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Convo Premium", color = Color(0xFFFFE9B0), fontWeight = FontWeight.Black, fontSize = 14.sp)
                androidx.compose.animation.AnimatedContent(perk, label = "promo_perk") { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(perks[i].second, null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(perks[i].first, color = Color.White.copy(alpha = .9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text("See plans", color = Color(0xFF3A2A06), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFFFD36E)).padding(horizontal = 12.dp, vertical = 7.dp))
            IconButton(onClick = { visible = false }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Dismiss", tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quick-reaction button polish: a breathing halo behind the emoji and a gentle idle wobble.
// ---------------------------------------------------------------------------------------------

fun Modifier.quickReactionGlow(accent: Color): Modifier = composed {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "quick_glow")
    val pulse by motion.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.LinearEasing)), label = "pulse")
    val spin by motion.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(5200, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    this.then(
        Modifier.drawBehind {
            val r = size.minDimension / 2f
            // Expanding soft ring.
            drawCircle(accent.copy(alpha = (1f - pulse) * .35f), radius = r * (.9f + .55f * pulse), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f + 4f * (1f - pulse)))
            // Glass disc with a slowly turning light edge.
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .30f), accent.copy(alpha = .08f)), center, r), radius = r)
            rotate(spin) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = .85f), accent, Color.Transparent), center),
                    startAngle = 0f, sweepAngle = 140f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - r + 2f, center.y - r + 2f), size = androidx.compose.ui.geometry.Size((r - 2f) * 2, (r - 2f) * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    )
}

@Composable
fun WobbleEmoji(emoji: String, sizeSp: Int = 27) {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "wobble")
    val tilt by motion.animateFloat(-9f, 9f, androidx.compose.animation.core.infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "tilt")
    val bob by motion.animateFloat(.94f, 1.08f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "bob")
    Text(emoji, fontSize = sizeSp.sp, modifier = Modifier.graphicsLayer { rotationZ = tilt; scaleX = bob; scaleY = bob })
}

/** "Send once" emoji tray: sends a different reaction without changing the chat's default one. */
@Composable
fun QuickSendOnceRow(onSend: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("Send once", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
        listOf(listOf("❤️", "😂", "😮", "😢", "😡"), listOf("🔥", "🎉", "🙏", "💯", "😍")).forEach { row ->
            Row {
                row.forEach { emoji ->
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable { onSend(emoji) }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 23.sp) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
}
