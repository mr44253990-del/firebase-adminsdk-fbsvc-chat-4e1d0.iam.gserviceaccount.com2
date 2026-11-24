package com.ebchat.desktop.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Public, non-secret Firebase web configuration for the desktop client.
 * These are the same public values the Android app ships inside google-services.json;
 * desktop reads them from resources/firebase.json and optional env overrides.
 */
data class FirebaseConfig(
    val apiKey: String,
    val projectId: String,
    val databaseUrl: String
) {
    val firestoreBase: String get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    val identityBase: String get() = "https://identitytoolkit.googleapis.com/v1"

    companion object {
        fun load(): FirebaseConfig {
            val text = object {} .javaClass.classLoader.getResourceAsStream("firebase.json")
                ?.bufferedReader()?.readText()
            requireNotNull(text) { "firebase.json missing from desktop resources" }
            val obj = Json.parseToJsonElement(text).let { it as JsonObject }
            fun str(key: String, env: String): String {
                val fromEnv = System.getenv(env)?.takeIf { it.isNotBlank() }
                if (fromEnv != null) return fromEnv
                val raw = obj[key]?.toString()?.trim('"')
                require(!raw.isNullOrBlank()) { "Missing $key in desktop/src/main/resources/firebase.json" }
                return raw
            }
            return FirebaseConfig(
                apiKey = str("apiKey", "CONVO_FIREBASE_API_KEY"),
                projectId = str("projectId", "CONVO_FIREBASE_PROJECT_ID"),
                databaseUrl = str("databaseUrl", "CONVO_FIREBASE_DATABASE_URL").trimEnd('/')
            )
        }
    }
}

/** Firebase Identity Toolkit (Auth REST) client — email/password flow for desktop v1. */
class AuthClient(private val config: FirebaseConfig) {

    data class AuthResult(
        val localId: String,
        val idToken: String,
        val refreshToken: String,
        val email: String,
        val displayName: String
    )

    private fun call(body: JsonObject): JsonObject =
        Rest.request(
            "POST",
            "${config.identityBase}/accounts:signInWithPassword?key=${config.apiKey}",
            body
        )

    fun signIn(email: String, password: String): AuthResult {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }
        val res = call(body)
        return result(res, res["email"]?.toString()?.trim('"') ?: email)
    }

    fun signUp(email: String, password: String, displayName: String): AuthResult {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("displayName", displayName)
            put("returnSecureToken", true)
        }
        val res = Rest.request(
            "POST",
            "${config.identityBase}/accounts:signUp?key=${config.apiKey}",
            body
        )
        return result(res, res["email"]?.toString()?.trim('"') ?: email)
    }

    fun refreshToken(refreshToken: String): AuthResult {
        val url = "https://securetoken.googleapis.com/v1/token?key=${config.apiKey}"
        val body = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }
        val res = Rest.request("POST", url, body)
        val uid = res["user_id"]?.toString()?.trim('"').orEmpty()
        val token = res["id_token"]?.toString()?.trim('"').orEmpty()
        val newRefresh = res["refresh_token"]?.toString()?.trim('"').orEmpty()
        if (uid.isBlank() || token.isBlank() || newRefresh.isBlank()) {
            throw Rest.HttpError(401, "invalid refresh token response")
        }
        return AuthResult(uid, token, newRefresh, res["email"]?.toString()?.trim('"').orEmpty(), "")
    }

    private fun result(res: JsonObject, email: String): AuthResult {
        val uid = res["localId"]?.toString()?.trim('"').orEmpty()
        val token = res["idToken"]?.toString()?.trim('"').orEmpty()
        val refresh = res["refreshToken"]?.toString()?.trim('"').orEmpty()
        if (uid.isBlank() || token.isBlank() || refresh.isBlank()) {
            val msg = res["error"]?.let { (it as? JsonObject)?.get("message")?.toString() } ?: "auth failed"
            throw Rest.HttpError(401, msg.trim('"'))
        }
        return AuthResult(
            localId = uid,
            idToken = token,
            refreshToken = refresh,
            email = email,
            displayName = res["displayName"]?.toString()?.trim('"').orEmpty()
        )
    }
}
