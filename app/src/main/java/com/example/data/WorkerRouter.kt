package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Smart multi-worker routing.
 *
 * The primary worker URL handles every service by default. The admin can add extra worker
 * URLs (2nd, 3rd, …) and move individual services onto them to spread the load, e.g. push
 * notifications on worker 2 while calls (TURN/SFU) run on worker 3.
 *
 *  - A service is assigned to at most ONE extra worker (exclusive). Turning a service on for a
 *    worker automatically removes it from every other worker, so it leaves the primary too.
 *  - If the assigned worker is disabled, has an invalid URL or recently failed, the request
 *    falls back to the primary automatically. A failed worker is skipped for [COOLDOWN_MS].
 *
 * Stored in RTDB `config/app_settings/workerPool/{id}` = { label, url, enabled, services{key:true}, weight }.
 *
 * SMART mode (`config/app_settings/workerPoolMode = "smart"`, the default once extra workers exist):
 * every enabled worker (primary included) serves every service at the same time. Each request
 * goes to a weighted-random healthy worker; if it fails (network error, 5xx or 429 = daily limit)
 * the next worker is tried immediately and the failing one rests for a while, so a single
 * worker's free-plan limit never stops the app. MANUAL mode keeps the exclusive assignment.
 */
object WorkerRouter {
    data class Service(val key: String, val label: String, val description: String)

    val SERVICES = listOf(
        Service("push", "Messages & push", "Chat/notification FCM delivery"),
        Service("calls", "Calls", "Call ringing push, TURN credentials and SFU"),
        Service("ai", "AI assistant", "/ai/chat requests"),
        Service("storage", "Storage / media", "Uploads, deletes, reels media"),
        Service("database", "Database & admin", "Sessions, reports, bans and other server writes"),
        Service("location", "Live location", "Location share relays (reserved)")
    )

    data class WorkerNode(
        val id: String,
        val label: String = "",
        val url: String = "",
        val enabled: Boolean = true,
        val services: Set<String> = emptySet(),
        val weight: Int = 1
    ) {
        val valid: Boolean get() = url.startsWith("https://", ignoreCase = true) && url.length > 12
    }

    private const val COOLDOWN_MS = 120_000L
    private val down = ConcurrentHashMap<String, Long>()
    /** Pool workers whose health JSON reports an API older than the app needs. */
    private val outdated = ConcurrentHashMap.newKeySet<String>()
    fun setOutdated(url: String, value: Boolean) { val c = url.trim().trimEnd('/'); if (value && !isPrimary(c)) outdated.add(c) else outdated.remove(c) }
    fun isOutdated(url: String): Boolean = url.trim().trimEnd('/') in outdated

    private val _primary = MutableStateFlow("")
    val primary: StateFlow<String> = _primary.asStateFlow()
    private val _nodes = MutableStateFlow<List<WorkerNode>>(emptyList())
    val nodes: StateFlow<List<WorkerNode>> = _nodes.asStateFlow()

    fun setPrimary(url: String) { if (url.isNotBlank()) _primary.value = url.trim().trimEnd('/') }
    fun setNodes(list: List<WorkerNode>) { _nodes.value = list }

    private val _mode = MutableStateFlow("smart")
    /** "smart" = all workers active with random selection + failover, "manual" = exclusive assignment. */
    val mode: StateFlow<String> = _mode.asStateFlow()
    fun setMode(value: String?) { _mode.value = if (value == "manual") "manual" else "smart" }
    val smart: Boolean get() = _mode.value != "manual"

    /** Per-worker live counters for the admin screen. */
    class Stats { val ok = AtomicLong(); val failed = AtomicLong(); val lastMs = AtomicLong(); @Volatile var lastError: String = "" }
    private val stats = ConcurrentHashMap<String, Stats>()
    fun stats(url: String): Stats = stats.getOrPut(url.trim().trimEnd('/')) { Stats() }

    private fun healthy(url: String, now: Long) = (down[url] ?: 0L) < now && url !in outdated

    /**
     * Ordered list of worker base URLs to try for [service]: the first is the chosen worker,
     * the rest are failover targets. Never empty when a primary/fallback exists.
     */
    fun candidates(service: String, fallback: String = _primary.value): List<String> {
        val now = System.currentTimeMillis()
        val primary = fallback.trim().trimEnd('/')
        val live = _nodes.value.filter { it.enabled && it.valid }
        if (!smart) {
            val owner = live.firstOrNull { service in it.services && healthy(it.url, now) }
            return listOfNotNull(owner?.url, primary.takeIf { it.isNotBlank() }).distinct()
        }
        data class C(val url: String, val weight: Int)
        val all = (listOf(C(primary, 1)) + live.map { C(it.url.trim().trimEnd('/'), it.weight.coerceIn(1, 10)) })
            .filter { it.url.startsWith("https://") }.distinctBy { it.url }
        val (ready, resting) = all.partition { healthy(it.url, now) }
        // Weighted random order (Efraimidis–Spirakis): bigger weight = picked first more often.
        val shuffled = ready.map { it to Math.pow(Math.random(), 1.0 / it.weight) }.sortedByDescending { it.second }.map { it.first.url }
        val restingSorted = resting.sortedBy { down[it.url] ?: 0L }.map { it.url }
        return (shuffled + restingSorted).ifEmpty { listOfNotNull(primary.takeIf { it.isNotBlank() }) }
    }

