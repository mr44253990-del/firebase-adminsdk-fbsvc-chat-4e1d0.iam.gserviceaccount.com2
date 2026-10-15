package com.example.ui

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Device-local chat preferences. Nothing here is uploaded: the quick reaction, message text
 * size, default chat theme and custom sound overrides stay on this phone.
 */
object ChatPreferences {
    private const val FILE = "convo_chat_prefs"
    const val DEFAULT_REACTION = "👍"
    val reactionChoices = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "🙏", "🥰", "🎉", "💯", "✅")

    private val _quickReaction = MutableStateFlow(DEFAULT_REACTION)
    val quickReaction: StateFlow<String> = _quickReaction.asStateFlow()
    private val _textScale = MutableStateFlow(1f)
    val textScale: StateFlow<Float> = _textScale.asStateFlow()
    private val _defaultChatTheme = MutableStateFlow("Aurora")
    val defaultChatTheme: StateFlow<String> = _defaultChatTheme.asStateFlow()
    private val _enterToSend = MutableStateFlow(false)
    val enterToSend: StateFlow<Boolean> = _enterToSend.asStateFlow()
    private val _soundsVersion = MutableStateFlow(0)
    /** Increments whenever a custom/global sound file changes so the UI can refresh labels. */
    val soundsVersion: StateFlow<Int> = _soundsVersion.asStateFlow()

    private val _appearance = MutableStateFlow(ChatAppearance())
    /** Bubble style, spacing and time format used by one-to-one and group chats. */
    val appearance: StateFlow<ChatAppearance> = _appearance.asStateFlow()

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _quickReaction.value = prefs.getString("quick_reaction", DEFAULT_REACTION)?.takeIf { it.isNotBlank() } ?: DEFAULT_REACTION
        _textScale.value = prefs.getFloat("chat_text_size", 1f).coerceIn(0.85f, 1.3f)
        _defaultChatTheme.value = prefs.getString("default_chat_theme", "Aurora") ?: "Aurora"
        _enterToSend.value = prefs.getBoolean("enter_to_send", false)
        _appearance.value = ChatAppearance(
            bubbleStyle = prefs.getString("bubble_style", "auto")?.takeIf { it in ChatAppearance.STYLES } ?: "auto",
            borderWidth = prefs.getFloat("bubble_border", 1f),
            cornerRadius = prefs.getFloat("bubble_corner", 20f),
            tail = prefs.getBoolean("bubble_tail", true),
            maxWidthFraction = prefs.getFloat("bubble_max_width", .80f),
            sidePadding = prefs.getFloat("bubble_side_padding", 8f),
            innerPadding = prefs.getFloat("bubble_inner_padding", 10f),
            messageSpacing = prefs.getFloat("bubble_spacing", 6f),
            use24h = prefs.getBoolean("time_24h", false),
            backgroundDim = prefs.getFloat("background_dim", 0f),
            glow = prefs.getBoolean("bubble_glow", true),
            animateReactions = prefs.getBoolean("animate_reactions", true)
        ).sanitized()
        loaded = true
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setQuickReaction(context: Context, emoji: String) {
        val value = emoji.trim().take(16).ifBlank { DEFAULT_REACTION }
        _quickReaction.value = value
        prefs(context).edit().putString("quick_reaction", value).apply()
    }

    fun setTextScale(context: Context, scale: Float) {
        val value = scale.coerceIn(0.85f, 1.3f)
        _textScale.value = value
        prefs(context).edit().putFloat("chat_text_size", value).apply()
    }

    fun setDefaultChatTheme(context: Context, theme: String) {
        _defaultChatTheme.value = theme
        prefs(context).edit().putString("default_chat_theme", theme).apply()
    }

    fun setEnterToSend(context: Context, enabled: Boolean) {
        _enterToSend.value = enabled
        prefs(context).edit().putBoolean("enter_to_send", enabled).apply()
    }

    fun setAppearance(context: Context, value: ChatAppearance) {
        val next = value.sanitized()
        _appearance.value = next
        prefs(context).edit()
            .putString("bubble_style", next.bubbleStyle)
            .putFloat("bubble_border", next.borderWidth)
            .putFloat("bubble_corner", next.cornerRadius)
            .putBoolean("bubble_tail", next.tail)
            .putFloat("bubble_max_width", next.maxWidthFraction)
            .putFloat("bubble_side_padding", next.sidePadding)
            .putFloat("bubble_inner_padding", next.innerPadding)
            .putFloat("bubble_spacing", next.messageSpacing)
            .putBoolean("time_24h", next.use24h)
            .putFloat("background_dim", next.backgroundDim)
            .putBoolean("bubble_glow", next.glow)
            .putBoolean("animate_reactions", next.animateReactions)
            .apply()
    }

    fun resetAppearance(context: Context) = setAppearance(context, ChatAppearance())

    internal fun bumpSounds() { _soundsVersion.value = _soundsVersion.value + 1 }
}

