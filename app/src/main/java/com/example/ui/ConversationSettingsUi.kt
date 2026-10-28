package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.Message
import com.example.data.User
import com.example.location.LocationShare
import com.example.security.PrivacyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Per-conversation, device-local preferences (mute, nickname, pin). Nothing is uploaded. */
object ConversationPrefs {
    private const val FILE = "convo_conversation_prefs"
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mutedUntil(ctx: Context, peerUid: String): Long = p(ctx).getLong("mute_$peerUid", 0L)
    fun isMuted(ctx: Context, peerUid: String): Boolean {
        val until = mutedUntil(ctx, peerUid)
        return until == Long.MAX_VALUE || until > System.currentTimeMillis()
    }
    fun setMuted(ctx: Context, peerUid: String, until: Long) { p(ctx).edit().putLong("mute_$peerUid", until).apply(); _version.value++ }

    fun nickname(ctx: Context, peerUid: String): String = p(ctx).getString("nick_$peerUid", "").orEmpty()
    fun setNickname(ctx: Context, peerUid: String, value: String) { p(ctx).edit().putString("nick_$peerUid", value.trim().take(40)).apply(); _version.value++ }

    fun isPinned(ctx: Context, peerUid: String): Boolean = p(ctx).getBoolean("pin_$peerUid", false)
    fun setPinned(ctx: Context, peerUid: String, value: Boolean) { p(ctx).edit().putBoolean("pin_$peerUid", value).apply(); _version.value++ }

    fun readReceiptsHidden(ctx: Context, peerUid: String): Boolean = p(ctx).getBoolean("noreceipt_$peerUid", false)
    fun setReadReceiptsHidden(ctx: Context, peerUid: String, value: Boolean) { p(ctx).edit().putBoolean("noreceipt_$peerUid", value).apply(); _version.value++ }

    /** Archived chats leave the main list (they come back with the Archived filter). */
    fun isArchived(ctx: Context, peerUid: String): Boolean = p(ctx).getBoolean("archive_$peerUid", false)
    fun setArchived(ctx: Context, peerUid: String, value: Boolean) { p(ctx).edit().putBoolean("archive_$peerUid", value).apply(); _version.value++ }

    /** Locked chats need fingerprint / screen lock / app PIN before they open. */
    fun isLocked(ctx: Context, peerUid: String): Boolean = peerUid.isNotBlank() && p(ctx).getBoolean("lock_$peerUid", false)
    fun setLocked(ctx: Context, peerUid: String, value: Boolean) { p(ctx).edit().putBoolean("lock_$peerUid", value).apply(); _version.value++ }
}

private val MUTE_OPTIONS = listOf(
    0L to "Off", 3_600_000L to "1 hour", 8 * 3_600_000L to "8 hours", 24 * 3_600_000L to "24 hours", 7 * 24 * 3_600_000L to "1 week", Long.MAX_VALUE to "Always"
)
private val DISAPPEAR_OPTIONS = listOf(0 to "Off", 3600 to "1 hour", 86_400 to "24 hours", 7 * 86_400 to "7 days", 30 * 86_400 to "30 days")

