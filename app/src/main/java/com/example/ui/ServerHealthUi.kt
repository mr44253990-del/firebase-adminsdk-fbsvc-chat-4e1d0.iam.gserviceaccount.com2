package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.User
import com.example.data.WorkerHealth
import com.example.data.WorkerRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cloudflare Worker updater. Uploads the Worker code bundled in the APK
 * (assets/worker/cloudflare-worker.js) with `keep_bindings`, so every existing secret
 * (FIREBASE_SERVICE_ACCOUNT, TURN, Calls, Mistral …) and binding stays untouched.
 * The API token is used once and never stored.
 */
object WorkerDeployer {
    const val DEFAULT_ACCOUNT = "54ff9e3c4c5908b14f410044be85b698"
    const val DEFAULT_SCRIPT = "solitary-hill-dcdc"

    fun bundledCode(context: Context): String? =
        runCatching { context.assets.open("worker/cloudflare-worker.js").bufferedReader().use { it.readText() } }.getOrNull()

    suspend fun deploy(context: Context, apiToken: String, accountId: String, scriptName: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val code = bundledCode(context) ?: return@withContext false to "This build doesn't contain the Worker code. Paste cloudflare-worker.js in the Cloudflare dashboard instead."
        runCatching {
            val meta = JSONObject()
                .put("main_module", "worker.js")
                .put("compatibility_date", "2026-10-01")
                .put("keep_bindings", JSONArray(listOf("secret_text", "secret_key", "plain_text", "json", "r2_bucket", "kv_namespace", "d1", "service", "queue", "analytics_engine")))
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, meta.toString().toRequestBody("application/json".toMediaType()))
                .addFormDataPart("worker.js", "worker.js", code.toRequestBody("application/javascript+module".toMediaType()))
                .build()
            val req = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/${accountId.trim()}/workers/scripts/${scriptName.trim()}")
                .header("Authorization", "Bearer ${apiToken.trim()}")
                .put(body).build()
            OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                val json = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (r.isSuccessful && json.optBoolean("success")) true to "Uploaded. Checking the live version…"
                else {
                    val errors = json.optJSONArray("errors")
                    val msg = (0 until (errors?.length() ?: 0)).joinToString("; ") { errors!!.optJSONObject(it)?.optString("message").orEmpty() }
                    false to (msg.ifBlank { "Cloudflare HTTP ${r.code}" } + if (r.code == 401 || r.code == 403) " — the token needs the \"Workers Scripts: Edit\" permission for this account." else "")
                }
            }
        }.getOrElse { false to (it.localizedMessage ?: "Network error") }
    }
}

