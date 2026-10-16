package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WorkerRouter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Minimal Task -> coroutine bridge (the play-services coroutine artifact is not a dependency). */
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resumeWith(Result.success(it)) }
    addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    addOnCanceledListener { cont.cancel() }
}

/** One OpenAI-compatible AI provider managed from the admin panel. */
data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = true,
    val custom: Boolean = false
) {
    val ready: Boolean get() = enabled && apiKey.length > 8 && baseUrl.startsWith("https://")
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name, "baseUrl" to baseUrl.trim().trimEnd('/'), "apiKey" to apiKey.trim(),
        "model" to model.trim(), "enabled" to enabled, "custom" to custom
    )
}

/**
 * Provider registry stored in Firestore `admin_secrets/ai` (admin-only by security rules).
 * The Cloudflare Worker (/ai/chat) and the Supabase `auto-ai-post` function read the active
 * provider with the service account, so changing the key here takes effect within ~1 minute
 * without redeploying anything.
 */
object AiProviderStore {
    val PRESETS = listOf(
        AiProvider("mistral", "Mistral AI", "https://api.mistral.ai/v1", model = "mistral-small-latest"),
        AiProvider("openai", "OpenAI", "https://api.openai.com/v1", model = "gpt-4o-mini"),
        AiProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", model = "gemini-2.0-flash"),
        AiProvider("groq", "Groq", "https://api.groq.com/openai/v1", model = "llama-3.1-8b-instant"),
        AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", model = "openrouter/auto"),
        AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", model = "deepseek-chat"),
        AiProvider("together", "Together AI", "https://api.together.xyz/v1", model = "meta-llama/Llama-3.3-70B-Instruct-Turbo")
    )

    private val doc get() = FirebaseFirestore.getInstance().collection("admin_secrets").document("ai")

    data class Config(val activeId: String = "", val providers: List<AiProvider> = emptyList()) {
        val active: AiProvider? get() = providers.firstOrNull { it.id == activeId && it.ready }
    }

    suspend fun load(): Config {
        val snap = doc.get().awaitTask()
        val active = snap.getString("activeProviderId").orEmpty()
        @Suppress("UNCHECKED_CAST")
        val raw = snap.get("providers") as? Map<String, Map<String, Any?>> ?: emptyMap()
        val saved = raw.map { (id, m) ->
            AiProvider(
                id = id, name = m["name"]?.toString().orEmpty().ifBlank { id },
                baseUrl = m["baseUrl"]?.toString().orEmpty(), apiKey = m["apiKey"]?.toString().orEmpty(),
                model = m["model"]?.toString().orEmpty(), enabled = m["enabled"] as? Boolean ?: true,
                custom = m["custom"] as? Boolean ?: false
            )
        }
        // Always show every preset so the admin can fill in a key; saved values win.
        val merged = PRESETS.map { preset -> saved.firstOrNull { it.id == preset.id } ?: preset } +
            saved.filter { s -> PRESETS.none { it.id == s.id } }
        return Config(active, merged)
    }

    suspend fun save(config: Config) {
        val providers = config.providers.filter { it.apiKey.isNotBlank() || it.custom }.associate { it.id to it.toMap() }
        doc.set(mapOf("activeProviderId" to config.activeId, "providers" to providers, "updatedAt" to System.currentTimeMillis())).awaitTask()
    }

    private val http by lazy { OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build() }