    /** Base URL (no trailing slash) that should serve [service]. */
    fun resolve(service: String, fallback: String = _primary.value): String =
        candidates(service, fallback).firstOrNull() ?: fallback.trim().trimEnd('/')

    /** Response codes that mean "try another worker": server errors and rate/daily limits. */
    fun shouldFailover(code: Int): Boolean = code >= 500 || code == 429

    /**
     * Runs [build] against the chosen worker and fails over through the rest of the pool on
     * network errors, 5xx or 429. Returns the first acceptable response (caller must close it).
     */
    fun execute(service: String, client: okhttp3.OkHttpClient, fallback: String = _primary.value, build: (String) -> okhttp3.Request): okhttp3.Response {
        val list = candidates(service, fallback).take(4)
        var lastError: Throwable? = null
        var lastResponse: okhttp3.Response? = null
        list.forEachIndexed { index, base ->
            val started = System.currentTimeMillis()
            val st = stats(base)
            try {
                val r = client.newCall(build(base)).execute()
                st.lastMs.set(System.currentTimeMillis() - started)
                if (!shouldFailover(r.code) || index == list.lastIndex) {
                    if (shouldFailover(r.code)) { st.failed.incrementAndGet(); st.lastError = "HTTP ${r.code}"; markFailed(base, r.code == 429) }
                    else { st.ok.incrementAndGet(); markHealthy(base) }
                    lastResponse?.close()
                    return r
                }
                st.failed.incrementAndGet(); st.lastError = "HTTP ${r.code}"
                markFailed(base, r.code == 429)
                r.close()
            } catch (t: Throwable) {
                st.failed.incrementAndGet(); st.lastError = t.localizedMessage ?: t.javaClass.simpleName
                markFailed(base, false)
                lastError = t
            }
        }
        throw (lastError ?: java.io.IOException("No worker reachable"))
    }

    /** Which node (or null = primary) currently owns [service], ignoring health. */
    fun owner(service: String): WorkerNode? = _nodes.value.firstOrNull { it.enabled && it.valid && service in it.services }

    fun isPrimary(url: String): Boolean = url.trim().trimEnd('/') == _primary.value

    fun markFailed(url: String, limitReached: Boolean = false) {
        val clean = url.trim().trimEnd('/')
        // In smart mode the primary can rest too (it stays the last resort in candidates()).
        if (clean.isNotBlank() && (smart || !isPrimary(clean))) down[clean] = System.currentTimeMillis() + if (limitReached) 30 * 60_000L else COOLDOWN_MS
    }

    fun markHealthy(url: String) { down.remove(url.trim().trimEnd('/')) }

    fun isCoolingDown(url: String): Boolean = (down[url.trim().trimEnd('/')] ?: 0L) > System.currentTimeMillis()

    /** Exclusive assignment helper used by the admin editor. */
    fun assign(list: List<WorkerNode>, nodeId: String, service: String, on: Boolean): List<WorkerNode> = list.map { node ->
        when {
            node.id == nodeId -> node.copy(services = if (on) node.services + service else node.services - service)
            on -> node.copy(services = node.services - service)
            else -> node
        }
    }

    fun parse(raw: Any?): List<WorkerNode> {
        val map = raw as? Map<*, *> ?: return emptyList()
        return map.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val services = (m["services"] as? Map<*, *>)?.filter { it.value == true }?.keys?.map { it.toString() }?.toSet().orEmpty()
            WorkerNode(
                id = k.toString(),
                label = m["label"]?.toString().orEmpty(),
                url = m["url"]?.toString().orEmpty().trim().trimEnd('/'),
                enabled = m["enabled"] as? Boolean ?: true,
                services = services,
                weight = (m["weight"] as? Number)?.toInt()?.coerceIn(1, 10) ?: 1
            )
        }.sortedBy { it.id }
    }

    fun toMap(list: List<WorkerNode>): Map<String, Any> = list.associate { n ->
        n.id to mapOf(
            "label" to n.label,
            "url" to n.url.trim().trimEnd('/'),
            "enabled" to n.enabled,
            "services" to n.services.associateWith { true },
            "weight" to n.weight
        )
    }
}
