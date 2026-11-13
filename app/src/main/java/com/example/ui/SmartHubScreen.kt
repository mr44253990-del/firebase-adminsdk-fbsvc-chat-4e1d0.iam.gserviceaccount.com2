package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Group
import com.example.data.Post
import com.example.data.User

private data class HubAction(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val route: String, val keywords: String)

private val HUB_ACTIONS = listOf(
    HubAction("Convo AI", "Ask anything, find profiles", Icons.Default.AutoAwesome, Color(0xFF8B7BFF), "assistant", "ai assistant chat bot ask question এআই সহকারী"),
    HubAction("AI Studio", "Rewrite, translate, captions", Icons.Default.AutoFixHigh, Color(0xFF36D8E6), "ai_studio", "studio rewrite translate caption grammar bio summary অনুবাদ"),
    HubAction("New post", "Share a photo, video or thought", Icons.Default.AddBox, Color(0xFFFF6FB5), "compose_post", "post create share upload story পোস্ট"),
    HubAction("Scan / my QR", "Add people with a QR code", Icons.Default.QrCodeScanner, Color(0xFF19C37D), "qr_contacts", "qr scan code add contact friend কিউআর"),
    HubAction("Link a computer", "Sign in on the web with QR", Icons.Default.Computer, Color(0xFF3AA0FF), "device_login", "web pc computer browser device login linked ওয়েব"),
    HubAction("Premium", "Plans and benefits", Icons.Default.WorkspacePremium, Color(0xFFE2A93B), "premium", "premium plan pay upgrade vip প্রিমিয়াম"),
    HubAction("What's new", "Everything in this update", Icons.Default.NewReleases, Color(0xFFB07CFF), "whats_new", "new update changes release notes আপডেট")
)

/**
 * Smart Hub (v5.1): one search box for the whole app — people, groups, posts and features —
 * plus shortcuts and "who is online now". Think of it as the app's command centre.
 */
@Composable
fun SmartHubScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenUser: (User) -> Unit,
    onOpenProfile: (User) -> Unit,
    onOpenGroup: (Group) -> Unit,
    onOpenPost: (Post) -> Unit,
    onRoute: (String) -> Unit
) {
    val users by viewModel.filteredUsersState.collectAsState()
    val groups by viewModel.groupsState.collectAsState()
    val posts by viewModel.postsState.collectAsState()
    val me by viewModel.currentUserState.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    KeyboardResizeMode()
    val parsed = remember(query) { SmartSearch.parse(query) }
    val people = remember(parsed, users) { if (parsed.tokens.isEmpty()) emptyList() else SmartSearch.rankUsers(parsed, users).take(12) }
    val groupHits = remember(query, groups) { if (query.isBlank()) emptyList() else groups.filter { it.name.contains(query.trim(), true) || it.description.contains(query.trim(), true) }.take(8) }
    val postHits = remember(parsed, posts) { if (parsed.tokens.isEmpty()) emptyList() else SmartSearch.rankPosts(parsed, posts).take(8) }
    val actionHits = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) HUB_ACTIONS else HUB_ACTIONS.filter { a -> q.split(' ').any { it.length >= 2 && (a.keywords.contains(it) || a.title.lowercase().contains(it)) } }
    }
    val onlineFriends = remember(users, me) { users.filter { it.isOnline && (me?.friends?.contains(it.uid) == true) }.take(20) }
    val drift by rememberInfiniteTransition(label = "hub").animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "hub_drift")

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .20f), Color.Transparent, MaterialTheme.colorScheme.tertiary.copy(alpha = .14f)), start = Offset(drift * 600f, 0f), end = Offset(900f, 1800f - drift * 500f)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                    placeholder = { Text("Search people, groups, posts, features…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    shape = CircleShape, modifier = Modifier.weight(1f).focusRequester(focus)
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (query.isBlank()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF2B1F6B), Color(0xFF5B3FD6), Color(0xFF14A3B8))))
                                .padding(16.dp)
                        ) {
                            Text("Hi ${me?.name?.substringBefore(' ').orEmpty().ifBlank { "there" }} 👋", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("${onlineFriends.size} friends online · ${groups.size} groups · ${users.size} people to discover", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        }
                    }
                    if (onlineFriends.isNotEmpty()) {
                        item { HubTitle("Online now") }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                onlineFriends.forEach { friend ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { onOpenUser(friend) }) {
                                        Box(contentAlignment = Alignment.BottomEnd) {
                                            HubAvatar(friend.profileImageUrl, friend.name, 54)
                                            Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2EE86F))) }
                                        }
                                        Text(friend.name.substringBefore(' '), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { HubTitle("Shortcuts") }
                }
                if (actionHits.isNotEmpty()) {
                    if (query.isNotBlank()) item { HubTitle("Features") }
                    items(actionHits.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { a ->
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                        .background(Brush.linearGradient(listOf(a.tint.copy(alpha = .20f), MaterialTheme.colorScheme.surface.copy(alpha = .7f))))
                                        .border(1.dp, a.tint.copy(alpha = .4f), RoundedCornerShape(20.dp))
                                        .clickable { onRoute(a.route) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(a.tint.copy(alpha = .25f)), contentAlignment = Alignment.Center) { Icon(a.icon, null, tint = a.tint, modifier = Modifier.size(20.dp)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(a.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                        Text(a.subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, lineHeight = 13.sp)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (people.isNotEmpty()) {
                    item { HubTitle("People") }
                    items(people, key = { "p_" + it.uid }) { person ->
                        HubRow(person.profileImageUrl, person.name.ifBlank { "Convo user" }, "@${person.username}" + if (person.isOnline) "  ·  online" else "", onClick = { onOpenProfile(person) }) {
                            IconButton(onClick = { onOpenUser(person) }) { Icon(Icons.Default.ChatBubble, "Message", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
                if (groupHits.isNotEmpty()) {
                    item { HubTitle("Groups") }
                    items(groupHits, key = { "g_" + it.id }) { group ->
                        HubRow(group.profileUrl, group.name, "${group.members.size} members" + if (group.activeCallRoomId.isNotBlank()) "  ·  call in progress" else "", onClick = { onOpenGroup(group) }) {
                            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (postHits.isNotEmpty()) {
                    item { HubTitle("Posts") }
                    items(postHits, key = { "post_" + it.id }) { post ->
                        HubRow(post.imageUrl.ifBlank { post.senderProfilePic }, post.title.ifBlank { post.text.ifBlank { "Media post" } }, "by ${post.senderName} · ${post.reactions.size} reactions", onClick = { onOpenPost(post) }) {
                            Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (query.isNotBlank()) item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)).clickable { onRoute("assistant") }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Ask Convo AI about \"${query.trim()}\"", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (query.isNotBlank() && people.isEmpty() && groupHits.isEmpty() && postHits.isEmpty() && actionHits.isEmpty()) item {
                    Text("Nothing matched. Try a name, @username, a group name or a feature like \"translate\".", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HubTitle(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = .5.sp)

@Composable
private fun HubAvatar(url: String, name: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (url.isNotBlank()) AsyncImage(url, name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
    }
}

@Composable
private fun HubRow(image: String, title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HubAvatar(image, title, 44)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
