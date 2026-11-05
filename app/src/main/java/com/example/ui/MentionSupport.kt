package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.User

/** @mention helpers shared by direct chats, group chats and posts. */
object Mentions {
    /** Matches @handle where handle is a username (letters, digits, dot, underscore). */
    val mentionRegex = Regex("(?<![A-Za-z0-9_.])@([A-Za-z0-9_.]{2,30})")
    private val urlRegex = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    /** Returns the partial handle being typed at the end of [text], e.g. "@sa" -> "sa". */
    fun activeQuery(text: String): String? {
        val match = Regex("(?:^|\\s)@([A-Za-z0-9_.]{0,30})$").find(text) ?: return null
        return match.groupValues[1]
    }

    /** Friends of the signed-in user. Mentions everywhere only suggest these people. */
    @Volatile var friendIds: Set<String> = emptySet()

    fun suggestions(query: String, users: List<User>, excludeUid: String, limit: Int = 6): List<User> {
        val q = query.lowercase().trim()
        val friends = friendIds
        return users.asSequence()
            .filter { it.uid != excludeUid && it.username.isNotBlank() && it.uid in friends }
            .distinctBy { it.uid }
            .filter { q.isBlank() || it.username.lowercase().startsWith(q) || it.name.lowercase().contains(q) }
            .sortedWith(compareBy<User>({ !it.username.lowercase().startsWith(q) }, { it.name.lowercase() }))
            .take(limit)
            .toList()
    }

    /** Replaces the trailing "@partial" with "@username ". */
    fun complete(text: String, user: User): String {
        val match = Regex("@([A-Za-z0-9_.]{0,30})$").find(text) ?: return "$text@${user.username} "
        return text.substring(0, match.range.first) + "@" + user.username + " "
    }

    fun findUser(handle: String, users: List<User>): User? =
        users.firstOrNull { it.username.equals(handle, ignoreCase = true) }

    fun mentionedUsernames(text: String): Set<String> =
        mentionRegex.findAll(text).map { it.groupValues[1].lowercase().trimEnd('.') }.toSet()

    private val formatRules: List<Pair<Regex, SpanStyle>> by lazy {
        listOf(
            Regex("(?<![\\w*])\\*(?![\\s*])([^*\\n]+?)(?<!\\s)\\*(?![\\w*])") to SpanStyle(fontWeight = FontWeight.Bold),
            Regex("(?<![\\w_])_(?![\\s_])([^_\\n]+?)(?<!\\s)_(?![\\w_])") to SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            Regex("(?<![\\w~])~(?![\\s~])([^~\\n]+?)(?<!\\s)~(?![\\w~])") to SpanStyle(textDecoration = TextDecoration.LineThrough),
            Regex("`([^`\\n]+)`") to SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, background = Color(0x33808080))
        )
    }

    /**
     * WhatsApp/Signal-style inline formatting: *bold*, _italic_, ~strike~ and `code`.
     * Returns the text without the marker characters plus the style ranges (in the new text).
     * Markers inside links are ignored so URLs with underscores stay intact.
     */
    fun parseFormatting(text: String): Pair<String, List<Triple<Int, Int, SpanStyle>>> {
        if (text.none { it == '*' || it == '_' || it == '~' || it == '`' }) return text to emptyList()
        val urls = urlRegex.findAll(text).map { it.range }.toList()
        data class M(val start: Int, val end: Int, val style: SpanStyle) // end inclusive (closing marker)
        val found = mutableListOf<M>()
        formatRules.forEach { (rx, style) ->
            rx.findAll(text).forEach { m ->
                val r = m.range
                if (urls.none { r.first <= it.last && r.last >= it.first } && found.none { r.first <= it.end && r.last >= it.start }) found += M(r.first, r.last, style)
            }
        }
        if (found.isEmpty()) return text to emptyList()
        found.sortBy { it.start }
        val out = StringBuilder(text.length)
        val styles = mutableListOf<Triple<Int, Int, SpanStyle>>()
        var cursor = 0
        found.forEach { m ->
            out.append(text, cursor, m.start)
            val from = out.length
            out.append(text, m.start + 1, m.end)
            styles += Triple(from, out.length, m.style)
            cursor = m.end + 1
        }
        out.append(text, cursor, text.length)
        return out.toString() to styles
    }

    fun annotate(rawText: String, linkColor: Color, mentionColor: Color): AnnotatedString = buildAnnotatedString {
        val (text, formatStyles) = parseFormatting(rawText)
        data class Span(val start: Int, val end: Int, val tag: String, val value: String)
        val spans = mutableListOf<Span>()
        urlRegex.findAll(text).forEach { m ->
            val clean = m.value.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')
            spans += Span(m.range.first, m.range.first + clean.length, "URL", clean)
        }
        mentionRegex.findAll(text).forEach { m ->
            val handle = m.groupValues[1].trimEnd('.')
            val end = m.range.first + 1 + handle.length
            if (spans.none { m.range.first < it.end && end > it.start }) spans += Span(m.range.first, end, "MENTION", handle)
        }
        spans.sortBy { it.start }
        var cursor = 0
        spans.forEach { span ->
            if (span.start < cursor) return@forEach
            append(text.substring(cursor, span.start))
            pushStringAnnotation(span.tag, span.value)
            if (span.tag == "URL") {
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
            } else {
                pushStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold, background = mentionColor.copy(alpha = .12f)))
            }
            append(text.substring(span.start, span.end))
            pop(); pop()
            cursor = span.end
        }
        if (cursor < text.length) append(text.substring(cursor))
        formatStyles.forEach { (start, end, style) -> if (start < end && end <= length) addStyle(style, start, end) }
    }
}

