package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * WhatsApp-style saving of received media: every received photo / file is downloaded once into
 * `Downloads/Convo Chat/<Images|Videos|Audio|PDF|Documents|Archives|Apps|Files>/`.
 * The DownloadManager id is remembered per message so the bubble can show progress and open it.
 */
object ReceivedFiles {
    const val APP_FOLDER = "Convo Chat"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("convo_received_files", Context.MODE_PRIVATE)

    fun autoDownloadEnabled(ctx: Context) = prefs(ctx).getBoolean("auto", true)
    fun setAutoDownload(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto", on).apply()

    /** v4.4 auto-download rules: Wi-Fi only and per-type switches (all default on, Wi-Fi only off). */
    fun wifiOnly(ctx: Context) = prefs(ctx).getBoolean("wifi_only", false)
    fun setWifiOnly(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("wifi_only", on).apply()
    fun typeAllowed(ctx: Context, folder: String) = prefs(ctx).getBoolean("type_$folder", true)
    fun setTypeAllowed(ctx: Context, folder: String, on: Boolean) = prefs(ctx).edit().putBoolean("type_$folder", on).apply()
    private fun onMeteredNetwork(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(false)
    fun rulesAllow(ctx: Context, mime: String, name: String): Boolean =
        typeAllowed(ctx, folderFor(mime, name)) && !(wifiOnly(ctx) && onMeteredNetwork(ctx))

    fun folderFor(mime: String, name: String): String {
        val m = mime.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            m.startsWith("image/") -> "Images"
            m.startsWith("video/") -> "Videos"
            m.startsWith("audio/") -> "Audio"
            m == "application/pdf" || ext == "pdf" -> "PDF"
            ext == "apk" || m.contains("android.package") -> "Apps"
            m.contains("zip") || m.contains("rar") || m.contains("7z") || m.contains("tar") || ext in setOf("zip", "rar", "7z", "tar", "gz") -> "Archives"
            m.contains("word") || m.contains("document") || m.contains("sheet") || m.contains("excel") || m.contains("presentation") ||
                m.contains("powerpoint") || m.startsWith("text/") || ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt") -> "Documents"
            else -> "Files"
        }
    }

    data class ManagedDownload(val id: Long, val title: String, val bytes: Long, val status: Int, val description: String)

    /** Only enumerates this app's DownloadManager items, identified by our app-folder description. */
    fun managedDownloads(ctx: Context): List<ManagedDownload> = runCatching {
        val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val description = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)).orEmpty()
                    if (!description.startsWith(APP_FOLDER)) continue
                    add(ManagedDownload(
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0L),
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        description
                    ))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun removeManagedDownload(ctx: Context, id: Long): Boolean = runCatching {
        val removed = (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) > 0
        val editor = prefs(ctx).edit()
        prefs(ctx).all.filterValues { it == id }.keys.forEach { key -> editor.remove(key) }
        editor.apply()
        removed
    }.getOrDefault(false)

    fun downloadIdFor(ctx: Context, key: String): Long = prefs(ctx).getLong("id_$key", 0L)

