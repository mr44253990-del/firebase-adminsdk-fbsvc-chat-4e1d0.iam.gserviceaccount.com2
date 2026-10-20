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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
 * Cloudflare Worker updater. Uploads either the Worker code bundled in the APK
 * (assets/worker/cloudflare-worker.js) or a .js file the admin picks, with `keep_bindings`, so
 * every existing secret (FIREBASE_SERVICE_ACCOUNT, TURN, Calls, Mistral …) and binding stays.
 * The API token is only kept if the admin asks (encrypted with the Android Keystore).
 */
object WorkerDeployer {
    const val DEFAULT_ACCOUNT = "54ff9e3c4c5908b14f410044be85b698"
    const val DEFAULT_SCRIPT = "solitary-hill-dcdc"
    private const val API = "https://api.cloudflare.com/client/v4"
    private val http by lazy { OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build() }

    fun bundledCode(context: Context): String? =
        runCatching { context.assets.open("worker/cloudflare-worker.js").bufferedReader().use { it.readText() } }.getOrNull()

    fun apiVersionOf(code: String): Int =
        Regex("""WORKER_API_VERSION\s*=\s*(\d+)""").find(code)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun looksLikeWorker(code: String): Boolean =
        code.length > 40 && (code.contains("export default") || code.contains("addEventListener(\"fetch\"") || code.contains("addEventListener('fetch'"))

    /** Worker script name for a `https://<name>.<sub>.workers.dev` URL (null for custom domains). */
    fun scriptFromUrl(url: String): String? {
        val host = url.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
        return if (host.endsWith(".workers.dev")) host.substringBefore('.').takeIf { it.isNotBlank() } else null
    }

    private fun errorText(json: JSONObject, code: Int): String {
        val errors = json.optJSONArray("errors")
        val msg = (0 until (errors?.length() ?: 0)).joinToString("; ") { errors!!.optJSONObject(it)?.optString("message").orEmpty() }
        return msg.ifBlank { "Cloudflare HTTP $code" } + if (code == 401 || code == 403) " — the token needs \"Workers Scripts: Edit\" for this account." else ""
    }

    /** Checks the token and returns (ok, message, accounts[id to name]). */
    suspend fun verifyToken(apiToken: String): Triple<Boolean, String, List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = "Bearer ${apiToken.trim()}"
            val verify = http.newCall(Request.Builder().url("$API/user/tokens/verify").header("Authorization", auth).build()).execute().use { r ->
                val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (r.isSuccessful && j.optBoolean("success")) j.optJSONObject("result")?.optString("status").orEmpty() else "error:" + errorText(j, r.code)
            }
            if (verify.startsWith("error:")) return@runCatching Triple(false, verify.removePrefix("error:"), emptyList<Pair<String, String>>())
            val accounts = http.newCall(Request.Builder().url("$API/accounts?per_page=50").header("Authorization", auth).build()).execute().use { r ->
                val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                val arr = j.optJSONArray("result") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { it.optString("id") to it.optString("name") } }.filter { it.first.isNotBlank() }
            }
            Triple(verify == "active", if (verify == "active") "Token is active · ${accounts.size} account(s)" else "Token status: $verify", accounts)
        }.getOrElse { Triple(false, it.localizedMessage ?: "Network error", emptyList()) }
    }

    suspend fun deploy(context: Context, apiToken: String, accountId: String, scriptName: String, customCode: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val code = customCode ?: bundledCode(context) ?: return@withContext false to "This build doesn't contain the Worker code. Pick a .js file or paste it in the Cloudflare dashboard."
        if (!looksLikeWorker(code)) return@withContext false to "That file doesn't look like a Worker script (no fetch handler found)."
        val module = code.contains("export default")
        runCatching {
            val meta = JSONObject()
                .put("compatibility_date", "2026-10-01")
                .put("keep_bindings", JSONArray(listOf("secret_text", "secret_key", "plain_text", "json", "r2_bucket", "kv_namespace", "d1", "service", "queue", "analytics_engine")))
            if (module) meta.put("main_module", "worker.js") else meta.put("body_part", "worker.js")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, meta.toString().toRequestBody("application/json".toMediaType()))
                .addFormDataPart("worker.js", "worker.js", code.toRequestBody((if (module) "application/javascript+module" else "application/javascript").toMediaType()))
                .build()
            val req = Request.Builder()
                .url("$API/accounts/${accountId.trim()}/workers/scripts/${scriptName.trim()}")
                .header("Authorization", "Bearer ${apiToken.trim()}")
                .put(body).build()
            http.newCall(req).execute().use { r ->
                val json = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (r.isSuccessful && json.optBoolean("success")) true to "Uploaded (${code.length / 1024} KB)"
                else false to errorText(json, r.code)
            }
        }.getOrElse { false to (it.localizedMessage ?: "Network error") }
    }

    /** Sets / replaces one secret on a Worker (value is sent once, never stored). */
    suspend fun putSecret(apiToken: String, accountId: String, scriptName: String, name: String, value: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("name", name.trim()).put("text", value).put("type", "secret_text").toString().toRequestBody("application/json".toMediaType())
            http.newCall(
                Request.Builder().url("$API/accounts/${accountId.trim()}/workers/scripts/${scriptName.trim()}/secrets")
                    .header("Authorization", "Bearer ${apiToken.trim()}").put(body).build()
            ).execute().use { r ->
                val json = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (r.isSuccessful && json.optBoolean("success")) true to "Secret ${name.trim()} saved on $scriptName" else false to errorText(json, r.code)
            }
        }.getOrElse { false to (it.localizedMessage ?: "Network error") }
    }
}

