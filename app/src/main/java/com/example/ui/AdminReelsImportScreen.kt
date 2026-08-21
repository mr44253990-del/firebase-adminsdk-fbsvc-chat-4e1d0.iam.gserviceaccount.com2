package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminReelsImportScreen(viewModel: ChatViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val importState by viewModel.adminReelImportState.collectAsState()
    var links by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val content = stream.readBytes().decodeToString()
                require(content.length <= 2_000_000) { "TXT file must be 2 MB or smaller" }
                links = content
            }
        }.onFailure { links = "# Could not read file: ${it.localizedMessage ?: "unknown error"}" }
    }
    val sample = """# One HTTPS source URL per line
https://example.com/video-one
https://example.com/video-two
# Blank lines and lines beginning with # are ignored
""".trimIndent()
    val count = links.lineSequence().count { it.trim().startsWith("https://") }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Admin Reels library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Import public oEmbed links as admin-owned Reels", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .76f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Up to 500 unique links per import", fontWeight = FontWeight.Bold)
                    Text("The app discovers the provider's public oEmbed endpoint, stores title/thumbnail/embed metadata, and never downloads or re-hosts protected media.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedTextField(
            value = links,
            onValueChange = { links = it.take(2_000_000) },
            modifier = Modifier.fillMaxWidth().height(230.dp),
            label = { Text("HTTPS links or TXT contents") },
            placeholder = { Text("One public link per line") },
            supportingText = { Text("$count HTTPS-looking line(s) detected") },
            minLines = 8
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/*")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text("Choose .txt")
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Convo Reels TXT format", sample))
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy demo")
            }
        }
        Button(
            onClick = { viewModel.importAdminReelsFromText(links) },
            enabled = !importState.importing && count > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (importState.importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, null)
            Spacer(Modifier.width(8.dp)); Text(if (importState.importing) "Importing…" else "Import links to Reels")
        }
        AnimatedVisibility(
            visible = importState.message.isNotBlank(),
            enter = slideInVertically() + fadeIn()
        ) {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(importState.message, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (importState.importing) {
                        val total = count.coerceAtLeast(1)
                        LinearProgressIndicator((importState.processed.toFloat() / total).coerceIn(0f, 1f), Modifier.fillMaxWidth())
                    }
                    Text("Processed ${importState.processed}  •  Saved ${importState.imported}  •  Skipped ${importState.skipped}  •  Failed ${importState.failed}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = { showHelp = !showHelp }, modifier = Modifier.fillMaxWidth()) { Text(if (showHelp) "Hide TXT format" else "Show TXT format") }
        AnimatedVisibility(showHelp) {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text(sample, modifier = Modifier.padding(14.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
