package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.User
import com.example.ui.components.*
import com.example.ui.theme.*

/** Profile — cover, avatar, bio, stats, profile link, and the user's posts. */
@Composable
fun ProfileScreen(
    viewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    userId: String,
    onBack: () -> Unit,
    onMessage: (User) -> Unit
) {
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val posts by feedViewModel.posts.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val presenceMap by viewModel.presenceMap.collectAsState()

    val isMe = userId == currentUser?.uid
    val user: User? = if (isMe) currentUser else users.firstOrNull { it.uid == userId }
    var editOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { feedViewModel.start() }

    val userPosts = posts.filter { it.authorId == userId }
    val totalLikes = userPosts.sumOf { it.likeCount }
    val profileLink = "https://firechat.app/u/${user?.username ?: ""}"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val presence = presenceMap[userId]

    GlassBackground(bubbleCount = 8) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            item(key = "header") {
                // Cover
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(Brush.linearGradient(LocalPalette.current.bubbleMine.map { it.copy(alpha = 0.55f) }))
                ) {
                    FloatingBubbles(count = 6)
                    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).align(Alignment.TopStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }

                Column(
                    Modifier.fillMaxWidth().offset(y = (-46).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.border(4.dp, Color(0xFF0B0B18), CircleShape)
                    ) {
                        UserAvatar(
                            name = user?.name ?: "", photoUrl = user?.photoUrl ?: "",
                            size = 104.dp, online = presence?.online == true
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(user?.name ?: "", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("@${user?.username ?: ""}", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.secondary)
                    if (!isMe) {
                        Text(
                            presence?.let { com.example.data.PresenceManager.lastSeenLabel(it) } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (presence?.online == true) OnlineGreen else TextSecondary
                        )
                    }
                    if (!user?.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            user?.bio ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBubble("${userPosts.size}", "Posts")
                        StatBubble("$totalLikes", "Likes")
                        StatBubble("${userPosts.sumOf { it.commentCount }}", "Comments")
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isMe) {
                            GlassChip("✏️ Edit profile") { editOpen = true }
                        } else {
                            GradientButton(
                                text = "Message",
                                onClick = { user?.let { onMessage(it) } }
                            )
                        }
                        GlassChip("🔗 Copy link") {
                            clipboard.setText(AnnotatedString(profileLink))
                            Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item(key = "posts_title") {
                SectionTitle(
                    if (isMe) "My posts" else "Posts",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (userPosts.isEmpty()) {
                item(key = "no_posts") {
                    EmptyState("📸", "Nothing shared yet", if (isMe) "Share your first post from Home!" else "This user hasn't posted yet")
                }
            } else {
                items(userPosts, key = { it.postId }) { post ->
                    PostCard(
                        post = post,
                        myUid = currentUser?.uid ?: "",
                        isMostVisible = false,
                        onLike = { feedViewModel.toggleLike(post, currentUser) },
                        onComment = { },
                        onAuthorClick = { },
                        onView = { },
                        onEdit = { text, vis -> feedViewModel.editPost(post, text, vis) },
                        onDelete = { feedViewModel.deletePost(post) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (editOpen && currentUser != null) {
        EditProfileDialog(
            user = currentUser!!,
            uploading = uploading,
            onDismiss = { editOpen = false },
            onSave = { name, bio, uri ->
                viewModel.updateProfile(name, bio, uri) { ok ->
                    editOpen = false
                    Toast.makeText(
                        context,
                        if (ok) "Profile updated" else "Update failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun StatBubble(value: String, label: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassWhite)
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
fun EditProfileDialog(
    user: User,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var bio by remember { mutableStateOf(user.bio) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photoUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151528),
        title = { GradientText("Edit profile", MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(GlassWhite)
                        .border(2.dp, Brush.linearGradient(LocalPalette.current.storyRing), CircleShape)
                        .clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val model: Any? = photoUri ?: user.photoUrl.ifBlank { null }
                    if (model != null) {
                        coil.compose.AsyncImage(
                            model = model, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Edit, null, tint = LocalPalette.current.primary)
                    }
                }
                Text("Tap to change photo (saved to Supabase)", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                GlassField(value = name, onValueChange = { name = it }, label = "Name")
                Spacer(Modifier.height(10.dp))
                GlassField(value = bio, onValueChange = { bio = it }, label = "Bio", singleLine = false)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !uploading, onClick = { onSave(name.trim(), bio.trim(), photoUri) }) {
                Text(if (uploading) "Saving…" else "Save", color = LocalPalette.current.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
