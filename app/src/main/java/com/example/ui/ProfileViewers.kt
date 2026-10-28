package com.example.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ProfileViewer(
    val viewerId: String,
    val name: String,
    val username: String,
    val imageUrl: String,
    val lastViewedAt: Long,
    val visits: Int = 1
)

/**
 * "Who viewed my profile" history kept only on the owner's phone. The server copy under
 * RTDB `profileViews/{owner}` is a short-lived inbox: the app saves each visit here and then
 * deletes it from the database.
 */
object ProfileViewerStore {
    private const val PREFS = "convo_profile_viewers"
    private const val MAX_ENTRIES = 300

    private val _viewers = MutableStateFlow<List<ProfileViewer>>(emptyList())
    val viewers: StateFlow<List<ProfileViewer>> = _viewers.asStateFlow()
    private val _shareVisits = MutableStateFlow(true)
    /** When off, this user's visits to other profiles are not recorded. */
    val shareVisits: StateFlow<Boolean> = _shareVisits.asStateFlow()
    private var loadedOwner: String? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, ownerUid: String) {
        _shareVisits.value = prefs(context).getBoolean("share_profile_visits", true)
        if (loadedOwner == ownerUid) return
        loadedOwner = ownerUid
        _viewers.value = parse(prefs(context).getString("viewers_$ownerUid", null))
    }

    fun setShareVisits(context: Context, enabled: Boolean) {
        _shareVisits.value = enabled
        prefs(context).edit().putBoolean("share_profile_visits", enabled).apply()
    }

    @Synchronized
    fun merge(context: Context, ownerUid: String, incoming: ProfileViewer) {
        if (loadedOwner != ownerUid) load(context, ownerUid)
        val current = _viewers.value.associateBy { it.viewerId }.toMutableMap()
        val existing = current[incoming.viewerId]
        current[incoming.viewerId] = if (existing == null) incoming else incoming.copy(
            visits = existing.visits + 1,
            lastViewedAt = maxOf(existing.lastViewedAt, incoming.lastViewedAt),
            name = incoming.name.ifBlank { existing.name },
            username = incoming.username.ifBlank { existing.username },
            imageUrl = incoming.imageUrl.ifBlank { existing.imageUrl }
        )
        val next = current.values.sortedByDescending { it.lastViewedAt }.take(MAX_ENTRIES)
        _viewers.value = next
        prefs(context).edit().putString("viewers_$ownerUid", serialize(next)).apply()
    }

    fun clear(context: Context, ownerUid: String) {
        _viewers.value = emptyList()
        prefs(context).edit().remove("viewers_$ownerUid").apply()
    }

    /** Local throttle so repeated opens of the same profile count as one visit per 30 minutes. */
    fun shouldRecordVisit(context: Context, targetUid: String): Boolean {
        val key = "visit_$targetUid"
        val now = System.currentTimeMillis()
        val last = prefs(context).getLong(key, 0L)
        if (now - last < 30 * 60_000L) return false
        prefs(context).edit().putLong(key, now).apply()
        return true
    }

    fun countSince(sinceMillis: Long): Int = _viewers.value.count { it.lastViewedAt >= sinceMillis }

    private fun serialize(list: List<ProfileViewer>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.viewerId).put("n", it.name).put("u", it.username).put("i", it.imageUrl).put("t", it.lastViewedAt).put("v", it.visits))
        }
    }.toString()

    private fun parse(raw: String?): List<ProfileViewer> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            ProfileViewer(o.optString("id"), o.optString("n"), o.optString("u"), o.optString("i"), o.optLong("t"), o.optInt("v", 1))
        }.filter { it.viewerId.isNotBlank() }
    }.getOrDefault(emptyList())
}
