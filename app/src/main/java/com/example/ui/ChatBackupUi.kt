package com.example.ui

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.security.TextBackupManager

/**
 * Chat backup control (v5.2): one encrypted file with the chats only, updated in place every
 * time the app goes to the background. Choosing a folder once makes that single file survive an
 * uninstall; after reinstalling, pick the same folder again and press Restore.
 */
@Composable
fun ChatBackupDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val status = remember(tick) { TextBackupManager.status(context) }
    var busy by remember { mutableStateOf("") }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            TextBackupManager.setFolder(context, uri)
            busy = "backup"
            viewModel.exportTextBackup { result ->
                busy = ""; tick++
                Toast.makeText(context, if (result.reason.isBlank()) "Folder connected and backup saved" else "Folder connected, but the backup failed: ${result.reason}", Toast.LENGTH_LONG).show()
            }
        }
        tick++
    }
    Dialog(onDismissRequest = { if (busy.isBlank()) onDismiss() }) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF14A3B8)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Backup, null, tint = Color.White) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Chat backup", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("One encrypted file · chats only · automatic", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (busy.isBlank()) onDismiss() }) { Icon(Icons.Default.Close, "Close") }
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background((if (status.folderConnected) Color(0xFF19C37D) else Color(0xFFFFB03A)).copy(alpha = .12f))
                        .border(1.dp, (if (status.folderConnected) Color(0xFF19C37D) else Color(0xFFFFB03A)).copy(alpha = .45f), RoundedCornerShape(20.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (status.folderConnected) Icons.Default.VerifiedUser else Icons.Default.Warning, null, tint = if (status.folderConnected) Color(0xFF19C37D) else Color(0xFFFF9D2E), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (status.folderConnected) "Survives uninstall" else "Not protected against uninstall yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        if (status.folderConnected) "Saved in: ${status.folderLabel}. The same single file is updated every time — no extra copies."
                        else "Android hides an app's own files after it is removed. Choose a folder once (for example Documents) and the single backup file will stay readable after a reinstall.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupFact("Last backup", if (status.lastBackupAt > 0) relativeTimeLabel(status.lastBackupAt) else "never", Modifier.weight(1f))
                    BackupFact("Messages", status.messages.toString(), Modifier.weight(1f))
                    BackupFact("File size", if (status.bytes > 0) Formatter.formatShortFileSize(context, status.bytes) else "—", Modifier.weight(1f))
                }
                Button(onClick = { runCatching { folderLauncher.launch(null) } }, enabled = busy.isBlank(), modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (status.folderConnected) "Change backup folder" else "Choose backup folder")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { busy = "backup"; viewModel.exportTextBackup { result -> busy = ""; tick++; Toast.makeText(context, if (result.reason.isBlank()) "Backup saved (${result.itemCount} messages)" else "Backup failed: ${result.reason}", Toast.LENGTH_LONG).show() } },
                        enabled = busy.isBlank(), modifier = Modifier.weight(1f), shape = CircleShape
                    ) { if (busy == "backup") CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp) else Text("Back up now") }
                    OutlinedButton(
                        onClick = { busy = "restore"; viewModel.restoreTextBackupNow { restored, count, reason -> busy = ""; Toast.makeText(context, if (restored) "Restored $count items" else "Nothing restored: $reason", Toast.LENGTH_LONG).show() } },
                        enabled = busy.isBlank(), modifier = Modifier.weight(1f), shape = CircleShape
                    ) { if (busy == "restore") CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp) else Text("Restore") }
                }
                if (status.folderConnected) TextButton(onClick = { TextBackupManager.clearFolder(context); tick++ }, enabled = busy.isBlank()) { Text("Disconnect folder", color = MaterialTheme.colorScheme.error) }
                Text("Photos, videos and voice files are not part of this backup. The file is encrypted for your account.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun BackupFact(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(vertical = 9.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
