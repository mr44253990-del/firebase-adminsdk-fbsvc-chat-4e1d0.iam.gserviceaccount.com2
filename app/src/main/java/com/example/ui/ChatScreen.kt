package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.TestTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Message
import com.example.data.User
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    recipient: User,
    onBack: () -> Unit
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messages by viewModel.chatMessagesState.collectAsState()
    val listState = rememberLazyListState()
    
    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var showMessageMenu by remember { mutableStateOf<Message?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    
    // Swipe to reply state
    var swipeState by remember { mutableStateOf<SwipeState?>(null) }

    // Initialize chat listener
    LaunchedEffect(recipient.uid) {
        viewModel.startListeningToChat(recipient.uid)
    }

    DisposableEffect(recipient.uid) {
        onDispose {
            viewModel.stopListeningToChat()
        }
    }

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            DarkBackground,
            DarkSurface.copy(alpha = 0.95f)
        )
    )

    Scaffold(
        topBar = {
            ChatTopBar(
                recipient = recipient,
                onBack = onBack,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedMessages.size,
                onClearSelection = {
                    isSelectionMode = false
                    selectedMessages = emptySet()
                },
                onForwardSelected = { showForwardDialog = true }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBg)
                .padding(innerPadding)
        ) {
            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = messages,
                    key = { it.messageId }
                ) { message ->
                    val isSentByMe = message.senderId == currentUserId
                    val isSelected = selectedMessages.contains(message.messageId)
                    
                    MessageBubble(
                        message = message,
                        isSentByMe = isSentByMe,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onLongPress = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedMessages = setOf(message.messageId)
                            }
                        },
                        onTap = {
                            if (isSelectionMode) {
                                selectedMessages = if (isSelected) {
                                    selectedMessages - message.messageId
                                } else {
                                    selectedMessages + message.messageId
                                }
                                if (selectedMessages.isEmpty()) {
                                    isSelectionMode = false
                                }
                            }
                        },
                        onReply = {
                            replyingTo = message
                        },
                        onEdit = {
                            editingMessage = message
                            messageText = message.text
                        },
                        onDelete = {
                            viewModel.deleteMessage(message.chatId, message.messageId)
                        },
                        onForward = {
                            showForwardDialog = true
                        },
                        onCopy = {
                            // Copy to clipboard
                        }
                    )
                }
            }

            // Reply Preview
            AnimatedVisibility(
                visible = replyingTo != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                replyingTo?.let { replyMessage ->
                    ReplyPreview(
                        message = replyMessage,
                        isSentByMe = replyMessage.senderId == currentUserId,
                        onDismiss = { replyingTo = null }
                    )
                }
            }

            // Message Input Bar
            MessageInputBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (editingMessage != null) {
                        viewModel.editMessage(editingMessage!!.chatId, editingMessage!!.messageId, messageText)
                        editingMessage = null
                    } else if (replyingTo != null) {
                        viewModel.sendMessage(
                            recipient = recipient,
                            text = messageText,
                            replyToMessageId = replyingTo!!.messageId,
                            replyToText = replyingTo!!.text,
                            replyToSenderName = replyingTo!!.senderName
                        )
                        replyingTo = null
                    } else {
                        viewModel.sendMessage(recipient = recipient, text = messageText)
                    }
                    messageText = ""
                },
                onAttach = { /* Show attachment options */ },
                isEditing = editingMessage != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }

    // Forward Dialog
    if (showForwardDialog) {
        ForwardDialog(
            selectedMessages = selectedMessages,
            onDismiss = {
                showForwardDialog = false
                isSelectionMode = false
                selectedMessages = emptySet()
            },
            onForward = { user ->
                messages.filter { selectedMessages.contains(it.messageId) }.forEach { msg ->
                    viewModel.sendMessage(
                        recipient = user,
                        text = msg.text,
                        replyToMessageId = msg.replyToMessageId,
                        replyToText = msg.replyToText,
                        replyToSenderName = msg.replyToSenderName
                    )
                }
                showForwardDialog = false
                isSelectionMode = false
                selectedMessages = emptySet()
            },
            users = viewModel.filteredUsersState.collectAsState().value
        )
    }
}

