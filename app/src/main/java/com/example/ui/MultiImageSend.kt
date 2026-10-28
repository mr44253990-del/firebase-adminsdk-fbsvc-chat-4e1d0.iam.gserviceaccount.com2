package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Max photos per batch (WhatsApp-style album send). */
const val MAX_BATCH_IMAGES = 10

/**
 * Downscale + re-encode a picked photo. Standard: longest side 1600 px, JPEG 82.
 * HD: longest side 2560 px, JPEG 92. EXIF rotation is applied so photos are never sideways.
 */
suspend fun compressChatImage(context: Context, uri: Uri, hd: Boolean): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val maxSide = if (hd) 2560 else 1600
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
        val finalBitmap = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded
        ByteArrayOutputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, if (hd) 92 else 82, out)
            if (finalBitmap !== decoded) decoded.recycle()
            finalBitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()
}

/** Preview tray for several picked photos: reorder-free remove, caption and HD toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiImageSendSheet(
    uris: List<Uri>,
    recipientName: String,
    onDismiss: () -> Unit,
    onSend: (images: List<Uri>, caption: String, hd: Boolean) -> Unit
) {
    var items by remember(uris) { mutableStateOf(uris.take(MAX_BATCH_IMAGES)) }
    var caption by remember { mutableStateOf("") }
    var hd by remember { mutableStateOf(false) }
    var focused by remember(uris) { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${items.size} photo${if (items.size == 1) "" else "s"} selected", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Sending to ${recipientName.ifBlank { "this chat" }} • up to $MAX_BATCH_IMAGES at once", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(selected = hd, onClick = { hd = !hd }, label = { Text("HD") }, leadingIcon = { Icon(if (hd) Icons.Default.HighQuality else Icons.Default.Hd, null, Modifier.size(18.dp)) })
            }
            items.getOrNull(focused.coerceIn(0, (items.size - 1).coerceAtLeast(0)))?.let { big ->
                AsyncImage(big, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items, key = { _, u -> u.toString() }) { index, uri ->
                    Box(Modifier.size(72.dp)) {
                        Surface(onClick = { focused = index }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxSize()
                            .border(if (index == focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))) {
                            AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = .6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = {
                                items = items.filterIndexed { i, _ -> i != index }
                                if (focused >= items.size) focused = (items.size - 1).coerceAtLeast(0)
                                if (items.isEmpty()) onDismiss()
                            }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                        Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
            OutlinedTextField(
                caption, { caption = it.take(1000) },
                placeholder = { Text("Add a caption…") },
                shape = RoundedCornerShape(20.dp), maxLines = 3, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSend(items, caption.trim(), hd) },
                enabled = items.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (items.size == 1) "Send photo" else "Send ${items.size} photos", fontWeight = FontWeight.Bold)
            }
        }
    }
}
