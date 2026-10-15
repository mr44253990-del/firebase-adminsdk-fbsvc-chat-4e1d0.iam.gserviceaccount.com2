package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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

    fun suggestions(query: String, users: List<User>, excludeUid: String, limit: Int = 6): List<User> {
        val q = query.lowercase()
        return users.asSequence()
            .filter { it.uid != excludeUid && it.username.isNotBlank() }
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

    fun annotate(text: String, linkColor: Color, mentionColor: Color): AnnotatedString = buildAnnotatedString {
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
    maxLines: Int = Int.MAX_VALUE
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, mentionColor) { Mentions.annotate(text, Color(0xFF71C7FF), mentionColor) }
    val firstUrl = remember(text) { LinkPreviewRepository.firstUrl(text) }
    Column(modifier) {
        ClickableText(
            text = annotated,
            style = style.copy(color = color),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                    runCatching { uriHandler.openUri(it.item) }
                    return@ClickableText
                }
                annotated.getStringAnnotations("MENTION", offset, offset).firstOrNull()?.let { onMentionClick(it.item) }
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
                "Mention someone",
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