@Composable
private fun ChatTopBar(
    recipient: User,
    onBack: () -> Unit,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onForwardSelected: () -> Unit
) {
    Surface(
        color = DarkSurface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (isSelectionMode) onClearSelection() else onBack()
            }) {
                Icon(
                    imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            if (isSelectionMode) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = onForwardSelected) {
                    Icon(
                        imageVector = Icons.Default.Forward,
                        contentDescription = "Forward",
                        tint = ElectricPurple
                    )
                }
            } else {
                // Avatar with online indicator
                Box {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(GradientPurpleCyan)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (recipient.profileImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = recipient.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = recipient.name.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (recipient.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(OnlineGreen)
                                .border(2.dp, DarkSurface, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipient.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = if (recipient.isOnline) "Online" else "@${recipient.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recipient.isOnline) OnlineGreen else Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = { /* Video call */ }) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = "Video Call",
                        tint = CyanGlow
                    )
                }
                
                IconButton(onClick = { /* Voice call */ }) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Voice Call",
                        tint = MintGreen
                    )
                }
                
                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isSentByMe: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "bubbleScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onTap() }
                )
            },
        horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
    ) {
        // Selection indicator
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) ElectricPurple else Color.White.copy(alpha = 0.2f)
                    )
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Reply indicator
        if (message.replyToMessageId.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isSentByMe) ElectricPurple.copy(alpha = 0.2f) else DarkSurface.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .background(if (isSentByMe) ElectricPurple else CyanGlow)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = message.replyToSenderName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSentByMe) ElectricPurple else CyanGlow
                        )
                        Text(
                            text = message.replyToText.take(50),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Main bubble
        Box {
            Surface(
                shape = if (isSentByMe) {
                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                } else {
                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                },
                color = if (isSentByMe) ElectricPurple else DarkSurface.copy(alpha = 0.8f),
                border = if (isSelected) BorderStroke(2.dp, CyanGlow) else null,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Sender name (only for received messages)
                    if (!isSentByMe) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyanGlow
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Message content
                    if (message.isDeleted) {
                        Text(
                            text = "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    } else {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    // Image if present
                    if (message.imageUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time and status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "edited • ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = formatTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        
                        if (isSentByMe && !message.isDeleted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.isEdited) Icons.Default.Edit else Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = if (message.isEdited) Color.White.copy(alpha = 0.5f) else CyanGlow,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Message menu button
            if (!isSelectionMode && !message.isDeleted) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .align(if (isSentByMe) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = if (isSentByMe) (-8).dp else (8).dp)
                        .size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Menu",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        onClick = {
                            showMenu = false
                            onReply()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Reply, null, tint = ElectricPurple)
                        }
                    )
                    
                    if (isSentByMe) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, null, tint = CyanGlow)
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("Delete", color = CoralRed) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = CoralRed)
                            }
                        )
                    }
                    
                    DropdownMenuItem(
                        text = { Text("Forward") },
                        onClick = {
                            showMenu = false
                            onForward()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Forward, null, tint = MintGreen)
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            showMenu = false
                            onCopy()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null, tint = Color.White)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyPreview(
    message: Message,
    isSentByMe: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        color = DarkSurface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(if (isSentByMe) ElectricPurple else CyanGlow)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSentByMe) "You" else message.senderName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSentByMe) ElectricPurple else CyanGlow
                )
                Text(
                    text = message.text.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    isEditing: Boolean,
    modifier: Modifier = Modifier
) {
    val sendButtonScale by animateFloatAsState(
        targetValue = if (text.isNotBlank()) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "sendScale"
    )

    Surface(
        modifier = modifier,
        color = DarkSurface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Editing indicator
            AnimatedVisibility(
                visible = isEditing,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                Surface(
                    color = GoldenYellow.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = GoldenYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Editing message",
                            color = GoldenYellow,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                // Attachment button
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Text field
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(ElectricPurple),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = if (isEditing) "Edit message..." else "Write a message...",
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            maxLines = 4
                        )

                        // Emoji button
                        IconButton(onClick = { /* Open emoji picker */ }) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "Emoji",
                                tint = GoldenYellow
                            )
                        }

                        // Image button
                        IconButton(onClick = onAttach) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Image",
                                tint = MintGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send/Edit button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(sendButtonScale)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) {
                                Brush.linearGradient(GradientPurpleCyan)
                            } else {
                                Brush.linearGradient(listOf(Color.Gray, Color.Gray))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) onSend()
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Send,
                            contentDescription = if (isEditing) "Save" else "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardDialog(
    selectedMessages: Set<String>,
    onDismiss: () -> Unit,
    onForward: (User) -> Unit,
    users: List<User>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Forward to", fontWeight = FontWeight.Bold)
        },
        text = {
            if (users.isEmpty()) {
                Text("No users to forward to")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(users) { user ->
                        Surface(
                            onClick = { onForward(user) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(GradientPurpleCyan)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.name.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text(
                                        text = user.name,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricPurple
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
