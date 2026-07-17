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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messages by viewModel.groupMessagesState.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
                recordStartTime = System.currentTimeMillis()
                Toast.makeText(context, "🎙️ Recording group voice...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
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
                    val fileName = "group_voice_${System.currentTimeMillis()}.m4a"
                    viewModel.uploadFileToSupabase(
                        bucket = "voice_notes",
                        fileName = fileName,
                        fileBytes = bytes,
                        contentType = "audio/m4a",
                        onSuccess = { publicUrl ->
                            viewModel.sendGroupMessage(
                                groupId = group.id,
                                text = "",
                                voiceUrl = publicUrl,
                                voiceDurationSec = durationSec
                            )
                            Toast.makeText(context, "Voice note sent to group!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Voice upload failed: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
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
        colors = listOf(Color(0xFF261C18), Color(0xFF382A24))
    )

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (group.profileUrl.isNotBlank()) {
                            AsyncImage(
                                model = group.profileUrl,
                                contentDescription = group.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFFDFBBA3), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4E3B33)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFFDFBBA3))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "${group.members.size} Members",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBCAAA4)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("group_chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFDFBBA3))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF382A24)
                )
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
                        Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFFDFBBA3).copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Welcome to ${group.name}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Send a message or a voice note to get started! (No Webhooks triggered)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBCAAA4),
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
                        if (msg.senderId == "system") {
                            // System Log message
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        color = Color(0xFFDFBBA3),
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
                                }
                            )
                        }
                    }
                }
            }

            // Input fields bar
            Surface(
                color = Color(0xFF382A24),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(1.dp, Color(0xFF4E3B33), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF4E3B33), CircleShape)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach image", tint = Color(0xFFDFBBA3))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Write a message to group...", color = Color(0xFFBCAAA4)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("group_message_input"),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFDFBBA3),
                            unfocusedBorderColor = Color(0xFF4E3B33),
                            focusedContainerColor = Color(0xFF261C18),
                            unfocusedContainerColor = Color(0xFF261C18)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (messageText.isBlank()) {
                        // Mic Button for Recording Voice
                        IconButton(
                            onClick = { if (isRecording) stopAndSendVoice() else startRecording() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (isRecording) Color(0xFFEF5350) else Color(0xFFDFBBA3), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record Audio",
                                tint = if (isRecording) Color.White else Color(0xFF261C18)
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
                                .background(Color(0xFFDFBBA3), CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF261C18))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupMessageBubbleItem(msg: GroupMessage, isSentByMe: Boolean, onDeleteSelect: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = if (isSentByMe) Color(0xFF4E3B33) else Color(0xFF382A24)
    val alignment = if (isSentByMe) Alignment.CenterEnd else Alignment.CenterStart
    val textColor = Color.White
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
                    color = Color(0xFFDFBBA3),
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }

            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSentByMe) 16.dp else 2.dp,
                    bottomEnd = if (isSentByMe) 2.dp else 16.dp
                ),
                border = BorderStroke(1.dp, Color(0xFF4E3B33)),
                modifier = Modifier.clickable { if (isSentByMe) showMenu = true }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!msg.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = msg.imageUrl,
                            contentDescription = "Image attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
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
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = timeString,
                            fontSize = 9.sp,
                            color = Color(0xFFBCAAA4).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Dropdown Menu for deletion
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF4E3B33))
            ) {
                DropdownMenuItem(
                    text = { Text("Delete Message", color = Color.Red) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
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
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
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
                tint = Color(0xFFDFBBA3)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = "🎙️ Voice Note",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${durationSec}s",
                fontSize = 10.sp,
                color = Color(0xFFBCAAA4)
            )
        }
    }
}
