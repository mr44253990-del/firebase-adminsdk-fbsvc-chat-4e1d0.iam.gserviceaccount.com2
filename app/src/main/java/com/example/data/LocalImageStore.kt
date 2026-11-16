package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Permanent on-phone copies of chat photos (v5.7). When the admin turns on "delete photos from
 * the server after delivery", the sender keeps the photo it uploaded and the receiver keeps the
 * photo it downloaded here; only then is the server copy removed. Chat bubbles always prefer
 * the local copy, so the photo keeps showing after the server no longer has it.
 */
object LocalImageStore {
    /** Admin switch mirrored from the app configuration. */
    val deleteAfterDelivery = MutableStateFlow(false)
    private val http by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "chat_media").apply { mkdirs() }
    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.substringBefore('?').toByteArray()).joinToString("") { "%02x".format(it) }

    /** The saved copy of [url], or null when there is none. */
    fun existing(context: Context, url: String?): File? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return null
        return File(dir(context), key(url)).takeIf { it.exists() && it.length() > 0 }
    }

    suspend fun save(context: Context, url: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir(context), key(url))
            val tmp = File(target.path + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) { target.writeBytes(bytes); tmp.delete() }
            true
        }.getOrDefault(false)
    }

    /** Downloads [url] into the store (no-op when it is already there). True when a copy exists afterwards. */
    suspend fun download(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        if (existing(context, url) != null) return@withContext true
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val bytes = response.body?.bytes() ?: return@use false
                if (bytes.isEmpty() || bytes.size > 40 * 1024 * 1024) return@use false
                val target = File(dir(context), key(url))
                target.writeBytes(bytes)
                true
            }
        }.getOrDefault(false)
    }
}