/** Optional on-device storage for the Cloudflare token, encrypted with an Android Keystore key. */
object DeployTokenVault {
    private const val ALIAS = "convo_cf_deploy_token"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("convo_deployer", Context.MODE_PRIVATE)

    private fun key(): javax.crypto.SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun save(ctx: Context, token: String) = runCatching {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(token.toByteArray())
        prefs(ctx).edit().putString("t", android.util.Base64.encodeToString(c.iv + enc, android.util.Base64.NO_WRAP)).apply()
    }.isSuccess

    fun load(ctx: Context): String = runCatching {
        val raw = android.util.Base64.decode(prefs(ctx).getString("t", null) ?: return "", android.util.Base64.NO_WRAP)
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    }.getOrDefault("")

    fun clear(ctx: Context) = prefs(ctx).edit().remove("t").apply()
    fun has(ctx: Context) = prefs(ctx).contains("t")
    fun saveTargets(ctx: Context, account: String, script: String) = prefs(ctx).edit().putString("account", account).putString("script", script).apply()
    fun account(ctx: Context) = prefs(ctx).getString("account", null)
    fun script(ctx: Context) = prefs(ctx).getString("script", null)
}

/** Result of one deploy target, shown as a live checklist. */
private data class DeployResult(val script: String, val url: String?, val uploaded: Boolean?, val message: String, val live: WorkerHealth.Report? = null)

