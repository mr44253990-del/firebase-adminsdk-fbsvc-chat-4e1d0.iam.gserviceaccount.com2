package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FlagshipConfig
import com.example.update.UpdateInstallTracker
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun FlagshipUpdateDialog(config: FlagshipConfig, mandatory: Boolean, onLater: () -> Unit) {
    val context = LocalContext.current
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var downloadId by remember { mutableLongStateOf(0L) }

    fun install() {
        val file = downloaded ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        UpdateInstallTracker.markInstallStarted(context, config)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    LaunchedEffect(downloadId) {
        if (downloadId <= 0) return@LaunchedEffect
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (downloading) {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (total > 0) progress = (done * 100 / total).toInt()
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false; progress = 100
                        downloaded = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Convo Chat-${config.versionName}.apk")
                    } else if (status == DownloadManager.STATUS_FAILED) downloading = false
                }
            }
            delay(500)
        }
    }

    Dialog(
        onDismissRequest = { if (!mandatory) onLater() },
        properties = DialogProperties(dismissOnBackPress = !mandatory, dismissOnClickOutside = !mandatory)
    ) {
        Surface(shape = RoundedCornerShape(30.dp), tonalElevation = 8.dp) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF5D4BFF), Color(0xFF1BA8D7), Color(0xFF35B88A))))
                        .padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (mandatory) "Required app update" else "A new version is ready", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Convo Chat ${config.versionName} · build ${config.latestVersionCode}", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("What changed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    val notes = config.releaseNotes.lines().map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() }
                    if (notes.isEmpty()) {
                        Text("Performance, security and feature improvements.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        notes.take(12).forEach { note ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(note, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (downloading) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading update • $progress%")
                }
                downloaded?.let {
                    Button(onClick = ::install, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("Install update") }
                } ?: Button(
                    onClick = {
                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Convo Chat-${config.versionName}.apk").apply { if (exists()) delete() }
                        val request = DownloadManager.Request(Uri.parse(config.apkUrl)).setTitle("Convo Chat ${config.versionName}")
                            .setDescription("Downloading signed Convo Chat update")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationUri(Uri.fromFile(file))
                        downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        downloading = true
                    }, enabled = !downloading && config.apkUrl.startsWith("https://"), modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Download update") }
                if (!mandatory) TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("Later") }
                if (mandatory) Text("This version is no longer allowed. Install the update to continue.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
