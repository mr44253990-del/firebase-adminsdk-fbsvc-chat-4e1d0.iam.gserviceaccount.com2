package com.ebchat.desktop.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Shared HTTP + JSON layer for Firebase REST access on the desktop. */
object Rest {
    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .build()

    class HttpError(val status: Int, val body: String) : Exception("HTTP $status: ${body.take(300)}")

    fun request(
        method: String,
        url: String,
        body: JsonElement? = null,
        headers: Map<String, String> = emptyMap()
    ): JsonObject {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(30))
        builder.header("Accept", "application/json")
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (body != null) {
            builder.header("Content-Type", "application/json")
            builder.method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw HttpError(response.statusCode(), response.body())
        val text = response.body()
        if (text.isBlank()) return JsonObject(emptyMap())
        return if (text.trimStart().startsWith("[") || text.trimStart() == "null") {
            JsonObject(emptyMap())
        } else {
            json.parseToJsonElement(text).jsonObject
        }
    }
}
