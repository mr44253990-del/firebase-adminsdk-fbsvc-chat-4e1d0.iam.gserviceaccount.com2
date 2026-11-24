package com.ebchat.desktop.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Firestore REST reader for the public feed, friends, and profiles. */
class FirestoreRest(private val config: FirebaseConfig, private val tokenProvider: () -> String) {

    private fun headers(): Map<String, String> = mapOf("Authorization" to "Bearer ${tokenProvider()}")

    /** Home feed: newest posts from the `posts` collection (text-only posts, like Android). */
    fun recentPosts(limit: Int = 25): List<Post> {
        val url = "${config.firestoreBase}/posts?pageSize=$limit&orderBy=timestamp%20desc"
        val res = Rest.request("GET", url, headers = headers())
        val docs = res["documents"]?.jsonArray ?: return emptyList()
        return docs.mapNotNull { el ->
            val doc = el.jsonObject
            val fields = doc["fields"]?.jsonObject ?: return@mapNotNull null
            fun text(name: String): String =
                (fields[name] as? JsonObject)?.get("stringValue")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
            fun long(name: String): Long =
                (fields[name] as? JsonObject)?.get("integerValue")?.let { (it as? JsonPrimitive)?.contentOrNull }?.toLongOrNull() ?: 0L
            val id = doc["name"]?.toString()?.substringAfterLast('/') ?: ""
            Post(
                id = id,
                senderId = text("senderId"),
                senderName = text("senderName"),
                text = text("text"),
                timestamp = long("timestamp"),
                feeling = text("feeling"),
                isPrivate = ((fields["isPrivate"] as? JsonObject)?.get("booleanValue") as? JsonPrimitive)?.contentOrNull == "true"
            )
        }
    }

    /** Profile fields used by the chat composer and home screen. */
    fun profile(uid: String): Profile? {
        val url = "${config.firestoreBase}/users/$uid?key=${config.apiKey}"
        val res = runCatching { Rest.request("GET", url, headers = headers()) }.getOrNull() ?: return null
        val fields = res["fields"]?.jsonObject ?: return null
        fun text(name: String): String =
            (fields[name] as? JsonObject)?.get("stringValue")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
        return Profile(
            uid = uid,
            name = text("name"),
            username = text("username"),
            profileImageUrl = text("profileImageUrl"),
            bio = text("bio")
        )
    }

    data class Post(
        val id: String,
        val senderId: String,
        val senderName: String,
        val text: String,
        val timestamp: Long,
        val feeling: String,
        val isPrivate: Boolean
    )

    data class Profile(
        val uid: String,
        val name: String,
        val username: String,
        val profileImageUrl: String,
        val bio: String
    )
}