@Composable
fun AdminServerHealthPanel(users: List<User>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reports by WorkerHealth.reports.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var apiToken by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(WorkerDeployer.DEFAULT_ACCOUNT) }
    var scriptName by remember { mutableStateOf(WorkerDeployer.DEFAULT_SCRIPT) }
    var deploying by remember { mutableStateOf(false) }
    var deployMsg by remember { mutableStateOf("") }
    val bundled = remember { WorkerDeployer.bundledCode(context) }

    fun recheck() { scope.launch { checking = true; WorkerHealth.checkAll(); checking = false } }
    LaunchedEffect(Unit) { recheck() }

    val primary = reports.firstOrNull()
    val pulse by rememberInfiniteTransition(label = "health").animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- Status hero ---------------------------------------------------------------
        val (heroColors, heroTitle, heroText) = when {
            checking && primary == null -> Triple(listOf(Color(0xFF37474F), Color(0xFF546E7A)), "Checking servers…", "Reading the live Worker version")
            primary == null || !primary.reachable -> Triple(listOf(Color(0xFFB71C1C), Color(0xFFE53935)), "Worker unreachable", primary?.error ?: "No answer from the primary Worker")
            primary.outdated -> Triple(listOf(Color(0xFFE65100), Color(0xFFFFA000)), "Worker update needed", "Live API v${primary.apiVersion} · app needs v${WorkerHealth.REQUIRED_API_VERSION}. This is why actions show \"Missing required fields: token, title, body\".")
            !primary.serviceAccount -> Triple(listOf(Color(0xFFC62828), Color(0xFFEF5350)), "Secret missing", "FIREBASE_SERVICE_ACCOUNT is not set on the Worker")
            else -> Triple(listOf(Color(0xFF1B5E20), Color(0xFF00897B)), "All systems ready", "Worker API v${primary.apiVersion} · ${primary.latencyMs} ms")
        }
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(heroColors), RoundedCornerShape(24.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = .18f), modifier = Modifier.scale(if (primary?.healthy == true) 1f else pulse)) {
                    Icon(if (primary?.healthy == true) Icons.Default.CheckCircle else if (primary?.outdated == true) Icons.Default.Warning else Icons.Default.Dns, null, Modifier.padding(12.dp).size(26.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(heroTitle, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(heroText, color = Color.White.copy(alpha = .88f), fontSize = 12.sp)
                }
                if (checking) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else androidx.compose.material3.IconButton(onClick = { recheck() }) { Icon(Icons.Default.Refresh, "Re-check", tint = Color.White) }
            }
        }

        // ---- Per-worker detail -----------------------------------------------------------
        reports.forEach { r ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (WorkerRouter.isPrimary(r.url) || r == reports.first()) "Primary worker" else "Pool worker", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(if (!r.reachable) "DOWN" else if (r.outdated) "OUTDATED" else "OK", if (!r.reachable) Color(0xFFC62828) else if (r.outdated) Color(0xFFEF6C00) else Color(0xFF2E7D32))
                    }
                    Text(r.url.removePrefix("https://"), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CheckChip("Firebase key", r.serviceAccount); CheckChip("TURN", r.turn); CheckChip("Calls", r.sfu)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CheckChip("AI key", r.ai); CheckChip("R2 media", r.r2); Text("API v${r.apiVersion} · ${r.latencyMs} ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (r.outdated && !WorkerRouter.isPrimary(r.url) && r != reports.first())
                        Text("Auto-fix active: traffic for this worker's services goes to the primary until it is updated.", fontSize = 11.sp, color = Color(0xFFEF6C00))
                }
            }
        }

        // ---- One-tap updater -----------------------------------------------------------------
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Update Worker from this phone", fontWeight = FontWeight.Bold)
                }
                Text("Uploads the Worker code shipped inside this app (API v${WorkerHealth.REQUIRED_API_VERSION}). Secrets and bindings are kept. Create a token at dash.cloudflare.com → My Profile → API Tokens → \"Edit Cloudflare Workers\" template. The token is used once and never saved.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(apiToken, { apiToken = it }, Modifier.fillMaxWidth(), label = { Text("Cloudflare API token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(accountId, { accountId = it }, Modifier.weight(1f), label = { Text("Account ID") }, singleLine = true)
                    OutlinedTextField(scriptName, { scriptName = it }, Modifier.weight(1f), label = { Text("Worker name") }, singleLine = true)
                }
                if (deploying) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (deployMsg.isNotBlank()) Text(deployMsg, fontSize = 12.sp, color = if (deployMsg.startsWith("✓")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        scope.launch {
                            deploying = true; deployMsg = ""
                            val (ok, msg) = WorkerDeployer.deploy(context, apiToken, accountId, scriptName)
                            deployMsg = msg
                            if (ok) {
                                apiToken = ""
                                var live = 0
                                repeat(6) { if (live < WorkerHealth.REQUIRED_API_VERSION) { delay(2500); live = WorkerHealth.check(WorkerRouter.primary.value.ifBlank { WorkerHealth.DEFAULT_PRIMARY }).apiVersion } }
                                WorkerHealth.checkAll()
                                deployMsg = if (live >= WorkerHealth.REQUIRED_API_VERSION) "✓ Worker updated — live API v$live. Every feature works now."
                                    else "Uploaded, but the live Worker still reports v$live. If you use gradual deployments, promote the new version in the Cloudflare dashboard."
                                AdminAudit.log("worker_deploy", "script=$scriptName live=$live")
                            }
                            deploying = false
                        }
                    },
                    enabled = !deploying && apiToken.length > 20 && accountId.isNotBlank() && scriptName.isNotBlank() && bundled != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.CloudSync, null); Spacer(Modifier.width(8.dp)); Text("Update Worker now") }
                HorizontalDivider()
                Text("Or by hand (1 minute): Cloudflare dashboard → Workers & Pages → $scriptName → Edit code → select all → paste the code → Deploy.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        val code = bundled ?: return@OutlinedButton
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("cloudflare-worker.js", code))
                        Toast.makeText(context, "Worker code copied (${code.length / 1024} KB)", Toast.LENGTH_SHORT).show()
                    },
                    enabled = bundled != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy latest Worker code") }
            }
        }

        // ---- Versions in use -----------------------------------------------------------------
        if (users.isNotEmpty()) {
            val byVersion = remember(users) { users.groupingBy { it.appVersion.ifBlank { "unknown" } }.eachCount().entries.sortedByDescending { it.value } }
            val current = com.example.BuildConfig.VERSION_NAME
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Convo versions in use", fontWeight = FontWeight.Bold)
                    byVersion.forEach { (version, count) ->
                        val share = count.toFloat() / users.size
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (version == "unknown") "Not opened since 4.3.5" else "v$version", fontSize = 12.sp, modifier = Modifier.width(130.dp), fontWeight = if (version == current) FontWeight.Bold else FontWeight.Normal)
                            LinearProgressIndicator(progress = { share }, modifier = Modifier.weight(1f).height(8.dp), color = if (version == current) Color(0xFF2E7D32) else Color(0xFFEF6C00))
                            Text("  $count", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Automatic deploys (recommended)", fontWeight = FontWeight.Bold)
                Text("GitHub → repository Settings → Secrets → Actions: add CLOUDFLARE_API_TOKEN (Worker), SUPABASE_ACCESS_TOKEN + SUPABASE_PROJECT_REF (Edge Functions) and FIREBASE_SERVICE_ACCOUNT (rules). Every push then deploys the server automatically.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = .15f)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun CheckChip(label: String, ok: Boolean) {
    Surface(shape = CircleShape, color = (if (ok) Color(0xFF2E7D32) else Color(0xFF9E9E9E)).copy(alpha = .13f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, Modifier.size(12.dp), tint = if (ok) Color(0xFF2E7D32) else Color(0xFF9E9E9E))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp)
        }
    }
}