    /** Lists the provider's models via the OpenAI-compatible GET /models endpoint. */
    suspend fun listModels(p: AiProvider): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(p.baseUrl.trimEnd('/') + "/models")
                .header("Authorization", "Bearer ${p.apiKey.trim()}").get().build()
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") ?: JSONObject(body).optString("message") }.getOrNull()
                    error("HTTP ${r.code}${msg?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
                }
                val json = runCatching { JSONObject(body) }.getOrNull()
                val arr = json?.optJSONArray("data") ?: json?.optJSONArray("models") ?: runCatching { JSONArray(body) }.getOrNull() ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.opt(i)
                    when (item) {
                        is JSONObject -> item.optString("id").ifBlank { item.optString("name") }.removePrefix("models/")
                        is String -> item
                        else -> null
                    }?.takeIf { it.isNotBlank() }
                }.distinct().sorted()
            }
        }
    }

    /** Single chat completion with an OpenAI-compatible provider. */
    suspend fun complete(p: AiProvider, system: String, user: String, temperature: Double = 0.9): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", p.model)
            .put("temperature", temperature)
            .put("max_tokens", 500)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        val req = Request.Builder().url(p.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${p.apiKey.trim()}")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("${p.name} HTTP ${r.code}")
            JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        }
    }

    /** Fallback: generate through the Worker's /ai/chat (uses the server-side key). */
    suspend fun completeViaWorker(system: String, user: String): String = withContext(Dispatchers.IO) {
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitTask()?.token ?: error("Not signed in")
        val base = WorkerRouter.resolve("ai", WorkerRouter.primary.value.ifBlank { "https://solitary-hill-dcdc.mr44253990.workers.dev" })
        val payload = JSONObject().put("message", user.take(4000)).put("systemPrompt", system.take(1200)).put("memory", JSONArray())
        val req = Request.Builder().url("$base/ai/chat").header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            if (!r.isSuccessful) error(json.optString("error").ifBlank { "Worker HTTP ${r.code}" })
            json.optString("reply")
        }
    }
}

private val autoBackgrounds = listOf("sunset", "ocean", "aurora", "forest", "rose", "midnight", "candy", "peach", "gold", "mint")
private val autoTypes = listOf("status", "tip", "quote", "question", "fun_fact", "motivation")
private val autoGuide = mapOf(
    "status" to "a friendly community status update or greeting that fits the time of day",
    "tip" to "one genuinely useful, practical tip (tech, productivity, safety, health basics or using the Convo Chat app)",
    "quote" to "an inspiring short quote (only attribute it if you are certain of the author) with a one-line reflection",
    "question" to "an engaging open question that invites people to comment",
    "fun_fact" to "one surprising but TRUE, well-known fun fact (no made-up statistics)",
    "motivation" to "a short motivational message",
    "poll" to "a light, fun community poll question with 2 to 4 short answer options"
)

/**
 * Creates one AI post RIGHT NOW from the admin's phone (no waiting for the 10-minute cron):
 * uses the active admin provider directly, or falls back to the Worker's server-side key.
 * Returns a short status message.
 */
