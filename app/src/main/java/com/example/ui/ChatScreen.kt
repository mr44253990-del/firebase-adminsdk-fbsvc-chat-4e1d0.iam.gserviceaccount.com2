package com.example.ui

import android.Manifest
import android.app.DownloadManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
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
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val deliveryStates by viewModel.messageDeliveryStates.collectAsState()
    val isTyping by viewModel.isRecipientTyping.collectAsState()
    val isVoiceRecording by viewModel.isRecipientVoiceRecording.collectAsState()
    val recipientUploadState by viewModel.recipientUploadState.collectAsState()
    val chatTheme by viewModel.chatTheme.collectAsState()
    val currentUser by viewModel.currentUserState.collectAsState()
    val pendingRequestRecipients by viewModel.pendingMessageRequestRecipients.collectAsState()
    val requestPending = recipient.uid in pendingRequestRecipients
    val typingSounds by viewModel.typingSoundsEnabled.collectAsState()
    val notificationSounds by viewModel.notificationSoundsEnabled.collectAsState()
    val callState by CallEngine.state.collectAsState()
    var showThemePicker by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }
    
    val users by viewModel.usersState.collectAsState()
    val updatedRecipient = users.find { it.uid == recipient.uid } ?: recipient

    val chatDraftPrefs = remember(context) { context.getSharedPreferences("chat_drafts", Context.MODE_PRIVATE) }
    var messageText by remember(recipient.uid) {
        mutableStateOf(chatDraftPrefs.getString("draft_${recipient.uid}", "") ?: "")
    }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var fileUploading by remember { mutableStateOf(false) }
    var fileProgress by remember { mutableIntStateOf(0) }
    var fileEta by remember { mutableLongStateOf(0L) }
    val conversationAccepted = currentUser?.friends?.contains(recipient.uid) == true || messages.isNotEmpty()
    val listState = rememberLazyListState()

    // Message edit, reply, and block helper states
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var forwardingMessage by remember { mutableStateOf<Message?>(null) }
    var translationMessage by remember { mutableStateOf<Message?>(null) }
    var translationText by remember { mutableStateOf("") }
    var translationLoading by remember { mutableStateOf(false) }

    val visibleMessages = remember(messages, messageSearchQuery) {
        val query = messageSearchQuery.trim().lowercase()
        if (query.isBlank()) messages else messages.filter { message ->
            listOf(
                message.text,
                message.senderName,
                message.fileName.orEmpty(),
                message.replyToText.orEmpty()
            ).any { it.lowercase().contains(query) }
        }
    }

    LaunchedEffect(recipient.uid, messageText, editingMessage) {
        if (editingMessage == null) {
            chatDraftPrefs.edit().putString("draft_${recipient.uid}", messageText).apply()
        }
    }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var recordStartTime by remember { mutableStateOf(0L) }

    // Audio permission is shared by voice notes, calls, and on-device dictation.
    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // On-device voice dictation state; recognized text remains editable before sending.
    var isDictating by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    val stopDictation = {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        isDictating = false
    }

    val startDictation = {
        if (!audioPermissionGranted) {
            Toast.makeText(context, "Use the voice-note button once to grant microphone access, then try dictation again", Toast.LENGTH_LONG).show()
        } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Voice dictation is not available on this device", Toast.LENGTH_LONG).show()
        } else {
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isDictating = true }
                    override fun onBeginningOfSpeech() { isDictating = true }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { isDictating = false }
                    override fun onError(error: Int) {
                        isDictating = false
                        if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            Toast.makeText(context, "Dictation stopped", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (spoken.isNotBlank()) {
                            messageText = listOf(messageText.trim(), spoken.trim()).filter { it.isNotBlank() }.joinToString(" ")
                        }
                        isDictating = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                speechRecognizer = recognizer
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
                }
                recognizer.startListening(intent)
                isDictating = true
            } catch (error: Exception) {
                isDictating = false
                Toast.makeText(context, "Dictation unavailable: ${error.message ?: "unknown error"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Request Audio & Storage Permissions Launcher
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

    // Debounced typing signal with an inactivity lease; this keeps the recipient indicator reliable.
    LaunchedEffect(recipient.uid, messageText) {
        if (messageText.isBlank()) {
            viewModel.setTypingState(false)
        } else {
            viewModel.setTypingState(true)
            delay(1800L)
            viewModel.setTypingState(false)
        }
    }

    DisposableEffect(recipient.uid) {
        onDispose {
            viewModel.setTypingState(false)
            viewModel.setVoiceRecordingState(false)
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

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (!conversationAccepted) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var fileName = "attachment_${System.currentTimeMillis()}"
        var fileSize = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(0) ?: fileName
                if (!cursor.isNull(1)) fileSize = cursor.getLong(1)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        fileUploading = true; fileProgress = 0; fileEta = 0L
        viewModel.setFileUploadState(fileName, 0, 0L, active = true)
        viewModel.uploadUriToSupabase(uri, fileName, mime, fileSize,
            onProgress = { percent, eta ->
                fileProgress = percent
                fileEta = eta
                viewModel.setFileUploadState(fileName, percent, eta, active = true)
            },
            onSuccess = { url ->
                fileUploading = false
                viewModel.setFileUploadState(fileName, 100, 0L, active = false)
                viewModel.sendMessage(recipient, "", fileUrl = url, fileName = fileName, fileMimeType = mime, fileSize = fileSize)
            },
            onFailure = { error ->
                fileUploading = false
                viewModel.setFileUploadState(fileName, 0, 0L, active = false)
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            })
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
                viewModel.setVoiceRecordingState(true)
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
                viewModel.setVoiceRecordingState(false)

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
                viewModel.setVoiceRecordingState(false)
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
            viewModel.setVoiceRecordingState(false)
            Toast.makeText(context, "Recording cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
        "Royal Gold" -> Brush.linearGradient(listOf(Color(0xFF120D05), Color(0xFF5B3A08), Color(0xFFFFD166).copy(.58f)))
        "Cyber Lime" -> Brush.linearGradient(listOf(Color.Black, Color(0xFF102A20), Color(0xFFA7FF4F).copy(.42f)))
        "Rose Quartz" -> Brush.linearGradient(listOf(Color(0xFF2A101F), Color(0xFF7A3157), Color(0xFFFFB4D7).copy(.55f)))
        "Liquid Aurora" -> Brush.linearGradient(listOf(Color(0xFF080A1C), Color(0xFF32256B), Color(0xFF00D9E8).copy(.42f), Color(0xFFFF3CA6).copy(.28f)))
        "Crystal Ocean" -> Brush.verticalGradient(listOf(Color(0xFF00141F), Color(0xFF07506A), Color(0xFF4CE6FF).copy(.35f)))
        "Velvet Rose" -> Brush.linearGradient(listOf(Color(0xFF170711), Color(0xFF5A1945), Color(0xFFFF72BC).copy(.46f)))
        "Obsidian" -> Brush.radialGradient(listOf(Color(0xFF302060), Color(0xFF07070D), Color.Black))
        "Frost" -> Brush.linearGradient(listOf(Color(0xFFF8F6FF), Color(0xFFDDEEFF), Color(0xFFEBDFFF)))
        "Glassmorphism" -> Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF304E68), Color(0xFF7AC7C4).copy(alpha = .40f)))
        "Paper Texture" -> Brush.linearGradient(listOf(Color(0xFFFFF8E7), Color(0xFFF3D9B1), Color(0xFFD99A6C).copy(alpha = .48f)))
        "Aurora Mist" -> Brush.radialGradient(listOf(Color(0xFFB8FFF9).copy(.64f), Color(0xFF6C63FF).copy(.38f), Color(0xFF11142B)))
        "Monochrome Grid" -> Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF252936), Color(0xFF6B7280).copy(.30f)))
        "Cherry Blossom" -> Brush.linearGradient(listOf(Color(0xFF32162C), Color(0xFF8C4A72), Color(0xFFFFD3E1).copy(.58f)))
        else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary.copy(alpha = .12f), MaterialTheme.colorScheme.surfaceVariant))
    }

    val textureModifier = Modifier.drawBehind {
        val w = size.width
        val h = size.height
        when (chatTheme) {
            "Glassmorphism", "Aurora Mist" -> {
                drawCircle(Color(0xFF8BE9FD).copy(alpha = .10f), radius = w * .34f, center = androidx.compose.ui.geometry.Offset(w * .12f, h * .16f))
                drawCircle(Color(0xFFFF79C6).copy(alpha = .08f), radius = w * .28f, center = androidx.compose.ui.geometry.Offset(w * .90f, h * .72f))
            }
            "Paper Texture" -> {
                for (i in 0..16) {
                    val y = h * (i / 16f)
                    drawLine(Color(0xFF7B4B2A).copy(alpha = .07f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y + 18f), strokeWidth = 1.2f)
                }
            }
            "Monochrome Grid" -> {
                val step = 42f
                var x = 0f
                while (x < w) { drawLine(Color.White.copy(alpha = .045f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1f); x += step }
                var y = 0f
                while (y < h) { drawLine(Color.White.copy(alpha = .045f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f); y += step }
            }
            "Cherry Blossom" -> {
                listOf(.12f to .18f, .72f to .26f, .36f to .62f, .88f to .82f).forEach { (x, y) ->
                    drawCircle(Color(0xFFFFC3D7).copy(alpha = .16f), radius = 28f, center = androidx.compose.ui.geometry.Offset(w * x, h * y))
                    drawCircle(Color(0xFFFFE6EF).copy(alpha = .18f), radius = 9f, center = androidx.compose.ui.geometry.Offset(w * x + 14f, h * y - 9f))
                }
            }
        }
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Choose chat theme", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val premiumActive = currentUser?.isPremium == true && (currentUser?.premiumPlan == "lifetime" || (currentUser?.premiumUntil ?: 0L) > System.currentTimeMillis())
                    val availableThemes = listOf("Aurora", "Sunset", "Ocean", "Forest", "Midnight", "Sakura", "Neon", "Desert", "Galaxy", "Pearl", "Liquid Aurora", "Crystal Ocean", "Velvet Rose", "Obsidian", "Frost", "Glassmorphism", "Paper Texture", "Aurora Mist", "Monochrome Grid", "Cherry Blossom") + if (premiumActive) listOf("Royal Gold", "Cyber Lime", "Rose Quartz") else emptyList()
                    availableThemes.forEach { theme ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (chatTheme == theme) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    viewModel.updateChatTheme(theme)
                                    showThemePicker = false
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val previewColors = when (theme) {
                                "Sunset" -> listOf(Color(0xFF7B2D45), Color(0xFFFF8A5B)); "Ocean" -> listOf(Color(0xFF004E64), Color(0xFF00A5CF)); "Forest" -> listOf(Color(0xFF174A35), Color(0xFF5BC88A)); "Midnight" -> listOf(Color.Black, Color(0xFF35205E)); "Sakura" -> listOf(Color(0xFF8E3E62), Color(0xFFFFA9C6)); "Neon" -> listOf(Color(0xFF00FFD5), Color(0xFFFF3DF2)); "Desert" -> listOf(Color(0xFF875B3A), Color(0xFFE8B26A)); "Galaxy" -> listOf(Color(0xFF7D4DFF), Color(0xFF14102A)); "Pearl" -> listOf(Color(0xFFF7F1FF), Color(0xFFDDEEFF)); "Royal Gold" -> listOf(Color(0xFF5B3A08), Color(0xFFFFD166)); "Cyber Lime" -> listOf(Color.Black, Color(0xFFA7FF4F)); "Rose Quartz" -> listOf(Color(0xFF7A3157), Color(0xFFFFB4D7)); "Liquid Aurora" -> listOf(Color(0xFF32256B), Color(0xFF00D9E8), Color(0xFFFF3CA6)); "Crystal Ocean" -> listOf(Color(0xFF07506A), Color(0xFF4CE6FF)); "Velvet Rose" -> listOf(Color(0xFF5A1945), Color(0xFFFF72BC)); "Obsidian" -> listOf(Color.Black, Color(0xFF302060)); "Frost" -> listOf(Color(0xFFF8F6FF), Color(0xFFDDEEFF)); "Glassmorphism" -> listOf(Color(0xFF304E68), Color(0xFF7AC7C4)); "Paper Texture" -> listOf(Color(0xFFFFF8E7), Color(0xFFD99A6C)); "Aurora Mist" -> listOf(Color(0xFFB8FFF9), Color(0xFF6C63FF)); "Monochrome Grid" -> listOf(Color(0xFF0B0D12), Color(0xFF6B7280)); "Cherry Blossom" -> listOf(Color(0xFF8C4A72), Color(0xFFFFD3E1)); else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            }
                            Box(Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(previewColors)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)))
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
                        showMessageSearch = !showMessageSearch
                        if (!showMessageSearch) messageSearchQuery = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search messages", tint = MaterialTheme.colorScheme.primary)
                    }
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
                .then(textureModifier)
                .padding(innerPadding)
        ) {
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageSearchQuery,
                            onValueChange = { messageSearchQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (messageSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { messageSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            placeholder = { Text("Search messages", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (messageSearchQuery.isBlank()) "All" else visibleMessages.size.toString(),
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
            } else if (visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching messages", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Try another keyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    items(visibleMessages.reversed(), key = { it.messageId }) { msg ->
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
                            deliveryState = deliveryStates[msg.messageId] ?: if (msg.deliveredToRecipient) "delivered" else "sent",
                            onRetry = { viewModel.retryMessage(recipient, msg) },
                            onVoicePlayed = {
                                viewModel.acknowledgeVoicePlayed(msg.messageId, msg.remoteVoiceUrl)
                            },
                            onFileConsumed = { viewModel.acknowledgeFileConsumed(msg.messageId, msg.remoteFileUrl) },
                            onForward = { forwardingMessage = msg },
                            onTranslate = { target ->
                                translationMessage = target
                                translationText = ""
                                translationLoading = true
                                viewModel.askAssistant(
                                    "Translate the following chat message into natural Bengali. Return only the Bengali translation, preserving names, numbers, links, and formatting:\n\n${target.text}",
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

            AnimatedVisibility(
                visible = isTyping || isVoiceRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (isVoiceRecording) VoiceRecordingIndicator(updatedRecipient.name)
                else TypingGlassIndicator(updatedRecipient.name)
            }

            AnimatedVisibility(
                visible = recipientUploadState?.active == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                recipientUploadState?.let { state ->
                    RecipientUploadIndicator(updatedRecipient.name, state)
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

            if (fileUploading) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LinearProgressIndicator(progress = { fileProgress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Uploading file • $fileProgress% • about ${fileEta}s left", fontSize = 11.sp)
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
                    Box {
                        IconButton(onClick = {
                            if (!conversationAccepted) Toast.makeText(context, "Send a text request and wait for confirmation first", Toast.LENGTH_LONG).show()
                            else showAttachmentMenu = true
                        }, enabled = !requestPending && !fileUploading) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "Attachments", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(showAttachmentMenu, { showAttachmentMenu = false }) {
                            DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null) }, onClick = {
                                showAttachmentMenu = false; photoPickerLauncher.launch("image/*")
                            })
                            DropdownMenuItem(text = { Text("Document, PDF, APK or audio") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = {
                                showAttachmentMenu = false; filePickerLauncher.launch(arrayOf("*/*"))
                            })
                            DropdownMenuItem(text = { Text("Schedule this message") }, leadingIcon = { Icon(Icons.Default.Schedule, null) }, onClick = {
                                showAttachmentMenu = false
                                if (messageText.isBlank()) Toast.makeText(context, "Write a message first", Toast.LENGTH_SHORT).show()
                                else if (!conversationAccepted) Toast.makeText(context, "Wait until the conversation is accepted", Toast.LENGTH_LONG).show()
                                else showScheduleDialog = true
                            })
                        }
                    }

                    // Voice Note Recording Trigger Button
                    IconButton(onClick = {
                        if (!conversationAccepted) Toast.makeText(context, "Wait until the message request is confirmed", Toast.LENGTH_LONG).show()
                        else if (isRecording) stopAndSendVoice() else startRecording()
                    }, enabled = !requestPending && !fileUploading && !isDictating) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record Voice",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { if (isDictating) stopDictation() else startDictation() },
                        enabled = !requestPending && !fileUploading && !isRecording
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicNone,
                            contentDescription = if (isDictating) "Stop dictation" else "Dictate message",
                            tint = if (isDictating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        enabled = !requestPending && !fileUploading,
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
                                chatDraftPrefs.edit().remove("draft_${recipient.uid}").apply()
                            }
                        },
                        enabled = !requestPending && !fileUploading,
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

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Schedule message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose when to send this text message.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("\"${messageText.trim().take(120)}${if (messageText.trim().length > 120) "…" else "\""}", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        val at = System.currentTimeMillis() + 10 * 60 * 1000L
                        viewModel.scheduleMessage(updatedRecipient, messageText, at) {
                            Toast.makeText(context, "Scheduled for 10 minutes from now", Toast.LENGTH_SHORT).show()
                            messageText = ""
                            chatDraftPrefs.edit().remove("draft_${recipient.uid}").apply()
                        }
                        showScheduleDialog = false
                    }) { Text("In 10 minutes") }
                    TextButton(onClick = {
                        val at = System.currentTimeMillis() + 60 * 60 * 1000L
                        viewModel.scheduleMessage(updatedRecipient, messageText, at) {
                            Toast.makeText(context, "Scheduled for 1 hour from now", Toast.LENGTH_SHORT).show()
                            messageText = ""
                            chatDraftPrefs.edit().remove("draft_${recipient.uid}").apply()
                        }
                        showScheduleDialog = false
                    }) { Text("In 1 hour") }
                    TextButton(onClick = {
                        val calendar = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        viewModel.scheduleMessage(updatedRecipient, messageText, calendar.timeInMillis) {
                            Toast.makeText(context, "Scheduled for tomorrow at 9:00 AM", Toast.LENGTH_SHORT).show()
                            messageText = ""
                            chatDraftPrefs.edit().remove("draft_${recipient.uid}").apply()
                        }
                        showScheduleDialog = false
                    }) { Text("Tomorrow at 9:00 AM") }
                }
            },
            dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text("Cancel") } }
        )
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
private fun RecipientUploadIndicator(name: String, state: ActiveUploadState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .94f),
        tonalElevation = 3.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(19.dp))
                Text("${name.ifBlank { "Someone" }} is uploading ${state.fileName.ifBlank { "a file" }}", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text("${state.percent}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            LinearProgressIndicator(progress = { (state.percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(
                if (state.etaSeconds > 0L) "About ${state.etaSeconds}s remaining" else "Preparing upload…",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f)
            )
        }
    }
}

