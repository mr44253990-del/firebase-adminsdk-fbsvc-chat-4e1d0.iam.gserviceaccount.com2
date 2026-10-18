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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.example.security.PrivacyPreferences
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    recipient: User,
    onBack: () -> Unit,
    onProfile: () -> Unit = {},
    onCall: () -> Unit = {},
    onVideoCall: () -> Unit = {},
    onOpenUserProfile: (User) -> Unit = {}
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
    val chatLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(chatLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.stopTypingLoop()
        }
        chatLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { chatLifecycleOwner.lifecycle.removeObserver(observer); viewModel.stopTypingLoop() }
    }
    val notificationSounds by viewModel.notificationSoundsEnabled.collectAsState()
    val privacySettings by PrivacyPreferences.settings.collectAsState()
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
    var voiceCancelArmed by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ChatPreferences.load(context) }
    val personalQuickReaction by ChatPreferences.quickReaction.collectAsState()
    val sharedQuickReaction by viewModel.chatQuickReaction.collectAsState()
    val chatReactions by viewModel.chatReactions.collectAsState()
    val allOutgoingUploads by viewModel.outgoingUploads.collectAsState()
    val uploadsChatId = remember(currentUserId, recipient.uid) { listOf(currentUserId, recipient.uid).sorted().joinToString("_") }
    val chatUploads = remember(allOutgoingUploads, uploadsChatId) { allOutgoingUploads.filter { it.chatId == uploadsChatId } }
    LaunchedEffect(chatUploads.size) { if (chatUploads.isNotEmpty()) runCatching { listState.animateScrollToItem(0) } }
    // The conversation emoji (set by either person) wins over the personal default.
    val quickReaction = sharedQuickReaction ?: personalQuickReaction
    val chatTextScale by ChatPreferences.textScale.collectAsState()
    val chatAppearance by ChatPreferences.appearance.collectAsState()
    // New messages animate in once; history and scroll-back stay still.
    val chatOpenedAt = remember { System.currentTimeMillis() }
    val enteredMessageIds = remember { HashSet<String>() }
    val mentionUsers by viewModel.usersState.collectAsState()
    val chatBackgroundImages by viewModel.chatBackgroundImages.collectAsState()
    val openMention: (String) -> Unit = { handle ->
        val target = Mentions.findUser(handle, mentionUsers + listOfNotNull(currentUser))
        if (target != null) onOpenUserProfile(target)
        else Toast.makeText(context, "@$handle was not found", Toast.LENGTH_SHORT).show()
    }

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

    // Initialize/start listening. Attach/detach is reference counted in the ViewModel so an old
    // chat screen that is disposed after a new one was composed can no longer kill the live
    // listener (the cause of "new messages sometimes don't appear while I'm in the chat").
    val chatScreenLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var chatResumed by remember { mutableStateOf(true) }
    DisposableEffect(recipient.uid, chatScreenLifecycle) {
        viewModel.attachChatScreen(recipient.uid)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { chatResumed = true; viewModel.ensureChatListening(recipient.uid) }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> chatResumed = false
                else -> Unit
            }
        }
        chatScreenLifecycle.lifecycle.addObserver(observer)
        onDispose {
            chatScreenLifecycle.lifecycle.removeObserver(observer)
            viewModel.setTypingState(false)
            viewModel.setVoiceRecordingState(false)
            viewModel.detachChatScreen(recipient.uid)
            mediaRecorder?.release()
        }
    }

    LaunchedEffect(recipient.uid, privacySettings.showTypingStatus) {
        viewModel.refreshTypingStatus(recipient.uid)
    }

    // Typing indicator: shown for as long as there is unsent text in the composer — even if the
    // user stops typing and just sits there — until the message is sent or the text is cleared.
    // The RTDB lease is refreshed every 8 s; leaving the screen/app clears it (onDisconnect too).
    val composerHasText = messageText.isNotBlank() && editingMessage == null
    LaunchedEffect(recipient.uid, composerHasText, chatResumed) {
        if (!composerHasText || !chatResumed) {
            viewModel.setTypingState(false)
        } else {
            try {
                while (true) {
                    viewModel.setTypingState(true)
                    delay(8_000L)
                }
            } finally {
                viewModel.setTypingState(false)
            }
        }
    }

    com.example.service.TrackOpenChat(peerUid = recipient.uid)

    // Keep the newest message visible: keyed on the newest id (not the list size, which can stay
    // the same when one message arrives while another expires/merges).
    val newestMessageId = messages.maxByOrNull { it.timestamp }?.messageId
    LaunchedEffect(newestMessageId) {
        if (messages.isNotEmpty()) {
            val newest = messages.maxByOrNull { it.timestamp }
            val nearBottom = listState.firstVisibleItemIndex <= 3
            if (nearBottom || newest?.senderId == currentUserId) listState.animateScrollToItem(0)
        }
    }

    // Play a full-screen reaction effect when the partner sends a fresh quick reaction / emoji.
    var lastFxMessageId by remember { mutableStateOf("") }
    LaunchedEffect(messages.size) {
        val newest = messages.maxByOrNull { it.timestamp } ?: return@LaunchedEffect
        if (newest.messageId != lastFxMessageId) {
            val firstLoad = lastFxMessageId.isEmpty()
            lastFxMessageId = newest.messageId
            if (!firstLoad && newest.senderId != currentUserId && chatAppearance.animateReactions &&
                System.currentTimeMillis() - newest.timestamp < 15_000L && newest.imageUrl.isNullOrBlank() &&
                isEmojiOnlyMessage(newest.text) && newest.text.length <= 12) {
                ReactionFx.fire(newest.text.trim())
            }
        }
    }

    // Photo Gallery picker for attachment uploading to Supabase
    // Photos appear in the chat immediately as an uploading bubble (progress + time left).
    val uploadChatPhoto: (ByteArray, String?) -> Unit = { bytes, previewUri ->
        val reply = replyingToMessage
        replyingToMessage = null
        viewModel.sendTrackedUpload(
            kind = "photo",
            label = "Photo",
            bucket = "chat_images",
            fileName = "chat_img_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg",
            bytes = bytes,
            contentType = "image/jpeg",
            preview = previewUri
        ) { publicUrl ->
            viewModel.sendMessage(
                recipientUser = recipient,
                text = "",
                imageUrl = publicUrl,
                replyToId = reply?.messageId,
                replyToText = reply?.text,
                replyToSenderName = reply?.senderName
            )
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    // Album send: pick up to 10 photos, preview, caption, then upload in order.
    var pendingImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightedMessageId) { if (highlightedMessageId != null) { kotlinx.coroutines.delay(1600); highlightedMessageId = null } }
    var albumProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val multiPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size > MAX_BATCH_IMAGES) Toast.makeText(context, "Only the first $MAX_BATCH_IMAGES photos will be sent", Toast.LENGTH_SHORT).show()
            pendingImages = uris.take(MAX_BATCH_IMAGES)
        }
    }
    val sendAlbum: (List<Uri>, String, Boolean) -> Unit = { images, caption, hd ->
        pendingImages = emptyList()
        val reply = replyingToMessage
        replyingToMessage = null
        uiScope.launch {
            var sent = 0
            images.forEachIndexed { index, uri ->
                albumProgress = (index + 1) to images.size
                val bytes = compressChatImage(context, uri, hd)
                if (bytes == null) return@forEachIndexed
                val url = viewModel.awaitTrackedUpload(
                    kind = "photo",
                    label = "Photo ${index + 1} of ${images.size}",
                    bucket = "chat_images",
                    fileName = "chat_img_${System.currentTimeMillis()}_${index}_${(1000..9999).random()}.jpg",
                    bytes = bytes,
                    contentType = "image/jpeg",
                    preview = uri.toString()
                ) { lateUrl -> viewModel.sendMessage(recipientUser = recipient, text = "", imageUrl = lateUrl) }
                if (url != null) {
                    viewModel.sendMessage(
                        recipientUser = recipient,
                        text = "",
                        imageUrl = url,
                        replyToId = if (index == 0) reply?.messageId else null,
                        replyToText = if (index == 0) reply?.text else null,
                        replyToSenderName = if (index == 0) reply?.senderName else null
                    )
                    sent++
                }
            }
            if (caption.isNotBlank() && sent > 0) viewModel.sendMessage(recipientUser = recipient, text = caption)
            albumProgress = null
            Toast.makeText(context, if (sent == images.size) "${if (sent == 1) "Photo" else "$sent photos"} sent" else "Sent $sent of ${images.size} photos", Toast.LENGTH_SHORT).show()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let { uploadChatPhoto(it, uri.toString()) }
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) uploadChatPhoto(bytes, uri.toString())
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
        // The file shows up in the chat right away as an uploading bubble; the composer stays usable.
        val upId = viewModel.startOutgoingUpload("file", fileName, fileSize)
        fun startFileUpload() {
            viewModel.setFileUploadState(fileName, 0, 0L, active = true)
            viewModel.uploadUriToSupabase(uri, fileName, mime, fileSize,
                onProgress = { percent, eta ->
                    viewModel.updateOutgoingUpload(upId, percent, eta)
                    viewModel.setFileUploadState(fileName, percent, eta, active = true)
                },
                onSuccess = { url ->
                    viewModel.setFileUploadState(fileName, 100, 0L, active = false)
                    val wanted = viewModel.isOutgoingUploadActive(upId)
                    viewModel.dismissOutgoingUpload(upId)
                    if (wanted) viewModel.sendMessage(recipient, "", fileUrl = url, fileName = fileName, fileMimeType = mime, fileSize = fileSize)
                },
                onFailure = { error ->
                    viewModel.setFileUploadState(fileName, 0, 0L, active = false)
                    viewModel.failOutgoingUpload(upId, error) { startFileUpload() }
                })
        }
        startFileUpload()
    }

    // ---- Location sharing (current + live) ----
    val locationScope = rememberCoroutineScope()
    var showLocationSheet by remember { mutableStateOf(false) }
    var locationLoading by remember { mutableStateOf(false) }
    var showLocationOffDialog by remember { mutableStateOf(false) }
    var pendingLiveMinutes by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingCurrent by remember { mutableStateOf(false) }
    val shareLocationNow: (Int?, String) -> Unit = { minutes, label ->
        if (!com.example.location.LocationShare.isLocationEnabled(context)) {
            showLocationOffDialog = true
        } else {
            locationLoading = true
            locationScope.launch {
                val fix = com.example.location.LocationShare.currentLocation(context)
                locationLoading = false
                if (fix == null) {
                    Toast.makeText(context, "Couldn't get your location. Move near a window or turn on high accuracy.", Toast.LENGTH_LONG).show()
                } else if (minutes == null) {
                    viewModel.sendMessage(recipient, com.example.location.LocationShare.currentMessage(fix))
                    showLocationSheet = false
                } else {
                    val me = currentUserId.orEmpty()
                    val shareId = java.util.UUID.randomUUID().toString().replace("-", "").take(20)
                    val until = System.currentTimeMillis() + minutes * 60_000L
                    val chatId = listOf(me, recipient.uid).sorted().joinToString("_")
                    com.example.location.LiveLocationService.start(context, shareId, me, recipient.uid, recipient.name, chatId, until, fix)
                    viewModel.sendMessage(recipient, com.example.location.LocationShare.liveMessage(fix, shareId, until, label))
                    showLocationSheet = false
                    Toast.makeText(context, "Live location shared for $label", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(context, "Location permission is needed to share your location", Toast.LENGTH_LONG).show()
        } else {
            pendingLiveMinutes?.let { (m, l) -> shareLocationNow(m, l) }
            if (pendingCurrent) shareLocationNow(null, "")
        }
        pendingLiveMinutes = null; pendingCurrent = false
    }
    val requestLocationThen: (Int?, String) -> Unit = { minutes, label ->
        if (com.example.location.LocationShare.hasPermission(context)) shareLocationNow(minutes, label)
        else {
            if (minutes == null) pendingCurrent = true else pendingLiveMinutes = minutes to label
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (minutes != null && android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            locationPermissionLauncher.launch(perms.toTypedArray())
        }
    }
    var showContactPicker by remember { mutableStateOf(false) }

    // Voice recording helpers
    val startRecording = {
        if (!isRecording && !isDictating) {
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
                } catch (e: Exception) {
                    runCatching { mediaRecorder?.release() }
                    mediaRecorder = null
                    isRecording = false
                    viewModel.setVoiceRecordingState(false)
                    voiceFile?.delete()
                    voiceFile = null
                    Log.e("VOICE_REC", "Failed to start media recorder", e)
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
        isRecording = false
        viewModel.setVoiceRecordingState(false)
        try {
            recorder?.stop()
            recorder?.release()
            val durationSec = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt().coerceAtLeast(1)
            val bytes = file?.takeIf { it.exists() && it.length() > 0L }?.readBytes()
            voiceFile = null
            file?.delete()
            if (bytes != null) {
                val fileName = "voice_${System.currentTimeMillis()}.m4a"
                val reply = replyingToMessage
                replyingToMessage = null
                viewModel.sendTrackedUpload(
                    kind = "voice",
                    label = "Voice message",
                    bucket = "voice_notes",
                    fileName = fileName,
                    bytes = bytes,
                    contentType = "audio/m4a",
                    durationSec = durationSec
                ) { publicUrl ->
                    viewModel.sendMessage(
                        recipientUser = recipient,
                        text = "",
                        voiceUrl = publicUrl,
                        voiceDurationSec = durationSec,
                        replyToId = reply?.messageId,
                        replyToText = reply?.text,
                        replyToSenderName = reply?.senderName
                    )
                }
            }
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            file?.delete()
            voiceFile = null
            Log.e("VOICE_REC", "Failed to stop recording safely", e)
            Toast.makeText(context, "Voice recording was cancelled", Toast.LENGTH_SHORT).show()
        }
        }
    }

    val cancelRecording = {
        val recorder = mediaRecorder
        val file = voiceFile
        mediaRecorder = null
        voiceFile = null
        isRecording = false
        viewModel.setVoiceRecordingState(false)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        file?.delete()
    }

    // Keep gesture callbacks fresh without restarting pointer input when recording state changes.
    // Restarting the detector during onLongPress can cancel the active gesture and leave the
    // recorder in an inconsistent state.
    val latestConversationAccepted = rememberUpdatedState(conversationAccepted)
    val latestRequestPending = rememberUpdatedState(requestPending)
    val latestFileUploading = rememberUpdatedState(fileUploading)
    val latestIsDictating = rememberUpdatedState(isDictating)
    val latestIsRecording = rememberUpdatedState(isRecording)
    val latestStartRecording = rememberUpdatedState(startRecording)
    val latestStopAndSendVoice = rememberUpdatedState(stopAndSendVoice)
    val latestCancelRecording = rememberUpdatedState(cancelRecording)

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Theme: built-in spec, admin photo background ("image:<url>"), or the adaptive default.
    val chatImageBackground = remember(chatTheme) { chatTheme.chatImageUrlOrNull() }
    val chatThemeSpec = remember(chatTheme) { ChatThemeCatalog.find(chatTheme)?.takeIf { !it.adaptive } }
    val barColors = chatBarColors(chatThemeSpec, chatImageBackground != null)
    val chatGradientBg = chatThemeSpec?.brush
        ?: Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary.copy(alpha = .12f), MaterialTheme.colorScheme.surfaceVariant))
    val composerAccent = chatThemeSpec?.accent ?: MaterialTheme.colorScheme.primary
    val bubbleAccent: Color? = chatThemeSpec?.accent ?: if (chatImageBackground != null) MaterialTheme.colorScheme.primary else null

    if (showThemePicker) {
        val premiumActive = currentUser?.isPremium == true && (currentUser?.premiumPlan == "lifetime" || (currentUser?.premiumUntil ?: 0L) > System.currentTimeMillis())
        ModalBottomSheet(
            onDismissRequest = { showThemePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            var pickerTab by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(if (pickerTab == 0) "Chat theme" else "Bubbles & spacing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (pickerTab == 0) "Both of you will see the theme you choose for this conversation." else "Only you see these bubble settings. They apply to every chat.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                TabRow(selectedTabIndex = pickerTab, containerColor = Color.Transparent, modifier = Modifier.clip(RoundedCornerShape(14.dp))) {
                    Tab(selected = pickerTab == 0, onClick = { pickerTab = 0 }, text = { Text(tr("Themes")) }, icon = { Icon(Icons.Default.Palette, null) })
                    Tab(selected = pickerTab == 1, onClick = { pickerTab = 1 }, text = { Text(tr("Bubbles")) }, icon = { Icon(Icons.Default.ChatBubble, null) })
                }
                Spacer(Modifier.height(12.dp))
                if (pickerTab == 1) {
                    ChatAppearanceEditor(
                        spec = chatThemeSpec,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
                    )
                } else
                ChatThemeGallery(
                    selected = chatTheme,
                    premiumActive = premiumActive,
                    images = chatBackgroundImages,
                    onSelect = { theme ->
                        viewModel.updateChatTheme(theme)
                        showThemePicker = false
                    },
                    onLocked = { Toast.makeText(context, "This theme is part of Convo Premium", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                )
            }
        }
    }

    if (translationMessage != null) {
        AlertDialog(
            onDismissRequest = { if (!translationLoading) translationMessage = null },
            title = { Text(tr("AI translation"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(translationMessage?.text.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    if (translationLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(tr("Translating to Bengali…"))
                        }
                    } else Text(translationText.ifBlank { "No translation returned." })
                }
            },
            confirmButton = { TextButton(enabled = !translationLoading, onClick = { translationMessage = null }) { Text(tr("Close")) } }
        )
    }

    var settingsImageUrl by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    if (showChatSettings) {
        val isBlocked = currentUser?.blockedUsers?.contains(recipient.uid) == true
        ConversationSettingsPage(
            recipient = updatedRecipient,
            messages = messages,
            myUid = currentUserId,
            chatTheme = chatTheme,
            headerBrush = chatGradientBg,
            isBlocked = isBlocked,
            typingSounds = typingSounds,
            onTypingSounds = { viewModel.updateSoundPreferences(notificationSounds, it) },
            onClose = { showChatSettings = false },
            onProfile = { showChatSettings = false; onProfile() },
            onAudioCall = {
                showChatSettings = false
                if (audioPermissionGranted) onCall() else { pendingCall = true; permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            },
            onVideoCall = {
                showChatSettings = false
                val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (audioPermissionGranted && cameraGranted) onVideoCall()
                else videoPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
            },
            onSearch = { showChatSettings = false; showMessageSearch = true },
            onTheme = { showChatSettings = false; showThemePicker = true },
            onBlockToggle = {
                if (isBlocked) viewModel.unblockUser(recipient.uid) { Toast.makeText(context, "User unblocked", Toast.LENGTH_SHORT).show() }
                else viewModel.blockUser(recipient.uid) { Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show(); onBack() }
                showChatSettings = false
            },
            onReport = { showReportDialog = true },
            onOpenImage = { settingsImageUrl = it }
        )
    }
    settingsImageUrl?.let { url -> FullScreenChatImage(url, onDismiss = { settingsImageUrl = null }) }
    if (showReportDialog) {
        val categories = listOf("Spam", "Harassment", "Fake account", "Inappropriate content", "Other")
        var category by remember { mutableStateOf(categories.first()) }
        var details by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!sending) showReportDialog = false },
            icon = { Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Report ${updatedRecipient.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRowCompat {
                        categories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                    }
                    OutlinedTextField(details, { details = it.take(500) }, label = { Text(tr("What happened? (optional)")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = !sending, onClick = {
                    sending = true
                    viewModel.submitUserReport(recipient.uid, category, details) { ok, msg ->
                        sending = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (ok) showReportDialog = false
                    }
                }) { Text(if (sending) "Sending…" else "Send report") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text(tr("Cancel")) } }
        )
    }

    Box(Modifier.fillMaxSize()) {
    ChatBackdrop(chatThemeSpec, chatImageBackground, chatGradientBg, chatAppearance.backgroundDim)
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
                                text = ConversationPrefs.version.collectAsState().value.let { ConversationPrefs.nickname(context, recipient.uid) }.ifBlank { updatedRecipient.name },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = barColors?.content ?: MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (updatedRecipient.isOnline) "Active now" else formatLastSeen(updatedRecipient.lastActive),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updatedRecipient.isOnline) Color(0xFF4CDB6A) else (barColors?.content?.copy(alpha = .72f) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                    ChatCallPill(
                        onVideo = {
                            val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (audioPermissionGranted && cameraGranted) onVideoCall()
                            else videoPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                        },
                        onAudio = {
                            if (audioPermissionGranted) onCall()
                            else { pendingCall = true; permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                    )
                    ChatThemeOrb(onClick = { showThemePicker = true }, accent = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { showChatSettings = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColors?.container ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    navigationIconContentColor = barColors?.content ?: MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = barColors?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = if (barColors != null) Modifier.drawBehind {
                    drawLine(barColors.border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                } else Modifier
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                            placeholder = { Text(tr("Search messages"), fontSize = 12.sp) },
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
                        Text(tr("No matching messages"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Try another keyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = chatAppearance.sidePadding.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(chatAppearance.messageSpacing.dp, Alignment.Bottom),
                    reverseLayout = true
                ) {
                    val lastSeenOwnId = visibleMessages.lastOrNull { it.senderId == currentUserId && it.seenByRecipient }?.messageId
                    val newestOwnId = visibleMessages.lastOrNull { it.senderId == currentUserId }?.messageId
                    // Uploading photos / voice / files sit at the bottom (reverse layout → first items).
                    items(chatUploads.reversed(), key = { it.id }) { up ->
                        OutgoingUploadBubble(
                            upload = up,
                            accent = bubbleAccent ?: MaterialTheme.colorScheme.primary,
                            onRetry = { viewModel.retryOutgoingUpload(up.id) },
                            onCancel = { viewModel.dismissOutgoingUpload(up.id) }
                        )
                    }
                    items(visibleMessages.reversed(), key = { it.messageId }) { rawMsg ->
                        val msg = chatReactions[rawMsg.messageId]?.let { live -> rawMsg.copy(reactions = rawMsg.reactions + live) } ?: rawMsg
                        val entranceAnimate = remember(msg.messageId) { msg.timestamp > chatOpenedAt - 1500L && enteredMessageIds.add(msg.messageId) }
                        val flash by animateFloatAsState(if (highlightedMessageId == msg.messageId) .22f else 0f, tween(450), label = "jump_flash")
                        Box(Modifier.fillMaxWidth().messageEntrance(chatAppearance.messageAnimation, entranceAnimate, msg.senderId == currentUserId).background(MaterialTheme.colorScheme.primary.copy(alpha = flash), RoundedCornerShape(16.dp)).padding(bottom = if (msg.messageId == lastSeenOwnId || msg.messageId == newestOwnId) 20.dp else 0.dp)) {
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
                                if (chatAppearance.animateReactions && !ConvoStickers.isSticker(reaction) && msg.reactions[currentUserId] != reaction) ReactionFx.fire(reaction)
                                viewModel.addReaction(recipient.uid, msg.messageId, reaction)
                            },
                            deliveryState = deliveryStates[msg.messageId] ?: if (msg.deliveredToRecipient) "delivered" else "sent",
                            onRetry = { viewModel.retryMessage(recipient, msg) },
                            onVoicePlayed = {
                                viewModel.acknowledgeVoicePlayed(msg.messageId, msg.remoteVoiceUrl)
                            },
                            onFileConsumed = { viewModel.acknowledgeFileConsumed(msg.messageId, msg.remoteFileUrl) },
                            onForward = { forwardingMessage = msg },
                            onJumpToReply = { targetId ->
                                val index = visibleMessages.reversed().indexOfFirst { it.messageId == targetId }
                                if (index >= 0) uiScope.launch { listState.animateScrollToItem(index + chatUploads.size); highlightedMessageId = targetId }
                                else Toast.makeText(context, "Original message is no longer loaded", Toast.LENGTH_SHORT).show()
                            },
                            bubbleAccent = bubbleAccent,
                            textScale = chatTextScale,
                            onMentionClick = openMention,
                            appearance = chatAppearance,
                            themeSpec = chatThemeSpec,
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
                        if (msg.messageId == newestOwnId) {
                            val liveState = deliveryStates[msg.messageId]
                            val statusLabel = when {
                                msg.seenByRecipient -> tr("Seen")
                                msg.deliveredToRecipient || liveState == "delivered" -> tr("Delivered · not seen yet")
                                liveState == "pending" -> tr("Sending…")
                                liveState == "failed" -> tr("Not sent — tap ! to retry")
                                else -> tr("Sent · waiting for") + " " + updatedRecipient.name.substringBefore(' ').ifBlank { tr("them") }
                            }
                            Text(
                                statusLabel,
                                fontSize = 10.sp,
                                color = if (chatThemeSpec?.isScene == true && !chatThemeSpec.light) Color.White.copy(alpha = .78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = if (msg.messageId == lastSeenOwnId) 24.dp else 6.dp).offset(y = 17.dp)
                            )
                        }
                        if (msg.messageId == lastSeenOwnId) {
                            SeenAvatar(
                                updatedRecipient.profileImageUrl, updatedRecipient.name,
                                Modifier.align(Alignment.BottomEnd).offset(x = 0.dp, y = 19.dp)
                            )
                        }
                        }
                    }
                }
                // Scrolled up reading history? Show a "new message" jump pill instead of yanking.
                val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
                if (showJump) {
                    Surface(
                        onClick = { uiScope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 8.dp
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Latest messages"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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

            albumProgress?.let { (done, total) ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("Sending photo $done of $total…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
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
                                Text(tr("Editing Message"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

            if (requestPending) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HourglassTop, null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(tr("Message request pending"), fontWeight = FontWeight.Bold)
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

            // Mention suggestions appear above the composer while typing "@name".
            val mentionQuery = remember(messageText) { Mentions.activeQuery(messageText) }
            val mentionSuggestions = remember(mentionQuery, mentionUsers) {
                mentionQuery?.let { Mentions.suggestions(it, mentionUsers, currentUserId) }.orEmpty()
            }
            MentionSuggestionBar(mentionSuggestions, onPick = { user -> messageText = Mentions.complete(messageText, user) })

            // Quick replies (saved on the server): type "/" and tap one to send it instantly.
            var showQuickManager by remember { mutableStateOf(false) }
            if (messageText.startsWith("/") && messageText.length <= 40 && editingMessage == null && conversationAccepted) {
                QuickReplyBar(
                    query = messageText,
                    accent = bubbleAccent ?: MaterialTheme.colorScheme.primary,
                    onSend = { reply ->
                        viewModel.sendMessage(recipientUser = recipient, text = reply)
                        messageText = ""
                    },
                    onManage = { showQuickManager = true }
                )
            }
            if (showQuickManager) QuickReplyManagerDialog(onDismiss = { showQuickManager = false })

            // Smart replies (on-device): one-tap answers to the latest incoming text.
            val newestIncoming = remember(newestMessageId) {
                messages.maxByOrNull { it.timestamp }?.takeIf { it.senderId != currentUserId && it.text.isNotBlank() && !ConvoStickers.isSticker(it.text) && it.text.length <= 300 }
            }
            var smartDismissedFor by remember(recipient.uid) { mutableStateOf<String?>(null) }
            val incoming = newestIncoming
            if (incoming != null && messageText.isBlank() && editingMessage == null && conversationAccepted && smartDismissedFor != incoming.messageId) {
                val smartChips = remember(incoming.messageId) { com.example.service.SmartReplies.suggest(incoming.text) }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    smartChips.forEach { chip ->
                        SuggestionChip(
                            onClick = {
                                viewModel.sendMessage(recipientUser = recipient, text = chip)
                                smartDismissedFor = incoming.messageId
                            },
                            label = { Text(chip, fontSize = 12.sp) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    IconButton(onClick = { smartDismissedFor = incoming.messageId }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Hide suggestions", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Messenger-style composer: attachments + hold-to-record mic on the left, the
            // text field (or live recording status) in the middle, and a quick reaction that
            // turns into Send as soon as something is typed.
            Surface(
                tonalElevation = 8.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .glassmorphic(
                        isDark = isDark,
                        backgroundColor = barColors?.container,
                        borderColor = barColors?.border,
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    )
            ) {
                val composerLocked = !conversationAccepted || requestPending || fileUploading || isDictating
                val hasText = messageText.isNotBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(visible = !isRecording) {
                        Box {
                            IconButton(onClick = {
                                if (!conversationAccepted) Toast.makeText(context, "Send a text request and wait for confirmation first", Toast.LENGTH_LONG).show()
                                else showAttachmentMenu = true
                            }, enabled = !requestPending && !fileUploading) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Attachments", tint = composerAccent, modifier = Modifier.size(27.dp))
                            }
                        }
                    }

                    // Hold to record; release or slide up to send, slide down to cancel.
                    AnimatedVisibility(visible = !hasText || isRecording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HoldToRecordButton(
                                isRecording = isRecording,
                                permissionGranted = audioPermissionGranted,
                                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onStart = {
                                    if (latestConversationAccepted.value && !latestRequestPending.value && !latestFileUploading.value && !latestIsDictating.value && !latestIsRecording.value) {
                                        latestStartRecording.value()
                                    } else if (!latestConversationAccepted.value) {
                                        Toast.makeText(context, "Voice messages unlock after the request is accepted", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSend = { if (latestIsRecording.value) latestStopAndSendVoice.value() },
                                onCancel = { if (latestIsRecording.value) latestCancelRecording.value() },
                                onHint = { Toast.makeText(context, "Hold to record • slide up to send, down to cancel", Toast.LENGTH_SHORT).show() },
                                onDragChange = { _, armed -> voiceCancelArmed = armed },
                                accent = composerAccent
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }

                    if (isRecording) {
                        VoiceRecordingPill(
                            startedAtMillis = recordStartTime,
                            cancelArmed = voiceCancelArmed,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            enabled = !requestPending && !fileUploading,
                            placeholder = { Text(if (requestPending) tr("Waiting for confirmation…") else tr("Type a message…"), color = (barColors?.content ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text_field"),
                            maxLines = 5,
                            shape = RoundedCornerShape(26.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = barColors?.content ?: MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = barColors?.content ?: MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = composerAccent.copy(alpha = .7f),
                                unfocusedBorderColor = barColors?.border ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                focusedContainerColor = barColors?.container ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                                unfocusedContainerColor = barColors?.container ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                cursorColor = composerAccent
                            )
                        )
                    }

                    if (!isRecording) {
                        Spacer(modifier = Modifier.width(6.dp))
                        if (hasText || editingMessage != null) {
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
                                    .background(Brush.linearGradient(listOf(composerAccent, composerAccent.copy(alpha = .78f))))
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = if (editingMessage != null) Icons.Default.Check else Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (composerAccent.luminance() > .6f) Color(0xFF1B1530) else Color.White
                                )
                            }
                        } else {
                            // Quick reaction: one tap sends it, long press changes it.
                            var showReactionPicker by remember { mutableStateOf(false) }
                            val reactionScale = remember { Animatable(1f) }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .graphicsLayer { scaleX = reactionScale.value; scaleY = reactionScale.value }
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            enabled = !composerLocked,
                                            onClick = {
                                                viewModel.playLikeSound()
                                                if (chatAppearance.animateReactions && !ConvoStickers.isSticker(quickReaction)) ReactionFx.fire(quickReaction)
                                                viewModel.sendMessage(
                                                    recipientUser = recipient,
                                                    text = quickReaction,
                                                    replyToId = replyingToMessage?.messageId,
                                                    replyToText = replyingToMessage?.text,
                                                    replyToSenderName = replyingToMessage?.senderName
                                                )
                                                replyingToMessage = null
                                                uiScope.launch {
                                                    reactionScale.animateTo(1.35f, tween(110))
                                                    reactionScale.animateTo(1f, spring(dampingRatio = .4f))
                                                }
                                            },
                                            onLongClick = { showReactionPicker = true }
                                        )
                                        .testTag("chat_quick_reaction_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (ConvoStickers.isSticker(quickReaction)) ConvoStickerView(quickReaction, 40.dp, animate = chatAppearance.animateReactions)
                                    else Text(quickReaction, fontSize = 27.sp)
                                }
                                DropdownMenu(showReactionPicker, { showReactionPicker = false }) {
                                    Text(tr("Chat emoji · changes for both of you"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                                    ChatPreferences.reactionChoices.chunked(4).forEach { row ->
                                        Row(Modifier.padding(horizontal = 8.dp)) {
                                            row.forEach { emoji ->
                                                Box(
                                                    Modifier.size(46.dp).clip(CircleShape)
                                                        .background(if (emoji == quickReaction) composerAccent.copy(alpha = .18f) else Color.Transparent)
                                                        .clickable {
                                                            viewModel.updateChatQuickReaction(emoji)
                                                            showReactionPicker = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) { Text(emoji, fontSize = 24.sp) }
                                            }
                                        }
                                    }
                                    Text(tr("Convo animated stickers"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                                    ConvoStickers.all.chunked(4).forEach { row ->
                                        Row(Modifier.padding(horizontal = 8.dp)) {
                                            row.forEach { sticker ->
                                                Box(
                                                    Modifier.size(46.dp).clip(CircleShape)
                                                        .background(if (sticker.token == quickReaction) composerAccent.copy(alpha = .18f) else Color.Transparent)
                                                        .clickable {
                                                            viewModel.updateChatQuickReaction(sticker.token)
                                                            showReactionPicker = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) { ConvoStickerView(sticker.token, 36.dp, animate = chatAppearance.animateReactions) }
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
    }
    ReactionFxOverlay()
    }

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text(tr("Schedule message")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Choose when to send this text message."), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }) { Text(tr("In 10 minutes")) }
                    TextButton(onClick = {
                        val at = System.currentTimeMillis() + 60 * 60 * 1000L
                        viewModel.scheduleMessage(updatedRecipient, messageText, at) {
                            Toast.makeText(context, "Scheduled for 1 hour from now", Toast.LENGTH_SHORT).show()
                            messageText = ""
                            chatDraftPrefs.edit().remove("draft_${recipient.uid}").apply()
                        }
                        showScheduleDialog = false
                    }) { Text(tr("In 1 hour")) }
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
                    }) { Text(tr("Tomorrow at 9:00 AM")) }
                }
            },
            dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text(tr("Cancel")) } }
        )
    }

    val locationAllowed by AppFeatureFlags.locationSharingEnabled.collectAsState()
    if (pendingImages.isNotEmpty()) {
        MultiImageSendSheet(pendingImages, updatedRecipient.name, onDismiss = { pendingImages = emptyList() }, onSend = sendAlbum)
    }
    if (showAttachmentMenu) {
        val attachActions = buildList {
            add(AttachAction("photo", "Gallery", Icons.Default.Image, listOf(Color(0xFF7C4DFF), Color(0xFFB388FF))))
            add(AttachAction("camera", "Camera", Icons.Default.PhotoCamera, listOf(Color(0xFFFF4081), Color(0xFFFF80AB))))
            add(AttachAction("file", "Document", Icons.Default.Description, listOf(Color(0xFF2979FF), Color(0xFF82B1FF))))
            add(AttachAction("audio", "Audio", Icons.Default.Headphones, listOf(Color(0xFFFF6D00), Color(0xFFFFAB40))))
            if (locationAllowed) add(AttachAction("location", "Location", Icons.Default.LocationOn, listOf(Color(0xFF00C853), Color(0xFF69F0AE))))
            add(AttachAction("contact", "Contact", Icons.Default.ContactPage, listOf(Color(0xFF00B8D4), Color(0xFF84FFFF))))
            add(AttachAction("schedule", "Schedule", Icons.Default.Schedule, listOf(Color(0xFF6200EA), Color(0xFF9575CD))))
            add(AttachAction("apk", "APK / app", Icons.Default.Android, listOf(Color(0xFF3DDC84), Color(0xFF1B8A4B))))
        }
        SmartAttachSheet(attachActions, onDismiss = { showAttachmentMenu = false }) { key ->
            showAttachmentMenu = false
            when (key) {
                "photo" -> multiPhotoLauncher.launch("image/*")
                "camera" -> runCatching {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                }.onFailure { Toast.makeText(context, "Camera unavailable: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
                "file" -> filePickerLauncher.launch(arrayOf("*/*"))
                "audio" -> filePickerLauncher.launch(arrayOf("audio/*"))
                "apk" -> filePickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                "location" -> showLocationSheet = true
                "contact" -> showContactPicker = true
                "schedule" -> {
                    if (messageText.isBlank()) Toast.makeText(context, "Write a message first, then schedule it", Toast.LENGTH_SHORT).show()
                    else showScheduleDialog = true
                }
            }
        }
    }
    if (showLocationSheet) {
        LocationShareSheet(
            recipientName = recipient.name, loading = locationLoading,
            onDismiss = { if (!locationLoading) showLocationSheet = false },
            onShareCurrent = { requestLocationThen(null, "") },
            onShareLive = { minutes, label -> requestLocationThen(minutes, label) }
        )
    }
    if (showLocationOffDialog) {
        AlertDialog(
            onDismissRequest = { showLocationOffDialog = false },
            icon = { Icon(Icons.Default.LocationOff, null) },
            title = { Text(tr("Turn on location")) },
            text = { Text("Your phone's location is off. Turn it on in Settings, then come back and share again.") },
            confirmButton = {
                Button(onClick = {
                    showLocationOffDialog = false
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }) { Text(tr("Open settings")) }
            },
            dismissButton = { TextButton(onClick = { showLocationOffDialog = false }) { Text(tr("Cancel")) } }
        )
    }
    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text(tr("Share a contact"), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(users.filter { it.uid != recipient.uid }, key = { it.uid }) { person ->
                        ListItem(
                            headlineContent = { Text(person.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("@${person.username}") },
                            leadingContent = { AsyncImage(person.profileImageUrl.ifBlank { null }, person.name, modifier = Modifier.size(40.dp).clip(CircleShape)) },
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                viewModel.sendMessage(recipient, "Contact: ${person.name} (@${person.username})\nhttps://solitary-hill-dcdc.mr44253990.workers.dev/profile?uid=${person.uid}")
                                showContactPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text(tr("Cancel")) } }
        )
    }

    forwardingMessage?.let { original ->
        ForwardMessageSheet(
            message = original,
            me = currentUser,
            users = users,
            onDismiss = { forwardingMessage = null },
            onSend = { targets ->
                targets.forEach { viewModel.forwardMessage(it, original) }
                Toast.makeText(context, if (targets.size == 1) "Forwarded to ${targets.first().name}" else "Forwarded to ${targets.size} friends", Toast.LENGTH_SHORT).show()
                forwardingMessage = null
            }
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
            Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                MicGlyph(MaterialTheme.colorScheme.error, size = 18.dp * pulse)
            }
            Column {
                Text("${name.ifBlank { "Someone" }} is recording a voice note", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(tr("You’ll hear it when it’s sent"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .78f))
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
                Text(tr("is composing a message"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onTranslate: (Message) -> Unit = {},
    onJumpToReply: (String) -> Unit = {},
    bubbleAccent: Color? = null,
    textScale: Float = 1f,
    onMentionClick: (String) -> Unit = {},
    appearance: ChatAppearance = ChatAppearance(),
    themeSpec: ChatThemeSpec? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var showSelectText by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedDrag by animateFloatAsState(dragOffset, spring(stiffness = Spring.StiffnessMedium), label = "swipe_reply")
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current

    val bubbleStyle = appearance.resolvedStyle(themeSpec)
    val palette = rememberBubblePalette(isSentByMe, themeSpec, bubbleAccent, bubbleStyle)
    val shape = chatBubbleShape(appearance, isSentByMe)
    val textColor = palette.text
    val maxBubbleWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * appearance.maxWidthFraction).dp
    val isStickerOnly = ConvoStickers.isSticker(message.text) && message.imageUrl.isNullOrBlank() && message.voiceUrl.isNullOrBlank() && message.fileUrl.isNullOrBlank()

    val timeString = remember(message.timestamp, appearance.use24h) { appearance.formatTime(message.timestamp) }
    if (showImageViewer && !message.imageUrl.isNullOrBlank()) {
        FullScreenChatImage(message.imageUrl, onDismiss = { showImageViewer = false })
    }
    if (showSelectText && message.text.isNotBlank()) {
        SelectableMessageDialog(message.text, "", onDismiss = { showSelectText = false })
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isSentByMe) Alignment.TopEnd else Alignment.TopStart) {
        Column(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
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
                        .clickable { message.replyToId?.let(onJumpToReply) }
                        .glassmorphic(isDark = isDark, backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .padding(6.dp)
                ) {
                    Column {
                        Text(
                            text = "Replied to ${message.replyToSenderName ?: "User"}",
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

            if (isStickerOnly) {
                ConvoStickerView(message.text, 92.dp, animate = appearance.animateReactions)
                val stickerMeta = if (themeSpec?.isScene == true && !themeSpec.light) Color.White.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f)
                // Stickers / quick likes show the same sent → delivered → seen ticks as text bubbles.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(timeString, fontSize = 9.sp, color = stickerMeta)
                    if (isSentByMe) {
                        Spacer(Modifier.width(4.dp))
                        val stickerState = if (message.deliveredToRecipient || message.seenByRecipient) "sent" else deliveryState
                        when (stickerState) {
                            "pending" -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = stickerMeta)
                            "failed" -> IconButton(onClick = onRetry, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = "Retry sending", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                            else -> MessageTicks(
                                level = when {
                                    message.seenByRecipient -> 2
                                    message.deliveredToRecipient || deliveryState == "delivered" -> 1
                                    else -> 0
                                },
                                tint = stickerMeta,
                                seenTint = Color(0xFF1E9BE9),
                                size = 14.dp
                            )
                        }
                    }
                }
            } else
            Box(
                modifier = Modifier.chatBubble(bubbleStyle, shape, palette, appearance)
            ) {
                Column(modifier = Modifier.padding(horizontal = appearance.innerPadding.dp, vertical = (appearance.innerPadding * .62f).dp)) {
                    if (!isSentByMe && message.senderName.isNotBlank()) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.label,
                            modifier = Modifier.padding(bottom = 1.dp)
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
                        VoicePlayerBubble(voiceUrl = message.voiceUrl, durationSec = message.voiceDurationSec ?: 0, onPlaybackStarted = onVoicePlayed, accent = if (isSentByMe) palette.text else palette.accent, contentColor = palette.text)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (!message.fileUrl.isNullOrBlank()) {
                        GenericFileBubble(message, onFileConsumed, palette.accent, palette.text)
                        Spacer(Modifier.height(5.dp))
                    }

                    // Render text message if present
                    val sharedLocation = remember(message.text) { com.example.location.LocationShare.parse(message.text) }
                    if (sharedLocation != null) {
                        LocationMessageCard(sharedLocation, isSentByMe, textColor, if (isSentByMe) textColor else palette.accent)
                    } else if (message.text.isNotBlank()) {
                        val onlyEmoji = remember(message.text) { isEmojiOnlyMessage(message.text) }
                        if (onlyEmoji) {
                            val freshEmoji = remember(message.messageId) { System.currentTimeMillis() - message.timestamp < 6_000L }
                            Text(message.text, fontSize = (34 * textScale).sp, lineHeight = (40 * textScale).sp, modifier = Modifier.jumboEmojiEntrance(message.messageId, freshEmoji && appearance.animateReactions))
                        } else {
                            RichMessageText(
                                text = message.text,
                                color = textColor,
                                onMentionClick = onMentionClick,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * textScale).sp, lineHeight = (20 * textScale).sp),
                                mentionColor = if (isSentByMe) textColor else palette.accent,
                                onPlainClick = { showMenu = !showMenu },
                                onLongPress = { showMenu = true },
                                onDoubleTap = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Convo Chat message", message.text))
                                    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
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
                                color = palette.meta
                            )
                        }
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = palette.meta,
                            textAlign = TextAlign.End
                        )
                        if (isSentByMe) {
                            Spacer(Modifier.width(4.dp))
                            // Receipts only move forward: once the peer confirmed delivery/seen,
                            // a stale local "pending"/"failed" state can never hide those ticks.
                            val effectiveDeliveryState = if (message.deliveredToRecipient || message.seenByRecipient) "sent" else deliveryState
                            when (effectiveDeliveryState) {
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
                                else -> MessageTicks(
                                    level = when {
                                        message.seenByRecipient -> 2
                                        message.deliveredToRecipient || deliveryState == "delivered" -> 1
                                        else -> 0
                                    },
                                    tint = palette.meta,
                                    seenTint = if (palette.text.luminance() > .6f) Color(0xFF9BE7FF) else Color(0xFF1E9BE9),
                                    size = 14.dp
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
                    message.reactions.values.groupingBy { it }.eachCount().forEach { (reaction, count) ->
                        Row(
                            modifier = Modifier
                                .offset(y = (-6).dp)
                                .clip(RoundedCornerShape(18.dp))
                                .glassmorphic(isDark = isDark, backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), borderColor = palette.accent.copy(alpha = .35f), shape = RoundedCornerShape(18.dp))
                                .clickable { onReactSelect(reaction) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ConvoStickers.isSticker(reaction)) ConvoStickerView(reaction, 22.dp, animate = appearance.animateReactions)
                            else Text(text = reaction, fontSize = 13.sp)
                            if (count > 1) Text(" $count", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
            Text(tr("Convo stickers"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            ConvoStickers.all.chunked(5).forEach { rowStickers ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowStickers.forEach { sticker ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable {
                                onReactSelect(sticker.token)
                                showMenu = false
                            },
                            contentAlignment = Alignment.Center
                        ) { ConvoStickerView(sticker.token, 36.dp, animate = appearance.animateReactions) }
                    }
                }
            }
            // Quick actions — one tap, big targets.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MenuQuickAction(Icons.Default.Reply, "Reply") { onReplySelect(); showMenu = false }
                if (message.text.isNotBlank()) {
                    MenuQuickAction(Icons.Default.ContentCopy, "Copy") {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Convo Chat message", message.text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    }
                }
                MenuQuickAction(Icons.AutoMirrored.Filled.Forward, "Forward") { showMenu = false; onForward() }
                if (isSentByMe && message.text.isNotBlank() && !isStickerOnly) {
                    MenuQuickAction(Icons.Default.Edit, "Edit") { onEditSelect(); showMenu = false }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            if (message.text.isNotBlank()) {
                if (!isStickerOnly) MessageSmartTextActions(message.text, onSelectText = { showSelectText = true }, onDone = { showMenu = false })
            }
            if (message.text.isNotBlank() && AppFeatureFlags.aiEnabled.collectAsState().value) {
                DropdownMenuItem(
                    text = { Text(tr("Translate to Bengali (AI)")) },
                    leadingIcon = { Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        showMenu = false
                        onTranslate(message)
                    }
                )
            }
            if (isSentByMe) {
                DropdownMenuItem(
                    text = { Text(tr("Delete Message"), color = MaterialTheme.colorScheme.error) },
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
private fun MenuQuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
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
private fun GenericFileBubble(message: Message, onConsumed: () -> Unit, accent: Color = MaterialTheme.colorScheme.primary, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    val context = LocalContext.current
    val mime = message.fileMimeType ?: "application/octet-stream"
    val name = message.fileName ?: "Attachment"
    if (mime.startsWith("video/")) {
        InlineChatVideo(message.fileUrl.orEmpty(), name)
        return
    }
    if (mime.startsWith("audio/")) {
        VoicePlayerBubble(message.remoteFileUrl ?: message.fileUrl.orEmpty(), 0, onPlaybackStarted = onConsumed, accent = accent, contentColor = contentColor, title = name)
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
    val isPdf = mime == "application/pdf"
    val isApk = name.endsWith(".apk", true)
    val extension = name.substringAfterLast('.', "").uppercase().take(4).ifBlank { mime.substringAfter('/').uppercase().take(4) }
    val tileColors = when {
        isPdf -> listOf(Color(0xFFFF6B6B), Color(0xFFD7263D))
        isApk -> listOf(Color(0xFF7BE495), Color(0xFF2EAD5B))
        mime.contains("zip") || mime.contains("rar") || mime.contains("7z") -> listOf(Color(0xFFFFC371), Color(0xFFFF8C42))
        mime.contains("sheet") || mime.contains("excel") || name.endsWith(".csv", true) -> listOf(Color(0xFF5EE7A0), Color(0xFF1E9E61))
        mime.contains("word") || mime.contains("document") -> listOf(Color(0xFF6CB4FF), Color(0xFF2B6BE0))
        else -> listOf(accent, accent.copy(alpha = .7f))
    }
    val sizeLabel = message.fileSize?.takeIf { it > 0 }?.let { bytes ->
        when {
            bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576f)
            bytes >= 1024L -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
    val onAccent = if (accent.luminance() > .6f) Color(0xFF1B1530) else Color.White
    Column(
        Modifier
            .widthIn(min = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(contentColor.copy(alpha = .08f))
            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (isPdf && localUri != null) PdfFirstPage(localUri!!)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(tileColors)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (isPdf) Icons.Default.PictureAsPdf else if (isApk) Icons.Default.Android else Icons.Default.Description, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(extension, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(sizeLabel, if (isPdf) "PDF · preview after download" else extension.ifBlank { "File" }).joinToString(" · "),
                    color = contentColor.copy(alpha = .68f), fontSize = 10.sp
                )
            }
        }
        if (downloadId > 0 && localUri == null) {
            LinearProgressIndicator(progress = { progress / 100f }, color = accent, trackColor = contentColor.copy(alpha = .15f), modifier = Modifier.fillMaxWidth().clip(CircleShape))
            Text("Saving to Downloads · $progress%", color = contentColor.copy(alpha = .75f), fontSize = 10.sp)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(accent, androidx.compose.ui.graphics.lerp(accent, Color(0xFF3D7BFF), .45f))))
                    .clickable {
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
                            } else {
                                Toast.makeText(context, "This file is already on your phone", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (localUri == null) Icons.Default.Download else Icons.Default.OpenInNew, null, tint = onAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (localUri == null) "Download / Save" else "Open ${if (isPdf) "PDF" else "file"}", color = onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
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
            Text(tr("Pinch to zoom • drag to move"), color = Color.White.copy(alpha = .65f), modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(18.dp))
        }
    }
}

@Composable
internal fun LinkPreviewCard(url: String, color: Color) {
    RichLinkPreviewCard(url = url, contentColor = color, compact = false)
}

/** True for short messages made only of emoji (shown large, without a bubble feel). */
internal fun isEmojiOnlyMessage(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 16) return false
    var i = 0
    var count = 0
    while (i < trimmed.length) {
        val cp = trimmed.codePointAt(i)
        val type = Character.getType(cp)
        val isEmojiLike = type == Character.OTHER_SYMBOL.toInt() || type == Character.SURROGATE.toInt() ||
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0x2600..0x27BF || cp == 0x2764
        if (!isEmojiLike && !Character.isWhitespace(cp)) return false
        if (type == Character.OTHER_SYMBOL.toInt()) count++
        i += Character.charCount(cp)
    }
    return count in 1..3
}
