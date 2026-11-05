package com.example.ui

import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.security.PrivacyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LinkPreviewData(
    val url: String,
    val title: String,
    val description: String,
    val image: String,
    val icon: String,
    val site: String,
    val isVideo: Boolean = false
)

/**
 * Fetches Open Graph previews. The Convo Worker is tried first; if it is unavailable the
 * phone reads the page head directly. Results (including failures) are cached in memory.
 * Respects Privacy → "Link previews".
 */
object LinkPreviewRepository {
    private const val WORKER = "https://solitary-hill-dcdc.mr44253990.workers.dev/link-preview?url="
    private val cache = LruCache<String, LinkPreviewData>(120)
    private val failed = LruCache<String, Long>(120)
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(7, TimeUnit.SECONDS).followRedirects(true).build()
    }
    private val urlRegex = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun firstUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')

    fun cached(url: String): LinkPreviewData? = cache.get(url)

    suspend fun load(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        failed.get(url)?.let { if (System.currentTimeMillis() - it < 5 * 60_000L) return@withContext null }
        val host = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
        val youtubeId = youtubeId(url)
        val result = fromWorker(url, host) ?: fromPage(url, host)
        val enriched = when {
            result == null && youtubeId != null -> LinkPreviewData(url, "YouTube video", "Watch on YouTube", "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg", favicon(url), "YouTube", true)
            result != null && youtubeId != null && result.image.isBlank() -> result.copy(image = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg", isVideo = true)
            result != null && youtubeId != null -> result.copy(isVideo = true)
            else -> result
        }
        if (enriched != null) cache.put(url, enriched) else failed.put(url, System.currentTimeMillis())
        enriched
    }

    private fun fromWorker(url: String, host: String): LinkPreviewData? = runCatching {
        client.newCall(Request.Builder().url(WORKER + Uri.encode(url)).header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string().orEmpty())
            val title = json.optString("title").trim()
            val image = absolute(url, json.optString("image").trim())
            if (title.isBlank() && image.isBlank()) return@use null
            LinkPreviewData(
                url = url,
                title = title.ifBlank { host.ifBlank { "Shared link" } },
                description = json.optString("description").trim(),
                image = image,
                icon = absolute(url, json.optString("icon").trim()).ifBlank { favicon(url) },
                site = json.optString("site").trim().ifBlank { host }
            )
        }
    }.getOrNull()

    private fun fromPage(url: String, host: String): LinkPreviewData? = runCatching {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ConvoChatLinkPreview/1.0")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val type = response.header("Content-Type").orEmpty()
            if (type.startsWith("image/")) {
                return@use LinkPreviewData(url, host.ifBlank { "Image" }, "", url, favicon(url), host)
            }
            if (!type.contains("html", true)) return@use null
            val source = response.body?.source() ?: return@use null
            source.request(256 * 1024)
            val html = source.buffer.snapshot().utf8().take(256 * 1024)
            fun meta(vararg names: String): String {
                for (name in names) {
                    val a = Regex("<meta[^>]+(?:property|name)=[\"']${Regex.escape(name)}[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(html)
                    if (a != null) return decode(a.groupValues[1])
                    val b = Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']${Regex.escape(name)}[\"']", RegexOption.IGNORE_CASE).find(html)
                    if (b != null) return decode(b.groupValues[1])
                }
                return ""
            }
            val title = meta("og:title", "twitter:title").ifBlank {
                Regex("<title[^>]*>([^<]{1,300})</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::decode).orEmpty()
            }.trim()
            val description = meta("og:description", "twitter:description", "description").trim()
            val image = absolute(url, meta("og:image", "og:image:url", "twitter:image", "twitter:image:src"))
            val iconHref = Regex("<link[^>]+rel=[\"'][^\"']*icon[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"'][^\"']*icon[^\"']*[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            if (title.isBlank() && image.isBlank()) return@use null
            LinkPreviewData(
                url = url,
                title = title.ifBlank { host },
                description = description,
                image = image,
                icon = iconHref?.let { absolute(url, decode(it)) }?.ifBlank { null } ?: favicon(url),
                site = meta("og:site_name").ifBlank { host },
                isVideo = meta("og:type").startsWith("video", true)
            )
        }
    }.getOrNull()

    private fun decode(value: String) = value
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    private fun absolute(base: String, value: String): String {
        if (value.isBlank()) return ""
        if (value.startsWith("https://")) return value
        if (value.startsWith("http://")) return value.replaceFirst("http://", "https://")
        return runCatching { java.net.URI(base).resolve(value).toString() }.getOrDefault("")
            .let { if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it }
    }

    fun favicon(url: String): String = runCatching {
        val uri = Uri.parse(url)
        "https://${uri.host}/favicon.ico"
    }.getOrDefault("")

    private fun youtubeId(url: String): String? {
        val match = Regex("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{6,15})", RegexOption.IGNORE_CASE).find(url)
        return match?.groupValues?.get(1)
    }
}

@Composable
fun rememberLinkPreview(url: String?): LinkPreviewData? {
    val privacy by PrivacyPreferences.settings.collectAsState()
    if (url.isNullOrBlank() || !privacy.allowLinkPreviewFetch) return null
    val state by produceState(initialValue = LinkPreviewRepository.cached(url), key1 = url) {
        if (value == null) value = LinkPreviewRepository.load(url)
    }
    return state
}

/**
 * Rich preview card with large image (or site icon when the page has no image).
 * [compact] uses a horizontal layout that fits inside chat bubbles.
 */
@Composable
fun RichLinkPreviewCard(
    url: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = .38f)
) {
    val privacy by PrivacyPreferences.settings.collectAsState()
    val uriHandler = LocalUriHandler.current
    val host = remember(url) { runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("") }
    val preview = rememberLinkPreview(url)
    val loading = preview == null && privacy.allowLinkPreviewFetch
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 7.dp).clickable { runCatching { uriHandler.openUri(url) } },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = .12f))
    ) {
        Column {
            val image = preview?.image.orEmpty()
            if (image.isNotBlank() && !compact) {
                Box {
                    AsyncImage(model = image, contentDescription = preview?.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 190.dp).aspectRatio(1.91f))
                    if (preview?.isVideo == true) {
                        Icon(Icons.Default.PlayCircle, "Video", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(52.dp))
                    }
                }
            }
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (compact && image.isNotBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                } else {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(contentColor.copy(alpha = .08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Link, null, tint = Color(0xFF71C7FF), modifier = Modifier.size(18.dp))
                        val icon = preview?.icon.orEmpty()
                        if (icon.isNotBlank()) {
                            AsyncImage(model = icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        (preview?.site ?: host).ifBlank { host }.uppercase(),
                        fontSize = 9.sp, letterSpacing = .6.sp, fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = .58f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        preview?.title ?: if (loading) "Loading preview…" else host.ifBlank { "Shared link" },
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = contentColor,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    val description = preview?.description.orEmpty()
                    if (description.isNotBlank()) {
                        Text(description, fontSize = 11.sp, color = contentColor.copy(alpha = .75f), maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.OpenInNew, "Open link", tint = Color(0xFF71C7FF), modifier = Modifier.size(17.dp))
            }
        }
    }
}
