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
    PostCanvasStyle("mono", "Monochrome", listOf(Color(0xFF111111), Color(0xFF424242), Color(0xFF151515)))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(viewModel: ChatViewModel, onBack: () -> Unit, onPublished: () -> Unit) {
    val context = LocalContext.current
    val initialMode = remember { viewModel.consumeComposerMode() }
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
    LaunchedEffect(initialMode) {
        when (initialMode) {
            "photo" -> imagePicker.launch("image/*")
            "video" -> videoPicker.launch("video/*")
            "reel" -> { isReel = true; videoPicker.launch("video/*") }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Column { Text("Create post", fontWeight = FontWeight.Bold); Text("R2 media expires in 10 days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
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
                                onComplete = { publishing = false; draftPrefs.edit().clear().apply(); onPublished() },
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
                        modifier = Modifier.padding(end = 8.dp)
                    ) { if (publishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Publish") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Media", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Publish video as Reel", fontSize = 11.sp)
                        Switch(isReel, { isReel = it }, enabled = videoMedia == null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !uploading && imageMedia.size < 5, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Photo")
                        }
                        OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !uploading && videoMedia == null, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.VideoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Video")
                        }
                    }
                    if (uploading) {
                        LinearProgressIndicator(progress = { uploadPercent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Uploading to Cloudflare R2 • $uploadPercent%${if (uploadEtaSeconds > 0) " • about ${uploadEtaSeconds}s left" else ""}", fontSize = 11.sp)
                    }
                    if (imageMedia.isNotEmpty()) {
                        Text("${imageMedia.size}/5 photos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }
            }
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(.58f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Convo Smart Studio", fontWeight = FontWeight.ExtraBold) }
                    Text("Generate a polished caption or keep an automatic local draft.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (text.isNotBlank() || title.isNotBlank()) Text("Draft saved automatically", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedTextField(
                value = title, onValueChange = { if (it.length <= 80) title = it },
                label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp)
            )
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 1200) text = it },
                label = { Text("Your post") }, supportingText = { Text("${text.length}/1200") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5, shape = RoundedCornerShape(24.dp)
            )
            Card(onClick = { customizeExpanded = !customizeExpanded }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.55f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Customize your post", fontWeight = FontWeight.ExtraBold); Text("Background • feeling • animation • hashtags • people", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(if (customizeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            AnimatedVisibility(customizeExpanded) {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(.72f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Background", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(postCanvasStyles) { item ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { style = item }) {
                                    Box(
                                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(item.colors))
                                            .border(if (style.id == item.id) 3.dp else 1.dp, if (style.id == item.id) MaterialTheme.colorScheme.primary else Color.White.copy(.25f), RoundedCornerShape(20.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { if (style.id == item.id) Icon(Icons.Default.Check, null, tint = Color.White) }
                                    Text(item.title, fontSize = 10.sp)
                                }
                            }
                        }
                        Text("Text animation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("none" to "Still", "pulse" to "Pulse", "breathe" to "Breathe", "glow" to "Glow")) { (id, label) ->
                                FilterChip(selected = animation == id, onClick = { animation = id }, label = { Text(label) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) })
                            }
                        }
                        Text("Feeling", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("😊 Happy", "🥰 Loved", "🎉 Excited", "😎 Cool", "😢 Sad", "✈️ Traveling", "🎮 Gaming")) { item ->
                                FilterChip(selected = feeling == item, onClick = { feeling = if (feeling == item) "" else item }, label = { Text(item) })
                            }
                        }
                        OutlinedTextField(
                            value = tags, onValueChange = { tags = it }, label = { Text("Hashtags") },
                            placeholder = { Text("convo, thoughts, friends") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = tagQuery, onValueChange = { tagQuery = it }, label = { Text("Tag people") },
                            leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, placeholder = { Text("Type name or username") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        val matches = if (tagQuery.trim().length < 2) emptyList() else users.filter { it.uid !in taggedIds && (it.name.contains(tagQuery.trim(), true) || it.username.contains(tagQuery.trim().removePrefix("@"), true)) }.take(5)
                        matches.forEach { user ->
                            ListItem(
                                headlineContent = { Text(user.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("@${user.username}") },
                                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { taggedIds.add(user.uid); tagQuery = "" }
                            )
                        }
                        if (taggedIds.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(taggedIds.toList()) { uid -> users.find { it.uid == uid }?.let { user -> InputChip(true, { taggedIds.remove(uid) }, { Text(user.name) }) } }
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Private post", fontWeight = FontWeight.Bold); Text("Only you can see this post", fontSize = 11.sp) }
                    Switch(privatePost, { privatePost = it })
                }
            }
        }
    }
}
