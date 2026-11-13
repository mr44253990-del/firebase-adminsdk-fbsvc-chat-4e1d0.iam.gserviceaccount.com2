package com.example.ui

import com.example.data.Post
import com.example.data.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Database tools" for Convo AI (v5.0). The assistant is allowed to look things up in the
 * app's own data on the user's behalf — profiles, community numbers, the user's own content —
 * and answer with real facts instead of guessing.
 *
 * Privacy: only what the signed-in user can already see in the app is used (public profile
 * fields of discoverable people, their own account data). The admin additionally gets
 * sign-in e-mails, which come from the admin-only directory.
 */
object AssistantDatabaseTools {
    private val STOP_WORDS = setOf(
        "find", "search", "user", "users", "please", "show", "me", "the", "a", "an", "of", "profile", "profiles", "who", "is", "about", "tell", "info",
        "information", "details", "account", "id", "and", "for", "with", "in", "on", "what", "does", "do", "have", "has", "how", "many", "his", "her", "their",
        "ইউজার", "খুঁজে", "খুঁজ", "বের", "করো", "কর", "দাও", "নামে", "নাম", "কে", "আছে", "প্রোফাইল", "সম্পর্কে", "বলো", "তথ্য", "এর", "কি", "কী", "কত", "একটা", "আমাকে", "দেখাও", "আইডি"
    )
    private val PROFILE_HINTS = listOf(
        "find", "search", "profile", "who is", "look up", "lookup", "user", "@", "খুঁজ", "প্রোফাইল", "ইউজার", "কে এই", "সম্পর্কে", "আইডি", "তথ্য দাও"
    )

    fun looksLikeProfileQuestion(prompt: String): Boolean {
        val q = prompt.lowercase()
        return PROFILE_HINTS.any { q.contains(it) }
    }

    /** Ranked profile search over name, @username, bio and title. */
    fun searchProfiles(users: List<User>, prompt: String, limit: Int = 8): List<User> {
        val tokens = prompt.lowercase().split(Regex("[^\\p{L}\\p{N}_.]+")).map { it.removePrefix("@") }.filter { it.length >= 2 && it !in STOP_WORDS }
        if (tokens.isEmpty()) return emptyList()
        return users.mapNotNull { user ->
            val name = user.name.lowercase(); val username = user.username.lowercase()
            var score = 0
            tokens.forEach { t ->
                score += when {
                    username == t -> 12
                    name == t -> 10
                    name.split(' ').any { it == t } -> 7
                    username.startsWith(t) || name.startsWith(t) -> 5
                    t.length >= 3 && (username.contains(t) || name.contains(t)) -> 3
                    t.length >= 4 && (user.bio.lowercase().contains(t) || user.professionalTitle.lowercase().contains(t)) -> 1
                    else -> 0
                }
            }
            if (score > 0) user to score else null
        }.sortedWith(compareByDescending<Pair<User, Int>> { it.second }.thenByDescending { it.first.followers.size }).take(limit).map { it.first }
    }

    private fun date(ms: Long) = if (ms > 0) SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(ms)) else "unknown"

    fun describe(user: User, posts: List<Post>, email: String? = null): String = buildString {
        append("- ").append(user.name.ifBlank { "Unnamed" }).append(" (@").append(user.username).append(")")
        if (user.professionalTitle.isNotBlank()) append(", ").append(user.professionalTitle)
        append("; ").append(if (user.isOnline) "online now" else "last active ${date(user.lastActive)}")
        append("; joined ").append(date(user.createdAt))
        append("; followers ").append(user.followers.size).append(", following ").append(user.following.size).append(", friends ").append(user.friends.size)
        append("; posts ").append(posts.count { it.senderId == user.uid })
        if (user.isPremium) append("; Premium member")
        if (user.role != "user" || user.staffRole.isNotBlank()) append("; staff/admin")
        if (user.bio.isNotBlank()) append("; bio: ").append(user.bio.take(160).replace('\n', ' '))
        if (!email.isNullOrBlank()) append("; e-mail: ").append(email)
    }

    /**
     * Context block handed to the model. Returns null when the question does not touch app data,
     * so ordinary chats stay small and fast.
     */
    fun buildContext(prompt: String, me: User?, users: List<User>, posts: List<Post>, matches: List<User>, emails: Map<String, String> = emptyMap()): String? {
        val q = prompt.lowercase()
        val wantsCommunity = listOf("how many users", "total users", "members", "community", "online now", "কতজন", "কত জন", "মোট ইউজার", "অনলাইনে").any { q.contains(it) }
        val wantsFriends = listOf("my friends", "friend list", "আমার বন্ধু", "ফ্রেন্ড লিস্ট").any { q.contains(it) }
        if (matches.isEmpty() && !wantsCommunity && !wantsFriends) return null
        return buildString {
            append("convo_database_results (live data from the Convo Chat database, fetched for this question; treat as facts, do not invent anything beyond it):\n")
            if (matches.isNotEmpty()) {
                append("Matching profiles:\n")
                matches.forEach { append(describe(it, posts, emails[it.uid])).append('\n') }
            }
            if (wantsCommunity) {
                append("Community: ").append(users.size + 1).append(" discoverable profiles, ")
                    .append(users.count { it.isOnline }).append(" online right now, ")
                    .append(posts.size).append(" recent posts in the feed.\n")
            }
            if (wantsFriends && me != null) {
                val friends = users.filter { it.uid in me.friends }
                append("The user's friends (").append(friends.size).append("): ")
                    .append(friends.take(40).joinToString { "${it.name} (@${it.username})${if (it.isOnline) " [online]" else ""}" }).append('\n')
            }
            append("When you mention a person, give their name and @username. The app shows tappable profile cards under your answer.")
        }.take(3800)
    }
}
