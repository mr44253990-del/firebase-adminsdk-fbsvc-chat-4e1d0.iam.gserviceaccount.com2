@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.theme.*
import com.example.call.CallSkinPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.R
import androidx.compose.ui.graphics.lerp
import com.example.data.ActivityNotification
import com.example.security.AppLockManager
import com.example.security.PrivacyPreferences
import com.example.data.FriendRequest
import com.example.data.Group
import com.example.data.MessageRequest
import com.example.data.Post
import com.example.data.PostComment
import com.example.video.SharedCachedVideo
import com.example.video.VideoPlayerManager
import com.example.data.Story
import com.example.data.User
import com.example.data.DeviceSession
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class VerifiedUpdateApk(val bytes: ByteArray, val versionCode: Long, val versionName: String)

@Suppress("DEPRECATION")
internal fun signerDigests(info: PackageInfo): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty()
    } else {
        info.signatures.orEmpty()
    }
    return signatures.map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.toSet()
}

@Suppress("DEPRECATION")
internal fun verifyUpdateApk(context: Context, uri: Uri): Result<VerifiedUpdateApk> = runCatching {
    val temp = File(context.cacheDir, "flagship-update-check.apk")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected APK")
        require(temp.length() in 1..95L * 1024L * 1024L) { "APK must be smaller than 95 MB" }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(temp.absolutePath, flags)
            ?: error("The selected file is not a valid Android APK")
        require(archive.packageName == context.packageName) { "APK package must be ${context.packageName}" }

        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(signerDigests(archive).isNotEmpty() && signerDigests(archive) == signerDigests(installed)) {
            "APK signing certificate does not match Convo Chat"
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        require(code >= com.example.BuildConfig.VERSION_CODE) {
            "Old APK rejected: selected code $code, admin app code ${com.example.BuildConfig.VERSION_CODE}. Choose the newest signed Release APK."
        }
        VerifiedUpdateApk(temp.readBytes(), code, archive.versionName.orEmpty())
    } finally {
        temp.delete()
    }
}

private fun formatOfflineAge(lastSeenAt: Long, now: Long = System.currentTimeMillis()): String {
    if (lastSeenAt <= 0L) return "Offline"
    val elapsed = (now - lastSeenAt).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 60L -> "Offline ${minutes}m ago"
        minutes < 24L * 60L -> "Offline ${minutes / 60L}h ago"
        else -> "Offline ${minutes / (24L * 60L)}d ago"
    }
}

