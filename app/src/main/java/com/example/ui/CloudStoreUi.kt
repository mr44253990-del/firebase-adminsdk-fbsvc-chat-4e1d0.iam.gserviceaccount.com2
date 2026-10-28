package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkerRouter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val DEFAULT_WORKER = "https://solitary-hill-dcdc.mr44253990.workers.dev"
private val workerHttp by lazy { OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build() }

/** Authenticated POST to the worker that owns [service]. Returns (HTTP code, JSON body). */
internal suspend fun convoWorkerPost(service: String, path: String, body: JSONObject): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
    val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitTask()?.token ?: error("Not signed in")
    WorkerRouter.execute(service, workerHttp, WorkerRouter.primary.value.ifBlank { DEFAULT_WORKER }) { base ->
        Request.Builder().url(base + path).header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
    }.use { r ->
        r.code to com.example.data.WorkerHealth.normalize(r.code, runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull() ?: JSONObject())
    }
}

/**
 * Optional "Cloud backup" message store. When the admin turns it on, the body of plain text /
 * emoji messages is written to a Cloudflare D1 database (through the Worker, which holds the
 * Cloudflare API token server-side) and the Firebase RTDB relay only carries the metadata
 * (`text = ""`, `cloudText = true`). Ticks, reactions, typing, media and calls are unchanged.
 * Public switch: Firestore `app_config/cloud_db` = { enabled, version }.
 * Secrets: Firestore `admin_secrets/cloud_db` (admin-only rules), read by the Worker.
 */
object CloudMessageStore {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    @Volatile private var version = 0L
    private var listener: ListenerRegistration? = null
    private val memory = ConcurrentHashMap<String, String>()
    private var prefs: android.content.SharedPreferences? = null

    @Volatile private var authHooked = false

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("cloud_text_cache", Context.MODE_PRIVATE)
        if (!authHooked) {
            authHooked = true
            // Attach after sign-in, detach on sign-out (init can run before login).
            FirebaseAuth.getInstance().addAuthStateListener { a -> if (a.currentUser != null) attach() else stop() }
        }
        attach()
    }

    private fun attach() {
        if (listener != null || FirebaseAuth.getInstance().currentUser == null) return
        listener = FirebaseFirestore.getInstance().collection("app_config").document("cloud_db")
            .addSnapshotListener { doc, _ ->
                _enabled.value = doc?.getBoolean("enabled") == true
                version = doc?.getLong("version") ?: 0L
            }
    }

    fun stop() { listener?.remove(); listener = null; _enabled.value = false }

    fun cached(id: String): String? = memory[id] ?: prefs?.getString(id, null)?.also { memory[id] = it }

    fun remember(id: String, text: String) {
        if (id.isBlank() || text.isBlank()) return
        memory[id] = text
        prefs?.let { p ->
            p.edit().putString(id, text).apply()
            // Keep the on-device lookup cache bounded; Room already holds the durable copy.
            if (p.all.size > 4000) p.edit().clear().apply()
        }
    }

    suspend fun put(chatId: String, id: String, text: String, ts: Long): Boolean = runCatching {
        val (code, json) = convoWorkerPost("database", "/clouddb/put", JSONObject().put("chatId", chatId).put("id", id).put("text", text).put("ts", ts).put("v", version))
        (code in 200..299 && json.optBoolean("success")).also { if (it) remember(id, text) }
    }.getOrDefault(false)

    /** Batch-resolves message bodies; returns only the ids that were found. */
    suspend fun fetch(chatId: String, ids: List<String>): Map<String, String> {
        val missing = ids.filter { cached(it) == null }.distinct()
        val out = HashMap<String, String>()
        ids.forEach { id -> cached(id)?.let { out[id] = it } }
        missing.chunked(100).forEach { chunk ->
            runCatching {
                val (code, json) = convoWorkerPost("database", "/clouddb/get", JSONObject().put("chatId", chatId).put("ids", JSONArray(chunk)).put("v", version))
                if (code in 200..299) json.optJSONObject("items")?.let { items ->
                    items.keys().forEach { k -> val t = items.optString(k); if (t.isNotBlank()) { remember(k, t); out[k] = t } }
                }
            }
        }
        return out
    }

    data class CloudRow(val id: String, val senderId: String, val text: String, val ts: Long)

    suspend fun history(chatId: String, before: Long = 0L, limit: Int = 300): List<CloudRow> = runCatching {
        val (code, json) = convoWorkerPost("database", "/clouddb/history", JSONObject().put("chatId", chatId).put("before", before).put("limit", limit).put("v", version))
        if (code !in 200..299) return emptyList()
        val arr = json.optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CloudRow(o.optString("id"), o.optString("sender_id"), o.optString("text"), o.optLong("ts")).takeIf { it.id.isNotBlank() && it.text.isNotBlank() }
                ?.also { remember(it.id, it.text) }
        }
    }.getOrDefault(emptyList())

    suspend fun delete(chatId: String, ids: List<String>) {
        runCatching { convoWorkerPost("database", "/clouddb/delete", JSONObject().put("chatId", chatId).put("ids", JSONArray(ids)).put("v", version)) }
        ids.forEach { memory.remove(it); prefs?.edit()?.remove(it)?.apply() }
    }
}

