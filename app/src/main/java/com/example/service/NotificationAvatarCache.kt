package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Sender photos for notifications.
 *
 * Why the photo used to be missing in the collapsed / heads-up notification: the full-size
 * profile picture was downloaded with a 2.5 s timeout on every push, so it often timed out
 * (no image → only the expanded MessagingStyle row, rendered later, showed it), and pushes
 * sent without `senderProfileUrl` never had a photo at all.
 *
 * Now photos are downsampled to 192 px, cached on disk per sender and URL (instant for every
 * later push), and when the payload has no URL the sender's profile is looked up once.
 */
object NotificationAvatarCache {
    private const val SIZE = 192

    private fun dir(context: Context) = File(context.cacheDir, "notif_avatars").apply { mkdirs() }

    /** Blocking — call from FirebaseMessagingService.onMessageReceived (worker thread) only. */
    fun load(context: Context, senderId: String, url: String): Bitmap? {
        var photo = url.trim()
        if (!photo.startsWith("http") && senderId.isNotBlank()) {
            photo = runCatching {
                Tasks.await(FirebaseFirestore.getInstance().collection("users").document(senderId).get(), 2500, TimeUnit.MILLISECONDS)
                    .getString("profileImageUrl").orEmpty()
            }.getOrDefault("")
        }
        if (!photo.startsWith("http")) return latestFor(context, senderId)
        val file = File(dir(context), "${senderId.ifBlank { "x" }.take(64)}_${photo.hashCode().toUInt()}.png")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }
        val bitmap = download(photo) ?: return latestFor(context, senderId)
        runCatching {
            dir(context).listFiles { f -> f.name.startsWith("${senderId}_") }?.forEach { it.delete() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }

    /** Last cached photo of this sender (used when the network is slow). */
    private fun latestFor(context: Context, senderId: String): Bitmap? =
        dir(context).listFiles { f -> senderId.isNotBlank() && f.name.startsWith("${senderId}_") }
            ?.maxByOrNull { it.lastModified() }?.let { BitmapFactory.decodeFile(it.path) }

    private fun download(url: String): Bitmap? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000; readTimeout = 5000; doInput = true
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
    }.onFailure { Log.w("NOTIF_AVATAR", "avatar download failed: ${it.javaClass.simpleName}") }.getOrNull()
}
