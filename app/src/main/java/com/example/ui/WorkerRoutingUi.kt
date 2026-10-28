package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkerRouter

private val nodeColors = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))

/**
 * Admin editor for smart multi-worker routing: primary URL + extra workers, each service
 * assigned to exactly one place (exclusive), with health probes and automatic fallback.
 */
@Composable
fun AdminWorkerRoutingPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val primary by viewModel.webhookUrl.collectAsState()
    val saved by viewModel.workerPool.collectAsState()
    var nodes by remember(saved) { mutableStateOf(saved) }
    var probe by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    val mode by WorkerRouter.mode.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Mode selector
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F766E)))).padding(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                        Text("Worker pool", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        val active = 1 + nodes.count { it.enabled && it.valid }
                        Text("$active active", color = Color(0xFF99F6E4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("smart" to "Smart: all active", "manual" to "Manual split").forEach { (id, label) ->
                            FilterChip(
                                selected = mode == id, onClick = { viewModel.saveWorkerPoolMode(id) },
                                label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = { Icon(if (id == "smart") Icons.Default.Shuffle else Icons.Default.AltRoute, null, Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, iconColor = Color.White, selectedContainerColor = Color.White, selectedLabelColor = Color(0xFF1E1B4B), selectedLeadingIconColor = Color(0xFF1E1B4B))
                            )
                        }
                    }
                    Text(
                        if (mode == "smart") "Every worker (primary included) runs every service at once. Each request picks a random healthy worker by weight; on a network error, 5xx or 429 (daily limit) it instantly retries on another worker, and the failing one rests 2 minutes (30 minutes after a limit)."
                        else "Each service runs on one chosen worker; the primary is the fallback.",
                        color = Color.White.copy(alpha = .8f), fontSize = 11.sp
                    )
                }
            }
        }
        // Live stats (smart mode)
        if (mode == "smart") Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Live traffic (this device)", fontWeight = FontWeight.Bold)
                val urls = (listOf(primary.trimEnd('/')) + nodes.filter { it.enabled && it.valid }.map { it.url.trimEnd('/') }).distinct()
                urls.forEachIndexed { i, url ->
                    val refreshKey = tick
                    val st = WorkerRouter.stats(url)
                    val resting = WorkerRouter.isCoolingDown(url)
                    val total = (st.ok.get() + st.failed.get()).coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(when { resting -> Color(0xFFEF4444); WorkerRouter.isOutdated(url) -> Color(0xFFF59E0B); else -> Color(0xFF22C55E) }))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (i == 0) "Primary" else nodes.firstOrNull { it.url.trimEnd('/') == url }?.label?.ifBlank { null } ?: "Worker ${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(progress = { st.ok.get().toFloat() / total }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${st.ok.get()} ok · ${st.failed.get()} fail" + (if (st.lastMs.get() > 0) " · ${st.lastMs.get()} ms" else "") + if (resting) " · resting" else "",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (resting && st.lastError.isNotBlank()) Text(st.lastError, fontSize = 10.sp, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
        }
        // Live routing map
        if (mode == "manual") Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Routing map", fontWeight = FontWeight.Bold)
                WorkerRouter.SERVICES.forEach { svc ->
                    val ownerIndex = nodes.indexOfFirst { it.enabled && it.valid && svc.key in it.services }
                    val owner = nodes.getOrNull(ownerIndex)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (owner == null) Color(0xFF64748B) else nodeColors[ownerIndex % nodeColors.size]))
                        Spacer(Modifier.width(8.dp))
                        Text(svc.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(130.dp))
                        Text(
                            owner?.let { it.label.ifBlank { "Worker ${ownerIndex + 2}" } + if (WorkerRouter.isCoolingDown(it.url)) " (failing → primary)" else "" } ?: "Primary",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // Primary
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0xFF64748B).copy(.5f), RoundedCornerShape(16.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Primary worker", fontWeight = FontWeight.Bold)
                Text(primary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (mode == "smart") "Active in the pool like every other worker, and the last-resort fallback." else "Handles every service that is not moved below, and is the automatic fallback.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { viewModel.probeWorker(primary) { ok, msg -> probe = probe + ("primary" to (ok to msg)) } }) { Text("Ping") }
        }
        probe["primary"]?.let { (ok, msg) -> Text(msg, fontSize = 11.sp, color = if (ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }

        nodes.forEachIndexed { index, node ->
            val color = nodeColors[index % nodeColors.size]
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .08f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                            Text("${index + 2}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(node.label, { v -> nodes = nodes.map { if (it.id == node.id) it.copy(label = v.take(30)) else it } },
                            label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(1f))
                        Switch(node.enabled, { v -> nodes = nodes.map { if (it.id == node.id) it.copy(enabled = v) else it } })
                    }
                    OutlinedTextField(node.url, { v -> nodes = nodes.map { if (it.id == node.id) it.copy(url = v.trim()) else it } },
                        label = { Text("Worker HTTPS URL") }, placeholder = { Text("https://worker-2.example.workers.dev") }, singleLine = true,
                        isError = node.url.isNotBlank() && !node.valid, modifier = Modifier.fillMaxWidth())
                    if (mode == "smart") {
                        Text("Traffic share: ${node.weight}x  (all services enabled)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(node.weight.toFloat(), { v -> nodes = nodes.map { if (it.id == node.id) it.copy(weight = v.toInt().coerceIn(1, 10)) else it } }, valueRange = 1f..10f, steps = 8)
                    }
                    if (mode == "manual") Text("Services on this worker (moving one here removes it from the primary and other workers)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mode == "manual") FlowRowCompat {
                        WorkerRouter.SERVICES.forEach { svc ->
                            FilterChip(
                                selected = svc.key in node.services,
                                onClick = { nodes = WorkerRouter.assign(nodes, node.id, svc.key, svc.key !in node.services) },
                                label = { Text(svc.label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewModel.probeWorker(node.url) { ok, msg -> probe = probe + (node.id to (ok to msg)) } }, enabled = node.valid) {
                            Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.width(6.dp)); Text("Health check")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { nodes = nodes.filterNot { it.id == node.id } }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
                    }
                    probe[node.id]?.let { (ok, msg) -> Text(msg, fontSize = 11.sp, color = if (ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }
                }
            }
        }
        OutlinedButton(onClick = {
            val id = "w" + (System.currentTimeMillis() % 100000)
            nodes = nodes + WorkerRouter.WorkerNode(id = id, label = "Worker ${nodes.size + 2}")
        }, enabled = nodes.size < 5, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add worker URL")
        }
        Button(onClick = {
            saving = true
            viewModel.saveWorkerPool(nodes) { ok, msg -> saving = false; Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(6.dp)); Text("Save routing")
        }
        Text(
            "Each extra worker must run the same cloudflare-worker.js with the same secrets/bindings (FIREBASE_SERVICE_ACCOUNT, R2, TURN…). Firestore/RTDB stay on Firebase; workers only relay server-side jobs. Tip: deploy the same Worker under 3-4 Cloudflare accounts (each free plan has its own 100k requests/day) and add them here; Smart mode spreads the load and fails over automatically.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Simple wrapping row (keeps us independent of the experimental FlowRow API surface). */
@Composable
fun FlowRowCompat(spacing: androidx.compose.ui.unit.Dp = 6.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        var x = 0; var y = 0; var rowH = 0
        val positions = placeables.map { p ->
            if (x > 0 && x + p.width > constraints.maxWidth) { x = 0; y += rowH + gap; rowH = 0 }
            val pos = x to y
            x += p.width + gap; rowH = maxOf(rowH, p.height)
            pos
        }
        val height = if (placeables.isEmpty()) 0 else y + rowH
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(positions[i].first, positions[i].second) }
        }
    }
}
