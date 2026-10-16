package com.example.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.User

data class PostCanvasStyle(val id: String, val title: String, val colors: List<Color>)

private val postCanvasStyles = listOf(
    PostCanvasStyle("glass", "Liquid Glass", listOf(Color(0xFF28233F), Color(0xFF6750A4), Color(0xFF15233A))),
    PostCanvasStyle("sunset", "Sunset", listOf(Color(0xFFFF6B6B), Color(0xFFFFB347), Color(0xFF7A3152))),
    PostCanvasStyle("ocean", "Ocean", listOf(Color(0xFF005C97), Color(0xFF00A8CC), Color(0xFF002B5B))),
    PostCanvasStyle("aurora", "Aurora", listOf(Color(0xFF4A148C), Color(0xFF00BFA5), Color(0xFF311B92))),
    PostCanvasStyle("forest", "Forest", listOf(Color(0xFF0B3D2E), Color(0xFF2E8B57), Color(0xFF102A1E))),
    PostCanvasStyle("rose", "Rose", listOf(Color(0xFF8E2DE2), Color(0xFFFF5F6D), Color(0xFF3A1C71))),
    PostCanvasStyle("midnight", "Midnight", listOf(Color(0xFF020024), Color(0xFF090979), Color(0xFF111827))),
    PostCanvasStyle("mono", "Monochrome", listOf(Color(0xFF111111), Color(0xFF424242), Color(0xFF151515))),
    PostCanvasStyle("candy", "Candy", listOf(Color(0xFFFF9A9E), Color(0xFFF6A6C1), Color(0xFFA18CD1))),
    PostCanvasStyle("peach", "Peach", listOf(Color(0xFFFFB88C), Color(0xFFDE6262), Color(0xFF8E3B46))),
    PostCanvasStyle("fire", "Fire", listOf(Color(0xFFF12711), Color(0xFFF5AF19), Color(0xFF7A1F0A))),
    PostCanvasStyle("gold", "Gold", listOf(Color(0xFF8A6100), Color(0xFFE0A526), Color(0xFF4A3400))),
    PostCanvasStyle("lime", "Lime", listOf(Color(0xFF1E5631), Color(0xFF56AB2F), Color(0xFF0E3B1E))),
    PostCanvasStyle("mint", "Mint", listOf(Color(0xFF00B09B), Color(0xFF3FA36B), Color(0xFF0B5345))),
    PostCanvasStyle("sky", "Sky", listOf(Color(0xFF2193B0), Color(0xFF4FB3D9), Color(0xFF0F4C75))),
    PostCanvasStyle("lavender", "Lavender", listOf(Color(0xFF8E6CC9), Color(0xFF7D93D6), Color(0xFF4B3B7A))),
    PostCanvasStyle("berry", "Berry", listOf(Color(0xFFC33764), Color(0xFF1D2671), Color(0xFF4A1942))),
    PostCanvasStyle("galaxy", "Galaxy", listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))),
    PostCanvasStyle("coffee", "Coffee", listOf(Color(0xFF3E2723), Color(0xFF8D6E63), Color(0xFF1B0F0C))),
    PostCanvasStyle("neon", "Neon", listOf(Color(0xFF0B0612), Color(0xFFFF4DA6), Color(0xFF00B8D4)))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(viewModel: ChatViewModel, onBack: () -> Unit, onPublished: () -> Unit) {
    val context = LocalContext.current
    val storageRoutingConfig by viewModel.storageRoutingConfig.collectAsState()
    val initialMode = remember { viewModel.consumeComposerMode() }
    val externalShare = remember { viewModel.consumeExternalShare() }
    val users by viewModel.usersState.collectAsState()
    val draftPrefs = remember { context.getSharedPreferences("convo_post_draft", android.content.Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(draftPrefs.getString("text", "").orEmpty()) }
    var title by remember { mutableStateOf(draftPrefs.getString("title", "").orEmpty()) }
    var tags by remember { mutableStateOf(draftPrefs.getString("tags", "").orEmpty()) }
    var tagQuery by remember { mutableStateOf("") }
    val taggedIds = remember { mutableStateListOf<String>() }
    var feeling by remember { mutableStateOf(draftPrefs.getString("feeling", "").orEmpty()) }
    var style by remember { mutableStateOf(postCanvasStyles.first()) }
    var animation by remember { mutableStateOf("none") }
    var privatePost by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var pendingUploads by remember { mutableIntStateOf(0) }
    var uploadPercent by remember { mutableIntStateOf(0) }
    var uploadEtaSeconds by remember { mutableLongStateOf(0L) }
    var isReel by remember { mutableStateOf(false) }
    var aiGenerating by remember { mutableStateOf(false) }
    var publishedPostId by remember { mutableStateOf<String?>(null) }
    var customizeExpanded by remember { mutableStateOf(false) }
    val imageMedia = remember { mutableStateListOf<R2MediaResult>() }
    var videoMedia by remember { mutableStateOf<R2MediaResult?>(null) }

    LaunchedEffect(text, title, tags, feeling) {
        draftPrefs.edit().putString("text", text).putString("title", title).putString("tags", tags).putString("feeling", feeling).apply()
    }

    fun finishOneUpload() {
        pendingUploads = (pendingUploads - 1).coerceAtLeast(0)
        uploading = pendingUploads > 0
    }

    fun upload(uri: Uri, video: Boolean) {
        val mime = context.contentResolver.getType(uri) ?: if (video) "video/mp4" else "image/jpeg"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) { Toast.makeText(context, "Could not read media", Toast.LENGTH_SHORT).show(); return }
        val max = if (video) 95 * 1024 * 1024 else 15 * 1024 * 1024
        if (bytes.size > max) { Toast.makeText(context, if (video) "Video limit is 95 MB" else "Image limit is 15 MB", Toast.LENGTH_LONG).show(); return }
        val thumbnailBytes = if (video) runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            frame?.let { bitmap ->
                val width = 540
                val height = (bitmap.height * (width.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 78, out); out.toByteArray() }
            }
        }.getOrNull() else null
        pendingUploads++
        uploading = true
        uploadPercent = 0
        uploadEtaSeconds = 0L
        val ext = when { mime.contains("webm") -> "webm"; mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; video -> "mp4"; else -> "jpg" }
        viewModel.uploadMediaToR2(bytes, mime, if (video && isReel) "reel" else "post", ext, tags,
            onProgress = { percent, eta -> uploadPercent = percent; uploadEtaSeconds = eta },
            onSuccess = { result ->
                if (!video) { if (imageMedia.size < 5) imageMedia.add(result); finishOneUpload() }
                else {
                    videoMedia = result
                    if (thumbnailBytes != null && imageMedia.isEmpty()) {
                        viewModel.uploadMediaToR2(thumbnailBytes, "image/jpeg", "thumbnail", "jpg", tags,
                            onSuccess = { thumb -> imageMedia.add(thumb); finishOneUpload() },
                            onFailure = { finishOneUpload() })
                    } else finishOneUpload()
                }
            },
            onFailure = { error -> finishOneUpload(); Toast.makeText(context, error, Toast.LENGTH_LONG).show() })
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take((5 - imageMedia.size).coerceAtLeast(0)).forEach { uri -> upload(uri, false) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uri -> upload(uri, true) } }
    LaunchedEffect(initialMode, externalShare) {
        externalShare?.let { shared ->
            if(shared.text.isNotBlank()) text=shared.text.take(1200)
            shared.uris.take(5).forEach { uri -> upload(uri,(context.contentResolver.getType(uri)?:shared.mimeType).startsWith("video/")) }
        } ?: when (initialMode) {
            "photo" -> imagePicker.launch("image/*")
            "video" -> videoPicker.launch("video/*")
            "reel" -> { isReel = true; videoPicker.launch("video/*") }
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.modernBackdrop(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Column { Text("Create post", fontWeight = FontWeight.Bold); Text("${if (storageRoutingConfig.provider == "supabase") "Supabase" else "R2"} media expires in 10 days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Button(
                        onClick = {
                            if (text.isBlank() && imageMedia.isEmpty() && videoMedia == null) return@Button
                            publishing = true
                            val expiry = (imageMedia.map { it.expiresAt } + listOfNotNull(videoMedia?.expiresAt)).maxOrNull() ?: 0L
                            viewModel.createPost(
                                text = text.trim(), imageUrl = imageMedia.firstOrNull()?.publicUrl.orEmpty(), audioUrl = "", videoUrl = videoMedia?.publicUrl.orEmpty(),
                                isPrivate = privatePost,
                                onComplete = { postId -> publishing = false; draftPrefs.edit().clear().apply(); if (!privatePost && !postId.isNullOrBlank()) viewModel.notifyMentionedUsers(text, postId, "in a post"); if (privatePost) onPublished() else publishedPostId = postId },
                                title = title.trim(),
                                tags = tags.split(",", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct(),
                                taggedUserIds = taggedIds.toList(), feeling = feeling,
                                backgroundStyle = style.id, textAnimation = animation,
                                r2ObjectKeys = imageMedia.map { it.key } + listOfNotNull(videoMedia?.key),
                                isReel = isReel && videoMedia != null,
                                expiresAt = expiry,
                                imageUrls = imageMedia.map { it.publicUrl }
                            )
                        },
                        enabled = (text.isNotBlank() || imageMedia.isNotEmpty() || videoMedia != null) && !publishing && !uploading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = Color.Transparent, disabledContentColor = Color.White.copy(alpha = .7f)),
                        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF6B4EFF), Color(0xFFE64DAB))))
                            .alpha(if ((text.isNotBlank() || imageMedia.isNotEmpty() || videoMedia != null) && !publishing && !uploading) 1f else .5f)
                    ) {
                        if (publishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Post", fontWeight = FontWeight.Bold) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            )
        }
    ) { padding ->
        val me by viewModel.currentUserState.collectAsState()
        val styledCanvas = style.id != "glass" && imageMedia.isEmpty() && videoMedia == null
        val mentionQuery = remember(text) { Mentions.activeQuery(text) }
        val mentionMatches = remember(mentionQuery, users, me?.uid) { mentionQuery?.let { Mentions.suggestions(it, users, me?.uid.orEmpty()) }.orEmpty() }
        val firstLink = remember(text) { LinkPreviewRepository.firstUrl(text) }
        var toolTab by rememberSaveable { mutableStateOf("media") }
        val canvasText = if (styledCanvas) Color.White else MaterialTheme.colorScheme.onSurface
        val transparentFieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = canvasText,
            unfocusedTextColor = canvasText,
            cursorColor = canvasText,
            focusedPlaceholderColor = canvasText.copy(alpha = .55f),
            unfocusedPlaceholderColor = canvasText.copy(alpha = .55f)
        )
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Author and audience
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatar = me?.profileImageUrl.orEmpty()
                if (avatar.isNotBlank()) {
                    AsyncImage(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(CircleShape))
                } else {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(me?.name?.take(1)?.uppercase().orEmpty().ifBlank { "C" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(me?.name?.takeIf { it.isNotBlank() } ?: "You", fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { privatePost = !privatePost },
                            label = { Text(if (privatePost) "Only me" else "Public", fontSize = 11.sp) },
                            leadingIcon = { Icon(if (privatePost) Icons.Outlined.Lock else Icons.Outlined.Public, null, Modifier.size(15.dp)) },
                            modifier = Modifier.height(30.dp)
                        )
                        if (feeling.isNotBlank()) AssistChip(onClick = { toolTab = "feeling" }, label = { Text(feeling, fontSize = 11.sp) }, modifier = Modifier.height(30.dp))
                        if (isReel) AssistChip(onClick = { toolTab = "media" }, label = { Text("Reel", fontSize = 11.sp) }, modifier = Modifier.height(30.dp))
                    }
                }
            }

            // Live canvas: what you type is shown the way it will look in the feed
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(if (styledCanvas) Brush.linearGradient(style.colors) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))))
                    .border(1.dp, if (styledCanvas) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), RoundedCornerShape(28.dp))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    TextField(
                        value = title, onValueChange = { if (it.length <= 80) title = it },
                        placeholder = { Text("Add a title (optional)") },
                        singleLine = true,
                        colors = transparentFieldColors,
                        textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = if (styledCanvas) TextAlign.Center else TextAlign.Start),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = text, onValueChange = { if (it.length <= 1200) text = it },
                        placeholder = { Text(if (styledCanvas) "Say something bold..." else "What's on your mind? Use @ to mention people") },
                        colors = transparentFieldColors,
                        textStyle = TextStyle(
                            fontSize = if (styledCanvas) (if (text.length < 90) 24.sp else 19.sp) else 16.sp,
                            lineHeight = if (styledCanvas) 32.sp else 23.sp,
                            fontWeight = if (styledCanvas) FontWeight.Bold else FontWeight.Normal,
                            textAlign = if (styledCanvas) TextAlign.Center else TextAlign.Start
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (styledCanvas) 210.dp else 150.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (text.isNotBlank() || title.isNotBlank()) "Draft saved" else style.title, fontSize = 11.sp, color = canvasText.copy(alpha = .7f), modifier = Modifier.weight(1f))
                        CircularProgressIndicator(progress = { text.length / 1200f }, modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = if (text.length > 1100) Color(0xFFFF6B6B) else canvasText.copy(alpha = .8f), trackColor = canvasText.copy(alpha = .18f))
                        Spacer(Modifier.width(6.dp))
                        Text("${text.length}/1200", fontSize = 11.sp, color = canvasText.copy(alpha = .7f))
                    }
                }
            }
            MentionSuggestionBar(suggestions = mentionMatches, onPick = { user -> text = Mentions.complete(text, user) })
            // Quick emoji bar: one tap appends to the post.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("😀", "😂", "😍", "🥰", "🔥", "🎉", "❤️", "👍", "🙏", "😎", "🤩", "😢", "💯", "✨", "🌸", "🇧🇩")) { emoji ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
                            .clickable { if (text.length < 1198) text += emoji },
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 20.sp) }
                }
            }
            if (!styledCanvas && !firstLink.isNullOrBlank()) {
                RichLinkPreviewCard(url = firstLink, contentColor = MaterialTheme.colorScheme.onSurface, containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
            }

            // Attached media
            if (uploading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(progress = { uploadPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Uploading $uploadPercent%${if (uploadEtaSeconds > 0) ", about ${uploadEtaSeconds}s left" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (imageMedia.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageMedia, key = { it.key }) { media ->
                        Box(Modifier.size(width = 150.dp, height = 190.dp).clip(RoundedCornerShape(20.dp))) {
                            AsyncImage(media.publicUrl, "Post image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            IconButton(onClick = { viewModel.discardR2Media(media.key); imageMedia.remove(media) }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(.5f), CircleShape)) { Icon(Icons.Outlined.Delete, "Remove", tint = Color.White) }
                        }
                    }
                }
            }
            videoMedia?.let { media ->
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                    AndroidView(factory = { ctx -> VideoView(ctx).apply { setVideoPath(media.publicUrl); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxSize())
                    IconButton(onClick = { viewModel.discardR2Media(media.key); videoMedia = null }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(.5f), CircleShape)) { Icon(Icons.Outlined.Delete, "Remove", tint = Color.White) }
                }
            }

            // Organized tools: one tab at a time
            val toolTabs = listOf(
                "media" to "Media", "background" to "Background", "feeling" to "Feeling",
                "people" to "People", "hashtags" to "Hashtags", "animation" to "Animation", "ai" to "AI studio"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(toolTabs, key = { it.first }) { (id, label) ->
                    FilterChip(
                        selected = toolTab == id,
                        onClick = { toolTab = id },
                        label = { Text(label, fontWeight = if (toolTab == id) FontWeight.Bold else FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                when (id) {
                                    "media" -> Icons.Outlined.AddPhotoAlternate
                                    "background" -> Icons.Outlined.Palette
                                    "feeling" -> Icons.Outlined.EmojiEmotions
                                    "people" -> Icons.Outlined.AlternateEmail
                                    "hashtags" -> Icons.Outlined.Tag
                                    "animation" -> Icons.Outlined.Animation
                                    else -> Icons.Outlined.AutoAwesome
                                },
                                null, Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.45f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (toolTab) {
                        "media" -> {
                            Text("Add up to 5 photos or one video. Text backgrounds are used only for posts without media.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { imagePicker.launch("image/*") }, enabled = !uploading && imageMedia.size < 5, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Photos ${imageMedia.size}/5")
                                }
                                FilledTonalButton(onClick = { videoPicker.launch("video/*") }, enabled = !uploading && videoMedia == null, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Outlined.VideoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Video")
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Publish video as a Reel", fontWeight = FontWeight.Bold)
                                    Text("Reels appear in the full-screen video feed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(isReel, { isReel = it }, enabled = videoMedia == null)
                            }
                        }
                        "background" -> {
                            Text("Pick a color canvas for text posts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            postCanvasStyles.chunked(4).forEach { rowStyles ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowStyles.forEach { item ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { style = item }) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(item.colors))
                                                    .border(if (style.id == item.id) 3.dp else 1.dp, if (style.id == item.id) MaterialTheme.colorScheme.primary else Color.White.copy(.25f), RoundedCornerShape(18.dp)),
                                                contentAlignment = Alignment.Center
                                            ) { if (style.id == item.id) Icon(Icons.Default.Check, null, tint = Color.White) else Text("Aa", color = Color.White, fontWeight = FontWeight.Bold) }
                                            Text(item.title, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                    repeat(4 - rowStyles.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        "feeling" -> {
                            val feelings = listOf(
                                "😊 Happy", "🥰 Loved", "🎉 Excited", "😎 Cool", "🙏 Blessed", "🤩 Amazed", "💪 Motivated",
                                "😢 Sad", "😴 Tired", "✈️ Traveling", "🎮 Gaming", "📚 Studying", "🍔 Hungry", "⚽ Sporty"
                            )
                            feelings.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { item ->
                                        FilterChip(selected = feeling == item, onClick = { feeling = if (feeling == item) "" else item }, label = { Text(item) }, modifier = Modifier.weight(1f))
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        "people" -> {
                            OutlinedTextField(
                                value = tagQuery, onValueChange = { tagQuery = it }, label = { Text("Tag people") },
                                leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, placeholder = { Text("Type a name or username") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
                            )
                            val matches = if (tagQuery.trim().length < 2) emptyList() else users.filter { it.uid !in taggedIds && (it.name.contains(tagQuery.trim(), true) || it.username.contains(tagQuery.trim().removePrefix("@"), true)) }.take(5)
                            matches.forEach { user ->
                                ListItem(
                                    headlineContent = { Text(user.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("@${user.username}") },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { taggedIds.add(user.uid); tagQuery = "" }
                                )
                            }
                            if (taggedIds.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(taggedIds.toList()) { uid -> users.find { it.uid == uid }?.let { user -> InputChip(true, { taggedIds.remove(uid) }, { Text(user.name) }) } }
                            }
                            Text("Tip: type @username in your post to mention someone. They get a notification.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "hashtags" -> {
                            OutlinedTextField(
                                value = tags, onValueChange = { tags = it }, label = { Text("Hashtags") },
                                placeholder = { Text("convo, thoughts, friends") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("convo", "friends", "life", "music", "travel", "food", "study", "fun", "bangladesh", "photography")) { tag ->
                                    SuggestionChip(onClick = {
                                        val existing = tags.split(",", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
                                        if (tag !in existing) tags = (existing + tag).joinToString(", ")
                                    }, label = { Text("#$tag") })
                                }
                            }
                        }
                        "animation" -> {
                            Text("Animate the text of a color post", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("none" to "Still", "pulse" to "Pulse", "breathe" to "Breathe", "glow" to "Glow").forEach { (id, label) ->
                                    FilterChip(selected = animation == id, onClick = { animation = id }, label = { Text(label) }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        else -> {
                            Text("Generate a polished caption from your title, hashtags and idea.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    aiGenerating = true
                                    val prompt = "Write one engaging social post caption. Title: $title. Tags: $tags. Existing idea: $text. Return only the caption, under 700 characters."
                                    viewModel.askAssistant(prompt, emptyList()) { reply -> aiGenerating = false; if (!reply.contains("unavailable", true) && !reply.contains("failed", true)) text = reply.take(1200) else Toast.makeText(context, reply, Toast.LENGTH_LONG).show() }
                                }, enabled = !aiGenerating, modifier = Modifier.weight(1f)) {
                                    if (aiGenerating) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("AI caption")
                                }
                                OutlinedButton(onClick = { text = ""; title = ""; tags = ""; feeling = ""; draftPrefs.edit().clear().apply() }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Delete, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Clear draft") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    publishedPostId?.let { postId ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Surface(color=Color(0xFF1877F2),shape=CircleShape){Text("f",color=Color.White,fontWeight=FontWeight.Black,fontSize=24.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))} },
            title = { Text("Share on Facebook?",fontWeight=FontWeight.ExtraBold) },
            text = { Text("Your post is published. Share its Convo preview link on Facebook? People can open the full post in the app or download Convo Chat.") },
            confirmButton = { Button(onClick = {
                val link="https://solitary-hill-dcdc.mr44253990.workers.dev/post/$postId";val shareText="${title.ifBlank{"New post on Convo Chat"}}\n${text.take(180)}\n$link"
                val facebook=android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,shareText);setPackage("com.facebook.katana")}
                runCatching{context.startActivity(facebook)}.onFailure{context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,shareText)},"Share post"))}
                publishedPostId=null;onPublished()
            }) { Text("Share to Facebook") } },
            dismissButton = { TextButton(onClick={publishedPostId=null;onPublished()}){Text("Not now")} }
        )
    }
}
