package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppTheme
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUserState.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val selectedAccentColor by viewModel.selectedAccentColor.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(DarkBackground, DarkSurface)
    )

    Scaffold(
        topBar = {
            SettingsTopBar(onBack = onBack)
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Section
            ProfileSection(
                user = currentUser,
                onEditProfile = { /* Navigate to edit */ }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Theme Section
            SettingsSection(title = "Appearance") {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "App Theme",
                    subtitle = when (selectedTheme) {
                        "light" -> "Light"
                        "dark" -> "Dark"
                        else -> "System Default"
                    },
                    onClick = { showThemeDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.ColorLens,
                    title = "Accent Color",
                    subtitle = "Customize app accent",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(selectedAccentColor))
                                    } catch (e: Exception) {
                                        ElectricPurple
                                    }
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        )
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Section
            SettingsSection(title = "Privacy & Security") {
                SettingsItem(
                    icon = Icons.Default.Visibility,
                    title = "Activity Status",
                    subtitle = if (currentUser?.isActivityVisible == true) "Everyone can see" else "Only you",
                    onClick = { viewModel.setActivityVisibility(!(currentUser?.isActivityVisible ?: true)) }
                )
                
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = "Blocked Users",
                    subtitle = "${currentUser?.blockedUsers?.size ?: 0} blocked",
                    onClick = { /* Show blocked users */ }
                )
                
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = "Two-Factor Authentication",
                    subtitle = "Not enabled",
                    onClick = { /* Enable 2FA */ }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notifications Section
            SettingsSection(title = "Notifications") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Push Notifications",
                    subtitle = "Enabled",
                    trailing = {
                        Switch(
                            checked = true,
                            onCheckedChange = { /* Toggle */ },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricPurple
                            )
                        )
                    },
                    onClick = { }
                )
                
                SettingsItem(
                    icon = Icons.Default.Message,
                    title = "Message Preview",
                    subtitle = "Show in notifications",
                    trailing = {
                        Switch(
                            checked = true,
                            onCheckedChange = { /* Toggle */ },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricPurple
                            )
                        )
                    },
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About FireChat Pro",
                    subtitle = "Version 2.0.0",
                    onClick = { showAboutDialog = true }
                )
                
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = "Terms of Service",
                    subtitle = "Read our terms",
                    onClick = { }
                )
                
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How we handle your data",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Version info
            Text(
                text = "FireChat Pro v2.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = selectedTheme,
            currentAccentColor = selectedAccentColor,
            onThemeSelected = { theme ->
                viewModel.updateTheme(theme)
            },
            onAccentColorSelected = { color ->
                viewModel.updateAccentColor(color)
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Surface(
        color = DarkSurface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileSection(
    user: com.example.data.User?,
    onEditProfile: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image with glow
            Box(
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .blur(20.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(ElectricPurple.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )
                
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(GradientPurpleCyan)),
                    contentAlignment = Alignment.Center
                ) {
                    if (user?.profileImageUrl?.isNotBlank() == true) {
                        coil.compose.AsyncImage(
                            model = user.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = user?.name?.take(1)?.uppercase() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                    }
                }
                
                // Edit badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp),
                    shape = CircleShape,
                    color = ElectricPurple,
                    border = BorderStroke(3.dp, DarkSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = user?.name ?: "User",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            
            Text(
                text = "@${user?.username ?: "username"}",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricPurple
            )
            
            if (user?.bio?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                StatItem(count = user?.followers?.size ?: 0, label = "Followers")
                StatItem(count = user?.following?.size ?: 0, label = "Following")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onEditProfile,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile")
            }
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = ElectricPurple,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
        )
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface.copy(alpha = 0.5f)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricPurple,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    currentAccentColor: String,
    onThemeSelected: (String) -> Unit,
    onAccentColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeOptions = listOf(
        Triple("system", "System Default", Icons.Default.Settings),
        Triple("light", "Light", Icons.Default.LightMode),
        Triple("dark", "Dark", Icons.Default.DarkMode)
    )
    
    val colorOptions = listOf(
        "#6C63FF" to "Purple",
        "#00D9FF" to "Cyan",
        "#FF6B9D" to "Pink",
        "#00FF88" to "Green",
        "#FFD700" to "Gold",
        "#FF6B6B" to "Red",
        "#9C27B0" to "Violet",
        "#FF9800" to "Orange"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Customize App", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                themeOptions.forEach { (value, label, icon) ->
                    Surface(
                        onClick = { onThemeSelected(value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (currentTheme == value) ElectricPurple.copy(alpha = 0.3f) else DarkSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (currentTheme == value) ElectricPurple else Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentTheme == value) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ElectricPurple
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colorOptions) { (color, name) ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(color))
                                    } catch (e: Exception) {
                                        ElectricPurple
                                    }
                                )
                                .border(
                                    width = if (currentAccentColor == color) 3.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { onAccentColorSelected(color) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentAccentColor == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(GradientPurpleCyan)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FireChat Pro",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 2.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "👨‍💻 Developer",
                    fontWeight = FontWeight.Bold,
                    color = ElectricPurple
                )
                Text(
                    text = "Rakibul Islam",
                    color = Color.White
                )
                Text(
                    text = "Student, Kapilamuni College",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "📝 About",
                    fontWeight = FontWeight.Bold,
                    color = ElectricPurple
                )
                Text(
                    text = "FireChat Pro is a next-generation social communication platform featuring real-time messaging, stories, posts, groups, and notes. Built with modern Android architecture and Firebase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "⚡ Features",
                    fontWeight = FontWeight.Bold,
                    color = ElectricPurple
                )
                Text(
                    text = "• Real-time Chat\n• Stories (24h auto-delete)\n• Posts with Reactions\n• Group Chats\n• Notes\n• Multiple Themes\n• Glassmorphism UI\n• Supabase Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
