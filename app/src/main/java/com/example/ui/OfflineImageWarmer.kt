package com.example.ui

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Warms Coil's persistent app-private disk cache for recently used public images while online.
 * The cache lives in filesDir (not cacheDir), so Android's cache cleaner does not discard it.
 * Coil remains size-bounded; current/recent images are prioritized by the caller.
 */
object OfflineImageWarmer {
    private const val REFRESH_COOLDOWN_MS = 6L * 60L * 60L * 1000L
    private val lastAttemptAt = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun prefetch(context: Context, urls: Iterable<String>, limit: Int = 400) {
        val appContext = context.applicationContext
        val loader = Coil.imageLoader(appContext)
        val now = System.currentTimeMillis()
        urls.asSequence()
            .map(String::trim)
            .filter { it.startsWith("https://", ignoreCase = true) }
            .distinct()
            .take(limit)
            .forEach { url ->
                val previousAttempt = lastAttemptAt[url] ?: 0L
                if (now - previousAttempt < REFRESH_COOLDOWN_MS || !inFlight.add(url)) return@forEach
                lastAttemptAt[url] = now
                val request = ImageRequest.Builder(appContext)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    // Bypass an old disk entry while warming: normal UI requests can display
                    // cached pixels immediately as this request refreshes the persistent entry.
                    .diskCachePolicy(CachePolicy.WRITE_ONLY)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .listener(
                        onSuccess = { _, _ -> inFlight.remove(url) },
                        onError = { _, _ -> inFlight.remove(url) },
                        onCancel = { inFlight.remove(url) }
                    )
                    .build()
                loader.enqueue(request)
            }
    }
}
