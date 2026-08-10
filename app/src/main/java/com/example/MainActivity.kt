package com.example

import android.os.Bundle
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.platform.LocalContext
import com.example.ui.AuthScreen
import com.example.ui.AssistantScreen
import com.example.ui.ChatScreen
import com.example.call.CallScreen
import com.example.call.CallEngine
import com.example.call.CallMiniOverlay
import com.example.ui.ChatViewModel
import com.example.ui.HomeScreen
import com.example.ui.GroupChatScreen
import com.example.ui.OnboardingScreen
import com.example.ui.PostComposerScreen
import com.example.ui.PostDetailScreen
import com.example.ui.PremiumScreen
import com.example.ui.SplashScreen
import com.example.ui.UserProfileScreen
import com.example.ui.isOnboardingCompleted
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PremiumBackground
import com.example.video.VideoPlayerManager
import com.example.security.AppLockManager
import com.example.security.AppLockScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.User
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.flow.MutableStateFlow

@androidx.media3.common.util.UnstableApi
class MainActivity : FragmentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private val pendingChatSenderId = MutableStateFlow<String?>(null)
    private val pendingPostId = MutableStateFlow<String?>(null)
    private fun captureDeepLink(intent: Intent?) { pendingPostId.value = intent?.data?.takeIf { it.host == "post" || it.path?.startsWith("/post/") == true }?.pathSegments?.lastOrNull() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatSenderId.value = intent.getStringExtra("senderId")?.takeIf { it.isNotBlank() }
        captureDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().getReference("status").child(uid)
                .setValue(mapOf("isOnline" to true, "lastActive" to System.currentTimeMillis(), "foreground" to true, "onlineSource" to "foreground"))
        }
    }

    override fun onStop() {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().getReference("status").child(uid)
                .setValue(mapOf("isOnline" to false, "lastActive" to System.currentTimeMillis(), "foreground" to false, "onlineSource" to "background"))
        }
        VideoPlayerManager.pause()
        AppLockManager.lock(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingChatSenderId.value = intent?.getStringExtra("senderId")?.takeIf { it.isNotBlank() }
        captureDeepLink(intent)
        AppLockManager.initialize(this)
        enableEdgeToEdge()

        // Configure standard Coil cache so images (like profile pics) are cached aggressively offline
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Avoid letting image cache consume a quarter of RAM on
                    // entry-level devices while keeping recent avatars warm.
                    .maxSizePercent(0.15)
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
                val appLocked by AppLockManager.locked.collectAsState()
                if (appLocked) {
                    AppLockScreen(this@MainActivity)
                } else {
                val navController = rememberNavController()
                val pendingSenderId by pendingChatSenderId.collectAsState()
                val linkedPostId by pendingPostId.collectAsState()
                val knownUsers by viewModel.usersState.collectAsState()
                val knownPosts by viewModel.postsState.collectAsState()
                var linkedPost by remember { mutableStateOf<com.example.data.Post?>(null) }
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

                LaunchedEffect(pendingSenderId, knownUsers) {
                    val senderId = pendingSenderId ?: return@LaunchedEffect
                    knownUsers.find { it.uid == senderId }?.let { sender ->
                        viewModel.selectRecipient(sender)
                        navController.navigate("chat") { launchSingleTop = true }
                        pendingChatSenderId.value = null
                    }
                }
                LaunchedEffect(linkedPostId, knownPosts) {
                    val id = linkedPostId ?: return@LaunchedEffect
                    knownPosts.find { it.id == id }?.let { post -> linkedPost = post; navController.navigate("post_detail") { launchSingleTop = true }; pendingPostId.value = null }
                }

                PremiumBackground {
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    Box(Modifier.fillMaxSize()) {
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
                                onAssistant = { navController.navigate("assistant") },
                                onPremium = { navController.navigate("premium") },
                                onSignOut = {
                                    navController.navigate("auth") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("premium") {
                            PremiumScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onChatAdmin = { admin ->
                                    viewModel.selectRecipient(admin)
                                    navController.navigate("chat")
                                }
                            )
                        }

                        composable("assistant") {
                            AssistantScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenUser = { user ->
                                    viewModel.selectRecipient(user)
                                    navController.navigate("chat")
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

                        composable("post_detail") {
                            linkedPost?.let { post -> PostDetailScreen(post, viewModel, onBack = { navController.popBackStack() }, onProfile = { user -> viewModel.selectProfile(user); navController.navigate("profile") }) }
                        }

                        composable(
                            route = "profile",
                            enterTransition = { slideInHorizontally(tween(360)) { it } + fadeIn(tween(300)) },
                            exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(240)) },
                            popEnterTransition = { slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(260)) },
                            popExitTransition = { slideOutHorizontally(tween(340)) { it } + fadeOut(tween(260)) }
                        ) {
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
                                video = callState.video,
                                initiallyAccepted = true,
                                onEndCall = {
                                    activeRecipient?.let { viewModel.endCall(it, callState.callId) } ?: CallEngine.end()
                                },
                                onMinimize = { navController.popBackStack() },
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
                                        viewModel.startAudioCall(recipient) { navController.navigate("call") }
                                    },
                                    onVideoCall = {
                                        viewModel.startVideoCall(recipient) { navController.navigate("call") }
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
                    if (currentRoute != "call" && callState.status !in listOf("idle", "ended", "declined", "missed", "failed")) {
                        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                            CallMiniOverlay(
                                state = callState,
                                onExpand = { navController.navigate("call") { launchSingleTop = true } },
                                onEnd = { activeRecipient?.let { viewModel.endCall(it, callState.callId) } ?: CallEngine.end() }
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
}