    /** Starts (or reuses) the download for [key]. Returns the DownloadManager id, or 0 on failure. */
    fun enqueue(ctx: Context, key: String, url: String, name: String, mime: String, notify: Boolean = true): Long {
        val existing = downloadIdFor(ctx, key)
        if (existing > 0 && statusOf(ctx, existing) != DownloadManager.STATUS_FAILED) return existing
        if (!url.startsWith("http")) return 0L
        val safe = name.ifBlank { "file_${System.currentTimeMillis()}" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val folder = folderFor(mime, safe)
        return runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(safe)
                .setDescription("$APP_FOLDER · $folder")
                .setMimeType(mime.ifBlank { "application/octet-stream" })
                .setNotificationVisibility(if (notify) DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED else DownloadManager.Request.VISIBILITY_HIDDEN)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$APP_FOLDER/$folder/$safe")
            val id = (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            prefs(ctx).edit().putLong("id_$key", id).apply()
            id
        }.getOrElse {
            // VISIBILITY_HIDDEN needs a permission on some builds; retry with a visible notification.
            if (!notify) enqueue(ctx, key, url, name, mime, notify = true) else 0L
        }
    }

    /** Automatic save for received media (once per message, only when the user keeps it on). */
    fun autoSave(ctx: Context, key: String, url: String?, name: String, mime: String): Long {
        if (!autoDownloadEnabled(ctx) || url.isNullOrBlank() || !url.startsWith("http") || !rulesAllow(ctx, mime, name)) return downloadIdFor(ctx, key)
        return enqueue(ctx, key, url, name, mime, notify = false)
    }

    fun statusOf(ctx: Context, id: Long): Int = runCatching {
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else DownloadManager.STATUS_FAILED
        }
    }.getOrDefault(DownloadManager.STATUS_FAILED)

    /** Opens a downloaded file with the best app (PDFs open in the built-in viewer instead). */
    fun openExternally(ctx: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(ctx, "No app on this phone can open this file type", Toast.LENGTH_LONG).show() }
    }

    fun share(ctx: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).setType(mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(Intent.createChooser(intent, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Full in-app PDF reader: every page, pinch to zoom, page counter, open-with and share. */
@Composable
fun PdfViewerDialog(uri: Uri, name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val renderLock = remember { Mutex() }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var descriptor by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val d = context.contentResolver.openFileDescriptor(uri, "r") ?: error("no file")
                descriptor = d
                renderer = PdfRenderer(d)
            }.onFailure { failed = true }
        }
    }
    DisposableEffect(uri) {
        onDispose {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }
    val pageCount = renderer?.pageCount ?: 0
    val listState = rememberLazyListState()
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val screenPx = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }.toInt().coerceIn(480, 1600)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E0F14))) {
            when {
                failed -> Text("This PDF could not be opened", color = Color.White, modifier = Modifier.align(Alignment.Center))
                renderer == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Box(
                    // Two-finger pinch/pan is handled here (Initial pass); one finger keeps scrolling pages.
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    val limit = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                    panX = if (scale <= 1.01f) 0f else (panX + pan.x).coerceIn(-limit, limit)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX),
                        contentPadding = PaddingValues(top = 86.dp, bottom = 40.dp, start = 10.dp, end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pageCount) { index ->
                            val page by produceState<Bitmap?>(null, renderer, index) {
                                value = withContext(Dispatchers.IO) {
                                    renderLock.withLock {
                                        runCatching {
                                            renderer!!.openPage(index).use { p ->
                                                val w = screenPx
                                                val h = (w.toFloat() * p.height / p.width.coerceAtLeast(1)).toInt().coerceIn(1, 6000)
                                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                                                    b.eraseColor(android.graphics.Color.WHITE)
                                                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                }
                                            }
                                        }.getOrNull()
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                                page?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                                    ?: Box(Modifier.fillMaxWidth().aspectRatio(0.707f), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }
                            }
                        }
                    }
                }
            }
            // Glass top bar
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .62f)).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val current = (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount.coerceAtLeast(1))
                    Text(if (pageCount > 0) "Page $current of $pageCount" else "PDF", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                }
                IconButton(onClick = { ReceivedFiles.share(context, uri, "application/pdf") }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = { ReceivedFiles.openExternally(context, uri, "application/pdf") }) { Icon(Icons.Default.OpenInNew, "Open with", tint = Color.White) }
            }
            if (scale > 1.01f) AssistChip(
                onClick = { scale = 1f; panX = 0f }, label = { Text("Reset zoom", color = Color.White) },
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(14.dp),
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = .6f))
            )
        }
    }
}

