package com.ebchat.desktop.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop-only session persistence: refresh token + cached profile inside the
 * per-user OS config directory (Windows: %APPDATA%\ConvoChat, Linux: ~/.config/convochat).
 */
object SessionStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val dir: Path = run {
        val os = System.getProperty("os.name", "").lowercase()
        val base = when {
            os.contains("windows") ->
                Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "ConvoChat")
            else ->
                Path.of(System.getenv("XDG_CONFIG_HOME") ?: System.getProperty("user.home"), ".config", "convochat")
        }
        Files.createDirectories(base)
        base
    }

    private val file: Path = dir.resolve("session.json")

    fun save(refreshToken: String, uid: String, email: String, displayName: String) {
        val obj = buildJsonObject {
            put("refreshToken", refreshToken)
            put("uid", uid)
            put("email", email)
            put("displayName", displayName)
        }
        Files.writeString(file, obj.toString())
    }

    fun load(): JsonObject? =
        runCatching { Files.readString(file) }.getOrNull()
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    fun clear() {
        runCatching { Files.deleteIfExists(file) }
    }
}