@Composable
private fun VoiceRecordingIndicator(name: String) {
    val motion = rememberInfiniteTransition(label = "voice_recording_indicator")
    val pulse by motion.animateFloat(
        initialValue = .88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(680, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "voice_pulse"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .92f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size((20.dp * pulse)))
            Column {
                Text("${name.ifBlank { "Someone" }} is recording a voice note", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("You’ll hear it when it’s sent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .78f))
            }
        }
    }
}

@Composable
private fun TypingGlassIndicator(name: String) {
    val motion = rememberInfiniteTransition(label = "typing_indicator")
    val pulse by motion.animateFloat(
        initialValue = .72f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(760, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "typing_pulse"
    )
    val dotScales = listOf(0, 130, 260).mapIndexed { index, delay ->
        motion.animateFloat(
            initialValue = .55f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(tween(520, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "typing_dot_$index"
        )
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) .88f else .96f),
        tonalElevation = 5.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .14f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).graphicsLayer(scaleX = pulse, scaleY = pulse)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Someone" }, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("is composing a message", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                dotScales.forEach { scale ->
                    Box(Modifier.size(5.dp).graphicsLayer(scaleX = scale.value, scaleY = scale.value).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(34.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    dotScales.forEachIndexed { index, scale ->
                        Box(Modifier.width(2.dp).height((5 + (scale.value * 5).toInt()).coerceAtMost(10).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .75f), RoundedCornerShape(2.dp)))
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
    onEditSelect: () -> Unit,
    onDeleteSelect: () -> Unit,
    onReactSelect: (String) -> Unit,
    deliveryState: String = "sent",
    onRetry: () -> Unit = {},
    onVoicePlayed: () -> Unit,
    onFileConsumed: () -> Unit,
    onForward: () -> Unit,
    onTranslate: (Message) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedDrag by animateFloatAsState(dragOffset, spring(stiffness = Spring.StiffnessMedium), label = "swipe_reply")
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
                            clipboard.setPrimaryClip(ClipData.newPlainText("Convo Chat message", message.text))
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

                    if (!message.fileUrl.isNullOrBlank()) {
                        GenericFileBubble(message, onFileConsumed)
                        Spacer(Modifier.height(5.dp))
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
                                text = if (message.editHistory.isNotEmpty()) "Edited (${message.editHistory.size})  " else "Edited  ",
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
                            when (deliveryState) {
                                "pending" -> CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                "failed" -> IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = "Retry sending",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                else -> Icon(
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
                        clipboard.setPrimaryClip(ClipData.newPlainText("Convo Chat message", message.text))
                        showMenu = false
                    }
                )
            }
            if (message.text.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Translate to Bengali (AI)") },
                    leadingIcon = { Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        showMenu = false
                        onTranslate(message)
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

@Composable
private fun PdfFirstPage(uri: Uri) {
    val context = LocalContext.current
    var fullPreview by remember(uri) { mutableStateOf(false) }
    val preview by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(480, (480f * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }
    preview?.let { bitmap -> androidx.compose.foundation.Image(bitmap.asImageBitmap(), "PDF first page preview", Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)).clickable { fullPreview = true }, contentScale = ContentScale.Fit) }
    if(fullPreview) Dialog(onDismissRequest={fullPreview=false},properties=DialogProperties(usePlatformDefaultWidth=false)) { Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){ preview?.let{androidx.compose.foundation.Image(it.asImageBitmap(),"PDF full preview",Modifier.fillMaxSize().padding(12.dp),contentScale=ContentScale.Fit)};IconButton(onClick={fullPreview=false},modifier=Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color.Black.copy(.5f),CircleShape)){Icon(Icons.Default.Close,"Close",tint=Color.White)} } }
}

@Composable
private fun InlineChatVideo(url: String, name: String) {
    val context = LocalContext.current
    var playing by remember(url) { mutableStateOf(false) }
    var fullscreen by remember(url) { mutableStateOf(false) }
    fun saveVideo() {
        if (!url.startsWith("http")) return
        val safe = name.ifBlank { "convo_video_${System.currentTimeMillis()}.mp4" }.replace(Regex("[\\/:*?\"<>|]"), "_")
        val request = DownloadManager.Request(Uri.parse(url)).setTitle(safe).setMimeType("video/*").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Convo_$safe")
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(context, "Video is saving to Downloads", Toast.LENGTH_LONG).show()
    }
    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { ctx -> android.widget.VideoView(ctx).apply { val controls=android.widget.MediaController(ctx);controls.setAnchorView(this);setMediaController(controls);setVideoURI(Uri.parse(url));setOnPreparedListener { if(playing)start() };setOnCompletionListener{playing=false} } }, update = { if(playing&&!it.isPlaying)it.start() else if(!playing&&it.isPlaying)it.pause() }, modifier=Modifier.fillMaxSize())
        if (!playing) FilledIconButton(onClick={playing=true},modifier=Modifier.size(58.dp)){Icon(Icons.Default.PlayArrow,"Play video",Modifier.size(32.dp))}
        Row(Modifier.align(Alignment.TopEnd).padding(7.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            IconButton(onClick={fullscreen=true},modifier=Modifier.background(Color.Black.copy(.55f),CircleShape)){Icon(Icons.Default.Fullscreen,"Full screen",tint=Color.White)}
            IconButton(onClick=::saveVideo,modifier=Modifier.background(Color.Black.copy(.55f),CircleShape)){Icon(Icons.Default.Download,"Save video",tint=Color.White)}
        }
        Text(name,color=Color.White,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).background(Color.Black.copy(.55f)).padding(7.dp))
    }
    if(fullscreen) Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory={ctx->android.widget.VideoView(ctx).apply{val controls=android.widget.MediaController(ctx);controls.setAnchorView(this);setMediaController(controls);setVideoURI(Uri.parse(url));setOnPreparedListener{start()}}},modifier=Modifier.fillMaxSize())
            IconButton(onClick={fullscreen=false},modifier=Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(14.dp).background(Color.Black.copy(.5f),CircleShape)){Icon(Icons.Default.Close,"Close",tint=Color.White)}
            IconButton(onClick=::saveVideo,modifier=Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(18.dp).background(MaterialTheme.colorScheme.primary,CircleShape)){Icon(Icons.Default.Download,"Save",tint=MaterialTheme.colorScheme.onPrimary)}
        }
    }
}

