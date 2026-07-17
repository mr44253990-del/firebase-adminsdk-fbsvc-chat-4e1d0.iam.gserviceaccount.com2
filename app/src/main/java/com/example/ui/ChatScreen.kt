package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.Group
import com.example.data.Message
import com.example.data.PresenceManager
import com.example.data.User
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** Chat — glass bubbles, swipe-to-reply, voice notes, reactions, edit/forward/delete. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    recipient: User?,
    group: Group?,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val messages by viewModel.chatMessagesState.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()
    val partnerTyping by viewModel.partnerTyping.collectAsState()
    val presenceMap by viewModel.presenceMap.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val blockedList = currentUser?.blockedUsers ?: emptyList()

    var inputText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var editTarget by remember { mutableStateOf<Message?>(null) }
    var forwardTarget by remember { mutableStateOf<Message?>(null) }
    var headerMenu by remember { mutableStateOf(false) }
    var showAddMembers by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isGroup = group != null
    val myUid = currentUser?.uid ?: ""

    val presence = recipient?.let { presenceMap[it.uid] }
    val isBlocked = recipient?.uid in blockedList

    // ---- voice recording ----
    var isRecording by remember { mutableStateOf(false) }
    var recordStart by remember { mutableStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }

    fun stopRecording(send: Boolean) {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
        val file = voiceFile
        if (send && file != null && file.exists()) {
            viewModel.sendVoiceMessage(recipient, group, file, System.currentTimeMillis() - recordStart)
        } else {
            file?.delete()
        }
        voiceFile = null
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            voiceFile = file
            recordStart = System.currentTimeMillis()
            isRecording = true
        } catch (_: Exception) {}
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.sendImageMessage(recipient, group, uri, replyTo).also { replyTo = null }
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && group != null) viewModel.updateGroupBackground(group.groupId, uri)
    }

    LaunchedEffect(recipient?.uid, group?.groupId) {
        if (group != null) viewModel.startListeningToGroupChat(group.groupId)
        else recipient?.let { viewModel.startListeningToChat(it.uid) }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopListeningToChat() } }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        if (!isGroup) recipient?.let { viewModel.markChatRead(viewModel.chatIdFor(it.uid)) }
        else group?.let { viewModel.markChatRead(it.groupId) }
    }

    // typing debounce
    LaunchedEffect(inputText) {
        if (!isGroup) {
            viewModel.setTyping(inputText.isNotBlank())
            if (inputText.isNotBlank()) { delay(2500); viewModel.setTyping(false) }
        }
    }

    GlassBackground(bubbleCount = 6) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---------- Header ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                val title = group?.name ?: recipient?.name ?: ""
                val photo = group?.photoUrl ?: recipient?.photoUrl ?: ""
                UserAvatar(
                    name = title, photoUrl = photo, size = 42.dp,
                    online = if (isGroup) null else presence?.online == true,
                    onClick = { if (!isGroup) recipient?.let { onOpenProfile(it.uid) } }
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { if (!isGroup) recipient?.let { onOpenProfile(it.uid) } }) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            isGroup -> "${group!!.memberIds.size} members"
                            partnerTyping -> "typing…"
                            else -> presence?.let { PresenceManager.lastSeenLabel(it) } ?: ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (partnerTyping || presence?.online == true) OnlineGreen else TextSecondary
                    )
                }
                Box {
                    IconButton(onClick = { headerMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                    }
                    DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }, containerColor = Color(0xFF1B1B32)) {
                        if (isGroup) {
                            DropdownMenuItem(
                                text = { Text("Add members", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, null, tint = Color.White) },
                                onClick = { headerMenu = false; showAddMembers = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Change background", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, null, tint = Color.White) },
                                onClick = {
                                    headerMenu = false
                                    bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("View profile", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = Color.White) },
                                onClick = { headerMenu = false; recipient?.let { onOpenProfile(it.uid) } }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isBlocked) "Unblock user" else "Block user", color = UnreadRed) },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = UnreadRed) },
                                onClick = {
                                    headerMenu = false
                                    recipient?.let {
                                        if (isBlocked) viewModel.unblockUser(it.uid) else { viewModel.blockUser(it.uid); onBack() }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ---------- Messages ----------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // group background image
                if (isGroup && !group!!.backgroundUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.backgroundUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f },
                        contentScale = ContentScale.Crop
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.messageId }) { msg ->
                        SwipeableMessageRow(
                            message = msg,
                            isMine = msg.senderId == myUid,
                            showSender = isGroup,
                            onSwipeReply = { replyTo = msg },
                            onLongPress = { actionMessage = msg }
                        )
                    }
                    if (partnerTyping && !isGroup) {
                        item(key = "typing") {
                            Row(Modifier.padding(6.dp)) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(GlassWhite)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) { PulsingDots(color = TextSecondary) }
                            }
                        }
                    }
                }
            }

            // ---------- Reply preview ----------
            AnimatedVisibility(visible = replyTo != null) {
                replyTo?.let { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassWhite)
                            .border(1.dp, GlassBorderSoft, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(50)).background(LocalPalette.current.primary))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Reply to ${r.senderName}", style = MaterialTheme.typography.labelLarge, color = LocalPalette.current.primary)
                            Text(
                                if (r.type == "text") r.text else "📎 ${r.type}",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyTo = null }) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ---------- Input bar ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassWhite)
                        .clickable {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploading) PulsingDots(color = LocalPalette.current.primary)
                    else Icon(Icons.Default.Image, contentDescription = "Send image", tint = LocalPalette.current.secondary)
                }
                Spacer(Modifier.width(8.dp))

                if (isRecording) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33FF3B5C))
                            .border(1.dp, UnreadRed.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UnreadDot(size = 9.dp)
                        Spacer(Modifier.width(10.dp))
                        RecordingTimer(recordStart)
                        Spacer(Modifier.weight(1f))
                        Text("Release to send", color = Color(0xFFFFB3C0), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(UnreadRed).clickable { stopRecording(true) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(GlassWhite).clickable { stopRecording(false) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = UnreadRed, modifier = Modifier.size(20.dp)) }
                } else {
                    GlassField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = if (isBlocked) "You blocked this user" else "Message…",
                        modifier = Modifier.weight(1f),
                        singleLine = false
                    )
                    Spacer(Modifier.width(8.dp))
                    if (inputText.isBlank()) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GlassWhite)
                                .clickable(enabled = !isBlocked) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) startRecording()
                                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice message", tint = LocalPalette.current.primary)
                        }
                    } else {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(LocalPalette.current.bubbleMine))
                                .clickable(enabled = !isBlocked) {
                                    viewModel.sendMessage(recipient, group, inputText.trim(), replyTo = replyTo)
                                    inputText = ""
                                    replyTo = null
                                    scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // ---------- Action sheet ----------
    actionMessage?.let { msg ->
        MessageActionSheet(
            message = msg,
            isMine = msg.senderId == myUid,
            onDismiss = { actionMessage = null },
            onReply = { replyTo = msg },
            onReact = { emoji -> viewModel.reactToMessage(msg, emoji) },
            onEdit = { editTarget = msg },
            onForward = { forwardTarget = msg },
            onDelete = { viewModel.deleteMessage(msg) }
        )
    }

    // ---------- Edit dialog ----------
    editTarget?.let { msg ->
        var newText by remember { mutableStateOf(msg.text) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            containerColor = Color(0xFF151528),
            title = { Text("Edit message", color = Color.White) },
            text = { GlassField(value = newText, onValueChange = { newText = it }, label = "Message") },
            confirmButton = {
                TextButton(onClick = { viewModel.editMessage(msg, newText.trim()); editTarget = null }) {
                    Text("Save", color = LocalPalette.current.primary)
                }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // ---------- Forward picker ----------
    forwardTarget?.let { msg ->
        ForwardDialog(
            viewModel = viewModel,
            onDismiss = { forwardTarget = null },
            onPick = { user ->
                viewModel.forwardMessage(msg, user)
                forwardTarget = null
            }
        )
    }

    // ---------- Add group members ----------
    if (showAddMembers && group != null) {
        AddMembersDialog(
            viewModel = viewModel,
            group = group,
            onDismiss = { showAddMembers = false }
        )
    }
}

@Composable
private fun RecordingTimer(start: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(200) } }
    val secs = ((now - start) / 1000).coerceAtLeast(0)
    Text(
        "%d:%02d".format(secs / 60, secs % 60),
        color = Color.White, style = MaterialTheme.typography.labelLarge
    )
}

/** Swipe right on any bubble -> reply comes flying in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableMessageRow(
    message: Message,
    isMine: Boolean,
    showSender: Boolean,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(message.messageId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX.value > 140f) onSwipeReply()
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
                ) { change, dragAmount ->
                    change.consume()
                    val newX = (offsetX.value + dragAmount).coerceIn(0f, 220f)
                    scope.launch { offsetX.snapTo(newX) }
                }
            }
    ) {
        // reply hint icon revealed behind
        if (offsetX.value > 10f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = LocalPalette.current.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .graphicsLayer { alpha = (offsetX.value / 140f).coerceIn(0f, 1f) }
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            MessageBubble(
                message = message,
                isMine = isMine,
                showSender = showSender,
                onLongPress = onLongPress
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    showSender: Boolean,
    onLongPress: () -> Unit
) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.padding(
            start = if (isMine) 60.dp else 0.dp,
            end = if (isMine) 0.dp else 60.dp
        )
    ) {
        if (showSender && !isMine) {
            Text(
                message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        Column(
            Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp, topEnd = 22.dp,
                        bottomStart = if (isMine) 22.dp else 6.dp,
                        bottomEnd = if (isMine) 6.dp else 22.dp
                    )
                )
                .background(
                    if (isMine) Brush.linearGradient(palette.bubbleMine)
                    else Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.08f))
                    )
                )
                .border(
                    1.dp,
                    if (isMine) Color.White.copy(alpha = 0.25f) else GlassBorderSoft,
                    RoundedCornerShape(
                        topStart = 22.dp, topEnd = 22.dp,
                        bottomStart = if (isMine) 22.dp else 6.dp,
                        bottomEnd = if (isMine) 6.dp else 22.dp
                    )
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            if (message.forwarded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Forwarded", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), fontStyle = FontStyle.Italic)
                }
            }
            if (message.replyToText.isNotBlank()) {
                Column(
                    Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(message.replyToSender, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                    Text(message.replyToText, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when (message.type) {
                "image" -> {
                    AsyncImage(
                        model = message.mediaUrl, contentDescription = "Photo",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                "voice" -> VoiceMessageContent(message, isMine)
                else -> Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (message.edited) {
                    Text("edited · ", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
                Text(
                    formatClock(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
        // reactions
        if (message.reactions.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1B1B32))
                    .border(1.dp, GlassBorderSoft, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                message.reactions.values.distinct().forEach { emoji ->
                    Text(emoji, style = MaterialTheme.typography.bodySmall)
                }
                if (message.reactions.size > 1) {
                    Text(" ${message.reactions.size}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun VoiceMessageContent(message: Message, isMine: Boolean) {
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(message.messageId) {
        onDispose {
            try { player?.release() } catch (_: Exception) {}
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable {
                    if (playing) {
                        try { player?.pause() } catch (_: Exception) {}
                        playing = false
                    } else {
                        try {
                            if (player == null) {
                                player = MediaPlayer().apply {
                                    setDataSource(message.mediaUrl)
                                    setOnCompletionListener { playing = false }
                                    prepareAsync()
                                    setOnPreparedListener { start(); playing = true }
                                }
                            } else { player?.start(); playing = true }
                        } catch (_: Exception) {}
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null, tint = Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        // fake waveform
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val heights = listOf(8, 14, 20, 12, 22, 16, 10, 18, 24, 14, 8, 16, 20, 10)
            heights.forEachIndexed { i, h ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (playing && i % 2 == 0) LocalPalette.current.secondary
                            else Color.White.copy(alpha = 0.7f)
                        )
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${message.voiceDuration / 1000}s",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: Message,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14142B),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(bottom = 26.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("❤️", "😂", "😮", "😢", "👍", "🔥").forEach { emoji ->
                    Text(
                        emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReact(emoji); onDismiss() }
                            .padding(8.dp)
                    )
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            ActionItem(Icons.AutoMirrored.Filled.Reply, "Reply") { onReply(); onDismiss() }
            ActionItem(Icons.Default.Forward, "Forward") { onForward(); onDismiss() }
            if (isMine && message.type == "text") {
                ActionItem(Icons.Default.Edit, "Edit") { onEdit(); onDismiss() }
            }
            if (isMine) {
                ActionItem(Icons.Default.Delete, "Delete everywhere", tint = UnreadRed) { onDelete(); onDismiss() }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ForwardDialog(viewModel: ChatViewModel, onDismiss: () -> Unit, onPick: (User) -> Unit) {
    val users by viewModel.filteredUsersState.collectAsState()
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151528),
        title = { GradientText("Forward to…", MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                GlassSearchBar(value = query, onValueChange = { query = it; viewModel.searchUsers(it) }, placeholder = "Search people…")
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(users, key = { it.uid }) { user ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(user) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = user.name, photoUrl = user.photoUrl, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(user.name, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun AddMembersDialog(viewModel: ChatViewModel, group: Group, onDismiss: () -> Unit) {
    val users by viewModel.filteredUsersState.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    val candidates = users.filter { it.uid !in group.memberIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151528),
        title = { GradientText("Add members", MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(candidates, key = { it.uid }) { user ->
                    val isSel = user.uid in selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) LocalPalette.current.primary.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selected = if (isSel) selected - user.uid else selected + user.uid }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = user.name, photoUrl = user.photoUrl, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(user.name, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    viewModel.addGroupMembers(group.groupId, candidates.filter { it.uid in selected })
                    onDismiss()
                }
            ) { Text("Add", color = LocalPalette.current.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
