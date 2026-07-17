package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.User
import com.example.ui.*
import com.example.ui.components.CountBadge
import com.example.ui.theme.GlassBorderSoft
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.LocalPalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private val feedViewModel: FeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var themeId by remember { mutableStateOf(viewModel.getSavedTheme()) }
            val currentUser by viewModel.currentUserState.collectAsState()

            // follow the theme synced from Firestore (e.g. after login)
            LaunchedEffect(currentUser?.theme) {
                currentUser?.theme?.let { if (it.isNotBlank() && it != themeId) themeId = it }
            }

            MyApplicationTheme(paletteId = themeId) {
                val navController = rememberNavController()
                val activeRecipient by viewModel.activeRecipientUser.collectAsState()
                val activeGroup by viewModel.activeGroup.collectAsState()
                val context = LocalContext.current

                val startDestination = remember {
                    try {
                        if (!isOnboardingCompleted(context)) "onboarding"
                        else if (FirebaseAuth.getInstance().currentUser != null) "main"
                        else "auth"
                    } catch (e: Exception) { "auth" }
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinished = {
                                val dest = try {
                                    if (FirebaseAuth.getInstance().currentUser != null) "main" else "auth"
                                } catch (e: Exception) { "auth" }
                                navController.navigate(dest) { popUpTo("onboarding") { inclusive = true } }
                            }
                        )
                    }

                    composable("auth") {
                        AuthScreen(
                            viewModel = viewModel,
                            onAuthSuccess = {
                                feedViewModel.start()
                                navController.navigate("main") { popUpTo("auth") { inclusive = true } }
                            }
                        )
                    }

                    composable("main") {
                        MainTabs(
                            viewModel = viewModel,
                            feedViewModel = feedViewModel,
                            currentThemeId = themeId,
                            onThemeChange = { themeId = it },
                            onOpenChat = { user ->
                                viewModel.selectRecipient(user)
                                viewModel.selectGroup(null)
                                navController.navigate("chat")
                            },
                            onOpenGroup = { group ->
                                viewModel.selectGroup(group)
                                viewModel.selectRecipient(null)
                                navController.navigate("chat")
                            },
                            onOpenProfile = { uid -> navController.navigate("profile/$uid") },
                            onOpenNotifications = { navController.navigate("notifications") },
                            onOpenNotes = { navController.navigate("notes") },
                            onSignOut = {
                                navController.navigate("auth") { popUpTo("main") { inclusive = true } }
                            }
                        )
                    }

                    composable("chat") {
                        val recipient = activeRecipient
                        val group = activeGroup
                        if (recipient == null && group == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            ChatScreen(
                                viewModel = viewModel,
                                recipient = recipient,
                                group = group,
                                onBack = {
                                    viewModel.selectRecipient(null)
                                    viewModel.selectGroup(null)
                                    navController.popBackStack()
                                },
                                onOpenProfile = { uid -> navController.navigate("profile/$uid") }
                            )
                        }
                    }

                    composable(
                        "profile/{uid}",
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })
                    ) { entry ->
                        val uid = entry.arguments?.getString("uid") ?: ""
                        ProfileScreen(
                            viewModel = viewModel,
                            feedViewModel = feedViewModel,
                            userId = uid,
                            onBack = { navController.popBackStack() },
                            onMessage = { user ->
                                viewModel.selectRecipient(user)
                                viewModel.selectGroup(null)
                                navController.navigate("chat")
                            }
                        )
                    }

                    composable("notifications") {
                        NotificationsScreen(
                            feedViewModel = feedViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("notes") {
                        NotesScreen(
                            feedViewModel = feedViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Push-notification deep link -> straight into the conversation
                LaunchedEffect(intent) {
                    val senderId = intent?.getStringExtra("senderId")
                    if (!senderId.isNullOrBlank()) {
                        try {
                            FirebaseFirestore.getInstance().collection("users")
                                .document(senderId).get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        val recipient = User(
                                            uid = document.getString("uid") ?: document.id,
                                            name = document.getString("name") ?: "",
                                            username = document.getString("username") ?: "",
                                            photoUrl = document.getString("photoUrl") ?: ""
                                        )
                                        viewModel.selectRecipient(recipient)
                                        navController.navigate("chat") { launchSingleTop = true }
                                    }
                                }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}

/** Root scaffold — glass tab host with a floating navigation bar + unread badges. */
@Composable
fun MainTabs(
    viewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    currentThemeId: String,
    onThemeChange: (String) -> Unit,
    onOpenChat: (User) -> Unit,
    onOpenGroup: (com.example.data.Group) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNotes: () -> Unit,
    onSignOut: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("home") }
    val totalUnread by viewModel.totalUnread.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()

    LaunchedEffect(Unit) { feedViewModel.start() }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            "home" -> HomeScreen(
                viewModel = viewModel,
                feedViewModel = feedViewModel,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications,
                onOpenNotes = onOpenNotes
            )
            "chats" -> ChatListScreen(
                viewModel = viewModel,
                onOpenChat = onOpenChat,
                onOpenGroup = onOpenGroup
            )
            "profile" -> ProfileScreen(
                viewModel = viewModel,
                feedViewModel = feedViewModel,
                userId = currentUser?.uid ?: "",
                onBack = { tab = "home" },
                onMessage = onOpenChat
            )
            "settings" -> SettingsScreen(
                viewModel = viewModel,
                currentThemeId = currentThemeId,
                onThemeChange = onThemeChange,
                onSignOut = onSignOut
            )
        }

        // Floating glass navigation bar
        GlassNavBar(
            tab = tab,
            unreadMessages = totalUnread,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        )
    }
}

@Composable
fun GlassNavBar(
    tab: String,
    unreadMessages: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val items = listOf(
        Triple("home", Icons.Default.Home, "Home"),
        Triple("chats", Icons.Default.Send, "Chats"),
        Triple("profile", Icons.Default.Person, "Profile"),
        Triple("settings", Icons.Default.Settings, "Settings")
    )

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.07f))
                )
            )
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(30.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (id, icon, label) ->
            val selected = tab == id
            val scale by animateFloatAsState(
                if (selected) 1.08f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "nav"
            )
            Column(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) palette.primary.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) palette.primary else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (id == "chats") {
                        CountBadge(
                            count = unreadMessages,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp)
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) palette.primary else TextSecondary
                )
            }
        }
    }
}
