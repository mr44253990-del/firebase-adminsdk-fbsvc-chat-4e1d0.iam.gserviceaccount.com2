package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.TestTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onUserSelected: (User) -> Unit,
    onSignOut: () -> Unit,
    onNavigateToStories: () -> Unit = {},
    onNavigateToPosts: () -> Unit = {},
    onNavigateToGroups: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.filteredUsersState.collectAsState()
    val stories by viewModel.storiesState.collectAsState()
    val posts by viewModel.postsState.collectAsState()
    val notifications by viewModel.notificationsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showProfileConfig by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }

    val unreadCount = notifications.count { !it.isRead }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification Permission Granted!", Toast.LENGTH_SHORT).show()
        }
    }

    // Check notification permission
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(DarkBackground, DarkSurface, DarkBackground)
    )

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FireChat Pro",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            brush = Brush.linearGradient(GradientPurpleCyan)
                        )
                        Text(
                            text = "Welcome, ${currentUser?.name ?: "User"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    // Notifications badge
                    IconButton(onClick = { /* Show notifications */ }) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White
                            )
                            if (unreadCount > 0) {
                                Badge(
                                    modifier = Modifier.offset(x = 8.dp, y = (-4).dp),
                                    containerColor = CoralRed
                                ) {
                                    Text(
                                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    IconButton(onClick = { showCreateStoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add Story",
                            tint = PinkNeon
                        )
                    }
                    
                    IconButton(onClick = { showProfileConfig = !showProfileConfig }) {
                        Icon(
                            imageVector = if (showProfileConfig) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreatePostDialog = true },
                containerColor = ElectricPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Post")
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface.copy(alpha = 0.95f),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Article, contentDescription = null) },
                    label = { Text("Posts") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    label = { Text("Groups") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.StickyNote2, contentDescription = null) },
                    label = { Text("Notes") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = {
                        selectedTab = 4
                        onNavigateToProfile()
                    },
                    icon = { 
                        Box {
                            Icon(Icons.Default.Person, contentDescription = null)
                            if (currentUser?.isOnline == true) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .border(2.dp, DarkSurface, CircleShape)
                                )
                            }
                        }
                    },
                    label = { Text("Profile") }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
                    0 -> HomeTab(viewModel, users, stories, posts, currentUser, onUserSelected)
                    1 -> PostsTab(viewModel, posts, currentUser)
                    2 -> GroupsTab(viewModel)
                    3 -> NotesTab(viewModel)
                    4 -> ProfileTab(viewModel, currentUser, onNavigateToSettings, onSignOut)
                }
            }

            // Profile config overlay
            AnimatedVisibility(
                visible = showProfileConfig,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                ProfileConfigCard(
                    currentUser = currentUser,
                    webhookUrl = viewModel.webhookUrl.collectAsState().value,
                    onUpdateWebhook = { viewModel.updateWebhookUrl(it) },
                    onLogout = {
                        viewModel.logout(onSignOut)
                    }
                )
            }
        }
    }

    // Create Post Dialog
    if (showCreatePostDialog) {
        CreatePostDialog(
            onDismiss = { showCreatePostDialog = false },
            onPost = { content, privacy ->
                viewModel.createPost(content, privacy = privacy)
                showCreatePostDialog = false
            }
        )
    }

    // Create Story Dialog
    if (showCreateStoryDialog) {
        CreateStoryDialog(
            onDismiss = { showCreateStoryDialog = false },
            onStory = { uri, caption ->
                viewModel.createStory(uri, caption)
                showCreateStoryDialog = false
            }
        )
    }
}