@Composable
fun AdminServerHealthPanel(users: List<User>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reports by WorkerHealth.reports.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var apiToken by remember { mutableStateOf(DeployTokenVault.load(context)) }
    var rememberToken by remember { mutableStateOf(DeployTokenVault.has(context)) }
    var accountId by remember { mutableStateOf(DeployTokenVault.account(context) ?: WorkerDeployer.DEFAULT_ACCOUNT) }
    var scriptName by remember { mutableStateOf(DeployTokenVault.script(context) ?: WorkerDeployer.DEFAULT_SCRIPT) }
    var deploying by remember { mutableStateOf(false) }
    var deployMsg by remember { mutableStateOf("") }
    val bundled = remember { WorkerDeployer.bundledCode(context) }
    var uploadedCode by remember { mutableStateOf<String?>(null) }
    var uploadedName by remember { mutableStateOf("") }
    var useUploaded by remember { mutableStateOf(false) }
    var tokenCheck by remember { mutableStateOf("") }
    var tokenOk by remember { mutableStateOf<Boolean?>(null) }
    var accounts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var deployPool by remember { mutableStateOf(false) }
    val results = remember { androidx.compose.runtime.mutableStateListOf<DeployResult>() }
    var secretName by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var secretMsg by remember { mutableStateOf("") }
    val pickWorkerFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size <= 10 * 1024 * 1024 }?.toString(Charsets.UTF_8) }.getOrNull()
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "worker.js"
        if (text == null || !WorkerDeployer.looksLikeWorker(text)) {
            Toast.makeText(context, "That file is not a Worker script (needs a fetch handler, max 10 MB)", Toast.LENGTH_LONG).show()
        } else { uploadedCode = text; uploadedName = name; useUploaded = true }
    }

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

        // ---- Worker deployer ---------------------------------------------------------------
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Deploy Worker from this phone", fontWeight = FontWeight.Bold)
                }
                Text("Upload the Worker built into this app or any cloudflare-worker.js file. Secrets and bindings are kept. Token: dash.cloudflare.com → My Profile → API Tokens → \"Edit Cloudflare Workers\" template.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 1) Code source
                Text("1 · Code", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                val bundledVersion = remember(bundled) { bundled?.let { WorkerDeployer.apiVersionOf(it) } ?: 0 }
                SourceOption(
                    selected = !useUploaded, enabled = bundled != null,
                    title = "Built into this app", subtitle = if (bundled != null) "API v$bundledVersion · ${bundled.length / 1024} KB" else "Not included in this build"
                ) { useUploaded = false }
                SourceOption(
                    selected = useUploaded, enabled = true,
                    title = if (uploadedCode != null) uploadedName else "Upload a .js file",
                    subtitle = uploadedCode?.let { "API v${WorkerDeployer.apiVersionOf(it).takeIf { v -> v > 0 } ?: "?"} · ${it.length / 1024} KB · tap to pick another" } ?: "Pick cloudflare-worker.js from your phone"
                ) { if (uploadedCode == null || useUploaded) pickWorkerFile.launch(arrayOf("*/*")) else useUploaded = true }

                // 2) Token
                Text("2 · Cloudflare access", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedTextField(apiToken, { apiToken = it.trim(); tokenOk = null; tokenCheck = "" }, Modifier.fillMaxWidth(), label = { Text("Cloudflare API token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(rememberToken, { on -> rememberToken = on; if (!on) DeployTokenVault.clear(context) })
                    Text("Remember token on this phone (encrypted)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(
                        onClick = { scope.launch { tokenCheck = "Checking…"; val (ok, msg, list) = WorkerDeployer.verifyToken(apiToken); tokenOk = ok; tokenCheck = msg; accounts = list; if (ok && list.size == 1) accountId = list.first().first } },
                        enabled = apiToken.length > 20
                    ) { Text("Verify") }
                }
                if (tokenCheck.isNotBlank()) Text(tokenCheck, fontSize = 12.sp, color = when (tokenOk) { true -> Color(0xFF2E7D32); false -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                if (accounts.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    accounts.forEach { (id, name) ->
                        androidx.compose.material3.FilterChip(selected = accountId == id, onClick = { accountId = id }, label = { Text(name.ifBlank { id.take(8) }, fontSize = 11.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(accountId, { accountId = it.trim() }, Modifier.weight(1f), label = { Text("Account ID") }, singleLine = true)
                    OutlinedTextField(scriptName, { scriptName = it.trim() }, Modifier.weight(1f), label = { Text("Worker name") }, singleLine = true)
                }

                // 3) Targets
                val poolScripts = remember(reports, scriptName) {
                    reports.mapNotNull { r -> WorkerDeployer.scriptFromUrl(r.url)?.let { it to r.url } }.filter { it.first != scriptName }.distinctBy { it.first }
                }
                if (poolScripts.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(deployPool, { deployPool = it })
                    Text("Also deploy to ${poolScripts.size} pool worker(s): ${poolScripts.joinToString { it.first }}", fontSize = 12.sp)
                }

                if (deploying) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (deployMsg.isNotBlank()) Text(deployMsg, fontSize = 12.sp, color = if (deployMsg.startsWith("All")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
                results.forEach { res -> DeployResultRow(res) }
                Button(
                    onClick = {
                        scope.launch {
                            deploying = true; deployMsg = ""; results.clear()
                            if (rememberToken) DeployTokenVault.save(context, apiToken)
                            DeployTokenVault.saveTargets(context, accountId, scriptName)
                            val code = if (useUploaded) uploadedCode else null
                            val primaryUrl = WorkerRouter.primary.value.ifBlank { WorkerHealth.DEFAULT_PRIMARY }
                            val targets = buildList {
                                add(scriptName to (reports.firstOrNull { WorkerDeployer.scriptFromUrl(it.url) == scriptName }?.url ?: if (scriptName == WorkerDeployer.DEFAULT_SCRIPT || WorkerDeployer.scriptFromUrl(primaryUrl) == scriptName) primaryUrl else null))
                                if (deployPool) poolScripts.forEach { add(it.first to it.second) }
                            }
                            val expected = (code?.let { WorkerDeployer.apiVersionOf(it) } ?: WorkerDeployer.apiVersionOf(bundled.orEmpty())).takeIf { it > 0 }
                            targets.forEach { (script, url) ->
                                results += DeployResult(script, url, null, "Uploading…")
                                val idx = results.lastIndex
                                val (ok, msg) = WorkerDeployer.deploy(context, apiToken, accountId, script, code)
                                results[idx] = DeployResult(script, url, ok, if (ok) "$msg · checking live version…" else msg)
                                if (ok && url != null) {
                                    var report = WorkerHealth.check(url)
                                    repeat(6) { if (expected != null && report.apiVersion < expected) { delay(2500); report = WorkerHealth.check(url) } }
                                    val fine = report.reachable && (expected == null || report.apiVersion >= expected)
                                    results[idx] = DeployResult(script, url, true,
                                        if (fine) ("Live API v${report.apiVersion} · ${report.latencyMs} ms" + (if (!report.serviceAccount) " · FIREBASE_SERVICE_ACCOUNT missing" else ""))
                                        else if (!report.reachable) "Uploaded, but the Worker doesn't answer: ${report.error}"
                                        else "Uploaded, live still v${report.apiVersion}. If gradual deployments are on, promote the new version in Cloudflare.",
                                        report)
                                } else if (ok) results[idx] = DeployResult(script, null, true, "$msg · no URL known to health-check")
                            }
                            if (!rememberToken) apiToken = ""
                            WorkerHealth.checkAll()
                            val okCount = results.count { it.uploaded == true }
                            deployMsg = if (okCount == results.size && results.all { it.live == null || it.live.healthy }) "All ${results.size} target(s) deployed and healthy."
                                else "$okCount of ${results.size} target(s) uploaded. See details below."
                            AdminAudit.log("worker_deploy", "targets=${targets.joinToString { it.first }} source=${if (code != null) uploadedName else "bundled"} ok=$okCount")
                            deploying = false
                        }
                    },
                    enabled = !deploying && apiToken.length > 20 && accountId.isNotBlank() && scriptName.isNotBlank() && (if (useUploaded) uploadedCode != null else bundled != null),
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.CloudSync, null); Spacer(Modifier.width(8.dp)); Text(if (deploying) "Deploying…" else "Deploy now") }

                // Optional: set a Worker secret
                HorizontalDivider()
                Text("Set a Worker secret (optional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("For example GMAIL_SERVICE_ACCOUNT, TAVILY_API_KEY or FIREBASE_SERVICE_ACCOUNT. Sent once to Cloudflare over HTTPS, never stored in the app.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(secretName, { v -> secretName = v.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(60) }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(secretValue, { secretValue = it }, Modifier.weight(1.4f), label = { Text("Value") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
                if (secretMsg.isNotBlank()) Text(secretMsg, fontSize = 12.sp)
                OutlinedButton(
                    onClick = { scope.launch { secretMsg = "Saving…"; val (ok, m) = WorkerDeployer.putSecret(apiToken, accountId, scriptName, secretName, secretValue); secretMsg = m; if (ok) { secretValue = ""; WorkerHealth.checkAll() } } },
                    enabled = apiToken.length > 20 && secretName.length >= 2 && secretValue.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save secret on $scriptName") }

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
                ) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy built-in Worker code") }
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
private fun SourceOption(selected: Boolean, enabled: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface.copy(alpha = .55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeployResultRow(res: DeployResult) {
    val color = when {
        res.uploaded == null -> MaterialTheme.colorScheme.primary
        res.uploaded == false -> Color(0xFFC62828)
        res.live != null && !res.live.reachable -> Color(0xFFC62828)
        res.live != null && res.live.outdated -> Color(0xFFEF6C00)
        else -> Color(0xFF2E7D32)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .10f)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (res.uploaded == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(if (color == Color(0xFF2E7D32)) Icons.Default.CheckCircle else if (color == Color(0xFFEF6C00)) Icons.Default.Warning else Icons.Default.Error, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(res.script, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(res.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                res.live?.takeIf { it.reachable }?.let { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        CheckChip("Firebase", r.serviceAccount); CheckChip("AI", r.ai); CheckChip("TURN", r.turn); CheckChip("R2", r.r2)
                    }
                }
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
