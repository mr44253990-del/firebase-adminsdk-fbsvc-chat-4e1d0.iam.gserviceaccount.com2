package com.example

import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.util.Rational
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.example.call.GroupCallEngine
import com.example.ui.ChatViewModel
import com.example.ui.CallHistory
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
import com.example.ui.QrDeviceLoginScreen
import com.example.ui.isOnboardingCompleted
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PremiumBackground
import com.example.video.VideoPlayerManager
import com.example.security.AppLockManager
import com.example.security.AppLockScreen
import com.example.security.PrivacyPreferences
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
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@androidx.media3.common.util.UnstableApi
class MainActivity : FragmentActivity() {

    private fun callIsActive(): Boolean =
        CallEngine.state.value.status !in listOf("idle", "ended", "declined", "missed", "failed")

    /** Locking the phone during a 1:1 call brings the full call screen above the lock screen. */
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF && callIsActive() && !com.example.call.IncomingCallActivity.alive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                pendingOpenCall.value = true
            }
        }
    }

    /** Leaving the call screen while the keyguard is up must not expose the app over the lock screen. */
    private fun leaveCallScreen(navigateBack: () -> Unit) {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguard?.isKeyguardLocked == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { navigateBack() }
            })
        } else navigateBack()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    /** True only while a 1:1 or group VIDEO call is live: the only case that may use Picture-in-Picture. */
    private fun liveVideoCall(): Boolean {
        val p = CallEngine.state.value
        val privateActive = p.status !in listOf("idle", "ended", "declined", "missed", "failed")
        if (privateActive) return p.video
        val g = GroupCallEngine.state.value
        return g.status !in listOf("idle", "ended", "failed") && g.video
    }

    private fun minimizeActiveCall() {
        val privateActive = CallEngine.state.value.status !in listOf("idle", "ended", "declined", "missed", "failed")
        // Audio calls never use PiP: they keep running with the in-app bubble / ongoing notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && liveVideoCall()) {
            if (privateActive) pendingOpenCall.value = true
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build())
                watchCallEndWhileInPip()
            }.onFailure { onBackPressedDispatcher.onBackPressed() }
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.example.call.CallPipState.set(isInPictureInPictureMode)
        // Closed / expanded while the call already ended: never leave a frozen frame behind.
        if (!isInPictureInPictureMode) pipCallWatcher?.cancel()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only a CONNECTED video call auto-enters PiP. Ringing calls, audio calls and in-app
        // activity switches (pickers, the incoming-call screen) never do.
        val connected = CallEngine.state.value.status in listOf("connected", "reconnecting") || GroupCallEngine.state.value.status in listOf("connected", "active", "reconnecting")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode && connected && liveVideoCall()) {
            minimizeActiveCall()
        }
    }

    private var pipCallWatcher: kotlinx.coroutines.Job? = null

    /** When the call ends (either side hangs up) while in PiP, close the PiP window instead of freezing it. */
    private fun watchCallEndWhileInPip() {
        pipCallWatcher?.cancel()
        pipCallWatcher = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(CallEngine.state, GroupCallEngine.state) { p, g ->
                p.status in listOf("idle", "ended", "declined", "missed", "failed") && g.status in listOf("idle", "ended", "failed")
            }.collect { over ->
                if (over && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                    kotlinx.coroutines.delay(600)
                    com.example.call.CallPipState.set(false)
                    moveTaskToBack(true)
                }
            }
        }
    }

    private val viewModel: ChatViewModel by viewModels()
    private val pendingChatSenderId = MutableStateFlow<String?>(null)
    private val pendingPostId = MutableStateFlow<String?>(null)
    private val incomingShare = MutableStateFlow<IncomingSharePayload?>(null)
    private val pendingProfileUid = MutableStateFlow<String?>(null)
    private val pendingGroupRoomId = MutableStateFlow<String?>(null)
    private val pendingOpenCall = MutableStateFlow(false)
    private val pendingGroupId = MutableStateFlow<String?>(null)
    private val pendingGroupName = MutableStateFlow<String?>(null)
    private val pendingGroupMembers = MutableStateFlow<List<String>>(emptyList())
    private val pendingGroupVideo = MutableStateFlow(false)
    private val presenceHandler = Handler(Looper.getMainLooper())
    private val presenceHeartbeat = object : Runnable {
        override fun run() {
            writePresence(active = true, foreground = true)
            viewModel.syncPresenceToWorker(active = true, foreground = true)
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
                "onlineUntil" to if (active) now + com.example.service.PRESENCE_LEASE_MS else 0L,
                "offlineSince" to if (active) 0L else now,
                "foreground" to foreground,
                "onlineSource" to if (active) "heartbeat" else "background"
            )
        )
    }

    private fun startPresenceHeartbeat() {
        presenceHandler.removeCallbacks(presenceHeartbeat)
        writePresence(active = true, foreground = true)
        viewModel.syncPresenceToWorker(active = true, foreground = true)
        presenceHandler.postDelayed(presenceHeartbeat, 5 * 60 * 1000L)
    }

    private fun stopPresenceHeartbeat() {
        // Mark the activity as background but keep a short lease. The Worker/FCM
        // probe can renew this lease every five minutes; if the device is not
        // reachable, RTDB lease expiry and onDisconnect transition it offline.
        presenceHandler.removeCallbacks(presenceHeartbeat)
        writePresence(active = true, foreground = false)
        viewModel.syncPresenceToWorker(active = true, foreground = false)
    }

    private val pendingPairCode = MutableStateFlow<String?>(null)
    private val pendingGroupChatId = MutableStateFlow<String?>(null)
    private val pendingActivityCenter = MutableStateFlow(false)

    /** A push for another saved account on this device: switch first, then open the chat / call. */
    data class SwitchAccountRequest(val accountUid: String, val openChatWith: String? = null, val callIntent: Intent? = null)
    private val pendingSwitchAccount = MutableStateFlow<SwitchAccountRequest?>(null)

    /** Reads a notification tap (or launcher start) and queues the screen that should open. */
    private fun captureNotificationTap(intent: Intent?) {
        intent ?: return
        val senderId = intent.getStringExtra("senderId")?.takeIf { it.isNotBlank() }
        val type = intent.getStringExtra("notificationType")
        val targetId = intent.getStringExtra("targetId")?.takeIf { it.isNotBlank() }
        val switchUid = intent.getStringExtra("switchAccountUid")?.takeIf { it.isNotBlank() }
        if (switchUid != null) {
            val callIntent = if (intent.getBooleanExtra("switchForCall", false)) {
                val group = intent.getBooleanExtra("switchGroupCall", false)
                Intent(this, if (group) com.example.call.IncomingGroupCallActivity::class.java else com.example.call.IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    intent.extras?.let { putExtras(it) }
                    removeExtra("switchAccountUid")
                }
            } else null
            pendingSwitchAccount.value = SwitchAccountRequest(switchUid, senderId.takeIf { callIntent == null && (type == null || type == "message") }, callIntent)
            runCatching { (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(intent.getIntExtra("switchNotificationId", -1)) }
            // Consume it: a rotation / activity re-creation must not switch accounts again.
            intent.removeExtra("switchAccountUid")
            return
        }
        when {
            type == null && senderId != null -> pendingChatSenderId.value = senderId
            type == "message" || type == "online_alert" -> if (senderId != null) pendingChatSenderId.value = senderId
            type == "group_message" && targetId != null -> pendingGroupChatId.value = targetId
            type != null -> pendingActivityCenter.value = true
        }
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.data
        pendingPostId.value = data?.takeIf { it.host == "post" || it.path?.startsWith("/post/") == true }?.pathSegments?.lastOrNull()
        pendingProfileUid.value = data?.takeIf { uri ->
            (uri.scheme.equals("convochat", true) && uri.host.equals("profile", true)) ||
                (uri.pathSegments.any { it.equals("profile", true) } && uri.getQueryParameter("uid") != null)
        }?.getQueryParameter("uid")?.takeIf { it.isNotBlank() }
        // QR login: convochat://pair?code=XXXXXXXXXX  → open the device-login flow.
        pendingPairCode.value = data?.takeIf { uri ->
            uri.scheme.equals("convochat", true) && uri.host.equals("pair", true)
        }?.getQueryParameter("code")?.takeIf { it.isNotBlank() }
    }

    private fun captureActiveGroupCall(intent: Intent?) {
        if (intent?.action == "com.ebchat.OPEN_ACTIVE_CALL") { pendingOpenCall.value = true; return }
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
        captureNotificationTap(intent)
        com.example.ui.ProTools.handleAction(intent.getStringExtra("convoAction"))
        captureDeepLink(intent); captureActiveGroupCall(intent); captureSharedContent(intent)
    }

    override fun onStart() {
        super.onStart()
        com.example.service.ActiveChatState.appVisible = true
        startPresenceHeartbeat()
    }

    override fun onStop() {
        com.example.service.ActiveChatState.appVisible = false
        stopPresenceHeartbeat()
        // Keep the uninstall-surviving encrypted text mirror current whenever the
        // activity leaves the foreground. Media payloads are excluded by the manager.
        viewModel.exportTextBackup()
        VideoPlayerManager.pause()
        AppLockManager.lock(this)
        com.example.ui.ChatLockSession.relockAll()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.ui.CrashGuard.install(this)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        captureNotificationTap(intent)
        com.example.ui.ProTools.handleAction(intent?.getStringExtra("convoAction"))
        com.example.ui.CallHistory.ensureStarted(this)
        com.example.ui.ProShortcuts.publish(this)
        captureDeepLink(intent); captureActiveGroupCall(intent); captureSharedContent(intent)
        AppLockManager.initialize(this)
        PrivacyPreferences.initialize(this)
        androidx.core.content.ContextCompat.registerReceiver(this, screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
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
                val openCallRequest by pendingOpenCall.collectAsState()
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
                /** Room to join when the group-call screen opens; null starts a brand-new call. */
                var groupCallJoinRoomId by remember { mutableStateOf<String?>(null) }
                var showCallParticipantPicker by remember { mutableStateOf(false) }
                val callState by CallEngine.state.collectAsState()
                val groupCallState by GroupCallEngine.state.collectAsState()
                val context = LocalContext.current
                LaunchedEffect(currentUser?.uid) {
                    if (!currentUser?.uid.isNullOrBlank()) CallHistory.ensureStarted(context)
                }
                LaunchedEffect(currentUser?.name, currentUser?.profileImageUrl) {
                    GroupCallEngine.localName = currentUser?.name.orEmpty()
                    GroupCallEngine.localImage = currentUser?.profileImageUrl.orEmpty()
                }
                var splashFinished by remember { mutableStateOf(false) }
                var pendingMfaStatus by remember { mutableStateOf<com.example.ui.MfaStatus?>(null) }
                var showOverlayPermissionPrompt by remember { mutableStateOf(false) }
                var overlayPromptShownThisSession by remember { mutableStateOf(false) }
                LaunchedEffect(splashFinished, currentUser?.uid, callState.status, groupCallState.status) {
                    val activeCall = callState.status !in listOf("idle", "ended", "declined", "missed", "failed") ||
                        groupCallState.status !in listOf("idle", "ended", "failed")
                    if (!overlayPromptShownThisSession && splashFinished && currentUser != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context) && activeCall) {
                        overlayPromptShownThisSession = true
                        showOverlayPermissionPrompt = true
                    }
                }

                // Check starting destination depending on whether onboarding has been completed and if a user is already signed in
                val destinationAfterSplash = remember {
                    try {
                        com.example.ui.AppLanguage.load(context)
                        if (!com.example.ui.AppLanguage.hasChosen(context)) {
                            "language"
                        } else if (!isOnboardingCompleted(context)) {
                            "onboarding"
                        } else if (FirebaseAuth.getInstance().currentUser != null) {
                            "session_gate"
                        } else {
                            "auth"
                        }
                    } catch (e: Exception) {
                        "auth"
                    }
                }

                // Deep links and notification taps are only executed once the user is really inside
                // the app (past splash / sign-in / 2FA). Navigating earlier used to be undone by the
                // splash "popUpTo", which dropped the user on Home instead of the chat or profile.
                val navEntryForLinks by navController.currentBackStackEntryAsState()
                val routeForLinks = navEntryForLinks?.destination?.route
                val appReady = splashFinished && (currentUser != null || FirebaseAuth.getInstance().currentUser != null) &&
                    routeForLinks != null && routeForLinks !in setOf("splash", "language", "onboarding", "auth", "session_gate", "mfa")
                val knownGroups by viewModel.groupsState.collectAsState()
                val pendingGroupChat by pendingGroupChatId.collectAsState()
                val pendingActivity by pendingActivityCenter.collectAsState()
                val pendingSwitch by pendingSwitchAccount.collectAsState()

                LaunchedEffect(pendingSenderId, appReady) {
                    val senderId = pendingSenderId ?: return@LaunchedEffect
                    if (!appReady) return@LaunchedEffect
                    val open: (User) -> Unit = { sender ->
                        viewModel.selectRecipient(sender)
                        if (navController.currentBackStackEntry?.destination?.route == "chat") navController.popBackStack()
                        navController.navigate("chat") { launchSingleTop = true }
                    }
                    pendingChatSenderId.value = null
                    val cached = knownUsers.find { it.uid == senderId }
                    if (cached != null) open(cached)
                    else viewModel.loadUserById(senderId) { loaded -> if (loaded != null) open(loaded) }
                }
                LaunchedEffect(pendingGroupChat, appReady, knownGroups) {
                    val groupId = pendingGroupChat ?: return@LaunchedEffect
                    if (!appReady) return@LaunchedEffect
                    val group = knownGroups.find { it.id == groupId }
                    if (group != null) {
                        pendingGroupChatId.value = null
                        viewModel.selectGroup(group)
                        navController.navigate("group_chat") { launchSingleTop = true }
                    } else {
                        // Group list not loaded yet: wait briefly, then fall back to the activity centre.
                        kotlinx.coroutines.delay(6000)
                        if (pendingGroupChatId.value == groupId) { pendingGroupChatId.value = null; viewModel.requestOpenActivityCenter() }
                    }
                }
                LaunchedEffect(pendingActivity, appReady) {
                    if (!pendingActivity || !appReady) return@LaunchedEffect
                    pendingActivityCenter.value = false
                    viewModel.requestOpenActivityCenter()
                    navController.navigate("home") { launchSingleTop = true; popUpTo("home") { inclusive = false } }
                }
                // "Switch account" notification: another saved account on this phone got a message/call.
                LaunchedEffect(pendingSwitch, splashFinished) {
                    val request = pendingSwitch ?: return@LaunchedEffect
                    if (!splashFinished) return@LaunchedEffect
                    pendingSwitchAccount.value = null
                    if (FirebaseAuth.getInstance().currentUser?.uid == request.accountUid) {
                        request.openChatWith?.let { pendingChatSenderId.value = it }
                        return@LaunchedEffect
                    }
                    viewModel.switchToRememberedAccount(request.accountUid) { ok ->
                        if (ok) {
                            Toast.makeText(this@MainActivity, "Switched account", Toast.LENGTH_SHORT).show()
                            // Through the session gate, so an account with two-step verification is still checked.
                            navController.navigate("session_gate") { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                            request.callIntent?.let { call -> runCatching { startActivity(call) } }
                            request.openChatWith?.let { pendingChatSenderId.value = it }
                        } else {
                            Toast.makeText(this@MainActivity, "Sign in to that account once to enable one-tap switching", Toast.LENGTH_LONG).show()
                            viewModel.logout { navController.navigate("auth") { popUpTo(0) { inclusive = true } } }
                        }
                    }
                }
                LaunchedEffect(linkedPostId, knownPosts, appReady) {
                    val id = linkedPostId ?: return@LaunchedEffect
                    if (!appReady) return@LaunchedEffect
                    knownPosts.find { it.id == id }?.let { post -> linkedPost = post; navController.navigate("post_detail") { launchSingleTop = true }; pendingPostId.value = null }
                }
                LaunchedEffect(linkedProfileUid, appReady) {
                    if (!appReady) return@LaunchedEffect
                    val uid = linkedProfileUid ?: return@LaunchedEffect
                    pendingProfileUid.value = null
                    val showProfile: (User) -> Unit = { profile ->
                        viewModel.selectProfile(profile)
                        if (navController.currentBackStackEntry?.destination?.route == "profile") navController.popBackStack()
                        navController.navigate("profile") { launchSingleTop = true }
                    }
                    val self = currentUser?.takeIf { it.uid == uid }
                    val cached = self ?: knownUsers.find { it.uid == uid }
                    if (cached != null) showProfile(cached)
                    else viewModel.loadUserById(uid) { profile ->
                        if (profile != null) showProfile(profile)
                        else Toast.makeText(this@MainActivity, "Profile not found", Toast.LENGTH_SHORT).show()
                    }
                }
                // QR login deep link: convochat://pair?code=XXXXXXXXXX
                LaunchedEffect(pendingPairCode.collectAsState().value, appReady) {
                    val code = pendingPairCode.value ?: return@LaunchedEffect
                    if (!appReady) return@LaunchedEffect
                    pendingPairCode.value = null
                    viewModel.setPendingQrPairCode(code)
                    navController.navigate("device_login") { launchSingleTop = true }
                }
                LaunchedEffect(activeGroupRoomId, appReady) {
                    if (appReady && !activeGroupRoomId.isNullOrBlank() && FirebaseAuth.getInstance().currentUser != null) {
                        viewModel.selectGroup(com.example.data.Group(
                            id = activeGroupId.orEmpty(),
                            name = activeGroupName?.ifBlank { "Convo Chat group" } ?: "Convo Chat group",
                            members = activeGroupMembers
                        ))
                        requestedGroupCallVideo = activeGroupVideo
                        // Incoming calls stay visible as a Join Call banner in the group chat.
                        // They must never auto-start the WebRTC engine or force navigation.
                        if (navController.currentBackStackEntry?.destination?.route != "group_chat") {
                            navController.navigate("group_chat") { launchSingleTop = true }
                        }
                    }
                }

                PremiumBackground {
                    val privacySettings by com.example.security.PrivacyPreferences.settings.collectAsState()
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    // Screenshots/recording are blocked only while a chat or call is on screen.
                    // Screenshot blocking is user-controlled: Privacy → Screenshot protection.
                    // OFF (default) = screenshots and screen recording always work, exactly like
                    // every other messenger. ON = sensitive screens are protected. Either way the
                    // phone never gets stuck in a blocked state when leaving a chat or call.
                    val sensitiveRoutes = setOf("chat", "group_chat", "call", "group_call")
                    LaunchedEffect(currentRoute, privacySettings.preventScreenshotOnSensitiveScreens) {
                        val needsProtection = privacySettings.preventScreenshotOnSensitiveScreens &&
                            currentRoute?.substringBefore('/')?.substringBefore('?') in sensitiveRoutes
                        if (needsProtection) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.fillMaxSize(),
                        // One motion language for every page: the new page glides in and settles,
                        // the old one eases back; going back mirrors it.
                        enterTransition = { slideInHorizontally(tween(340)) { it / 5 } + fadeIn(tween(280)) + androidx.compose.animation.scaleIn(tween(340), initialScale = .96f) },
                        exitTransition = { fadeOut(tween(200)) + androidx.compose.animation.scaleOut(tween(300), targetScale = .97f) },
                        popEnterTransition = { fadeIn(tween(260)) + androidx.compose.animation.scaleIn(tween(320), initialScale = .97f) },
                        popExitTransition = { slideOutHorizontally(tween(300)) { it / 5 } + fadeOut(tween(220)) }
                    ) {
                        composable("splash") {
                            // The session/2FA check runs inside the splash animation, so a signed-in
                            // user goes splash → Home directly (no second "securing" screen).
                            var gateStatus by remember { mutableStateOf<com.example.ui.MfaStatus?>(null) }
                            val gated = destinationAfterSplash == "session_gate"
                            SplashScreen(
                                work = if (gated) ({ gateStatus = kotlinx.coroutines.withTimeoutOrNull(9000) { com.example.ui.MfaClient.precheck(context) } }) else null
                            ) {
                                splashFinished = true
                                val destination = when {
                                    !gated -> destinationAfterSplash
                                    gateStatus != null -> { pendingMfaStatus = gateStatus; "mfa" }
                                    else -> "home"
                                }
                                navController.navigate(destination) { popUpTo("splash") { inclusive = true } }
                            }
                        }

                        composable("language") {
                            com.example.ui.LanguagePickerScreen(onDone = {
                                val dest = try {
                                    if (!isOnboardingCompleted(context)) "onboarding"
                                    else if (FirebaseAuth.getInstance().currentUser != null) "session_gate" else "auth"
                                } catch (e: Exception) { "auth" }
                                navController.navigate(dest) { popUpTo("language") { inclusive = true } }
                            })
                        }

                        composable("onboarding") {
                            OnboardingScreen(
                                onFinished = {
                                    val dest = try {
                                        if (FirebaseAuth.getInstance().currentUser != null) "session_gate" else "auth"
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
                                    navController.navigate("session_gate") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Decides before Home is shown whether two-step verification is needed,
                        // so Home never flashes and is then covered by the 2FA screen.
                        composable("session_gate") {
                            com.example.ui.SessionGateScreen(
                                onHome = { navController.navigate("home") { popUpTo("session_gate") { inclusive = true } } },
                                onMfa = { status ->
                                    pendingMfaStatus = status
                                    navController.navigate("mfa") { popUpTo("session_gate") { inclusive = true } }
                                }
                            )
                        }

                        composable("mfa") {
                            val st = pendingMfaStatus
                            com.example.ui.MfaGateScreen(
                                method = st?.method.orEmpty().ifBlank { "email" },
                                maskedEmail = st?.email.orEmpty().ifBlank { "your e-mail" },
                                onVerified = {
                                    viewModel.mfaVerified()
                                    navController.navigate("home") { popUpTo("mfa") { inclusive = true } }
                                },
                                onSignOut = {
                                    viewModel.logout { navController.navigate("auth") { popUpTo("mfa") { inclusive = true } } }
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
                                onPostSelected = { post ->
                                    linkedPost = post
                                    pendingPostId.value = post.id
                                },
                                onCreatePost = { navController.navigate("compose_post") },
                                onGroupSelected = { group ->
                                    viewModel.selectGroup(group)
                                    navController.navigate("group_chat")
                                },
                                onAssistant = { navController.navigate("assistant") },
                                onQrContacts = { navController.navigate("qr_contacts") },
                                onPremium = { navController.navigate("premium") },
                                onDeviceLogin = { navController.navigate("device_login") },
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

                        // Device login: scan the web login QR to link this phone to a browser session.
                        composable("device_login") {
                            QrDeviceLoginScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onLinked = {
                                    navController.popBackStack()
                                    Toast.makeText(this@MainActivity, "Browser linked — open Convo Chat Web", Toast.LENGTH_SHORT).show()
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
                                onOpenAiSettings = {
                                    viewModel.requestSettingsPage("privacy-ai")
                                    viewModel.setCurrentTab(4)
                                    navController.popBackStack("home", false)
                                },
                                onOpenUser = { user ->
                                    viewModel.selectRecipient(user)
                                    navController.navigate("chat")
                                }
                            )
                        }

                        composable("smart_hub") {
                            com.example.ui.SmartHubScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenUser = { user -> viewModel.selectRecipient(user); navController.navigate("chat") },
                                onOpenProfile = { user -> viewModel.selectProfile(user); navController.navigate("profile") },
                                onOpenGroup = { group -> viewModel.selectGroup(group); navController.navigate("group_chat") },
                                onOpenPost = { post -> linkedPost = post; navController.navigate("post_detail") },
                                onRoute = { route -> navController.navigate(route) { launchSingleTop = true } }
                            )
                        }

                        composable("ai_studio") {
                            com.example.ui.AiStudioScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenAssistant = { navController.navigate("assistant") { launchSingleTop = true } }
                            )
                        }

                        composable("whats_new") {
                            com.example.ui.WhatsNewScreen(
                                onBack = { navController.popBackStack() },
                                onOpenStudio = { navController.navigate("ai_studio") { launchSingleTop = true } }
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
                                // Back keeps the user inside Convo Chat: the call keeps running in the
                                // floating mini bubble. Home/recents still switches to Picture-in-Picture.
                                onMinimize = { leaveCallScreen { if (!navController.popBackStack()) navController.navigate("home") { launchSingleTop = true } } },
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
                                    },
                                    onOpenUserProfile = { user ->
                                        viewModel.selectProfile(user)
                                        navController.navigate("profile")
                                    }
                                )
                            }
                        }
                        composable("group_chat") {
                            activeGroup?.let { group ->
                                GroupChatScreen(
                                    viewModel = viewModel,
                                    group = group,
                                    onOpenUserProfile = { user ->
                                        viewModel.selectProfile(user)
                                        navController.navigate("profile")
                                    },
                                    pendingCallRoomId = activeGroupRoomId,
                                    pendingCallVideo = activeGroupVideo,
                                    onBack = { navController.popBackStack() },
                                    onGroupCall = { video ->
                                        // Starting a call now opens the call screen straight away. If a
                                        // call is already running in this group, the button joins it
                                        // instead of creating a second room.
                                        val liveCall = viewModel.activeGroupCall.value
                                        val engine = GroupCallEngine.state.value
                                        val alreadyInside = engine.groupId == group.id && engine.status !in listOf("idle", "ended", "failed")
                                        if (!alreadyInside) {
                                            requestedGroupCallVideo = video
                                            groupCallJoinRoomId = liveCall?.roomId
                                        }
                                        navController.navigate("group_call") { launchSingleTop = true }
                                    },
                                    onJoinPendingCall = { roomId, video ->
                                        val engine = GroupCallEngine.state.value
                                        val alreadyInside = engine.roomId == roomId && engine.status !in listOf("idle", "ended", "failed")
                                        if (!alreadyInside) {
                                            requestedGroupCallVideo = video
                                            groupCallJoinRoomId = roomId
                                        }
                                        navController.navigate("group_call") { launchSingleTop = true }
                                    },
                                    onDismissPendingCall = {
                                        pendingGroupRoomId.value = null
                                        pendingGroupId.value = null
                                        pendingGroupName.value = null
                                        pendingGroupMembers.value = emptyList()
                                        pendingGroupVideo.value = false
                                    }
                                )
                            }
                        }
                        composable("group_call") {
                            activeGroup?.let { group ->
                                GroupCallScreen(
                                    group = group,
                                    video = requestedGroupCallVideo,
                                    onClose = {
                                        GroupCallEngine.end()
                                        groupCallJoinRoomId = null
                                        pendingGroupRoomId.value = null
                                        if (navController.currentBackStackEntry?.destination?.route == "group_call") navController.popBackStack()
                                    },
                                    onMinimize = { if (liveVideoCall()) minimizeActiveCall() else navController.popBackStack() },
                                    joinRoomId = groupCallJoinRoomId,
                                    autoStart = true,
                                    onRoomReady = { roomId ->
                                        // New room: ring every member. Everyone also sees the Join bar in the group.
                                        viewModel.inviteGroupMembersToCall(group, requestedGroupCallVideo, roomId)
                                    },
                                    candidates = knownUsers,
                                    onInvite = { ids, roomId, video -> viewModel.inviteUsersToGroupCall(group, ids, video, roomId) }
                                )
                            }
                        }
                    }
                    // Any screen can ask for a top-level page (AI Studio, What's new, …).
                    val navRequest by com.example.ui.AppNav.request.collectAsState()
                    LaunchedEffect(navRequest) {
                        val route = navRequest ?: return@LaunchedEffect
                        com.example.ui.AppNav.consume()
                        if (FirebaseAuth.getInstance().currentUser != null && route in setOf("ai_studio", "whats_new", "assistant", "smart_hub")) {
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                    val pendingProRoute by com.example.ui.ProTools.pendingRoute.collectAsState()
                    LaunchedEffect(pendingProRoute) {
                        if (pendingProRoute == "assistant") {
                            if (FirebaseAuth.getInstance().currentUser != null) navController.navigate("assistant") { launchSingleTop = true }
                            com.example.ui.ProTools.consumeRoute()
                        }
                    }
                    com.example.ui.ProToolsOverlay(
                        users = knownUsers,
                        onOpenChat = { peerUid ->
                            val cachedPeer = knownUsers.firstOrNull { it.uid == peerUid }
                            if (cachedPeer != null) {
                                viewModel.selectRecipient(cachedPeer)
                                navController.navigate("chat") { launchSingleTop = true }
                            } else {
                                FirebaseFirestore.getInstance().collection("users").document(peerUid).get()
                                    .addOnSuccessListener { doc -> doc.toObject(User::class.java)?.let { viewModel.selectRecipient(it); navController.navigate("chat") { launchSingleTop = true } } }
                            }
                        },
                        onStartCall = { peerUid, video ->
                            val beginCall: (User) -> Unit = { peer ->
                                viewModel.selectRecipient(peer)
                                if (video) viewModel.startVideoCall(peer) { navController.navigate("call") { launchSingleTop = true } }
                                else viewModel.startAudioCall(peer) { navController.navigate("call") { launchSingleTop = true } }
                            }
                            knownUsers.firstOrNull { it.uid == peerUid }?.let(beginCall)
                                ?: FirebaseFirestore.getInstance().collection("users").document(peerUid).get()
                                    .addOnSuccessListener { doc -> doc.toObject(User::class.java)?.let(beginCall) }
                        }
                    )
                    if (splashFinished) com.example.ui.CrashReportDialog()
                    // Connection status pill (offline / back online), above every page.
                    if (splashFinished && currentRoute !in listOf("call", "group_call", "splash")) {
                        com.example.ui.ConnectivityBanner(Modifier.align(Alignment.TopCenter).statusBarsPadding())
                    }
                    // Two-step verification also guards screens opened from notifications/deep links.
                    val globalMfaGate by viewModel.mfaGate.collectAsState()
                    globalMfaGate?.let { gate ->
                        if (currentRoute !in listOf("home", "mfa", "session_gate", "auth", "splash", "language", "onboarding")) {
                            com.example.ui.MfaGateDialog(
                                method = gate.method.ifBlank { "email" },
                                maskedEmail = gate.email.ifBlank { "your e-mail" },
                                onVerified = { viewModel.mfaVerified() },
                                onSignOut = { viewModel.logout { navController.navigate("auth") { popUpTo(0) } } }
                            )
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
                    LaunchedEffect(openCallRequest, callState.status) {
                        if (openCallRequest && callState.status !in listOf("idle", "ended", "declined", "missed", "failed")) {
                            pendingOpenCall.value = false
                            navController.navigate("call") { launchSingleTop = true }
                        } else if (openCallRequest && callState.status in listOf("ended", "declined", "missed", "failed")) pendingOpenCall.value = false
                    }
                    val inCallPip by com.example.call.CallPipState.inPip.collectAsState()
                    if (inCallPip) {
                        // PiP while not on a call screen: cover the app with a compact call card.
                        val privateLive = callState.status !in listOf("idle", "ended", "declined", "missed", "failed")
                        val groupLive = groupCallState.status !in listOf("idle", "ended", "failed")
                        if (privateLive && currentRoute != "call") Box(Modifier.fillMaxSize().clickable(enabled = false) {}) { com.example.call.PrivateCallPipContent(callState.copy(video = false)) }
                        else if (!privateLive && groupLive && currentRoute != "group_call") Box(Modifier.fillMaxSize().clickable(enabled = false) {}) { com.example.call.GroupCallPipContent(groupCallState) }
                    }
                    if (!inCallPip && currentRoute != "group_call" && groupCallState.status !in listOf("idle", "ended", "failed") && activeGroup?.id == groupCallState.groupId && callState.status in listOf("idle", "ended", "declined", "missed", "failed")) {
                        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                            com.example.call.GroupCallReturnPill(groupCallState, onExpand = { navController.navigate("group_call") { launchSingleTop = true } })
                        }
                    }
                    if (!inCallPip && currentRoute != "call" && callState.status !in listOf("idle", "ended", "declined", "missed", "failed")) {
                        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                            CallMiniOverlay(
                                state = callState,
                                onExpand = { navController.navigate("call") { launchSingleTop = true } },
                                onEnd = { activeRecipient?.let { viewModel.endCall(it, callState.callId) } ?: CallEngine.end() }
                            )
                        }
                    }
                    com.example.ui.CallReadinessDialog(
                        enabled = splashFinished && currentUser != null && currentRoute !in listOf("call", "auth", "onboarding", "splash") && !showOverlayPermissionPrompt
                    )
                    if (showOverlayPermissionPrompt) {
                        AlertDialog(
                            onDismissRequest = { showOverlayPermissionPrompt = false },
                            title = { Text("Keep calls visible over other apps?") },
                            text = { Text("Allow Display over other apps so an active call can stay available while you use another app. Android Picture-in-Picture will still be used when supported." ) },
                            confirmButton = {
                                Button(onClick = {
                                    showOverlayPermissionPrompt = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                    }
                                }) { Text("Open permission") }
                            },
                            dismissButton = { TextButton(onClick = { showOverlayPermissionPrompt = false }) { Text("Later") } }
                        )
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
