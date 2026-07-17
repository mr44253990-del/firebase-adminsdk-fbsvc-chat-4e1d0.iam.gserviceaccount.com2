package com.example.data

import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Supabase Storage manager – every media file (avatars, post media, stories,
 * voice notes, group photos) lives in Supabase Storage while the structured
 * data lives in Firebase. Deleting content removes it from BOTH places.
 *
 * Buckets (create as PUBLIC in the Supabase dashboard – see SUPABASE_SETUP.md):
 *   avatars · posts · stories · voice · groups
 */
object SupabaseManager {

    const val SUPABASE_URL = "https://srfztgcdejfaesrvkarg.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_BcH2xwywnUCVG48LYjPOLQ_8-y2InGA"

    const val BUCKET_AVATARS = "avatars"
    const val BUCKET_POSTS = "posts"
    const val BUCKET_STORIES = "stories"
    const val BUCKET_VOICE = "voice"
    const val BUCKET_GROUPS = "groups"

    val client by lazy {
        try {
            createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_KEY
            ) {
                install(Postgrest)
                install(Storage)
            }
        } catch (e: Exception) {
            Log.e("Supabase", "Client init failed: ${e.message}")
            null
        }
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun publicUrl(bucket: String, path: String): String =
        "$SUPABASE_URL/storage/v1/object/public/$bucket/$path"

    /**
     * Uploads bytes to Supabase Storage using the official supabase-kt client,
     * with a raw REST fallback so a misconfiguration never crashes the app.
     * Returns the public URL or null on failure.
     */
    suspend fun upload(
        bucket: String,
        path: String,
        bytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): String? = withContext(Dispatchers.IO) {
        // 1) Official supabase-kt client
        try {
            val c = client
            if (c != null) {
                c.storage.from(bucket).upload(path, bytes) { upsert = true }
                return@withContext c.storage.from(bucket).publicUrl(path)
            }
        } catch (e: Exception) {
            Log.w("Supabase", "supabase-kt upload failed, trying REST: ${e.message}")
        }
        // 2) REST fallback (same storage, zero extra deps)
        try {
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val request = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/$bucket/$path")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .addHeader("Content-Type", mimeType)
                .addHeader("x-upsert", "true")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) return@withContext publicUrl(bucket, path)
                Log.e("Supabase", "REST upload failed: ${resp.code} ${resp.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("Supabase", "REST upload error: ${e.message}")
        }
        null
    }

    /** Deletes a stored object. Safe – never throws. */
    suspend fun delete(bucket: String, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = client
            if (c != null) {
                c.storage.from(bucket).delete(path)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w("Supabase", "supabase-kt delete failed, trying REST: ${e.message}")
        }
        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/$bucket/$path")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .delete()
                .build()
            http.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("Supabase", "REST delete error: ${e.message}")
            false
        }
    }

    /** Extracts "bucket/path" from a public URL so we can delete it later. */
    fun bucketAndPathFromUrl(url: String): Pair<String, String>? {
        return try {
            val marker = "/storage/v1/object/public/"
            val idx = url.indexOf(marker)
            if (idx < 0) return null
            val rest = url.substring(idx + marker.length)
            val slash = rest.indexOf('/')
            if (slash < 0) return null
            rest.substring(0, slash) to rest.substring(slash + 1)
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes media referenced by a public URL (used when posts/stories/messages are removed). */
    suspend fun deleteByUrl(url: String) {
        if (url.isBlank()) return
        bucketAndPathFromUrl(url)?.let { (bucket, path) -> delete(bucket, path) }
    }
}
