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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messages by viewModel.chatMessagesState.collectAsState()
    val isTyping by viewModel.isRecipientTyping.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Message edit, reply, and block helper states
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        audioPermissionGranted = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Microphone access is needed for voice messages.", Toast.LENGTH_SHORT).show()
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

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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

    val chatGradientBg = Brush.verticalGradient(
        colors = listOf(ChocolateDark, ChocolateMedium)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (recipient.profileImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = recipient.profileImageUrl,
                                    contentDescription = recipient.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, GoldAccent, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(ChocolateLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = recipient.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = GoldAccent
                                    )
                                }
                            }
                            // Glowing indicator
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(ChocolateMedium)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(if (recipient.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = recipient.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = WhiteText
                            )
                            if (isTyping) {
                                Text(
                                    text = "✍️ Typing...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF81C784)
                                )
                            } else {
                                Text(
                                    text = if (recipient.isOnline) "Active Now" else "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (recipient.isOnline) Color(0xFF81C784) else GrayText
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAccent)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.blockUser(recipient.uid) {
                            Toast.makeText(context, "User Blocked", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Block, contentDescription = "Block User", tint = Color(0xFFEF5350))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChocolateMedium
                ),
                modifier = Modifier.border(0.dp, ChocolateLight)
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
                        Icon(Icons.Default.Forum, contentDescription = null, tint = GoldAccent.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhiteText
                        )
                        Text(
                            text = "Say hello to start the conversation securely!",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
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
                            }
                        )
                    }
                }
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
                            .background(ChocolateLight)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Reply, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Replying to ${if (rmsg.senderId == currentUserId) "yourself" else rmsg.senderName}", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(rmsg.text.ifEmpty { "Attachment file" }, color = WhiteText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = GrayText, modifier = Modifier.size(18.dp))
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
                            .background(ChocolateLight)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Editing Message", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(emsg.text, color = WhiteText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = {
                            editingMessage = null
                            messageText = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = GrayText, modifier = Modifier.size(18.dp))
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
                        .background(Color(0xFFC62828))
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
                            Text("Send", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Interactive dynamic input bar
            Surface(
                tonalElevation = 8.dp,
                color = ChocolateMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(1.dp, ChocolateLight, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment Plus Button
                    IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach picture", tint = GoldAccent)
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
                            tint = if (isRecording) Color.Red else GoldAccent
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Write a message...", color = GrayText) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_text_field"),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WhiteText,
                            unfocusedTextColor = WhiteText,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = ChocolateLight,
                            focusedContainerColor = ChocolateDark,
                            unfocusedContainerColor = ChocolateDark
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
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(GoldAccent)
                            .testTag("chat_send_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = ChocolateDark
                        )
                    ) {
                        Icon(
                            imageVector = if (editingMessage != null) Icons.Default.Check else Icons.Default.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: Message,
    isSentByMe: Boolean,
    onReplySelect: () -> Unit,
    onEditSelect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val shape = if (isSentByMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    val containerColor = if (isSentByMe) ChocolateLight else ChocolateMedium
    val textColor = WhiteText
    val alignment = if (isSentByMe) Alignment.End else Alignment.Start

    val timeString = remember(message.timestamp) {
        // Displays time correctly formatted in 12-hour local format
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isSentByMe) Alignment.TopEnd else Alignment.TopStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = { showMenu = !showMenu },
                    onLongClick = { showMenu = true }
                ),
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // Reply indicator inside bubble
            if (message.replyToId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChocolateMedium.copy(alpha = 0.5f))
                        .border(BorderStroke(1.dp, ChocolateLight), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Column {
                        Text(
                            text = "💬 Replied to ${message.replyToSenderName ?: "User"}",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = message.replyToText ?: "Attachment",
                            color = GrayText,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                color = containerColor,
                shape = shape,
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 280.dp),
                border = BorderStroke(1.dp, ChocolateLight)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isSentByMe) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GoldAccent,
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
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChocolateDark)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Render Voice message if present
                    if (!message.voiceUrl.isNullOrBlank()) {
                        VoicePlayerBubble(voiceUrl = message.voiceUrl, durationSec = message.voiceDurationSec ?: 0)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Render text message if present
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
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
                                color = GrayText.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = GrayText,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        // Action context dropdown menu on click/long press
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(ChocolateMedium)
        ) {
            DropdownMenuItem(
                text = { Text("Reply", color = WhiteText) },
                leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null, tint = GoldAccent) },
                onClick = {
                    onReplySelect()
                    showMenu = false
                }
            )
            if (isSentByMe && message.text.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Edit Message", color = WhiteText) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = GoldAccent) },
                    onClick = {
                        onEditSelect()
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
    durationSec: Int
) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current

    // Dispose media player when view leaves screen
    DisposableEffect(voiceUrl) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChocolateDark)
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
                .background(GoldAccent)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = ChocolateDark,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("🎙️ Voice Note", color = WhiteText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${durationSec}s duration", color = GrayText, fontSize = 10.sp)
        }
    }
}