suspend fun publishAiPostNow(settings: Map<String, Any?>): String {
    val auth = FirebaseAuth.getInstance().currentUser ?: error("Not signed in")
    val db = FirebaseFirestore.getInstance()
    val configured = settings["postType"] as? String ?: "mixed"
    val type = if (configured in autoGuide.keys) configured else autoTypes.random()
    val language = settings["language"] as? String ?: "Bengali"
    val useEmoji = settings["useEmoji"] as? Boolean ?: true
    val adminPrompt = (settings["prompt"] as? String).orEmpty().take(1500)
    val system = listOf(
        "You write short posts for the community feed of Convo Chat, a friendly messaging and social app.",
        "Write in $language. Keep it under 70 words, warm and natural, never spammy.",
        if (useEmoji) "Use 2 to 4 fitting emoji naturally inside the text." else "Do not use emoji.",
        "Never invent news, prices, medical or legal claims, or statistics. No politics, religion debates, hate, adult content or personal data.",
        "Reply ONLY with a JSON object: {\"title\": string (max 6 words, may be empty), \"text\": string, \"tags\": [up to 3 short hashtags without #], \"feeling\": string (one word, may be empty)" +
            (if (type == "poll") ", \"options\": [2-4 short strings]" else "") + "}."
    ).joinToString("\n")
    val cal = java.util.Calendar.getInstance()
    val user = listOfNotNull(
        "Post type: ${autoGuide[type]}.",
        "Local time for readers: %02d:%02d.".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)),
        adminPrompt.takeIf { it.isNotBlank() }?.let { "Admin guidance (follow it unless it breaks the rules above):\n$it" }
    ).joinToString("\n")

    val provider = runCatching { AiProviderStore.load().active }.getOrNull()
    val raw = if (provider != null) AiProviderStore.complete(provider, system, user) else AiProviderStore.completeViaWorker(system, user)
    val json = runCatching { JSONObject(raw) }.getOrNull()
        ?: Regex("\\{[\\s\\S]*\\}").find(raw)?.value?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject().put("text", raw)
    val text = json.optString("text").trim().take(1200)
    if (text.isBlank()) error("AI returned an empty post")
    val tags = (0 until (json.optJSONArray("tags")?.length() ?: 0)).map { json.optJSONArray("tags")!!.optString(it).removePrefix("#").trim() }.filter { it.isNotBlank() }.take(3)
    val options = (0 until (json.optJSONArray("options")?.length() ?: 0)).map { json.optJSONArray("options")!!.optString(it).trim().take(60) }.filter { it.isNotBlank() }.take(4)
    val profile = runCatching { db.collection("users").document(auth.uid).get().awaitTask() }.getOrNull()
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()
    if (type == "poll" && options.size >= 2) {
        db.collection("polls").document(id).set(mapOf(
            "question" to text.take(200), "options" to options,
            "counts" to options.indices.associate { it.toString() to 0 }, "totalVotes" to 0,
            "createdAt" to now, "endsAt" to now + ((settings["pollHours"] as? Number)?.toLong() ?: 24L) * 3_600_000L,
            "active" to true, "createdBy" to auth.uid, "autoGenerated" to true
        )).awaitTask()
    } else {
        db.collection("posts").document(id).set(mapOf(
            "id" to id, "senderId" to auth.uid,
            "senderName" to (profile?.getString("name") ?: "Convo Chat"),
            "senderProfilePic" to (profile?.getString("profileImageUrl") ?: ""),
            "text" to text, "title" to json.optString("title").trim().take(80), "tags" to tags,
            "feeling" to json.optString("feeling").trim().take(30),
            "imageUrl" to "", "audioUrl" to "", "videoUrl" to "", "imageUrls" to emptyList<String>(),
            "timestamp" to now, "reactions" to emptyMap<String, String>(), "comments" to emptyList<Any>(),
            "viewsCount" to 0, "private" to false, "isPrivate" to false, "reel" to false, "isReel" to false,
            "backgroundStyle" to if (settings["styledBackground"] == false) "glass" else autoBackgrounds.random(),
            "textAnimation" to "none", "postEverywhere" to true, "autoGenerated" to true, "autoPostType" to type
        )).awaitTask()
    }
    db.collection("app_config").document("auto_post").set(mapOf(
        "lastRunAt" to now, "lastPostId" to id, "lastPostType" to type, "lastPostPreview" to text.take(160),
        "lastError" to "", "totalPosts" to com.google.firebase.firestore.FieldValue.increment(1)
    ), com.google.firebase.firestore.SetOptions.merge()).awaitTask()
    return "Posted now via ${provider?.name ?: "server key"} (${type.replace('_', ' ')})"
}

