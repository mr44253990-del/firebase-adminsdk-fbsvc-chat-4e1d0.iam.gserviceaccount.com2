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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
