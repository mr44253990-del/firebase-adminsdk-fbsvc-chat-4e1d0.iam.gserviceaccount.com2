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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.Group
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
    val currentTheme by viewModel.themeState.collectAsState()
    val allUsers by viewModel.usersState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val currentTab by viewModel.currentTabState.collectAsState()

    // Dialog & Creation controllers
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showStoryViewer by remember { mutableStateOf<Story?>(null) }

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
                        IconButton(onClick = { viewModel.setCurrentTab(4) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.logout { onSignOut() } }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
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
                    // SOCIAL HOME TAB: Tabbed view for Feed and Stories
                    var homeTabIndex by remember { mutableStateOf(0) }
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = homeTabIndex,
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ) {
                            Tab(
                                selected = homeTabIndex == 0,
                                onClick = { homeTabIndex = 0 },
                                text = { Text("Social Feed", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = homeTabIndex == 1,
                                onClick = { homeTabIndex = 1 },
                                text = { Text("Stories", fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (homeTabIndex == 0) {
                            // Feed Tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showCreatePostDialog = true },
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Create Post", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Post", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (posts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), modifier = Modifier.size(60.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("No posts available", fontWeight = FontWeight.Bold)
                                        Text("Be the first to share a post in the community!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 14.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(posts) { post ->
                                        SocialPostItem(post = post, viewModel = viewModel)
                                    }
                                }
                            }
                        } else {
                            // Stories Tab
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    StoriesHorizontalSection(
                                        viewModel = viewModel,
                                        stories = stories,
                                        onStorySelected = { showStoryViewer = it }
                                    )
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
                                        text = "Webhook Push Notifications Configuration",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Incoming message events in direct chats trigger notifications using this URL. This configuration instantly propagates to all user devices via Realtime Database.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = adminUrlInput,
                                        onValueChange = { adminUrlInput = it },
                                        placeholder = { Text("https://rakibul.n8n-host.com/webhook/ra") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(18.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            viewModel.updateWebhookUrl(adminUrlInput)
                                            Toast.makeText(context, "Saved webhook URL securely to database!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save and Deploy Webhook", fontWeight = FontWeight.Bold)
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

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            if (inputName.isBlank()) {
                                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            viewModel.updateUserProfile(inputName, inputDob, inputProfileUrl,
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

    // CREATE POST DIALOG
    if (showCreatePostDialog) {
        var postText by remember { mutableStateOf("") }
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        placeholder = { Text("What's on your mind?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )

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
                        if (postText.isNotBlank() || postImageUrl.isNotBlank() || postVideoUrl.isNotBlank()) {
                            viewModel.createPost(postText, postImageUrl, "", postVideoUrl, isPrivateToggle) {}
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

    // STORY VIEWER DIALOG
    if (showStoryViewer != null) {
        val activeStory = showStoryViewer!!
        var commentText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showStoryViewer = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = activeStory.senderProfilePic.ifBlank { null },
                        contentDescription = null,
                        error = painterResource(id = R.drawable.img_app_logo),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(activeStory.senderName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Story Details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeStory.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = activeStory.imageUrl,
                            contentDescription = "Story Media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }

                    if (activeStory.videoUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            var isPlaying by remember { mutableStateOf(true) }
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoPath(activeStory.videoUrl)
                                        setOnPreparedListener { mediaPlayer ->
                                            mediaPlayer.isLooping = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { videoView ->
                                    if (isPlaying) {
                                        videoView.start()
                                    } else {
                                        videoView.pause()
                                    }
                                }
                            )

                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Text(
                        text = activeStory.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // React to Story Buttons
                    Text("Reactions:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "😡")
                        reactions.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .clickable {
                                        viewModel.reactToStory(activeStory.id, emoji)
                                        // Update local preview state
                                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                        val updatedMap = activeStory.reactions.toMutableMap()
                                        updatedMap[currentUserId] = emoji
                                        showStoryViewer = activeStory.copy(reactions = updatedMap)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 16.sp)
                            }
                        }
                    }

                    // Display reactions list count
                    if (activeStory.reactions.isNotEmpty()) {
                        Text(
                            text = "Active Reactions: ${activeStory.reactions.values.groupBy { it }.map { "${it.key} x${it.value.size}" }.joinToString(", ")}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Comments block
                    val isDark = isSystemInDarkTheme()
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val currentUserProfile = allUsers.find { it.uid == currentUserId }
                    val currentUserPic = currentUserProfile?.profileImageUrl ?: ""

                    Text("Comments:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    
                    activeStory.comments.forEach { c ->
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
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
                            value = commentText,
                            onValueChange = { commentText = it },
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
                                if (commentText.isNotBlank()) {
                                    viewModel.commentOnStory(activeStory.id, commentText)
                                    commentText = ""
                                    showStoryViewer = null // close and refresh
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Post Comment", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Allowed deletion if owned by current user
                    if (activeStory.senderId == currentUserId) {
                        Button(
                            onClick = {
                                viewModel.deleteStory(activeStory.id)
                                showStoryViewer = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Story")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStoryViewer = null }) {
                    Text("Close")
                }
            }
        )
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
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // "Add Story" first card bubble
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { showAddStoryDialog = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingStoryMedia) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "(Add Story)", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Add Story", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        items(stories) { story ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onStorySelected(story) }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = story.senderProfilePic.ifBlank { null },
                        contentDescription = story.senderName,
                        error = painterResource(id = R.drawable.img_app_logo),
                        modifier = Modifier
                            .size(50.dp)
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
fun SocialPostItem(post: Post, viewModel: ChatViewModel) {
    var isCommentsExpanded by remember { mutableStateOf(false) }
    var commentInputText by remember { mutableStateOf("") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isDark = isSystemInDarkTheme()

    // Register simple visual view count increment on render
    LaunchedEffect(post.id) {
        viewModel.incrementPostViews(post.id)
    }

    // Get commenters' profiles
    val allUsers by viewModel.usersState.collectAsState()
    val currentUserProfile = allUsers.find { it.uid == currentUserId }
    val currentUserPic = currentUserProfile?.profileImageUrl ?: ""

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

            // Post Content Video
            if (post.videoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    var isPlaying by remember { mutableStateOf(false) }
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(post.videoUrl)
                                setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { videoView ->
                            if (isPlaying) {
                                videoView.start()
                            } else {
                                videoView.pause()
                            }
                        }
                    )

                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
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
