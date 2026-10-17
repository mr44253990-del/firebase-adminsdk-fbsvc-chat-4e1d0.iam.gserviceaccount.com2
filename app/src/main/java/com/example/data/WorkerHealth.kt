package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Detects an outdated Cloudflare Worker and routes around it.
 *
 * Root cause of "Missing required fields: token, title, body": an older Worker build does
 * not know the newer API routes (/mfa/…, /sessions/…, /clouddb/…, /admin/…) and lets them
 * fall through to its push-sender, which then complains about missing push fields.
 *
 *  - [normalize] turns that confusing answer into a clear "server update needed" message.
 *  - [checkAll] reads every worker's public health JSON; extra pool workers that are older
 *    than [REQUIRED_API_VERSION] are skipped by [WorkerRouter] (traffic goes to the primary).
 */
object WorkerHealth {
    /** Must match WORKER_API_VERSION in cloudflare-worker.js. */
    const val REQUIRED_API_VERSION = 44
    const val DEFAULT_PRIMARY = "https://solitary-hill-dcdc.mr44253990.workers.dev"
    const val OUTDATED_MESSAGE =
        "Server update needed — the Cloudflare Worker is an older version and doesn't know this feature yet. " +
            "Admin → Server health → \"Update Worker\" fixes it in one tap (or paste the latest cloudflare-worker.js in the Cloudflare dashboard)."

    data class Report(
        val url: String,
        val reachable: Boolean,
        val apiVersion: Int = 0,
        val version: String = "",
        val serviceAccount: Boolean = false,
        val turn: Boolean = false,
        val sfu: Boolean = false,
        val ai: Boolean = false,
        val r2: Boolean = false,
        val latencyMs: Long = 0,
        val error: String = "",
        val checkedAt: Long = System.currentTimeMillis()
    ) {
        val outdated: Boolean get() = reachable && apiVersion < REQUIRED_API_VERSION
        val healthy: Boolean get() = reachable && !outdated && serviceAccount
    }

    private val http by lazy { OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build() }
    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()
    private val _outdatedSeen = MutableStateFlow(false)
    /** True once any worker answered a request in the "outdated" way during this run. */
    val outdatedSeen: StateFlow<Boolean> = _outdatedSeen.asStateFlow()

    fun isOutdatedAnswer(json: JSONObject): Boolean =
        json.optString("code") == "UNKNOWN_ROUTE" ||
            json.optString("error").contains("Missing required fields: token, title, body", ignoreCase = true)

    /** Rewrites an outdated-worker answer into a readable message (in place) and returns it. */
    fun normalize(code: Int, json: JSONObject): JSONObject {
        if (isOutdatedAnswer(json)) {
            _outdatedSeen.value = true
            json.put("error", OUTDATED_MESSAGE).put("outdatedWorker", true).put("httpCode", code)
        }
        return json
    }

    /** Friendly text for a raw error string coming from any worker call. */
    fun friendly(raw: String): String =
        if (raw.contains("Missing required fields: token, title, body", ignoreCase = true) || raw.contains("UNKNOWN_ROUTE")) {
            _outdatedSeen.value = true; OUTDATED_MESSAGE
        } else raw

    suspend fun check(url: String): Report = withContext(Dispatchers.IO) {
        val clean = url.trim().trimEnd('/')
        val started = System.currentTimeMillis()
        runCatching {
            http.newCall(Request.Builder().url("$clean/").get().header("Cache-Control", "no-cache").build()).execute().use { r ->
                val json = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                Report(
                    url = clean,
                    reachable = json.has("service") || json.has("ok"),
                    apiVersion = json.optInt("apiVersion", 0),
                    version = json.optString("version"),
                    serviceAccount = json.optBoolean("serviceAccountConfigured"),
                    turn = json.optBoolean("turnConfigured"),
                    sfu = json.optBoolean("sfuConfigured"),
                    ai = json.optBoolean("aiConfigured"),
                    r2 = json.optBoolean("r2Configured"),
                    latencyMs = System.currentTimeMillis() - started,
                    error = if (r.isSuccessful) "" else json.optString("error").ifBlank { "HTTP ${r.code}" }
                )
            }
        }.getOrElse { Report(clean, reachable = false, error = it.localizedMessage ?: "Unreachable", latencyMs = System.currentTimeMillis() - started) }
    }

    /** Checks the primary and every pool worker; outdated extra workers are routed around. */
    suspend fun checkAll(): List<Report> {
        val primary = WorkerRouter.primary.value.ifBlank { DEFAULT_PRIMARY }
        val urls = (listOf(primary) + WorkerRouter.nodes.value.filter { it.valid }.map { it.url }).map { it.trim().trimEnd('/') }.distinct()
        val results = urls.map { check(it) }
        results.forEach { WorkerRouter.setOutdated(it.url, it.outdated) }
        _reports.value = results
        if (results.firstOrNull()?.outdated == false) _outdatedSeen.value = false
        return results
    }
}
