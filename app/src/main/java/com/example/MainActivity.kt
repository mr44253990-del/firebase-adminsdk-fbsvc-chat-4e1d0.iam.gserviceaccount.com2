package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.call.GroupCallScreen
import com.example.ui.ChatViewModel
import com.example.ui.HomeScreen
import com.example.ui.IncomingShareHub
import com.example.ui.GroupChatScreen
import com.example.ui.OnboardingScreen
import com.example.ui.PostComposerScreen
import com.example.ui.PostDetailScreen
import com.example.ui.PremiumScreen
import com.example.ui.SplashScreen
import com.example.ui.UserProfileScreen
import com.example.ui.QrContactScreen
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
import com.example.data.IncomingSharePayload
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
    private val incomingShare = MutableStateFlow<IncomingSharePayload?>(null)
    private val pendingProfileUid = MutableStateFlow<String?>(null)
    private val pendingGroupRoomId = MutableStateFlow<String?>(null)
    private val pendingGroupId = MutableStateFlow<String?>(null)
    private val pendingGroupName = MutableStateFlow<String?>(null)
    private val pendingGroupMembers = MutableStateFlow<List<String>>(emptyList())
    private val pendingGroupVideo = MutableStateFlow(false)
    private val presenceHandler = Handler(Looper.getMainLooper())
    private val presenceHeartbeat = object : Runnable {
        override fun run() {
            writePresence(active = true, foreground = true)
            presenceHandler.postDelayed(this, 5 * 60 * 1000L)
        }
    }

    private fun writePresence(active: Boolean, foreground: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        FirebaseDatabase.getInstance().getReference("status").child(uid).updateChildren(
            mapOf(
                "isOnline" to active,
                "lastActive" to now,
                "onlineUntil" to if (active) now + 5 * 60 * 1000L else 0L,
                "offlineSince" to if (active) null else now,
                "foreground" to foreground,
                "onlineSource" to if (active) "heartbeat" else "background"
            )
        )
    }

    private fun startPresenceHeartbeat() {
        presenceHandler.removeCallbacks(presenceHeartbeat)
        writePresence(active = true, foreground = true)
        presenceHandler.postDelayed(presenceHeartbeat, 5 * 60 * 1000L)
    }

    private fun stopPresenceHeartbeat() {
        // Keep the five-minute server lease alive while the app is backgrounded.
        // Do not write an immediate offline state here; RTDB onDisconnect/lease expiry
        // is responsible for transitioning the user to offline.
        presenceHandler.removeCallbacks(presenceHeartbeat)
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.data
        pendingPostId.value = data?.takeIf { it.host == "post" || it.path?.startsWith("/post/") == true }?.pathSegments?.lastOrNull()
        pendingProfileUid.value = data?.takeIf { uri ->
            (uri.scheme.equals("convochat", true) && uri.host.equals("profile", true)) ||
                (uri.pathSegments.any { it.equals("profile", true) } && uri.getQueryParameter("uid") != null)
        }?.getQueryParameter("uid")?.takeIf { it.isNotBlank() }
    }

    private fun captureActiveGroupCall(intent: Intent?) {
        if (intent?.action != "com.ebchat.OPEN_ACTIVE_GROUP_CALL") return
        pendingGroupRoomId.value = intent.getStringExtra("roomId")?.takeIf { it.isNotBlank() }
        pendingGroupId.value = intent.getStringExtra("groupId").orEmpty()
        pendingGroupName.value = intent.getStringExtra("groupName").orEmpty()
        pendingGroupMembers.value = intent.getStringArrayListExtra("memberIds").orEmpty()
        pendingGroupVideo.value = intent.getBooleanExtra("videoCall", false)
    }
    @Suppress("DEPRECATION") private fun captureSharedContent(intent:Intent?){
        if(intent?.action!=Intent.ACTION_SEND&&intent?.action!=Intent.ACTION_SEND_MULTIPLE)return
        val uris:List<android.net.Uri> = if(intent.action==Intent.ACTION_SEND_MULTIPLE)intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM).orEmpty() else listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)
        val text=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty();if(text.isNotBlank()||uris.isNotEmpty())incomingShare.value=IncomingSharePayload(text,uris,intent.type?:"application/octet-stream")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatSenderId.value = intent.getStringExtra("senderId")?.takeIf { it.isNotBlank() }
        captureDeepLink(intent); captureActiveGroupCall(intent); captureSharedContent(intent)
    }

    override fun onStart() {
        super.onStart()
        startPresenceHeartbeat()
    }

    override fun onStop() {
        stopPresenceHeartbeat()
        VideoPlayerManager.pause()
        AppLockManager.lock(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingChatSenderId.value = intent?.getStringExtra("senderId")?.takeIf { it.isNotBlank() }
        captureDeepLink(intent); captureActiveGroupCall(intent); captureSharedContent(intent)
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
                val sharedContent by incomingShare.collectAsState()
                val linkedProfileUid by pendingProfileUid.collectAsState()
                val activeGroupRoomId by pendingGroupRoomId.collectAsState()
                val activeGroupId by pendingGroupId.collectAsState()
                val activeGroupName by pendingGroupName.collectAsState()
                val activeGroupMembers by pendingGroupMembers.collectAsState()
                val activeGroupVideo by pendingGroupVideo.collectAsState()
                val knownUsers by viewModel.usersState.collectAsState()
                val currentUser by viewModel.currentUserState.collectAsState()
                val knownPosts by viewModel.postsState.collectAsState()
                var linkedPost by remember { mutableStateOf<com.example.data.Post?>(null) }
                val activeRecipient by viewModel.activeRecipientUser.collectAsState()
                val activeGroup by viewModel.activeGroup.collectAsState()
                val selectedProfile by viewModel.selectedProfile.collectAsState()
                var requestedGroupCallVideo by remember { mutableStateOf(false) }
                var showCallParticipantPicker by remember { mutableStateOf(false) }
                val callState by CallEngine.state.collectAsState()
                val context = LocalContext.current
                var splashFinished by remember { mutableStateOf(false) }

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
                LaunchedEffect(linkedProfileUid, knownUsers, splashFinished) {
                    if (!splashFinished) return@LaunchedEffect
                    val uid = linkedProfileUid ?: return@LaunchedEffect
                    val cached = knownUsers.find { it.uid == uid }
                    if (cached != null) {
                        viewModel.selectProfile(cached)
                        navController.navigate("profile") { launchSingleTop = true }
                        pendingProfileUid.value = null
                    } else {
                        viewModel.loadUserById(uid) { profile ->
                            if (profile != null) {
                                viewModel.selectProfile(profile)
                                navController.navigate("profile") { launchSingleTop = true }
                            } else Toast.makeText(this@MainActivity, "Profile not found", Toast.LENGTH_SHORT).show()
                            pendingProfileUid.value = null
                        }
                    }
                }
                LaunchedEffect(activeGroupRoomId) {
                    if (!activeGroupRoomId.isNullOrBlank() && FirebaseAuth.getInstance().currentUser != null) {
                        viewModel.selectGroup(com.example.data.Group(
                            id = activeGroupId.orEmpty(),
                            name = activeGroupName?.ifBlank { "Convo Chat group" } ?: "Convo Chat group",
                            members = activeGroupMembers
                        ))
                        requestedGroupCallVideo = activeGroupVideo
                        navController.navigate("group_call") { launchSingleTop = true }
                    }
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
                                    splashFinished = true
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
                                onQrContacts = { navController.navigate("qr_contacts") },
                                onPremium = { navController.navigate("premium") },
                                onSignOut = {
                                    navController.navigate("auth") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("qr_contacts") {
                            QrContactScreen(
                                user = knownUsers.find { it.uid == FirebaseAuth.getInstance().currentUser?.uid } ?: currentUser,
                                onBack = { navController.popBackStack() },
                                onProfileIdScanned = { uid ->
                                    val cached = knownUsers.find { it.uid == uid }
                                    if (cached != null) {
                                        viewModel.selectProfile(cached)
                                        navController.navigate("profile")
                                    } else {
                                        viewModel.loadUserById(uid) { profile ->
                                            if (profile != null) {
                                                viewModel.selectProfile(profile)
                                                navController.navigate("profile")
                                            } else {
                                                Toast.makeText(this@MainActivity, "Profile not found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
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
                                onClose = { navController.popBackStack() },
                                onAddParticipant = { showCallParticipantPicker = true }
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
                                    onBack = { navController.popBackStack() },
                                    onGroupCall = { video ->
                                        requestedGroupCallVideo = video
                                        navController.navigate("group_call")
                                    }
                                )
                            }
                        }
                        composable("group_call") {
                            activeGroup?.let { group ->
                                GroupCallScreen(
                                    group = group,
                                    video = requestedGroupCallVideo,
                                    onClose = { navController.popBackStack() },
                                    joinRoomId = activeGroupRoomId,
                                    onRoomReady = { roomId ->
                                        viewModel.inviteGroupMembersToCall(group, requestedGroupCallVideo, roomId)
                                        if (activeGroupRoomId == roomId) pendingGroupRoomId.value = null
                                    }
                                )
                            }
                        }
                    }
                    if (showCallParticipantPicker && currentRoute == "call") {
                        CallParticipantPickerDialog(
                            users = knownUsers,
                            excludedIds = setOfNotNull(FirebaseAuth.getInstance().currentUser?.uid, activeRecipient?.uid),
                            onDismiss = { showCallParticipantPicker = false },
                            onSelect = { participant ->
                                showCallParticipantPicker = false
                                val firstParticipant = activeRecipient
                                if (firstParticipant != null) {
                                    CallEngine.end()
                                    viewModel.createGroup(
                                        name = "Call with ${firstParticipant.name.ifBlank { "contact" }} & ${participant.name.ifBlank { "contact" }}",
                                        profileUrl = participant.profileImageUrl,
                                        members = listOf(firstParticipant.uid, participant.uid)
                                    ) { createdGroup ->
                                        viewModel.selectGroup(createdGroup)
                                        requestedGroupCallVideo = callState.video
                                        navController.navigate("group_call") {
                                            popUpTo("call") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                    if(FirebaseAuth.getInstance().currentUser!=null) sharedContent?.let { payload -> IncomingShareHub(payload,viewModel,onCreatePost={navController.navigate("compose_post")},onDismiss={incomingShare.value=null}) }
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


@Composable
private fun CallParticipantPickerDialog(
    users: List<User>,
    excludedIds: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (User) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val candidates = remember(users, excludedIds, query) {
        users.filter { user ->
            user.uid !in excludedIds &&
                (query.isBlank() || user.name.contains(query, ignoreCase = true) || user.username.contains(query, ignoreCase = true))
        }.take(30)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add participant", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose someone to move this call into a group call.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search people") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (candidates.isEmpty()) {
                    Text("No matching users found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(candidates, key = { it.uid }) { user ->
                            TextButton(onClick = { onSelect(user) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Text(user.name.ifBlank { "Convo Chat user" }, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    if (user.username.isNotBlank()) Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
