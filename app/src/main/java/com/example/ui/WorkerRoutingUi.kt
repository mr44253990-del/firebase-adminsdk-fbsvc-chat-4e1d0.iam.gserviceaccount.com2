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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Live routing map
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
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
                Text("Handles every service that is not moved below, and is the automatic fallback.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Services on this worker (moving one here removes it from the primary and other workers)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRowCompat {
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
            "Each extra worker must run the same cloudflare-worker.js with the same secrets/bindings (FIREBASE_SERVICE_ACCOUNT, R2, TURN…). Firestore/RTDB stay on Firebase; workers only relay server-side jobs. If an assigned worker fails, the app retries on the primary and skips that worker for 2 minutes.",
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
