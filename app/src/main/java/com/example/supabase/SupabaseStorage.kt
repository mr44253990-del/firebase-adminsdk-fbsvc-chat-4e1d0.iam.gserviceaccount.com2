package com.example.supabase

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Convo Nexus file store.
 *
 * Every binary asset (avatars, post media, story media, group covers, voice notes)
 * lives in Supabase Storage while the text/graph data stays in Firebase. This keeps
 * Firestore documents tiny and makes media deletion a single authoritative call.
 *
 * Implemented directly on top of the OkHttp client that already ships with the app,
 * so no additional Gradle dependency is required and the release APK size is unchanged.
 * (The official `io.github.jan-tennert.supabase:supabase-kt` artifact pulls in Ktor +
 * kotlinx-serialization; the REST surface used here is identical.)
 */
object SupabaseConfig {
    const val URL = "https://srfztgcdejfaesrvkarg.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA"

    const val BUCKET_AVATARS = "avatars"
    const val BUCKET_POSTS = "posts"
    const val BUCKET_STORIES = "stories"
    const val BUCKET_VOICE = "voice"
    const val BUCKET_GROUPS = "groups"
}

data class StoredAsset(
    val bucket: String,
    val path: String,
    val publicUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

object SupabaseStorage {

    private const val TAG = "SupabaseStorage"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun publicUrl(bucket: String, path: String): String =
        "${SupabaseConfig.URL}/storage/v1/object/public/$bucket/${path.trimStart('/')}"

    /** Deterministic, collision-free object key: `uid/yyyyMM/uuid.ext`. */
    fun buildPath(ownerId: String, fileName: String?, fallbackExtension: String = "bin"): String {
        val safeOwner = ownerId.ifBlank { "anon" }.replace(Regex("[^A-Za-z0-9_-]"), "")
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)?.takeIf {
            it.isNotBlank() && it.length <= 5 && it.all(Char::isLetterOrDigit)
        } ?: fallbackExtension
        val month = java.text.SimpleDateFormat("yyyyMM", Locale.US).format(java.util.Date())
        return "$safeOwner/$month/${UUID.randomUUID()}.$ext"
    }

    /** Upload raw bytes. Returns null instead of throwing so callers can degrade gracefully. */
    suspend fun uploadBytes(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        upsert: Boolean = true
    ): StoredAsset? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${SupabaseConfig.URL}/storage/v1/object/$bucket/${path.trimStart('/')}")
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                .addHeader("x-upsert", upsert.toString())
                .addHeader("cache-control", "public, max-age=31536000, immutable")
                .post(bytes.toRequestBody(contentType.toMediaTypeOrNull()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "upload failed ${response.code}: ${response.body?.string()?.take(240)}")
                    return@withContext null
                }
                StoredAsset(bucket, path, publicUrl(bucket, path), bytes.size.toLong(), contentType)
            }
        }.getOrElse {
            Log.w(TAG, "upload crashed: ${it.message}")
            null
        }
    }

    /** Upload straight from a picker/camera [Uri] without buffering the whole file twice. */
    suspend fun uploadUri(
        context: Context,
        bucket: String,
        ownerId: String,
        uri: Uri,
        maxBytes: Long = 40L * 1024 * 1024
    ): StoredAsset? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val fallbackExt = when {
            mime.startsWith("image/") -> mime.substringAfter('/').take(4)
            mime.startsWith("video/") -> "mp4"
            mime.startsWith("audio/") -> "m4a"
            else -> "bin"
        }
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val sink = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return@runCatching null
                    sink.write(buffer, 0, read)
                }
                sink.toByteArray()
            }
        }.getOrNull() ?: return@withContext null

        uploadBytes(bucket, buildPath(ownerId, uri.lastPathSegment, fallbackExt), bytes, mime)
    }

    /** Hard-delete an object. Used by post/story/message delete so nothing is orphaned. */
    suspend fun delete(bucket: String, path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${SupabaseConfig.URL}/storage/v1/object/$bucket/${path.trimStart('/')}")
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Convenience for cleanup jobs that only stored the public URL. */
    suspend fun deleteByPublicUrl(url: String): Boolean {
        val marker = "/storage/v1/object/public/"
        val index = url.indexOf(marker)
        if (index < 0) return false
        val remainder = url.substring(index + marker.length)
        val bucket = remainder.substringBefore('/')
        val path = remainder.substringAfter('/')
        if (bucket.isBlank() || path.isBlank()) return false
        return delete(bucket, path)
    }
}