@Composable
private fun HomeTab(
    viewModel: ChatViewModel,
    users: List<User>,
    stories: List<Story>,
    posts: List<Post>,
    currentUser: User?,
    onUserSelected: (User) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Stories Section
        StoriesSection(stories = stories, currentUserId = currentUser?.uid ?: "")
        
        // Search Bar
        SearchBar(
            query = "",
            onQueryChange = { viewModel.searchUsers(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Posts Feed
        if (posts.isEmpty()) {
            EmptyStateMessage(
                message = "No posts yet",
                subMessage = "Be the first to share something!",
                icon = Icons.Default.Article
            )
        } else {
            posts.take(5).forEach { post ->
                PostCard(
                    post = post,
                    currentUserId = currentUser?.uid ?: "",
                    onReact = { reaction -> viewModel.reactToPost(post.postId, reaction) },
                    onComment = { /* Open comments */ },
                    onShare = { /* Share post */ },
                    onDelete = { viewModel.deletePost(post.postId) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Users Section
        Text(
            text = "People you can chat with",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        users.take(10).forEach { user ->
            UserListItem(
                user = user,
                onClick = { onUserSelected(user) }
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun StoriesSection(stories: List<Story>, currentUserId: String) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = "Stories",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add Story button
            item {
                AddStoryItem()
            }

            // Stories
            items(stories) { story ->
                StoryRingItem(story = story)
            }
        }
    }
}

@Composable
private fun AddStoryItem() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(DarkSurface)
                .border(2.dp, ElectricPurple, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Story",
                tint = ElectricPurple,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your Story",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@Composable
private fun StoryRingItem(story: Story) {
    val isViewed = story.views.contains(story.userId)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (isViewed) listOf(Color.Gray, Color.Gray) 
                        else GradientPurpleCyan
                    )
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (story.userProfileUrl.isNotBlank()) {
                AsyncImage(
                    model = story.userProfileUrl,
                    contentDescription = "Story",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = story.userName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = story.userName.split(" ").first(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf(query) }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    onQueryChange(it)
                },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(ElectricPurple),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchText.isEmpty()) {
                            Text(
                                text = "Search users...",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (searchText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        searchText = ""
                        onQueryChange("")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    currentUserId: String,
    onReact: (String) -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReactions by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(GradientPurpleCyan)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (post.userProfileUrl.isNotBlank()) {
                        AsyncImage(
                            model = post.userProfileUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = post.userName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.userName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${post.userUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricPurple
                        )
                        Text(
                            text = " • ",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = getTimeAgo(post.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // Privacy icon
                Text(
                    text = PrivacyOptions.icons[post.privacy] ?: "🌍",
                    fontSize = 16.sp
                )
                
                if (post.userId == currentUserId) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = CoralRed) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = CoralRed) }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            
            // Images
            if (post.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.imageUrls.first(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reactions and stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reaction count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val reactionEmojis = post.reactions.values.distinct().take(3)
                    reactionEmojis.forEach { emoji ->
                        Text(text = emoji, fontSize = 16.sp)
                    }
                    if (post.reactions.isNotEmpty()) {
                        Text(
                            text = " ${post.reactions.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Comments and views
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${post.commentCount} comments",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = " • ",
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "${post.views.size} views",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ReactionButton(
                    icon = Icons.Default.ThumbUp,
                    label = "Like",
                    isSelected = post.reactions[currentUserId] != null,
                    onClick = {
                        if (post.reactions[currentUserId] != null) {
                            // Already liked, show reaction picker
                            showReactions = !showReactions
                        } else {
                            onReact(ReactionTypes.LIKE)
                        }
                    }
                )
                CommentButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    label = "Comment",
                    onClick = onComment
                )
                ShareButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = onShare
                )
            }
            
            // Reaction picker
            AnimatedVisibility(
                visible = showReactions,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ReactionTypes.all.forEach { reaction ->
                        Text(
                            text = ReactionTypes.emojiMap[reaction],
                            fontSize = 28.sp,
                            modifier = Modifier.clickable {
                                onReact(reaction)
                                showReactions = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) ElectricPurple else Color.White.copy(alpha = 0.6f)
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) ElectricPurple.copy(alpha = 0.2f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CommentButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ShareButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun UserListItem(
    user: User,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(GradientPurpleCyan)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.profileImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = user.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = user.name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                
                // Online status
                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .border(2.dp, DarkSurface, CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricPurple
                )
                if (user.isOnline) {
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnlineGreen
                    )
                } else if (user.lastSeen > 0) {
                    Text(
                        text = "Last seen ${getTimeAgo(user.lastSeen)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Chat",
                tint = ElectricPurple
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(
    message: String,
    subMessage: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = subMessage,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun GlassTopAppBar(
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface.copy(alpha = 0.9f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                title()
            }
            actions()
        }
    }
}

@Composable
private fun ProfileConfigCard(
    currentUser: User?,
    webhookUrl: String,
    onUpdateWebhook: (String) -> Unit,
    onLogout: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface.copy(alpha = 0.95f),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Your Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            currentUser?.let { user ->
                InfoRow("Name", user.name)
                InfoRow("Username", "@${user.username}")
                InfoRow("Email", user.email)
                InfoRow("DOB", user.dob)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = onUpdateWebhook,
                label = { Text("Webhook URL") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricPurple,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

// Placeholder tabs
@Composable
private fun PostsTab(viewModel: ChatViewModel, posts: List<Post>, currentUser: User?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "All Posts",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        
        if (posts.isEmpty()) {
            item {
                EmptyStateMessage(
                    message = "No posts yet",
                    subMessage = "Create your first post!",
                    icon = Icons.Default.Article
                )
            }
        } else {
            items(posts) { post ->
                PostCard(
                    post = post,
                    currentUserId = currentUser?.uid ?: "",
                    onReact = { viewModel.reactToPost(post.postId, it) },
                    onComment = { },
                    onShare = { },
                    onDelete = { viewModel.deletePost(post.postId) }
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun GroupsTab(viewModel: ChatViewModel) {
    val groups by viewModel.groupsState.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Groups",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            IconButton(onClick = { showCreateGroupDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Group", tint = ElectricPurple)
            }
        }
        
        if (groups.isEmpty()) {
            EmptyStateMessage(
                message = "No groups yet",
                subMessage = "Create or join a group!",
                icon = Icons.Default.Group
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups) { group ->
                    GroupItem(group = group)
                }
            }
        }
    }
}

@Composable
private fun GroupItem(group: Group) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(GradientPurpleCyan)),
                contentAlignment = Alignment.Center
            ) {
                if (group.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = group.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${group.memberIds.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                if (group.lastMessage.isNotBlank()) {
                    Text(
                        text = group.lastMessage.take(30),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesTab(viewModel: ChatViewModel) {
    val notes by viewModel.notesState.collectAsState()
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Notes",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            IconButton(onClick = { showCreateNoteDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Note", tint = MintGreen)
            }
        }
        
        if (notes.isEmpty()) {
            EmptyStateMessage(
                message = "No notes yet",
                subMessage = "Create a note to remember!",
                icon = Icons.Default.StickyNote2
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes) { note ->
                    NoteItem(note = note)
                }
            }
        }
    }
}

@Composable
private fun NoteItem(note: Note) {
    val noteColor = try {
        Color(android.graphics.Color.parseColor(note.color))
    } catch (e: Exception) {
        ElectricPurple
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = noteColor.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, noteColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = GoldenYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note.content,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = getTimeAgo(note.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ProfileTab(
    viewModel: ChatViewModel,
    currentUser: User?,
    onNavigateToSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(GradientPurpleCyan)),
            contentAlignment = Alignment.Center
        ) {
            if (currentUser?.profileImageUrl?.isNotBlank() == true) {
                AsyncImage(
                    model = currentUser.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = currentUser?.name?.take(1)?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = currentUser?.name ?: "User",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        
        Text(
            text = "@${currentUser?.username ?: "username"}",
            style = MaterialTheme.typography.bodyLarge,
            color = ElectricPurple
        )
        
        if (currentUser?.bio?.isNotBlank() == true) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentUser.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (currentUser?.isOnline == true) OnlineGreen else OfflineGray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (currentUser?.isOnline == true) "Online" else "Offline",
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Menu items
        ProfileMenuItem(
            icon = Icons.Default.Edit,
            label = "Edit Profile",
            onClick = { /* Navigate to edit */ }
        )
        ProfileMenuItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            onClick = onNavigateToSettings
        )
        ProfileMenuItem(
            icon = Icons.Default.Info,
            label = "About",
            onClick = { /* Show about */ }
        )
        ProfileMenuItem(
            icon = Icons.Default.ExitToApp,
            label = "Sign Out",
            onClick = onSignOut,
            tint = CoralRed
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                color = tint,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = tint.copy(alpha = 0.5f)
            )
        }
    }
}

// Dialogs
@Composable
private fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPost: (String, String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PrivacyOptions.PUBLIC) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Post", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What's on your mind?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Privacy", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrivacyOptions.all.forEach { option ->
                        FilterChip(
                            selected = privacy == option,
                            onClick = { privacy = option },
                            label = { Text("${PrivacyOptions.icons[option]} ${PrivacyOptions.displayNames[option]}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(content, privacy) },
                enabled = content.isNotBlank()
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onStory: (String, String) -> Unit
) {
    var imageUri by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri?.toString()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Story", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to select image",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { imageUri?.let { onStory(it, caption) } },
                enabled = imageUri != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper function
private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> "${diff / 604_800_000}w"
    }
}
