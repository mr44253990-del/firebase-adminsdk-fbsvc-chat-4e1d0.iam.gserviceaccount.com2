package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.User
import java.text.SimpleDateFormat
import java.util.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import android.content.ClipData
import android.content.ClipboardManager

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
    val userPosts = remember(posts, liveUser.uid) { posts.filter { it.senderId == liveUser.uid } }
    val totalLikes = remember(userPosts) { userPosts.sumOf { post -> post.reactions.size + post.mediaReactions.values.sumOf { it.size } } }
    val isMe = liveUser.uid == currentUser?.uid
    val isFriend = currentUser?.friends?.contains(liveUser.uid) == true
    val isFollowing = currentUser?.following?.contains(liveUser.uid) == true
    val isRequested = sentRequests.contains("${currentUser?.uid}_${liveUser.uid}")
    var showFeatureSuggestion by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    val profileLink = remember(liveUser.uid) { "https://solitary-hill-dcdc.mr44253990.workers.dev/profile?uid=${Uri.encode(liveUser.uid)}" }
    val profileQr = remember(profileLink) { runCatching { createQrBitmap(profileLink, 640) }.getOrNull() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(liveUser.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${liveUser.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { showProfileMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Profile actions")
                        }
                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy profile link") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Convo profile link", profileLink))
                                    showProfileMenu = false
                                    Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share profile") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    showProfileMenu = false
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${liveUser.name} — Convo profile\\n$profileLink")
                                    }, "Share profile"))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero") {
                Column {
                    Box(Modifier.fillMaxWidth().height(250.dp)) {
                        if (liveUser.coverImageUrl.isNotBlank()) {
                            AsyncImage(liveUser.coverImageUrl, "Cover photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = liveUser.coverScale.coerceIn(1f, 2f); scaleY = liveUser.coverScale.coerceIn(1f, 2f); translationY = liveUser.coverOffsetY.coerceIn(-1f, 1f) * 90f })
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = .96f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = .82f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = .78f)
                                        )
                                    )
                                )
                            )
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = .18f), MaterialTheme.colorScheme.background.copy(alpha = .88f)))))
                        Box(
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp).offset(y = 50.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            AsyncImage(
                                model = liveUser.profileImageUrl.ifBlank { null }, contentDescription = liveUser.name,
                                error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                                modifier = Modifier.size(122.dp).clip(CircleShape).border(5.dp, MaterialTheme.colorScheme.background, CircleShape)
                            )
                            Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(4.dp).clip(CircleShape).background(if (liveUser.isOnline) Color(0xFF45D483) else MaterialTheme.colorScheme.outline))
                        }
                    }
                    Spacer(Modifier.height(58.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(liveUser.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            if (liveUser.activeProfileType.equals("Creator", ignoreCase = true)) {
                                Spacer(Modifier.width(6.dp))
                                Surface(color = Color(0xFF6C4BFF), shape = CircleShape) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = Color.White)
                                        Spacer(Modifier.width(4.dp)); Text("Creator", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (liveUser.role == "moderator") {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Verified, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(5.dp)); Text("Moderator", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            } else if (liveUser.isPremium && (liveUser.premiumPlan == "lifetime" || liveUser.premiumUntil > System.currentTimeMillis())) {
                                Surface(color = Color(0xFFFFE082), shape = CircleShape) {
                                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Verified, null, Modifier.size(16.dp), tint = Color(0xFF7A4E00))
                                        val remaining = if (liveUser.premiumPlan == "lifetime") "∞" else "${((liveUser.premiumUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 86_400_000L) + 1}d"
                                        Spacer(Modifier.width(4.dp)); Text("Premium $remaining", color = Color(0xFF7A4E00), fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Text("@${liveUser.username}  •  ${if (liveUser.isOnline) "Active now" else "Offline"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f), shape = CircleShape) {
                            Text(liveUser.activeProfileType.ifBlank { "Personal" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        if (liveUser.professionalTitle.isNotBlank() || liveUser.pronouns.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOf(liveUser.professionalTitle, liveUser.pronouns).filter { it.isNotBlank() }.joinToString(" • "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (liveUser.bio.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(liveUser.bio, style = MaterialTheme.typography.bodyLarge, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (liveUser.publicContactEmail.isNotBlank() || liveUser.socialLinks.any { it.value.isNotBlank() }) {
                item(key = "contact_card") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f))) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Digital contact card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            if (liveUser.publicContactEmail.isNotBlank()) {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${liveUser.publicContactEmail}"))) }
                                }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Outlined.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(liveUser.publicContactEmail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            liveUser.socialLinks.filterValues { it.isNotBlank() }.forEach { (network, url) ->
                                TextButton(onClick = {
                                    val safeUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))) }
                                }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(network.replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "profile_qr") {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f))) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.QrCode2, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Profile QR", fontWeight = FontWeight.ExtraBold)
                                Text("Scan or share this profile", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isMe) {
                            profileQr?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), "${liveUser.name} profile QR code", Modifier.size(190.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp))
                            } ?: Text("QR preview unavailable", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("QR codes are available only for your own profile.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            item(key = "metrics") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f))
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        PremiumMetric(liveUser.friends.size.toString(), "Friends", Modifier.weight(1f))
                        VerticalDivider(Modifier.height(34.dp))
                        PremiumMetric(liveUser.followers.size.toString(), "Followers", Modifier.weight(1f))
                        VerticalDivider(Modifier.height(34.dp))
                        PremiumMetric(userPosts.size.toString(), "Posts", Modifier.weight(1f))
                        VerticalDivider(Modifier.height(34.dp))
                        PremiumMetric(totalLikes.toString(), "Likes", Modifier.weight(1f))
                    }
                }
            }

            if (!isMe) item(key = "actions") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = { viewModel.toggleFollow(liveUser) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                            Icon(if (isFollowing) Icons.Outlined.PersonRemove else Icons.Outlined.PersonAdd, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(if (isFollowing) "Following" else "Follow")
                        }
                        if (isFriend) {
                            OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null); Spacer(Modifier.width(6.dp)); Text("Message")
                            }
                        } else if (isRequested) {
                            OutlinedButton(
                                onClick = { viewModel.cancelFriendRequest(liveUser.uid) { Toast.makeText(context, if (it) "Request cancelled" else "Could not cancel", Toast.LENGTH_SHORT).show() } },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)
                            ) { Text("Requested") }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.sendFriendRequest(liveUser) { _, message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)
                            ) { Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(5.dp)); Text("Add friend") }
                        }
                    }
                    if (!isFriend) {
                        OutlinedButton(onClick = onMessage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Outlined.MarkUnreadChatAlt, null); Spacer(Modifier.width(7.dp)); Text("Send message request")
                        }
                    }
                    if (liveUser.role == "moderator") {
                        TextButton(onClick = { showFeatureSuggestion = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Lightbulb, null); Spacer(Modifier.width(7.dp)); Text("Suggest an app update or feature")
                        }
                    }
                }
            }

            item(key = "about") {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        ProfileInfoRow(Icons.Outlined.Cake, if (liveUser.dob.isBlank()) "Birthday not shared" else "Born ${liveUser.dob}")
                        ProfileInfoRow(Icons.Outlined.CalendarMonth, "Joined ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(liveUser.createdAt))}")
                        ProfileInfoRow(Icons.Outlined.People, "${liveUser.followers.size} followers • ${liveUser.following.size} following")
                    }
                }
            }

            item(key = "post_title") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Posts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) { Text(userPosts.size.toString(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontWeight = FontWeight.Bold) }
                }
            }
            if (userPosts.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.fillMaxWidth().height(180.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Outlined.Article, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .55f))
                            Spacer(Modifier.height(8.dp)); Text("No posts yet", fontWeight = FontWeight.Bold)
                            Text("Shared moments will appear here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(userPosts, key = { it.id }) { post ->
                    Box(Modifier.padding(horizontal = 14.dp)) { SocialPostItem(post, viewModel, onProfileSelected = {}, autoPlayVideo = false, allowInlineVideo = true) }
                }
            }
        }
    }

    if (showFeatureSuggestion) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeatureSuggestion = false },
            title = { Text("Suggest a Convo Chat feature", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it.take(100) }, label = { Text("Request title") }, singleLine = true)
                    OutlinedTextField(description, { description = it.take(1500) }, label = { Text("Describe the update or feature") }, minLines = 5)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitFeatureRequest(title, description) { ok -> Toast.makeText(context, if (ok) "Request sent to the moderator" else "Could not send request", Toast.LENGTH_LONG).show(); if (ok) showFeatureSuggestion = false } },
                    enabled = title.isNotBlank() && description.isNotBlank()
                ) { Text("Send request") }
            },
            dismissButton = { TextButton(onClick = { showFeatureSuggestion = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PremiumMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(18.dp)) }
        Spacer(Modifier.width(11.dp)); Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}
