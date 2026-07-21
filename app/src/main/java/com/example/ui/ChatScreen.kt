package com.example.ui

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.theme.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.call.CallEngine
import com.example.data.Message
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    recipient: User,
    onBack: () -> Unit,
    onProfile: () -> Unit = {},
    onCall: () -> Unit = {},
    onVideoCall: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messages by viewModel.chatMessagesState.collectAsState()
    val isTyping by viewModel.isRecipientTyping.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()
    val pendingRequestRecipients by viewModel.pendingMessageRequestRecipients.collectAsState()
    val requestPending = recipient.uid in pendingRequestRecipients
    val typingSounds by viewModel.typingSoundsEnabled.collectAsState()
    val notificationSounds by viewModel.notificationSoundsEnabled.collectAsState()
    val callState by CallEngine.state.collectAsState()
    var showThemePicker by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    
    val users by viewModel.usersState.collectAsState()
    val updatedRecipient = users.find { it.uid == recipient.uid } ?: recipient

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Message edit, reply, and block helper states
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var forwardingMessage by remember { mutableStateOf<Message?>(null) }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var recordStartTime by remember { mutableStateOf(0L) }

    // Request Audio & Storage Permissions Launcher
    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingCall by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        audioPermissionGranted = isGranted
        if (isGranted && pendingCall) {
            pendingCall = false
            onCall()
        } else if (!isGranted) {
            pendingCall = false
            Toast.makeText(context, "Microphone access is needed for voice messages and calls.", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true && permissions[Manifest.permission.CAMERA] == true) onVideoCall()
        else Toast.makeText(context, "Camera and microphone permissions are required for video calls.", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(callState.error) {
        callState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            CallEngine.clearError()
        }
    }

    // Initialize/start listening
    LaunchedEffect(recipient.uid) {
        viewModel.startListeningToChat(recipient.uid)
    }

    // Dynamic typing status setter based on text field focus/input
    LaunchedEffect(messageText) {
        viewModel.setTypingState(messageText.isNotBlank())
    }

    DisposableEffect(recipient.uid) {
        onDispose {
            viewModel.setTypingState(false)
            viewModel.stopListeningToChat()
            mediaRecorder?.release()
        }
    }

    // Typing sound feedback when recipient starts typing
    val currentView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(isTyping) {
        if (isTyping) {
            try {
                currentView.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }
    }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Photo Gallery picker for attachment uploading to Supabase
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Uploading photo...", Toast.LENGTH_SHORT).show()
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val fileName = "chat_img_${System.currentTimeMillis()}.jpg"
                    viewModel.uploadFileToSupabase(
                        bucket = "chat_images",
                        fileName = fileName,
                        fileBytes = bytes,
                        contentType = "image/jpeg",
                        onSuccess = { publicUrl ->
                            viewModel.sendMessage(
                                recipientUser = recipient,
                                text = "",
                                imageUrl = publicUrl,
                                replyToId = replyingToMessage?.messageId,
                                replyToText = replyingToMessage?.text,
                                replyToSenderName = replyingToMessage?.senderName
                            )
                            replyingToMessage = null
                            Toast.makeText(context, "Photo sent!", Toast.LENGTH_SHORT).show()
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
        if (!audioPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            try {
                val tempFile = File.createTempFile("voice_temp", ".m4a", context.cacheDir)
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
                recordStartTime = System.currentTimeMillis()
                Toast.makeText(context, "🎙️ Recording started...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("VOICE_REC", "Failed to start media recorder: ${e.message}")
                Toast.makeText(context, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val stopAndSendVoice = {
        if (isRecording && mediaRecorder != null && voiceFile != null) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false

                val durationSec = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt().coerceAtLeast(1)
                val bytes = voiceFile?.readBytes()

                if (bytes != null) {
                    Toast.makeText(context, "Sending voice note...", Toast.LENGTH_SHORT).show()
                    val fileName = "voice_${System.currentTimeMillis()}.m4a"
                    viewModel.uploadFileToSupabase(
                        bucket = "voice_notes",
                        fileName = fileName,
                        fileBytes = bytes,
                        contentType = "audio/m4a",
                        onSuccess = { publicUrl ->
                            viewModel.sendMessage(
                                recipientUser = recipient,
                                text = "",
                                voiceUrl = publicUrl,
                                voiceDurationSec = durationSec,
                                replyToId = replyingToMessage?.messageId,
                                replyToText = replyingToMessage?.text,
                                replyToSenderName = replyingToMessage?.senderName
                            )
                            replyingToMessage = null
                            Toast.makeText(context, "Voice message sent!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Voice upload failed: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("VOICE_REC", "Failed to stop recording safely: ${e.message}")
                isRecording = false
                mediaRecorder = null
            }
        }
    }

    val cancelRecording = {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {}
            mediaRecorder = null
            isRecording = false
            Toast.makeText(context, "Recording cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val isDark = isSystemInDarkTheme()
    val chatGradientBg = when (chatTheme) {
        "Sunset" -> Brush.verticalGradient(listOf(Color(0xFF2B1025), Color(0xFF7B2D45), Color(0xFFFF8A5B).copy(alpha = .55f)))
        "Ocean" -> Brush.verticalGradient(listOf(Color(0xFF001B2E), Color(0xFF004E64), Color(0xFF00A5CF).copy(alpha = .45f)))
        "Forest" -> Brush.verticalGradient(listOf(Color(0xFF071A12), Color(0xFF174A35), Color(0xFF5BC88A).copy(alpha = .35f)))
        "Midnight" -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF171029), Color(0xFF35205E)))
        "Sakura" -> Brush.linearGradient(listOf(Color(0xFF351625), Color(0xFF8E3E62), Color(0xFFFFA9C6).copy(alpha = .62f)))
        "Neon" -> Brush.linearGradient(listOf(Color(0xFF050816), Color(0xFF172554), Color(0xFF00FFD5).copy(alpha = .38f), Color(0xFFFF3DF2).copy(alpha = .28f)))
        "Desert" -> Brush.verticalGradient(listOf(Color(0xFF2C1A12), Color(0xFF875B3A), Color(0xFFE8B26A).copy(alpha = .48f)))
        "Galaxy" -> Brush.radialGradient(listOf(Color(0xFF7D4DFF).copy(.55f), Color(0xFF14102A), Color(0xFF03040B)))
        "Pearl" -> Brush.linearGradient(listOf(Color(0xFFF7F1FF), Color(0xFFDDEEFF), Color(0xFFFCE8F3)))
        else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary.copy(alpha = .12f), MaterialTheme.colorScheme.surfaceVariant))
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Choose chat theme", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Aurora", "Sunset", "Ocean", "Forest", "Midnight", "Sakura", "Neon", "Desert", "Galaxy", "Pearl").forEach { theme ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (chatTheme == theme) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    viewModel.updateChatTheme(theme)
                                    showThemePicker = false
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(theme, fontWeight = if (chatTheme == theme) FontWeight.Bold else FontWeight.Normal)
                            Spacer(Modifier.weight(1f))
                            if (chatTheme == theme) Icon(Icons.Default.Check, null)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text("Close") } }
        )
    }

    if (showChatSettings) {
        val isBlocked = currentUser?.blockedUsers?.contains(recipient.uid) == true
        AlertDialog(
            onDismissRequest = { showChatSettings = false },
            title = { Text("Chat settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ListItem(
                        headlineContent = { Text("View profile") },
                        leadingContent = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { showChatSettings = false; onProfile() }
                    )
                    ListItem(
                        headlineContent = { Text("Conversation theme") },
                        supportingContent = { Text(chatTheme) },
                        leadingContent = { Icon(Icons.Default.Palette, null) },
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { showChatSettings = false; showThemePicker = true }
                    )
                    ListItem(
                        headlineContent = { Text("Typing sounds") },
                        leadingContent = { Icon(Icons.Default.Keyboard, null) },
                        trailingContent = {
                            Switch(typingSounds, { viewModel.updateSoundPreferences(notificationSounds, it) })
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (isBlocked) "Unblock user" else "Block user") },
                        leadingContent = { Icon(if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable {
                            if (isBlocked) viewModel.unblockUser(recipient.uid) { Toast.makeText(context, "User unblocked", Toast.LENGTH_SHORT).show() }
                            else viewModel.blockUser(recipient.uid) { Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show(); onBack() }
                            showChatSettings = false
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showChatSettings = false }) { Text("Done") } }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onProfile).padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (updatedRecipient.profileImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = updatedRecipient.profileImageUrl,
                                    contentDescription = updatedRecipient.name,
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
                                    Text(
                                        text = updatedRecipient.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            // Glowing indicator
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(if (updatedRecipient.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = updatedRecipient.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (updatedRecipient.isOnline) "Active now" else formatLastSeen(updatedRecipient.lastActive),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updatedRecipient.isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (audioPermissionGranted && cameraGranted) onVideoCall()
                        else videoPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = Color(0xFF55C7FF))
                    }
                    IconButton(onClick = {
                        if (audioPermissionGranted) onCall()
                        else { pendingCall = true; permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Audio call", tint = Color(0xFF45D483))
                    }
                    IconButton(onClick = { showThemePicker = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Chat theme", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showChatSettings = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                modifier = Modifier.border(0.dp, Color.Transparent)
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
                            Icons.Default.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Say hello to start the conversation securely!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
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
                    items(messages.reversed(), key = { it.messageId }) { msg ->
                        val isSentByMe = msg.senderId == currentUserId
                        MessageBubbleItem(
                            message = msg,
                            isSentByMe = isSentByMe,
                            onReplySelect = { replyingToMessage = msg },
                            onEditSelect = {
                                if (isSentByMe) {
                                    editingMessage = msg
                                    messageText = msg.text
                                }
                            },
                            onDeleteSelect = {
                                viewModel.deleteMessage(recipient.uid, msg.messageId)
                            },
                            onReactSelect = { reaction ->
                                viewModel.addReaction(recipient.uid, msg.messageId, reaction)
                            },
                            onVoicePlayed = {
                                viewModel.acknowledgeVoicePlayed(msg.messageId, msg.remoteVoiceUrl)
                            },
                            onForward = { forwardingMessage = msg }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isTyping,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TypingGlassIndicator(updatedRecipient.name)
            }

            // Replying to preview banner
            AnimatedVisibility(
                visible = replyingToMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                replyingToMessage?.let { rmsg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Replying to ${if (rmsg.senderId == currentUserId) "yourself" else rmsg.senderName}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(rmsg.text.ifEmpty { "Attachment file" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Editing message preview banner
            AnimatedVisibility(
                visible = editingMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                editingMessage?.let { emsg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Editing Message", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(emsg.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = {
                            editingMessage = null
                            messageText = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Animated Voice Recording status banner
            AnimatedVisibility(
                visible = isRecording,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎙️ Recording voice note...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Row {
                        TextButton(onClick = cancelRecording) {
                            Text("Cancel", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = stopAndSendVoice,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Send", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (requestPending) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HourglassTop, null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Message request pending", fontWeight = FontWeight.Bold)
                            Text("${recipient.name} must confirm before you can continue chatting.", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Interactive dynamic input bar
            Surface(
                tonalElevation = 8.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .glassmorphic(
                        isDark = isDark,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment Plus Button
                    IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach picture", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Voice Note Recording Trigger Button
                    IconButton(onClick = {
                        if (isRecording) {
                            stopAndSendVoice()
                        } else {
                            startRecording()
                        }
                    }) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record Voice",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        enabled = !requestPending,
                        placeholder = { Text(if (requestPending) "Waiting for confirmation…" else "Write a message...",  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_text_field"),
                        maxLines = 4,
                        shape = RoundedCornerShape(28.dp),
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
                            if (messageText.isNotBlank()) {
                                val emsg = editingMessage
                                if (emsg != null) {
                                    viewModel.editMessage(recipient.uid, emsg.messageId, messageText)
                                    editingMessage = null
                                } else {
                                    viewModel.sendMessage(
                                        recipientUser = recipient,
                                        text = messageText,
                                        replyToId = replyingToMessage?.messageId,
                                        replyToText = replyingToMessage?.text,
                                        replyToSenderName = replyingToMessage?.senderName
                                    )
                                    replyingToMessage = null
                                }
                                messageText = ""
                            }
                        },
                        enabled = !requestPending,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("chat_send_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (editingMessage != null) Icons.Default.Check else Icons.Default.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    forwardingMessage?.let { original ->
        AlertDialog(
            onDismissRequest = { forwardingMessage = null },
            title = { Text("Forward message", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(users, key = { it.uid }) { target ->
                        ListItem(
                            headlineContent = { Text(target.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("@${target.username}") },
                            leadingContent = {
                                AsyncImage(target.profileImageUrl.ifBlank { null }, target.name, modifier = Modifier.size(42.dp).clip(CircleShape))
                            },
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable {
                                viewModel.forwardMessage(target, original)
                                Toast.makeText(context, "Forwarded to ${target.name}", Toast.LENGTH_SHORT).show()
                                forwardingMessage = null
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { forwardingMessage = null }) { Text("Cancel") } }
        )
    }

}

@Composable
private fun TypingGlassIndicator(name: String) {
    val motion = rememberInfiniteTransition(label = "typing_dots")
    val phases = listOf(0, 140, 280).mapIndexed { index, delay ->
        motion.animateFloat(
            initialValue = .35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "dot_$index"
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)
            .glassmorphic(isDark = isSystemInDarkTheme(), shape = RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$name is typing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        phases.forEach { alpha ->
            Box(Modifier.padding(horizontal = 2.dp).size(6.dp).graphicsLayer(alpha = alpha.value, scaleX = alpha.value, scaleY = alpha.value).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: Message,
    isSentByMe: Boolean,
    onReplySelect: () -> Unit,
    onEditSelect: () -> Unit,
    onDeleteSelect: () -> Unit,
    onReactSelect: (String) -> Unit,
    onVoicePlayed: () -> Unit,
    onForward: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedDrag by animateFloatAsState(dragOffset, spring(stiffness = Spring.StiffnessMedium), label = "swipe_reply")
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val shape = if (isSentByMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

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

    val textColor = if (isSentByMe && !isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val alignment = if (isSentByMe) Alignment.End else Alignment.Start

    val timeString = remember(message.timestamp) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }
    if (showImageViewer && !message.imageUrl.isNullOrBlank()) {
        FullScreenChatImage(message.imageUrl, onDismiss = { showImageViewer = false })
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isSentByMe) Alignment.TopEnd else Alignment.TopStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .graphicsLayer { translationX = animatedDrag }
                .pointerInput(message.messageId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffset) > 72f) onReplySelect()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceIn(-120f, 120f)
                        }
                    )
                }
                .combinedClickable(
                    onClick = { showMenu = !showMenu },
                    onLongClick = { showMenu = true },
                    onDoubleClick = {
                        if (message.text.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("FireChat message", message.text))
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                ),
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // Reply indicator inside bubble
            if (message.replyToId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .glassmorphic(isDark = isDark, backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .padding(6.dp)
                ) {
                    Column {
                        Text(
                            text = "💬 Replied to ${message.replyToSenderName ?: "User"}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = message.replyToText ?: "Attachment",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                color = Color.Transparent,
                shape = shape,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .glassmorphic(
                        isDark = isDark,
                        backgroundColor = bubbleBg,
                        borderColor = bubbleBorder,
                        shape = shape
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isSentByMe) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    // Render Chat image if present
                    if (!message.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Image attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { showImageViewer = true }
                                .background(MaterialTheme.colorScheme.surface)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Render Voice message if present
                    if (!message.voiceUrl.isNullOrBlank()) {
                        VoicePlayerBubble(voiceUrl = message.voiceUrl, durationSec = message.voiceDurationSec ?: 0, onPlaybackStarted = onVoicePlayed)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Render text message if present
                    if (message.text.isNotBlank()) {
                        LinkifiedChatText(message.text, textColor)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (message.edited) {
                            Text(
                                text = "Edited  ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.End
                        )
                        if (isSentByMe) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = when {
                                    message.seenByRecipient -> Icons.Default.CheckCircle
                                    message.deliveredToRecipient -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                },
                                contentDescription = when {
                                    message.seenByRecipient -> "Seen and saved"
                                    message.deliveredToRecipient -> "Delivered"
                                    else -> "Sent"
                                },
                                tint = if (message.seenByRecipient) Color(0xFF55D6FF) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
                                modifier = Modifier.size(if (message.seenByRecipient) 14.dp else 15.dp)
                            )
                        }
                    }
                }
            }
            
            // Show Reactions
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.values.distinct().forEach { reaction ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .glassmorphic(isDark = isDark, backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = reaction, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Action context dropdown menu on click/long press
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("❤️", "😂", "😮", "😢", "👍").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable {
                                onReactSelect(emoji)
                                showMenu = false
                            }
                            .padding(4.dp)
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Reply", color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onReplySelect()
                    showMenu = false
                }
            )
            if (message.text.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("FireChat message", message.text))
                        showMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Forward") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, null) },
                onClick = { showMenu = false; onForward() }
            )
            if (isSentByMe && message.text.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Edit Message", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        onEditSelect()
                        showMenu = false
                    }
                )
            }
            if (isSentByMe) {
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

// Playable inline custom voice note message widget
@Composable
fun VoicePlayerBubble(
    voiceUrl: String,
    durationSec: Int,
    onPlaybackStarted: () -> Unit = {}
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playbackAcknowledged by remember(voiceUrl) { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // Dispose media player when view leaves screen
    DisposableEffect(voiceUrl) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .glassmorphic(isDark = isDark, backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (mediaPlayer == null) {
                            val mp = MediaPlayer().apply {
                                setDataSource(voiceUrl)
                                prepareAsync()
                                setOnPreparedListener {
                                    start()
                                    isPlaying = true
                                    if (!playbackAcknowledged) {
                                        playbackAcknowledged = true
                                        onPlaybackStarted()
                                    }
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    release()
                                    mediaPlayer = null
                                }
                            }
                            mediaPlayer = mp
                        } else {
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("🎙️ Voice Note", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${durationSec}s duration", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

private fun formatLastSeen(lastActive: Long): String {
    if (lastActive <= 0L) return "Offline"
    val minutes = ((System.currentTimeMillis() - lastActive).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> "Last seen just now"
        minutes < 60 -> "Last seen ${minutes}m ago"
        minutes < 24 * 60 -> "Last seen ${minutes / 60}h ago"
        else -> "Last seen ${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastActive))}"
    }
}

@Composable
private fun FullScreenChatImage(imageUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = imageUrl, contentDescription = "Full screen image", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().pointerInput(imageUrl) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x; offsetY += pan.y
                    }
                }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            )
            Row(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(14.dp)) {
                IconButton(
                    onClick = {
                        if (imageUrl.startsWith("http")) {
                            val request = DownloadManager.Request(Uri.parse(imageUrl))
                                .setTitle("FireChat image")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "FireChat_${System.currentTimeMillis()}.jpg")
                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "Image is already saved on this device", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)
                ) { Icon(Icons.Default.Download, "Download", tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
            Text("Pinch to zoom • drag to move", color = Color.White.copy(alpha = .65f), modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(18.dp))
        }
    }
}

@Composable
private fun LinkifiedChatText(text: String, color: Color) {
    val uriHandler = LocalUriHandler.current
    val urlRegex = remember { Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE) }
    val firstUrl = remember(text) { urlRegex.find(text)?.value }
    val annotated = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            urlRegex.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                pushStringAnnotation("URL", match.value)
                pushStyle(SpanStyle(color = Color(0xFF71C7FF), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                append(match.value)
                pop(); pop(); cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    Column {
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(color = color),
            onClick = { offset -> annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } } }
        )
        firstUrl?.let { url ->
            Spacer(Modifier.height(7.dp))
            Surface(
                onClick = { runCatching { uriHandler.openUri(url) } },
                color = MaterialTheme.colorScheme.surface.copy(alpha = .32f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .15f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, null, tint = Color(0xFF71C7FF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(runCatching { Uri.parse(url).host }.getOrNull() ?: "Open link", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Tap to preview in browser", fontSize = 10.sp, color = color.copy(alpha = .7f))
                    }
                }
            }
        }
    }
}