@Composable
private fun GenericFileBubble(message: Message, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val mime = message.fileMimeType ?: "application/octet-stream"
    val name = message.fileName ?: "Attachment"
    if (mime.startsWith("video/")) {
        InlineChatVideo(message.fileUrl.orEmpty(), name)
        return
    }
    if (mime.startsWith("audio/")) {
        VoicePlayerBubble(message.fileUrl.orEmpty(), 0, onPlaybackStarted = onConsumed)
        return
    }
    var downloadId by remember(message.messageId) { mutableLongStateOf(0L) }
    var progress by remember(message.messageId) { mutableIntStateOf(0) }
    var localUri by remember(message.messageId) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(downloadId) {
        if (downloadId <= 0) return@LaunchedEffect
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (localUri == null) {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (total > 0) progress = (done * 100 / total).toInt()
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> { localUri = manager.getUriForDownloadedFile(downloadId); onConsumed() }
                        DownloadManager.STATUS_FAILED -> return@LaunchedEffect
                    }
                }
            }
            delay(500)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface.copy(.55f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (mime == "application/pdf" && localUri != null) PdfFirstPage(localUri!!)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (mime == "application/pdf") Icons.Default.PictureAsPdf else if (name.endsWith(".apk", true)) Icons.Default.Android else Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (mime == "application/pdf") "PDF • preview after download" else mime.substringAfter('/').uppercase(), fontSize = 10.sp)
                }
            }
            if (downloadId > 0 && localUri == null) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Saving to Downloads • $progress%", fontSize = 10.sp)
            } else Button(onClick = {
                localUri?.let { uri ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                } ?: run {
                    val source = message.remoteFileUrl ?: message.fileUrl
                    if (source?.startsWith("http") == true) {
                        val safeName = name.replace(Regex("[\\/:*?\"<>|]"), "_")
                        val request = DownloadManager.Request(Uri.parse(source)).setTitle(name).setMimeType(mime)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Convo Chat_$safeName")
                        downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (localUri == null) Icons.Default.Download else Icons.Default.OpenInNew, null)
                Spacer(Modifier.width(6.dp)); Text(if (localUri == null) "Download / Save" else "Open ${if (mime == "application/pdf") "PDF" else "file"}")
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
                                .setTitle("Convo Chat image")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Convo Chat_${System.currentTimeMillis()}.jpg")
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

private data class ChatLinkPreview(
    val title: String,
    val description: String,
    val image: String,
    val site: String
)

@Composable
private fun rememberChatLinkPreview(url: String): ChatLinkPreview? {
    val preview by produceState<ChatLinkPreview?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = "https://solitary-hill-dcdc.mr44253990.workers.dev/link-preview?url=${Uri.encode(url)}"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/json")
                }
                connection.inputStream.bufferedReader().use { reader ->
                    val json = JSONObject(reader.readText())
                    ChatLinkPreview(
                        title = json.optString("title", Uri.parse(url).host ?: "Shared link"),
                        description = json.optString("description", "Open link"),
                        image = json.optString("image", ""),
                        site = json.optString("site", Uri.parse(url).host ?: "")
                    )
                }.also { connection.disconnect() }
            }.getOrNull()
        }
    }
    return preview
}

