package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Group
import com.example.data.PresenceManager
import com.example.data.User
import com.example.ui.components.*
import com.example.ui.theme.*

/**
 * Messages menu — every conversation with unread red glow badges, live search,
 * active-now rail, and group chats (create group, add members, group photo).
 */
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    onOpenChat: (User) -> Unit,
    onOpenGroup: (Group) -> Unit
) {
    val conversations by viewModel.conversationsState.collectAsState()
    val filteredUsers by viewModel.filteredUsersState.collectAsState()
    val presenceMap by viewModel.presenceMap.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()

    var query by remember { mutableStateOf("") }
    var showCreateGroup by remember { mutableStateOf(false) }

    GlassBackground(bubbleCount = 8) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientText("Messages", MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GlassWhite)
                        .border(1.dp, GlassBorderSoft, CircleShape)
                        .clickable { showCreateGroup = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Create group", tint = LocalPalette.current.primary)
                }
            }

            GlassSearchBar(
                value = query,
                onValueChange = { query = it; viewModel.searchUsers(it) },
                placeholder = "Search people or chats…",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                // Active now rail
                if (query.isBlank()) {
                    val onlineUsers = filteredUsers.filter { presenceMap[it.uid]?.online == true }
                    if (onlineUsers.isNotEmpty()) {
                        item(key = "active_rail") {
                            Column {
                                SectionTitle("Active now", modifier = Modifier.padding(horizontal = 16.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(onlineUsers, key = { "on_${it.uid}" }) { user ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { onOpenChat(user) }
                                        ) {
                                            UserAvatar(
                                                name = user.name, photoUrl = user.photoUrl,
                                                size = 56.dp, online = true
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                user.name.split(" ").firstOrNull() ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary, maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Conversations
                if (query.isBlank()) {
                    if (conversations.isEmpty()) {
                        item(key = "conv_empty") {
                            EmptyState("💬", "No conversations yet", "Search for people above and say hello!")
                        }
                    }
                    items(conversations, key = { it.chatId }) { convo ->
                        val presence = presenceMap[convo.partnerId]
                        ConversationRow(
                            convo = convo,
                            online = presence?.online == true,
                            lastSeen = presence?.let { PresenceManager.lastSeenLabel(it) } ?: "",
                            myUid = currentUser?.uid ?: "",
                            onClick = {
                                if (convo.isGroup) {
                                    val g = viewModel.groupsState.value.firstOrNull { it.groupId == convo.chatId }
                                    if (g != null) onOpenGroup(g)
                                } else {
                                    val u = viewModel.usersState.value.firstOrNull { it.uid == convo.partnerId }
                                    if (u != null) onOpenChat(u) else onOpenChat(
                                        User(
                                            uid = convo.partnerId, name = convo.partnerName,
                                            username = convo.partnerUsername, photoUrl = convo.partnerPhoto
                                        )
                                    )
                                }
                            }
                        )
                    }
                } else {
                    // Search results — tap to open chat
                    item(key = "search_header") {
                        SectionTitle("People", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(filteredUsers, key = { "s_${it.uid}" }) { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(GlassWhite)
                                .clickable { onOpenChat(user) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = user.name, photoUrl = user.photoUrl, size = 48.dp,
                                online = presenceMap[user.uid]?.online == true
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(user.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("@${user.username}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            viewModel = viewModel,
            uploading = uploading,
            onDismiss = { showCreateGroup = false },
            onCreated = { group ->
                showCreateGroup = false
                if (group != null) onOpenGroup(group)
            }
        )
    }
}

@Composable
private fun ConversationRow(
    convo: com.example.data.Conversation,
    online: Boolean,
    lastSeen: String,
    myUid: String,
    onClick: () -> Unit
) {
    val hasUnread = convo.unreadCount > 0
    val glowAlpha by rememberInfiniteTransition(label = "g").animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "a"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (hasUnread) Color(0x14FF3B5C) else GlassWhite)
            .border(
                1.dp,
                if (hasUnread) UnreadRed.copy(alpha = glowAlpha) else GlassBorderSoft,
                RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            if (convo.isGroup) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(LocalPalette.current.bubbleMine)),
                    contentAlignment = Alignment.Center
                ) {
                    if (convo.partnerPhoto.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = convo.partnerPhoto, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
                    }
                }
            } else {
                UserAvatar(
                    name = convo.partnerName, photoUrl = convo.partnerPhoto,
                    size = 54.dp, online = online
                )
            }
            // pulsing red light for unread
            if (hasUnread) {
                UnreadDot(Modifier.align(Alignment.TopEnd), size = 13.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    convo.partnerName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    timeAgo(convo.lastTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) UnreadRed else TextSecondary
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prefix = if (convo.lastSenderId == myUid) "You: " else ""
                Text(
                    prefix + convo.lastMessage.ifBlank { lastSeen },
                    color = if (hasUnread) Color.White else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasUnread) {
                    CountBadge(count = convo.unreadCount)
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    viewModel: ChatViewModel,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onCreated: (Group?) -> Unit
) {
    val users by viewModel.filteredUsersState.collectAsState()
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var query by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photoUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151528),
        title = { GradientText("Create group", MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(GlassWhite)
                            .border(1.dp, GlassBorderSoft, CircleShape)
                            .clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUri != null) {
                            coil.compose.AsyncImage(
                                model = photoUri, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else Icon(Icons.Default.Add, null, tint = LocalPalette.current.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassField(
                        value = name, onValueChange = { name = it },
                        label = "Group name", modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                GlassSearchBar(
                    value = query,
                    onValueChange = { query = it; viewModel.searchUsers(it) },
                    placeholder = "Add members…"
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(users, key = { it.uid }) { user ->
                        val isSelected = user.uid in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) LocalPalette.current.primary.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable {
                                    selected = if (isSelected) selected - user.uid else selected + user.uid
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = user.name, photoUrl = user.photoUrl, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(user.name, color = Color.White, modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(Icons.Default.Add, null, tint = LocalPalette.current.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty() && !uploading,
                onClick = {
                    val members = users.filter { it.uid in selected }
                    viewModel.createGroup(name.trim(), members, photoUri, onCreated)
                }
            ) {
                if (uploading) Text("Creating…", color = TextSecondary)
                else Text("Create (${selected.size})", color = LocalPalette.current.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
