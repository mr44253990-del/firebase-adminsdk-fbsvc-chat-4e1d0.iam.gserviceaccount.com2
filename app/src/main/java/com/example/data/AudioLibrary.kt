package com.example.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** One playable item from the audio library (a song preview, a full track or a radio station). */
data class AudioTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val url: String = "",
    val artwork: String = "",
    /** "audius" | "itunes" | "radio" */
    val source: String = "",
    val durationSec: Int = 0
)

/**
 * Audio library (v5.3). Three free catalogues, switched on and configured by the admin:
 *  - Audius        — full tracks from independent artists (needs only an app name)
 *  - iTunes Search — 30-second previews of commercial songs (no key)
 *  - Radio Browser — live radio stations from around the world (no key)
 * Nothing is uploaded: a post or story only stores the stream address and the track details.
 */
object AudioLibraryApi {
    private val http by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
    private val radioHosts = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info")

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", "ConvoChat/5.3 (Android)").header("Accept", "application/json").build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    suspend fun search(source: String, query: String, config: FlagshipConfig): List<AudioTrack> = withContext(Dispatchers.IO) {
        when (source) {
            "audius" -> audius(query.trim(), config)
            "itunes" -> itunes(query.trim(), config)
            "radio" -> radio(query.trim())
            else -> emptyList()
        }
    }

    private fun audius(query: String, config: FlagshipConfig): List<AudioTrack> {
        val host = config.audioAudiusHost.trim().trimEnd('/').ifBlank { "https://discoveryprovider.audius.co" }
        val app = enc(config.audioAudiusAppName.ifBlank { "ConvoChat" })
        val url = if (query.isBlank()) "$host/v1/tracks/trending?app_name=$app&limit=30" else "$host/v1/tracks/search?query=${enc(query)}&app_name=$app&limit=30"
        val data = get(url)?.let { runCatching { JSONObject(it).optJSONArray("data") }.getOrNull() } ?: return emptyList()
        return List(data.length()) { i ->
            val t = data.getJSONObject(i)
            val id = t.optString("id")
            AudioTrack(
                id = "audius_$id", title = t.optString("title"), artist = t.optJSONObject("user")?.optString("name").orEmpty(),
                url = "$host/v1/tracks/$id/stream?app_name=$app",
                artwork = t.optJSONObject("artwork")?.let { a -> a.optString("150x150").ifBlank { a.optString("480x480") } }.orEmpty(),
                source = "audius", durationSec = t.optInt("duration")
            )
        }.filter { it.title.isNotBlank() && it.id != "audius_" }
    }

    private fun itunes(query: String, config: FlagshipConfig): List<AudioTrack> {
        val country = config.audioItunesCountry.trim().uppercase().takeIf { it.length == 2 } ?: "US"
        val term = enc(query.ifBlank { "top hits" })
        val results = get("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=30&country=$country")
            ?.let { runCatching { JSONObject(it).optJSONArray("results") }.getOrNull() } ?: return emptyList()
        return List(results.length()) { i ->
            val t = results.getJSONObject(i)
            AudioTrack(
                id = "itunes_" + t.optLong("trackId"), title = t.optString("trackName"), artist = t.optString("artistName"),
                url = t.optString("previewUrl"), artwork = t.optString("artworkUrl100"), source = "itunes", durationSec = 30
            )
        }.filter { it.url.startsWith("http") && it.title.isNotBlank() }
    }

    private fun radio(query: String): List<AudioTrack> {
        val path = if (query.isBlank()) "/json/stations/search?limit=30&hidebroken=true&order=clickcount&reverse=true&countrycode=BD"
        else "/json/stations/search?name=${enc(query)}&limit=30&hidebroken=true&order=clickcount&reverse=true"
        for (host in radioHosts) {
            val body = get(host + path) ?: continue
            val array = runCatching { JSONArray(body) }.getOrNull() ?: continue
            val list = List(array.length()) { i ->
                val s = array.getJSONObject(i)
                AudioTrack(
                    id = "radio_" + s.optString("stationuuid"), title = s.optString("name").trim(),
                    artist = listOf(s.optString("country"), s.optString("tags").split(',').take(2).joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
                    url = s.optString("url_resolved").ifBlank { s.optString("url") }, artwork = s.optString("favicon"), source = "radio"
                )
            }.filter { it.url.startsWith("https://") && it.title.isNotBlank() }
            if (list.isNotEmpty() || query.isNotBlank()) return list
        }
        return emptyList()
    }

    /** Quick health check for the admin page: number of results per catalogue. */
    suspend fun test(config: FlagshipConfig): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf("Audius" to audius("love", config).size, "iTunes" to itunes("love", config).size, "Radio Browser" to radio("bbc").size)
    }
}

/** One shared streaming player so only a single preview / post track / story song plays at a time. */
object AudioStreamPlayer {
    private var player: MediaPlayer? = null
    private val _playingUrl = MutableStateFlow("")
    /** Address of the stream that is playing or loading right now ("" = nothing). */
    val playingUrl: StateFlow<String> = _playingUrl
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun toggle(url: String) { if (_playingUrl.value == url) stop() else play(url) }

    fun play(url: String) {
        stop()
        if (!url.startsWith("http")) return
        _playingUrl.value = url; _loading.value = true
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(url)
                setOnPreparedListener { if (_playingUrl.value == url) { _loading.value = false; it.start() } }
                setOnCompletionListener { if (_playingUrl.value == url) stop() }
                setOnErrorListener { _, _, _ -> if (_playingUrl.value == url) stop(); true }
                prepareAsync()
            }
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { player?.reset(); player?.release() }
        player = null; _playingUrl.value = ""; _loading.value = false
    }
}