/**
 * Message/post text with clickable links and @mentions, plus a rich preview for the
 * first link.
 */
@Composable
fun RichMessageText(
    text: String,
    color: Color,
    onMentionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    showLinkPreview: Boolean = true,
    compactPreview: Boolean = false,
    mentionColor: Color = Color(0xFF8AB4FF),
    maxLines: Int = Int.MAX_VALUE,
    onPlainClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val annotated = remember(text, mentionColor) { Mentions.annotate(text, Color(0xFF71C7FF), mentionColor) }
    val firstUrl = remember(text) { LinkPreviewRepository.firstUrl(text) }
    var layout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val latestPlain = androidx.compose.runtime.rememberUpdatedState(onPlainClick)
    val latestLong = androidx.compose.runtime.rememberUpdatedState(onLongPress)
    val latestDouble = androidx.compose.runtime.rememberUpdatedState(onDoubleTap)
    Column(modifier) {
        // Plain Text + our own gesture detector: taps open links/mentions, long-press on a link
        // copies it, and every other gesture is forwarded to the bubble (menu, copy, …). The
        // old ClickableText swallowed long-presses, so bubbles with text could not be copied.
        Text(
            text = annotated,
            style = style.copy(color = color),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier.pointerInput(annotated) {
                detectTapGestures(
                    onTap = { pos ->
                        val offset = layout?.getOffsetForPosition(pos)
                        val url = offset?.let { annotated.getStringAnnotations("URL", it, it).firstOrNull() }
                        val mention = offset?.let { annotated.getStringAnnotations("MENTION", it, it).firstOrNull() }
                        when {
                            url != null -> runCatching { uriHandler.openUri(url.item) }
                            mention != null -> onMentionClick(mention.item)
                            else -> latestPlain.value?.invoke()
                        }
                    },
                    onLongPress = { pos ->
                        val offset = layout?.getOffsetForPosition(pos)
                        val url = offset?.let { annotated.getStringAnnotations("URL", it, it).firstOrNull() }
                        if (url != null) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Link", url.item))
                            android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val handler = latestLong.value
                            if (handler != null) {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                handler()
                            } else {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Text", text))
                                android.widget.Toast.makeText(context, "Text copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDoubleTap = if (onDoubleTap != null) { _ -> latestDouble.value?.invoke() } else null
                )
            }
        )
        if (showLinkPreview && !firstUrl.isNullOrBlank()) {
            RichLinkPreviewCard(firstUrl, color, compact = compactPreview)
        }
    }
}

/** Floating list of people to tag while typing "@". */
@Composable
fun MentionSuggestionBar(
    suggestions: List<User>,
    onPick: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                "Mention a friend",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
            suggestions.forEach { user ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(user) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        if (user.profileImageUrl.isNotBlank()) {
                            AsyncImage(user.profileImageUrl, user.name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name.ifBlank { user.username }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${user.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
            }
        }
    }
}
