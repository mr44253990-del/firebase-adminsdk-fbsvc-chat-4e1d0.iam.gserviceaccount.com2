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

        /** Reads the saved accounts list without a ViewModel (used by the FCM service). */
        fun list(context: Context): List<RememberedAccount> = runCatching {
            val array = JSONArray(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            )
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                RememberedAccount(
                    uid = item.optString("uid"),
                    name = item.optString("name"),
                    email = item.optString("email"),
                    photoUrl = item.optString("photoUrl"),
                    provider = item.optString("provider").ifBlank { "password" },
                    lastUsedAt = item.optLong("lastUsedAt"),
                    unread = item.optInt("unread")
                )
            }.filter { it.uid.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
