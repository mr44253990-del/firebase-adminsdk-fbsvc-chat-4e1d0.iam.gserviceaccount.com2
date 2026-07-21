package com.example.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val users by viewModel.usersState.collectAsState()
    var text by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var tagQuery by remember { mutableStateOf("") }
    val taggedIds = remember { mutableStateListOf<String>() }
    var feeling by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(postCanvasStyles.first()) }
    var animation by remember { mutableStateOf("none") }
    var privatePost by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var pendingUploads by remember { mutableIntStateOf(0) }
    var uploadPercent by remember { mutableIntStateOf(0) }
    var uploadEtaSeconds by remember { mutableLongStateOf(0L) }
    var isReel by remember { mutableStateOf(false) }
    val imageMedia = remember { mutableStateListOf<R2MediaResult>() }
    var videoMedia by remember { mutableStateOf<R2MediaResult?>(null) }

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
                                onComplete = { publishing = false; onPublished() },
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
            PostCanvasPreview(style, animation, title, text.ifBlank { if (imageMedia.isNotEmpty() || videoMedia != null) "Add a caption…" else "Write something meaningful…" }, feeling)
            OutlinedTextField(
                value = title, onValueChange = { if (it.length <= 80) title = it },
                label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp)
            )
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 1200) text = it },
                label = { Text("Your post") }, supportingText = { Text("${text.length}/1200") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5, shape = RoundedCornerShape(24.dp)
            )
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
                placeholder = { Text("firechat, thoughts, friends") }, modifier = Modifier.fillMaxWidth(), singleLine = true
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

@Composable
private fun PostCanvasPreview(style: PostCanvasStyle, animation: String, title: String, text: String, feeling: String) {
    val transition = rememberInfiniteTransition(label = "post_preview")
    val pulse by transition.animateFloat(
        initialValue = if (animation == "pulse") .96f else 1f,
        targetValue = if (animation == "pulse") 1.04f else if (animation == "breathe") 1.02f else 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "preview_scale"
    )
    Box(
        Modifier.fillMaxWidth().heightIn(min = 260.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(style.colors))
            .border(1.dp, Color.White.copy(alpha = if (animation == "glow") .7f else .22f), RoundedCornerShape(32.dp)).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(pulse)) {
            if (feeling.isNotBlank()) Text(feeling, color = Color.White.copy(.8f), fontWeight = FontWeight.Bold)
            if (title.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(10.dp)); Text(text, color = Color.White, fontSize = 19.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
        }
    }
}
