package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.theme.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import com.example.R
import com.example.data.ActivityNotification
import com.example.data.FriendRequest
import com.example.data.Group
import com.example.data.MessageRequest
import com.example.data.Post
import com.example.data.Story
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onUserSelected: (User) -> Unit,
    onProfileSelected: (User) -> Unit,
    onGroupSelected: (Group) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val isOnlineState by viewModel.isNetworkAvailable.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.filteredUsersState.collectAsState()
    val groups by viewModel.groupsState.collectAsState()
    val stories by viewModel.storiesState.collectAsState()
    val posts by viewModel.postsState.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val inAppNotification by viewModel.inAppNotification.collectAsState()
    val activityNotifications by viewModel.activityNotifications.collectAsState()
    val openActivitySignal by viewModel.openActivityCenterSignal.collectAsState()
    val friendRequests by viewModel.friendRequests.collectAsState()
    val sentFriendRequests by viewModel.sentFriendRequestIds.collectAsState()
    val messageRequests by viewModel.messageRequests.collectAsState()
    val currentTheme by viewModel.themeState.collectAsState()
    val notificationSounds by viewModel.notificationSoundsEnabled.collectAsState()
    val typingSounds by viewModel.typingSoundsEnabled.collectAsState()
    val allUsers by viewModel.usersState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showActivityCenter by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    val currentTab by viewModel.currentTabState.collectAsState()

    // Dialog & Creation controllers
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedStoryIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(openActivitySignal) {
        if (openActivitySignal > 0L) {
            showActivityCenter = true
            viewModel.markAllActivityRead()
        }
    }

    // Run Android 13+ Notification Permission Checks
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val scaffoldBgColor = Color.Transparent

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // WiFi Offline Mode indicator banner
                if (!isOnlineState) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE53935))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = "WiFi No Signal", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WiFi No Signal | Offline Mode Active (Local Room Database Caching is operational)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // App branding top bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "FireChat",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = if (isOnlineState) "Online" else "Offline Cache Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showGlobalSearch = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search people and media")
                        }
                        BadgedBox(
                            badge = {
                                val unread = activityNotifications.count { !it.isRead } + friendRequests.size + messageRequests.size
                                if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
                            }
                        ) {
                            IconButton(onClick = {
                                showActivityCenter = true
                                viewModel.dismissInAppNotification()
                                viewModel.markAllActivityRead()
                            }) {
                                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                            }
                        }
                        Box {
                            IconButton(onClick = { showAccountMenu = true }) {
                                AsyncImage(
                                    model = currentUser?.profileImageUrl?.ifBlank { null },
                                    contentDescription = "Account",
                                    error = painterResource(R.drawable.img_app_logo),
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f), CircleShape)
                                )
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Profile & appearance") },
                                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                                    onClick = {
                                        showAccountMenu = false
                                        viewModel.setCurrentTab(4)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showAccountMenu = false
                                        viewModel.logout { onSignOut() }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setCurrentTab(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.setCurrentTab(1) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setCurrentTab(2) },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Groups") },
                    label = { Text("Groups", fontSize = 10.sp) }
                )
                val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
                val isAdmin = email.lowercase().trim() == "mr4425390@gmail.com"

                if (isAdmin) {
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { viewModel.setCurrentTab(3) },
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Service") },
                        label = { Text("Service", fontSize = 10.sp) }
                    )
                }
                NavigationBarItem(
                    selected = currentTab == 5,
                    onClick = { viewModel.setCurrentTab(5) },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = "People") },
                    label = { Text("People", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { viewModel.setCurrentTab(4) },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp) }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scaffoldBgColor)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> {
                    // One continuous list: stories/composer/filters naturally collapse while scrolling
                    // and return when the user reaches the top, leaving maximum room for media.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "stories") {
                            StoriesHorizontalSection(
                                viewModel = viewModel,
                                stories = stories,
                                onStorySelected = { story ->
                                    viewModel.recordStoryView(story)
                                    selectedStoryIndex = stories.indexOfFirst { it.id == story.id }.takeIf { it >= 0 }
                                }
                            )
                        }
                        item(key = "composer") {
                            Card(
                                onClick = { showCreatePostDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .70f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = currentUser?.profileImageUrl?.ifBlank { null },
                                        contentDescription = "Your profile",
                                        error = painterResource(id = R.drawable.img_app_logo),
                                        modifier = Modifier.size(44.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("What's on your mind?", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                                    Icon(Icons.Outlined.Image, "Add photo", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Icon(Icons.Outlined.Videocam, "Add video", tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(10.dp))
                                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                                        Text("Post", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item(key = "filters") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(listOf("All Posts", "Friends", "Groups", "Following")) { label ->
                                    FilterChip(
                                        selected = label == "All Posts",
                                        onClick = { },
                                        label = { Text(label) },
                                        leadingIcon = if (label == "All Posts") { { Icon(Icons.Outlined.DynamicFeed, null, Modifier.size(17.dp)) } } else null,
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                        if (posts.isEmpty()) {
                            item(key = "empty") {
                                Column(
                                    modifier = Modifier.fillParentMaxHeight(.62f).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Outlined.DynamicFeed, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = .45f), modifier = Modifier.size(60.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No posts yet", fontWeight = FontWeight.Bold)
                                    Text("Share the first update with your community.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(posts, key = { it.id }) { post ->
                                Box(Modifier.padding(horizontal = 14.dp)) {
                                    SocialPostItem(post = post, viewModel = viewModel, onProfileSelected = onProfileSelected)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // CHATS TAB (Direct private chats list with bold highlights, unread badges, and last messages instead of username)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Search contacts bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchUsers(it)
                            },
                            placeholder = { Text("Search users by name...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Messages",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (users.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(users) { user ->
                                    val hasActiveStory = stories.any { it.senderId == user.uid }
                                    ChatConversationUserItem(
                                        user = user,
                                        viewModel = viewModel,
                                        hasActiveStory = hasActiveStory,
                                        onSelect = { onUserSelected(user) }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // GROUPS TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your groups",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { showCreateGroupDialog = true },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "(Create Group)", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Group", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (groups.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("You haven't joined any groups yet", fontWeight = FontWeight.SemiBold)
                                    Text("Create a new group above to invite friends!", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(groups) { group ->
                                    GroupItemCard(group = group, onClick = { onGroupSelected(group) })
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // SERVICE TAB (Strictly admin access restricted to mr4425390@gmail.com)
                    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
                    val isAdmin = email.lowercase().trim() == "mr4425390@gmail.com"

                    if (!isAdmin) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(60.dp))
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Admin access only",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "The service configuration panel is restricted to the admin mr4425390@gmail.com. Non-authorized access is disabled.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        // Admin Dashboard
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Admin control center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            var adminUrlInput by remember { mutableStateOf(webhookUrl) }

                            Card(
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "Direct FCM Gateway Configuration",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Messages, friend requests, tags and activity alerts are sent directly through your secure FCM v1 Worker. No n8n webhook is used. Keep the service-account key only in the Worker secret.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = adminUrlInput,
                                        onValueChange = { adminUrlInput = it },
                                        placeholder = { Text("https://firechat-fcm.your-subdomain.workers.dev") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(18.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            viewModel.updateWebhookUrl(adminUrlInput)
                                            Toast.makeText(context, "Saved direct FCM gateway URL!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save FCM Gateway", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Admin Session Authenticated", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                        Text("Logged in as: mr4425390@gmail.com", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    // SETTINGS & PROFILE TAB: Theme selector + User details modification
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Profile & appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        // 1. Theme selection block
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Choose Application Theme",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Personalize your layout with custom dynamic palettes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                val themes = listOf(
                                    "Dynamic" to Color(0xFF7C5CFC),
                                    "Light" to Color(0xFFF0F2FA),
                                    "Dark" to Color(0xFF171A24),
                                    "AMOLED" to Color.Black,
                                    "Chocolate" to Color(0xFF6D4C41),
                                    "Ocean" to Color(0xFF0288D1),
                                    "Forest" to Color(0xFF2E7D32),
                                    "Midnight" to Color(0xFF4A148C)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    themes.forEach { (themeName, themeColor) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (currentTheme == themeName) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                                .clickable {
                                                    viewModel.updateTheme(themeName)
                                                    Toast.makeText(context, "$themeName theme applied!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(themeColor)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = themeName,
                                                    fontWeight = if (currentTheme == themeName) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            if (currentTheme == themeName) {
                                                Icon(Icons.Default.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Profile Details Modification
                        currentUser?.let { user ->
                            var inputName by remember { mutableStateOf(user.name) }
                            var inputDob by remember { mutableStateOf(user.dob) }
                            var inputBio by remember { mutableStateOf(user.bio) }
                            var inputCoverUrl by remember { mutableStateOf(user.coverImageUrl) }
                            var inputProfileUrl by remember { mutableStateOf(user.profileImageUrl) }
                            var isUploadingAvatar by remember { mutableStateOf(false) }

                            val calendar = Calendar.getInstance()
                            val datePickerDialog = DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    inputDob = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            )

                            val galleryLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent()
                            ) { uri: Uri? ->
                                if (uri != null) {
                                    isUploadingAvatar = true
                                    try {
                                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                                        val bytes = inputStream?.readBytes()
                                        if (bytes != null) {
                                            viewModel.uploadFileToSupabase(
                                                bucket = "avatars",
                                                fileName = "avatar_${user.uid}_${System.currentTimeMillis()}.jpg",
                                                fileBytes = bytes,
                                                contentType = "image/jpeg",
                                                onSuccess = { publicUrl ->
                                                    isUploadingAvatar = false
                                                    inputProfileUrl = publicUrl
                                                    Toast.makeText(context, "Uploaded profile picture!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { err ->
                                                    isUploadingAvatar = false
                                                    Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        isUploadingAvatar = false
                                    }
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Modify User Details",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clickable { galleryLauncher.launch("image/*") }
                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (inputProfileUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = inputProfileUrl,
                                                contentDescription = "Profile picture",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(86.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(86.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Person, contentDescription = null)
                                            }
                                        }

                                        if (isUploadingAvatar) {
                                            Box(
                                                modifier = Modifier
                                                    .size(86.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = inputName,
                                        onValueChange = { inputName = it },
                                        label = { Text("Your Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = inputDob,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Birth Date (YYYY-MM-DD)") },
                                        trailingIcon = {
                                            IconButton(onClick = { datePickerDialog.show() }) {
                                                Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = inputBio,
                                        onValueChange = { inputBio = it },
                                        label = { Text("Bio") },
                                        placeholder = { Text("Tell people about yourself") },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = inputCoverUrl,
                                        onValueChange = { inputCoverUrl = it },
                                        label = { Text("Cover image URL") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            if (inputName.isBlank()) {
                                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            viewModel.updateUserProfile(
                                                name = inputName,
                                                dob = inputDob,
                                                profileImageUrl = inputProfileUrl,
                                                bio = inputBio,
                                                coverImageUrl = inputCoverUrl,
                                                onSuccess = {
                                                    Toast.makeText(context, "Profile details updated!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, "Update failed: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save Changes", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Sounds & activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Control notification and live typing feedback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Notification sounds", Modifier.padding(start = 12.dp).weight(1f))
                                    Switch(
                                        checked = notificationSounds,
                                        onCheckedChange = { viewModel.updateSoundPreferences(it, typingSounds) }
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Typing indicator sound", Modifier.padding(start = 12.dp).weight(1f))
                                    Switch(
                                        checked = typingSounds,
                                        onCheckedChange = { viewModel.updateSoundPreferences(notificationSounds, it) }
                                    )
                                }
                            }
                        }

                        // 3. Blocked Users Management
                        val allUsers by viewModel.usersState.collectAsState()
                        currentUser?.let { user ->
                            val blockedUsersList = allUsers.filter { user.blockedUsers.contains(it.uid) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Blocked Contacts (${blockedUsersList.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Manage your blocked contacts. Tap unblock to resume chatting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (blockedUsersList.isEmpty()) {
                                        Text(
                                            text = "No blocked contacts.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            blockedUsersList.forEach { blockedUser ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        AsyncImage(
                                                            model = blockedUser.profileImageUrl.ifBlank { null },
                                                            contentDescription = null,
                                                            error = painterResource(id = R.drawable.img_app_logo),
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(blockedUser.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            viewModel.unblockUser(blockedUser.uid) {
                                                                Toast.makeText(context, "${blockedUser.name} Unblocked", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    ) {
                                                        Text("Unblock", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                5 -> {
                    var peopleQuery by remember { mutableStateOf("") }
                    val visiblePeople = allUsers.filter {
                        peopleQuery.isBlank() || it.name.contains(peopleQuery, true) || it.username.contains(peopleQuery, true)
                    }.sortedByDescending { it.createdAt }
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Text("Discover people", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Find new accounts and build your circle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = peopleQuery,
                            onValueChange = { peopleQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("Search people") },
                            singleLine = true,
                            shape = CircleShape
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(visiblePeople, key = { it.uid }) { person ->
                                val isFriend = currentUser?.friends?.contains(person.uid) == true
                                val isRequested = sentFriendRequests.contains("${currentUser?.uid}_${person.uid}")
                                Card(shape = RoundedCornerShape(24.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = person.profileImageUrl.ifBlank { null },
                                            contentDescription = person.name,
                                            error = painterResource(R.drawable.img_app_logo),
                                            modifier = Modifier.size(54.dp).clip(CircleShape).clickable { onProfileSelected(person) }
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(person.name, fontWeight = FontWeight.Bold)
                                            Text("@${person.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                            Text("${person.friends.size} friends", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        }
                                        when {
                                            isFriend -> AssistChip(onClick = { onUserSelected(person) }, label = { Text("Message") })
                                            isRequested -> OutlinedButton(onClick = {
                                                viewModel.cancelFriendRequest(person.uid) { ok ->
                                                    Toast.makeText(context, if (ok) "Request cancelled" else "Could not cancel", Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text("Cancel") }
                                            else -> Button(onClick = {
                                                viewModel.sendFriendRequest(person) { _, message ->
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text("Add") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // In-app floating overlay notifications alert
            AnimatedVisibility(
                visible = inAppNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                inAppNotification?.let { notif ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = users.find { it.uid == notif.senderId }
                                if (target != null) {
                                    onUserSelected(target)
                                }
                                viewModel.dismissInAppNotification()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.senderName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(notif.text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            IconButton(onClick = { viewModel.dismissInAppNotification() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGlobalSearch) {
        SmartSearchDialog(
            users = allUsers,
            posts = posts,
            onProfileSelected = {
                showGlobalSearch = false
                onProfileSelected(it)
            },
            onDismiss = { showGlobalSearch = false }
        )
    }

    if (showActivityCenter) {
        ActivityCenterDialog(
            notifications = activityNotifications,
            requests = friendRequests,
            messageRequests = messageRequests,
            onDismiss = { showActivityCenter = false },
            onAccept = { viewModel.respondToFriendRequest(it, true) },
            onReject = { viewModel.respondToFriendRequest(it, false) },
            onAcceptMessage = { viewModel.respondToMessageRequest(it, true) },
            onRejectMessage = { viewModel.respondToMessageRequest(it, false) }
        )
    }

    // CREATE POST DIALOG
    if (showCreatePostDialog) {
        var postTitle by remember { mutableStateOf("") }
        var postText by remember { mutableStateOf("") }
        var postTags by remember { mutableStateOf("") }
        var taggedPeople by remember { mutableStateOf("") }
        val selectedTaggedIds = remember { mutableStateListOf<String>() }
        var postFeeling by remember { mutableStateOf("") }
        var postImageUrl by remember { mutableStateOf("") }
        var postVideoUrl by remember { mutableStateOf("") }
        var isPrivateToggle by remember { mutableStateOf(false) }
        var isUploadingMedia by remember { mutableStateOf(false) }

        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isUploadingMedia = true
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        viewModel.uploadFileToSupabase(
                            bucket = "chat_images",
                            fileName = "post_${System.currentTimeMillis()}.jpg",
                            fileBytes = bytes,
                            contentType = "image/jpeg",
                            onSuccess = { publicUrl ->
                                isUploadingMedia = false
                                postImageUrl = publicUrl
                                Toast.makeText(context, "Post image uploaded!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                isUploadingMedia = false
                                Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    isUploadingMedia = false
                }
            }
        }

        val videoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isUploadingMedia = true
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        viewModel.uploadFileToSupabase(
                            bucket = "chat_images",
                            fileName = "post_${System.currentTimeMillis()}.mp4",
                            fileBytes = bytes,
                            contentType = "video/mp4",
                            onSuccess = { publicUrl ->
                                isUploadingMedia = false
                                postVideoUrl = publicUrl
                                Toast.makeText(context, "Post video uploaded!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                isUploadingMedia = false
                                Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    isUploadingMedia = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreatePostDialog = false },
            title = { Text("Create a post", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = postTitle,
                        onValueChange = { postTitle = it },
                        label = { Text("Title") },
                        placeholder = { Text("Give your post a title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        placeholder = { Text("What's on your mind?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )
                    Text("Feeling / activity", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(listOf("😊 Happy", "🥰 Loved", "🎉 Excited", "😎 Cool", "😢 Sad", "✈️ Traveling", "🍽️ Eating", "🎮 Gaming")) { feeling ->
                            FilterChip(
                                selected = postFeeling == feeling,
                                onClick = { postFeeling = if (postFeeling == feeling) "" else feeling },
                                label = { Text(feeling) },
                                shape = CircleShape
                            )
                        }
                    }
                    OutlinedTextField(
                        value = postTags,
                        onValueChange = { postTags = it },
                        label = { Text("Hashtags") },
                        placeholder = { Text("travel, friends, firechat") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = taggedPeople,
                        onValueChange = { taggedPeople = it },
                        label = { Text("Tag people") },
                        placeholder = { Text("Type @name or username") },
                        leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (selectedTaggedIds.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(selectedTaggedIds.toList()) { uid ->
                                allUsers.find { it.uid == uid }?.let { person ->
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedTaggedIds.remove(uid) },
                                        label = { Text(person.name) },
                                        avatar = {
                                            AsyncImage(person.profileImageUrl.ifBlank { null }, person.name, error = painterResource(R.drawable.img_app_logo), modifier = Modifier.size(24.dp).clip(CircleShape))
                                        },
                                        trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                    val tagQuery = taggedPeople.trim().removePrefix("@").lowercase()
                    val tagMatches = if (tagQuery.length < 2) emptyList() else allUsers.filter {
                        it.uid !in selectedTaggedIds && (it.name.contains(tagQuery, true) || it.username.contains(tagQuery, true))
                    }.take(5)
                    tagMatches.forEach { person ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                selectedTaggedIds.add(person.uid)
                                taggedPeople = ""
                            }.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(person.profileImageUrl.ifBlank { null }, person.name, error = painterResource(R.drawable.img_app_logo), modifier = Modifier.size(38.dp).clip(CircleShape))
                            Spacer(Modifier.width(9.dp))
                            Column { Text(person.name, fontWeight = FontWeight.Bold); Text("@${person.username}", fontSize = 11.sp) }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach image")
                        }

                        IconButton(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Attach video")
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (postImageUrl.isNotBlank()) {
                                Text("Image Attached ✅", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (postVideoUrl.isNotBlank()) {
                                Text("Video Attached ✅", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isPrivateToggle, onCheckedChange = { isPrivateToggle = it })
                            Text("Private", fontSize = 12.sp)
                        }
                    }

                    if (isUploadingMedia) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (postTitle.isNotBlank() || postText.isNotBlank() || postImageUrl.isNotBlank() || postVideoUrl.isNotBlank()) {
                            val tags = postTags.split(",", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct()
                            val taggedIds = selectedTaggedIds.toList()
                            viewModel.createPost(
                                text = postText,
                                imageUrl = postImageUrl,
                                audioUrl = "",
                                videoUrl = postVideoUrl,
                                isPrivate = isPrivateToggle,
                                onComplete = {},
                                title = postTitle,
                                tags = tags,
                                taggedUserIds = taggedIds,
                                feeling = postFeeling
                            )
                            showCreatePostDialog = false
                        }
                    },
                    enabled = !isUploadingMedia
                ) {
                    Text("Post", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePostDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // CREATE GROUP DIALOG (Renders users multi selection nicely)
    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var groupPicUrl by remember { mutableStateOf("") }
        val selectedMembers = remember { mutableStateListOf<String>() }
        var isUploadingGroupPic by remember { mutableStateOf(false) }

        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isUploadingGroupPic = true
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        viewModel.uploadFileToSupabase(
                            bucket = "avatars",
                            fileName = "group_${System.currentTimeMillis()}.jpg",
                            fileBytes = bytes,
                            contentType = "image/jpeg",
                            onSuccess = { publicUrl ->
                                isUploadingGroupPic = false
                                groupPicUrl = publicUrl
                                Toast.makeText(context, "Group profile photo selected!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                isUploadingGroupPic = false
                                Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    isUploadingGroupPic = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create a group", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Group Icon")
                        }

                        if (groupPicUrl.isNotBlank()) {
                            Text("Icon selected!", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isUploadingGroupPic) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }

                    Text("Select Members:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(users) { user ->
                            val isSelected = selectedMembers.contains(user.uid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        if (isSelected) {
                                            selectedMembers.remove(user.uid)
                                        } else {
                                            selectedMembers.add(user.uid)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(user.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.createGroup(groupName, groupPicUrl, selectedMembers.toList()) { newGroup ->
                                onGroupSelected(newGroup)
                            }
                            showCreateGroupDialog = false
                        }
                    },
                    enabled = !isUploadingGroupPic
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedStoryIndex?.let { index ->
        FullScreenStoryViewer(
            stories = stories,
            storyIndex = index,
            onPrevious = { selectedStoryIndex = (index - 1).coerceAtLeast(0) },
            onNext = {
                val next = (index + 1).coerceAtMost(stories.size)
                stories.getOrNull(next)?.let(viewModel::recordStoryView)
                selectedStoryIndex = next
            },
            onDismiss = { selectedStoryIndex = null },
            onReact = { story, emoji -> viewModel.reactToStory(story.id, emoji) },
            onComment = { story, text -> viewModel.commentOnStory(story.id, text) },
            onDelete = { story ->
                viewModel.deleteStory(story.id)
                selectedStoryIndex = null
            }
        )
    }

}

@Composable
fun SmartSearchDialog(
    users: List<User>,
    posts: List<Post>,
    onProfileSelected: (User) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var fullScreenVideo by remember { mutableStateOf<String?>(null) }
    val normalized = query.trim()
    val matchedUsers = if (normalized.isBlank()) emptyList() else users.filter {
        it.name.contains(normalized, true) || it.username.contains(normalized, true) || it.bio.contains(normalized, true)
    }
    val matchedPosts = if (normalized.isBlank()) emptyList() else posts.filter { post ->
        post.title.contains(normalized, true) || post.text.contains(normalized, true) ||
            post.feeling.contains(normalized, true) || post.tags.any { it.contains(normalized.removePrefix("#"), true) }
    }
    fullScreenVideo?.let { FullScreenVideoPlayer(it) { fullScreenVideo = null } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search people, posts, videos and photos") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                        },
                        singleLine = true,
                        shape = CircleShape
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close search") }
                }
                if (normalized.isBlank()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.ManageSearch, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp))
                        Text("Search everything", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("People • Posts • Videos • Photos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (matchedUsers.isNotEmpty()) {
                            item { Text("People", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(matchedUsers, key = { "search_user_${it.uid}" }) { user ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onProfileSelected(user) }
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(user.profileImageUrl.ifBlank { null }, user.name, error = painterResource(R.drawable.img_app_logo), modifier = Modifier.size(46.dp).clip(CircleShape))
                                    Spacer(Modifier.width(10.dp))
                                    Column { Text(user.name, fontWeight = FontWeight.Bold); Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                        if (matchedPosts.isNotEmpty()) {
                            item { Text("Posts & media", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(matchedPosts, key = { "search_post_${it.id}" }) { post ->
                                Card(
                                    onClick = { if (post.videoUrl.isNotBlank()) fullScreenVideo = post.videoUrl },
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        when {
                                            post.imageUrl.isNotBlank() -> AsyncImage(
                                                post.imageUrl, "Photo result", contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp))
                                            )
                                            post.videoUrl.isNotBlank() -> Box(
                                                Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                                            else -> Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.Article, null)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(post.title.ifBlank { post.senderName }, fontWeight = FontWeight.Bold)
                                            Text(post.text, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (post.videoUrl.isNotBlank()) Text("Video • tap to play", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                            else if (post.imageUrl.isNotBlank()) Text("Photo", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        if (matchedUsers.isEmpty() && matchedPosts.isEmpty()) {
                            item {
                                Box(Modifier.fillParentMaxHeight(.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No results for “$normalized”", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityCenterDialog(
    notifications: List<ActivityNotification>,
    requests: List<FriendRequest>,
    messageRequests: List<MessageRequest>,
    onDismiss: () -> Unit,
    onAccept: (FriendRequest) -> Unit,
    onReject: (FriendRequest) -> Unit,
    onAcceptMessage: (MessageRequest) -> Unit,
    onRejectMessage: (MessageRequest) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                if (notifications.isEmpty() && requests.isEmpty() && messageRequests.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .5f))
                        Spacer(Modifier.height(12.dp))
                        Text("No activity yet", fontWeight = FontWeight.Bold)
                        Text("Likes, comments, views and requests appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (requests.isNotEmpty()) {
                            item { Text("Friend requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(requests, key = { "request_${it.id}" }) { request ->
                                Card(shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = request.senderImageUrl.ifBlank { null },
                                                contentDescription = request.senderName,
                                                error = painterResource(R.drawable.img_app_logo),
                                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(request.senderName, fontWeight = FontWeight.Bold)
                                                Text("sent you a friend request", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onAccept(request) }, modifier = Modifier.weight(1f)) { Text("Accept") }
                                            OutlinedButton(onClick = { onReject(request) }, modifier = Modifier.weight(1f)) { Text("Delete") }
                                        }
                                    }
                                }
                            }
                        }
                        if (messageRequests.isNotEmpty()) {
                            item { Text("Message requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(messageRequests, key = { "message_request_${it.id}" }) { request ->
                                Card(shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(request.senderName, fontWeight = FontWeight.Bold)
                                        Text(request.preview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onAcceptMessage(request) }, modifier = Modifier.weight(1f)) { Text("Accept") }
                                            OutlinedButton(onClick = { onRejectMessage(request) }, modifier = Modifier.weight(1f)) { Text("Delete") }
                                        }
                                    }
                                }
                            }
                        }
                        if (notifications.isNotEmpty()) {
                            item { Text("Recent activity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(notifications, key = { "activity_${it.id}" }) { item ->
                                val icon = when (item.type) {
                                    "post_reaction", "story_reaction" -> Icons.Outlined.Favorite
                                    "post_comment", "story_comment" -> Icons.Outlined.ChatBubbleOutline
                                    "story_view" -> Icons.Outlined.Visibility
                                    "friend_accepted", "friend_request" -> Icons.Outlined.PersonAdd
                                    else -> Icons.Outlined.Notifications
                                }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item.actorImageUrl.ifBlank { null },
                                        contentDescription = item.actorName,
                                        error = painterResource(R.drawable.img_app_logo),
                                        modifier = Modifier.size(46.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.actorName, fontWeight = FontWeight.Bold)
                                        Text(item.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
                                        )
                                    }
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenStoryViewer(
    stories: List<Story>,
    storyIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onReact: (Story, String) -> Unit,
    onComment: (Story, String) -> Unit,
    onDelete: (Story) -> Unit
) {
    var replyText by remember(storyIndex) { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (stories.isEmpty() || storyIndex >= stories.size) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onPrevious),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No more stories", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to view the previous story", color = Color.White.copy(alpha = .65f))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            } else {
                val story = stories[storyIndex]
                key(story.id) {
                    when {
                        story.videoUrl.isNotBlank() -> AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoPath(story.videoUrl)
                                    setOnPreparedListener { player ->
                                        player.isLooping = false
                                        start()
                                    }
                                    setOnCompletionListener { onNext() }
                                }
                            },
                            update = { video -> if (!video.isPlaying) video.start() },
                            modifier = Modifier.fillMaxSize()
                        )
                        story.imageUrl.isNotBlank() -> AsyncImage(
                            model = story.imageUrl,
                            contentDescription = "${story.senderName}'s story",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF111327)))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                story.text,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }

                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .62f),
                            .28f to Color.Transparent,
                            .68f to Color.Transparent,
                            1f to Color.Black.copy(alpha = .78f)
                        )
                    )
                )

                // Invisible left/right navigation zones provide familiar story physics.
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onPrevious))
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onNext))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEachIndexed { index, _ ->
                            LinearProgressIndicator(
                                progress = { if (index <= storyIndex) 1f else 0f },
                                modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = .25f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = story.senderProfilePic.ifBlank { null },
                            contentDescription = story.senderName,
                            error = painterResource(R.drawable.img_app_logo),
                            modifier = Modifier.size(42.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(story.senderName, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(story.timestamp)),
                                color = Color.White.copy(alpha = .68f),
                                fontSize = 11.sp
                            )
                        }
                        if (story.senderId == FirebaseAuth.getInstance().currentUser?.uid) {
                            IconButton(onClick = { onDelete(story) }) {
                                Icon(Icons.Outlined.Delete, "Delete story", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (story.text.isNotBlank() && (story.imageUrl.isNotBlank() || story.videoUrl.isNotBlank())) {
                        Text(
                            story.text,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("👍", "❤️", "😂", "😮", "😢").forEach { emoji ->
                            Surface(
                                onClick = { onReact(story, emoji) },
                                color = Color.White.copy(alpha = .13f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
                                shape = CircleShape
                            ) {
                                Text(emoji, fontSize = 18.sp, modifier = Modifier.padding(9.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply to story…", color = Color.White.copy(alpha = .7f)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = .75f),
                                unfocusedBorderColor = Color.White.copy(alpha = .35f),
                                focusedContainerColor = Color.Black.copy(alpha = .25f),
                                unfocusedContainerColor = Color.Black.copy(alpha = .25f)
                            )
                        )
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    onComment(story, replyText.trim())
                                    replyText = ""
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp).background(Color.White, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, "Send reply", tint = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalVideoFeedDialog(
    videos: List<Post>,
    startIndex: Int,
    users: List<User>,
    viewModel: ChatViewModel,
    onProfileSelected: (User) -> Unit,
    onDismiss: () -> Unit
) {
    if (videos.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(videos.indices), pageCount = { videos.size })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                ImmersiveVideoPage(
                    post = videos[page],
                    owner = users.find { it.uid == videos[page].senderId },
                    isActive = pagerState.currentPage == page,
                    viewModel = viewModel,
                    onProfileSelected = onProfileSelected
                )
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color.Black.copy(alpha = .36f), shape = CircleShape) {
                    Text("Videos", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(alpha = .42f), CircleShape)) {
                    Icon(Icons.Default.Close, "Close video feed", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ImmersiveVideoPage(
    post: Post,
    owner: User?,
    isActive: Boolean,
    viewModel: ChatViewModel,
    onProfileSelected: (User) -> Unit
) {
    var prepared by remember(post.id) { mutableStateOf(false) }
    var isPaused by remember(post.id) { mutableStateOf(false) }
    var comment by remember(post.id) { mutableStateOf("") }
    val videoView = remember(post.id) { mutableStateOf<VideoView?>(null) }
    var optimisticReaction by remember(post.id) { mutableStateOf<Boolean?>(null) }
    DisposableEffect(post.id) {
        onDispose {
            videoView.value?.stopPlayback()
            videoView.value = null
        }
    }
    val currentUser by viewModel.currentUserState.collectAsState()
    val sentRequests by viewModel.sentFriendRequestIds.collectAsState()
    val isFriend = owner?.let { currentUser?.friends?.contains(it.uid) == true } ?: false
    val requested = owner?.let { sentRequests.contains("${currentUser?.uid}_${it.uid}") } ?: false
    val serverReacted = post.reactions.containsKey(currentUser?.uid.orEmpty())
    val reacted = optimisticReaction ?: serverReacted
    val displayedReactionCount = (post.reactions.size + when {
        optimisticReaction == true && !serverReacted -> 1
        optimisticReaction == false && serverReacted -> -1
        else -> 0
    }).coerceAtLeast(0)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        key(post.id) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        videoView.value = this
                        setVideoPath(post.videoUrl)
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            prepared = true
                            if (isActive && !isPaused) { player.setVolume(1f, 1f); start() }
                        }
                        setOnInfoListener { _, what, _ ->
                            prepared = what != MediaPlayer.MEDIA_INFO_BUFFERING_START
                            false
                        }
                    }
                },
                update = { video ->
                    if (isActive && !isPaused) {
                        if (!video.isPlaying) video.start()
                    } else {
                        video.pause()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (!prepared) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("ভিডিও প্রস্তুত হচ্ছে…", color = Color.White.copy(alpha = .75f))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .78f)))))
        AnimatedVisibility(
            visible = isPaused,
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = { isPaused = false },
                modifier = Modifier.size(68.dp).background(Color.Black.copy(alpha = .48f), CircleShape)
            ) { Icon(Icons.Default.PlayArrow, "Resume video", tint = Color.White, modifier = Modifier.size(38.dp)) }
        }
        Box(
            Modifier.fillMaxWidth().height(52.dp).align(Alignment.Center).clickable { isPaused = !isPaused }
        )

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            owner?.let { person ->
                AsyncImage(
                    person.profileImageUrl.ifBlank { null }, person.name,
                    error = painterResource(R.drawable.img_app_logo),
                    modifier = Modifier.size(52.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).clickable { onProfileSelected(person) }
                )
            }
            IconButton(onClick = {
                optimisticReaction = !reacted
                viewModel.reactToPost(post.id, "❤️")
            }, modifier = Modifier.background(Color.Black.copy(alpha = .38f), CircleShape)) {
                Icon(if (reacted) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, "Like", tint = if (reacted) Color(0xFFFF4F78) else Color.White)
            }
            Text(displayedReactionCount.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            if (owner != null && owner.uid != currentUser?.uid && !isFriend) {
                if (requested) {
                    IconButton(onClick = { viewModel.cancelFriendRequest(owner.uid) }, modifier = Modifier.background(Color.Black.copy(alpha = .38f), CircleShape)) {
                        Icon(Icons.Default.PersonRemove, "Cancel request", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { viewModel.sendFriendRequest(owner) }, modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)) {
                        Icon(Icons.Default.PersonAdd, "Add friend", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.senderName, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                if (post.feeling.isNotBlank()) Text("  • ${post.feeling}", color = Color.White.copy(alpha = .72f))
            }
            if (post.title.isNotBlank()) Text(post.title, color = Color.White, fontWeight = FontWeight.Bold)
            if (post.text.isNotBlank()) Text(post.text, color = Color.White.copy(alpha = .88f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (post.tags.isNotEmpty()) Text(post.tags.joinToString(" ") { "#$it" }, color = Color(0xFFB9A8FF))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment…", color = Color.White.copy(alpha = .65f)) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = .7f), unfocusedBorderColor = Color.White.copy(alpha = .3f),
                        focusedContainerColor = Color.Black.copy(alpha = .28f), unfocusedContainerColor = Color.Black.copy(alpha = .28f)
                    )
                )
                IconButton(onClick = {
                    if (comment.isNotBlank()) { viewModel.commentOnPost(post.id, comment.trim()); comment = "" }
                }) { Icon(Icons.Default.Send, "Comment", tint = Color.White) }
            }
        }
    }
}

@Composable
fun FullScreenVideoPlayer(videoUrl: String, onDismiss: () -> Unit) {
    val playerView = remember(videoUrl) { mutableStateOf<VideoView?>(null) }
    var paused by remember(videoUrl) { mutableStateOf(false) }
    DisposableEffect(videoUrl) {
        onDispose { playerView.value?.stopPlayback(); playerView.value = null }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        playerView.value = this
                        setVideoPath(videoUrl)
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            if (!paused) start()
                        }
                    }
                },
                update = { video -> if (paused) video.pause() else if (!video.isPlaying) video.start() },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { paused = !paused },
                modifier = Modifier.align(Alignment.Center).size(62.dp).background(Color.Black.copy(alpha = .42f), CircleShape)
            ) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Play" else "Pause", tint = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = .45f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "Close full screen video", tint = Color.White)
            }
        }
    }
}

@Composable
fun StoriesHorizontalSection(
    viewModel: ChatViewModel,
    stories: List<Story>,
    onStorySelected: (Story) -> Unit
) {
    val context = LocalContext.current
    var isUploadingStoryMedia by remember { mutableStateOf(false) }
    var showAddStoryDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingStoryMedia = true
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    viewModel.uploadFileToSupabase(
                        bucket = "chat_images",
                        fileName = "story_${System.currentTimeMillis()}.jpg",
                        fileBytes = bytes,
                        contentType = "image/jpeg",
                        onSuccess = { publicUrl ->
                            isUploadingStoryMedia = false
                            // Add standard text story
                            viewModel.uploadStory(text = "My Story", imageUrl = publicUrl, videoUrl = "") {}
                            Toast.makeText(context, "Story uploaded successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            isUploadingStoryMedia = false
                            Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                isUploadingStoryMedia = false
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingStoryMedia = true
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    viewModel.uploadFileToSupabase(
                        bucket = "chat_images",
                        fileName = "story_${System.currentTimeMillis()}.mp4",
                        fileBytes = bytes,
                        contentType = "video/mp4",
                        onSuccess = { publicUrl ->
                            isUploadingStoryMedia = false
                            viewModel.uploadStory(text = "My Story", imageUrl = "", videoUrl = publicUrl) {}
                            Toast.makeText(context, "Video Story uploaded successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            isUploadingStoryMedia = false
                            Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                isUploadingStoryMedia = false
            }
        }
    }

    if (showAddStoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddStoryDialog = false },
            title = { Text("Add Story", fontWeight = FontWeight.Bold) },
            text = { Text("Choose what type of story you want to share:") },
            confirmButton = {
                Button(onClick = {
                    showAddStoryDialog = false
                    imagePicker.launch("image/*")
                }) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photo Story")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showAddStoryDialog = false
                        videoPicker.launch("video/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Video Story")
                }
            }
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // "Add Story" first card bubble
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(82.dp)
                    .clickable { showAddStoryDialog = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingStoryMedia) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "Add story", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your Story", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        items(stories) { story ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(82.dp)
                    .clickable { onStorySelected(story) }
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(3.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = story.senderProfilePic.ifBlank { null },
                        contentDescription = story.senderName,
                        error = painterResource(id = R.drawable.img_app_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = story.senderName.split(" ").firstOrNull() ?: story.senderName,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SocialPostItem(post: Post, viewModel: ChatViewModel, onProfileSelected: (User) -> Unit = {}) {
    var isCommentsExpanded by remember { mutableStateOf(false) }
    var commentInputText by remember { mutableStateOf("") }
    var showFullscreenVideo by remember(post.id) { mutableStateOf(false) }
    val inlineVideoView = remember(post.id) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(post.id) {
        onDispose {
            inlineVideoView.value?.stopPlayback()
            inlineVideoView.value = null
        }
    }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isDark = isSystemInDarkTheme()
    val allUsers by viewModel.usersState.collectAsState()
    val allPosts by viewModel.postsState.collectAsState()
    val videoPosts = allPosts.filter { it.videoUrl.isNotBlank() }

    if (showFullscreenVideo && post.videoUrl.isNotBlank()) {
        VerticalVideoFeedDialog(
            videos = videoPosts,
            startIndex = videoPosts.indexOfFirst { it.id == post.id }.coerceAtLeast(0),
            users = allUsers,
            viewModel = viewModel,
            onProfileSelected = onProfileSelected,
            onDismiss = { showFullscreenVideo = false }
        )
    }

    // Register simple visual view count increment on render
    LaunchedEffect(post.id) {
        viewModel.incrementPostViews(post.id)
    }

    // Get commenters' profiles
    val currentUserProfile = allUsers.find { it.uid == currentUserId }
    val currentUserPic = currentUserProfile?.profileImageUrl ?: ""
    val postOwner = allUsers.find { it.uid == post.senderId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(
                isDark = isDark,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Sender top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = post.senderProfilePic.ifBlank { null },
                        contentDescription = post.senderName,
                        error = painterResource(id = R.drawable.img_app_logo),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { postOwner?.let(onProfileSelected) }
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            post.senderName, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (post.isPrivate) "🔒 Private" else "🌐 Public",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
                                    timeZone = TimeZone.getTimeZone("Asia/Dhaka")
                                }.format(Date(post.timestamp)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Delete option if creator
                if (post.senderId == currentUserId) {
                    IconButton(onClick = { viewModel.deletePost(post.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (post.title.isNotBlank()) {
                Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
            }
            if (post.feeling.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text("Feeling ${post.feeling}") }, leadingIcon = { Text("✨") })
                Spacer(Modifier.height(6.dp))
            }

            // Post Content Text
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text, 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (post.tags.isNotEmpty()) {
                Text(post.tags.joinToString("  ") { "#$it" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }

            // Post Content Image
            if (post.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Post Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Post videos autoplay silently in-feed; tapping opens immersive portrait playback.
            if (post.videoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .clickable { showFullscreenVideo = true },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                inlineVideoView.value = this
                                setVideoPath(post.videoUrl)
                                setOnPreparedListener { player ->
                                    player.isLooping = true
                                    player.setVolume(1f, 1f)
                                    if (!showFullscreenVideo) start()
                                }
                            }
                        },
                        update = { video ->
                            if (showFullscreenVideo) video.pause()
                            else if (!video.isPlaying) video.start()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().clickable { showFullscreenVideo = true })
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = .55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Fullscreen, "Open full screen", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 1. Reactions & Comments Summary Row (Facebook style)
            val reactionCount = post.reactions.size
            val uniqueReactions = post.reactions.values.distinct().take(3)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Overlapping emojis & count
                if (reactionCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-4).dp)
                    ) {
                        uniqueReactions.forEach { emoji ->
                            Text(text = emoji, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (reactionCount == 1) "1 person" else "$reactionCount people",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = "Be the first to react",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Right side: Comment & Views count
                Text(
                    text = "${post.comments.size} comments • ${post.viewsCount} views",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 8.dp), 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            // 2. Interactive Action Buttons (Like / Comment / Share)
            var showReactionPicker by remember { mutableStateOf(false) }
            val myReaction = post.reactions[currentUserId]

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like / React Button
                Box {
                    val scaleBtn = animateFloatAsState(targetValue = if (myReaction != null) 1.15f else 1.0f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .graphicsLayer(scaleX = scaleBtn.value, scaleY = scaleBtn.value)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (myReaction != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (myReaction != null) {
                                            viewModel.reactToPost(post.id, myReaction)
                                        } else {
                                            viewModel.reactToPost(post.id, "👍")
                                        }
                                    },
                                    onLongPress = {
                                        showReactionPicker = true
                                    }
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (myReaction != null) {
                            Text(text = myReaction, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (myReaction) {
                                    "👍" -> "Like"
                                    "❤️" -> "Love"
                                    "😂" -> "Haha"
                                    "😮" -> "Wow"
                                    "😢" -> "Sad"
                                    "😡" -> "Angry"
                                    else -> "Reacted"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Like",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Floating Reaction Picker Card (Glassmorphic Popup)
                    if (showReactionPicker) {
                        Popup(
                            alignment = Alignment.TopCenter,
                            onDismissRequest = { showReactionPicker = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .glassmorphic(isDark = isDark, shape = RoundedCornerShape(32.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "😡")
                                    emojis.forEach { emoji ->
                                        Text(
                                            text = emoji,
                                            fontSize = 24.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    viewModel.reactToPost(post.id, emoji)
                                                    showReactionPicker = false
                                                }
                                                .padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Comment Trigger Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { isCommentsExpanded = !isCommentsExpanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = if (isCommentsExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Comment",
                        color = if (isCommentsExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isCommentsExpanded) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Share link Button
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Post Link", "https://ais-pre-rmbon56osx2tjzuwdbosiw-734111311118.asia-southeast1.run.app/post/${post.id}")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Post link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // Expanded comments block (Facebook dense bubble style)
            AnimatedVisibility(visible = isCommentsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Facebook dense style comment items
                    post.comments.forEach { c ->
                        val commenter = allUsers.find { it.uid == c.senderId }
                        val avatarUrl = commenter?.profileImageUrl ?: ""

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            AsyncImage(
                                model = avatarUrl.ifBlank { null },
                                contentDescription = c.senderName,
                                error = painterResource(id = R.drawable.img_app_logo),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                // Rounded bubble container
                                Box(
                                    modifier = Modifier
                                        .glassmorphic(
                                            isDark = isDark,
                                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = c.senderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = c.text,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                
                                // Comment action buttons and time below the bubble
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                                ) {
                                    val cTime = try {
                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
                                            timeZone = TimeZone.getTimeZone("Asia/Dhaka")
                                        }.format(Date(c.timestamp))
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    Text(
                                        text = cTime,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Like",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable { }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Reply",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable { }
                                    )
                                }
                            }
                        }
                    }

                    // Add comment text row (Facebook-like style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = currentUserPic.ifBlank { null },
                            contentDescription = "Me",
                            error = painterResource(id = R.drawable.img_app_logo),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = commentInputText,
                            onValueChange = { commentInputText = it },
                            placeholder = { Text("Write comment...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (commentInputText.isNotBlank()) {
                                    viewModel.commentOnPost(post.id, commentInputText)
                                    commentInputText = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send, 
                                contentDescription = "Send Comment",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatConversationUserItem(
    user: User,
    viewModel: ChatViewModel,
    hasActiveStory: Boolean,
    onSelect: () -> Unit
) {
    // Collect the dynamic last message between currentUser and otherUser
    val lastMessageState by viewModel.getLastMessageFlow(user.uid).collectAsState(initial = null)
    
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val unreadCounts by viewModel.unreadCountsState.collectAsState()
    val unreadCount = unreadCounts[user.uid] ?: 0
    val hasUnread = unreadCount > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            width = if (hasUnread) 2.dp else 1.dp,
            color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar bubble with online indicator badge
            Box(contentAlignment = Alignment.BottomEnd) {
                val storyModifier = if (hasActiveStory) {
                    Modifier
                        .size(54.dp)
                        .border(3.dp, MaterialTheme.colorScheme.error, CircleShape)
                        .padding(3.dp)
                } else {
                    Modifier
                        .size(50.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                }

                if (user.profileImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = "Profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = storyModifier.clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = storyModifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Green/Grey dynamic online status dot
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (user.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Display name + LAST MESSAGE (bold highlighted if unread!)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                    
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Show last message text instead of username
                val lastMsgText = if (lastMessageState != null) {
                    val m = lastMessageState!!
                    if (!m.imageUrl.isNullOrBlank()) "📷 [Photo Attachment]" 
                    else if (!m.voiceUrl.isNullOrBlank()) "🎙️ [Voice Note]"
                    else m.text
                } else {
                    "No messages yet. Say Hello!"
                }

                Text(
                    text = lastMsgText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (hasUnread) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Message Status indicators & unread markers!
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasUnread) {
                    // Cute glowing notification indicator badge
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else if (lastMessageState != null && lastMessageState!!.senderId == currentUserId) {
                    // Check message receipt ticks
                    val seen = lastMessageState!!.seenByRecipient
                    Icon(
                        imageVector = if (seen) Icons.Default.DoneAll else Icons.Default.Check,
                        contentDescription = if (seen) "Seen" else "Sent",
                        tint = if (seen) Color(0xFF0288D1) else Color(0xFF9E9E9E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupItemCard(group: Group, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (group.profileUrl.isNotBlank()) {
                AsyncImage(
                    model = group.profileUrl,
                    contentDescription = group.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (group.lastMessage.isNotBlank()) group.lastMessage else "No messages in group yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onClick) {
                Icon(Icons.Default.ArrowForwardIos, contentDescription = "Enter", modifier = Modifier.size(16.dp))
            }
        }
    }
}