/**
 * Custom sound storage.
 *  - A user's own MP3 (picked in Settings) is copied to private storage and always wins.
 *  - The admin can publish a global default in Firestore `app_config/sounds`; each phone
 *    downloads it quietly in the background and keeps it until the admin changes it.
 *  - Otherwise the built-in app sound is used.
 */
object AppSoundStore {
    const val NOTIFICATION = "notification"
    const val TYPING = "typing"
    private const val PREFS = "convo_chat_prefs"
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val MAX_DURATION_MS = 15_000L

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "sounds").apply { mkdirs() }
    private fun localFile(context: Context, kind: String) = File(dir(context), "local_$kind.audio")
    private fun globalFile(context: Context, kind: String) = File(dir(context), "global_$kind.audio")

    fun hasLocal(context: Context, kind: String) = localFile(context, kind).let { it.exists() && it.length() > 0 }
    fun hasGlobal(context: Context, kind: String) = globalFile(context, kind).let { it.exists() && it.length() > 0 }
    fun localName(context: Context, kind: String): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("local_${kind}_name", "Custom sound") ?: "Custom sound"

    /** Returns the file that should be played, or null when the built-in sound should be used. */
    fun activeFile(context: Context, kind: String): File? =
        localFile(context, kind).takeIf { it.exists() && it.length() > 0 }
            ?: globalFile(context, kind).takeIf { it.exists() && it.length() > 0 }

    fun describe(context: Context, kind: String): String = when {
        hasLocal(context, kind) -> localName(context, kind)
        hasGlobal(context, kind) -> "Convo default (admin)"
        else -> "Built-in sound"
    }

    /** Copies a picked audio file after validating size and length. Returns an error message or null. */
    suspend fun importLocal(context: Context, uri: Uri, kind: String, displayName: String): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return@withContext "Please choose a sound smaller than 2 MB"
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            } ?: return@withContext "Could not read that file"
            val temp = File(dir(context), "import_$kind.tmp")
            temp.writeBytes(bytes)
            val duration = runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(temp.absolutePath)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally { runCatching { r.release() } }
            }.getOrDefault(0L)
            if (duration <= 0L) { temp.delete(); return@withContext "That file is not a playable audio file" }
            if (duration > MAX_DURATION_MS) { temp.delete(); return@withContext "Please choose a sound shorter than 15 seconds" }
            val target = localFile(context, kind)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("local_${kind}_name", displayName.take(60)).apply()
            ChatPreferences.bumpSounds()
            null
        } catch (e: Throwable) {
            e.localizedMessage ?: "Could not import the sound"
        }
    }

    fun clearLocal(context: Context, kind: String) {
        localFile(context, kind).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("local_${kind}_name").apply()
        ChatPreferences.bumpSounds()
    }

    /** Background sync for the admin default. Blank URL removes the downloaded default. */
    suspend fun syncGlobal(context: Context, kind: String, url: String?) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "global_${kind}_url"
        val target = globalFile(context, kind)
        val clean = url?.trim().orEmpty()
        if (clean.isBlank() || !clean.startsWith("https://")) {
            if (target.exists()) { target.delete(); ChatPreferences.bumpSounds() }
            prefs.edit().remove(key).apply()
            return@withContext
        }
        if (prefs.getString(key, null) == clean && target.exists() && target.length() > 0) return@withContext
        try {
            val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(clean).build()).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body ?: return@use
                if (body.contentLength() > MAX_BYTES) return@use
                val temp = File(dir(context), "global_$kind.tmp")
                body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
                if (temp.length() in 1..MAX_BYTES) {
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
                    prefs.edit().putString(key, clean).apply()
                    ChatPreferences.bumpSounds()
                } else temp.delete()
            }
        } catch (e: Throwable) {
            Log.w("AppSoundStore", "Global $kind sound sync failed: ${e.message}")
        }
    }

    /** Plays the active custom sound. Returns false if the built-in sound should be used instead. */
    fun playCustom(context: Context, kind: String, volume: Float = 1f): Boolean {
        val file = activeFile(context, kind) ?: return false
        if (kind == NOTIFICATION) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return true
        }
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (kind == NOTIFICATION) AudioAttributes.USAGE_NOTIFICATION else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(volume, volume)
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
                prepare()
                start()
            }
            true
        } catch (e: Throwable) {
            Log.w("AppSoundStore", "Custom $kind sound failed: ${e.message}")
            false
        }
    }
}
