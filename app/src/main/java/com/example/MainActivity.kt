package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.example.ui.*
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsState()
            val selectedAccentColor by viewModel.selectedAccentColor.collectAsState()
            
            val isDarkTheme = when (selectedTheme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            val accentColor = try {
                androidx.compose.ui.graphics.Color(
                    android.graphics.Color.parseColor(selectedAccentColor)
                )
            } catch (e: Exception) {
                ElectricPurple
            }

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                accentColor = accentColor
            ) {
                val navController = rememberNavController()
                val activeRecipient by viewModel.activeRecipientUser.collectAsState()
                val context = LocalContext.current

                // Determine start destination
                val startDestination = remember {
                    try {
                        if (!isOnboardingCompleted(context)) {
                            "onboarding"
                        } else if (FirebaseAuth.getInstance().currentUser != null) {
                            "home"
                        } else {
                            "auth"
                        }
                    } catch (e: Exception) {
                        "auth"
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Onboarding Screen
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinished = {
                                val dest = try {
                                    if (FirebaseAuth.getInstance().currentUser != null) "home" else "auth"
                                } catch (e: Exception) {
                                    "auth"
                                }
                                navController.navigate(dest) {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Auth Screen
                    composable("auth") {
                        AuthScreen(
                            viewModel = viewModel,
                            onAuthSuccess = {
                                navController.navigate("home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Home Screen
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onUserSelected = { recipient ->
                                viewModel.selectRecipient(recipient)
                                navController.navigate("chat")
                            },
                            onSignOut = {
                                navController.navigate("auth") {
                                    popUpTo("home") { inclusive = true }
                                }
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }

                    // Chat Screen
                    composable("chat") {
                        activeRecipient?.let { recipient ->
                            ChatScreen(
                                viewModel = viewModel,
                                recipient = recipient,
                                onBack = {
                                    viewModel.selectRecipient(null)
                                    navController.popBackStack()
                                }
                            )
                        } ?: run {
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        }
                    }

                    // Settings Screen
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // Stories Full Screen
                    composable("stories") {
                        StoriesFullScreen(
                            viewModel = viewModel,
                            onClose = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                // Handle deep link from push notification
                LaunchedEffect(intent) {
                    val senderId = intent?.getStringExtra("senderId")
                    if (!senderId.isNullOrBlank()) {
                        try {
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(senderId)
                                .get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        val recipient = document.toObject(com.example.data.User::class.java)
                                        if (recipient != null) {
                                            viewModel.selectRecipient(recipient)
                                            navController.navigate("chat") {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                        } catch (e: Exception) {
                            // Handle error silently
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update online status when app is opened
        viewModel.updateOnlineStatus(true)
    }

    override fun onPause() {
        super.onPause()
        // Keep online status for a while in case of accidental close
        // Status will be updated to offline after timeout
    }

    override fun onDestroy() {
        super.onDestroy()
        // Update offline status when app is completely closed
        viewModel.updateOnlineStatus(false)
    }
}

@Composable
fun StoriesFullScreen(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val stories by viewModel.storiesState.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    
    var currentStoryIndex by remember { mutableStateOf(0) }
    var showReactions by remember { mutableStateOf(false) }
    
    if (stories.isNotEmpty()) {
        val currentStory = stories[currentStoryIndex]
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .androidx.compose.ui.graphics.Color.Black
        ) {
            // Story Image
            coil.compose.AsyncImage(
                model = currentStory.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            // Top gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .androidx.compose.foundation.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                                androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    )
            )
            
            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .androidx.compose.foundation.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            
            // Progress bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    ..padding(horizontal = 8.dp, vertical = 32.dp)
                    .align(androidx.compose.ui.Alignment.TopCenter)
            ) {
                stories.take(5).forEachIndexed { index, _ ->
                    LinearProgressIndicator(
                        progress = { if (index < currentStoryIndex) 1f else if (index == currentStoryIndex) 0.5f else 0f },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 2.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            
            // User info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .padding(top = 48.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                // Avatar
                androidx.compose.foundation.shape.CircleShape.let {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .androidx.compose.ui.draw.clip(it)
                            .androidx.compose.foundation.background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(ElectricPurple, CyanGlow)
                                )
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        if (currentStory.userProfileUrl.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = currentStory.userProfileUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentStory.userName.take(1).uppercase(),
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = currentStory.userName,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = viewModel.getTimeAgo(currentStory.createdAt),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
            
            // Caption
            if (currentStory.caption.isNotBlank()) {
                Text(
                    text = currentStory.caption,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(bottom = 80.dp)
                )
            }
            
            // Views count
            Row(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Visibility,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${currentStory.views.size} views",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Reaction button
            IconButton(
                onClick = { showReactions = !showReactions },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                val userReaction = currentStory.reactions[currentUserId]
                val emoji = if (userReaction != null) {
                    com.example.data.ReactionTypes.emojiMap[userReaction]
                } else {
                    "❤️"
                }
                Text(text = emoji ?: "❤️", fontSize = 24.sp)
            }
            
            // Reactions picker
            AnimatedVisibility(
                visible = showReactions,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = DarkSurface.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.data.ReactionTypes.all.forEach { reaction ->
                            Text(
                                text = com.example.data.ReactionTypes.emojiMap[reaction] ?: "",
                                fontSize = 32.sp,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.reactToStory(currentStory.storyId, reaction)
                                        showReactions = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
            
            // Swipe handlers
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGesture { _, dragAmount ->
                            if (dragAmount > 50 && currentStoryIndex > 0) {
                                currentStoryIndex--
                            } else if (dragAmount < -50 && currentStoryIndex < stories.size - 1) {
                                currentStoryIndex++
                            }
                        }
                    }
            )
        }
    } else {
        // No stories
        Box(
            modifier = Modifier
                .fillMaxSize()
                .androidx.compose.ui.graphics.Color.Black,
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No stories yet",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClose) {
                    Text("Go Back")
                }
            }
        }
    }
}
