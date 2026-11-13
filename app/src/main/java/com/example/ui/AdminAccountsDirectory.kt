package com.example.ui

import com.example.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Sign-in facts for one account, visible to the admin only (comes from Firebase Auth via the Worker). */
data class AdminAccountInfo(
    val uid: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val phone: String = "",
    val disabled: Boolean = false,
    val createdAt: Long = 0L,
    val lastLoginAt: Long = 0L,
    val providers: List<String> = emptyList()
)

/**
 * Admin-only e-mail directory. Profile documents are readable by every signed-in user, so
 * e-mails are never stored there; the Worker reads them from Firebase Auth after checking that
 * the caller is the admin (or a sub-admin with the "users" permission).
 */
object AdminAccounts {
    private val _accounts = MutableStateFlow<Map<String, AdminAccountInfo>>(emptyMap())
    val accounts: StateFlow<Map<String, AdminAccountInfo>> = _accounts.asStateFlow()
    private val _status = MutableStateFlow("")
    /** "" = fine, otherwise a short human readable problem (e.g. Worker needs an update). */
    val status: StateFlow<String> = _status.asStateFlow()
    private val requested = HashSet<String>()
    private val lock = Mutex()

    private fun parse(array: JSONArray?): List<AdminAccountInfo> = if (array == null) emptyList() else List(array.length()) { i ->
        val o = array.getJSONObject(i)
        val p = o.optJSONArray("providers")
        AdminAccountInfo(
            uid = o.optString("uid"), email = o.optString("email"), emailVerified = o.optBoolean("emailVerified"),
            phone = o.optString("phone"), disabled = o.optBoolean("disabled"),
            createdAt = o.optLong("createdAt"), lastLoginAt = o.optLong("lastLoginAt"),
            providers = if (p == null) emptyList() else List(p.length()) { k -> p.optString(k) }
        )
    }.filter { it.uid.isNotBlank() }

    /** Loads e-mails for the given users (100 per request); already loaded ones are skipped. */
    suspend fun ensure(uids: List<String>) = lock.withLock {
        val missing = uids.filter { it.isNotBlank() && it !in requested }.distinct()
        missing.chunked(100).forEach { chunk ->
            requested += chunk
            val (code, json) = runCatching {
                convoWorkerPost("admin", "/admin/users/lookup", JSONObject().put("uids", JSONArray(chunk)))
            }.getOrElse { 0 to JSONObject() }
            if (code in 200..299) {
                _status.value = ""
                _accounts.value = _accounts.value + parse(json.optJSONArray("users")).associateBy { it.uid }
            } else {
                requested -= chunk.toSet()
                _status.value = when (code) {
                    404 -> "E-mails need the latest server: Admin → Server health → Update Worker."
                    403 -> "Only the admin can view account e-mails."
                    0 -> "Could not reach the server for e-mails."
                    else -> json.optString("error").ifBlank { "E-mail lookup failed (HTTP $code)" }
                }
                return@withLock
            }
        }
    }

    /** Exact e-mail → account, even when that profile page has not been loaded yet. */
    suspend fun findByEmail(email: String): AdminAccountInfo? {
        val (code, json) = runCatching {
            convoWorkerPost("admin", "/admin/users/lookup", JSONObject().put("email", email.trim().lowercase()))
        }.getOrElse { 0 to JSONObject() }
        if (code !in 200..299) return null
        val found = parse(json.optJSONArray("users")).firstOrNull() ?: return null
        _accounts.value = _accounts.value + (found.uid to found)
        return found
    }

    fun clear() { requested.clear(); _accounts.value = emptyMap(); _status.value = "" }
}

/**
 * Smart admin search. Every word must match; plain words search name, username, UID, e-mail,
 * phone, bio, device and app version. Operators narrow the list:
 *   is:online  is:offline  is:premium  is:free  is:staff  is:new (7 days)  is:inactive (30 days)
 *   is:verified  is:unverified  is:google  is:hidden  has:photo  no:photo  v:4.9  @gmail.com
 */
fun adminSmartUserMatch(user: User, account: AdminAccountInfo?, rawQuery: String, now: Long = System.currentTimeMillis()): Boolean {
    val tokens = rawQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    val online = user.isOnline && (user.onlineUntil <= 0L || user.onlineUntil > now)
    val premium = user.isPremium && (user.premiumPlan.equals("lifetime", true) || user.premiumUntil > now)
    val haystack = listOf(
        user.name, user.username, user.uid, account?.email.orEmpty(), account?.phone.orEmpty(), user.bio,
        user.deviceModel, user.appVersion, user.professionalTitle, user.publicContactEmail, user.staffTitle
    ).joinToString(" \u0001 ").lowercase()
    return tokens.all { token ->
        when {
            token == "is:online" -> online
            token == "is:offline" -> !online
            token == "is:premium" -> premium
            token == "is:free" -> !premium
            token == "is:staff" -> user.staffRole.isNotBlank() || user.role != "user"
            token == "is:new" -> user.createdAt > 0L && now - user.createdAt < 7L * 86_400_000L
            token == "is:inactive" -> user.lastActive > 0L && now - user.lastActive > 30L * 86_400_000L
            token == "is:verified" -> account?.emailVerified == true
            token == "is:unverified" -> account != null && !account.emailVerified
            token == "is:google" -> account?.providers?.any { it.contains("google") } == true
            token == "is:2fa" -> user.mfaEnabled
            token == "is:hidden" -> user.profileHidden
            token == "has:photo" -> user.profileImageUrl.isNotBlank()
            token == "no:photo" -> user.profileImageUrl.isBlank()
            token.startsWith("v:") -> user.appVersion.startsWith(token.removePrefix("v:"))
            token.startsWith("@") && token.contains('.') -> account?.email.orEmpty().lowercase().endsWith(token)
            else -> haystack.contains(token.removePrefix("@"))
        }
    }
}

/** Ranks better matches first: exact username / e-mail, then prefix, then anything else. */
fun adminSmartUserScore(user: User, account: AdminAccountInfo?, rawQuery: String): Int {
    val q = rawQuery.trim().lowercase().removePrefix("@")
    if (q.isBlank() || q.contains(':')) return 0
    val email = account?.email.orEmpty().lowercase()
    return when {
        user.username.lowercase() == q || email == q || user.uid.lowercase() == q -> 100
        user.name.lowercase() == q -> 90
        user.username.lowercase().startsWith(q) || email.startsWith(q) -> 70
        user.name.lowercase().startsWith(q) -> 60
        user.name.lowercase().split(' ').any { it.startsWith(q) } -> 40
        else -> 10
    }
}
