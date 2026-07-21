package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: ChatViewModel,
    user: User,
    onBack: () -> Unit,
    onMessage: () -> Unit
) {
    val context = LocalContext.current
    val allUsers by viewModel.usersState.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()
    val posts by viewModel.postsState.collectAsState()
    val sentRequests by viewModel.sentFriendRequestIds.collectAsState()
    val liveUser = allUsers.find { it.uid == user.uid } ?: user
    val userPosts = posts.filter { it.senderId == liveUser.uid }
    val totalLikes = userPosts.sumOf { post -> post.reactions.size + post.mediaReactions.values.sumOf { it.size } }
    val isFriend = currentUser?.friends?.contains(liveUser.uid) == true
    val isFollowing = currentUser?.following?.contains(liveUser.uid) == true
    val isRequested = sentRequests.contains("${currentUser?.uid}_${liveUser.uid}")
    var showFeatureSuggestion by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(liveUser.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "profile_header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(190.dp)) {
                        if (liveUser.coverImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = liveUser.coverImageUrl,
                                contentDescription = "Cover photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary))
                                )
                            )
                        }
                        AsyncImage(
                            model = liveUser.profileImageUrl.ifBlank { null },
                            contentDescription = liveUser.name,
                            error = painterResource(R.drawable.img_app_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 54.dp)
                                .size(116.dp).clip(CircleShape)
                                .border(5.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                    Spacer(Modifier.height(62.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(liveUser.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (liveUser.role == "moderator") {
                            Spacer(Modifier.width(7.dp))
                            AssistChip(onClick = {}, label = { Text("Moderator") }, leadingIcon = { Icon(Icons.Outlined.Verified, null, Modifier.size(16.dp)) })
                        }
                    }
                    Text("@${liveUser.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (liveUser.bio.isNotBlank()) {
                        Text(liveUser.bio, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    }
                    Row(
                        Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileMetric(liveUser.friends.size.toString(), "Friends")
                        ProfileMetric(liveUser.followers.size.toString(), "Followers")
                        ProfileMetric(userPosts.size.toString(), "Posts")
                        ProfileMetric(totalLikes.toString(), "Likes")
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = { viewModel.toggleFollow(liveUser) }, modifier = Modifier.weight(1f)) {
                            Icon(if (isFollowing) Icons.Outlined.PersonRemove else Icons.Outlined.PersonAdd, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp)); Text(if (isFollowing) "Unfollow" else "Follow")
                        }
                        if (!isFriend) {
                            if (isRequested) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.cancelFriendRequest(liveUser.uid) { ok ->
                                            Toast.makeText(context, if (ok) "Request cancelled" else "Could not cancel", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel request") }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.sendFriendRequest(liveUser) { _, message ->
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.PersonAdd, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add friend")
                                }
                            }
                        }
                        OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isFriend) "Message" else "Message request")
                        }
                    }
                    if (liveUser.role == "moderator" && liveUser.uid != currentUser?.uid) {
                        TextButton(onClick = { showFeatureSuggestion = true }) {
                            Icon(Icons.Outlined.Lightbulb, null); Spacer(Modifier.width(6.dp)); Text("Suggest an app update or feature")
                        }
                    }
                }
            }
            item(key = "about") {
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Cake, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(if (liveUser.dob.isBlank()) "Birthday not shared" else "Born ${liveUser.dob}")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Joined ${java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(liveUser.createdAt))}")
                        }
                    }
                }
            }
            item { Text("Posts", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (userPosts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text("No posts yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(userPosts, key = { it.id }) { post ->
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        SocialPostItem(post, viewModel, onProfileSelected = {})
                    }
                }
            }
        }
    }

    if (showFeatureSuggestion) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeatureSuggestion = false },
            title = { Text("Suggest a FireChat feature", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it.take(100) }, label = { Text("Request title") }, singleLine = true)
                    OutlinedTextField(description, { description = it.take(1500) }, label = { Text("Describe the update or feature") }, minLines = 5)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.submitFeatureRequest(title, description) { ok ->
                        Toast.makeText(context, if (ok) "Request sent to the moderator" else "Could not send request", Toast.LENGTH_LONG).show()
                        if (ok) showFeatureSuggestion = false
                    }
                }, enabled = title.isNotBlank() && description.isNotBlank()) { Text("Send request") }
            },
            dismissButton = { TextButton(onClick = { showFeatureSuggestion = false }) { Text("Cancel") } }
        )
    }

}

@Composable
private fun ProfileMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
