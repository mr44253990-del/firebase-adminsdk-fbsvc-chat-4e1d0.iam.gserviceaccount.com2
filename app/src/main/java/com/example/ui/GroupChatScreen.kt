package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.theme.glassmorphic
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.Group
import com.example.data.GroupMessage
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    viewModel: ChatViewModel,
    group: Group,
    onBack: () -> Unit,
    onGroupCall: (video: Boolean) -> Unit = {},
    pendingCallRoomId: String? = null,
    pendingCallVideo: Boolean = false,
    onJoinPendingCall: (String, Boolean) -> Unit = { _, _ -> },
    onDismissPendingCall: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messages by viewModel.groupMessagesState.collectAsState()
    val allUsers by viewModel.usersState.collectAsState()
    val groupVoiceRecorders by viewModel.groupVoiceRecorders.collectAsState()
    val otherVoiceRecorders = remember(groupVoiceRecorders, currentUserId) { groupVoiceRecorders.filter { it != currentUserId } }
    var showAddMembers by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }
    val visibleGroupMessages = remember(messages, messageSearchQuery) {
        val query = messageSearchQuery.trim().lowercase()
        if (query.isBlank()) messages else messages.filter {
            it.text.lowercase().contains(query) || it.senderName.lowercase().contains(query)
        }
    }

    var messageText by remember { mutableStateOf("") }
    var translationMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var translationText by remember { mutableStateOf("") }
    var translationLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    if (translationMessage != null) {
        AlertDialog(
            onDismissRequest = { if (!translationLoading) translationMessage = null },
            title = { Text("AI translation", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(translationMessage?.text.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (translationLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Translating to Bengali…")
                        }
                    } else Text(translationText.ifBlank { "No translation returned." })
                }
            },
            confirmButton = { TextButton(enabled = !translationLoading, onClick = { translationMessage = null }) { Text("Close") } }
        )
    }

    // Select the active group when entering this screen and clean up when leaving
    LaunchedEffect(group.id) {
        viewModel.selectGroup(group)
    }

    DisposableEffect(group.id) {
        onDispose {
            viewModel.selectGroup(null)
        }
    }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var recordStartTime by remember { mutableStateOf(0L) }

    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        audioPermissionGranted = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Microphone access is needed for voice messages.", Toast.LENGTH_SHORT).show()
        }
    }

    // Scroll to bottom on new message instantly
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Photo Gallery picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Uploading photo...", Toast.LENGTH_SHORT).show()
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val fileName = "group_img_${System.currentTimeMillis()}.jpg"
                    viewModel.uploadFileToSupabase(
                        bucket = "chat_images",
                        fileName = fileName,
                        fileBytes = bytes,
                        contentType = "image/jpeg",
                        onSuccess = { publicUrl ->
                            viewModel.sendGroupMessage(
                                groupId = group.id,
                                text = "",
                                imageUrl = publicUrl
                            )
                            Toast.makeText(context, "Photo sent to group!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Photo upload failed: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Voice recording helpers
    val startRecording = {
        if (!isRecording) {
            if (!audioPermissionGranted) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                try {
                    val tempFile = File.createTempFile("group_voice_temp", ".m4a", context.cacheDir)
                    voiceFile = tempFile
                    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    recorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(tempFile.absolutePath)
                        prepare()
                        start()
                    }
                    mediaRecorder = recorder
                    isRecording = true
                    viewModel.setGroupVoiceRecordingState(group.id, true)
                    recordStartTime = System.currentTimeMillis()
                } catch (e: Exception) {
                    runCatching { mediaRecorder?.release() }
                    mediaRecorder = null
                    isRecording = false
                    viewModel.setGroupVoiceRecordingState(group.id, false)
                    voiceFile?.delete()
                    voiceFile = null
                    Log.e("GROUP_VOICE_REC", "Failed to start recording", e)
                    Toast.makeText(context, "Could not start voice recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val stopAndSendVoice = {
        if (isRecording) {
            val recorder = mediaRecorder
            val file = voiceFile
            mediaRecorder = null
            voiceFile = null
            isRecording = false
            viewModel.setGroupVoiceRecordingState(group.id, false)
            try {
                recorder?.stop()
                recorder?.release()
                val durationSec = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt().coerceAtLeast(1)
                val bytes = file?.takeIf { it.exists() && it.length() > 0L }?.readBytes()
                file?.delete()
                if (bytes != null) {
                    val fileName = "group_voice_${System.currentTimeMillis()}.m4a"
                    viewModel.uploadFileToSupabase(
                        bucket = "voice_notes",
                        fileName = fileName,
                        fileBytes = bytes,
                        contentType = "audio/m4a",
                        onSuccess = { publicUrl ->
                            viewModel.sendGroupMessage(group.id, "", voiceUrl = publicUrl, voiceDurationSec = durationSec)
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Voice upload failed: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                runCatching { recorder?.release() }
                file?.delete()
                Log.e("GROUP_VOICE_REC", "Failed to stop recording", e)
            }
        }
    }

    val cancelRecording = {
        val recorder = mediaRecorder
        val file = voiceFile
        mediaRecorder = null
        voiceFile = null
        isRecording = false
        viewModel.setGroupVoiceRecordingState(group.id, false)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        file?.delete()
    }

    // Keep gesture callbacks fresh without restarting pointer input when recording state changes.
    // Restarting the detector during onLongPress can cancel the active gesture and leave the
    // group recorder in an inconsistent state.
    val latestIsRecording = rememberUpdatedState(isRecording)
    val latestAudioPermissionGranted = rememberUpdatedState(audioPermissionGranted)
    val latestStartRecording = rememberUpdatedState(startRecording)
    val latestStopAndSendVoice = rememberUpdatedState(stopAndSendVoice)
    val latestCancelRecording = rememberUpdatedState(cancelRecording)

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgStart = MaterialTheme.colorScheme.background
    val bgEnd = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val chatGradientBg = Brush.verticalGradient(
        colors = listOf(bgStart, bgEnd)
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showMembers = true }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        if (group.profileUrl.isNotBlank()) {
                            AsyncImage(
                                model = group.profileUrl,
                                contentDescription = group.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${group.members.size} Members",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("group_chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onGroupCall(false)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Start group audio call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        onGroupCall(true)
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Start group video call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showMessageSearch = !showMessageSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search group messages", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showAddMembers = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add members", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                modifier = Modifier.border(0.dp, Color.Transparent) // clean glass look
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(chatGradientBg)
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = !pendingCallRoomId.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Group call available", fontWeight = FontWeight.Bold)
                            Text("Join ${group.name} when you are ready", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { pendingCallRoomId?.let { onJoinPendingCall(it, pendingCallVideo) } }) {
                            Text("Join Call", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismissPendingCall) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss call")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = otherVoiceRecorders.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val names = otherVoiceRecorders.mapNotNull { uid -> allUsers.firstOrNull { it.uid == uid }?.name?.takeIf { it.isNotBlank() } }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .92f)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (names.isNotEmpty()) "${names.take(2).joinToString(" and ")} ${if (names.size > 2) "and others " else ""}is recording a voice note…" else "Someone is recording a voice note…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showMessageSearch,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageSearchQuery,
                            onValueChange = { messageSearchQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (messageSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { messageSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            placeholder = { Text("Search group messages") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (messageSearchQuery.isBlank()) "All" else visibleGroupMessages.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Messages list area
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Welcome to ${group.name}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Send a message or a voice note to get started!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }
            } else if (visibleGroupMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No matching group messages", style = MaterialTheme.typography.titleMedium)
                        Text("Try another keyword", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                    reverseLayout = true
                ) {
                    items(visibleGroupMessages.reversed(), key = { it.messageId }) { msg ->
                        if (msg.senderId == "system") {
                            // System Log message
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val isSentByMe = msg.senderId == currentUserId
                            GroupMessageBubbleItem(
                                msg = msg, 
                                isSentByMe = isSentByMe,
                                onDeleteSelect = {
                                    viewModel.deleteGroupMessage(group.id, msg.messageId)
                                },
                                onTranslate = { target ->
                                    translationMessage = target
                                    translationText = ""
                                    translationLoading = true
                                    viewModel.askAssistant(
                                        "Translate the following group chat message into natural Bengali. Return only the Bengali translation, preserving names, numbers, links, and formatting:\n\n${target.text}",
                                        emptyList()
                                    ) { result ->
                                        translationText = result
                                        translationLoading = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Input fields bar
            Surface(
                color = Color.Transparent,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    // Apply the IME inset only to the composer. Keeping the message column at its
                    // stable height prevents the entire chat surface from jumping when the keyboard opens.
                    .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                    .glassmorphic(
                        isDark = isDark,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Write a message to group...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("group_message_input"),
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (messageText.isBlank()) {
                        // Hold to record, release to send. A canceled gesture discards the clip.
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            val releasedNormally = runCatching { tryAwaitRelease() }.getOrDefault(false)
                                            if (latestIsRecording.value) {
                                                if (releasedNormally) latestStopAndSendVoice.value() else latestCancelRecording.value()
                                            }
                                        },
                                        onLongPress = {
                                            if (latestAudioPermissionGranted.value && !latestIsRecording.value) {
                                                latestStartRecording.value()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Release to send voice message" else "Hold to record voice message",
                                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Send text message Button
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendGroupMessage(group.id, messageText)
                                    messageText = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showMembers) {
        ModalBottomSheet(
            onDismissRequest = { showMembers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${group.name} members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("${group.members.size} people in this group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(group.members, key = { it }) { memberId ->
                        val member = allUsers.firstOrNull { it.uid == memberId }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!member?.profileImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = member?.profileImageUrl,
                                        contentDescription = member?.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(42.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text((member?.name ?: memberId).take(1).uppercase(), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(member?.name?.ifBlank { memberId.take(10) } ?: memberId.take(10), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (member?.isOnline == true) "Online now" else "Member", style = MaterialTheme.typography.bodySmall, color = if (member?.isOnline == true) Color(0xFF1B8A4B) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (memberId == currentUserId) Text("You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMembers) {
        val selectable = allUsers.filterNot { group.members.contains(it.uid) }
        val selected = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { showAddMembers = false },
            title = { Text("Add group members", fontWeight = FontWeight.Bold) },
            text = {
                if (selectable.isEmpty()) {
                    Text("Everyone is already in this group.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selectable, key = { it.uid }) { user ->
                            val checked = selected.contains(user.uid)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable { if (checked) selected.remove(user.uid) else selected.add(user.uid) }
                                    .background(if (checked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked, null)
                                Spacer(Modifier.width(8.dp))
                                Text(user.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addGroupMembers(group, selected.toList()) { ok ->
                            Toast.makeText(context, if (ok) "Members added" else "Could not add members", Toast.LENGTH_SHORT).show()
                        }
                        showAddMembers = false
                    },
                    enabled = selected.isNotEmpty()
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddMembers = false }) { Text("Cancel") } }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupMessageBubbleItem(
    msg: GroupMessage,
    isSentByMe: Boolean,
    onDeleteSelect: () -> Unit,
    onTranslate: (GroupMessage) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    val bubbleBg = if (isSentByMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.3f else 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.9f)
    }
    
    val bubbleBorder = if (isSentByMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }

    val alignment = if (isSentByMe) Alignment.CenterEnd else Alignment.CenterStart
    val textColor = if (isSentByMe && !isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
    val timeString = try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        }
        sdf.format(Date(msg.timestamp))
    } catch (e: Exception) {
        ""
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // Sender display name (small)
            if (!isSentByMe) {
                Text(
                    text = msg.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }

            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSentByMe) 16.dp else 2.dp,
                    bottomEnd = if (isSentByMe) 2.dp else 16.dp
                ),
                modifier = Modifier
                    .glassmorphic(
                        isDark = isDark,
                        backgroundColor = bubbleBg,
                        borderColor = bubbleBorder,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isSentByMe) 16.dp else 2.dp,
                            bottomEnd = if (isSentByMe) 2.dp else 16.dp
                        )
                    )
                    .combinedClickable(
                        onClick = { showMenu = true },
                        onLongClick = { showMenu = true },
                        onDoubleClick = {
                            if (msg.text.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Convo Chat message", msg.text))
                                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!msg.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = msg.imageUrl,
                            contentDescription = "Image attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (!msg.voiceUrl.isNullOrBlank()) {
                        GroupAudioPlayerItem(voiceUrl = msg.voiceUrl, durationSec = msg.voiceDurationSec ?: 0)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (msg.text.isNotBlank()) {
                        Text(
                            text = msg.text,
                            color = textColor,
                            fontSize = 14.sp
                        )
                        val urlRegex = remember { Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE) }
                        val detectedUrl = remember(msg.text) { urlRegex.find(msg.text)?.value?.trimEnd('.', ',', '!', '?', ')', ']') }
                        if (!detectedUrl.isNullOrBlank()) {
                            GroupLinkPreviewCard(url = detectedUrl, isSentByMe = isSentByMe)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = timeString,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Dropdown Menu for deletion
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (msg.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Translate to Bengali (AI)") },
                        leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showMenu = false
                            onTranslate(msg)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete Message", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDeleteSelect()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun GroupAudioPlayerItem(voiceUrl: String, durationSec: Int) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(voiceUrl) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    isPlaying = false
                } else {
                    try {
                        val mp = MediaPlayer().apply {
                            setDataSource(context, Uri.parse(voiceUrl))
                            prepare()
                            start()
                            setOnCompletionListener {
                                isPlaying = false
                                release()
                                mediaPlayer = null
                            }
                        }
                        mediaPlayer = mp
                        isPlaying = true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = "🎙️ Voice Note",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${durationSec}s",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
private fun GroupLinkPreviewCard(url: String, isSentByMe: Boolean) {
    LinkPreviewCard(
        url = url,
        color = if (isSentByMe) Color.White else MaterialTheme.colorScheme.onSurface
    )
}

