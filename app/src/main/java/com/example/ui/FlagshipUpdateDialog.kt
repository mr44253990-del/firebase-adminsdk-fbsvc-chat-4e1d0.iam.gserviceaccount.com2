package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FlagshipConfig
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
                        downloaded = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FireChat-${config.versionName}.apk")
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
                Text(if (mandatory) "Required FireChat update" else "New FireChat update", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Version ${config.versionName} • Code ${config.latestVersionCode}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(config.releaseNotes.ifBlank { "Performance, security and feature improvements." })
                if (downloading) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading update • $progress%")
                }
                downloaded?.let {
                    Button(onClick = ::install, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("Install update") }
                } ?: Button(
                    onClick = {
                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FireChat-${config.versionName}.apk").apply { if (exists()) delete() }
                        val request = DownloadManager.Request(Uri.parse(config.apkUrl)).setTitle("FireChat ${config.versionName}")
                            .setDescription("Downloading signed FireChat update")
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