/** Admin page: Cloudflare D1 credentials, Test (creates the table) and on/off switch. */
@Composable
fun AdminCloudStorePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf("") }
    var databaseId by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testedOk by remember { mutableStateOf(false) }
    var perUserLimit by remember { mutableStateOf("") }
    var userLimits by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var overrideUser by remember { mutableStateOf("") }
    var overrideLimit by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    val secrets = remember { FirebaseFirestore.getInstance().collection("admin_secrets").document("cloud_db") }

    LaunchedEffect(Unit) {
        runCatching { secrets.get().awaitTask() }.onSuccess { d ->
            enabled = d.getBoolean("enabled") == true
            accountId = d.getString("accountId").orEmpty()
            databaseId = d.getString("databaseId").orEmpty()
            apiToken = d.getString("apiToken").orEmpty()
            testedOk = d.getBoolean("tested") == true
            perUserLimit = (d.getLong("perUserLimit") ?: 0L).takeIf { it > 0 }?.toString().orEmpty()
            @Suppress("UNCHECKED_CAST")
            userLimits = (d.get("userLimits") as? Map<String, Any?>)?.mapNotNull { (k, v) -> (v as? Number)?.toLong()?.let { k to it } }?.toMap().orEmpty()
        }.onFailure { status = false to "Could not load: ${it.localizedMessage}. Deploy firestore.rules first." }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF38020).copy(.22f), Color(0xFFFFB347).copy(.10f))))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF38020)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CloudDone, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Cloud backup for text messages", fontWeight = FontWeight.ExtraBold)
                Text(if (enabled) "ON · text & emoji bodies go to Cloudflare D1" else "OFF · everything stays on Firebase RTDB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(enabled, { on ->
                if (on && !testedOk) Toast.makeText(context, "Press Test first so the table is created", Toast.LENGTH_LONG).show()
                else enabled = on
            })
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.5f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("One-time setup (2 minutes)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                listOf(
                    "1. Cloudflare dashboard → Storage & Databases → D1 → Create database (any name).",
                    "2. Copy the Database ID from the database page.",
                    "3. Account ID: right side of the dashboard home (or Workers & Pages overview).",
                    "4. My Profile → API Tokens → Create token → Custom → permission Account · D1 · Edit.",
                    "5. Paste all three below, press Test (creates the table), then turn ON and Save."
                ).forEach { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        OutlinedTextField(accountId, { accountId = it.trim().take(64); testedOk = false }, label = { Text("Cloudflare Account ID") }, singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(databaseId, { databaseId = it.trim().take(64); testedOk = false }, label = { Text("D1 Database ID") }, singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            apiToken, { apiToken = it.trim().take(200); testedOk = false }, label = { Text("API token (D1 Edit)") }, singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                busy = true; status = null
                scope.launch {
                    val result = runCatching {
                        convoWorkerPost("database", "/clouddb/admin/test", JSONObject().put("accountId", accountId).put("databaseId", databaseId).put("apiToken", apiToken))
                    }
                    busy = false
                    result.onSuccess { (code, json) ->
                        testedOk = code in 200..299 && json.optBoolean("success")
                        status = if (testedOk) true to "✓ Connected · table ready · ${json.optInt("messages")} messages stored · ${json.optLong("latencyMs")} ms"
                        else false to "✗ ${json.optString("error").ifBlank { "HTTP $code" }}" + if ((code == 404 || code == 405) && !json.optBoolean("outdatedWorker")) " — redeploy the Worker (v4.3.3) once" else ""
                    }.onFailure { status = false to "✗ ${it.localizedMessage}" }
                }
            }, enabled = !busy && accountId.isNotBlank() && databaseId.isNotBlank() && apiToken.length > 10, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.NetworkCheck, null)
                Spacer(Modifier.width(6.dp)); Text("Test")
            }
            Button(onClick = {
                busy = true
                scope.launch {
                    val now = System.currentTimeMillis()
                    val db = FirebaseFirestore.getInstance()
                    val r = runCatching {
                        secrets.set(mapOf("enabled" to (enabled && testedOk), "accountId" to accountId, "databaseId" to databaseId, "apiToken" to apiToken, "tested" to testedOk,
                            "perUserLimit" to (perUserLimit.toLongOrNull() ?: 0L), "userLimits" to userLimits, "updatedAt" to now)).awaitTask()
                        db.collection("app_config").document("cloud_db").set(mapOf("enabled" to (enabled && testedOk), "version" to now, "updatedAt" to now)).awaitTask()
                    }
                    busy = false
                    r.onSuccess {
                        AdminAudit.log("cloud_db_save", "enabled=${enabled && testedOk}")
                        Toast.makeText(context, if (enabled && testedOk) "Saved — new text messages now go to Cloudflare D1" else "Saved — cloud backup is OFF", Toast.LENGTH_LONG).show()
                    }.onFailure { Toast.makeText(context, "Save failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                }
            }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save") }
        }
        status?.let { (ok, msg) -> Text(msg, fontSize = 12.sp, color = if (ok) Color(0xFF2E9E6A) else MaterialTheme.colorScheme.error) }

        // ---- Per-user storage limit ---------------------------------------------------------
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.5f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Per-user message limit", fontWeight = FontWeight.Bold)
                Text("Each user keeps only their newest N cloud messages. When they send more, the oldest ones are deleted from D1 automatically. Messages already saved on phones stay there. Leave empty for no limit.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("100", "500", "1000", "5000").forEach { preset ->
                        FilterChip(selected = perUserLimit == preset, onClick = { perUserLimit = preset }, label = { Text(preset) })
                    }
                }
                OutlinedTextField(perUserLimit, { perUserLimit = it.filter(Char::isDigit).take(7) }, label = { Text("Messages per user (default for everyone)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Custom limit for one user", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(overrideUser, { overrideUser = it.trim() }, label = { Text("User ID") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(overrideLimit, { overrideLimit = it.filter(Char::isDigit).take(7) }, label = { Text("Limit") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = overrideUser.length > 5 && overrideLimit.isNotBlank(), onClick = {
                        userLimits = userLimits + (overrideUser to (overrideLimit.toLongOrNull() ?: 0L)); overrideUser = ""; overrideLimit = ""
                    }) { Icon(Icons.Default.Add, "Add") }
                }
                userLimits.forEach { (uid, lim) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${uid.take(14)}…  →  $lim", fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        IconButton(onClick = { userLimits = userLimits - uid }) { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp)) }
                    }
                }
                Text("Press Save above to apply limits.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            val (code, j) = runCatching { convoWorkerPost("database", "/clouddb/admin/stats", JSONObject()) }.getOrElse { 0 to JSONObject().put("error", it.localizedMessage) }
                            busy = false
                            if (code in 200..299) stats = j else Toast.makeText(context, j.optString("error").ifBlank { "HTTP $code" }, Toast.LENGTH_LONG).show()
                        }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Insights, null); Spacer(Modifier.width(4.dp)); Text("Usage") }
                    Button(enabled = !busy && (perUserLimit.toLongOrNull() ?: 0L) > 0, onClick = {
                        scope.launch {
                            busy = true
                            val (code, j) = runCatching { convoWorkerPost("database", "/clouddb/admin/prune", JSONObject().put("limit", perUserLimit.toLongOrNull() ?: 0L)) }.getOrElse { 0 to JSONObject().put("error", it.localizedMessage) }
                            busy = false
                            Toast.makeText(context, if (code in 200..299) "Cleaned up: ${j.optInt("deleted")} old messages deleted" else j.optString("error").ifBlank { "HTTP $code" }, Toast.LENGTH_LONG).show()
                        }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CleaningServices, null); Spacer(Modifier.width(4.dp)); Text("Apply now") }
                }
                stats?.let { st ->
                    Text("${st.optInt("messages")} messages from ${st.optInt("users")} users", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    val top = st.optJSONArray("top")
                    if (top != null) for (i in 0 until minOf(top.length(), 10)) {
                        val row = top.optJSONObject(i) ?: continue
                        val n = row.optLong("n"); val lim = perUserLimit.toLongOrNull() ?: 0L
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.optString("sender_id").take(12) + "…", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(118.dp))
                            LinearProgressIndicator(progress = { if (lim > 0) (n.toFloat() / lim).coerceIn(0f, 1f) else 1f }, modifier = Modifier.weight(1f).height(5.dp))
                            Spacer(Modifier.width(6.dp)); Text("$n", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Text(
            "Only text and emoji bodies move to D1. Photos, voice, files, reactions, ticks, typing and calls work exactly as before. " +
                "If the cloud is unreachable a message falls back to Firebase automatically so nothing is lost. The token is stored in admin_secrets (admin-only) and used only by the Worker.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