/** In-app viewer for plain-text style files (txt, csv, json, code, logs). */
@Composable
fun TextFileViewerDialog(uri: Uri, name: String, mime: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().let { b -> if (b.size > 400_000) b.copyOf(400_000) else b }.toString(Charsets.UTF_8) } }.getOrNull() ?: "This file could not be read."
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFF0E0F14))) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .6f)).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { ReceivedFiles.share(context, uri, mime) }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = { ReceivedFiles.openExternally(context, uri, mime) }) { Icon(Icons.Default.OpenInNew, "Open with", tint = Color.White) }
            }
            val t = text
            if (t == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    t, color = Color(0xFFE6E8F0), fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
                )
            }
        }
    }
}

/** True when [mime]/[name] is something the in-app text viewer can show. */
fun isTextLikeFile(mime: String, name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" ||
        ext in setOf("txt", "csv", "json", "xml", "log", "md", "kt", "java", "js", "ts", "py", "html", "css", "yml", "yaml", "ini", "srt")
}

/** True when the universal in-app viewer can show this file (image, video, audio or zip). */
fun isInAppPreviewable(mime: String, name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") || mime == "application/zip" ||
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "mp4", "mkv", "webm", "3gp", "mov", "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "zip")
}

/**
 * Universal in-app file viewer (v5.2): photos with pinch-zoom, videos and audio with player
 * controls, and ZIP archives as a browsable list. PDFs and text files use their own viewers;
 * anything else still offers "Open with".
 */
@Composable
fun UniversalFileViewerDialog(uri: Uri, name: String, mime: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ext = name.substringAfterLast('.', "").lowercase()
    val kind = when {
        mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic") -> "image"
        mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "3gp", "mov") -> "video"
        mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac") -> "audio"
        mime == "application/zip" || ext == "zip" -> "zip"
        else -> "other"
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFF07080D))) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF1B1548), Color(0xFF0F3D4A)))).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(kind.replaceFirstChar { it.uppercase() } + " · " + ext.uppercase().ifBlank { mime }, color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
                IconButton(onClick = { ReceivedFiles.share(context, uri, mime) }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = { ReceivedFiles.openExternally(context, uri, mime) }) { Icon(Icons.Default.OpenInNew, "Open with", tint = Color.White) }
            }
            when (kind) {
                "image" -> {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale <= 1.01f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        coil.compose.AsyncImage(
                            model = uri, contentDescription = name, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        )
                    }
                }
                "video", "audio" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (kind == "audio") Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 120.dp)) {
                        Box(Modifier.size(132.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 30.dp))
                    }
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = if (kind == "audio") Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomCenter) else Modifier.fillMaxSize(),
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                val controller = android.widget.MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(uri)
                                setOnPreparedListener { start(); controller.show(0) }
                                setOnErrorListener { _, _, _ -> android.widget.Toast.makeText(ctx, "This file cannot be played here — use Open with", android.widget.Toast.LENGTH_LONG).show(); true }
                            }
                        },
                        onRelease = { runCatching { it.stopPlayback() } }
                    )
                }
                "zip" -> {
                    val entries by produceState<List<Pair<String, Long>>?>(null, uri) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val out = mutableListOf<Pair<String, Long>>()
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    java.util.zip.ZipInputStream(input).use { zip ->
                                        var entry = zip.nextEntry
                                        while (entry != null && out.size < 500) { out += entry.name to entry.size; entry = zip.nextEntry }
                                    }
                                }
                                out.toList()
                            }.getOrDefault(emptyList())
                        }
                    }
                    val list = entries
                    if (list == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                        Text("${list.size} item${if (list.size == 1) "" else "s"} in this archive", color = Color(0xFF9FE8FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        list.forEach { (entryName, size) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (entryName.endsWith("/")) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(entryName, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (size > 0) Text(android.text.format.Formatter.formatShortFileSize(context, size), color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                            }
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No built-in preview for this file type", color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { ReceivedFiles.openExternally(context, uri, mime) }) { Text("Open with another app") }
                    }
                }
            }
        }
    }
}