@Composable
internal fun LinkPreviewCard(url: String, color: Color) {
    val uriHandler = LocalUriHandler.current
    val fallbackHost = remember(url) { runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("") }
    val preview = rememberChatLinkPreview(url)
    val title = preview?.title?.ifBlank { fallbackHost.ifBlank { "Shared link" } } ?: fallbackHost.ifBlank { "Shared link" }
    val description = preview?.description?.ifBlank { "Tap to open" } ?: "Loading preview…"
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).clickable { runCatching { uriHandler.openUri(url) } },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .38f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .14f))
    ) {
        Column {
            if (!preview?.image.isNullOrBlank()) {
                AsyncImage(model = preview?.image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp))
            }
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, tint = Color(0xFF71C7FF), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(description, fontSize = 11.sp, color = color.copy(alpha = .78f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(preview?.site?.ifBlank { fallbackHost } ?: fallbackHost, fontSize = 9.sp, color = color.copy(alpha = .58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(url, fontSize = 8.sp, color = color.copy(alpha = .42f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.OpenInNew, "Open link", tint = Color(0xFF71C7FF), modifier = Modifier.size(17.dp))
            }
        }
    }
}

private fun cleanChatUrl(raw: String): String = raw.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')

@Composable
private fun LinkifiedChatText(text: String, color: Color) {
    val uriHandler = LocalUriHandler.current
    val urlRegex = remember { Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE) }
    val firstUrl = remember(text) { urlRegex.find(text)?.value?.let(::cleanChatUrl) }
    val annotated = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            urlRegex.findAll(text).forEach { match ->
                val cleanUrl = cleanChatUrl(match.value)
                val suffixStart = match.range.first + cleanUrl.length
                append(text.substring(cursor, match.range.first))
                pushStringAnnotation("URL", cleanUrl)
                pushStyle(SpanStyle(color = Color(0xFF71C7FF), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                append(cleanUrl)
                pop(); pop()
                if (suffixStart <= match.range.last) append(text.substring(suffixStart, match.range.last + 1))
                cursor = match.range.last + 1
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
        firstUrl?.takeIf { it.isNotBlank() }?.let { url -> LinkPreviewCard(url, color) }
    }
}
