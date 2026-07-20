package com.example.data

import android.content.Context
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object ReelCacheManager {
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    private val inFlight = ConcurrentHashMap<String, MutableList<(String?) -> Unit>>()

    fun cachedPath(context: Context, postId: String): String? {
        val dir = File(context.filesDir, "reel_cache")
        return dir.listFiles()?.firstOrNull { it.name.startsWith("${safe(postId)}.") && !it.name.endsWith(".part") && it.length() > 0 }?.absolutePath
    }

    fun cache(context: Context, postId: String, url: String, onReady: (String?) -> Unit = {}) {
        cachedPath(context, postId)?.let { onReady(it); return }
        if (!url.startsWith("http")) { onReady(url); return }
        val callbacks = inFlight.putIfAbsent(postId, mutableListOf(onReady))
        if (callbacks != null) { synchronized(callbacks) { callbacks += onReady }; return }
        val dir = File(context.filesDir, "reel_cache").apply { mkdirs() }
        val ext = url.substringBefore('?').substringAfterLast('.', "mp4").take(5).ifBlank { "mp4" }
        val finalFile = File(dir, "${safe(postId)}.$ext")
        val part = File(dir, "${safe(postId)}.$ext.part")
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = finish(postId, null)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return finish(postId, null)
                    runCatching {
                        it.body?.byteStream()?.use { input -> part.outputStream().use(input::copyTo) }
                        if (part.length() > 0) { if (finalFile.exists()) finalFile.delete(); part.renameTo(finalFile) }
                    }.onFailure { part.delete() }
                }
                trim(dir)
                finish(postId, finalFile.takeIf { it.exists() }?.absolutePath)
            }
        })
    }

    private fun finish(postId: String, path: String?) {
        val callbacks = inFlight.remove(postId).orEmpty()
        callbacks.toList().forEach { runCatching { it(path) } }
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { !it.name.endsWith(".part") }?.sortedByDescending { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        files.drop(30).forEach { bytes -= it.length(); it.delete() }
        if (bytes > 1024L * 1024L * 1024L) {
            files.sortedBy { it.lastModified() }.forEach { if (bytes > 1024L * 1024L * 1024L) { bytes -= it.length(); it.delete() } }
        }
    }

    private fun safe(value: String) = value.replace("[^A-Za-z0-9._-]".toRegex(), "_")
}
