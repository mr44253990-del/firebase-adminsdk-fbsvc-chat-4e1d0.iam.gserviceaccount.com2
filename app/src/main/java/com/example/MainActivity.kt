package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.example.ui.AuthScreen
import com.example.ui.ChatScreen
import com.example.call.CallScreen
import com.example.call.CallEngine
import com.example.ui.ChatViewModel
import com.example.ui.HomeScreen
import com.example.ui.GroupChatScreen
import com.example.ui.OnboardingScreen
import com.example.ui.PostComposerScreen
import com.example.ui.SplashScreen
import com.example.ui.UserProfileScreen
import com.example.ui.isOnboardingCompleted
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PremiumBackground
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.User
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure standard Coil cache so images (like profile pics) are cached aggressively offline
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10) // Use up to 10% of disk space for images
                    .build()
            }
            .respectCacheHeaders(false) // Force caching regardless of server headers so offline mode works perfectly
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            val currentTheme by viewModel.themeState.collectAsState()
            
            MyApplicationTheme(themeType = currentTheme) {
                val navController = rememberNavController()
                val activeRecipient by viewModel.activeRecipientUser.collectAsState()
                val activeGroup by viewModel.activeGroup.collectAsState()
                val selectedProfile by viewModel.selectedProfile.collectAsState()
                val callState by CallEngine.state.collectAsState()
                val context = LocalContext.current

                // Check starting destination depending on whether onboarding has been completed and if a user is already signed in
                val destinationAfterSplash = remember {
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

                PremiumBackground {
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("splash") {
                            SplashScreen {
                                navController.navigate(destinationAfterSplash) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        }

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

                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onUserSelected = { recipient ->
                                    viewModel.selectRecipient(recipient)
                                    navController.navigate("chat")
                                },
                                onProfileSelected = { user ->
                                    viewModel.selectProfile(user)
                                    navController.navigate("profile")
                                },
                                onCreatePost = { navController.navigate("compose_post") },
                                onGroupSelected = { group ->
                                    viewModel.selectGroup(group)
                                    navController.navigate("group_chat")
                                },
                                onSignOut = {
                                    navController.navigate("auth") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("compose_post") {
                            PostComposerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onPublished = { navController.popBackStack() }
                            )
                        }

                        composable("profile") {
                            selectedProfile?.let { profile ->
                                UserProfileScreen(
                                    viewModel = viewModel,
                                    user = profile,
                                    onBack = { navController.popBackStack() },
                                    onMessage = {
                                        viewModel.selectRecipient(profile)
                                        navController.navigate("chat")
                                    }
                                )
                            }
                        }

                        composable("call") {
                            CallScreen(
                                callId = callState.callId,
                                remoteUid = callState.remoteUid,
                                remoteName = callState.remoteName,
                                remoteImage = callState.remoteImage,
                                incoming = false,
                                initiallyAccepted = true,
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable("chat") {
                            activeRecipient?.let { recipient ->
                                ChatScreen(
                                    viewModel = viewModel,
                                    recipient = recipient,
                                    onBack = {
                                        navController.popBackStack()
                                    },
                                    onProfile = {
                                        viewModel.selectProfile(recipient)
                                        navController.navigate("profile")
                                    },
                                    onCall = {
                                        viewModel.startAudioCall(recipient) {
                                            navController.navigate("call")
                                        }
                                    }
                                )
                            }
                        }
                        composable("group_chat") {
                            activeGroup?.let { group ->
                                GroupChatScreen(
                                    viewModel = viewModel,
                                    group = group,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }

                // Process deep link if launched via push notification click
                LaunchedEffect(intent) {
                    val notificationType = intent?.getStringExtra("notificationType") ?: "message"
                    val senderId = intent?.getStringExtra("senderId")
                    if (notificationType != "message") {
                        viewModel.requestOpenActivityCenter()
                        navController.navigate("home") { launchSingleTop = true }
                    } else if (!senderId.isNullOrBlank()) {
                        // Fetch recipient user profile from Firestore and open chat
                        FirebaseFirestore.getInstance().collection("users")
                            .document(senderId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val recipient = document.toObject(User::class.java)
                                    if (recipient != null) {
                                        viewModel.selectRecipient(recipient)
                                        navController.navigate("chat") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
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
