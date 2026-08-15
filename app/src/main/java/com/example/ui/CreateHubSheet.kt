package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHubSheet(viewModel: ChatViewModel, onDismiss: () -> Unit, onCreatePost: () -> Unit) {
    val context = LocalContext.current
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("") }

    fun uploadStory(uri: Uri, video: Boolean) {
        uploading = true; progress = 0; label = if (video) "Video story" else "Photo story"
        val mime = context.contentResolver.getType(uri) ?: if (video) "video/mp4" else "image/jpeg"
        val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        viewModel.uploadUriToSupabase(uri, "story_${System.currentTimeMillis()}.${if (video) "mp4" else "jpg"}", mime, size,
            onProgress = { p, _ -> progress = p },
            onSuccess = { url ->
                viewModel.uploadStory("My Story", if (video) "" else url, if (video) url else "") {
                    uploading = false; Toast.makeText(context, "Story published", Toast.LENGTH_SHORT).show(); onDismiss()
                }
            },
            onFailure = { uploading = false; Toast.makeText(context, it, Toast.LENGTH_LONG).show() })
    }
    val photoStory = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uri -> uploadStory(uri, false) } }
    val videoStory = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { uri -> uploadStory(uri, true) } }

    ModalBottomSheet(onDismissRequest = { if (!uploading) onDismiss() }, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Color(0xFF6652FF), Color(0xFFFF39BD))), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp)); Column { Text("Create something", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text("One smart hub for every format", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (uploading) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Uploading $label • $progress%")
            } else {
                CreateHubAction(Icons.Default.EditNote, "Text post", "Write, background, feelings, tags and polls") { viewModel.prepareComposer("post"); onDismiss(); onCreatePost() }
                CreateHubAction(Icons.Default.AddPhotoAlternate, "Photo post", "Upload up to five photos and add details") { viewModel.prepareComposer("photo"); onDismiss(); onCreatePost() }
                CreateHubAction(Icons.Default.VideoLibrary, "Video post", "Upload a video, title, caption and thumbnail") { viewModel.prepareComposer("video"); onDismiss(); onCreatePost() }
                CreateHubAction(Icons.Default.SmartDisplay, "Create Reel", "Upload directly as a vertical Reel") { viewModel.prepareComposer("reel"); onDismiss(); onCreatePost() }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { photoStory.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.AutoStories, null); Spacer(Modifier.width(6.dp)); Text("Photo story") }
                    OutlinedButton(onClick = { videoStory.launch("video/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Movie, null); Spacer(Modifier.width(6.dp)); Text("Video story") }
                }
            }
        }
    }
}

@Composable
private fun CreateHubAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(.55f), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.ExtraBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
