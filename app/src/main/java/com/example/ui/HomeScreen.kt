package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.User
import java.io.InputStream
import java.util.*

// Chocolate / Cosmic Warm Premium Palette
val ChocolateDark = Color(0xFF261C18) // #261c18
val ChocolateMedium = Color(0xFF382A24) // #382a24
val ChocolateLight = Color(0xFF4E3B33) // #4e3b33
val GoldAccent = Color(0xFFDFBBA3) // #dfbba3
val WhiteText = Color(0xFFF9F6F0)
val GrayText = Color(0xFFBCAAA4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onUserSelected: (User) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.filteredUsersState.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val inAppNotification by viewModel.inAppNotification.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: Contacts, 2: Blocked, 3: Service, 4: Profile

    // Runtime Permission Launcher for POST_NOTIFICATIONS (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification Permission Granted!", Toast.LENGTH_SHORT).show()
        }
    }

    // Check permissions
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Base background brush matching the mockup's rich warm tone
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(ChocolateDark, ChocolateMedium)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = ChocolateMedium,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(1.dp, ChocolateLight, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = if (currentTab == 0) GoldAccent else GrayText) },
                    label = { Text("Home", color = if (currentTab == 0) GoldAccent else GrayText, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = ChocolateLight)
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats", tint = if (currentTab == 1) GoldAccent else GrayText) },
                    label = { Text("Chats", color = if (currentTab == 1) GoldAccent else GrayText, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = ChocolateLight)
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Block, contentDescription = "Blocked", tint = if (currentTab == 2) GoldAccent else GrayText) },
                    label = { Text("Blocked", color = if (currentTab == 2) GoldAccent else GrayText, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = ChocolateLight)
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Service", tint = if (currentTab == 3) GoldAccent else GrayText) },
                    label = { Text("Service", color = if (currentTab == 3) GoldAccent else GrayText, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = ChocolateLight)
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile", tint = if (currentTab == 4) GoldAccent else GrayText) },
                    label = { Text("Profile", color = if (currentTab == 4) GoldAccent else GrayText, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = ChocolateLight)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
            ) {
                // Header Area with Title & Subtitle (Less spacing, tighter layout as requested)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FireChat",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = GoldAccent,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Real-time chat & push messaging",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { currentTab = 4 },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ChocolateMedium)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = GoldAccent)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = { viewModel.logout { onSignOut() } },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ChocolateMedium)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Color(0xFFEF5350))
                        }
                    }
                }

                // Dynamic body display based on the selected bottom navigation tab
                when (currentTab) {
                    0 -> {
                        // HOME TAB
                        Spacer(modifier = Modifier.height(10.dp))

                        // Modern Rounded Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchUsers(it)
                            },
                            placeholder = { Text("Search users by username...", color = GrayText) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldAccent) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        viewModel.searchUsers("")
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = GoldAccent)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_bar"),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = WhiteText,
                                unfocusedTextColor = WhiteText,
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = ChocolateLight,
                                focusedContainerColor = ChocolateMedium,
                                unfocusedContainerColor = ChocolateMedium
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Now row (renders online users mapped from RTDB status)
                        val activeUsers = users.filter { it.isOnline }
                        if (activeUsers.isNotEmpty()) {
                            ActiveUsersRow(users = activeUsers, onUserSelected = onUserSelected)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = "Conversations",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhiteText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Mapped users and their online states
                        if (users.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.People,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = GrayText.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No other users registered",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WhiteText
                                    )
                                    Text(
                                        text = "Share with your friends to chat!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GrayText
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(users) { user ->
                                    UserItem(user = user, onClick = { onUserSelected(user) })
                                }
                            }
                        }
                    }
                    1 -> {
                        // CHATS TAB (All users list)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Contacts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhiteText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (users.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No other contacts available", color = GrayText)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(users) { user ->
                                    UserItem(user = user, onClick = { onUserSelected(user) })
                                }
                            }
                        }
                    }
                    2 -> {
                        // BLOCKED USERS TAB
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Blocked Users",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhiteText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val blockedList = currentUser?.blockedUsers ?: emptyList()
                        if (blockedList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("You haven't blocked any users.", color = GrayText)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(blockedList) { blockedUid ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = ChocolateMedium),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(ChocolateLight),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = GoldAccent)
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "ID: ...${blockedUid.takeLast(6)}",
                                                    color = WhiteText,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.unblockUser(blockedUid) {
                                                        Toast.makeText(context, "User unblocked successfully!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                                            ) {
                                                Text("Unblock", color = ChocolateDark, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // SERVICE SETTINGS TAB
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Webhook Push Config",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhiteText,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        var localUrlInput by remember { mutableStateOf(webhookUrl) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ChocolateMedium),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Configure Webhook URL",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GoldAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "This webhook handles all outgoing push notifications in real time. Stored securely on Firebase Realtime Database.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GrayText
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = localUrlInput,
                                    onValueChange = { localUrlInput = it },
                                    placeholder = { Text("https://example.com/webhook", color = GrayText) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = WhiteText,
                                        unfocusedTextColor = WhiteText,
                                        focusedBorderColor = GoldAccent,
                                        unfocusedBorderColor = ChocolateLight
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        viewModel.updateWebhookUrl(localUrlInput)
                                        Toast.makeText(context, "Successfully saved Webhook URL!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                                ) {
                                    Text("Save Configuration", color = ChocolateDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ChocolateMedium),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Service Connection Active", color = WhiteText, fontWeight = FontWeight.Bold)
                                    Text("FCM listener synced on Bangladesh Time (Asia/Dhaka)", color = GrayText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    4 -> {
                        // PROFILE TAB (With real profile picture picker and Supabase storage upload!)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Edit Profile Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhiteText,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        currentUser?.let { user ->
                            var editedName by remember { mutableStateOf(user.name) }
                            var editedDob by remember { mutableStateOf(user.dob) }
                            var profilePicUrl by remember { mutableStateOf(user.profileImageUrl) }
                            var isUploading by remember { mutableStateOf(false) }

                            // Register date picker
                            val calendar = Calendar.getInstance()
                            val datePickerDialog = DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    editedDob = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            )

                            // Launch photo gallery picker
                            val galleryLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent()
                            ) { uri: Uri? ->
                                if (uri != null) {
                                    isUploading = true
                                    try {
                                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                                        val bytes = inputStream?.readBytes()
                                        if (bytes != null) {
                                            val fileExtension = "jpg"
                                            val fileName = "avatar_${user.uid}_${System.currentTimeMillis()}.$fileExtension"
                                            
                                            // Call Supabase Upload
                                            viewModel.uploadFileToSupabase(
                                                bucket = "avatars",
                                                fileName = fileName,
                                                fileBytes = bytes,
                                                contentType = "image/jpeg",
                                                onSuccess = { publicUrl ->
                                                    isUploading = false
                                                    profilePicUrl = publicUrl
                                                    Toast.makeText(context, "Profile picture uploaded successfully!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { errorMsg ->
                                                    isUploading = false
                                                    Toast.makeText(context, "Upload failed: $errorMsg", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } else {
                                            isUploading = false
                                        }
                                    } catch (e: Exception) {
                                        isUploading = false
                                        Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Image Circle with upload trigger overlay
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clickable { galleryLauncher.launch("image/*") }
                                        .border(2.dp, GoldAccent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profilePicUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = profilePicUrl,
                                            contentDescription = "Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(106.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(106.dp)
                                                .clip(CircleShape)
                                                .background(ChocolateLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.name.take(1).uppercase(),
                                                color = GoldAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 32.sp
                                            )
                                        }
                                    }
                                    // Uploading progress loading overlay
                                    if (isUploading) {
                                        Box(
                                            modifier = Modifier
                                                .size(106.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(24.dp))
                                        }
                                    } else {
                                        // Upload Overlay Camera Icon
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(ChocolateDark)
                                                .align(Alignment.BottomEnd)
                                                .border(1.dp, GoldAccent, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = "Edit photo", tint = GoldAccent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("@${user.username}", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(18.dp))

                                // Name Input Field
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    label = { Text("Display Name", color = GrayText) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = WhiteText,
                                        unfocusedTextColor = WhiteText,
                                        focusedBorderColor = GoldAccent,
                                        unfocusedBorderColor = ChocolateLight
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Date of Birth Picker Field
                                OutlinedTextField(
                                    value = editedDob,
                                    onValueChange = { },
                                    label = { Text("Date of Birth", color = GrayText) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { datePickerDialog.show() },
                                    enabled = false,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = WhiteText,
                                        disabledBorderColor = ChocolateLight,
                                        disabledLabelColor = GrayText
                                    )
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        if (editedName.isBlank()) {
                                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        viewModel.updateUserProfile(editedName, editedDob, profilePicUrl,
                                            onSuccess = {
                                                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { error ->
                                                Toast.makeText(context, "Update failed: $error", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                                ) {
                                    Text("Save Changes", color = ChocolateDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Beautiful glowing sliding In-App Notification alert box overlay
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
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = ChocolateMedium),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, GoldAccent, RoundedCornerShape(20.dp))
                            .clickable {
                                // Find user matching notification senderId and open chat
                                val targetUser = users.find { it.uid == notif.senderId }
                                if (targetUser != null) {
                                    onUserSelected(targetUser)
                                }
                                viewModel.dismissInAppNotification()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(ChocolateLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(notif.senderName.take(1).uppercase(), color = GoldAccent, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notif.senderName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = GoldAccent
                                )
                                Text(
                                    text = notif.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WhiteText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.dismissInAppNotification() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = GrayText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveUsersRow(
    users: List<User>,
    onUserSelected: (User) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Active Now",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = GoldAccent,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(users) { user ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onUserSelected(user) }
                        .padding(vertical = 4.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (user.profileImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.profileImageUrl,
                                contentDescription = user.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, GoldAccent, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ChocolateLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = GoldAccent
                                )
                            }
                        }
                        // Glowing Green status dot (Active user badge)
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(ChocolateDark)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.name.split(" ").firstOrNull() ?: user.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp),
                        textAlign = TextAlign.Center,
                        color = WhiteText
                    )
                }
            }
        }
    }
}

@Composable
fun UserItem(
    user: User,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("user_item_${user.username}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ChocolateMedium),
        border = BorderStroke(1.dp, ChocolateLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar circle with green/grey status dot
            Box(contentAlignment = Alignment.BottomEnd) {
                if (user.profileImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = user.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, GoldAccent, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(ChocolateLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = GoldAccent
                        )
                    }
                }
                // Dynamic online status badge (Green if isOnline, grey if offline!)
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(ChocolateMedium)
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = WhiteText
                )
                Text(
                    text = if (user.isOnline) "Active Now" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.isOnline) Color(0xFF81C784) else GrayText
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "Message",
                    tint = GoldAccent
                )
            }
        }
    }
}
