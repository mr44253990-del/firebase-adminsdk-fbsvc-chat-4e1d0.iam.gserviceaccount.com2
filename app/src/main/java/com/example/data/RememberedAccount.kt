package com.example.data

import android.content.Context
import org.json.JSONArray

data class RememberedAccount(
    val uid:String="",
    val name:String="",
    val email:String="",
    val photoUrl:String="",
    val provider:String="password",
    val lastUsedAt:Long=0L,
    val unread:Int=0
) {
    companion object {
        private const val PREFS = "firechat_prefs"
        private const val KEY = "remembered_accounts"

        /** Load remembered accounts from the same SharedPreferences ChatViewModel uses. Safe to call from services/receivers. */
        fun loadAll(context: Context): List<RememberedAccount> = runCatching {
            val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            (0 until array.length()).map { array.getJSONObject(it) }.map {
                RememberedAccount(
                    it.optString("uid"), it.optString("name"), it.optString("email"),
                    it.optString("photoUrl"), it.optString("provider", "password"),
                    it.optLong("lastUsedAt"), it.optInt("unread")
                )
            }.sortedByDescending { it.lastUsedAt }
        }.getOrDefault(emptyList())

        fun findByUid(context: Context, uid: String?): RememberedAccount? =
            if (uid.isNullOrBlank()) null else loadAll(context).firstOrNull { it.uid == uid }
    }
}
