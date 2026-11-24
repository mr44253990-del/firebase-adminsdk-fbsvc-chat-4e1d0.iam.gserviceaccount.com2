package com.ebchat.desktop.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Realtime Database REST client.
 *
 * Uses the same envelope the Android app uses:
 *   chats/{chatId}/messages/{messageId}  where chatId = sorted(uidA_uidB)
 *   status/{uid} = { state, lastChanged }
 */
class RtdbClient(private val config: FirebaseConfig, private val tokenProvider: () -> String) {

    private fun url(path: String, query: String = ""): String {
        val token = tokenProvider()
        val sep = if (query.isBlank()) "?" else "&$query&"
        return "${config.databaseUrl}/$path.json${sep}auth=${URLEncoder.encode(token, Charsets.UTF_8)}"
    }

    data class ChatMessage(
        val messageId: String,
        val senderId: String,
        val senderName: String,
        val text: String,
        val timestamp: Long
    )

    fun chatIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    /** Latest 100 messages of a direct chat (RTDB keeps the ephemeral delivery envelope, like Android). */
    fun messages(chatId: String, limit: Int = 100): List<ChatMessage> {
        val res = Rest.request("GET", url("chats/$chatId/messages", "orderBy=\"timestamp\"&limitToLast=$limit"))
        return res.entries.mapNotNull { (id, el) ->
            val o = el.jsonObject
            fun text(name: String): String =
                (o[name] as? JsonPrimitive)?.contentOrNull ?: ""
            ChatMessage(
                messageId = id,
                senderId = text("senderId"),
                senderName = text("senderName"),
                text = text("text"),
                timestamp = (o["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0L
            )
        }.sortedBy { it.timestamp }
    }

    /** Append a text message with the same Message shape the Android client writes. */
    fun sendMessage(chatId: String, messageId: String, senderId: String, senderName: String, senderUsername: String, text: String): Boolean {
        val payload = buildJsonObject {
            put("messageId", messageId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("senderUsername", senderUsername)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
            put("edited", false)
            put("seenByRecipient", false)
            put("deliveredToRecipient", false)
            put("expiresAt", 0L)
        }
        Rest.request("PUT", url("chats/$chatId/messages/$messageId"), payload)
        return true
    }

    /** Presence status identical to the Android `status/{uid}` node. */
    fun setStatus(uid: String, state: String) {
        val payload = buildJsonObject {
            put("state", state)
            put("lastChanged", System.currentTimeMillis())
        }
        runCatching { Rest.request("PUT", url("status/$uid"), payload) }
    }
}
