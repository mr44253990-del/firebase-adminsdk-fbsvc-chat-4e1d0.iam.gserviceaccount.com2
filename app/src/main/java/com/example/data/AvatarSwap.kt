package com.example.data

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Seamless profile pictures. Every image the app shows is already kept on the phone by the
 * image cache. The one visible "loading" moment was when a friend changed their picture: the
 * list switched to the new address at once and showed an empty circle until it downloaded.
 *
 * This keeps showing the picture that is already on the phone, downloads the new one quietly
 * in the background, and swaps to it only when it is ready — so nothing ever appears to load.
 */
object AvatarSwap {
    private const val PREFS = "convo_avatar_swap"
    private val shown = ConcurrentHashMap<String, String>()
    private val loading = ConcurrentHashMap<String, String>()
    @Volatile private var restored = false

    private fun restore(context: Context) {
        if (restored) return
        restored = true
        runCatching {
            val json = JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("shown", "{}") ?: "{}")
            json.keys().forEach { key -> shown[key] = json.optString(key) }
        }
    }

    @Volatile private var lastPersistAt = 0L

    /** Writes at most once every few seconds; a full user list would otherwise save hundreds of times. */
    private fun persist(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 4000L) return
        lastPersistAt = now
        runCatching {
            val json = JSONObject()
            shown.entries.take(1500).forEach { json.put(it.key, it.value) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shown", json.toString()).apply()
        }
    }

    /**
     * Returns the address that should be displayed right now for [uid]. When [latest] is new,
     * the previous (cached) picture is returned and [onReady] fires once the new one is stored.
     */
    fun display(context: Context, uid: String, latest: String, onReady: (uid: String, url: String) -> Unit): String {
        if (uid.isBlank() || !latest.startsWith("http")) return latest
        val app = context.applicationContext
        restore(app)
        val current = shown[uid]
        if (current == null || current.isBlank()) { shown[uid] = latest; persist(app); return latest }
        if (current == latest) return latest
        if (loading[uid] != latest) {
            loading[uid] = latest
            val request = ImageRequest.Builder(app).data(latest)
                .listener(
                    onSuccess = { _, _ ->
                        if (loading[uid] == latest) {
                            loading.remove(uid); shown[uid] = latest; persist(app, force = true)
                            onReady(uid, latest)
                        }
                    },
                    onError = { _, _ ->
                        // Could not download (offline?): keep the old picture and retry on the next update.
                        loading.remove(uid)
                    }
                ).build()
            app.imageLoader.enqueue(request)
        }
        return current
    }
}
