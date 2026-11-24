package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Group
import com.example.data.User

private fun groupCallLive(group: Group, now: Long) = group.activeCallRoomId.isNotBlank() && now - group.activeCallStartedAt < 3 * 60 * 60 * 1000L

/**
 * Groups home (v5.2 redesign): a living header with totals, search, smart filters, and cards
 * that show who is inside, the latest message, a pinned note and a pulsing LIVE badge when a
 * call is running (those groups float to the top).
 */
@Composable
fun GroupsHubTab(groups: List<Group>, users: List<User>, me: User?, onCreate: () -> Unit, onOpen: (Group) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val now = System.currentTimeMillis()
    val myUid = me?.uid.orEmpty()
    val live = groups.count { groupCallLive(it, now) }
    val shown = remember(groups, query, filter, now / 30_000L) {
        groups.filter { g ->
            (query.isBlank() || g.name.contains(query.trim(), true) || g.lastMessage.contains(query.trim(), true) || g.description.contains(query.trim(), true)) &&
                when (filter) {
                    "live" -> groupCallLive(g, now)
                    "mine" -> g.createdBy == myUid || myUid in g.admins
                    "big" -> g.members.size >= 5
                    "pinned" -> g.pinnedMessage.isNotBlank()
                    else -> true
                }
        }.sortedWith(compareByDescending<Group> { groupCallLive(it, now) }.thenByDescending { it.createdAt })
    }
    val motion = rememberInfiniteTransition(label = "groups_hub")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val pulse by motion.animateFloat(.8f, 1.2f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "hero") {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B1548), Color(0xFF4B36C9), Color(0xFF0F7F94)), start = Offset(drift * 500f, 0f), end = Offset(900f - drift * 300f, 500f)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Your groups"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text(
                            if (live > 0) "$live group ${if (live == 1) "call is" else "calls are"} live right now" else "Talk, call and plan together",
                            color = Color.White.copy(alpha = .85f), fontSize = 12.sp
                        )
                    }
                    Row(
                        Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onCreate).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GroupAdd, null, tint = Color(0xFF4B36C9), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Create Group"), color = Color(0xFF4B36C9), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HubStat("Groups", groups.size.toString(), Modifier.weight(1f))
                    HubStat("Live calls", live.toString(), Modifier.weight(1f))
                    HubStat("People", groups.flatMap { it.members }.distinct().size.toString(), Modifier.weight(1f))
                    HubStat("You manage", groups.count { it.createdBy == myUid || myUid in it.admins }.toString(), Modifier.weight(1f))
                }
            }
        }
        if (groups.isNotEmpty()) {
            item(key = "search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                    placeholder = { Text("Search groups and last messages") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    shape = CircleShape, modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "filters") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("all" to "All", "live" to "Live calls", "mine" to "I manage", "pinned" to "With pinned note", "big" to "5+ members").forEach { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
            }
        }
        if (groups.isEmpty()) item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(92.dp).graphicsLayer { scaleX = pulse * .9f; scaleY = pulse * .9f }.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Forum, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(tr("You haven't joined any groups yet"), fontWeight = FontWeight.Bold)
                Text("Create one for family, friends or your team — calls, polls and AI summaries included.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 30.dp, vertical = 6.dp))
                Button(onClick = onCreate, shape = CircleShape) { Icon(Icons.Default.GroupAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Create Group")) }
            }
        } else if (shown.isEmpty()) item(key = "none") {
            Text("No group matches this search or filter.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        itemsIndexed(shown, key = { _, g -> g.id }) { index, group ->
            val isLive = groupCallLive(group, now)
            val enter = remember(group.id) { Animatable(0f) }
            LaunchedEffect(group.id) { kotlinx.coroutines.delay(40L * index.coerceAtMost(8)); enter.animateTo(1f, spring(dampingRatio = .75f, stiffness = 300f)) }
            val accent = if (isLive) Color(0xFF19C37D) else MaterialTheme.colorScheme.primary
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * 34f }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = if (isLive) .20f else .10f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))))
                    .border(1.dp, accent.copy(alpha = if (isLive) .7f else .18f), RoundedCornerShape(24.dp))
                    .clickable { onOpen(group) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                    if (isLive) Box(Modifier.size(58.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 1.3f - pulse }.clip(CircleShape).background(accent.copy(alpha = .35f)))
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6)))), contentAlignment = Alignment.Center) {
                        Text(group.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        if (group.profileUrl.isNotBlank()) AsyncImage(group.profileUrl, group.name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (isLive) {
                            Spacer(Modifier.width(6.dp))
                            Row(Modifier.clip(CircleShape).background(accent).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (group.activeCallVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(3.dp)); Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        if (group.createdBy == myUid || myUid in group.admins) { Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Shield, "You manage this group", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp)) }
                    }
                    Text(
                        when {
                            isLive -> "${group.activeCallHostName.ifBlank { "Someone" }} started a call — tap to join"
                            group.lastMessage.isNotBlank() -> group.lastMessage
                            group.description.isNotBlank() -> group.description
                            else -> "No messages yet — say hello 👋"
                        },
                        fontSize = 12.5.sp, color = if (isLive) accent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (group.pinnedMessage.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(group.pinnedMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(5.dp))
                    // Overlapping member pictures.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val faces = group.members.take(5).map { id -> users.firstOrNull { it.uid == id } ?: me?.takeIf { it.uid == id } }
                        Box(Modifier.height(22.dp).width((22 + (faces.size - 1).coerceAtLeast(0) * 14).dp)) {
                            faces.forEachIndexed { i, person ->
                                Box(
                                    Modifier.padding(start = (i * 14).dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .25f))
                                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(person?.name?.take(1)?.uppercase() ?: "•", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    if (!person?.profileImageUrl.isNullOrBlank()) AsyncImage(person?.profileImageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        val online = group.members.count { id -> users.firstOrNull { it.uid == id }?.isOnline == true }
                        Text("${group.members.size} members" + if (online > 0) "  ·  $online online" else "", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HubStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .12f)).border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(16.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = .75f), fontSize = 9.5.sp, maxLines = 1)
    }
}
