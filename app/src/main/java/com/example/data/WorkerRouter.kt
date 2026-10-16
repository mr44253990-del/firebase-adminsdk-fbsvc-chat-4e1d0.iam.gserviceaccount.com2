package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

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
 * Stored in RTDB `config/app_settings/workerPool/{id}` = { label, url, enabled, services{key:true} }.
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
        val services: Set<String> = emptySet()
    ) {
        val valid: Boolean get() = url.startsWith("https://", ignoreCase = true) && url.length > 12
    }

    private const val COOLDOWN_MS = 120_000L
    private val down = ConcurrentHashMap<String, Long>()

    private val _primary = MutableStateFlow("")
    val primary: StateFlow<String> = _primary.asStateFlow()
    private val _nodes = MutableStateFlow<List<WorkerNode>>(emptyList())
    val nodes: StateFlow<List<WorkerNode>> = _nodes.asStateFlow()

    fun setPrimary(url: String) { if (url.isNotBlank()) _primary.value = url.trim().trimEnd('/') }
    fun setNodes(list: List<WorkerNode>) { _nodes.value = list }

    /** Base URL (no trailing slash) that should serve [service]. */
    fun resolve(service: String, fallback: String = _primary.value): String {
        val now = System.currentTimeMillis()
        val node = _nodes.value.firstOrNull { it.enabled && it.valid && service in it.services && (down[it.url] ?: 0L) < now }
        return (node?.url ?: fallback).trim().trimEnd('/')
    }

    /** Which node (or null = primary) currently owns [service], ignoring health. */
    fun owner(service: String): WorkerNode? = _nodes.value.firstOrNull { it.enabled && it.valid && service in it.services }

    fun isPrimary(url: String): Boolean = url.trim().trimEnd('/') == _primary.value

    fun markFailed(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isNotBlank() && !isPrimary(clean)) down[clean] = System.currentTimeMillis() + COOLDOWN_MS
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
                services = services
            )
        }.sortedBy { it.id }
    }

    fun toMap(list: List<WorkerNode>): Map<String, Any> = list.associate { n ->
        n.id to mapOf(
            "label" to n.label,
            "url" to n.url.trim().trimEnd('/'),
            "enabled" to n.enabled,
            "services" to n.services.associateWith { true }
        )
    }
}