/** Full-page conversation settings: header with actions, personalise, notifications, media, insights, safety. */
@Composable
fun ConversationSettingsPage(
    recipient: User,
    messages: List<Message>,
    myUid: String,
    chatTheme: String,
    headerBrush: Brush,
    isBlocked: Boolean,
    typingSounds: Boolean,
    onTypingSounds: (Boolean) -> Unit,
    onClose: () -> Unit,
    onProfile: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onSearch: () -> Unit,
    onTheme: () -> Unit,
    onBlockToggle: () -> Unit,
    onReport: () -> Unit,
    onOpenImage: (String) -> Unit,
    onChatLockToggle: (Boolean) -> Unit = {},
    autoSaveGloballyEnabled: Boolean = true
) {
    val context = LocalContext.current
    val prefsVersion by ConversationPrefs.version.collectAsState()
    val quickReaction by ChatPreferences.quickReaction.collectAsState()
    val textScale by ChatPreferences.textScale.collectAsState()
    val enterToSend by ChatPreferences.enterToSend.collectAsState()
    val privacy by PrivacyPreferences.settings.collectAsState()
    val muted = remember(prefsVersion) { ConversationPrefs.isMuted(context, recipient.uid) }
    val mutedUntil = remember(prefsVersion) { ConversationPrefs.mutedUntil(context, recipient.uid) }
    val pinned = remember(prefsVersion) { ConversationPrefs.isPinned(context, recipient.uid) }
    val archived = remember(prefsVersion) { ConversationPrefs.isArchived(context, recipient.uid) }
    val chatLocked = remember(prefsVersion) { ConversationPrefs.isLocked(context, recipient.uid) }
    val nickname = remember(prefsVersion) { ConversationPrefs.nickname(context, recipient.uid) }
    var dialog by remember { mutableStateOf("") }
    var mediaTab by remember { mutableIntStateOf(0) }
    var pdfOpen by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }
    var autoSave by remember { mutableStateOf(ReceivedFiles.autoDownloadEnabled(context)) }
    var wifiOnly by remember { mutableStateOf(ReceivedFiles.wifiOnly(context)) }
    var allowedDownloadTypes by remember { mutableStateOf(listOf("Images", "Videos", "Audio", "PDF", "Documents", "Archives", "Apps", "Files").associateWith { ReceivedFiles.typeAllowed(context, it) }) }

    val photos = remember(messages) { messages.mapNotNull { it.imageUrl?.takeIf { u -> u.isNotBlank() } }.reversed() }
    val files = remember(messages) { messages.filter { !it.fileUrl.isNullOrBlank() }.reversed() }
    val links = remember(messages) {
        val rx = Regex("""https?://\S+""")
        messages.filter { LocationShare.parse(it.text) == null }.flatMap { m -> rx.findAll(m.text).map { it.value }.toList() }.distinct().reversed()
    }
    val places = remember(messages) { messages.mapNotNull { m -> LocationShare.parse(m.text)?.let { m to it } }.reversed() }
    val mine = remember(messages, myUid) { messages.count { it.senderId == myUid } }
    val voiceCount = remember(messages) { messages.count { !it.voiceUrl.isNullOrBlank() } }
    val firstAt = remember(messages) { messages.minOfOrNull { it.timestamp }?.takeIf { it > 0 } }
    val topReaction = remember(messages) { messages.flatMap { it.reactions.values }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().navigationBarsPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // ---------- Header ----------
                Box(Modifier.fillMaxWidth().height(250.dp).background(headerBrush)) {
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Transparent, MaterialTheme.colorScheme.background))))
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                        Text("Conversation settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    }
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(92.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                            if (recipient.profileImageUrl.isNotBlank()) AsyncImage(recipient.profileImageUrl, recipient.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(recipient.name.take(1).uppercase(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(nickname.ifBlank { recipient.name }, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            (if (nickname.isNotBlank()) "${recipient.name} · " else "") + if (recipient.isOnline) "Active now" else "@${recipient.username}",
                            fontSize = 12.sp, color = if (recipient.isOnline) Color(0xFF2E9B60) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HeaderAction(Icons.Default.Person, "Profile", Color(0xFF7C4DFF), onProfile)
                    HeaderAction(Icons.Default.Call, "Audio", Color(0xFF11998E), onAudioCall)
                    HeaderAction(Icons.Default.Videocam, "Video", Color(0xFF2F80ED), onVideoCall)
                    HeaderAction(Icons.Default.Search, "Search", Color(0xFFFF8F00), onSearch)
                    HeaderAction(if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications, if (muted) "Unmute" else "Mute", Color(0xFFE53935)) {
                        if (muted) ConversationPrefs.setMuted(context, recipient.uid, 0L) else dialog = "mute"
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ---------- Personalise ----------
                    SettingsGroup("Personalise") {
                        SettingRow(Icons.Default.Palette, Color(0xFF8E24AA), "Theme & background", chatTheme.removePrefix("image:").take(40), onClick = onTheme)
                        SettingRow(Icons.Default.EmojiEmotions, Color(0xFFFFB300), "Quick reaction", "Tap the reaction button to send it", trailingText = quickReaction, onClick = { dialog = "reaction" })
                        SettingRow(Icons.Default.Badge, Color(0xFF00897B), "Nickname", nickname.ifBlank { "Only you will see it" }, onClick = { dialog = "nickname" })
                        SettingRow(Icons.Default.TextFields, Color(0xFF3949AB), "Message text size", "${(textScale * 100).toInt()}%", onClick = { dialog = "text" })
                        SettingRow(Icons.Default.PushPin, Color(0xFFD81B60), "Pin conversation", "Keep it at the top of your chats", switch = pinned, onSwitch = { ConversationPrefs.setPinned(context, recipient.uid, it) })
                        SettingRow(Icons.Default.Archive, Color(0xFF546E7A), "Archive conversation", "Move it out of All; find it under Archived", switch = archived, onSwitch = { ConversationPrefs.setArchived(context, recipient.uid, it) })
                        SettingRow(Icons.Default.Lock, Color(0xFF3949AB), "Lock this chat", "Requires your fingerprint, face or screen lock", switch = chatLocked, onSwitch = onChatLockToggle)
                    }
                    // ---------- Notifications & input ----------
                    SettingsGroup("Notifications & typing") {
                        SettingRow(Icons.Default.NotificationsOff, Color(0xFFE53935), "Mute messages",
                            when { !muted -> "Off"; mutedUntil == Long.MAX_VALUE -> "Muted until you turn it back on"; else -> "Muted until " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(mutedUntil)) },
                            onClick = { dialog = "mute" })
                        SettingRow(Icons.Default.Keyboard, Color(0xFF1E88E5), "Typing sounds", "Soft sound while you type", switch = typingSounds, onSwitch = onTypingSounds)
                        SettingRow(Icons.Default.KeyboardReturn, Color(0xFF43A047), "Enter to send", "Enter key sends instead of new line", switch = enterToSend, onSwitch = { ChatPreferences.setEnterToSend(context, it) })
                        SettingRow(
                            Icons.Default.Download, Color(0xFF00897B), "Auto-save received media",
                            if (autoSaveGloballyEnabled) "Saved to Downloads/${ReceivedFiles.APP_FOLDER} by type (all chats)" else "Automatic downloads are disabled by the administrator",
                            switch = autoSave && autoSaveGloballyEnabled,
                            enabled = autoSaveGloballyEnabled,
                            onSwitch = { autoSave = it; ReceivedFiles.setAutoDownload(context, it) }
                        )
                        if (autoSave && autoSaveGloballyEnabled) {
                            SettingRow(Icons.Default.Wifi, Color(0xFF1976D2), "Wi-Fi only", "Pause automatic downloads on mobile data", switch = wifiOnly, onSwitch = { wifiOnly = it; ReceivedFiles.setWifiOnly(context, it) })
                            listOf("Images", "Videos", "Audio", "PDF", "Documents", "Archives", "Apps", "Files").forEach { kind ->
                                val enabled = allowedDownloadTypes[kind] ?: true
                                SettingRow(Icons.Default.Folder, Color(0xFF00897B), "Auto-save $kind", "Include this type in automatic downloads", switch = enabled, onSwitch = { allowedDownloadTypes = allowedDownloadTypes + (kind to it); ReceivedFiles.setTypeAllowed(context, kind, it) })
                            }
                        }
                    }
                    // ---------- Privacy ----------
                    SettingsGroup("Privacy") {
                        SettingRow(Icons.Default.Timer, Color(0xFF6D4C41), "Disappearing messages",
                            DISAPPEAR_OPTIONS.firstOrNull { it.first == privacy.disappearingMessageSeconds }?.second ?: "${privacy.disappearingMessageSeconds / 3600} h",
                            onClick = { dialog = "disappear" })
                        SettingRow(Icons.Default.Lock, Color(0xFF546E7A), "Screenshot protection", "Chat and call screens are protected", trailingText = "On")
                    }
                    // ---------- Media / files / links / places ----------
                    SettingsGroup("Media, files & links") {
                        ScrollableTabRow(selectedTabIndex = mediaTab, edgePadding = 8.dp, containerColor = Color.Transparent) {
                            listOf("Photos ${photos.size}", "Files ${files.size}", "Links ${links.size}", "Places ${places.size}").forEachIndexed { i, t ->
                                Tab(selected = mediaTab == i, onClick = { mediaTab = i }, text = { Text(t, fontSize = 12.sp) })
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(10.dp)) {
                            when (mediaTab) {
                                0 -> if (photos.isEmpty()) EmptyMedia("No photos yet") else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    photos.take(24).chunked(3).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row.forEach { url ->
                                                AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onOpenImage(url) })
                                            }
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                                1 -> if (files.isEmpty()) EmptyMedia("No files yet") else Column {
                                    files.take(40).forEach { m ->
                                        val name = m.fileName ?: "File"
                                        val mime = m.fileMimeType ?: "application/octet-stream"
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        val id = ReceivedFiles.downloadIdFor(context, m.messageId)
                                        val saved = id > 0 && ReceivedFiles.statusOf(context, id) == android.app.DownloadManager.STATUS_SUCCESSFUL
                                        MediaLine(
                                            if (mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Description, name,
                                            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(m.timestamp)) + if (saved) " · saved in Downloads/${ReceivedFiles.APP_FOLDER}/${ReceivedFiles.folderFor(mime, name)}" else " · tap to download"
                                        ) {
                                            val local = if (saved) runCatching { dm.getUriForDownloadedFile(id) }.getOrNull() else null
                                            when {
                                                local != null && mime == "application/pdf" -> pdfOpen = local to name
                                                local != null -> ReceivedFiles.openExternally(context, local, mime)
                                                else -> {
                                                    val src = m.remoteFileUrl ?: m.fileUrl
                                                    if (src != null && src.startsWith("http") && ReceivedFiles.enqueue(context, m.messageId, src, name, mime) > 0)
                                                        android.widget.Toast.makeText(context, "Saving to Downloads/${ReceivedFiles.APP_FOLDER}", android.widget.Toast.LENGTH_SHORT).show()
                                                    else android.widget.Toast.makeText(context, "This file is no longer available on the server", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> if (links.isEmpty()) EmptyMedia("No links yet") else Column {
                                    links.take(30).forEach { url ->
                                        MediaLine(Icons.Default.Link, url.removePrefix("https://").removePrefix("http://").take(48), "Tap to open") {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                                        }
                                    }
                                }
                                else -> if (places.isEmpty()) EmptyMedia("No shared locations yet") else Column {
                                    places.take(20).forEach { (m, loc) ->
                                        MediaLine(Icons.Default.Place, if (loc.isLive) "Live location" else "Location · ${loc.coords}", SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(m.timestamp))) {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(loc.mapsUrl))) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ---------- Insights ----------
                    SettingsGroup("Chat insights") {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightTile("${messages.size}", "Messages", Color(0xFF7C4DFF), Modifier.weight(1f))
                            InsightTile("$mine", "From you", Color(0xFF00897B), Modifier.weight(1f))
                            InsightTile("${messages.size - mine}", "From them", Color(0xFF1E88E5), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightTile("$voiceCount", "Voice notes", Color(0xFFFF6D00), Modifier.weight(1f))
                            InsightTile("${photos.size}", "Photos", Color(0xFFD81B60), Modifier.weight(1f))
                            InsightTile(topReaction ?: "–", "Top reaction", Color(0xFFFFB300), Modifier.weight(1f))
                        }
                        firstAt?.let { Text("Chatting since " + SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(it)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, bottom = 12.dp)) }
                        SettingRow(Icons.Default.IosShare, Color(0xFF00ACC1), "Export chat", "Share the loaded messages as text", onClick = {
                            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val body = messages.sortedBy { it.timestamp }.joinToString("\n") { m ->
                                val who = if (m.senderId == myUid) "You" else recipient.name
                                val content = when {
                                    !m.imageUrl.isNullOrBlank() -> "[Photo] ${m.imageUrl}"
                                    !m.voiceUrl.isNullOrBlank() -> "[Voice message]"
                                    !m.fileUrl.isNullOrBlank() -> "[File] ${m.fileName.orEmpty()}"
                                    else -> m.text
                                }
                                "[${fmt.format(Date(m.timestamp))}] $who: $content"
                            }
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Convo chat with ${recipient.name}"); putExtra(Intent.EXTRA_TEXT, body) }
                            runCatching { context.startActivity(Intent.createChooser(send, "Export chat")) }
                        })
                    }
                    // ---------- Safety ----------
                    SettingsGroup("Safety") {
                        SettingRow(Icons.Default.Flag, Color(0xFFF4511E), "Report", "Tell the admin team about a problem", onClick = onReport)
                        SettingRow(if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block, Color(0xFFC62828), if (isBlocked) "Unblock ${recipient.name}" else "Block ${recipient.name}",
                            if (isBlocked) "Allow messages and calls again" else "Stop messages, calls and interactions", danger = true, onClick = onBlockToggle)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    pdfOpen?.let { (uri, name) -> PdfViewerDialog(uri, name) { pdfOpen = null } }

    when (dialog) {
        "mute" -> OptionDialog("Mute messages", MUTE_OPTIONS.map { it.second }, MUTE_OPTIONS.indexOfFirst { if (!muted) it.first == 0L else it.first == Long.MAX_VALUE && mutedUntil == Long.MAX_VALUE }, { dialog = "" }) { i ->
            val d = MUTE_OPTIONS[i].first
            ConversationPrefs.setMuted(context, recipient.uid, when (d) { 0L -> 0L; Long.MAX_VALUE -> Long.MAX_VALUE; else -> System.currentTimeMillis() + d })
            dialog = ""
        }
        "disappear" -> OptionDialog("Disappearing messages (all chats)", DISAPPEAR_OPTIONS.map { it.second }, DISAPPEAR_OPTIONS.indexOfFirst { it.first == privacy.disappearingMessageSeconds }, { dialog = "" }) { i ->
            PrivacyPreferences.update(context) { it.copy(disappearingMessageSeconds = DISAPPEAR_OPTIONS[i].first) }
            dialog = ""
        }
        "text" -> {
            val scales = listOf(.85f, 1f, 1.15f, 1.3f, 1.5f)
            OptionDialog("Message text size", scales.map { "${(it * 100).toInt()}%" }, scales.indexOfFirst { kotlin.math.abs(it - textScale) < .01f }, { dialog = "" }) { i ->
                ChatPreferences.setTextScale(context, scales[i]); dialog = ""
            }
        }
        "reaction" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(tr("Quick reaction")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatPreferences.reactionChoices.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { e ->
                                Box(
                                    Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                                        .background(if (e == quickReaction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                                        .clickable { ChatPreferences.setQuickReaction(context, e); dialog = "" },
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 26.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = "" }) { Text(tr("Close")) } }
        )
        "nickname" -> {
            var value by remember { mutableStateOf(nickname) }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Nickname") },
                text = { OutlinedTextField(value, { value = it.take(40) }, singleLine = true, label = { Text("Only visible to you") }) },
                confirmButton = { Button(onClick = { ConversationPrefs.setNickname(context, recipient.uid, value); dialog = "" }) { Text(tr("Save")) } },
                dismissButton = { TextButton(onClick = { ConversationPrefs.setNickname(context, recipient.uid, ""); dialog = "" }) { Text(tr("Clear")) } }
            )
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color, color.copy(alpha = .65f)))), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector, color: Color, title: String, subtitle: String,
    trailingText: String? = null, switch: Boolean? = null, onSwitch: (Boolean) -> Unit = {},
    danger: Boolean = false, onClick: (() -> Unit)? = null, enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (!enabled) Modifier else if (onClick != null) Modifier.clickable(onClick = onClick) else if (switch != null) Modifier.clickable { onSwitch(!switch) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(color, color.copy(alpha = .7f)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (danger) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailingText != null) Text(trailingText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp))
        if (switch != null) Switch(checked = switch, onCheckedChange = onSwitch, enabled = enabled)
        else if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .14f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color, maxLines = 1)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun MediaLine(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyMedia(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
}

@Composable
private fun OptionDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == selected, onClick = { onPick(i) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } }
    )
}