/** Admin panel: manage AI providers & keys, test them (lists models) and hide/show AI. */
@Composable
fun AdminAiProvidersPanel(assistantEnabled: Boolean, onAssistantEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(AiProviderStore.Config()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var testing by remember { mutableStateOf<String?>(null) }
    var testMsg by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var modelFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        config = runCatching { AiProviderStore.load() }.getOrElse {
            Toast.makeText(context, "Could not load AI settings: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            AiProviderStore.Config(providers = AiProviderStore.PRESETS)
        }
        loading = false
    }
    fun update(p: AiProvider) { config = config.copy(providers = config.providers.map { if (it.id == p.id) p else it }) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF8E5CE6).copy(.18f), Color(0xFF22D3EE).copy(.12f))))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (assistantEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color(0xFF8E5CE6))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Show AI features", fontWeight = FontWeight.Bold)
                Text("Off hides the assistant button, AI replies and translation for everyone.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(assistantEnabled, onAssistantEnabled)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading) {

        Text("Active provider", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = config.activeId.isBlank(), onClick = { config = config.copy(activeId = "") }, label = { Text("Server default (Mistral secret)") })
            config.providers.filter { it.ready }.forEach { p ->
                FilterChip(selected = config.activeId == p.id, onClick = { config = config.copy(activeId = p.id) }, label = { Text(if (config.activeId == p.id) "✓ ${p.name}" else p.name) })
            }
        }
        config.active?.let { Text("Using ${it.name} · ${it.model.ifBlank { "default model" }}", fontSize = 11.sp, color = Color(0xFF2E9E6A)) }

        config.providers.forEach { p ->
            val open = expanded == p.id
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (open) null else p.id }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(providerColor(p.id).copy(.2f)), contentAlignment = Alignment.Center) {
                            Text(p.name.take(1), fontWeight = FontWeight.ExtraBold, color = providerColor(p.id))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name + if (p.custom) " · custom" else "", fontWeight = FontWeight.Bold)
                            Text(
                                when { p.ready && config.activeId == p.id -> "Active · ${p.model}"; p.ready -> "Key saved · ${p.model.ifBlank { "pick a model" }}"; else -> "No key yet" },
                                fontSize = 11.sp, color = if (p.ready) Color(0xFF2E9E6A) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    AnimatedVisibility(open) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            var showKey by remember(p.id) { mutableStateOf(false) }
                            if (p.custom) OutlinedTextField(p.name, { update(p.copy(name = it.take(40))) }, label = { Text("Provider name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(p.baseUrl, { update(p.copy(baseUrl = it.trim())) }, label = { Text("Base URL (OpenAI-compatible)") }, supportingText = { Text("…/v1 — the app calls /models and /chat/completions") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(
                                p.apiKey, { update(p.copy(apiKey = it.trim())) }, label = { Text("API key") }, singleLine = true,
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(p.model, { update(p.copy(model = it.trim().take(120))) }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = {
                                    testing = p.id
                                    scope.launch {
                                        val r = AiProviderStore.listModels(p)
                                        testing = null
                                        r.onSuccess { list -> models = models + (p.id to list); testMsg = testMsg + (p.id to "✓ Key works · ${list.size} models") }
                                            .onFailure { testMsg = testMsg + (p.id to "✗ ${it.localizedMessage}") }
                                    }
                                }, enabled = p.apiKey.length > 8 && p.baseUrl.startsWith("https://") && testing == null) {
                                    if (testing == p.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.NetworkCheck, null)
                                    Spacer(Modifier.width(6.dp)); Text("Test")
                                }
                                Spacer(Modifier.weight(1f))
                                Text("Enabled", fontSize = 12.sp); Spacer(Modifier.width(4.dp))
                                Switch(p.enabled, { update(p.copy(enabled = it)) })
                            }
                            testMsg[p.id]?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("✓")) Color(0xFF2E9E6A) else MaterialTheme.colorScheme.error) }
                            models[p.id]?.takeIf { it.isNotEmpty() }?.let { list ->
                                OutlinedTextField(modelFilter, { modelFilter = it }, label = { Text("Filter ${list.size} models") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    list.filter { modelFilter.isBlank() || it.contains(modelFilter, true) }.take(150).forEach { m ->
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                .background(if (m == p.model) MaterialTheme.colorScheme.primary.copy(.16f) else Color.Transparent)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                                .clickable { update(p.copy(model = m)) }.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(m, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                            if (m == p.model) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { config = config.copy(activeId = p.id) }, enabled = p.ready, modifier = Modifier.weight(1f)) { Text(if (config.activeId == p.id) "Active ✓" else "Use this provider") }
                                if (p.custom) OutlinedButton(onClick = {
                                    config = config.copy(providers = config.providers.filterNot { it.id == p.id }, activeId = if (config.activeId == p.id) "" else config.activeId)
                                }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null) }
                                else if (p.apiKey.isNotBlank()) OutlinedButton(onClick = { update(p.copy(apiKey = "")) }) { Text("Clear key") }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            val id = "custom_" + UUID.randomUUID().toString().take(6)
            config = config.copy(providers = config.providers + AiProvider(id, "Custom provider", "https://", custom = true))
            expanded = id
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add custom provider") }
        Button(onClick = {
            saving = true
            scope.launch {
                runCatching { AiProviderStore.save(config) }
                    .onSuccess { Toast.makeText(context, "AI providers saved — live within ~1 minute", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, "Save failed: ${it.localizedMessage}. Deploy firestore.rules (admin_secrets).", Toast.LENGTH_LONG).show() }
                saving = false
            }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(6.dp)); Text("Save AI providers")
        }
        Text(
            "Keys are stored in Firestore admin_secrets/ai, readable only by the admin account (firestore.rules). The Worker and the auto-post function read them server-side; users never receive them.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

private fun providerColor(id: String): Color = when {
    id.startsWith("mistral") -> Color(0xFFFF7000)
    id.startsWith("openai") -> Color(0xFF10A37F)
    id.startsWith("gemini") -> Color(0xFF4285F4)
    id.startsWith("groq") -> Color(0xFFF55036)
    id.startsWith("openrouter") -> Color(0xFF6467F2)
    id.startsWith("deepseek") -> Color(0xFF4D6BFE)
    id.startsWith("together") -> Color(0xFF0F6FFF)
    else -> Color(0xFF8E5CE6)
}