@Composable
private fun DeviceSessionRow(session: DeviceSession, onDelete: (DeviceSession) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (session.active) Icons.Default.PhoneAndroid else Icons.Default.PhoneLocked,
                null,
                tint = if (session.active) Color(0xFF36C878) else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(session.deviceName, fontWeight = FontWeight.Bold)
                Text("${session.androidVersion} • Convo ${session.appVersion}", fontSize = 10.sp)
                Text("${session.city}, ${session.region} ${session.country} • ${session.maskedIp}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(if (session.active) "Active now" else formatOfflineAge(session.lastSeenAt), fontSize = 9.sp, color = if (session.active) Color(0xFF23864D) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onDelete(session) }) {
                Icon(Icons.Default.DeleteOutline, "Delete device history", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onUserSelected: (User) -> Unit,
    onProfileSelected: (User) -> Unit,
    onCreatePost: () -> Unit,
    onGroupSelected: (Group) -> Unit,
    onAssistant: () -> Unit,
    onQrContacts: () -> Unit,
    onPremium: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val isOnlineState by viewModel.isNetworkAvailable.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.filteredUsersState.collectAsState()
    val groups by viewModel.groupsState.collectAsState()
    val stories by viewModel.storiesState.collectAsState()
    val storyPlaybackQueue = remember(stories) {
        val now = System.currentTimeMillis()
        stories.groupBy { it.senderId }.values.sortedWith(
            compareByDescending<List<Story>> { group -> group.any { it.spotlightUntil > now } }
                .thenByDescending { group -> group.maxOfOrNull { it.timestamp } ?: 0L }
        ).flatMap { group -> group.sortedBy { it.timestamp } }
    }
    val posts by viewModel.postsState.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val gatewayHealth by viewModel.gatewayHealth.collectAsState()
    val presenceWorkerUrl by viewModel.presenceWorkerUrl.collectAsState()
    val presenceWorkerHealth by viewModel.presenceWorkerHealth.collectAsState()
    val presenceDashboard by viewModel.presenceDashboard.collectAsState()
    val storageRoutingConfig by viewModel.storageRoutingConfig.collectAsState()
    val storageSetupHealth by viewModel.storageSetupHealth.collectAsState()
    val flagshipConfig by viewModel.flagshipConfig.collectAsState()
    val featureRequests by viewModel.featureRequests.collectAsState()
    val premiumRequests by viewModel.premiumRequests.collectAsState()
    val deviceSessions by viewModel.deviceSessions.collectAsState()
    val adminReports by viewModel.adminReports.collectAsState()
    val accountBanned by viewModel.accountBanned.collectAsState()
    val forcedLogout by viewModel.forcedLogout.collectAsState()
    val mfaGate by viewModel.mfaGate.collectAsState()
    val inAppNotification by viewModel.inAppNotification.collectAsState()
    val activityNotifications by viewModel.activityNotifications.collectAsState()
    val openActivitySignal by viewModel.openActivityCenterSignal.collectAsState()
    val friendRequests by viewModel.friendRequests.collectAsState()
    val sentFriendRequests by viewModel.sentFriendRequestIds.collectAsState()
    val messageRequests by viewModel.messageRequests.collectAsState()
    val currentTheme by viewModel.themeState.collectAsState()
    val notificationSounds by viewModel.notificationSoundsEnabled.collectAsState()
    val typingSounds by viewModel.typingSoundsEnabled.collectAsState()
    val mutedUsers by viewModel.mutedUserIds.collectAsState()
    val savedPostIds by viewModel.savedPostIds.collectAsState()
    val allUsers by viewModel.usersState.collectAsState()
    val conversationUserIds by viewModel.conversationUserIds.collectAsState()
    val unreadCounts by viewModel.unreadCountsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var feedFilter by rememberSaveable { mutableStateOf("All Posts") }
    var showAnalytics by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showActivityCenter by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showLockSetup by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showAllDeviceSessions by rememberSaveable { mutableStateOf(false) }
    var deviceSessionPendingDelete by remember { mutableStateOf<DeviceSession?>(null) }
    var showClearDeviceHistoryDialog by rememberSaveable { mutableStateOf(false) }
    val funPrefs = remember { context.getSharedPreferences("convo_fun_campaign", Context.MODE_PRIVATE) }
    val funKey = "${currentUser?.uid}_${flagshipConfig.funCampaignId}"
    var showFunCampaign by remember(funKey, flagshipConfig.funCampaignEnabled) { mutableStateOf(flagshipConfig.funCampaignEnabled && flagshipConfig.funCampaignId.isNotBlank() && currentUser != null && !funPrefs.getBoolean(funKey, false)) }
    var lockEnabled by remember { mutableStateOf(AppLockManager.isEnabled(context)) }
    val privacySettings by PrivacyPreferences.settings.collectAsState()
    val currentTab by viewModel.currentTabState.collectAsState()
    val selectedReelPostId by viewModel.selectedReelPostId.collectAsState()
    BackHandler(enabled = currentTab == 6 && !flagshipConfig.tiktokModeEnabled) { viewModel.setCurrentTab(0) }
    BackHandler(enabled = currentTab == 7) { viewModel.setCurrentTab(3) }
    val isAdmin = FirebaseAuth.getInstance().currentUser?.email?.lowercase()?.trim()?.trimEnd('.') == "mr4425390@gmail.com"
    val staffPermissions: Set<String>? = currentUser?.takeIf { !isAdmin && it.staffRole == "sub_admin" && it.staffPermissions.isNotEmpty() }?.staffPermissions?.toSet()
    val isStaff = staffPermissions != null
    var dismissNotice by remember(flagshipConfig.updatedAt) { mutableStateOf(false) }
    val validUpdate = flagshipConfig.updateEnabled && flagshipConfig.apkUrl.startsWith("https://")
    // Version code is the single source of truth. A republished campaign must
    // never force users who already run the configured (or newer) version.
    val updateRequired = !isAdmin && validUpdate && flagshipConfig.latestVersionCode > com.example.BuildConfig.VERSION_CODE

    // Dialog & Creation controllers
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateHub by remember { mutableStateOf(false) }
    var selectedStoryIndex by remember { mutableStateOf<Int?>(null) }
    val premiumWelcomeKey = currentUser?.takeIf { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > System.currentTimeMillis()) && it.premiumApprovedAt > 0 }?.let { "premium_${it.premiumApprovedAt}" }
    var showPremiumWelcome by remember(premiumWelcomeKey) { mutableStateOf(premiumWelcomeKey != null && !context.getSharedPreferences("convo_premium_welcome", Context.MODE_PRIVATE).getBoolean(premiumWelcomeKey, false)) }
    val upsellPrefs = remember { context.getSharedPreferences("convo_premium_upsell", Context.MODE_PRIVATE) }
    var showPremiumUpsell by remember(currentUser?.uid, flagshipConfig.premiumUpsellEnabled) { mutableStateOf(currentUser != null && currentUser?.isPremium != true && flagshipConfig.premiumUpsellEnabled && !upsellPrefs.getBoolean("dismissed_${currentUser?.uid}", false)) }

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

    // First-launch guided tour (spotlight + blurred surroundings). Replayable from Settings.
    var coachTourActive by remember { mutableStateOf(false) }
    val coachReplay by CoachTour.replay.collectAsState()
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        if (!CoachTour.isDone(context)) coachTourActive = true
    }
    LaunchedEffect(coachReplay) { if (coachReplay > 0) { kotlinx.coroutines.delay(350); coachTourActive = true } }

    Scaffold(
        modifier = Modifier.coachBlurHost(coachTourActive && currentTab != 6, CoachTour.activeKey.value?.let { CoachTour.targets[it] }),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (currentTab != 6) Column(modifier = Modifier.fillMaxWidth()) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Convo Chat",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 25.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = (-0.8).sp
                                )
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(
                                        if (isOnlineState) Color(0xFF26E86F) else Color(0xFFFF5252)
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isOnlineState) "Online" else "Offline Cache Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onPremium) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = "Convo Chat Premium", tint = Color(0xFFFFB300))
                        }
                        IconButton(onClick = { showGlobalSearch = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search people and media")
                        }
                        if (isAdmin || isStaff) IconButton(onClick = { viewModel.setCurrentTab(3) }) {
                            Icon(if (isStaff) Icons.Outlined.Shield else Icons.Outlined.AdminPanelSettings, contentDescription = if (isStaff) "Staff panel" else "Admin tools", tint = MaterialTheme.colorScheme.primary)
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
                                    text = { Text(tr("Messages")) },
                                    leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                                    onClick = { showAccountMenu = false; viewModel.setCurrentTab(1) }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Groups")) },
                                    leadingIcon = { Icon(Icons.Outlined.Groups, null) },
                                    onClick = { showAccountMenu = false; viewModel.setCurrentTab(2) }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("My profile")) },
                                    leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) },
                                    enabled = currentUser != null,
                                    onClick = { showAccountMenu = false; currentUser?.let { onProfileSelected(it) } }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Profile & settings")) },
                                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                                    onClick = {
                                        showAccountMenu = false
                                        viewModel.setCurrentTab(4)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Discover people")) },
                                    leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) },
                                    onClick = { showAccountMenu = false; viewModel.setCurrentTab(5) }
                                )
                                if (isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("Admin analytics dashboard") },
                                        leadingIcon = { Icon(Icons.Outlined.Analytics, null) },
                                        onClick = { showAccountMenu = false; viewModel.setCurrentTab(7) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Admin control center") },
                                        leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, null) },
                                        onClick = { showAccountMenu = false; viewModel.setCurrentTab(3) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(tr("Sign out"), color = MaterialTheme.colorScheme.error) },
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
            if (currentTab != 6) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setCurrentTab(0) },
                    modifier = Modifier.coachTarget("nav_home"),
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text(tr("Home"), fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.setCurrentTab(1) },
                    modifier = Modifier.coachTarget("nav_chats"),
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text(tr("Chats"), fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { showCreateHub = true },
                    modifier = Modifier.coachTarget("nav_create"),
                    icon = {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF674CFF), Color(0xFFFF36BE)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Create", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    },
                    label = null
                )
                // Always five slots so the create (+) button stays centred. When the admin
                // turns Reels off (for everyone, admin included) the slot becomes Groups.
                if (flagshipConfig.reelsEnabled) NavigationBarItem(
                    selected = currentTab == 6,
                    onClick = { viewModel.openReel() },
                    modifier = Modifier.coachTarget("nav_slot4"),
                    icon = { Icon(Icons.Default.SmartDisplay, contentDescription = "Reels") },
                    label = { Text(tr("Reels"), fontSize = 10.sp) }
                ) else NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setCurrentTab(2) },
                    modifier = Modifier.coachTarget("nav_slot4"),
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Groups") },
                    label = { Text(tr("Groups"), fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 5,
                    onClick = { viewModel.setCurrentTab(5) },
                    modifier = Modifier.coachTarget("nav_friends"),
                    icon = { Icon(Icons.Default.PeopleAlt, contentDescription = "Friends") },
                    label = { Text(tr("Friends"), fontSize = 10.sp) }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0 && flagshipConfig.assistantEnabled) {
                FloatingActionButton(
                    onClick = onAssistant,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Open AI assistant", modifier = Modifier.size(26.dp))
                }
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
                    val feedState = rememberLazyListState()
                    val regularFeedPosts = remember(posts, flagshipConfig.reelsEnabled) { posts.filter { (!it.isAdminReel || it.postEverywhere) && (flagshipConfig.reelsEnabled || isAdmin || !it.isReel) } }
                    val visibleFeedPosts = remember(regularFeedPosts, feedFilter, currentUser?.friends, currentUser?.following, savedPostIds) {
                        when (feedFilter) {
                            "Friends" -> regularFeedPosts.filter { it.senderId in currentUser?.friends.orEmpty() }
                            "Following" -> regularFeedPosts.filter { it.senderId in currentUser?.following.orEmpty() }
                            "Saved" -> regularFeedPosts.filter { it.id in savedPostIds }
                            else -> regularFeedPosts
                        }
                    }
                    val suggestions = remember(allUsers, currentUser) {
                        val me = currentUser
                        allUsers.filter { person -> person.uid != me?.uid && person.uid !in me?.friends.orEmpty() && person.uid !in me?.following.orEmpty() }
                            .sortedWith(compareByDescending<User> { it.role == "moderator" }
                                .thenByDescending { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > System.currentTimeMillis()) }
                                .thenByDescending { person -> person.friends.count { it in me?.friends.orEmpty() } })
                            .take(10)
                    }
                    val postIds = remember(visibleFeedPosts) { visibleFeedPosts.map { it.id }.toSet() }
                    val activeFeedPostId by remember(feedState, postIds) {
                        derivedStateOf {
                            val center = (feedState.layoutInfo.viewportStartOffset + feedState.layoutInfo.viewportEndOffset) / 2
                            val layout = feedState.layoutInfo
                            layout.visibleItemsInfo
                                .filter { item ->
                                    item.key in postIds && run {
                                        val visibleTop = maxOf(item.offset, layout.viewportStartOffset)
                                        val visibleBottom = minOf(item.offset + item.size, layout.viewportEndOffset)
                                        (visibleBottom - visibleTop).coerceAtLeast(0) >= item.size * 0.82f
                                    }
                                }
                                .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }
                                ?.key as? String
                        }
                    }
                    // One continuous list: stories/composer/filters naturally collapse while scrolling
                    // and return when the user reaches the top, leaving maximum room for media.
                    LazyColumn(
                        state = feedState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (storyPlaybackQueue.isNotEmpty()) item(key = "stories") {
                            StoriesHorizontalSection(
                                viewModel = viewModel,
                                stories = storyPlaybackQueue,
                                onStorySelected = { story ->
                                    viewModel.recordStoryView(story)
                                    selectedStoryIndex = storyPlaybackQueue.indexOfFirst { it.id == story.id }.takeIf { it >= 0 }
                                }
                            )
                        }
                        item(key = "polls") {
                            ActivePollsSection(
                                userName = currentUser?.name.orEmpty(),
                                userPhoto = currentUser?.profileImageUrl.orEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                        item(key = "composer") {
                            Card(
                                onClick = onCreatePost,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .70f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = currentUser?.profileImageUrl?.ifBlank { null },
                                            contentDescription = "Your profile",
                                            error = painterResource(id = R.drawable.img_app_logo),
                                            modifier = Modifier.size(46.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                        Spacer(Modifier.width(11.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("What's on your mind, ${currentUser?.name?.substringBefore(' ') ?: "friend"}? ✨", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                            Text(tr("Share a moment with your community"), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                        }
                                        Box(Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5C53FF), Color(0xFFFF35BE)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Send, "Create post", tint = Color.White) }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(.5f))
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround) {
                                        listOf(
                                            Triple(Icons.Outlined.Photo, "Photo", Color(0xFF3EE07A)),
                                            Triple(Icons.Outlined.Videocam, "Video", Color(0xFFFF3F6C)),
                                            Triple(Icons.Outlined.SmartDisplay, "Reel", Color(0xFFD85CFF)),
                                            Triple(Icons.Outlined.EmojiEmotions, "Feeling", Color(0xFFFFC145)),
                                            Triple(Icons.Outlined.Analytics, "Analytics", Color(0xFF37D8E7))
                                        ).forEach { (icon, label, tint) ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                                if (label == "Analytics") showAnalytics = true else {
                                                    when (label) {
                                                        "Photo" -> viewModel.prepareComposer("photo")
                                                        "Video" -> viewModel.prepareComposer("video")
                                                        "Reel" -> viewModel.prepareComposer("reel")
                                                        else -> viewModel.prepareComposer("post")
                                                    }
                                                    onCreatePost()
                                                }
                                            }.padding(4.dp)) { Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp)); Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
                                        }
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
                                items(listOf("All Posts", "Friends", "Groups", "Following", "Saved")) { label ->
                                    FilterChip(
                                        selected = feedFilter == label,
                                        onClick = { if (label == "Groups") viewModel.setCurrentTab(2) else feedFilter = label },
                                        label = { Text(label) },
                                        leadingIcon = if (label == "All Posts") { { Icon(Icons.Outlined.DynamicFeed, null, Modifier.size(17.dp)) } } else null,
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                        if (suggestions.isNotEmpty() && feedFilter == "All Posts") {
                            item(key = "suggestions") {
                                SuggestedPeopleStrip(
                                    suggestions = suggestions,
                                    myFriendIds = currentUser?.friends.orEmpty(),
                                    onFollow = viewModel::toggleFollow,
                                    onOpen = onProfileSelected
                                )
                            }
                        }
                        if (visibleFeedPosts.isEmpty()) {
                            item(key = "empty") {
                                Column(
                                    modifier = Modifier.fillParentMaxHeight(.62f).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Outlined.DynamicFeed, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = .45f), modifier = Modifier.size(60.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text(tr("No posts yet"), fontWeight = FontWeight.Bold)
                                    Text(tr("Share the first update with your community."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(visibleFeedPosts, key = { it.id }) { post ->
                                Box(Modifier.padding(horizontal = 14.dp)) {
                                    SocialPostItem(post = post, viewModel = viewModel, onProfileSelected = onProfileSelected, autoPlayVideo = post.id == activeFeedPostId && selectedStoryIndex == null)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val myUid = currentUser?.uid.orEmpty()
                    val sentRequestTargets = sentFriendRequests.mapNotNull { id -> id.removePrefix("${myUid}_").takeIf { it != id } }.toSet()
                    val conversationOrder by viewModel.conversationOrder.collectAsState()
                    val recencyRank = remember(conversationOrder) { conversationOrder.withIndex().associate { it.value to it.index } }
                    val filteredChatUsers = if (searchQuery.isNotBlank()) users else users.filter { user ->
                        currentUser?.friends?.contains(user.uid) == true ||
                            conversationUserIds.contains(user.uid) ||
                            unreadCounts.containsKey(user.uid) ||
                            sentRequestTargets.contains(user.uid)
                    }
                    // People you actually chat with stay on top: unread first, then the most
                    // recent conversations, then friends you have not messaged yet.
                    val chatUsers = remember(filteredChatUsers, recencyRank, unreadCounts) {
                        filteredChatUsers.distinctBy { it.uid }.sortedWith(
                            compareByDescending<User> { (unreadCounts[it.uid] ?: 0) > 0 }
                                .thenBy { recencyRank[it.uid] ?: Int.MAX_VALUE }
                                .thenBy { it.name.lowercase() }
                        )
                    }
                    // Only friends, requests and real conversations appear until the user searches.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        AnnouncementBanner(Modifier.fillMaxWidth().padding(bottom = 10.dp))
                        // Search contacts bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchUsers(it)
                            },
                            placeholder = { Text(tr("Search users by name...")) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val activeContacts = chatUsers.filter { it.isOnline }.distinctBy { it.uid }
                        if (searchQuery.isBlank() && activeContacts.isNotEmpty()) {
                            ActiveContactsStrip(users = activeContacts, onSelect = onUserSelected)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        if (chatUsers.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(tr("No conversations yet — search to find people"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(chatUsers.sortedByDescending { ConversationPrefs.isPinned(context, it.uid) }, key = { it.uid }) { user ->
                                    val hasActiveStory = stories.any { it.senderId == user.uid }
                                    ChatConversationUserItem(
                                        user = user,
                                        viewModel = viewModel,
                                        hasActiveStory = hasActiveStory,
                                        isMuted = user.uid in mutedUsers,
                                        onSelect = { onUserSelected(user) },
                                        onLongPress = {
                                            viewModel.toggleMuteUser(user.uid)
                                            Toast.makeText(context, if (user.uid in mutedUsers) "${user.name} unmuted" else "${user.name} muted", Toast.LENGTH_SHORT).show()
                                        }
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
                                Text(tr("Create Group"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (groups.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(tr("You haven't joined any groups yet"), fontWeight = FontWeight.SemiBold)
                                    Text(tr("Create a new group above to invite friends!"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (isAdmin || isStaff) {
                        AdminControlCenterScreen(
                            viewModel = viewModel,
                            onOpenAnalytics = { viewModel.setCurrentTab(7) },
                            staffPermissions = staffPermissions
                        )
                    } else {
                        AdminAccessDeniedScreen()
                    }
                }
                4 -> {
                    val themeSectionRequester = remember { BringIntoViewRequester() }
                    val callSkinSectionRequester = remember { BringIntoViewRequester() }
                    val profileSectionRequester = remember { BringIntoViewRequester() }
                    val privacySectionRequester = remember { BringIntoViewRequester() }
                    val notificationsSectionRequester = remember { BringIntoViewRequester() }
                    val devicesSectionRequester = remember { BringIntoViewRequester() }
                    val blockedSectionRequester = remember { BringIntoViewRequester() }
                    var showSettingsNavigationMenu by remember { mutableStateOf(false) }
                    var showSettingsMoreMenu by remember { mutableStateOf(false) }
                    // Each hub row opens its own page; "" is the hub itself.
                    var settingsPage by rememberSaveable { mutableStateOf("") }
                    val settingsScroll = rememberScrollState()
                    BackHandler(enabled = settingsPage.isNotEmpty()) { settingsPage = "" }
                    // Remember where the hub list was, so Back from a page returns to the same spot
                    // instead of jumping to the top. Pages themselves always open at the top.
                    var hubScrollPosition by rememberSaveable { mutableIntStateOf(0) }
                    LaunchedEffect(settingsPage) {
                        if (settingsPage.isEmpty()) {
                            val target = hubScrollPosition
                            var tries = 0
                            withFrameNanos { }
                            while (tries < 8 && settingsScroll.maxValue < target) { withFrameNanos { }; tries++ }
                            settingsScroll.scrollTo(target.coerceAtMost(settingsScroll.maxValue))
                            snapshotFlow { settingsScroll.value }.collect { hubScrollPosition = it }
                        } else settingsScroll.scrollTo(0)
                    }
                    LaunchedEffect(Unit) { ChatPreferences.load(context); currentUser?.uid?.let { ProfileViewerStore.load(context, it) } }
                    val callSkinId by CallSkinPreferences.selectedId.collectAsState()
                    LaunchedEffect(Unit) { CallSkinPreferences.load(context); viewModel.refreshDeviceSessions() }
                    // Settings hub: account first, grouped controls, and clear shortcuts.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .verticalScroll(settingsScroll),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (settingsPage.isEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { showSettingsNavigationMenu = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open app menu")
                                }
                                DropdownMenu(
                                    expanded = showSettingsNavigationMenu,
                                    onDismissRequest = { showSettingsNavigationMenu = false }
                                ) {
                                    DropdownMenuItem(text = { Text(tr("Home")) }, leadingIcon = { Icon(Icons.Outlined.Home, null) }, onClick = { showSettingsNavigationMenu = false; viewModel.setCurrentTab(0) })
                                    DropdownMenuItem(text = { Text(tr("Messages")) }, leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, onClick = { showSettingsNavigationMenu = false; viewModel.setCurrentTab(1) })
                                    DropdownMenuItem(text = { Text(tr("Groups")) }, leadingIcon = { Icon(Icons.Outlined.Groups, null) }, onClick = { showSettingsNavigationMenu = false; viewModel.setCurrentTab(2) })
                                    DropdownMenuItem(text = { Text(tr("Discover people")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, onClick = { showSettingsNavigationMenu = false; viewModel.setCurrentTab(5) })
                                    if (isAdmin || isStaff) DropdownMenuItem(text = { Text(if (isStaff) "Staff panel" else "Admin tools") }, leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, null) }, onClick = { showSettingsNavigationMenu = false; viewModel.setCurrentTab(3) })
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(tr("Profile & settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text(tr("Your account, messages and preferences"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { showSettingsMoreMenu = true }) {
                                    Icon(Icons.Default.MoreHoriz, contentDescription = "More account options")
                                }
                                DropdownMenu(
                                    expanded = showSettingsMoreMenu,
                                    onDismissRequest = { showSettingsMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(tr("My profile")) },
                                        leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) },
                                        enabled = currentUser != null,
                                        onClick = { showSettingsMoreMenu = false; currentUser?.let { onProfileSelected(it) } }
                                    )
                                    DropdownMenuItem(text = { Text(tr("QR & contacts")) }, leadingIcon = { Icon(Icons.Outlined.QrCode2, null) }, onClick = { showSettingsMoreMenu = false; onQrContacts() })
                                    DropdownMenuItem(text = { Text(tr("Convo Chat Premium")) }, leadingIcon = { Icon(Icons.Outlined.WorkspacePremium, null, tint = Color(0xFFFFA000)) }, onClick = { showSettingsMoreMenu = false; onPremium() })
                                    DropdownMenuItem(text = { Text(tr("Report a problem")) }, leadingIcon = { Icon(Icons.Outlined.ReportProblem, null) }, onClick = { showSettingsMoreMenu = false; showReportDialog = true })
                                    DropdownMenuItem(
                                        text = { Text(tr("Sign out"), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { showSettingsMoreMenu = false; viewModel.logout { onSignOut() } }
                                    )
                                }
                            }
                        }

                        val activePremium = currentUser?.isPremium == true &&
                            (currentUser?.premiumPlan == "lifetime" || (currentUser?.premiumUntil ?: 0L) > System.currentTimeMillis())
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .liquidCard(shape = RoundedCornerShape(30.dp))
                                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .28f), MaterialTheme.colorScheme.tertiary.copy(alpha = .10f), Color.Transparent))),
                            shape = RoundedCornerShape(30.dp),
                            color = Color.Transparent
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(Modifier.size(98.dp).clip(CircleShape).background(ProfileRingBrush).padding(4.dp)) {
                                    if (!currentUser?.profileImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = currentUser?.profileImageUrl,
                                            contentDescription = currentUser?.name ?: "Your profile",
                                            error = painterResource(R.drawable.img_app_logo),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                                        }
                                    }
                                }
                                Text(currentUser?.name?.ifBlank { "Your Convo account" } ?: "Your Convo account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    currentUser?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Your profile, privacy and chats in one place",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentUser?.staffRole == "sub_admin") StaffBadge(currentUser?.staffTitle.orEmpty().ifBlank { "Sub-admin" } + " • Convo staff")
                                Surface(
                                    color = if (activePremium) Color(0xFFFFE6A8) else MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (activePremium) Icons.Outlined.WorkspacePremium else Icons.Outlined.VerifiedUser, null, tint = if (activePremium) Color(0xFF8A5A00) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (activePremium) "Convo Premium" else "Convo account", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (activePremium) Color(0xFF8A5A00) else MaterialTheme.colorScheme.primary)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { currentUser?.let { onProfileSelected(it) } },
                                    enabled = currentUser != null,
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                                ) {
                                    Text(tr("Manage account"), fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        SettingsHubGroup("Account & messages") {
                            SettingsHubRow(
                                icon = Icons.Outlined.Person,
                                title = "Profile details",
                                subtitle = "Edit your photo, bio and account information",
                                accent = Color(0xFF5B6FE8),
                                onClick = { settingsPage = "profile" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                title = "Messages",
                                subtitle = "Open conversations and message requests",
                                trailingValue = "Chats",
                                accent = Color(0xFF2789E8),
                                onClick = { viewModel.setCurrentTab(1) }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.Forum,
                                title = "Chat settings",
                                subtitle = "Quick reaction, text size, Enter to send, link previews",
                                accent = Color(0xFF0E9F75),
                                onClick = { settingsPage = "chat" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.QrCode2,
                                title = "QR & contacts",
                                subtitle = "Share your profile or connect with friends",
                                accent = Color(0xFF32A68B),
                                onClick = onQrContacts
                            )
                        }

                        SettingsHubGroup("Personalization") {
                            SettingsHubRow(
                                icon = Icons.Outlined.Palette,
                                title = "App theme",
                                subtitle = "Choose the look of Convo Chat",
                                trailingValue = currentTheme,
                                accent = Color(0xFF765BFF),
                                onClick = { settingsPage = "theme" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.Call,
                                title = "Call appearance",
                                subtitle = "Pick a style for audio and video calls",
                                trailingValue = CallSkinPreferences.palette(callSkinId).label,
                                accent = Color(0xFF13A6B7),
                                onClick = { settingsPage = "call" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.Chat,
                                title = "Chat backgrounds",
                                subtitle = "Default background, photo backgrounds and themes",
                                accent = Color(0xFF8B62D9),
                                onClick = { settingsPage = "backgrounds" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                title = "Bubbles & spacing",
                                subtitle = "Liquid, neon, outline or no border · side space · 24h time",
                                trailingValue = ChatAppearance.label(ChatPreferences.appearance.collectAsState().value.bubbleStyle),
                                accent = Color(0xFFA66BFF),
                                onClick = { settingsPage = "bubbles" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.MusicNote,
                                title = "Sounds",
                                subtitle = "Your own notification and typing sounds",
                                accent = Color(0xFFE5674B),
                                onClick = { settingsPage = "sounds" }
                            )
                        }

                        SettingsHubGroup("Privacy & permissions") {
                            SettingsHubRow(
                                icon = Icons.Outlined.Security,
                                title = "Privacy controls",
                                subtitle = "Presence, receipts, AI and screen protection",
                                accent = Color(0xFF3287D5),
                                onClick = { settingsPage = "privacy" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.NotificationsActive,
                                title = "Notifications & calls",
                                subtitle = "Sounds, alerts and incoming-call access",
                                accent = Color(0xFFE79024),
                                onClick = { settingsPage = "notifications" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.Visibility,
                                title = "Profile viewers",
                                subtitle = "See who visited your profile",
                                trailingValue = ProfileViewerStore.viewers.collectAsState().value.count { it.lastViewedAt >= System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }.let { if (it > 0) "$it this week" else null },
                                accent = Color(0xFFD18B12),
                                onClick = { settingsPage = "viewers" }
                            )
                        }

                        SettingsHubGroup("Smart tools") {
                            SettingsHubRow(
                                icon = Icons.Default.AutoAwesome,
                                title = "Smart tools",
                                subtitle = "Quiet hours, motion, live location and storage",
                                accent = Color(0xFF00A3A3),
                                onClick = { settingsPage = "smart" }
                            )
                        }

                        SettingsHubGroup("More") {
                            SettingsHubRow(
                                icon = Icons.Default.Translate,
                                title = "Language",
                                subtitle = "English or বাংলা",
                                trailingValue = AppLanguage.options.firstOrNull { it.code == AppLanguage.code.collectAsState().value }?.nativeName,
                                accent = Color(0xFF6C4DF5),
                                onClick = { settingsPage = "language" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Default.Explore,
                                title = "Replay app tour",
                                subtitle = "Show the guided tour of the main buttons again",
                                accent = Color(0xFFFF6FB5),
                                onClick = { viewModel.setCurrentTab(0); CoachTour.requestReplay() }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.Security,
                                title = "Account security",
                                subtitle = "Two-step verification, backup codes, devices & alerts",
                                accent = Color(0xFF397BCC),
                                onClick = { settingsPage = "devices" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.Block,
                                title = "Blocked accounts",
                                subtitle = "Manage who can contact you",
                                accent = Color(0xFF718096),
                                onClick = { settingsPage = "blocked" }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Default.Download,
                                title = "Download my data",
                                subtitle = "Export your profile and posts as JSON",
                                accent = Color(0xFF0097A7),
                                onClick = { exportMyData(context, currentUser, posts) }
                            )
                            SettingsHubDivider()
                            SettingsHubRow(
                                icon = Icons.Outlined.WorkspacePremium,
                                title = "Convo Chat Premium",
                                subtitle = "Plans and exclusive features",
                                accent = Color(0xFFD18B12),
                                onClick = onPremium
                            )
                        }
                        } else {
                            SettingsPageHero(settingsPage, tr(settingsPageTitle(settingsPage)), tr(settingsPageSubtitle(settingsPage))) { settingsPage = "" }
                        }

                        if (settingsPage == "theme") {
                        val themeUnlocked = flagshipConfig.advancedThemesEnabled && viewModel.hasActivePremiumEntitlement()
                        val themeGroups = listOf(
                            Triple("Liquid glass", "Milky glass, neon and scene-matched looks (pair them with the Scenes chat themes)", listOf(
                                AppThemePreview("Milk Liquid Glass", Color(0xFFFBF8F3), Color.White, Color(0xFF6F78F0)),
                                AppThemePreview("Neon Glass", Color(0xFF0A0718), Color(0xFF181332), Color(0xFFB794FF)),
                                AppThemePreview("Matrix", Color(0xFF000502), Color(0xFF07140C), Color(0xFF39FF88)),
                                AppThemePreview("Dream Pink", Color(0xFFFFF4F9), Color.White, Color(0xFFE94B9A)),
                                AppThemePreview("Ocean Glass", Color(0xFF031523), Color(0xFF0A2840), Color(0xFF66C2FF))
                            )),
                            Triple("Light", "Bright, clean and easy on the eyes in daylight", listOf(
                                AppThemePreview("Aurora Light", Color(0xFFF7F5FF), Color.White, Color(0xFF765BFF)),
                                AppThemePreview("Light", Color(0xFFF0F2FA), Color.White, Color(0xFF5B6FE8)),
                                AppThemePreview("Frosted Light", Color(0xFFF5F5FF), Color(0xFFFCFAFF), Color(0xFF6554D9)),
                                AppThemePreview("Sky Light", Color(0xFFF3F9FF), Color.White, Color(0xFF1E88E5)),
                                AppThemePreview("Mint Light", Color(0xFFF2FCF8), Color.White, Color(0xFF0E9F75)),
                                AppThemePreview("Peach Light", Color(0xFFFFF6F2), Color.White, Color(0xFFE5674B)),
                                AppThemePreview("Lavender Light", Color(0xFFFAF6FF), Color.White, Color(0xFF8E5CF7)),
                                AppThemePreview("Sand Light", Color(0xFFFBF7F0), Color(0xFFFFFDF9), Color(0xFFA0672C))
                            )),
                            Triple("Dark", "Comfortable at night and kind to the battery", listOf(
                                AppThemePreview("Dynamic", Color(0xFF1B1530), Color(0xFF2A2245), Color(0xFF7C5CFC)),
                                AppThemePreview("Dark", Color(0xFF171A24), Color(0xFF222634), Color(0xFF8C9EFF)),
                                AppThemePreview("AMOLED", Color.Black, Color(0xFF121212), Color(0xFF9E8BFF)),
                                AppThemePreview("Midnight", Color(0xFF07030D), Color(0xFF190D28), Color(0xFFE3B9FF)),
                                AppThemePreview("Ocean", Color(0xFF001017), Color(0xFF062532), Color(0xFF83E6F2)),
                                AppThemePreview("Ocean Calm", Color(0xFF00151E), Color(0xFF07313D), Color(0xFF8CEBFF)),
                                AppThemePreview("Forest", Color(0xFF050D08), Color(0xFF102319), Color(0xFFA7E3AE)),
                                AppThemePreview("Chocolate", Color(0xFF100805), Color(0xFF21120D), Color(0xFFE8C7B5)),
                                AppThemePreview("Coffee", Color(0xFF110B07), Color(0xFF23170F), Color(0xFFE2B98E)),
                                AppThemePreview("Berry Night", Color(0xFF14060E), Color(0xFF2A0F20), Color(0xFFFF7AA8)),
                                AppThemePreview("Lo-fi", Color(0xFF110A1C), Color(0xFF221634), Color(0xFFFFB38A)),
                                AppThemePreview("Slate", Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF9EC1FF)),
                                AppThemePreview("Crimson", Color(0xFF120506), Color(0xFF250C0E), Color(0xFFFF6B6B)),
                                AppThemePreview("Emerald", Color(0xFF02100A), Color(0xFF0A2218), Color(0xFF4EE3A2))
                            )),
                            Triple("Anime & vivid", "Inspired by anime skies, neon cities and cozy evenings", listOf(
                                AppThemePreview("Anime Sky", Color(0xFFF1F8FF), Color.White, Color(0xFF2F80ED)),
                                AppThemePreview("Sakura Night", Color(0xFF120A16), Color(0xFF261729), Color(0xFFFF9EC4)),
                                AppThemePreview("Neo Tokyo", Color(0xFF05010F), Color(0xFF160B2C), Color(0xFFFF4FD8)),
                                AppThemePreview("Sunset Rooftop", Color(0xFF130C1F), Color(0xFF261A36), Color(0xFFFFB067)),
                                AppThemePreview("Nordic", Color(0xFF242933), Color(0xFF2E3440), Color(0xFF88C0D0)),
                                AppThemePreview("Terminal", Color(0xFF020402), Color(0xFF0C120C), Color(0xFF7CFC00)),
                                AppThemePreview("Ruby", Color(0xFF110307), Color(0xFF240A12), Color(0xFFFF5C7A)),
                                AppThemePreview("Rose Light", Color(0xFFFFF5F8), Color.White, Color(0xFFD6336C)),
                                AppThemePreview("Teal Light", Color(0xFFF1FBFA), Color.White, Color(0xFF00897B)),
                                AppThemePreview("Mono Light", Color(0xFFF8F9FA), Color.White, Color(0xFF212529))
                            )),
                            Triple("Premium", if (themeUnlocked) "Unlocked with Convo Premium" else "Unlock with Convo Premium", listOf(
                                AppThemePreview("Royal Gold", Color(0xFF120C02), Color(0xFF241806), Color(0xFFFFD166)),
                                AppThemePreview("Galaxy", Color(0xFF05030F), Color(0xFF140C2A), Color(0xFFB69CFF)),
                                AppThemePreview("Diamond", Color(0xFF070A1C), Color(0xFF131A3A), Color(0xFFE0E7FF)),
                                AppThemePreview("Sapphire", Color(0xFF040A1C), Color(0xFF0D1836), Color(0xFF7FA8FF)),
                                AppThemePreview("Liquid Aurora", Color(0xFF070817), Color(0xFF17152E), Color(0xFFC5B4FF)),
                                AppThemePreview("Glass Ocean", Color(0xFF001018), Color(0xFF062A38), Color(0xFF79ECFF)),
                                AppThemePreview("Glass Rose", Color(0xFF160711), Color(0xFF301327), Color(0xFFFFA9D6)),
                                AppThemePreview("Obsidian Neon", Color.Black, Color(0xFF0A0B12), Color(0xFF00F0D0)),
                                AppThemePreview("Neon Pulse", Color(0xFF0B0612), Color(0xFF1A0C27), Color(0xFFFF4DA6)),
                                AppThemePreview("Cyber Lime", Color(0xFF071006), Color(0xFF102014), Color(0xFFC8FF4D)),
                                AppThemePreview("Rose Quartz", Color(0xFF140810), Color(0xFF2A1423), Color(0xFFFFB3CF))
                            ))
                        )
                        themeGroups.forEach { (groupTitle, groupSubtitle, groupThemes) ->
                            SettingsHubGroup(groupTitle) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(groupSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    groupThemes.chunked(3).forEach { rowThemes ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            rowThemes.forEach { preview ->
                                                val locked = preview.name in PREMIUM_APP_THEMES && !themeUnlocked
                                                AppThemeTile(
                                                    preview = preview,
                                                    selected = currentTheme.equals(preview.name, ignoreCase = true),
                                                    locked = locked,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = {
                                                        if (viewModel.updateTheme(preview.name)) {
                                                            Toast.makeText(context, "${preview.name} theme applied", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "${preview.name} is a Convo Premium theme", Toast.LENGTH_LONG).show()
                                                            onPremium()
                                                        }
                                                    }
                                                )
                                            }
                                            repeat(3 - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (settingsPage == "call") {
                        Text(
                            "Your call skin is saved on this phone and used for audio, video and group calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        CallSkinPreferences.options.map { it.category }.distinct().forEach { category ->
                            SettingsHubGroup(category) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CallSkinPreferences.options.filter { it.category == category }.chunked(3).forEach { rowSkins ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            rowSkins.forEach { option ->
                                                val selected = callSkinId == option.id
                                                Column(
                                                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable {
                                                        CallSkinPreferences.select(context, option.id)
                                                        Toast.makeText(context, "${option.label} call skin applied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(18.dp))
                                                            .background(Brush.verticalGradient(option.background))
                                                            .border(if (selected) 3.dp else 1.dp, if (selected) option.accent else Color.White.copy(alpha = .16f), RoundedCornerShape(18.dp))
                                                    ) {
                                                        ThemeTextureLayer(option.texture, option.accent, .85f)
                                                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Box(Modifier.size(34.dp).clip(CircleShape).background(option.accent.copy(alpha = .35f)).border(1.5.dp, option.accent, CircleShape), contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                            }
                                                            Spacer(Modifier.height(8.dp))
                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
                                                                Box(Modifier.size(18.dp).clip(CircleShape).background(option.surface), contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, tint = option.accent, modifier = Modifier.size(11.dp)) }
                                                            }
                                                        }
                                                        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(option.accent), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Check, "Active", tint = Color.Black, modifier = Modifier.size(13.dp))
                                                        }
                                                    }
                                                    Spacer(Modifier.height(5.dp))
                                                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium)
                                                }
                                            }
                                            repeat(3 - rowSkins.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (settingsPage == "chat") {
                        val quickReaction by ChatPreferences.quickReaction.collectAsState()
                        val chatTextScale by ChatPreferences.textScale.collectAsState()
                        val enterToSend by ChatPreferences.enterToSend.collectAsState()
                        var customReaction by remember { mutableStateOf("") }
                        SettingsHubGroup("Quick reaction") {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Shown in place of Send while the message box is empty. Tap it to send instantly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChatPreferences.reactionChoices.chunked(6).forEach { rowEmojis ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowEmojis.forEach { emoji ->
                                            val selected = quickReaction == emoji
                                            Box(
                                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                                                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                                                    .clickable { ChatPreferences.setQuickReaction(context, emoji) },
                                                contentAlignment = Alignment.Center
                                            ) { Text(emoji, fontSize = 22.sp) }
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = customReaction,
                                        onValueChange = { customReaction = it.take(8) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        label = { Text(tr("Any other emoji")) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(onClick = {
                                        if (customReaction.isNotBlank()) { ChatPreferences.setQuickReaction(context, customReaction); customReaction = "" }
                                    }, enabled = customReaction.isNotBlank()) { Text(tr("Use")) }
                                }
                                Text(tr("Convo animated stickers"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                ConvoStickers.all.chunked(5).forEach { rowStickers ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowStickers.forEach { sticker ->
                                            val selected = quickReaction == sticker.token
                                            Box(
                                                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp))
                                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                                                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                                                    .clickable { ChatPreferences.setQuickReaction(context, sticker.token) },
                                                contentAlignment = Alignment.Center
                                            ) { ConvoStickerView(sticker.token, 44.dp) }
                                        }
                                    }
                                }
                                Text("Current: ${convoStickerPreview(quickReaction)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        SettingsHubGroup("Reading & typing") {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tr("Message text size"), fontWeight = FontWeight.Bold)
                                Slider(
                                    value = chatTextScale,
                                    onValueChange = { ChatPreferences.setTextScale(context, it) },
                                    valueRange = 0.85f..1.3f,
                                    steps = 8
                                )
                                Surface(shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.End)) {
                                    Text(tr("This is how your messages look"), color = MaterialTheme.colorScheme.onPrimary, fontSize = (14 * chatTextScale).sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(tr("Enter key sends"), fontWeight = FontWeight.Bold)
                                        Text(tr("Press Enter on the keyboard to send instead of adding a new line"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = enterToSend, onCheckedChange = { ChatPreferences.setEnterToSend(context, it) })
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(tr("Link previews"), fontWeight = FontWeight.Bold)
                                        Text(tr("Show a title, image and site icon for links in chats and posts"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = privacySettings.allowLinkPreviewFetch, onCheckedChange = { enabled -> PrivacyPreferences.update(context) { it.copy(allowLinkPreviewFetch = enabled) } })
                                }
                            }
                        }
                        SettingsHubGroup("More for chats") {
                            SettingsHubRow(icon = Icons.Outlined.Wallpaper, title = "Default chat background", subtitle = "Used in chats that do not have their own theme", trailingValue = ChatPreferences.defaultChatTheme.collectAsState().value.removePrefix(CHAT_IMAGE_THEME_PREFIX).let { if (it.startsWith("https://")) "Photo" else it }, accent = Color(0xFF8B62D9), onClick = { settingsPage = "backgrounds" })
                            SettingsHubDivider()
                            SettingsHubRow(icon = Icons.Outlined.MusicNote, title = "Notification & typing sounds", subtitle = "Use your own MP3 sounds", accent = Color(0xFFE5674B), onClick = { settingsPage = "sounds" })
                        }
                        }

                        if (settingsPage == "bubbles") {
                            SettingsHubGroup("Live preview & style") {
                                ChatAppearanceEditor(
                                    spec = ChatThemeCatalog.find(ChatPreferences.defaultChatTheme.collectAsState().value)?.takeIf { !it.adaptive },
                                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                                )
                            }
                        }

                        if (settingsPage == "sounds") {
                        val soundsVersion by ChatPreferences.soundsVersion.collectAsState()
                        val globalSoundUrls by viewModel.globalSoundUrls.collectAsState()
                        var pendingSoundKind by remember { mutableStateOf<String?>(null) }
                        var soundBusy by remember { mutableStateOf(false) }
                        val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            val kind = pendingSoundKind
                            pendingSoundKind = null
                            if (uri != null && kind != null) {
                                val displayName = runCatching {
                                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                                        if (c.moveToFirst()) c.getString(0) else null
                                    }
                                }.getOrNull() ?: "Custom sound"
                                soundBusy = true
                                uiScope.launch {
                                    val error = AppSoundStore.importLocal(context, uri, kind, displayName)
                                    soundBusy = false
                                    if (error == null) {
                                        AppSoundStore.playCustom(context, kind)
                                        Toast.makeText(context, "Sound saved on this phone", Toast.LENGTH_SHORT).show()
                                    } else Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        Text("Pick an MP3 (or any audio up to 15 seconds and 2 MB). It is saved on this phone and replaces the built-in sound. If Convo publishes a default sound, it downloads quietly in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                        var likeSoundOn by remember { mutableStateOf(LikeSound.isEnabled(context)) }
                        Row(
                            Modifier.fillMaxWidth().liquidCard(shape = RoundedCornerShape(22.dp)).clickable { likeSoundOn = !likeSoundOn; LikeSound.setEnabled(context, likeSoundOn) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr("Like sound"), fontWeight = FontWeight.Bold)
                                Text(tr("Play a soft pop when you like or react to a message"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(likeSoundOn, { likeSoundOn = it; LikeSound.setEnabled(context, it) })
                        }
                        listOf(
                            Triple(AppSoundStore.NOTIFICATION, "Message notification", Icons.Outlined.NotificationsActive),
                            Triple(AppSoundStore.TYPING, "Typing indicator", Icons.Outlined.Keyboard),
                            Triple(AppSoundStore.LIKE, "Like / reaction", Icons.Outlined.FavoriteBorder)
                        ).forEach { (kind, label, icon) ->
                            val description = remember(soundsVersion, globalSoundUrls) { AppSoundStore.describe(context, kind) }
                            val hasLocal = remember(soundsVersion) { AppSoundStore.hasLocal(context, kind) }
                            SettingsHubGroup(label) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(description, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (hasLocal) "Your sound" else "Default", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            if (kind == AppSoundStore.LIKE) LikeSound.play(context, force = true)
                                            else if (!AppSoundStore.playCustom(context, kind)) Toast.makeText(context, "Using the built-in sound", Toast.LENGTH_SHORT).show()
                                        }) { Icon(Icons.Default.PlayArrow, "Preview sound") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = { pendingSoundKind = kind; soundPicker.launch("audio/*") }, enabled = !soundBusy, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(if (hasLocal) "Change" else "Choose MP3")
                                        }
                                        if (hasLocal) OutlinedButton(onClick = { AppSoundStore.clearLocal(context, kind); Toast.makeText(context, "Back to the default sound", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f)) { Text(tr("Reset")) }
                                    }
                                }
                            }
                        }
                        if (soundBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Turn sounds on or off in Notifications & calls.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (settingsPage == "backgrounds") {
                        val defaultChatTheme by ChatPreferences.defaultChatTheme.collectAsState()
                        val backgroundImages by viewModel.chatBackgroundImages.collectAsState()
                        Text("Pick the default background for your chats. Any chat can still use its own theme from the chat menu, Chat theme.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                        ChatThemeGallery(
                            selected = defaultChatTheme,
                            premiumActive = viewModel.hasActivePremiumEntitlement(),
                            images = backgroundImages,
                            onSelect = { value ->
                                ChatPreferences.setDefaultChatTheme(context, value)
                                Toast.makeText(context, "Default chat background updated", Toast.LENGTH_SHORT).show()
                            },
                            onLocked = {
                                Toast.makeText(context, "This background is part of Convo Premium", Toast.LENGTH_LONG).show()
                                onPremium()
                            },
                            modifier = Modifier.fillMaxWidth().height(640.dp)
                        )
                        }

                        if (settingsPage == "viewers") {
                        val profileViewers by ProfileViewerStore.viewers.collectAsState()
                        val shareVisits by ProfileViewerStore.shareVisits.collectAsState()
                        val viewersUnlocked = viewModel.hasActivePremiumEntitlement()
                        var confirmClearViewers by remember { mutableStateOf(false) }
                        val nowMs = rememberTickingNow(60_000L)
                        val dayAgo = nowMs - 24L * 60 * 60 * 1000
                        val weekAgo = nowMs - 7L * 24 * 60 * 60 * 1000
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                "Today" to profileViewers.count { it.lastViewedAt >= dayAgo },
                                "7 days" to profileViewers.count { it.lastViewedAt >= weekAgo },
                                "All" to profileViewers.size
                            ).forEach { (label, count) ->
                                Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f)) {
                                    Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        SettingsHubGroup(if (viewersUnlocked) "Recent visitors" else "Recent visitors (Premium)") {
                            if (!viewersUnlocked) {
                                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Lock, null, tint = Color(0xFFD18B12), modifier = Modifier.size(34.dp))
                                    Text("See exactly who viewed your profile", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                                    Text("Everyone sees the count. Names and times are part of Convo Premium.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    Button(onClick = onPremium) { Icon(Icons.Outlined.WorkspacePremium, null); Spacer(Modifier.width(6.dp)); Text(tr("Get Premium")) }
                                }
                            } else if (profileViewers.isEmpty()) {
                                Text("No visits yet. New visits appear here as they happen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
                            } else {
                                profileViewers.sortedByDescending { it.lastViewedAt }.take(100).forEachIndexed { index, viewer ->
                                    if (index > 0) SettingsHubDivider()
                                    val viewerUser = allUsers.firstOrNull { it.uid == viewer.viewerId }
                                    Row(
                                        Modifier.fillMaxWidth().clickable(enabled = viewerUser != null) { viewerUser?.let { onProfileSelected(it) } }.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val avatar = viewerUser?.profileImageUrl?.takeIf { it.isNotBlank() } ?: viewer.imageUrl
                                        if (avatar.isNotBlank()) {
                                            AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp).clip(CircleShape))
                                        } else {
                                            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                                Text(viewer.name.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(viewerUser?.name?.takeIf { it.isNotBlank() } ?: viewer.name.ifBlank { "Convo user" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                buildString {
                                                    append(relativeTimeLabel(viewer.lastViewedAt, nowMs))
                                                    if (viewer.visits > 1) append(" · ${viewer.visits} visits")
                                                },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        SettingsHubGroup("Privacy") {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Share my visits", fontWeight = FontWeight.Bold)
                                    Text("When off, people are not told that you viewed their profile", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = shareVisits, onCheckedChange = { ProfileViewerStore.setShareVisits(context, it) })
                            }
                            SettingsHubDivider()
                            SettingsHubRow(icon = Icons.Outlined.DeleteOutline, title = "Clear visitor history", subtitle = "History is stored only on this phone", accent = Color(0xFFE53935), onClick = { confirmClearViewers = true }, enabled = profileViewers.isNotEmpty())
                        }
                        if (confirmClearViewers) {
                            AlertDialog(
                                onDismissRequest = { confirmClearViewers = false },
                                title = { Text("Clear visitor history?") },
                                text = { Text("This removes the saved list from this phone. It cannot be undone.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmClearViewers = false
                                        currentUser?.uid?.let { ProfileViewerStore.clear(context, it) }
                                    }) { Text(tr("Clear"), color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { confirmClearViewers = false }) { Text(tr("Cancel")) } }
                            )
                        }
                        }

                        if (settingsPage == "profile") {
                        // 2. Profile Details Modification
                        currentUser?.let { user ->
                            var inputName by remember { mutableStateOf(user.name) }
                            var inputDob by remember { mutableStateOf(user.dob) }
                            var inputBio by remember { mutableStateOf(user.bio) }
                            var inputProfessionalTitle by remember { mutableStateOf(user.professionalTitle) }
                            var inputPronouns by remember { mutableStateOf(user.pronouns) }
                            var inputPublicContactEmail by remember { mutableStateOf(user.publicContactEmail) }
                            var inputCoverUrl by remember { mutableStateOf(user.coverImageUrl) }
                            var coverScale by remember { mutableFloatStateOf(user.coverScale.coerceIn(1f, 2f)) }
                            var coverOffsetY by remember { mutableFloatStateOf(user.coverOffsetY.coerceIn(-1f, 1f)) }
                            var inputProfileUrl by remember { mutableStateOf(user.profileImageUrl) }
                            var isUploadingAvatar by remember { mutableStateOf(false) }
                            var isUploadingCover by remember { mutableStateOf(false) }
                            var profileHidden by remember { mutableStateOf(user.profileHidden) }
                            var activeProfileType by remember { mutableStateOf(user.activeProfileType.ifBlank { "Personal" }) }
                            var awayReplyEnabled by remember { mutableStateOf(user.awayReplyEnabled) }
                            var awayReplyText by remember { mutableStateOf(user.awayReplyText) }
                            var awayReplyUntilText by remember { mutableStateOf(if (user.awayReplyUntil > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(user.awayReplyUntil)) else "") }
                            var socialInstagram by remember { mutableStateOf(user.socialLinks["instagram"].orEmpty()) }
                            var socialLinkedIn by remember { mutableStateOf(user.socialLinks["linkedin"].orEmpty()) }
                            var socialTwitter by remember { mutableStateOf(user.socialLinks["twitter"].orEmpty()) }

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

                            val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                                uri ?: return@rememberLauncherForActivityResult
                                isUploadingCover = true
                                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                                if (bytes == null) isUploadingCover = false else viewModel.uploadFileToSupabase(
                                    bucket = "avatars", fileName = "cover_${user.uid}_${System.currentTimeMillis()}.jpg", fileBytes = bytes, contentType = context.contentResolver.getType(uri) ?: "image/jpeg",
                                    onSuccess = { inputCoverUrl = it; isUploadingCover = false; coverScale = 1f; coverOffsetY = 0f },
                                    onFailure = { isUploadingCover = false; Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                )
                            }

                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(profileSectionRequester)
                                    .liquidCard(shape = RoundedCornerShape(28.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFF637BFF).copy(alpha = .22f), Color(0xFFE755B6).copy(alpha = .10f), Color.Transparent), endY = 700f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Edit your profile",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Text(
                                        text = "Tap the photo to change it. Changes show on your profile after you save.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                            .clickable { galleryLauncher.launch("image/*") }
                                            .border(3.dp, ProfileRingBrush, CircleShape),
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
                                        label = { Text(tr("Bio")) },
                                        placeholder = { Text(tr("Tell people about yourself")) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = inputProfessionalTitle,
                                        onValueChange = { inputProfessionalTitle = it.take(80) },
                                        label = { Text("Professional title (optional)") },
                                        placeholder = { Text("Designer, Founder, Student...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputPronouns,
                                        onValueChange = { inputPronouns = it.take(40) },
                                        label = { Text("Pronouns (optional)") },
                                        placeholder = { Text("They/Them, She/Her, He/Him") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputPublicContactEmail,
                                        onValueChange = { inputPublicContactEmail = it.take(160) },
                                        label = { Text("Public contact email (optional)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Active profile", fontWeight = FontWeight.Bold)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        listOf("Personal", "Creator", "Work", "Student").forEach { type ->
                                            FilterChip(
                                                selected = activeProfileType.equals(type, ignoreCase = true),
                                                onClick = { activeProfileType = type },
                                                label = { Text(type, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = activeProfileType,
                                        onValueChange = { activeProfileType = it.take(24) },
                                        label = { Text("Custom profile label (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    Text("Digital contact card", fontWeight = FontWeight.Bold)
                                    Text("Add public social links to your profile card.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(socialInstagram, { socialInstagram = it.take(240) }, label = { Text("Instagram URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                                    OutlinedTextField(socialLinkedIn, { socialLinkedIn = it.take(240) }, label = { Text("LinkedIn URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                                    OutlinedTextField(socialTwitter, { socialTwitter = it.take(240) }, label = { Text("X / Twitter URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Hide profile from new discovery", fontWeight = FontWeight.Bold)
                                            Text("Friends and existing chat contacts can still see you.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = profileHidden, onCheckedChange = { profileHidden = it })
                                    }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tr("Automatic away reply"), fontWeight = FontWeight.Bold)
                                            Text(tr("Reply when you are unavailable."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = awayReplyEnabled, onCheckedChange = { awayReplyEnabled = it })
                                    }
                                    if (awayReplyEnabled) {
                                        OutlinedTextField(
                                            value = awayReplyText,
                                            onValueChange = { awayReplyText = it.take(240) },
                                            label = { Text(tr("Away reply message")) },
                                            placeholder = { Text("I am offline right now. I will reply later.") },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 3,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        OutlinedTextField(
                                            value = awayReplyUntilText,
                                            onValueChange = { awayReplyUntilText = it.take(16) },
                                            label = { Text("Active until (yyyy-MM-dd HH:mm, optional)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(tr("Cover photo"), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                                    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))), contentAlignment = Alignment.Center) {
                                        ProfileCoverArt(inputCoverUrl, user.uid, Modifier.fillMaxSize(), coverScale, coverOffsetY)
                                        if (isUploadingCover) CircularProgressIndicator()
                                    }
                                    OutlinedButton(onClick = { coverLauncher.launch("image/*") }, enabled = !isUploadingCover, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(if (inputCoverUrl.isBlank()) "Upload cover photo" else "Replace cover photo") }
                                    CoverPresetPicker(selectedUrl = inputCoverUrl, onSelect = { inputCoverUrl = it; coverScale = 1f; coverOffsetY = 0f })
                                    if (inputCoverUrl.isNotBlank() && !inputCoverUrl.startsWith("preset:")) {
                                        Text(tr("Zoom"), fontSize = 11.sp); Slider(coverScale, { coverScale = it }, valueRange = 1f..2f)
                                        Text(tr("Vertical position"), fontSize = 11.sp); Slider(coverOffsetY, { coverOffsetY = it }, valueRange = -1f..1f)
                                    }
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
                                                coverScale = coverScale,
                                                coverOffsetY = coverOffsetY,
                                                profileHidden = profileHidden,
                                                activeProfileType = activeProfileType,
                                                pronouns = inputPronouns.trim(),
                                                professionalTitle = inputProfessionalTitle.trim(),
                                                publicContactEmail = inputPublicContactEmail.trim(),
                                                awayReplyEnabled = awayReplyEnabled,
                                                awayReplyText = awayReplyText,
                                                awayReplyUntil = awayReplyUntilText.trim().takeIf { it.isNotBlank() }?.let { value -> runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(value)?.time ?: 0L }.getOrDefault(0L) } ?: 0L,
                                                socialLinks = mapOf("instagram" to socialInstagram.trim(), "linkedin" to socialLinkedIn.trim(), "twitter" to socialTwitter.trim()),
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
                                        Text(tr("Save Changes"), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        }

                        if (settingsPage == "privacy") {
                        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(tr("App lock"), fontWeight = FontWeight.Bold)
                                        Text(if (lockEnabled) "PIN and notification privacy enabled" else "Protect Convo Chat with PIN or biometrics", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = lockEnabled, onCheckedChange = {
                                        if (it) showLockSetup = true else { AppLockManager.disable(context); lockEnabled = false }
                                    })
                                }
                                if (lockEnabled && AppLockManager.canUseBiometric(context)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tr("Biometric unlock"), Modifier.weight(1f))
                                        Switch(AppLockManager.isBiometricEnabled(context), { AppLockManager.setBiometric(context, it) })
                                    }
                                }
                            }
                        }

                        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().bringIntoViewRequester(privacySectionRequester)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(tr("Privacy & Security"), fontWeight = FontWeight.Bold)
                                        Text("Control presence, receipts, AI consent and sensitive-screen protection.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                PrivacyToggleRow("Show online status", privacySettings.showOnlineStatus) {
                                    PrivacyPreferences.update(context) { it.copy(showOnlineStatus = !it.showOnlineStatus) }
                                }
                                PrivacyToggleRow("Show last seen", privacySettings.showLastSeen) {
                                    PrivacyPreferences.update(context) { it.copy(showLastSeen = !it.showLastSeen) }
                                }
                                PrivacyToggleRow("Show typing and read receipts", privacySettings.showTypingStatus && privacySettings.sendReadReceipts) {
                                    PrivacyPreferences.update(context) { it.copy(showTypingStatus = !it.showTypingStatus, sendReadReceipts = !it.sendReadReceipts) }
                                }
                                PrivacyToggleRow("Allow AI processing", privacySettings.allowAiProcessing) {
                                    PrivacyPreferences.update(context) { it.copy(allowAiProcessing = !it.allowAiProcessing) }
                                }
                                PrivacyToggleRow("Hide notification content", privacySettings.hideNotificationContent) {
                                    PrivacyPreferences.update(context) { it.copy(hideNotificationContent = !it.hideNotificationContent) }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(tr("Screenshot protection"), fontWeight = FontWeight.SemiBold)
                                        Text("Always on inside chats and calls. Screenshots are allowed everywhere else.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(tr("Disappearing messages"), fontWeight = FontWeight.SemiBold)
                                        Text(tr("Outgoing messages hide after the selected time"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val expiryLabel = when (privacySettings.disappearingMessageSeconds) {
                                        10 -> "10 sec"
                                        60 -> "1 min"
                                        300 -> "5 min"
                                        3600 -> "1 hour"
                                        else -> "Off"
                                    }
                                    TextButton(onClick = {
                                        val next = when (privacySettings.disappearingMessageSeconds) {
                                            0 -> 10
                                            10 -> 60
                                            60 -> 300
                                            300 -> 3600
                                            else -> 0
                                        }
                                        PrivacyPreferences.update(context) { it.copy(disappearingMessageSeconds = next) }
                                    }) { Text(expiryLabel) }
                                }
                            }
                        }
                        }

                        if (settingsPage == "notifications") {
                        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().bringIntoViewRequester(notificationsSectionRequester)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(tr("Sounds & activity"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(tr("Control notification and live typing feedback."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(tr("Notification sounds"), Modifier.padding(start = 12.dp).weight(1f))
                                    Switch(
                                        checked = notificationSounds,
                                        onCheckedChange = { viewModel.updateSoundPreferences(it, typingSounds) }
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(tr("Typing indicator sound"), Modifier.padding(start = 12.dp).weight(1f))
                                    Switch(
                                        checked = typingSounds,
                                        onCheckedChange = { viewModel.updateSoundPreferences(notificationSounds, it) }
                                    )
                                }
                                val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
                                val systemNotificationManager = context.getSystemService(android.app.NotificationManager::class.java)
                                val appNotificationsEnabled = notificationManager.areNotificationsEnabled()
                                val callChannelImportance = if (Build.VERSION.SDK_INT >= 26) {
                                    systemNotificationManager.getNotificationChannel("convo_calls_v4")?.importance
                                        ?: android.app.NotificationManager.IMPORTANCE_HIGH
                                } else android.app.NotificationManager.IMPORTANCE_HIGH
                                val callChannelEnabled = callChannelImportance >= android.app.NotificationManager.IMPORTANCE_HIGH
                                val fullScreenAllowed = Build.VERSION.SDK_INT < 34 || systemNotificationManager.canUseFullScreenIntent()
                                val callAlertsReady = appNotificationsEnabled && callChannelEnabled && fullScreenAllowed
                                Surface(
                                    color = if (callAlertsReady) Color(0xFF1F7A4D).copy(alpha = .14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = .6f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (callAlertsReady) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (callAlertsReady) Color(0xFF2CBF79) else MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(9.dp))
                                        Column {
                                            Text(if (callAlertsReady) "Lock-screen call alerts are enabled" else "Incoming-call access needs attention", fontWeight = FontWeight.Bold)
                                            Text("Android can still choose a heads-up banner while the phone is unlocked.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (!appNotificationsEnabled) {
                                    OutlinedButton(
                                        onClick = {
                                            val settingsIntent = if (Build.VERSION.SDK_INT >= 26) {
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                            context.startActivity(settingsIntent)
                                        }, modifier = Modifier.fillMaxWidth()
                                    ) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(8.dp)); Text(tr("Enable app notifications")) }
                                }
                                if (Build.VERSION.SDK_INT >= 26 && !callChannelEnabled) {
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .putExtra(Settings.EXTRA_CHANNEL_ID, "convo_calls_v4"))
                                        }, modifier = Modifier.fillMaxWidth()
                                    ) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text(tr("Enable incoming-call channel")) }
                                }
                                if (Build.VERSION.SDK_INT >= 34 && !fullScreenAllowed) {
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            })
                                        }, modifier = Modifier.fillMaxWidth()
                                    ) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text(tr("Allow full-screen incoming calls")) }
                                }
                            }
                        }
                        }

                        if (settingsPage == "devices") {
                        TwoFactorCard()
                        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().bringIntoViewRequester(devicesSectionRequester)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(9.dp)); Text(tr("Devices & sessions"), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); IconButton(onClick = viewModel::refreshDeviceSessions) { Icon(Icons.Default.Refresh, "Refresh") } }
                                Text("Approximate city/region comes from the connection edge. Exact home location and full IP are not stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (deviceSessions.isEmpty()) TextButton(onClick = viewModel::refreshDeviceSessions) { Text(tr("Load signed-in devices")) }
                                deviceSessions.take(3).forEach { session ->
                                    DeviceSessionRow(session = session, onDelete = { deviceSessionPendingDelete = it })
                                }
                                if (deviceSessions.size > 3) {
                                    OutlinedButton(onClick = { showAllDeviceSessions = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.MoreHoriz, null); Spacer(Modifier.width(7.dp)); Text("More devices (${deviceSessions.size - 3})")
                                    }
                                }
                                if (deviceSessions.isNotEmpty()) {
                                    TextButton(onClick = { showClearDeviceHistoryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.ClearAll, null)
                                        Spacer(Modifier.width(7.dp))
                                        Text(tr("Delete all device history"))
                                    }
                                }
                                Button(onClick = { viewModel.logout { onSignOut() } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SwitchAccount, null); Spacer(Modifier.width(7.dp)); Text(tr("Switch account")) }
                                OutlinedButton(onClick = { viewModel.logoutAllDevices { ok, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show(); if (ok) onSignOut() } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(7.dp)); Text(tr("Log out all devices")) }
                                TextButton(onClick = { showReportDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(7.dp)); Text("Report a problem to AI/Admin") }
                            }
                        }
                        }

                        if (showAllDeviceSessions) {
                            AlertDialog(
                                onDismissRequest = { showAllDeviceSessions = false },
                                title = { Text(tr("All signed-in devices")) },
                                text = {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                                        items(deviceSessions, key = { it.sessionId }) { session -> DeviceSessionRow(session = session, onDelete = { deviceSessionPendingDelete = it }) }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showAllDeviceSessions = false }) { Text(tr("Done")) } }
                            )
                        }
                        if (showClearDeviceHistoryDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearDeviceHistoryDialog = false },
                                title = { Text("Delete all device history?") },
                                text = { Text("Every saved device-session record will be deleted from the database. This does not sign you out of the current device.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showClearDeviceHistoryDialog = false
                                        viewModel.clearDeviceSessions { _, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                                    }) { Text(tr("Delete all"), color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { showClearDeviceHistoryDialog = false }) { Text(tr("Cancel")) } }
                            )
                        }
                        deviceSessionPendingDelete?.let { session ->
                            AlertDialog(
                                onDismissRequest = { deviceSessionPendingDelete = null },
                                title = { Text("Delete device history?") },
                                text = { Text("${session.deviceName} will be removed from the database and this device list.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        deviceSessionPendingDelete = null
                                        viewModel.deleteDeviceSession(session.sessionId) { ok, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                                    }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { deviceSessionPendingDelete = null }) { Text(tr("Cancel")) } }
                            )
                        }

                        if (settingsPage == "smart") {
                            SmartSettingsPage()
                        }

                        if (settingsPage == "language") {
                            LanguageSettingsPage()
                        }

                        if (settingsPage == "blocked") {
                        // 3. Blocked Users Management
                        val allUsers by viewModel.usersState.collectAsState()
                        currentUser?.let { user ->
                            val blockedUsersList = allUsers.filter { user.blockedUsers.contains(it.uid) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(blockedSectionRequester),
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
                                                        Text(tr("Unblock"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
                6 -> {
                    if (!flagshipConfig.reelsEnabled && !isAdmin) {
                        LaunchedEffect(Unit) { viewModel.setCurrentTab(0) }
                    } else if (flagshipConfig.tiktokModeEnabled) {
                        TikTokBrowserScreen(onClose = { viewModel.setCurrentTab(0) })
                    } else {
                        ReelsFeedScreen(
                            posts = posts,
                            users = allUsers,
                            viewModel = viewModel,
                            initialPostId = selectedReelPostId,
                            onProfileSelected = onProfileSelected,
                            onCreateReel = { viewModel.prepareComposer("reel"); onCreatePost() },
                            onClose = { viewModel.setCurrentTab(0) }
                        )
                    }
                }
                7 -> AdminAnalyticsDashboard(
                    users = allUsers,
                    posts = posts,
                    stories = stories,
                    reports = adminReports,
                    onRefreshReports = { viewModel.loadAdminReports() },
                    onOpenControlCenter = { viewModel.setCurrentTab(3) }
                )
                5 -> {
                    var peopleQuery by remember { mutableStateOf("") }
                    val visiblePeople = allUsers.filter {
                        peopleQuery.isBlank() || it.name.contains(peopleQuery, true) || it.username.contains(peopleQuery, true)
                    }.sortedWith(
                        compareByDescending<User> { it.role == "moderator" }
                            .thenByDescending { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > System.currentTimeMillis()) }
                            .thenByDescending { it.createdAt }
                    )
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Text(tr("Discover people"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(tr("Find new accounts and build your circle."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = peopleQuery,
                            onValueChange = { peopleQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text(tr("Search people")) },
                            singleLine = true,
                            shape = CircleShape
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(visiblePeople, key = { it.uid }) { person ->
                                val isFriend = currentUser?.friends?.contains(person.uid) == true
                                val isFollowing = currentUser?.following?.contains(person.uid) == true
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
                                        IconButton(onClick = { viewModel.toggleFollow(person) }) {
                                            Icon(if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd, if (isFollowing) "Unfollow" else "Follow", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        when {
                                            isFriend -> AssistChip(onClick = { onUserSelected(person) }, label = { Text(tr("Message")) })
                                            isRequested -> OutlinedButton(onClick = {
                                                viewModel.cancelFriendRequest(person.uid) { ok ->
                                                    Toast.makeText(context, if (ok) "Request cancelled" else "Could not cancel", Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text(tr("Cancel")) }
                                            else -> Button(onClick = {
                                                viewModel.sendFriendRequest(person) { _, message ->
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text(tr("Add")) }
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
                    val sender = users.find { it.uid == notif.senderId }
                    InAppMessageBanner(
                        data = notif,
                        sender = sender,
                        onOpen = {
                            if (sender != null) onUserSelected(sender)
                            viewModel.dismissInAppNotification()
                        },
                        onDismiss = { viewModel.dismissInAppNotification() }
                    )
                }
            }
        }
    }

    if (coachTourActive && currentTab != 6) {
        CoachMarkOverlay(CoachTour.homeSteps, onFinish = { coachTourActive = false })
    }

    // A valid published update has the highest priority. The dialog is truly
    // blocking for every non-admin user until PackageManager confirms install.
    // A maintenance timer ends maintenance automatically for users when it reaches zero.
    var maintenanceExpired by remember(flagshipConfig.maintenanceEndsAt) { mutableStateOf(false) }
    LaunchedEffect(flagshipConfig.maintenanceEndsAt) {
        val end = flagshipConfig.maintenanceEndsAt
        if (end > 0L) {
            val wait = end - System.currentTimeMillis()
            if (wait > 0L) kotlinx.coroutines.delay(wait)
            maintenanceExpired = true
        }
    }
    if (updateRequired) {
        FlagshipUpdateDialog(flagshipConfig, mandatory = true, onLater = {})
    } else if (!isAdmin && flagshipConfig.maintenanceMode && !maintenanceExpired) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            MaintenanceCountdownCard(
                title = flagshipConfig.maintenanceTitle.ifBlank { "Convo Chat maintenance" },
                message = flagshipConfig.noticeBody.ifBlank { "We are preparing a better Convo Chat experience. Please try again later." },
                endsAt = flagshipConfig.maintenanceEndsAt,
                startedAt = flagshipConfig.updatedAt
            )
        }
    } else if (flagshipConfig.noticeEnabled && !dismissNotice && flagshipConfig.noticeTitle.isNotBlank()) {
        AnimatedGlobalNotice(
            title = flagshipConfig.noticeTitle,
            body = flagshipConfig.noticeBody,
            onDismiss = { dismissNotice = true }
        )
    }

    if (showLockSetup) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var biometric by remember { mutableStateOf(AppLockManager.canUseBiometric(context)) }
        AlertDialog(
            onDismissRequest = { showLockSetup = false },
            title = { Text("Set Convo Chat lock", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a 4–8 digit PIN. Notification content will be hidden while lock is enabled.")
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text(tr("PIN")) }, singleLine = true)
                    OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit).take(8) }, label = { Text(tr("Confirm PIN")) }, singleLine = true)
                    if (AppLockManager.canUseBiometric(context)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable biometric unlock", Modifier.weight(1f)); Switch(biometric, { biometric = it }) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pin == confirmPin && AppLockManager.setLock(context, pin, biometric)) {
                        lockEnabled = true; showLockSetup = false
                    } else Toast.makeText(context, "PINs must match and contain 4–8 digits", Toast.LENGTH_LONG).show()
                }) { Text(tr("Enable lock")) }
            },
            dismissButton = { TextButton(onClick = { showLockSetup = false }) { Text(tr("Cancel")) } }
        )
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
            onReplyMessage = { request, reply -> viewModel.replyToMessageRequest(request, reply) },
            onRejectMessage = { viewModel.respondToMessageRequest(it, false) }
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
            title = { Text(tr("Create a group"), fontWeight = FontWeight.Bold) },
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
                        placeholder = { Text(tr("Group Name")) },
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
                    Text(tr("Create"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text(tr("Cancel"))
                }
            }
        )
    }

    if (showFunCampaign) FridayFunDialog(flagshipConfig.funCampaignTitle, flagshipConfig.funCampaignBody) { funPrefs.edit().putBoolean(funKey, true).apply(); showFunCampaign=false }

    if (!showFunCampaign && showPremiumUpsell) AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFB300), modifier = Modifier.size(50.dp)) },
        title = { Text(tr("Unlock Convo Premium"), fontWeight = FontWeight.Black) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("✨ Verified Premium badge"); Text("🤖 Advanced AI and smart tools"); Text("🎨 Exclusive Liquid Glass themes"); Text("📊 Story and creator insights"); Text("🎵 Custom call ringtone"); Text("💬 Priority Admin support") } },
        confirmButton = { Button(onClick = { showPremiumUpsell = false; onPremium() }) { Text(tr("View Premium")) } },
        dismissButton = { TextButton(onClick = { currentUser?.uid?.let { upsellPrefs.edit().putBoolean("dismissed_$it", true).apply() }; showPremiumUpsell = false }) { Text("Skip forever") } }
    )

    if (showPremiumWelcome) currentUser?.let { premiumUser ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFB300), modifier = Modifier.size(54.dp)) },
            title = { Text("🎉 Congratulations!", fontWeight = FontWeight.Black, textAlign = TextAlign.Center) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${premiumUser.name}, Convo Chat Premium is active!", textAlign = TextAlign.Center); Spacer(Modifier.height(10.dp)); val days = if (premiumUser.premiumPlan == "lifetime") "Lifetime" else "${((premiumUser.premiumUntil - System.currentTimeMillis()).coerceAtLeast(0) / 86_400_000L) + 1} days remaining"; Text(days, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold); if (premiumUser.premiumPlan == "trial") { Spacer(Modifier.height(8.dp)); Text("Your one-time 14-day free trial has started.", textAlign = TextAlign.Center) } } },
            confirmButton = { Button(onClick = { premiumWelcomeKey?.let { context.getSharedPreferences("convo_premium_welcome", Context.MODE_PRIVATE).edit().putBoolean(it, true).apply() }; showPremiumWelcome = false }) { Text(tr("Explore Premium")) } }
        )
    }

    if (showReportDialog) {
        var category by remember { mutableStateOf("app_problem") }
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report to Convo AI & Admin", fontWeight = FontWeight.ExtraBold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("app_problem", "user", "post", "safety").forEach { FilterChip(category == it, { category = it }, { Text(it.replace('_',' ')) }) } }; OutlinedTextField(details, { details = it.take(3000) }, label = { Text(tr("What happened?")) }, minLines = 5, modifier = Modifier.fillMaxWidth()); Text("AI can help diagnose the issue; submitting sends it to ADMIN RAKIB.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } },
            confirmButton = { Button(onClick = { viewModel.submitUserReport("", category, details) { ok, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show(); if (ok) showReportDialog = false } }, enabled = details.length >= 10) { Text(tr("Submit report")) } },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text(tr("Cancel")) } }
        )
    }

    mfaGate?.let { gate ->
        if (!forcedLogout && !accountBanned) MfaGateDialog(
            method = gate.method.ifBlank { "email" },
            maskedEmail = gate.email.ifBlank { "your e-mail" },
            onVerified = { viewModel.mfaVerified() },
            onSignOut = { viewModel.logout { onSignOut() } }
        )
    }
    if (forcedLogout && !accountBanned) AlertDialog(onDismissRequest = {}, icon = { Icon(Icons.Default.Logout, null) }, title = { Text("Signed out by admin") }, text = { Text("For security, this session was ended by the administrator. Please sign in again.") }, confirmButton = { Button(onClick = { viewModel.logout { onSignOut() } }) { Text(tr("Sign in again")) } })
    if (accountBanned) AlertDialog(onDismissRequest = {}, title = { Text(tr("Account suspended")) }, text = { Text("This account was disabled by ADMIN RAKIB after review. Contact support from another account if you believe this is a mistake.") }, confirmButton = { Button(onClick = { viewModel.logout { onSignOut() } }) { Text(tr("Sign out")) } })

    if (showAnalytics) {
        AccountAnalyticsSheet(currentUser = currentUser, posts = posts.filter { it.senderId == currentUser?.uid }, onDismiss = { showAnalytics = false })
    }

    if (showCreateHub) {
        CreateHubSheet(viewModel = viewModel, onDismiss = { showCreateHub = false }, onCreatePost = onCreatePost)
    }

    selectedStoryIndex?.let { index ->
        FullScreenStoryViewer(
            stories = storyPlaybackQueue,
            storyIndex = index,
            onPrevious = { selectedStoryIndex = (index - 1).coerceAtLeast(0) },
            onNext = {
                val next = (index + 1).coerceAtMost(storyPlaybackQueue.size)
                storyPlaybackQueue.getOrNull(next)?.let(viewModel::recordStoryView)
                selectedStoryIndex = next
            },
            onDismiss = { selectedStoryIndex = null },
            onReact = { story, emoji -> viewModel.reactToStory(story.id, emoji) },
            onComment = { story, text -> viewModel.commentOnStory(story.id, text) },
            onDelete = { story ->
                viewModel.deleteStory(story.id)
                selectedStoryIndex = null
            },
            onSpotlight = { story -> viewModel.spotlightStory(story) { _, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }
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
    var searchFilter by remember { mutableStateOf("All") }
    var fullScreenVideo by remember { mutableStateOf<String?>(null) }
    val normalized = query.trim()
    val searchContext = LocalContext.current
    var recentSearches by remember { mutableStateOf(SmartSearch.recent(searchContext)) }
    val trendingTags = remember(posts) { SmartSearch.trendingTags(posts) }
    val parsedQuery = remember(normalized) { SmartSearch.parse(normalized) }
    fun rememberQuery() { SmartSearch.remember(searchContext, normalized); recentSearches = SmartSearch.recent(searchContext) }
    val matchedUsers = remember(parsedQuery, users, searchFilter) {
        if (normalized.isBlank() || (searchFilter != "All" && searchFilter != "People")) emptyList() else SmartSearch.rankUsers(parsedQuery, users).take(30)
    }
    val matchedPosts = remember(parsedQuery, posts, searchFilter) {
        if (normalized.isBlank() || searchFilter == "People") emptyList() else SmartSearch.rankPosts(parsedQuery, posts.filter { post ->
            if (post.isAdminReel && !post.postEverywhere) return@filter false
            when (searchFilter) {
                "Photos" -> post.imageUrl.isNotBlank() || post.imageUrls.isNotEmpty()
                "Videos" -> post.videoUrl.isNotBlank() || post.isReel
                "Audio" -> post.audioUrl.isNotBlank()
                else -> true
            }
        }).take(60)
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
                        placeholder = { Text("Search • @people • #tags") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { rememberQuery() }),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                        },
                        singleLine = true,
                        shape = CircleShape
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close search") }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "People", "Posts", "Photos", "Videos", "Audio").forEach { filter ->
                        FilterChip(
                            selected = searchFilter == filter,
                            onClick = { searchFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
                if (normalized.isBlank()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (recentSearches.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Recent"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { SmartSearch.clear(searchContext); recentSearches = emptyList() }) { Text(tr("Clear all")) }
                            }
                            recentSearches.forEach { recent ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { query = recent }.padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Text(recent, Modifier.weight(1f))
                                    IconButton(onClick = { SmartSearch.forget(searchContext, recent); recentSearches = SmartSearch.recent(searchContext) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (trendingTags.isNotEmpty()) {
                            Text(tr("Trending this week"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                trendingTags.forEach { tag ->
                                    AssistChip(onClick = { query = "#$tag" }, label = { Text("#$tag") }, leadingIcon = { Icon(Icons.Default.TrendingUp, null, Modifier.size(16.dp)) })
                                }
                            }
                        }
                        val onlinePeople = remember(users) { users.filter { it.isOnline && (it.onlineUntil <= 0L || it.onlineUntil > System.currentTimeMillis()) }.take(12) }
                        if (onlinePeople.isNotEmpty()) {
                            Text(tr("Active now"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                onlinePeople.forEach { person ->
                                    Column(Modifier.width(64.dp).clickable { onProfileSelected(person) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box {
                                            AsyncImage(person.profileImageUrl.ifBlank { null }, person.name, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(CircleShape))
                                            Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                                        }
                                        Text(person.name.substringBefore(' '), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ManageSearch, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                            Text(tr("Search everything"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Type @name for people, #tag for hashtags. Small typos are OK.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else if (matchedUsers.isEmpty() && matchedPosts.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        Text("No results for “$normalized”", fontWeight = FontWeight.Bold)
                        Text("Try fewer words, another spelling, or a different filter.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (matchedUsers.isNotEmpty()) {
                            item { Text(tr("People"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(matchedUsers, key = { "search_user_${it.uid}" }) { user ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { rememberQuery(); onProfileSelected(user) }
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
                            item { Text(tr("Posts & media"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(matchedPosts, key = { "search_post_${it.id}" }) { post ->
                                Card(
                                    onClick = { rememberQuery(); if (post.videoUrl.isNotBlank()) fullScreenVideo = post.videoUrl },
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
                                            when {
                                                post.videoUrl.isNotBlank() || post.isReel -> Text("Video • tap to play", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                                post.imageUrl.isNotBlank() || post.imageUrls.isNotEmpty() -> Text(tr("Photo"), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                                post.audioUrl.isNotBlank() -> Text("Audio post", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                            }
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
    onReplyMessage: (MessageRequest, String) -> Unit,
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
                    Text(tr("Activity"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                        Text(tr("No activity yet"), fontWeight = FontWeight.Bold)
                        Text("Likes, comments, views and requests appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (requests.isNotEmpty()) {
                            item { Text(tr("Friend requests"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
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
                                                Text(tr("sent you a friend request"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onAccept(request) }, modifier = Modifier.weight(1f)) { Text(tr("Accept")) }
                                            OutlinedButton(onClick = { onReject(request) }, modifier = Modifier.weight(1f)) { Text(tr("Delete")) }
                                        }
                                    }
                                }
                            }
                        }
                        if (messageRequests.isNotEmpty()) {
                            item { Text(tr("Message requests"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            items(messageRequests, key = { "message_request_${it.id}" }) { request ->
                                var quickReply by remember(request.id) { mutableStateOf("") }
                                Card(shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Text(request.senderName, fontWeight = FontWeight.Bold)
                                        Text(request.preview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                                        OutlinedTextField(quickReply, { quickReply = it.take(600) }, placeholder = { Text("Reply to auto-accept…") }, modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RoundedCornerShape(18.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { if (quickReply.isNotBlank()) onReplyMessage(request, quickReply) else onAcceptMessage(request) }, modifier = Modifier.weight(1f)) { Text(if (quickReply.isBlank()) "Accept" else "Reply & Accept") }
                                            OutlinedButton(onClick = { onRejectMessage(request) }, modifier = Modifier.weight(1f)) { Text(tr("Delete")) }
                                        }
                                    }
                                }
                            }
                        }
                        if (notifications.isNotEmpty()) {
                            item { Text(tr("Recent activity"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
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
    onDelete: (Story) -> Unit,
    onSpotlight: (Story) -> Unit
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
                    Text(tr("No more stories"), color = Color.White, style = MaterialTheme.typography.headlineSmall)
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
                            val totalViews = story.viewCounts.values.sum()
                            val rewatches = (totalViews - story.viewers.size).coerceAtLeast(0)
                            Column(horizontalAlignment = Alignment.End) { Text("${story.viewers.size} viewers", color = Color.White, fontSize = 10.sp); Text("$rewatches rewatches", color = Color(0xFFFFD166), fontSize = 9.sp) }
                            IconButton(onClick = { onSpotlight(story) }) { Icon(Icons.Default.AutoAwesome, "Weekly story spotlight", tint = Color(0xFFFFD166)) }
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
                        listOf("👍", "❤️", "😂", "😮", "😢", "💖✨").forEach { emoji ->
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
                            placeholder = { Text(tr("Reply to story…"), color = Color.White.copy(alpha = .7f)) },
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
fun ReelsFeedScreen(
    posts: List<Post>,
    users: List<User>,
    viewModel: ChatViewModel,
    initialPostId: String? = null,
    onProfileSelected: (User) -> Unit,
    onCreateReel: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val reels = remember(posts) {
        posts.filter { (it.videoUrl.isNotBlank() || it.thumbnailUrl.isNotBlank()) && (it.isReel || it.r2ObjectKeys.isNotEmpty()) && !it.isAdminReel }
            .sortedWith(compareByDescending<Post> { it.timestamp }.thenBy { it.id })
    }
    if (reels.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text(tr("Reels"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 27.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onCreateReel) { Icon(Icons.Default.Add, "Create reel", tint = Color.White) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp)); Text(tr("No reels yet"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Create a reel or switch feed.", color = Color.White.copy(.65f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); Button(onClick = onCreateReel) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Create Reel")) }
            }
        }
        return
    }
    val startPage = remember(reels.map { it.id }, initialPostId) {
        val base = (Int.MAX_VALUE / 2).let { it - (it % reels.size) }
        base + reels.indexOfFirst { it.id == initialPostId }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = startPage, pageCount = { Int.MAX_VALUE })
    LaunchedEffect(reels, pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            VideoPlayerManager.preload(context, (1..3).map { offset -> reels[(page + offset) % reels.size].videoUrl })
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 2) { page ->
            val source = reels[page % reels.size]
            ImmersiveVideoPage(
                post = source, owner = users.find { it.uid == source.senderId },
                isActive = pager.currentPage == page, viewModel = viewModel, pageKey = page,
                onProfileSelected = { user -> VideoPlayerManager.pause(); onProfileSelected(user) }
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(10.dp).background(Color.Black.copy(.28f), CircleShape)
        ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
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
                    Text(tr("Videos"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(alpha = .42f), CircleShape)) {
                    Icon(Icons.Default.Close, "Close video feed", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveVideoPage(
    post: Post,
    owner: User?,
    isActive: Boolean,
    viewModel: ChatViewModel,
    onProfileSelected: (User) -> Unit,
    pageKey: Int = 0
) {
    val context = LocalContext.current
    var isPaused by remember(post.id) { mutableStateOf(false) }
    var comment by remember(post.id) { mutableStateOf("") }
    var replyingToComment by remember(post.id) { mutableStateOf<PostComment?>(null) }
    var showComments by remember(post.id) { mutableStateOf(false) }
    var showDescription by remember(post.id) { mutableStateOf(false) }
    var horizontalDrag by remember(post.id) { mutableFloatStateOf(0f) }
    var optimisticReaction by remember(post.id) { mutableStateOf<Boolean?>(null) }
    val currentUser by viewModel.currentUserState.collectAsState()
    val commentUsers by viewModel.usersState.collectAsState()
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
        when {
            post.videoUrl.isNotBlank() -> SharedCachedVideo(
                ownerId = "reel_${post.id}_$pageKey", videoUrl = post.videoUrl,
                thumbnailUrl = post.thumbnailUrl.ifBlank { post.imageUrl }, active = isActive,
                playWhenReady = isActive && !isPaused, sound = true, modifier = Modifier.fillMaxSize()
            )
            else -> AsyncImage(
                model = post.thumbnailUrl.ifBlank { post.imageUrl },
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Box(Modifier.fillMaxSize().pointerInput(post.id) {
            detectTapGestures(onDoubleTap = {
                if (!reacted) {
                    optimisticReaction = true
                    viewModel.reactToPost(post.id, "❤️")
                }
            })
        }.pointerInput(post.id, owner?.uid) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                onDragCancel = { horizontalDrag = 0f },
                onDragEnd = {
                    if (horizontalDrag < -90f) owner?.let(onProfileSelected)
                    horizontalDrag = 0f
                }
            )
        })
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
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(end = 10.dp, bottom = 82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            owner?.let { person ->
                Box(contentAlignment = Alignment.BottomCenter) {
                    AsyncImage(
                        person.profileImageUrl.ifBlank { null }, person.name,
                        error = painterResource(R.drawable.img_app_logo),
                        modifier = Modifier.size(44.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).clickable { onProfileSelected(person) }
                    )
                    if (person.uid != currentUser?.uid && !isFriend) {
                        Box(Modifier.offset(y = 7.dp).size(18.dp).clip(CircleShape).background(Color(0xFFFF315F)).clickable { if (requested) viewModel.cancelFriendRequest(person.uid) else viewModel.sendFriendRequest(person) }, contentAlignment = Alignment.Center) {
                            Icon(if (requested) Icons.Default.Check else Icons.Default.Add, "Follow", tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
            ReelSideAction(if (reacted) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, displayedReactionCount.toString(), if (reacted) Color(0xFFFF3F69) else Color.White) {
                optimisticReaction = !reacted; viewModel.reactToPost(post.id, "❤️")
            }
            ReelSideAction(Icons.Outlined.ChatBubbleOutline, post.comments.size.toString()) { showComments = true }
            ReelSideAction(Icons.Default.Share, "Share") {
                val shareText = listOf(post.title, post.text, post.videoUrl).filter { it.isNotBlank() }.joinToString("\n")
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "Share reel"))
            }
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 68.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.senderName, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                if (post.feeling.isNotBlank()) Text("  • ${post.feeling}", color = Color.White.copy(alpha = .72f))
            }
            if (post.title.isNotBlank()) Text(post.title, color = Color.White, fontWeight = FontWeight.Bold)
            if (post.text.isNotBlank()) Text(
                post.text, color = Color.White.copy(alpha = .88f), maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { showDescription = true }
            )
            if (post.tags.isNotEmpty()) Text(post.tags.joinToString(" ") { "#$it" }, color = Color(0xFFB9A8FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (showComments) {
        val commentsSheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            sheetState = commentsSheet,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 560.dp).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Comments"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text(post.comments.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (post.comments.isEmpty()) item { Box(Modifier.fillParentMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { Text(tr("No comments yet — start the conversation"), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    items(post.comments, key = { it.commentId }) { item ->
                        val person = if (item.senderId == currentUser?.uid) currentUser else commentUsers.find { it.uid == item.senderId }
                        Row(verticalAlignment = Alignment.Top) {
                            AsyncImage(person?.profileImageUrl?.ifBlank { null }, item.senderName, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { person?.let(onProfileSelected) })
                            Spacer(Modifier.width(8.dp))
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(.58f), shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(11.dp)) {
                                    Text(item.senderName, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { person?.let(onProfileSelected) })
                                    if (item.replyToName.isNotBlank()) Text("Replying to ${item.replyToName}", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp)
                                    Spacer(Modifier.height(2.dp)); Text(item.text)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { viewModel.reactToPostComment(post.id,item.commentId) }, contentPadding = PaddingValues(horizontal=4.dp)) { Text(if (item.reactions.containsKey(currentUser?.uid.orEmpty())) "♥ ${item.reactions.size}" else "♡ ${item.reactions.size}") }
                                        TextButton(onClick = { replyingToComment = item }, contentPadding = PaddingValues(horizontal=6.dp)) { Text(tr("Reply")) }
                                    }
                                }
                            }
                        }
                    }
                }
                replyingToComment?.let { target -> AssistChip(onClick = { replyingToComment = null }, label = { Text("Replying to ${target.senderName} • tap to cancel") }, leadingIcon = { Icon(Icons.Default.Reply,null) }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(comment, { comment = it.take(600) }, modifier = Modifier.weight(1f), placeholder = { Text(if(replyingToComment==null)"Add a comment…" else "Write a reply…") }, maxLines = 3, shape = RoundedCornerShape(24.dp))
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(onClick = { if (comment.isNotBlank()) { val target=replyingToComment; viewModel.commentOnPost(post.id, comment.trim(), target?.commentId.orEmpty(), target?.senderName.orEmpty()); comment = ""; replyingToComment=null } }) { Icon(Icons.Default.Send, "Send") }
                }
            }
        }
    }
    if (showDescription) {
        val descriptionSheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showDescription = false },
            sheetState = descriptionSheet,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 460.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(post.title.ifBlank { post.senderName }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp)); Text(post.text, style = MaterialTheme.typography.bodyLarge)
                if (post.tags.isNotEmpty()) { Spacer(Modifier.height(14.dp)); Text(post.tags.joinToString(" ") { "#$it" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(14.dp)); Text("${post.reactions.size} reactions • ${post.comments.size} comments • ${post.viewsCount} views", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminOEmbedWebView(html: String, active: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        val scheme = request.url.scheme?.lowercase()
                        return scheme != "https"
                    }
                }
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { webView ->
            if (active) webView.onResume() else webView.onPause()
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://oembed.local/", html, "text/html", "UTF-8", null)
            }
        }
    )
}

@Composable
private fun AnimatedGlobalNotice(title: String, body: String, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "global_notice_motion")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "global_notice_pulse"
    )
    val sweep by transition.animateFloat(
        initialValue = -80f,
        targetValue = 420f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "global_notice_sweep"
    )
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF00D4FF), Color(0xFF6C4DFF), Color(0xFFFF2C83), Color(0xFFFFA62B))
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(2.dp)
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 12.dp) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF00D4FF), Color(0xFFFF2C83), Color.Transparent)))
                            .graphicsLayer { translationX = sweep }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Outlined.Campaign, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    }
                    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(tr("Got it")) }
                }
            }
        }
    }
}

@Composable
private fun ReelSideAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp).background(Color.Black.copy(.30f), CircleShape)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(23.dp))
        }
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAnalyticsSheet(currentUser: User?, posts: List<Post>, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf("7 days") }
    val now = System.currentTimeMillis()
    val cutoff = when (range) { "7 days" -> now - 7L * 86_400_000L; "30 days" -> now - 30L * 86_400_000L; else -> 0L }
    val filtered = posts.filter { it.timestamp >= cutoff }
    val reactions = filtered.sumOf { it.reactions.size + it.mediaReactions.values.sumOf { map -> map.size } }
    val views = filtered.sumOf { it.viewsCount }
    val comments = filtered.sumOf { it.comments.size }
    val best = filtered.maxByOrNull { it.viewsCount + it.reactions.size * 3 + it.comments.size * 2 }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF6757FF), Color(0xFF27D8FF)))), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Analytics, null, tint = Color.White) }
                Spacer(Modifier.width(11.dp)); Column { Text("Creator Analytics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("Understand your Convo growth", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("7 days", "30 days", "All time").forEach { item -> FilterChip(range == item, { range = item }, { Text(item) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalyticsMetric("Posts", filtered.size.toString(), Icons.Outlined.DynamicFeed, Modifier.weight(1f))
                AnalyticsMetric("Views", views.toString(), Icons.Outlined.Visibility, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalyticsMetric("Reactions", reactions.toString(), Icons.Outlined.FavoriteBorder, Modifier.weight(1f))
                AnalyticsMetric("Comments", comments.toString(), Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalyticsMetric("Reach", views.toString(), Icons.Outlined.Campaign, Modifier.weight(1f))
                AnalyticsMetric("Follower growth", "+${currentUser?.followerGrowth ?: 0L}", Icons.Outlined.TrendingUp, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AnalyticsMetric("Followers", (currentUser?.followers?.size ?: 0).toString(), Icons.Outlined.People, Modifier.weight(1f))
                AnalyticsMetric("Following", (currentUser?.following?.size ?: 0).toString(), Icons.Outlined.PersonAdd, Modifier.weight(1f))
            }
            Text("Views and reach are calculated from unique server-side post views; follower growth is updated when follow relationships change.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(.65f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Top content", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(5.dp)); Text(best?.title?.takeIf { it.isNotBlank() } ?: best?.text?.take(90) ?: "Publish more content to unlock insights", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (best != null) { Spacer(Modifier.height(8.dp)); Text("${best.viewsCount} views • ${best.reactions.size} reactions • ${best.comments.size} comments", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(.62f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(15.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
    }
}

@Composable
private fun SuggestedPeopleStrip(
    suggestions: List<User>,
    myFriendIds: List<String>,
    onFollow: (User) -> Unit,
    onOpen: (User) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f), contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.65f))
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonSearch, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text(tr("Suggested for you"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("Smart matches", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            }
            Spacer(Modifier.height(9.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(suggestions, key = { it.uid }) { person ->
                    val mutual = person.friends.count { it in myFriendIds }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(.48f),
                        modifier = Modifier.width(145.dp)
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                AsyncImage(person.profileImageUrl.ifBlank { null }, person.name, error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape).clickable { onOpen(person) }.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
                                if (person.isOnline) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF35E86F)).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
                            }
                            Spacer(Modifier.height(7.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(person.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (person.role == "moderator" || (person.isPremium && (person.premiumPlan == "lifetime" || person.premiumUntil > System.currentTimeMillis()))) {
                                    Spacer(Modifier.width(3.dp)); Icon(Icons.Default.Verified, null, tint = if (person.role == "moderator") MaterialTheme.colorScheme.primary else Color(0xFFFFB300), modifier = Modifier.size(13.dp))
                                }
                            }
                            Text(if (mutual > 0) "$mutual mutual friends" else "@${person.username}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Spacer(Modifier.height(7.dp))
                            Button(onClick = { onFollow(person) }, modifier = Modifier.fillMaxWidth().height(34.dp), contentPadding = PaddingValues(0.dp), shape = CircleShape) { Text(tr("Follow"), fontSize = 11.sp) }
                        }
                    }
                }
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
    val users by viewModel.usersState.collectAsState()
    val groupedStories = remember(stories) { stories.groupBy { it.senderId }.values.map { it.sortedBy { story -> story.timestamp } } }
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
            title = { Text(tr("Add Story"), fontWeight = FontWeight.Bold) },
            text = { Text("Choose what type of story you want to share:") },
            confirmButton = {
                Button(onClick = {
                    showAddStoryDialog = false
                    imagePicker.launch("image/*")
                }) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Photo Story"))
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
                    Text(tr("Video Story"))
                }
            }
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(groupedStories, key = { it.first().senderId }) { group ->
            val story = group.first()
            val preview = group.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl ?: story.senderProfilePic
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(82.dp).clickable { onStorySelected(story) }
            ) {
                Box(
                    modifier = Modifier.size(68.dp).clip(CircleShape)
                        .border(3.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = preview.ifBlank { null }, contentDescription = story.senderName,
                        error = painterResource(id = R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(CircleShape)
                    )
                    if (group.size > 1) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd)) {
                            Text(group.size.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    if (users.find { it.uid == story.senderId }?.isOnline == true) {
                        Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(Color(0xFF2BE56C)).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(story.senderName.split(" ").firstOrNull() ?: story.senderName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReactionOrb(emoji:String,index:Int,onClick:()->Unit){
    val colors=listOf(listOf(Color(0xFFFF5C8A),Color(0xFFFFB347)),listOf(Color(0xFFFF3D71),Color(0xFF9B5CFF)),listOf(Color(0xFFFFD54F),Color(0xFFFF7A45)),listOf(Color(0xFF44D7FF),Color(0xFF6D68FF)),listOf(Color(0xFF5CA8FF),Color(0xFF8E6BFF)),listOf(Color(0xFFFF664D),Color(0xFF8D2738)))[index%6]
    Surface(onClick=onClick,shape=CircleShape,color=Color.Transparent,modifier=Modifier.size(44.dp).background(Brush.linearGradient(colors),CircleShape).border(1.dp,Color.White.copy(.45f),CircleShape)){Box(contentAlignment=Alignment.Center){Text(emoji,fontSize=23.sp)}}
}

@Composable
private fun animatedPostScale(postId: String, animation: String): Float {
    if (animation != "pulse" && animation != "breathe") return 1f
    val motion = rememberInfiniteTransition(label = "post_$postId")
    val scale by motion.animateFloat(
        initialValue = if (animation == "pulse") .985f else 1f,
        targetValue = if (animation == "pulse") 1.015f else 1.008f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "post_motion"
    )
    return scale
}

@Composable
fun SocialPostItem(post: Post, viewModel: ChatViewModel, onProfileSelected: (User) -> Unit = {}, autoPlayVideo: Boolean = false, allowInlineVideo: Boolean = false, startWithComments: Boolean = false) {
    val context = LocalContext.current
    var isCommentsExpanded by remember { mutableStateOf(startWithComments) }
    var commentInputText by remember { mutableStateOf("") }
    var replyingFeedComment by remember(post.id) { mutableStateOf<PostComment?>(null) }
    var commentLimit by remember(post.id) { mutableIntStateOf(12) }
    var showPostMenu by remember { mutableStateOf(false) }
    var showEditPost by remember { mutableStateOf(false) }
    var showReportPost by remember { mutableStateOf(false) }
    var showSendPost by remember { mutableStateOf(false) }
    var showLikers by remember { mutableStateOf(false) }
    var playInlineVideo by remember(post.id) { mutableStateOf(false) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val savedPostIds by viewModel.savedPostIds.collectAsState()
    val postSaved = post.id in savedPostIds
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val allUsers by viewModel.usersState.collectAsState()

    // Register simple visual view count increment on render
    LaunchedEffect(post.id) {
        viewModel.incrementPostViews(post.id)
    }

    // Get commenters' profiles
    val currentUserProfile = allUsers.find { it.uid == currentUserId }
    val currentUserPic = currentUserProfile?.profileImageUrl ?: ""
    val postOwner = allUsers.find { it.uid == post.senderId }
    val mediaImages = post.imageUrls.ifEmpty { post.imageUrl.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty() }
    val styledPost = post.backgroundStyle != "glass"
    // Static posts should not own an infinite animation clock. This removes a
    // large amount of per-frame work in long feeds on low/mid-range phones.
    val postScale = animatedPostScale(post.id, post.textAnimation)
    val postShape = RoundedCornerShape(26.dp)
    val postContainerModifier = (if (styledPost) {
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(postBackgroundColors(post.backgroundStyle)), postShape)
            .border(1.dp, Color.White.copy(alpha = .24f), postShape)
    } else {
        Modifier.fillMaxWidth().glassmorphic(isDark = isDark, shape = postShape)
    }).graphicsLayer { scaleX = postScale; scaleY = postScale }
    val primaryPostText = if (styledPost) Color.White else MaterialTheme.colorScheme.onSurface

    Box(modifier = postContainerModifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Sender top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(Brush.sweepGradient(if (post.autoGenerated) listOf(Color(0xFF00C6FF), Color(0xFF7C4DFF), Color(0xFFFF4FD8), Color(0xFF00C6FF)) else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary))),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = post.senderProfilePic.ifBlank { null },
                            contentDescription = post.senderName,
                            error = painterResource(id = R.drawable.img_app_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(41.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .clickable { postOwner?.let(onProfileSelected) }
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(post.senderName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryPostText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
                            if (post.autoGenerated) {
                                Spacer(Modifier.width(6.dp))
                                Row(
                                    Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF00C6FF), Color(0xFF7C4DFF), Color(0xFFFF4FD8)))).padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    Text(" AI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            if (postOwner?.activeProfileType.equals("Creator", ignoreCase = true)) {
                                Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.AutoAwesome, "Creator", tint = Color(0xFF6C4BFF), modifier = Modifier.size(15.dp))
                                Text(" CREATOR", color = Color(0xFF6C4BFF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            if (postOwner?.role == "moderator") {
                                Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.Verified, "Moderator", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                Text(" MOD", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            if (postOwner?.staffRole == "sub_admin") {
                                Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Shield, "Staff", tint = Color(0xFF3949AB), modifier = Modifier.size(14.dp))
                                Text(" STAFF", color = Color(0xFF3949AB), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        val metaColor = if (styledPost) Color.White.copy(alpha = .82f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = relativeTimeLabel(post.timestamp),
                                fontSize = 11.sp,
                                color = metaColor
                            )
                            Text("  ·  ", fontSize = 11.sp, color = metaColor.copy(alpha = .5f))
                            Icon(if (post.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public, if (post.isPrivate) "Private" else "Public", tint = metaColor, modifier = Modifier.size(12.dp))
                            if (post.autoGenerated && post.autoPostType.isNotBlank()) {
                                Text("  ·  ", fontSize = 11.sp, color = metaColor.copy(alpha = .5f))
                                Text(post.autoPostType.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = metaColor)
                            }
                            if (post.viewsCount > 0) {
                                Text("  ·  ", fontSize = 11.sp, color = metaColor.copy(alpha = .5f))
                                Icon(Icons.Outlined.Visibility, null, tint = metaColor, modifier = Modifier.size(12.dp))
                                Text(" ${post.viewsCount}", fontSize = 11.sp, color = metaColor)
                            }
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showPostMenu = true }) { Icon(Icons.Default.MoreVert, "Post options", tint = primaryPostText) }
                    DropdownMenu(expanded = showPostMenu, onDismissRequest = { showPostMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Send to a chat")) }, leadingIcon = { Icon(Icons.Default.Send, null) }, onClick = { showPostMenu = false; showSendPost = true })
                        if (post.senderId != currentUserId) DropdownMenuItem(text = { Text(tr("Report post")) }, leadingIcon = { Icon(Icons.Default.ReportProblem, null) }, onClick = { showPostMenu = false; showReportPost = true })
                        if (post.senderId == currentUserId) {
                            DropdownMenuItem(text = { Text(tr("Edit post")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showPostMenu = false; showEditPost = true })
                            DropdownMenuItem(text = { Text(tr("Delete post"), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showPostMenu = false; viewModel.deletePost(post.id) })
                        }
                        if (post.senderId != currentUserId && viewModel.staffCan("posts")) DropdownMenuItem(
                            text = { Text("Remove post (staff)", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showPostMenu = false; viewModel.deletePost(post.id); AdminAudit.log("post_removed", "${post.id} by ${post.senderId}") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (post.title.isNotBlank()) {
                Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = primaryPostText)
                Spacer(Modifier.height(6.dp))
            }
            if (post.feeling.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text("Feeling ${post.feeling}") }, leadingIcon = { Text("✨") })
                Spacer(Modifier.height(6.dp))
            }

            // Post Content Text
            if (post.text.isNotBlank()) {
                LinkifiedPostText(
                    text = post.text,
                    color = primaryPostText,
                    styled = styledPost,
                    onMentionClick = { handle -> Mentions.findUser(handle, allUsers)?.let(onProfileSelected) },
                    showLinkPreview = mediaImages.isEmpty() && post.videoUrl.isBlank(),
                    modifier = if (styledPost) Modifier.fillMaxWidth().padding(vertical = 18.dp) else Modifier
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (post.tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.forEach { tag ->
                        Text(
                            "#$tag", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (styledPost) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (styledPost) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.primary.copy(alpha = .10f)).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Facebook-style multi-image carousel with per-image reactions.
            if (mediaImages.isNotEmpty() && post.videoUrl.isBlank()) {
                val imagePager = rememberPagerState(pageCount = { mediaImages.size })
                val mediaKey = imagePager.currentPage.toString()
                val imageLikes = post.mediaReactions[mediaKey].orEmpty()
                val imageLiked = imageLikes.containsKey(currentUserId)
                var heartBurst by remember(post.id) { mutableIntStateOf(0) }
                Box(
                    Modifier.fillMaxWidth().then(if (mediaImages.size == 1) Modifier.heightIn(min = 180.dp, max = 540.dp) else Modifier.height(320.dp)).clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                ) {
                    if (mediaImages.size == 1) {
                        AsyncImage(
                            model = mediaImages.first(), contentDescription = "Post image", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight().align(Alignment.Center).pointerInput(post.id) { detectTapGestures(onDoubleTap = { if (!imageLiked) viewModel.reactToPostMedia(post.id, "0", "❤️"); heartBurst++ }) }
                        )
                    } else HorizontalPager(state = imagePager, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = mediaImages[page], contentDescription = "Post image ${page + 1}", contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().pointerInput(post.id, page) {
                                detectTapGestures(onDoubleTap = { if (!imageLiked) viewModel.reactToPostMedia(post.id, page.toString(), "❤️"); heartBurst++ })
                            }
                        )
                    }
                    HeartBurstOverlay(heartBurst, Modifier.align(Alignment.Center))
                    if (mediaImages.size > 1) {
                        Surface(
                            color = Color.Black.copy(.58f), shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                        ) { Text("${imagePager.currentPage + 1}/${mediaImages.size}", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                    }
                    Surface(
                        onClick = { viewModel.reactToPostMedia(post.id, mediaKey, "❤️") },
                        color = Color.Black.copy(.55f), shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (imageLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (imageLiked) Color(0xFFFF5577) else Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp)); Text(imageLikes.size.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
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
                        .clickable { if (allowInlineVideo) playInlineVideo = true else viewModel.openReel(post.id) },
                    contentAlignment = Alignment.Center
                ) {
                    val videoActive = autoPlayVideo || playInlineVideo
                    SharedCachedVideo(
                        ownerId = "feed_${post.id}", videoUrl = post.videoUrl,
                        thumbnailUrl = post.imageUrl, active = videoActive,
                        playWhenReady = videoActive, sound = videoActive,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().clickable { if (allowInlineVideo) playInlineVideo = true else viewModel.openReel(post.id) })
                    if (!videoActive) Surface(color = Color.Black.copy(.58f), shape = CircleShape, modifier = Modifier.align(Alignment.Center)) {
                        Icon(Icons.Default.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.padding(13.dp).size(30.dp))
                    }
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
            val reactionCount = post.reactions.size + post.mediaReactions.values.sumOf { it.size }
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
                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                        modifier = Modifier.clickable { showLikers = true }
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
                                    emojis.forEachIndexed { index, emoji ->
                                        ReactionOrb(emoji,index) { viewModel.reactToPost(post.id,emoji);showReactionPicker=false }
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
                            val link = "https://solitary-hill-dcdc.mr44253990.workers.dev/post/${post.id}"
                            val shareText = "${post.title.ifBlank { "A post from ${post.senderName}" }}\n${post.text.take(180)}\n\nOpen in Convo Chat: $link"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "Share Convo post"))
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
                IconButton(onClick = { viewModel.toggleSavedPost(post.id) }) {
                    Icon(if (postSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder, if (postSaved) "Remove saved post" else "Save post", tint = if (postSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    post.comments.takeLast(commentLimit).forEach { c ->
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
                                    .clickable { commenter?.let(onProfileSelected) }
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
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { commenter?.let(onProfileSelected) }
                                        )
                                        if(c.replyToName.isNotBlank()) Text("Replying to ${c.replyToName}",fontSize=9.sp,color=MaterialTheme.colorScheme.secondary)
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
                                        text = if (c.reactions.containsKey(currentUserId)) "♥ ${c.reactions.size}" else "Like ${c.reactions.size.takeIf { it > 0 } ?: ""}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (c.reactions.containsKey(currentUserId)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { viewModel.reactToPostComment(post.id,c.commentId) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Reply",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable { replyingFeedComment = c }
                                    )
                                }
                            }
                        }
                    }

                    if(post.comments.size>commentLimit) TextButton(onClick={commentLimit=(commentLimit+12).coerceAtMost(post.comments.size)},modifier=Modifier.fillMaxWidth()){Text("Load older comments (${post.comments.size-commentLimit})")}
                    replyingFeedComment?.let { target -> AssistChip(onClick={replyingFeedComment=null},label={Text("Replying to ${target.senderName} • cancel")},leadingIcon={Icon(Icons.Default.Reply,null)}) }
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
                            placeholder = { Text(tr("Write comment..."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
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
                                    val target=replyingFeedComment
                                    viewModel.commentOnPost(post.id, commentInputText, target?.commentId.orEmpty(), target?.senderName.orEmpty())
                                    commentInputText = ""; replyingFeedComment=null
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

    if (showSendPost) {
        AlertDialog(onDismissRequest={showSendPost=false},title={Text(tr("Send post to chat"),fontWeight=FontWeight.ExtraBold)},text={LazyColumn(Modifier.heightIn(max=420.dp)){items(allUsers,key={it.uid}){person->ListItem(headlineContent={Text(person.name,fontWeight=FontWeight.Bold)},supportingContent={Text("@${person.username}")},leadingContent={AsyncImage(person.profileImageUrl.ifBlank{null},person.name,Modifier.size(42.dp).clip(CircleShape))},modifier=Modifier.clickable{viewModel.sendPostToUser(person,post){ok->Toast.makeText(context,if(ok)"Sent to ${person.name}" else "Send failed",Toast.LENGTH_SHORT).show()};showSendPost=false})}}},confirmButton={TextButton(onClick={showSendPost=false}){Text(tr("Cancel"))}})
    }
    if (showReportPost) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={showReportPost=false},title={Text(tr("Report this post"))},text={OutlinedTextField(reason,{reason=it.take(1200)},label={Text("What is wrong with this post?")},minLines=4)},confirmButton={Button(onClick={viewModel.submitUserReport(post.senderId,"post",reason){ok,message->Toast.makeText(context,message,Toast.LENGTH_LONG).show();if(ok)showReportPost=false}},enabled=reason.length>=10){Text(tr("Submit"))}},dismissButton={TextButton(onClick={showReportPost=false}){Text(tr("Cancel"))}})
    }
    if (showLikers) {
        val likerIds = remember(post.id, post.reactions, post.mediaReactions) {
            (post.reactions.keys + post.mediaReactions.values.flatMap { it.keys }).distinct()
        }
        val likerProfiles = likerIds.mapNotNull { id -> allUsers.find { it.uid == id } }
        AlertDialog(
            onDismissRequest = { showLikers = false },
            title = { Text(tr("People who reacted"), fontWeight = FontWeight.ExtraBold) },
            text = {
                if (likerIds.isEmpty()) {
                    Text(tr("No reactions yet."))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(likerIds, key = { it }) { uid ->
                            val person = allUsers.find { it.uid == uid }
                            ListItem(
                                headlineContent = { Text(person?.name?.ifBlank { "Convo user" } ?: "Convo user", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(person?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "User ID: ${uid.take(12)}") },
                                leadingContent = {
                                    AsyncImage(
                                        model = person?.profileImageUrl?.ifBlank { null },
                                        contentDescription = person?.name ?: "User",
                                        error = painterResource(R.drawable.img_app_logo),
                                        modifier = Modifier.size(42.dp).clip(CircleShape)
                                    )
                                },
                                trailingContent = { Text(post.reactions[uid] ?: "♥", fontSize = 18.sp) },
                                modifier = Modifier.clickable { person?.let { onProfileSelected(it) }; showLikers = false }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLikers = false }) { Text(tr("Close")) } }
        )
    }
    if (showEditPost) {
        var editTitle by remember { mutableStateOf(post.title) }
        var editText by remember { mutableStateOf(post.text) }
        var editTags by remember { mutableStateOf(post.tags.joinToString(", ")) }
        var editPrivate by remember { mutableStateOf(post.isPrivate) }
        AlertDialog(
            onDismissRequest = { showEditPost = false },
            title = { Text(tr("Edit post"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editTitle, { editTitle = it.take(80) }, label = { Text(tr("Title")) }, singleLine = true)
                    OutlinedTextField(editText, { editText = it.take(1200) }, label = { Text(tr("Description")) }, minLines = 4)
                    OutlinedTextField(editTags, { editTags = it }, label = { Text(tr("Hashtags")) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Private post"), Modifier.weight(1f)); Switch(editPrivate, { editPrivate = it }) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.editPost(post.id, editText.trim(), editPrivate, editTitle.trim(), editTags.split(",", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct()) { showEditPost = false }
                }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { showEditPost = false }) { Text(tr("Cancel")) } }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatConversationUserItem(
    user: User,
    viewModel: ChatViewModel,
    hasActiveStory: Boolean,
    isMuted: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit
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
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress),
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        val convCtx = androidx.compose.ui.platform.LocalContext.current
                        val convVersion by ConversationPrefs.version.collectAsState()
                        val convPinned = remember(convVersion, user.uid) { ConversationPrefs.isPinned(convCtx, user.uid) }
                        val convMuted = remember(convVersion, user.uid) { ConversationPrefs.isMuted(convCtx, user.uid) }
                        val convNick = remember(convVersion, user.uid) { ConversationPrefs.nickname(convCtx, user.uid) }
                        if (convPinned) { Icon(Icons.Default.PushPin, "Pinned", tint = Color(0xFFD81B60), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)) }
                        Text(
                            text = convNick.ifBlank { user.name },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        if (user.role == "moderator") {
                            Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.Verified, "Moderator", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        } else if (user.isPremium && (user.premiumPlan == "lifetime" || user.premiumUntil > System.currentTimeMillis())) {
                            Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.Verified, "Premium", tint = Color(0xFFFFA000), modifier = Modifier.size(15.dp))
                        }
                        if (convMuted && !isMuted) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.NotificationsOff, "Muted", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp)) }
                    }
                    
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
                val lastKind = lastMessageState?.let { m ->
                    when {
                        !m.imageUrl.isNullOrBlank() -> "photo"
                        !m.voiceUrl.isNullOrBlank() -> "voice"
                        com.example.location.LocationShare.parse(m.text)?.isLive == true -> "live_location"
                        com.example.location.LocationShare.parse(m.text) != null -> "location"
                        else -> "text"
                    }
                } ?: "none"
                val lastMsgText = when (lastKind) {
                    "photo" -> "Photo"
                    "voice" -> "Voice message"
                    "live_location" -> "Live location"
                    "location" -> "Location"
                    "text" -> convoStickerPreview(lastMessageState!!.text)
                    else -> "No messages yet. Say Hello!"
                }
                val previewColor = if (hasUnread) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (lastKind) {
                        "voice" -> { MicGlyph(MaterialTheme.colorScheme.primary, size = 15.dp); Spacer(Modifier.width(4.dp)) }
                        "photo" -> { PhotoGlyph(MaterialTheme.colorScheme.primary, size = 15.dp); Spacer(Modifier.width(4.dp)) }
                        "location", "live_location" -> { MapPinGlyph(Color(0xFFE53935), size = 14.dp); Spacer(Modifier.width(4.dp)) }
                    }
                    Text(
                        text = lastMsgText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.Normal,
                        color = previewColor
                    )
                }
                val presenceText = if (user.isOnline) "Active now" else {
                    val since = user.offlineSince.takeIf { it > 0L } ?: user.lastActive
                    if (since <= 0L) "Offline" else formatOfflineAge(since)
                }
                Text(
                    text = presenceText,
                    fontSize = 10.sp,
                    color = if (user.isOnline) Color(0xFF35B96A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f)
                )
            }

            // Message Status indicators & unread markers!
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMuted) {
                    Icon(Icons.Default.NotificationsOff, "Muted", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                }
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
                    text = if (group.lastMessage.isNotBlank()) convoStickerPreview(group.lastMessage) else "No messages in group yet",
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

@Composable
private fun LinkifiedPostText(
    text: String,
    color: Color,
    styled: Boolean,
    modifier: Modifier = Modifier,
    onMentionClick: (String) -> Unit = {},
    showLinkPreview: Boolean = true
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, styled) {
        Mentions.annotate(
            text,
            if (styled) Color(0xFFBDEBFF) else Color(0xFF3D8BFF),
            if (styled) Color(0xFFFFE08A) else Color(0xFF7C5CFC)
        )
    }
    val firstUrl = remember(text) { LinkPreviewRepository.firstUrl(text) }
    Column(modifier) {
        ClickableText(
            text = annotated,
            modifier = if (styled) Modifier.fillMaxWidth() else Modifier,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = color,
                lineHeight = if (styled) 26.sp else 20.sp,
                fontSize = if (styled) 18.sp else 14.sp,
                textAlign = if (styled) TextAlign.Center else TextAlign.Start
            ),
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                    runCatching { uriHandler.openUri(it.item) }
                    return@ClickableText
                }
                annotated.getStringAnnotations("MENTION", offset, offset).firstOrNull()?.let { onMentionClick(it.item) }
            }
        )
        if (showLinkPreview && !styled && !firstUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            RichLinkPreviewCard(
                url = firstUrl,
                contentColor = MaterialTheme.colorScheme.onSurface,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
            )
        }
    }
}

private fun postBackgroundColors(style: String): List<Color> = when (style) {
    "sunset" -> listOf(Color(0xFFFF6B6B), Color(0xFFFFB347), Color(0xFF7A3152))
    "ocean" -> listOf(Color(0xFF005C97), Color(0xFF00A8CC), Color(0xFF002B5B))
    "aurora" -> listOf(Color(0xFF4A148C), Color(0xFF00BFA5), Color(0xFF311B92))
    "forest" -> listOf(Color(0xFF0B3D2E), Color(0xFF2E8B57), Color(0xFF102A1E))
    "rose" -> listOf(Color(0xFF8E2DE2), Color(0xFFFF5F6D), Color(0xFF3A1C71))
    "midnight" -> listOf(Color(0xFF020024), Color(0xFF090979), Color(0xFF111827))
    "mono" -> listOf(Color(0xFF111111), Color(0xFF424242), Color(0xFF151515))
    "candy" -> listOf(Color(0xFFFF9A9E), Color(0xFFF6A6C1), Color(0xFFA18CD1))
    "peach" -> listOf(Color(0xFFFFB88C), Color(0xFFDE6262), Color(0xFF8E3B46))
    "fire" -> listOf(Color(0xFFF12711), Color(0xFFF5AF19), Color(0xFF7A1F0A))
    "gold" -> listOf(Color(0xFF8A6100), Color(0xFFE0A526), Color(0xFF4A3400))
    "lime" -> listOf(Color(0xFF1E5631), Color(0xFF56AB2F), Color(0xFF0E3B1E))
    "mint" -> listOf(Color(0xFF00B09B), Color(0xFF3FA36B), Color(0xFF0B5345))
    "sky" -> listOf(Color(0xFF2193B0), Color(0xFF4FB3D9), Color(0xFF0F4C75))
    "lavender" -> listOf(Color(0xFF8E6CC9), Color(0xFF7D93D6), Color(0xFF4B3B7A))
    "berry" -> listOf(Color(0xFFC33764), Color(0xFF1D2671), Color(0xFF4A1942))
    "galaxy" -> listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))
    "coffee" -> listOf(Color(0xFF3E2723), Color(0xFF8D6E63), Color(0xFF1B0F0C))
    "neon" -> listOf(Color(0xFF0B0612), Color(0xFFFF4DA6), Color(0xFF00B8D4))
    else -> listOf(Color(0xFF28233F), Color(0xFF6750A4), Color(0xFF15233A))
}

@Composable
private fun PrivacyToggleRow(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

@Composable
private fun ActiveContactsStrip(users: List<User>, onSelect: (User) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("Active now"), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("${users.size} online", fontSize = 11.sp, color = Color(0xFF35B96A))
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(users, key = { it.uid }) { user ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).clickable { onSelect(user) }
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (user.profileImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.profileImageUrl,
                                contentDescription = user.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp).clip(CircleShape).border(2.dp, Color(0xFF35B96A), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).border(2.dp, Color(0xFF35B96A), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                        }
                        Box(Modifier.size(15.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(3.dp)) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF35B96A)))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(user.name.ifBlank { "User" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
private fun SettingsHubGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))))
            Spacer(Modifier.width(7.dp))
            Text(
                text = tr(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.fillMaxWidth().liquidCard(shape = RoundedCornerShape(25.dp)), content = content)
    }
}

@Composable
private fun SettingsHubRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    trailingValue: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .10f), accent.copy(alpha = .03f), Color.Transparent)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(lerp(accent, Color.White, .22f), accent, lerp(accent, Color.Black, .12f))))
                .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(tr(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!trailingValue.isNullOrBlank()) {
            Text(
                trailingValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 82.dp).padding(end = 3.dp)
            )
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsHubDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f))
    )
}

private fun settingsPageTitle(page: String): String = when (page) {
    "profile" -> "Profile details"
    "theme" -> "App theme"
    "call" -> "Call appearance"
    "chat" -> "Chat settings"
    "bubbles" -> "Bubbles & spacing"
    "sounds" -> "Sounds"
    "backgrounds" -> "Chat backgrounds"
    "privacy" -> "Privacy controls"
    "notifications" -> "Notifications & calls"
    "viewers" -> "Profile viewers"
    "devices" -> "Account security"
    "blocked" -> "Blocked accounts"
    "smart" -> "Smart tools"
    "language" -> "Language"
    else -> "Settings"
}

private fun settingsPageSubtitle(page: String): String = when (page) {
    "profile" -> "Photo, bio, links and account details"
    "theme" -> "Light, dark and premium looks for the whole app"
    "call" -> "Skins and textures for audio and video calls"
    "chat" -> "How messages look and send"
    "bubbles" -> "Make chat bubbles yours: style, border, padding and time"
    "sounds" -> "Custom MP3 sounds saved on this phone"
    "backgrounds" -> "Themes and photo backgrounds for chats"
    "privacy" -> "App lock, presence, receipts and AI"
    "notifications" -> "Alerts, sounds and incoming-call access"
    "viewers" -> "Stored only on this phone"
    "devices" -> "2-step verification, devices & alerts"
    "blocked" -> "People who cannot contact you"
    "smart" -> "Quiet hours, motion, live location and storage"
    "language" -> "Choose your app language"
    else -> ""
}

private data class AppThemePreview(val name: String, val background: Color, val surface: Color, val accent: Color)

/** Miniature phone preview of an app theme: background, a card and two chat bubbles. */
@Composable
private fun AppThemeTile(preview: AppThemePreview, selected: Boolean, locked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isLight = preview.background.luminance() > .5f
    val textColor = if (isLight) Color(0xFF1B1B24) else Color.White
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(preview.background, lerp(preview.background, preview.accent, .16f))))
                .border(if (selected) 3.dp else 1.dp, if (selected) preview.accent else textColor.copy(alpha = .12f), RoundedCornerShape(18.dp))
                .padding(8.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(preview.accent))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.height(5.dp).fillMaxWidth(.6f).clip(CircleShape).background(textColor.copy(alpha = .55f)))
                }
                Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(preview.surface.copy(alpha = if (isLight) 1f else .9f)).border(1.dp, textColor.copy(alpha = .06f), RoundedCornerShape(8.dp)))
                Box(Modifier.fillMaxWidth(.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(preview.surface))
                Box(Modifier.align(Alignment.End).fillMaxWidth(.62f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(preview.accent))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == 0) preview.accent else textColor.copy(alpha = .3f))) }
                }
            }
            if (selected) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(preview.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Active", tint = if (preview.accent.luminance() > .5f) Color.Black else Color.White, modifier = Modifier.size(13.dp))
            }
            if (locked) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, "Premium", tint = Color(0xFFFFD166), modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(preview.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium)
    }
}


/** Full-screen maintenance card with a live countdown when the admin set a timer. */
@Composable
fun MaintenanceCountdownCard(title: String, message: String, endsAt: Long, startedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    val remaining = (endsAt - now).coerceAtLeast(0L)
    val total = (endsAt - startedAt).coerceAtLeast(1L)
    val motion = rememberInfiniteTransition(label = "maintenance")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "gear")
    val glow by motion.animateFloat(.6f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E0B2A), Color(0xFF2A1458), Color(0xFF0B1E3A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(26.dp).clip(RoundedCornerShape(34.dp))
                .background(Color.White.copy(alpha = .08f))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.35f), Color.White.copy(.05f))), RoundedCornerShape(34.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(110.dp).graphicsLayer { alpha = glow }.clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8B7CFF).copy(.55f), Color.Transparent))))
                Icon(Icons.Outlined.Settings, null, tint = Color.White, modifier = Modifier.size(58.dp).graphicsLayer { rotationZ = spin })
            }
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White.copy(.78f), textAlign = TextAlign.Center)
            if (endsAt > 0L) {
                Spacer(Modifier.height(22.dp))
                if (remaining > 0L) {
                    val hours = remaining / 3_600_000L
                    val minutes = (remaining / 60_000L) % 60
                    val seconds = (remaining / 1000L) % 60
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(hours to "HRS", minutes to "MIN", seconds to "SEC").forEach { (value, unit) ->
                            Column(
                                Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.12f)).padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("%02d".format(value), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                Text(unit, color = Color.White.copy(.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (1f - remaining.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Color(0xFF8B7CFF), trackColor = Color.White.copy(.15f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Back at ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(endsAt))}", color = Color.White.copy(.6f), fontSize = 12.sp)
                } else {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Almost done — finishing up…", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


/** Instagram-style heart that pops on double-tap. [trigger] increments for every double-tap. */
@Composable
fun HeartBurstOverlay(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        alpha.snapTo(1f); scale.snapTo(.2f)
        scale.animateTo(1.15f, spring(dampingRatio = .45f, stiffness = 420f))
        scale.animateTo(1f, tween(120))
        kotlinx.coroutines.delay(380)
        alpha.animateTo(0f, tween(260))
    }
    Icon(
        Icons.Default.Favorite, null,
        tint = Color.White,
        modifier = modifier.size(96.dp).graphicsLayer {
            scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
            shadowElevation = 18f
        }
    )
}
