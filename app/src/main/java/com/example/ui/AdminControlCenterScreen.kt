@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.R
import com.example.data.User
import com.example.data.UserReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

private data class AdminMenuEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val badge: String = ""
)

/** Main Admin landing page and independently navigable settings sub-pages. */
@Composable
fun AdminControlCenterScreen(
    viewModel: ChatViewModel,
    onOpenAnalytics: () -> Unit,
    /** null = full admin; otherwise the sub-admin's delegated permissions. */
    staffPermissions: Set<String>? = null
) {
    val isStaffMode = staffPermissions != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flagshipConfig by viewModel.flagshipConfig.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val storageConfig by viewModel.storageRoutingConfig.collectAsState()
    val storageHealth by viewModel.storageSetupHealth.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val gatewayHealth by viewModel.gatewayHealth.collectAsState()
    val presenceUrl by viewModel.presenceWorkerUrl.collectAsState()
    val presenceHealth by viewModel.presenceWorkerHealth.collectAsState()
    val presenceDashboard by viewModel.presenceDashboard.collectAsState()
    val reports by viewModel.adminReports.collectAsState()
    val premiumRequests by viewModel.premiumRequests.collectAsState()
    val featureRequests by viewModel.featureRequests.collectAsState()
    val chatBackgroundImages by viewModel.chatBackgroundImages.collectAsState()
    val globalSoundUrls by viewModel.globalSoundUrls.collectAsState()

    var selectedPage by rememberSaveable { mutableStateOf("dashboard") }
    // Hoisted so returning from a sub-page restores the dashboard position instead of jumping to the top.
    val dashboardScroll = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }
    BackHandler(enabled = selectedPage != "dashboard") { selectedPage = "dashboard" }

    LaunchedEffect(webhookUrl) {
        viewModel.testFcmGateway(webhookUrl)
        viewModel.loadAdminReports()
    }

    var updateEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.updateEnabled) }
    var versionCode by remember(flagshipConfig) { mutableStateOf(flagshipConfig.latestVersionCode.toString()) }
    var versionName by remember(flagshipConfig) { mutableStateOf(flagshipConfig.versionName) }
    var releaseNotes by remember(flagshipConfig) { mutableStateOf(flagshipConfig.releaseNotes) }
    var apkUrl by remember(flagshipConfig) { mutableStateOf(flagshipConfig.apkUrl) }
    var apkKey by remember(flagshipConfig) { mutableStateOf(flagshipConfig.apkR2Key) }
    var noticeEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.noticeEnabled) }
    var noticeTitle by remember(flagshipConfig) { mutableStateOf(flagshipConfig.noticeTitle) }
    var noticeBody by remember(flagshipConfig) { mutableStateOf(flagshipConfig.noticeBody) }
    var maintenanceEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.maintenanceMode) }
    var maintenanceTitle by remember(flagshipConfig) { mutableStateOf(flagshipConfig.maintenanceTitle) }
    // Minutes left on the countdown; 0 = no timer. Pre-filled from the running timer.
    var maintenanceMinutes by remember(flagshipConfig) {
        mutableStateOf(((flagshipConfig.maintenanceEndsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L).toString().takeIf { flagshipConfig.maintenanceEndsAt > System.currentTimeMillis() } ?: "0")
    }
    var reelsEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.reelsEnabled) }
    var assistantEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.assistantEnabled) }
    var locationSharingEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.locationSharingEnabled) }
    val aiPublic by AiPublicInfo.info.collectAsState()
    val cloudDbOn by CloudMessageStore.enabled.collectAsState()
    var aiName by remember(flagshipConfig) { mutableStateOf(flagshipConfig.aiDisplayName) }
    var aiPrompt by remember(flagshipConfig) { mutableStateOf(flagshipConfig.aiSystemPrompt) }
    var premiumEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumEnabled) }
    var premiumUpsellEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumUpsellEnabled) }
    var paymentNumber by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumPaymentNumber) }
    var monthlyPrice by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumMonthlyPrice.toString()) }
    var yearlyPrice by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumYearlyPrice.toString()) }
    var lifetimePrice by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumLifetimePrice.toString()) }
    var monthlyEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumMonthlyEnabled) }
    var yearlyEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumYearlyEnabled) }
    var lifetimeEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumLifetimeEnabled) }
    var bkashEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumBkashEnabled) }
    var nagadEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumNagadEnabled) }
    var rocketEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.premiumRocketEnabled) }
    var smartReplyEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.smartReplyEnabled) }
    var memorySearchEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.memorySearchEnabled) }
    var aiStreamingEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.aiStreamingEnabled) }
    var aiWebSearchEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.aiWebSearchEnabled) }
    var aiHumanStyle by remember(flagshipConfig) { mutableStateOf(flagshipConfig.aiHumanStyle) }
    var phoneAuthEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.phoneAuthEnabled) }
    var emailAuthEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.emailAuthEnabled) }
    var contactsDiscoveryEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.contactsDiscoveryEnabled) }
    var defaultCountryCode by remember(flagshipConfig) { mutableStateOf(flagshipConfig.defaultCountryCode) }
    var taskExtractionEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.taskExtractionEnabled) }
    var callQualityEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.advancedCallQualityEnabled) }
    var advancedThemesEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.advancedThemesEnabled) }
    var creatorProfileEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.creatorProfileEnabled) }
    var analyticsEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.analyticsEnabled) }
    var tiktokModeEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.tiktokModeEnabled) }
    var funEnabled by remember(flagshipConfig) { mutableStateOf(flagshipConfig.funCampaignEnabled) }
    var funTitle by remember(flagshipConfig) { mutableStateOf(flagshipConfig.funCampaignTitle) }
    var funBody by remember(flagshipConfig) { mutableStateOf(flagshipConfig.funCampaignBody) }

    var adminUrlInput by remember(webhookUrl) { mutableStateOf(webhookUrl) }
    var adminPresenceUrlInput by remember(presenceUrl) { mutableStateOf(presenceUrl) }
    var storageProvider by remember(storageConfig.provider) { mutableStateOf(storageConfig.provider) }
    var storageProjectUrl by remember(storageConfig.supabaseUrl) { mutableStateOf(storageConfig.supabaseUrl) }
    var storageWorkerUrl by remember(storageConfig.storageWorkerUrl) { mutableStateOf(storageConfig.storageWorkerUrl) }
    var storageAutoCreate by remember(storageConfig.autoCreateBuckets) { mutableStateOf(storageConfig.autoCreateBuckets) }
    var bucketAvatars by remember(storageConfig.bucketAvatars) { mutableStateOf(storageConfig.bucketAvatars) }
    var bucketChat by remember(storageConfig.bucketChatImages) { mutableStateOf(storageConfig.bucketChatImages) }
    var bucketVoice by remember(storageConfig.bucketVoiceNotes) { mutableStateOf(storageConfig.bucketVoiceNotes) }
    var bucketPosts by remember(storageConfig.bucketPosts) { mutableStateOf(storageConfig.bucketPosts) }
    var bucketReels by remember(storageConfig.bucketReels) { mutableStateOf(storageConfig.bucketReels) }
    var bucketThumbnails by remember(storageConfig.bucketThumbnails) { mutableStateOf(storageConfig.bucketThumbnails) }
    var bucketUpdates by remember(storageConfig.bucketUpdates) { mutableStateOf(storageConfig.bucketUpdates) }
    var storageSaving by remember { mutableStateOf(false) }
    var presenceQuery by rememberSaveable { mutableStateOf("") }
    var adminUserSearch by rememberSaveable { mutableStateOf("") }
    var adminUserFilter by rememberSaveable { mutableStateOf("all") }
    var selectedAdminUser by remember { mutableStateOf<User?>(null) }
    var giftDays by rememberSaveable { mutableStateOf("5") }
    var giftCount by rememberSaveable { mutableStateOf("10") }
    var updateUploading by remember { mutableStateOf(false) }
    var updateUploadProgress by remember { mutableIntStateOf(0) }

    val updateApkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        updateUploading = true
        updateUploadProgress = 0
        scope.launch {
            val candidate = withContext(Dispatchers.IO) { verifyUpdateApk(context, uri) }
            candidate.onSuccess { apk ->
                versionCode = apk.versionCode.toString()
                if (apk.versionName.isNotBlank()) versionName = apk.versionName
                viewModel.uploadMediaToR2(
                    apk.bytes,
                    "application/vnd.android.package-archive",
                    "update",
                    "apk",
                    onProgress = { percent, _ -> updateUploadProgress = percent },
                    onSuccess = { media ->
                        updateUploading = false
                        apkUrl = media.publicUrl
                        apkKey = media.key
                        updateEnabled = true
                    },
                    onFailure = { error ->
                        updateUploading = false
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }.onFailure { error ->
                updateUploading = false
                Toast.makeText(context, error.message ?: "APK verification failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    val workerReports by com.example.data.WorkerHealth.reports.collectAsState()
    val workerOutdatedSeen by com.example.data.WorkerHealth.outdatedSeen.collectAsState()
    LaunchedEffect(Unit) { if (staffPermissions == null) com.example.data.WorkerHealth.checkAll() }
    val workerNeedsUpdate = workerReports.firstOrNull()?.let { !it.reachable || it.outdated || !it.serviceAccount } ?: workerOutdatedSeen
    val allMenuItems = listOf(
        AdminMenuEntry("server_health", "Server health & updater", "Worker version, secrets, one-tap Worker update", Icons.Outlined.Dns, Color(0xFF00897B), if (workerNeedsUpdate) "FIX" else "OK"),
        AdminMenuEntry("staff", "Sub-admins & roles", "Delegate access: users, posts, reports, Premium…", Icons.Outlined.AdminPanelSettings, Color(0xFF3949AB), users.count { it.staffRole.isNotBlank() }.takeIf { it > 0 }?.toString().orEmpty()),
        AdminMenuEntry("email_security", "Email & 2FA", "Multi-provider OTP mail pool, Gmail API, failover and test", Icons.Outlined.MarkEmailRead, Color(0xFF00897B)),
        AdminMenuEntry("updates", "App updates", "Signed APK, release notes and rollout", Icons.Outlined.Publish, Color(0xFF635BFF), if (flagshipConfig.updateEnabled) "LIVE" else ""),
        AdminMenuEntry("maintenance", "Maintenance & notice", "Service status and announcements", Icons.Outlined.Build, Color(0xFFE28A22)),
        AdminMenuEntry("announce", "Announcements", "Banner for every user: notices, features, events", Icons.Outlined.Campaign, Color(0xFF2979FF)),
        AdminMenuEntry("audit", "Audit log", "Every admin change with time and account", Icons.Outlined.History, Color(0xFF546E7A)),
        AdminMenuEntry("power", "Power tools", "Broadcast push, live switches, reminders, typing sound", Icons.Outlined.Bolt, Color(0xFFFF6D00)),
        AdminMenuEntry("cloud_db", "Cloud backup", "Text messages in Cloudflare D1 · on/off · test", Icons.Outlined.Cloud, Color(0xFFF38020), if (cloudDbOn) "ON" else ""),
        AdminMenuEntry("ai_autoreply", "AI auto-reply (admin)", "AI answers messages sent to you — server side", Icons.Outlined.SmartToy, Color(0xFF7C4DFF)),
        AdminMenuEntry("ai_providers", "AI providers & keys", "Update keys, test models, custom providers, hide AI", Icons.Outlined.Key, Color(0xFF7A4FE0)),
        AdminMenuEntry("workers", "Smart worker pool", "3-4 workers all active: weighted random + automatic failover", Icons.Outlined.Hub, Color(0xFF2B7FD9)),
        AdminMenuEntry("insights", "Smart insights", "Segments, sign-up chart, health checks, CSV export", Icons.Outlined.Insights, Color(0xFF00A3A3)),
        AdminMenuEntry("ai", "AI & feature flags", "Assistant and staged capabilities", Icons.Outlined.AutoAwesome, Color(0xFF8E5CE6)),
        AdminMenuEntry("premium", "Premium & payments", "Plans, pricing and payment methods", Icons.Outlined.WorkspacePremium, Color(0xFFCF9829)),
        AdminMenuEntry("campaigns", "Campaigns", "One-time Friday Fun message", Icons.Outlined.Campaign, Color(0xFFDB5B8C)),
        AdminMenuEntry("users", "User management", "Search profiles, filter status and open quick details", Icons.Outlined.Groups, Color(0xFF4B83BE), users.size.takeIf { it > 0 }?.toString().orEmpty()),
        AdminMenuEntry("reports", "Safety reports", "Review reports and take action", Icons.Outlined.Security, Color(0xFFD84949), reports.size.takeIf { it > 0 }?.toString().orEmpty()),
        AdminMenuEntry("feature_requests", "Feature requests", "Review community suggestions", Icons.Outlined.Lightbulb, Color(0xFF2F9A89), featureRequests.size.takeIf { it > 0 }?.toString().orEmpty()),
        AdminMenuEntry("premium_requests", "Payment approvals", "Approve or reject Premium requests", Icons.Outlined.Payments, Color(0xFFBD8B24), premiumRequests.size.takeIf { it > 0 }?.toString().orEmpty()),
        AdminMenuEntry("premium_access", "Gifts & access", "Grant Premium or revoke access", Icons.Outlined.CardGiftcard, Color(0xFFCB5C90)),
        AdminMenuEntry("presence", "Presence / 5-min probe", "Supabase Edge Function + Cron", Icons.Outlined.Groups, Color(0xFF358B9A)),
        AdminMenuEntry("chat_media", "Chat backgrounds & sounds", "Photo backgrounds and default app sounds", Icons.Outlined.Wallpaper, Color(0xFF8B62D9), chatBackgroundImages.size.takeIf { it > 0 }?.toString().orEmpty()),
        AdminMenuEntry("polls", "Polls & votes", "Timed polls with live results and voters", Icons.Outlined.HowToVote, Color(0xFF7C4DFF)),
        AdminMenuEntry("auto_post", "Auto AI posts", "Scheduled AI posts from the admin account", Icons.Outlined.AutoAwesome, Color(0xFFE0457B)),
        AdminMenuEntry("reels", "Reels & TikTok", "Choose the Reels experience", Icons.Outlined.SmartDisplay, Color(0xFF1B9A8B)),
        AdminMenuEntry("storage", "Storage routing", "Supabase buckets and upload tests", Icons.Outlined.CloudUpload, Color(0xFF5386CA)),
        AdminMenuEntry("gateway", "Push / FCM gateway", "Chat, call and diagnostic push", Icons.Outlined.SendToMobile, Color(0xFF4E78B8)),
        AdminMenuEntry("infrastructure", "Service health", "Health checks and admin tools", Icons.Outlined.HealthAndSafety, Color(0xFF477C67)),
        AdminMenuEntry("analytics", "Analytics", "Open usage and content insights", Icons.Outlined.Analytics, Color(0xFF6D61CC))
    )
    val staffMenuIds = mapOf(
        "users" to "users", "reports" to "reports", "feature_requests" to "support", "premium_requests" to "premium",
        "premium_access" to "premium", "announce" to "announce", "analytics" to "analytics", "insights" to "analytics"
    )
    val menuItems = if (staffPermissions == null) allMenuItems
        else allMenuItems.filter { entry -> staffMenuIds[entry.id]?.let { it in staffPermissions } == true }
    val pageEntry = menuItems.firstOrNull { it.id == selectedPage }

    Column(Modifier.fillMaxSize()) {
        if (selectedPage == "dashboard") {
            Column(
                Modifier.fillMaxSize().verticalScroll(dashboardScroll).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(2.dp))
                if (isStaffMode) StaffPanelHeader(viewModel, staffPermissions.orEmpty())
                if (!isStaffMode && workerNeedsUpdate) {
                    Surface(
                        onClick = { selectedPage = "server_health" },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Dns, null, tint = Color(0xFFE65100))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Server needs an update", fontWeight = FontWeight.Bold, color = Color(0xFFBF360C))
                                Text("Some actions fail with \"Missing required fields\". Tap to fix in one step.", fontSize = 11.sp, color = Color(0xFF6D4C41))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFE65100))
                        }
                    }
                }
                if (!isStaffMode) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF24235C), Color(0xFF523F9D), Color(0xFF187E91))),
                            RoundedCornerShape(30.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .15f)) {
                                Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.padding(11.dp), tint = Color.White)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text("Admin dashboard", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                                Text("Control center • manage each area separately", color = Color.White.copy(alpha = .82f), fontSize = 11.sp)
                            }
                        }
                        Text(
                            "Choose a section below. Each tool now opens its own page, with its own save and test controls.",
                            color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardPill("${users.size} profiles", Icons.Default.People)
                            DashboardPill("${reports.size} reports", Icons.Default.Flag)
                            DashboardPill("${premiumRequests.size} payments", Icons.Default.WorkspacePremium)
                        }
                    }
                }

                val dashboardPosts by viewModel.postsState.collectAsState()
                var confirmQuickMaintenance by remember { mutableStateOf<Boolean?>(null) }
                AdminLivePulse(
                    users = users,
                    posts = dashboardPosts,
                    config = flagshipConfig,
                    onToggleMaintenance = { confirmQuickMaintenance = it },
                    onToggleReels = { on ->
                        viewModel.updateFlagshipFields(mapOf("reelsEnabled" to on)) { ok ->
                            Toast.makeText(context, if (!ok) "Save failed" else if (on) "Reels visible to users" else "Reels hidden from users", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSection = { selectedPage = it }
                )
                confirmQuickMaintenance?.let { turnOn ->
                    AlertDialog(
                        onDismissRequest = { confirmQuickMaintenance = null },
                        title = { Text(if (turnOn) "Start maintenance?" else "End maintenance?") },
                        text = { Text(if (turnOn) "Users will be blocked by the maintenance screen (no timer). Use Maintenance & notice to add a countdown." else "Users can use the app again immediately.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmQuickMaintenance = null
                                viewModel.updateFlagshipFields(mapOf("maintenanceMode" to turnOn, "maintenanceEndsAt" to 0L)) { ok ->
                                    Toast.makeText(context, if (ok) "Maintenance ${if (turnOn) "started" else "ended"}" else "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(if (turnOn) "Start" else "End") }
                        },
                        dismissButton = { TextButton(onClick = { confirmQuickMaintenance = null }) { Text("Cancel") } }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStatCard("Profiles", users.size.toString(), Icons.Default.People, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    DashboardStatCard("Open reports", reports.size.toString(), Icons.Default.Security, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStatCard("Payment requests", premiumRequests.size.toString(), Icons.Default.Payments, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    DashboardStatCard("Feature ideas", featureRequests.size.toString(), Icons.Default.Lightbulb, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }

                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isStaffMode) "Your tools" else "Management", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("Tap a tile to open its dedicated settings page", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${menuItems.size} sections", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var toolSearch by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    toolSearch, { toolSearch = it.take(40) },
                    placeholder = { Text("Search admin tools…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (toolSearch.isNotBlank()) IconButton(onClick = { toolSearch = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
                )
                val shownMenuItems = menuItems.filter { toolSearch.isBlank() || it.title.contains(toolSearch, true) || it.description.contains(toolSearch, true) }
                if (shownMenuItems.isEmpty()) Text("No tool matches “$toolSearch”", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                shownMenuItems.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { entry ->
                            AdminMenuTile(entry, Modifier.weight(1f)) {
                                if (entry.id == "analytics") onOpenAnalytics() else selectedPage = entry.id
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (isStaffMode) "Staff session • limited access" else "Admin session authenticated", fontWeight = FontWeight.Bold)
                            Text(if (isStaffMode) "Every action is checked by the server and written to the admin audit log" else "Signed in with the authorized administrator account", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPage = "dashboard" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to admin dashboard")
                }
                Column(Modifier.weight(1f)) {
                    Text(pageEntry?.title ?: "Admin settings", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text("Admin dashboard  /  ${pageEntry?.title ?: "Settings"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                pageEntry?.let { entry ->
                    Surface(shape = CircleShape, color = entry.accent.copy(alpha = .12f)) {
                        Icon(entry.icon, null, Modifier.padding(10.dp), tint = entry.accent)
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                pageEntry?.let { AdminPageIntro(it) }
                when (selectedPage) {
                    "users" -> {
                        val now = System.currentTimeMillis()
                        val query = adminUserSearch.trim()
                        val visibleAdminUsers = users.filter { person ->
                            val isOnlineNow = person.isOnline && (person.onlineUntil <= 0L || person.onlineUntil > now)
                            val hasPremium = person.isPremium && (person.premiumPlan.equals("lifetime", true) || person.premiumUntil > now)
                            val matchesQuery = query.isBlank() || person.name.contains(query, true) ||
                                person.username.contains(query, true) || person.uid.contains(query, true)
                            val matchesFilter = when (adminUserFilter) {
                                "online" -> isOnlineNow
                                "offline" -> !isOnlineNow
                                "premium" -> hasPremium
                                else -> true
                            }
                            matchesQuery && matchesFilter
                        }.sortedWith(
                            compareByDescending<User> { it.isOnline && (it.onlineUntil <= 0L || it.onlineUntil > now) }
                                .thenBy { it.name.lowercase(Locale.getDefault()) }
                        )
                        val onlineCount = users.count { it.isOnline && (it.onlineUntil <= 0L || it.onlineUntil > now) }
                        val premiumCount = users.count { it.isPremium && (it.premiumPlan.equals("lifetime", true) || it.premiumUntil > now) }

                        AdminPanelCard("User directory", "Search profiles, filter online status and open quick profile actions", Icons.Outlined.Groups) {
                            OutlinedTextField(
                                value = adminUserSearch,
                                onValueChange = { adminUserSearch = it.take(120) },
                                label = { Text("Search name, username or UID") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (adminUserSearch.isNotBlank()) IconButton(onClick = { adminUserSearch = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear user search")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                listOf(
                                    "all" to "All (${users.size})",
                                    "online" to "Online ($onlineCount)",
                                    "offline" to "Offline (${users.size - onlineCount})",
                                    "premium" to "Premium ($premiumCount)"
                                ).forEach { (filter, label) ->
                                    FilterChip(
                                        selected = adminUserFilter == filter,
                                        onClick = { adminUserFilter = filter },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Text("${visibleAdminUsers.size} shown • profiles update from Firestore", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            when {
                                users.isEmpty() -> Text("No user profiles are loaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                visibleAdminUsers.isEmpty() -> Text("No users match this search and filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> visibleAdminUsers.take(150).forEach { person ->
                                    AdminUserManagementRow(
                                        user = person,
                                        now = now,
                                        onOpenProfile = { selectedAdminUser = person },
                                        onCopyUid = { copyAdminUserUid(context, person.uid) }
                                    )
                                }
                            }
                            if (visibleAdminUsers.size > 150) {
                                Text("Showing 150 of ${visibleAdminUsers.size}; narrow the search to find a specific profile.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    "updates" -> {
                        AdminPanelCard("APK update campaign", "Verify and publish a signed Convo Chat APK", Icons.Outlined.Publish) {
                            AdminSwitchRow("Publish APK update", "Users see this campaign once; the admin keeps emergency access.", updateEnabled, { updateEnabled = it })
                            if (flagshipConfig.updateId.isNotBlank()) {
                                AdminInlineInfo("Current campaign ${flagshipConfig.updateId.take(8)} • already-installed users will not be asked twice")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(versionCode, { versionCode = it.filter(Char::isDigit).take(8) }, label = { Text("Version code") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(versionName, { versionName = it.take(30) }, label = { Text("Version name") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(releaseNotes, { releaseNotes = it.take(4000) }, label = { Text("What's new / release notes") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { updateApkPicker.launch("application/vnd.android.package-archive") }, enabled = !updateUploading, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text(if (apkUrl.isBlank()) "Choose and verify signed APK" else "Replace update APK")
                            }
                            if (updateUploading) {
                                LinearProgressIndicator(progress = { updateUploadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("Uploading update • $updateUploadProgress%", fontSize = 11.sp)
                            }
                            if (apkUrl.isNotBlank()) Text("APK ready: ${apkKey.ifBlank { apkUrl }}", fontSize = 10.sp, color = Color(0xFF23864D), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Button(
                                onClick = {
                                    val code = versionCode.toIntOrNull() ?: com.example.BuildConfig.VERSION_CODE
                                    viewModel.publishFlagshipConfig(
                                        flagshipConfig.copy(
                                            updateEnabled = updateEnabled,
                                            mandatoryUpdate = updateEnabled,
                                            latestVersionCode = code,
                                            minimumVersionCode = if (updateEnabled) code else 1,
                                            versionName = versionName,
                                            apkUrl = apkUrl,
                                            apkR2Key = apkKey,
                                            releaseNotes = releaseNotes,
                                            tiktokModeEnabled = flagshipConfig.tiktokModeEnabled
                                        )
                                    ) { ok -> Toast.makeText(context, if (ok) "Update campaign published" else "Update publish failed", Toast.LENGTH_LONG).show() }
                                },
                                enabled = updateEnabled && apkUrl.startsWith("https://"),
                                modifier = Modifier.fillMaxWidth()
                            ) { Icon(Icons.Outlined.Publish, null); Spacer(Modifier.width(8.dp)); Text("Publish APK update") }
                            if (flagshipConfig.updateEnabled) {
                                OutlinedButton(onClick = {
                                    viewModel.publishFlagshipConfig(flagshipConfig.copy(updateEnabled = false, mandatoryUpdate = false))
                                }, modifier = Modifier.fillMaxWidth()) { Text("Disable current update") }
                            }
                        }
                    }
                    "maintenance" -> {
                        AdminPanelCard("Maintenance & global notice", "Control service availability and app-wide announcements", Icons.Outlined.Build) {
                            AdminSwitchRow("Maintenance mode", "Temporarily pause the app for users while you work.", maintenanceEnabled, { maintenanceEnabled = it })
                            if (maintenanceEnabled) {
                                OutlinedTextField(maintenanceTitle, { maintenanceTitle = it.take(80) }, label = { Text("Maintenance title (optional)") }, placeholder = { Text("We're upgrading Convo Chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("Countdown timer shown to users", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("0" to "No timer", "15" to "15 min", "30" to "30 min", "60" to "1 hour", "120" to "2 hours", "360" to "6 hours", "1440" to "1 day").forEach { (minutes, label) ->
                                        FilterChip(selected = maintenanceMinutes == minutes, onClick = { maintenanceMinutes = minutes }, label = { Text(label) })
                                    }
                                }
                                OutlinedTextField(
                                    maintenanceMinutes, { maintenanceMinutes = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Custom minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    supportingText = { Text(if ((maintenanceMinutes.toLongOrNull() ?: 0L) > 0L) "Users see a live countdown; the app unlocks automatically at ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (maintenanceMinutes.toLongOrNull() ?: 0L) * 60_000L))}" else "No countdown; users just see the maintenance message.") }
                                )
                            }
                            HorizontalDivider()
                            AdminSwitchRow("Show global notice", "Display a notice dialog to users.", noticeEnabled, { noticeEnabled = it })
                            OutlinedTextField(noticeTitle, { noticeTitle = it.take(100) }, label = { Text("Notice title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(noticeBody, { noticeBody = it.take(1000) }, label = { Text("Notice details") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                viewModel.updateFlagshipFields(mapOf(
                                    "maintenanceMode" to maintenanceEnabled,
                                    "maintenanceTitle" to maintenanceTitle.trim(),
                                    "maintenanceEndsAt" to if (maintenanceEnabled && (maintenanceMinutes.toLongOrNull() ?: 0L) > 0L) System.currentTimeMillis() + (maintenanceMinutes.toLongOrNull() ?: 0L) * 60_000L else 0L,
                                    "noticeEnabled" to noticeEnabled,
                                    "noticeTitle" to noticeTitle,
                                    "noticeBody" to noticeBody
                                )) { ok -> Toast.makeText(context, if (ok) "Maintenance and notice settings saved" else "Save failed", Toast.LENGTH_LONG).show() }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Save maintenance & notice") }
                        }
                    }
                    "ai_providers" -> {
                        AdminPanelCard("AI providers & API keys", "Mistral, OpenAI, Gemini, Groq, OpenRouter, DeepSeek, Together or any OpenAI-compatible endpoint", Icons.Outlined.Key) {
                            AdminAiProvidersPanel(assistantEnabled) { enabled ->
                                assistantEnabled = enabled
                                viewModel.updateFlagshipFields(mapOf("assistantEnabled" to enabled)) { ok ->
                                    Toast.makeText(context, if (!ok) "Save failed" else if (enabled) "AI features visible" else "AI features hidden", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    "ai_autoreply" -> {
                        AdminPanelCard("AI auto-reply", "When ON, messages to the admin get an AI answer from the server", Icons.Outlined.SmartToy) { AdminAiAutoReplyPanel(viewModel) }
                    }
                    "staff" -> {
                        AdminPanelCard("Sub-admins & roles", "Give trusted people only the access they need", Icons.Outlined.AdminPanelSettings) { AdminStaffPanel(users) }
                    }
                    "server_health" -> {
                        AdminPanelCard("Server health & updater", "Fixes \"Missing required fields\" by updating the Worker", Icons.Outlined.Dns) { AdminServerHealthPanel(users) }
                    }
                    "email_security" -> {
                        AdminPanelCard("Email & 2FA", "Sender for login codes, new-device alerts and recovery", Icons.Outlined.MarkEmailRead) { AdminEmailSecurityPanel() }
                    }
                    "power" -> {
                        AdminPanelCard("Power tools", "Act on every user at once and flip live switches", Icons.Outlined.Bolt) { AdminPowerToolsPanel(viewModel) }
                    }
                    "cloud_db" -> {
                        AdminPanelCard("Cloud backup", "Store text & emoji message bodies in your Cloudflare D1 database", Icons.Outlined.Cloud) { AdminCloudStorePanel() }
                    }
                    "insights" -> {
                        AdminPanelCard("Smart insights", "Live numbers from loaded profiles - no extra database reads", Icons.Outlined.Insights) { AdminInsightsPanel(users) }
                    }
                    "announce" -> {
                        AdminPanelCard("Announcements", "Publish a banner to every signed-in user", Icons.Outlined.Campaign) { AdminAnnouncementPanel() }
                    }
                    "audit" -> {
                        AdminPanelCard("Audit log", "Latest 100 admin actions", Icons.Outlined.History) { AdminAuditPanel() }
                    }
                    "workers" -> {
                        AdminPanelCard("Smart worker pool", "All workers active together: weighted random pick per request, automatic failover and cooldown", Icons.Outlined.Hub) {
                            AdminWorkerRoutingPanel(viewModel)
                        }
                    }
                    "ai" -> {
                        AdminPanelCard("AI assistant", "Edit the assistant presentation and safe system guidance", Icons.Outlined.AutoAwesome) {
                            AdminSwitchRow("Show assistant to all users", "Remote control for the in-app assistant.", assistantEnabled, { assistantEnabled = it })
                            OutlinedTextField(aiName, { aiName = it.take(60) }, label = { Text("Assistant name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Surface(onClick = { selectedPage = "ai_providers" }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Model: ${aiPublic.model.ifBlank { "server default" }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${aiPublic.providerName.ifBlank { "Mistral (server key)" }} · change provider/model on the AI providers page", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                            OutlinedTextField(aiPrompt, { aiPrompt = it.take(2000) }, label = { Text("Safe system instructions") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                            AdminInlineInfo("API keys and the model are managed only on the AI providers page and stay server-side.")
                            HorizontalDivider()
                            Text("Remote feature flags", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Stage-roll out app features without changing the APK.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AdminSwitchRow("Smart replies", "", smartReplyEnabled, { smartReplyEnabled = it })
                            AdminSwitchRow("AI memory search", "", memorySearchEnabled, { memorySearchEnabled = it })
                            AdminSwitchRow("AI live streaming", "Answers appear word by word while the AI writes them. Off = the full answer appears at once.", aiStreamingEnabled, { aiStreamingEnabled = it })
                            AdminSwitchRow("AI web search", "The assistant can search the internet for fresh facts and shows its sources. Keys: AI providers page → Web search.", aiWebSearchEnabled, { aiWebSearchEnabled = it })
                            AdminSwitchRow("Human-like talking style", "Warm, natural replies in the user's own language (Bengali / English / Banglish).", aiHumanStyle, { aiHumanStyle = it })
                            HorizontalDivider()
                            Text("Sign-up & sign-in methods", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            AdminSwitchRow("E-mail sign-up / sign-in", "Classic e-mail + password accounts.", emailAuthEnabled, { emailAuthEnabled = it || !phoneAuthEnabled })
                            AdminSwitchRow("Phone number sign-up / sign-in", "SMS code via Firebase Phone Auth. Needs: Firebase console → Authentication → Phone enabled + the app's SHA-1/SHA-256 added.", phoneAuthEnabled, { phoneAuthEnabled = it; if (!it) emailAuthEnabled = true })
                            OutlinedTextField(defaultCountryCode, { v -> defaultCountryCode = ("+" + v.filter(Char::isDigit)).take(5) }, label = { Text("Default country code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            AdminSwitchRow("Find friends from contacts", "Users who allow it can find people whose verified phone number is in their contacts (numbers are matched as one-way hashes).", contactsDiscoveryEnabled, { contactsDiscoveryEnabled = it })
                            AdminSwitchRow("Task extraction", "", taskExtractionEnabled, { taskExtractionEnabled = it })
                            AdminSwitchRow("Advanced call quality", "", callQualityEnabled, { callQualityEnabled = it })
                            AdminSwitchRow("Advanced themes", "", advancedThemesEnabled, { advancedThemesEnabled = it })
                            AdminSwitchRow("Creator profile", "", creatorProfileEnabled, { creatorProfileEnabled = it })
                            AdminSwitchRow("Usage analytics", "", analyticsEnabled, { analyticsEnabled = it })
                            AdminSwitchRow("Location sharing", "Current + live location in chats (live sharing runs as a foreground service with a notification).", locationSharingEnabled, { locationSharingEnabled = it })
                            Button(onClick = {
                                viewModel.updateFlagshipFields(mapOf(
                                    "assistantEnabled" to assistantEnabled,
                                    "aiDisplayName" to aiName,
                                    "aiSystemPrompt" to aiPrompt,
                                    "smartReplyEnabled" to smartReplyEnabled,
                                    "memorySearchEnabled" to memorySearchEnabled,
                                    "aiStreamingEnabled" to aiStreamingEnabled,
                                    "aiWebSearchEnabled" to aiWebSearchEnabled,
                                    "aiHumanStyle" to aiHumanStyle,
                                    "phoneAuthEnabled" to phoneAuthEnabled,
                                    "emailAuthEnabled" to (emailAuthEnabled || !phoneAuthEnabled),
                                    "defaultCountryCode" to defaultCountryCode.ifBlank { "+880" },
                                    "contactsDiscoveryEnabled" to contactsDiscoveryEnabled,
                                    "taskExtractionEnabled" to taskExtractionEnabled,
                                    "advancedCallQualityEnabled" to callQualityEnabled,
                                    "advancedThemesEnabled" to advancedThemesEnabled,
                                    "creatorProfileEnabled" to creatorProfileEnabled,
                                    "analyticsEnabled" to analyticsEnabled,
                                    "locationSharingEnabled" to locationSharingEnabled
                                )) { ok -> Toast.makeText(context, if (ok) "AI & feature flags saved" else "Save failed", Toast.LENGTH_LONG).show() }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Save AI & feature flags") }
                        }
                    }
                    "premium" -> {
                        AdminPanelCard("Premium & payments", "Configure plans, pricing and supported payment methods", Icons.Outlined.WorkspacePremium) {
                            AdminSwitchRow("Premium purchases enabled", "Allow users to submit a Premium request.", premiumEnabled, { premiumEnabled = it })
                            AdminSwitchRow("First-login Premium showcase", "Show the Premium introduction to eligible users.", premiumUpsellEnabled, { premiumUpsellEnabled = it })
                            OutlinedTextField(paymentNumber, { paymentNumber = it.filter(Char::isDigit).take(15) }, label = { Text("Payment number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                FilterChip(monthlyEnabled, { monthlyEnabled = !monthlyEnabled }, { Text("Monthly") })
                                FilterChip(yearlyEnabled, { yearlyEnabled = !yearlyEnabled }, { Text("Yearly") })
                                FilterChip(lifetimeEnabled, { lifetimeEnabled = !lifetimeEnabled }, { Text("Lifetime") })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(monthlyPrice, { monthlyPrice = it.filter(Char::isDigit).take(7) }, label = { Text("Monthly ৳") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(yearlyPrice, { yearlyPrice = it.filter(Char::isDigit).take(7) }, label = { Text("Yearly ৳") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(lifetimePrice, { lifetimePrice = it.filter(Char::isDigit).take(7) }, label = { Text("Lifetime ৳") }, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                FilterChip(bkashEnabled, { bkashEnabled = !bkashEnabled }, { Text("bKash") })
                                FilterChip(nagadEnabled, { nagadEnabled = !nagadEnabled }, { Text("Nagad") })
                                FilterChip(rocketEnabled, { rocketEnabled = !rocketEnabled }, { Text("Rocket") })
                            }
                            Button(onClick = {
                                viewModel.updateFlagshipFields(mapOf(
                                    "premiumEnabled" to premiumEnabled,
                                    "premiumUpsellEnabled" to premiumUpsellEnabled,
                                    "premiumPaymentNumber" to paymentNumber,
                                    "premiumMonthlyPrice" to (monthlyPrice.toIntOrNull() ?: 199),
                                    "premiumYearlyPrice" to (yearlyPrice.toIntOrNull() ?: 1499),
                                    "premiumLifetimePrice" to (lifetimePrice.toIntOrNull() ?: 3999),
                                    "premiumMonthlyEnabled" to monthlyEnabled,
                                    "premiumYearlyEnabled" to yearlyEnabled,
                                    "premiumLifetimeEnabled" to lifetimeEnabled,
                                    "premiumBkashEnabled" to bkashEnabled,
                                    "premiumNagadEnabled" to nagadEnabled,
                                    "premiumRocketEnabled" to rocketEnabled
                                )) { ok -> Toast.makeText(context, if (ok) "Premium settings saved" else "Save failed", Toast.LENGTH_LONG).show() }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Save Premium settings") }
                        }
                    }
                    "campaigns" -> {
                        AdminPanelCard("One-time campaign", "Publish a Friday Fun message once per campaign ID", Icons.Outlined.Campaign) {
                            AdminSwitchRow("Campaign enabled", "Show this campaign to eligible users.", funEnabled, { funEnabled = it })
                            OutlinedTextField(funTitle, { funTitle = it.take(100) }, label = { Text("Campaign title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(funBody, { funBody = it.take(500) }, label = { Text("Campaign message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                viewModel.updateFlagshipFields(mapOf(
                                    "funCampaignEnabled" to funEnabled,
                                    "funCampaignId" to "fun_${System.currentTimeMillis()}",
                                    "funCampaignTitle" to funTitle,
                                    "funCampaignBody" to funBody
                                )) { ok -> Toast.makeText(context, if (ok) "New one-time campaign published" else "Publish failed", Toast.LENGTH_LONG).show() }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(7.dp)); Text("Publish new campaign") }
                        }
                    }
                    "reports" -> {
                        AdminPanelCard("Safety reports", "Review a report and take appropriate moderation action", Icons.Outlined.Security) {
                            OutlinedButton(onClick = viewModel::loadAdminReports, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh reports") }
                            if (reports.isEmpty()) Text("No reports are currently loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            reports.sortedByDescending { it.createdAt }.forEach { report ->
                                AdminReportRow(report, context, viewModel)
                            }
                        }
                    }
                    "feature_requests" -> {
                        AdminPanelCard("Community feature requests", "Accept, keep pending, or reject suggestions", Icons.Outlined.Lightbulb) {
                            if (featureRequests.isEmpty()) Text("No feature requests are currently loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            featureRequests.forEach { request ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(request.title, fontWeight = FontWeight.Bold)
                                        Text("${request.requesterName} • ${request.status}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(request.description, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(onClick = { viewModel.updateFeatureRequest(request.id, "accepted") }) { Text("Accept") }
                                            TextButton(onClick = { viewModel.updateFeatureRequest(request.id, "pending") }) { Text("Pending") }
                                            TextButton(onClick = { viewModel.updateFeatureRequest(request.id, "rejected") }) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "premium_requests" -> {
                        AdminPanelCard("Premium payment approvals", "Verify the submitted transaction before approving", Icons.Outlined.Payments) {
                            if (premiumRequests.isEmpty()) Text("No pending payment requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            premiumRequests.forEach { request ->
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(model = request.userImageUrl.ifBlank { null }, contentDescription = request.userName, error = painterResource(R.drawable.img_app_logo), modifier = Modifier.size(42.dp).clip(CircleShape))
                                            Spacer(Modifier.width(9.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(request.userName, fontWeight = FontWeight.Bold)
                                                Text(request.userEmail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text("${request.plan.uppercase()} • ৳${request.amount} • ${request.paymentMethod.uppercase()}")
                                        Text("Transaction: ${request.transactionId}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        if (request.paymentProofUrl.isNotBlank()) {
                                            AsyncImage(model = request.paymentProofUrl, contentDescription = "Payment proof", modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.reviewPremiumRequest(request, true) { ok -> Toast.makeText(context, if (ok) "Premium activated" else "Approval failed", Toast.LENGTH_LONG).show() } }, modifier = Modifier.weight(1f)) { Text("Approve") }
                                            OutlinedButton(onClick = { viewModel.reviewPremiumRequest(request, false) { ok -> Toast.makeText(context, if (ok) "Request rejected" else "Reject failed", Toast.LENGTH_LONG).show() } }, modifier = Modifier.weight(1f)) { Text("Reject") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "premium_access" -> {
                        AdminPanelCard("Premium access & gifts", "Grant a time-limited gift or revoke an active entitlement", Icons.Outlined.CardGiftcard) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(giftDays, { giftDays = it.filter(Char::isDigit).take(3) }, label = { Text("Gift days") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(giftCount, { giftCount = it.filter(Char::isDigit).take(3) }, label = { Text("Random users") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Button(onClick = {
                                val days = giftDays.toIntOrNull()?.coerceIn(1, 365) ?: 5
                                val targets = users.filterNot { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > System.currentTimeMillis()) }
                                    .shuffled().take(giftCount.toIntOrNull()?.coerceIn(1, 100) ?: 10)
                                viewModel.grantPremiumGift(targets.map { it.uid }, days) { ok ->
                                    Toast.makeText(context, if (ok) "Gift sent to ${targets.size} users" else "Gift failed", Toast.LENGTH_LONG).show()
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CardGiftcard, null); Spacer(Modifier.width(7.dp)); Text("Send Premium gifts") }
                            val activePremium = users.filter { it.isPremium && (it.premiumPlan == "lifetime" || it.premiumUntil > System.currentTimeMillis()) }
                            if (activePremium.isEmpty()) Text("No active Premium profiles are currently loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            activePremium.take(50).forEach { person ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        Text("${person.premiumPlan} • ${person.username}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { viewModel.revokePremium(person.uid) }) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                    "presence" -> {
                        AdminPanelCard("Five-minute presence probe", "Supabase Edge Function sends silent, per-device probes on a five-minute Supabase Cron", Icons.Outlined.Groups) {
                            Text(
                                "বাংলা কনফিগারেশন: এই silent data-only probe কোনো notification banner বা শব্দ দেখায় না। Supabase-এ presence-monitor Edge Function deploy করুন (--no-verify-jwt), Firebase service-account JSON শুধু Edge Function secret হিসেবে দিন, তারপর supabase/sql/presence_cron.sql চালিয়ে পাঁচ মিনিটের Supabase Cron চালু করুন। পুরোনো Cloudflare Cron বন্ধ রাখুন—একটিই schedule সক্রিয় থাকবে। FCM acceptance receipt-এর প্রমাণ নয়; device acknowledgement-ই reachability।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AdminInlineInfo("Service-account JSON/key শুধু Supabase Edge Function secret-এ রাখুন—Android, Firestore, Git বা public log-এ নয়। সেটআপ: docs/presence-edge-function-bn.md")
                            val supabaseFunctionUrl = storageConfig.supabaseUrl.trim().trimEnd('/').takeIf { it.startsWith("https://") }?.let { "$it/functions/v1/presence-monitor" }
                            if (supabaseFunctionUrl != null && adminPresenceUrlInput != supabaseFunctionUrl) {
                                AssistChip(
                                    onClick = { adminPresenceUrlInput = supabaseFunctionUrl },
                                    label = { Text("Use my Supabase project URL", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }
                                )
                            }
                            OutlinedTextField(
                                value = adminPresenceUrlInput,
                                onValueChange = { adminPresenceUrlInput = it.trim() },
                                label = { Text("Presence API base URL") },
                                placeholder = { Text("https://<ref>.supabase.co/functions/v1/presence-monitor") },
                                supportingText = { Text("Supabase Edge Function base URL; omit /health. Supabase Cron is set up once with presence_cron_secret SQL.") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    viewModel.updatePresenceWorkerUrl(adminPresenceUrlInput) { ok, message ->
                                        Toast.makeText(context, message, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Save URL") }
                                OutlinedButton(onClick = { viewModel.testPresenceWorker(adminPresenceUrlInput) }, enabled = !presenceHealth.checking, modifier = Modifier.weight(1f)) {
                                    if (presenceHealth.checking) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Default.HealthAndSafety, null)
                                    Spacer(Modifier.width(6.dp)); Text("Test")
                                }
                            }
                            AdminHealthBanner(presenceHealth.configured, presenceHealth.message, presenceHealth.version)
                            OutlinedButton(
                                onClick = { viewModel.sendPresenceProbeNow { _, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } },
                                enabled = presenceHealth.configured,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SendToMobile, null); Spacer(Modifier.width(7.dp)); Text("Send one-time silent probe")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PresenceMetric("Profiles", presenceDashboard.totalUsers.toString(), Modifier.weight(1f))
                                PresenceMetric("Active", (presenceDashboard.foregroundUsers + presenceDashboard.reachableUsers).toString(), Modifier.weight(1f))
                                PresenceMetric("In app", presenceDashboard.foregroundUsers.toString(), Modifier.weight(1f))
                            }
                            Text("${presenceDashboard.activeUsers} active/recent • ${presenceDashboard.registeredDevices} registered devices • last probe sent: ${if (presenceDashboard.lastProbeAt > 0L) SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(presenceDashboard.lastProbeAt)) else "not sent yet"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(presenceQuery, { presenceQuery = it.take(100) }, label = { Text("Search name, username, or UID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { viewModel.loadPresenceUsers(presenceQuery) }, enabled = !presenceDashboard.checking && presenceHealth.configured, modifier = Modifier.fillMaxWidth()) {
                                if (presenceDashboard.checking) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(7.dp)); Text("Refresh Firebase user list")
                            }
                            if (presenceDashboard.message.isNotBlank()) Text(presenceDashboard.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            presenceDashboard.users.forEach { person ->
                                val statusColor = when (person.status) {
                                    "online" -> Color(0xFF2CBF79)
                                    "reachable" -> Color(0xFF42D392)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                val seenText = if (person.lastActive > 0L) SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(person.lastActive)) else "No recent signal"
                                val probeText = if (person.lastProbeReceivedAt > 0L) "Probe reply ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(person.lastProbeReceivedAt))}" else "No probe reply yet"
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Circle, null, tint = statusColor, modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(9.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(person.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${if (person.username.isNotBlank()) "@${person.username} · " else ""}last seen $seenText", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("$probeText · ${if (person.hasFcmToken) "FCM ready" else "no FCM token"}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(when (person.status) { "online" -> "ACTIVE · IN APP"; "reachable" -> "ACTIVE"; else -> "OFFLINE" }, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                    }
                                }
                            }
                            presenceDashboard.nextCursor?.let { next ->
                                OutlinedButton(onClick = { viewModel.loadPresenceUsers(presenceQuery, next) }, enabled = !presenceDashboard.checking, modifier = Modifier.fillMaxWidth()) { Text("Load more users") }
                            }
                        }
                    }
                    "chat_media" -> {
                        var newBackgroundName by remember { mutableStateOf("") }
                        var newBackgroundPremium by remember { mutableStateOf(false) }
                        var mediaBusy by remember { mutableStateOf(false) }
                        var pendingDelete by remember { mutableStateOf<ChatBackgroundImage?>(null) }
                        var pendingSoundKind by remember { mutableStateOf<String?>(null) }
                        fun report(ok: Boolean, message: String) {
                            scope.launch(Dispatchers.Main) {
                                mediaBusy = false
                                Toast.makeText(context, message, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            }
                        }
                        val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            if (uri != null) {
                                mediaBusy = true
                                scope.launch {
                                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                                    val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
                                    when {
                                        bytes == null -> report(false, "Could not read that image")
                                        bytes.size > 8 * 1024 * 1024 -> report(false, "Please use an image under 8 MB")
                                        else -> viewModel.addChatBackground(bytes, mime, newBackgroundName.ifBlank { "Background ${chatBackgroundImages.size + 1}" }, newBackgroundPremium) { ok, message ->
                                            if (ok) newBackgroundName = ""
                                            report(ok, message)
                                        }
                                    }
                                }
                            }
                        }
                        val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            val kind = pendingSoundKind
                            pendingSoundKind = null
                            if (uri != null && kind != null) {
                                mediaBusy = true
                                scope.launch {
                                    val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                                    val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
                                    if (bytes == null) report(false, "Could not read that sound")
                                    else viewModel.publishGlobalSound(kind, bytes, mime) { ok, message -> report(ok, message) }
                                }
                            }
                        }
                        AdminPanelCard("Chat photo backgrounds", "Images are stored in app storage and appear in every user's chat theme gallery", Icons.Outlined.Wallpaper) {
                            OutlinedTextField(newBackgroundName, { newBackgroundName = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Name (optional)") }, singleLine = true)
                            AdminSwitchRow("Premium only", "Only Convo Premium members can use this background", newBackgroundPremium, { newBackgroundPremium = it })
                            Button(onClick = { backgroundPicker.launch("image/*") }, enabled = !mediaBusy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(7.dp)); Text("Upload background image")
                            }
                            if (mediaBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (chatBackgroundImages.isEmpty()) {
                                AdminInlineInfo("No photo backgrounds yet. Portrait images around 1080 x 1920 look best.")
                            } else {
                                chatBackgroundImages.chunked(3).forEach { rowImages ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowImages.forEach { image ->
                                            Column(Modifier.weight(1f)) {
                                                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                    AsyncImage(image.url, image.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    if (image.premium) Surface(Modifier.align(Alignment.TopStart).padding(6.dp), shape = CircleShape, color = Color.Black.copy(alpha = .55f)) {
                                                        Text("PREMIUM", color = Color(0xFFFFD166), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { pendingDelete = image },
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(34.dp).background(Color.Black.copy(alpha = .5f), CircleShape)
                                                    ) { Icon(Icons.Outlined.Delete, "Delete background", tint = Color.White, modifier = Modifier.size(18.dp)) }
                                                }
                                                Text(image.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        repeat(3 - rowImages.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                        AdminPanelCard("Default app sounds", "Phones download the new sound quietly in the background. Users can still pick their own sound in Settings.", Icons.Outlined.MusicNote) {
                            listOf(AppSoundStore.NOTIFICATION to "Message notification", AppSoundStore.TYPING to "Typing indicator", AppSoundStore.LIKE to "Like / reaction pop").forEach { (kind, label) ->
                                val url = globalSoundUrls[kind].orEmpty()
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(label, fontWeight = FontWeight.Bold)
                                        Text(if (url.isBlank()) "Built-in sound" else "Custom default published", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { pendingSoundKind = kind; soundPicker.launch("audio/*") }, enabled = !mediaBusy) { Text(if (url.isBlank()) "Upload MP3" else "Replace") }
                                    if (url.isNotBlank()) TextButton(onClick = { mediaBusy = true; viewModel.clearGlobalSound(kind) { ok, message -> report(ok, message) } }, enabled = !mediaBusy) {
                                        Text("Reset", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            AdminInlineInfo("Use short MP3 files: up to 2 MB and 15 seconds. Longer files are ignored on phones.")
                        }
                        pendingDelete?.let { image ->
                            AlertDialog(
                                onDismissRequest = { pendingDelete = null },
                                title = { Text("Delete background?") },
                                text = { Text("${image.name} will be removed for everyone. Chats using it fall back to their default theme.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        pendingDelete = null
                                        mediaBusy = true
                                        viewModel.deleteChatBackground(image) { ok, message -> report(ok, message) }
                                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
                            )
                        }
                    }
                    "polls" -> {
                        AdminPanelCard("Polls & votes", "Timed polls for every user; expired polls disappear automatically", Icons.Outlined.HowToVote) {
                            AdminPollsPanel()
                        }
                    }
                    "auto_post" -> {
                        AdminPanelCard("Auto AI posts", "The server posts from your account on a schedule, even when the app is closed", Icons.Outlined.AutoAwesome) {
                            AdminAutoPostPanel()
                        }
                    }
                    "reels" -> {
                        AdminPanelCard("Reels experience", "Turn Reels on/off for everyone, or open TikTok.com inside the Reels tab", Icons.Outlined.SmartDisplay) {
                            AdminSwitchRow("Show Reels to users", "Off hides the Reels tab, the Reel option in Create and reel posts in the feed for every user (admins still see them).", reelsEnabled, { reelsEnabled = it })
                            HorizontalDivider()
                            AdminSwitchRow("TikTok browser mode", "When on, the Reels tab opens TikTok in an embedded browser; Convo's reel description, like, comment, share and action controls are not shown.", tiktokModeEnabled, { tiktokModeEnabled = it })
                            AdminInlineInfo("External-app and intent links are blocked. The browser best-effort chooses TikTok's exact ‘Not now’ option if the app-open prompt appears; TikTok can change or block WebView behavior.")
                            Button(onClick = {
                                viewModel.updateFlagshipFields(mapOf("tiktokModeEnabled" to tiktokModeEnabled, "reelsEnabled" to reelsEnabled)) { ok ->
                                    Toast.makeText(context, if (ok) "Reels experience saved" else "Could not save Reels experience", Toast.LENGTH_LONG).show()
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Save Reels experience") }
                        }
                    }
                    "storage" -> {
                        AdminPanelCard("Unified app storage", "Route all new app uploads through the configured storage provider", Icons.Outlined.CloudUpload) {
                            AdminSwitchRow("Use Supabase for all uploads", "Turn off to restore the existing mixed storage route.", storageProvider.equals("supabase", true), { storageProvider = if (it) "supabase" else "legacy" })
                            if (storageProvider.equals("supabase", true)) {
                                OutlinedTextField(storageProjectUrl, { storageProjectUrl = it.trim() }, label = { Text("Supabase API base URL") }, placeholder = { Text("https://your-project.supabase.co or https://app.example.com/w/superbase") }, supportingText = { Text("HTTPS host and optional path prefix are allowed. Do not append /storage/v1 or any API key.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(storageWorkerUrl, { storageWorkerUrl = it.trim() }, label = { Text("Storage Worker base URL") }, placeholder = { Text("https://app.example.com/w/superbase") }, supportingText = { Text("Any HTTPS host and optional prefix. Do not add /storage/upload or /admin/storage/test.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                AdminSwitchRow("Create missing buckets during test", "Requires the server-side Supabase service-role secret.", storageAutoCreate, { storageAutoCreate = it })
                                Text("Bucket IDs (unique, lowercase; 2–63 characters)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedTextField(bucketAvatars, { bucketAvatars = cleanBucketInput(it) }, label = { Text("Avatars") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(bucketChat, { bucketChat = cleanBucketInput(it) }, label = { Text("Chat / stories") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedTextField(bucketVoice, { bucketVoice = cleanBucketInput(it) }, label = { Text("Voice notes") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(bucketPosts, { bucketPosts = cleanBucketInput(it) }, label = { Text("Posts") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedTextField(bucketReels, { bucketReels = cleanBucketInput(it) }, label = { Text("Reels") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(bucketThumbnails, { bucketThumbnails = cleanBucketInput(it) }, label = { Text("Thumbnails") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(bucketUpdates, { bucketUpdates = cleanBucketInput(it) }, label = { Text("APK updates") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("Folders are virtual and appear after the first upload. Media buckets must be public; do not store private documents here.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Security: never place SUPABASE_SERVICE_ROLE_KEY in Android or Firestore. Keep it only in the Storage Worker secret.", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { saveStorageSettings(false, viewModel, context, scope, storageProvider, storageProjectUrl, storageWorkerUrl, storageAutoCreate, bucketAvatars, bucketChat, bucketVoice, bucketPosts, bucketReels, bucketThumbnails, bucketUpdates) { storageSaving = it } }, enabled = !storageSaving, modifier = Modifier.weight(1f)) {
                                    if (storageSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(6.dp)); Text("Save settings")
                                }
                                OutlinedButton(onClick = { saveStorageSettings(true, viewModel, context, scope, storageProvider, storageProjectUrl, storageWorkerUrl, storageAutoCreate, bucketAvatars, bucketChat, bucketVoice, bucketPosts, bucketReels, bucketThumbnails, bucketUpdates) { storageSaving = it } }, enabled = !storageSaving && storageProvider.equals("supabase", true), modifier = Modifier.weight(1f)) {
                                    if (storageSaving || storageHealth.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.HealthAndSafety, null)
                                    Spacer(Modifier.width(6.dp)); Text("Save + test")
                                }
                            }
                            AdminHealthBanner(storageHealth.ok, storageHealth.message)
                            if (storageHealth.projectHost.isNotBlank()) Text("Project: ${storageHealth.projectHost}", fontSize = 10.sp)
                            storageHealth.bucketChecks.forEach { Text("• $it", fontSize = 10.sp) }
                            storageHealth.instructions.forEach { Text("Next step: $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    "gateway" -> {
                        AdminPanelCard("Direct FCM gateway", "Chat/call delivery and app diagnostics use the separate secure gateway", Icons.Outlined.SendToMobile) {
                            Text("The gateway sends FCM v1 messages. Keep FIREBASE_SERVICE_ACCOUNT and all provider tokens as server-side secrets; do not put service-account JSON in the Android app or Firestore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(adminUrlInput, { adminUrlInput = it.trim() }, label = { Text("FCM Gateway HTTPS URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                viewModel.updateWebhookUrl(adminUrlInput)
                                Toast.makeText(context, "FCM gateway URL saved", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Save FCM gateway URL") }
                            OutlinedButton(onClick = { viewModel.testFcmGateway(adminUrlInput) }, enabled = !gatewayHealth.checking, modifier = Modifier.fillMaxWidth()) {
                                if (gatewayHealth.checking) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Default.HealthAndSafety, null)
                                Spacer(Modifier.width(7.dp)); Text("Test gateway")
                            }
                            Button(onClick = { viewModel.sendAdminTestNotification() }, enabled = gatewayHealth.configured, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.SendToMobile, null); Spacer(Modifier.width(7.dp)); Text("Send test notification to this device")
                            }
                            AdminHealthBanner(gatewayHealth.configured, gatewayHealth.message, gatewayHealth.version)
                            if (gatewayHealth.projectId.isNotBlank()) {
                                Text("Project: ${gatewayHealth.projectId}", fontSize = 11.sp)
                                Text("R2 ${if (gatewayHealth.r2Configured) "✓" else "✗"} • TURN ${if (gatewayHealth.turnConfigured) "✓" else "✗"} • SFU ${if (gatewayHealth.sfuConfigured) "✓" else "✗"}", fontSize = 11.sp)
                            }
                        }
                    }
                    "infrastructure" -> {
                        AdminPanelCard("Service health & tools", "Check key services and run safe diagnostics", Icons.Outlined.HealthAndSafety) {
                            AdminStatusRow("Firebase / FCM gateway", gatewayHealth.configured, "Server-side FCM service account")
                            AdminStatusRow("Presence Edge Function", presenceHealth.configured, "Supabase Edge Function secret and one five-minute Supabase Cron")
                            AdminStatusRow("Cloudflare R2", gatewayHealth.r2Configured, "Media bucket and public base URL")
                            AdminStatusRow("TURN calling", gatewayHealth.turnConfigured, "TURN token ID and API token")
                            AdminStatusRow("Realtime SFU", gatewayHealth.sfuConfigured, "Calls application ID and token")
                            AdminInlineInfo("Post and reel media retention: 10 days. Android/FCM background delivery is best-effort and can be delayed by Doze, battery settings, network loss or the device manufacturer.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.testFcmGateway(webhookUrl) }, modifier = Modifier.weight(1f)) { Text("Health check") }
                                OutlinedButton(onClick = { viewModel.loadPosts(); viewModel.loadStories(); viewModel.loadGroups() }, modifier = Modifier.weight(1f)) { Text("Refresh app data") }
                            }
                            Button(onClick = { viewModel.sendAdminTestNotification() }, enabled = gatewayHealth.configured, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.SendToMobile, null); Spacer(Modifier.width(7.dp)); Text("Send diagnostic push")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    selectedAdminUser?.let { person ->
        val now = System.currentTimeMillis()
        val onlineNow = person.isOnline && (person.onlineUntil <= 0L || person.onlineUntil > now)
        val premiumNow = person.isPremium && (person.premiumPlan.equals("lifetime", true) || person.premiumUntil > now)
        AlertDialog(
            onDismissRequest = { selectedAdminUser = null },
            icon = {
                AsyncImage(
                    model = person.profileImageUrl.ifBlank { null },
                    contentDescription = person.name,
                    error = painterResource(R.drawable.img_app_logo),
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(person.name.ifBlank { "User profile" }, fontWeight = FontWeight.Bold)
                    if (person.username.isNotBlank()) Text("@${person.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("UID: ${person.uid}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.appVersion.isNotBlank()) Text("Convo ${person.appVersion} (${person.appVersionCode}) · ${person.deviceModel} · ${person.androidVersion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Status: ${if (onlineNow) "Online" else "Offline"} • ${if (premiumNow) "Premium" else "Standard"}", fontWeight = FontWeight.SemiBold)
                    if (person.bio.isNotBlank()) Text(person.bio, style = MaterialTheme.typography.bodySmall)
                    Text("Role: ${person.role.ifBlank { "user" }}", fontSize = 11.sp)
                    Text("Last active: ${formatAdminTimestamp(person.lastActive)}", fontSize = 11.sp)
                    Text("Joined: ${formatAdminTimestamp(person.createdAt)}", fontSize = 11.sp)
                    if (premiumNow && !person.premiumPlan.equals("lifetime", true)) {
                        Text("Premium until: ${formatAdminTimestamp(person.premiumUntil)}", fontSize = 11.sp)
                    }
                    HorizontalDivider()
                    AdminUserPowerActions(person, permissions = staffPermissions)
                    OutlinedButton(
                        onClick = {
                            val uid = person.uid
                            selectedAdminUser = null
                            selectedPage = "presence"
                            presenceQuery = uid
                            viewModel.loadPresenceUsers(uid)
                        },
                        enabled = presenceHealth.configured && person.uid.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(7.dp)); Text("Check presence")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyAdminUserUid(context, person.uid)
                    selectedAdminUser = null
                }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Copy UID") }
            },
            dismissButton = { TextButton(onClick = { selectedAdminUser = null }) { Text("Close") } }
        )
    }
}

@Composable
fun AdminAccessDeniedScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(54.dp))
                Text("Admin access only", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("The admin dashboard is restricted to the authorized administrator account.", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun AdminUserManagementRow(
    user: User,
    now: Long,
    onOpenProfile: () -> Unit,
    onCopyUid: () -> Unit
) {
    val onlineNow = user.isOnline && (user.onlineUntil <= 0L || user.onlineUntil > now)
    val premiumNow = user.isPremium && (user.premiumPlan.equals("lifetime", true) || user.premiumUntil > now)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfile),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = user.profileImageUrl.ifBlank { null },
                    contentDescription = user.name,
                    error = painterResource(R.drawable.img_app_logo),
                    modifier = Modifier.size(46.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name.ifBlank { "Unnamed user" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            if (user.username.isNotBlank()) append("@${user.username} · ")
                            append("last active ${formatAdminTimestamp(user.lastActive)}")
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCopyUid) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy user UID") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppVersionChip(user)
                Surface(shape = CircleShape, color = if (onlineNow) Color(0xFF2CBF79).copy(alpha = .15f) else MaterialTheme.colorScheme.outline.copy(alpha = .12f)) {
                    Text(
                        if (onlineNow) "ONLINE" else "OFFLINE",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (onlineNow) Color(0xFF218E5C) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (premiumNow) {
                    Surface(shape = CircleShape, color = Color(0xFFFFC857).copy(alpha = .2f)) {
                        Text("PREMIUM", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9B6711))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenProfile) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(4.dp)); Text("Quick profile") }
            }
        }
    }
}

private fun copyAdminUserUid(context: Context, uid: String) {
    if (uid.isBlank()) {
        Toast.makeText(context, "User UID is missing", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "Clipboard is unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("Convo Chat user UID", uid))
    Toast.makeText(context, "User UID copied", Toast.LENGTH_SHORT).show()
}

private fun formatAdminTimestamp(timestamp: Long): String =
    if (timestamp > 0L) SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp)) else "Not available"

@Composable
private fun AdminMenuTile(entry: AdminMenuEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 132.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = entry.accent.copy(alpha = .12f)) {
                    Icon(entry.icon, null, Modifier.padding(9.dp), tint = entry.accent)
                }
                Spacer(Modifier.weight(1f))
                if (entry.badge.isNotBlank()) {
                    Surface(shape = CircleShape, color = entry.accent.copy(alpha = .13f)) {
                        Text(entry.badge, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = entry.accent)
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f), modifier = Modifier.size(18.dp))
            }
            Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.description, fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdminPageIntro(entry: AdminMenuEntry) {
    Surface(shape = RoundedCornerShape(20.dp), color = entry.accent.copy(alpha = .09f)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(entry.icon, null, tint = entry.accent, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminPanelCard(title: String, subtitle: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f)) {
                    Icon(icon, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun AdminSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AdminInlineInfo(text: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardPill(text: String, icon: ImageVector) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = .14f)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DashboardStatCard(title: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .12f)) { Icon(icon, null, Modifier.padding(8.dp), tint = accent) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminHealthBanner(ready: Boolean, message: String, detail: String = "") {
    Surface(
        color = if (ready) Color(0xFF1F7A4D).copy(alpha = .14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if (ready) Color(0xFF23864D) else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(message, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (detail.isNotBlank()) Text(detail, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PresenceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)).padding(11.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdminStatusRow(label: String, ready: Boolean, requirement: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ready) Color(0xFF2CBF79) else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(requirement, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (ready) "READY" else "CHECK", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = if (ready) Color(0xFF23864D) else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun AdminReportRow(report: UserReport, context: Context, viewModel: ChatViewModel) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(report.category, fontWeight = FontWeight.Bold)
            Text(report.description, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("Reporter: ${report.reporterEmail}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.targetUid.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.setUserBanned(report.targetUid, true, report.description) { _, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }) { Text("Ban target") }
                    OutlinedButton(onClick = { viewModel.setUserBanned(report.targetUid, false) { _, message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }) { Text("Unban") }
                }
            }
        }
    }
}

private fun cleanBucketInput(value: String): String = value.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(63)

private fun saveStorageSettings(
    testAfterSave: Boolean,
    viewModel: ChatViewModel,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    provider: String,
    projectUrl: String,
    workerUrl: String,
    autoCreate: Boolean,
    avatars: String,
    chat: String,
    voice: String,
    posts: String,
    reels: String,
    thumbnails: String,
    updates: String,
    onSavingChanged: (Boolean) -> Unit
) {
    onSavingChanged(true)
    viewModel.saveStorageRoutingConfig(
        provider = provider,
        supabaseUrl = projectUrl,
        storageWorkerUrl = workerUrl,
        autoCreateBuckets = autoCreate,
        bucketAvatars = avatars,
        bucketChatImages = chat,
        bucketVoiceNotes = voice,
        bucketPosts = posts,
        bucketReels = reels,
        bucketThumbnails = thumbnails,
        bucketUpdates = updates
    ) { saved, message ->
        if (!saved) {
            onSavingChanged(false)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } else if (!testAfterSave) {
            onSavingChanged(false)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.testStorageRoutingConfig(workerUrl) { testOk, resultMessage ->
                scope.launch(Dispatchers.Main) {
                    onSavingChanged(false)
                    Toast.makeText(context, resultMessage, if (testOk) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}


/** Small "v4.3.4" chip; green when the user runs the same build as this admin app, amber when older. */
@Composable
fun AppVersionChip(user: User) {
    val current = com.example.BuildConfig.VERSION_CODE.toLong()
    val (label, color) = when {
        user.appVersion.isBlank() -> "v?" to MaterialTheme.colorScheme.outline
        user.appVersionCode >= current -> "v${user.appVersion}" to Color(0xFF2E7D32)
        else -> "v${user.appVersion} · old" to Color(0xFFEF6C00)
    }
    Surface(shape = CircleShape, color = color.copy(alpha = .14f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
