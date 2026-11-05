package com.example.ui

import android.content.Context
import com.example.data.Post
import com.example.data.User
import org.json.JSONArray

/**
 * Smarter on-device search: multi-word matching, light typo tolerance, relevance ranking,
 * "@" (people) and "#" (hashtag) shortcuts, recent searches and trending hashtags.
 */
object SmartSearch {
    private const val PREFS = "convo_search"
    private const val KEY_RECENT = "recent"

    data class Query(val tokens: List<String>, val peopleOnly: Boolean, val tagOnly: Boolean)

    fun parse(raw: String): Query {
        val trimmed = raw.trim()
        val peopleOnly = trimmed.startsWith("@")
        val tagOnly = trimmed.startsWith("#")
        val tokens = trimmed.removePrefix("@").removePrefix("#").lowercase()
            .split(Regex("[\\s,]+")).map { it.trim('#', '@') }.filter { it.isNotBlank() }
        return Query(tokens, peopleOnly, tagOnly)
    }

    /** Levenshtein distance capped at 2 (cheap enough for on-device lists). */
    private fun closeEnough(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin > 1) return false
            prev = cur
        }
        return prev[b.length] <= 1
    }

    /** Score one token against one field: exact word > prefix > contains > typo match. */
    private fun tokenScore(token: String, field: String): Int {
        if (field.isBlank()) return 0
        val lower = field.lowercase()
        if (!lower.contains(token)) {
            if (token.length < 4) return 0
            return if (lower.split(Regex("\\W+")).any { it.length >= 3 && closeEnough(token, it) }) 2 else 0
        }
        val words = lower.split(Regex("\\W+"))
        return when {
            words.any { it == token } -> 10
            words.any { it.startsWith(token) } -> 7
            else -> 4
        }
    }

    /** Every token must match some field; the result sums token scores times field weights. */
    fun score(query: Query, fields: List<Pair<String, Int>>): Int {
        if (query.tokens.isEmpty()) return 0
        var total = 0
        for (token in query.tokens) {
            val best = fields.maxOfOrNull { (text, weight) -> tokenScore(token, text) * weight } ?: 0
            if (best == 0) return 0
            total += best
        }
        return total
    }

    fun rankUsers(query: Query, users: List<User>, now: Long = System.currentTimeMillis()): List<User> {
        if (query.tagOnly) return emptyList()
        return users.mapNotNull { user ->
            val s = score(query, listOf(user.name to 4, user.username to 4, user.bio to 1))
            if (s == 0) null else {
                val onlineBoost = if (user.isOnline && (user.onlineUntil <= 0L || user.onlineUntil > now)) 3 else 0
                user to (s + onlineBoost)
            }
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun rankPosts(query: Query, posts: List<Post>, now: Long = System.currentTimeMillis()): List<Post> {
        if (query.peopleOnly) return emptyList()
        return posts.mapNotNull { post ->
            val s = if (query.tagOnly) {
                score(query, post.tags.map { it to 5 })
            } else {
                score(query, listOf(post.title to 4, post.text to 2, post.feeling to 2, post.senderName to 2) + post.tags.map { it to 5 })
            }
            if (s == 0) null else {
                val ageDays = ((now - post.timestamp) / 86_400_000L).coerceAtLeast(0L)
                val recency = (10 - ageDays).coerceAtLeast(0L).toInt()
                val popularity = (post.reactions.size + post.comments.size).coerceAtMost(20) / 4
                post to (s + recency + popularity)
            }
        }.sortedByDescending { it.second }.map { it.first }
    }

    /** Most-used hashtags over the last 7 days. */
    fun trendingTags(posts: List<Post>, limit: Int = 10, now: Long = System.currentTimeMillis()): List<String> =
        posts.asSequence().filter { now - it.timestamp < 7L * 86_400_000L }
            .flatMap { it.tags.asSequence() }
            .map { it.trim().removePrefix("#").lowercase() }.filter { it.length in 2..30 }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .take(limit).map { it.key }

    fun recent(context: Context): List<String> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    fun remember(context: Context, query: String) {
        val clean = query.trim().take(60)
        if (clean.length < 2) return
        val next = (listOf(clean) + recent(context).filterNot { it.equals(clean, true) }).take(8)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RECENT, JSONArray(next).toString()).apply()
    }

    fun forget(context: Context, query: String) {
        val next = recent(context).filterNot { it == query }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RECENT, JSONArray(next).toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_RECENT).apply()
    }
}
