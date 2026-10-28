package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Post
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private data class AssistantSource(val title: String, val url: String)
private data class AssistantLine(val role: String, val text: String, val time: Long = System.currentTimeMillis(), val sources: List<AssistantSource> = emptyList())

private val AiViolet = Color(0xFF7C5CFF)
private val AiPink = Color(0xFFFF4FD8)
private val AiCyan = Color(0xFF29D3FF)
private val AiInk = Color(0xFF070616)

/**
 * Convo AI: futuristic assistant screen. Streams answers live (admin switch), can search the web
 * (admin switch + per-user toggle) and cite sources, reads answers aloud, and keeps a private
 * on-device memory. Account actions (stats, user search, post deletion) stay local and confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpenAiSettings: () -> Unit, onOpenUser: (User) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val posts by viewModel.postsState.collectAsState()
    val config by viewModel.flagshipConfig.collectAsState()
    val aiPublicInfo by AiPublicInfo.info.collectAsState()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val prefs = remember(uid) { context.getSharedPreferences("assistant_memory_$uid", Context.MODE_PRIVATE) }
    val initial = remember(uid) {
        runCatching {
            val array = JSONArray(prefs.getString("lines", "[]"))
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                val src = item.optJSONArray("sources")
                AssistantLine(
                    item.optString("role"), item.optString("text"), item.optLong("time"),
                    if (src == null) emptyList() else List(src.length()) { k -> src.getJSONObject(k).let { AssistantSource(it.optString("t"), it.optString("u")) } }
                )
            }
        }.getOrDefault(emptyList())
    }
    var lines by remember(uid) { mutableStateOf(initial) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<User>>(emptyList()) }
    var deleteChoices by remember { mutableStateOf<List<Post>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf<Post?>(null) }
    var showAiSetupGuide by remember { mutableStateOf(false) }
    var showImageGenerator by remember { mutableStateOf(false) }
    var imagePrompt by remember { mutableStateOf("") }
    var imageGenerating by remember { mutableStateOf(false) }
    var generatedImage by remember { mutableStateOf<File?>(null) }
    var imageGenerationError by remember { mutableStateOf("") }
    var webOn by remember(uid) { mutableStateOf(prefs.getBoolean("web_on", true)) }
    val webAllowed = config.aiWebSearchEnabled
    val listState = rememberLazyListState()

    // Text-to-speech for "Listen".
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speakingTime by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status -> if (status == TextToSpeech.SUCCESS) tts = engine }
        onDispose { runCatching { engine?.stop(); engine?.shutdown() } }
    }
    fun speak(line: AssistantLine) {
        val engine = tts ?: return
        if (speakingTime == line.time) { engine.stop(); speakingTime = 0L; return }
        engine.language = if (line.text.any { it in '\u0980'..'\u09FF' }) Locale("bn", "BD") else Locale.getDefault()
        engine.speak(line.text.take(3900), TextToSpeech.QUEUE_FLUSH, null, "ai_${line.time}")
        speakingTime = line.time
    }

    fun persist(updated: List<AssistantLine>) {
        lines = updated.takeLast(80)
        val array = JSONArray()
        lines.forEach { l ->
            array.put(JSONObject().put("role", l.role).put("text", l.text).put("time", l.time).put("sources", JSONArray().apply { l.sources.forEach { put(JSONObject().put("t", it.title).put("u", it.url)) } }))
        }
        prefs.edit().putString("lines", array.toString()).apply()
    }
    fun answer(text: String, sources: List<AssistantSource> = emptyList()) = persist(lines + AssistantLine("assistant", text, sources = sources))
    fun clean(reply: String) = reply.replace(Regex("(?m)^#{1,6}\\s*"), "").replace("**", "").replace(Regex("(?m)^>\\s?"), "").trim()

    LaunchedEffect(lines.size, streamingText != null, thinking) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun askAi(prompt: String) {
        val myPosts = posts.filter { it.senderId == uid }
        val accountContext = "account_context: name=${currentUser?.name}, username=${currentUser?.username}, posts=${myPosts.size}, likes=${myPosts.sumOf { it.reactions.size + it.mediaReactions.values.sumOf { r -> r.size } }}, followers=${currentUser?.followers?.size}, following=${currentUser?.following?.size}, friends=${currentUser?.friends?.size}, joined=${currentUser?.createdAt}, premium=${currentUser?.isPremium}, today=${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
        thinking = true
        scope.launch {
            var sources = emptyList<AssistantSource>()
            var webContext: String? = null
            val releaseQuestion = listOf("what changed", "what's new", "whats new", "release notes", "new update", "কি পরিবর্তন", "নতুন কী", "কি নতুন", "আপডেটে কী", "আপডেটের খবর")
                .any { prompt.lowercase().contains(it) }
            val officialReleaseContext = if (releaseQuestion && config.releaseNotes.isNotBlank()) {
                "Official app release notes for version ${config.versionName} (code ${config.latestVersionCode}). Answer the user's question using these notes only; summarize clearly and do not invent additional changes:\n${config.releaseNotes.take(4000)}"
            } else null
            if (webOn && webAllowed && !releaseQuestion) {
                phase = "Searching the web"
                val (code, j) = runCatching { convoWorkerPost("ai", "/ai/search", JSONObject().put("q", prompt.take(300)).put("max", 6)) }.getOrElse { 0 to JSONObject() }
                val arr = j.optJSONArray("results")
                if (code in 200..299 && arr != null && arr.length() > 0) {
                    sources = List(arr.length()) { k -> arr.getJSONObject(k).let { AssistantSource(it.optString("title"), it.optString("url")) } }
                    webContext = buildString {
                        append("web_search_results for \"").append(prompt.take(200)).append("\" (via ").append(j.optString("provider")).append("). Use them for current facts and cite as [n]:\n")
                        j.optString("answer").takeIf { it.isNotBlank() }?.let { append("Quick answer: ").append(it).append('\n') }
                        for (k in 0 until arr.length()) arr.getJSONObject(k).let { r -> append('[').append(k + 1).append("] ").append(r.optString("title")).append(" - ").append(r.optString("snippet")).append(" (").append(r.optString("url")).append(")\n") }
                    }.take(3500)
                }
            }
            phase = "Thinking"
            val memory = listOfNotNull(accountContext, webContext, officialReleaseContext) + lines.takeLast(18).map { "${it.role}: ${it.text}" }
            if (config.aiStreamingEnabled) {
                streamingText = ""
                viewModel.askAssistantStreaming(prompt, memory, onDelta = { d -> phase = "Writing"; streamingText = (streamingText ?: "") + d }) { error ->
                    val text = streamingText.orEmpty()
                    streamingText = null; thinking = false; phase = ""
                    if (text.isNotBlank()) answer(clean(text), sources) else {
                        val failure = error ?: "I could not generate a response."
                        if (failure.contains("not configured", true) || failure.contains("disabled", true)) showAiSetupGuide = true
                        answer(failure)
                    }
                }
            } else {
                viewModel.askAssistant(prompt, memory) { reply ->
                    thinking = false; phase = ""
                    if (reply.contains("not configured", true) || reply.contains("disabled", true)) showAiSetupGuide = true
                    answer(clean(reply), sources)
                }
            }
        }
    }

    fun submit(text: String = input) {
        val prompt = text.trim(); if (prompt.isBlank() || thinking) return
        input = ""; matches = emptyList(); deleteChoices = emptyList()
        persist(lines + AssistantLine("user", prompt))
        val normalized = prompt.lowercase()
        val myPosts = posts.filter { it.senderId == uid }
        when {
            normalized.contains("who created") || normalized.contains("who made") || normalized.contains("কে তৈরি") || normalized.contains("creator") -> {
                matches = users.filter { it.role == "moderator" || it.name.contains("Rakib", true) }.take(3)
                answer("Convo Chat is created and maintained by ADMIN RAKIB. The verified profile is below: open it to view the profile or start a chat.")
            }
            normalized.contains("delete post") || normalized.contains("পোস্ট ডিলিট") || normalized.contains("পোস্ট মুছ") -> {
                deleteChoices = myPosts.sortedByDescending { it.timestamp }.take(12)
                answer(if (deleteChoices.isEmpty()) "No posts were found on your account." else "For your safety, pick a post below and confirm the deletion.")
            }
            listOf("my account", "আমার অ্যাকাউন্ট", "my stats", "followers", "কবে অ্যাকাউন্ট").any(normalized::contains) -> {
                val likes = myPosts.sumOf { post -> post.reactions.size + post.mediaReactions.values.sumOf { reactions -> reactions.size } }
                val joined = currentUser?.createdAt?.takeIf { it > 0 }?.let { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "unknown"
                answer("Account report\n- Posts: ${myPosts.size}\n- Total likes/reactions: $likes\n- Followers: ${currentUser?.followers?.size ?: 0}\n- Friends: ${currentUser?.friends?.size ?: 0}\n- Account created: $joined")
            }
            normalized.startsWith("find user") || normalized.startsWith("search user") || normalized.contains("ইউজার খুঁজ") -> {
                val stopWords = setOf("find", "search", "user", "please", "show", "me", "ইউজার", "খুঁজে", "খুঁজ", "বের", "করো", "দাও", "নামে", "নাম", "কে", "আছে")
                val tokens = normalized.split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.length >= 2 && it !in stopWords }
                matches = users.map { user ->
                    val haystack = "${user.name} ${user.username}".lowercase()
                    user to tokens.count { token -> haystack.contains(token) }
                }.filter { it.second > 0 }.sortedWith(compareByDescending<Pair<User, Int>> { it.second }.thenBy { it.first.name }).map { it.first }.take(20)
                answer(if (matches.isEmpty()) "No users matched. Try a few more letters of the name." else "Found ${matches.size} matching people. Open a profile or start a chat below.")
            }
            else -> askAi(prompt)
        }
    }

    val infinite = rememberInfiniteTransition(label = "ai_bg")
    val drift by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")

    Box(Modifier.fillMaxSize().background(AiInk)) {
        // Living background: drifting aurora light + a faint perspective grid.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (w > 1f && h > 1f) {
                val r = (w * .75f).coerceAtLeast(1f)
                val a = Offset(w * (.1f + .5f * drift), h * .12f)
                val b = Offset(w * (.95f - .6f * drift), h * .55f)
                val c = Offset(w * .5f, h * (.95f - .15f * drift))
                drawCircle(Brush.radialGradient(listOf(AiViolet.copy(alpha = .35f), Color.Transparent), a, r), r, a)
                drawCircle(Brush.radialGradient(listOf(AiPink.copy(alpha = .22f), Color.Transparent), b, r * .8f), r * .8f, b)
                drawCircle(Brush.radialGradient(listOf(AiCyan.copy(alpha = .18f), Color.Transparent), c, r * .7f), r * .7f, c)
                val step = 46.dp.toPx()
                var x = 0f
                while (x < w) { drawLine(Color.White.copy(alpha = .03f), Offset(x, 0f), Offset(x, h), 1f); x += step }
                var y = (drift * step) % step
                while (y < h) { drawLine(Color.White.copy(alpha = .03f), Offset(0f, y), Offset(w, y), 1f); y += step }
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- Header ---------------------------------------------------------------------
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                AiOrb(size = 44, active = thinking)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.aiDisplayName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        !config.assistantEnabled -> "Paused by admin"
                        thinking -> phase.ifBlank { "Thinking" } + "..."
                        else -> "Online" + aiPublicInfo.model.takeIf { it.isNotBlank() }?.let { "  ·  $it" }.orEmpty()
                    }
                    Text(status, color = if (thinking) AiCyan else Color.White.copy(alpha = .6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { prefs.edit().remove("lines").apply(); lines = emptyList(); matches = emptyList(); deleteChoices = emptyList() }) {
                    Icon(Icons.Default.DeleteSweep, "Clear conversation", tint = Color.White.copy(alpha = .75f))
                }
            }
            // ---- Capability chips --------------------------------------------------------------
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (webAllowed) AiChip(Icons.Default.TravelExplore, if (webOn) "Web search on" else "Web search off", webOn) { webOn = !webOn; prefs.edit().putBoolean("web_on", webOn).apply() }
                if (config.aiStreamingEnabled) AiChip(Icons.Default.Bolt, "Live answers", true) {}
                if (config.aiHumanStyle) AiChip(Icons.Default.RecordVoiceOver, "Human style", true) {}
                AiChip(Icons.Default.Lock, "Private memory", true) {}
                if (config.assistantEnabled) AiChip(Icons.Default.Image, "Create image", true) { showImageGenerator = true }
                AiChip(Icons.Default.Settings, "AI setup guide", false) { showAiSetupGuide = true }
            }
            Spacer(Modifier.height(6.dp))
            // ---- Conversation ----------------------------------------------------------------
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (lines.isEmpty()) item { AiWelcome(config.aiDisplayName, config.assistantEnabled, config.smartReplyEnabled, config.taskExtractionEnabled, webAllowed) { submit(it) } }
                items(lines) { line ->
                    if (line.role == "user") UserBubble(line.text)
                    else AssistantBubble(
                        line = line,
                        speaking = speakingTime == line.time,
                        onCopy = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Convo AI", line.text))
                            android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, line.text), "Share")) },
                        onSpeak = { speak(line) },
                        onOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                    )
                }
                streamingText?.let { live -> if (live.isNotEmpty()) item(key = "live") { AssistantBubble(AssistantLine("assistant", live + " \u258D"), false, {}, {}, {}, {}) } }
                if (thinking && streamingText.isNullOrEmpty()) item(key = "thinking") { AiThinking(phase.ifBlank { "Thinking" }) }
                items(matches, key = { "u_" + it.uid }) { user ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).clickable { onOpenUser(user) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(user.profileImageUrl.ifBlank { null }, user.name, Modifier.size(44.dp).clip(CircleShape).background(AiViolet.copy(alpha = .4f)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("@${user.username}", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Chat, "Open chat", tint = AiCyan)
                    }
                }
                items(deleteChoices, key = { "p_" + it.id }) { post ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(post.title.ifBlank { post.text.ifBlank { "Media post" } }, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${post.reactions.size} reactions", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                        }
                        IconButton(onClick = { confirmDelete = post }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF6B78)) }
                    }
                }
            }
            // ---- Composer ----------------------------------------------------------------------
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = .08f))
                    .border(BorderStroke(1.dp, Brush.linearGradient(listOf(AiCyan.copy(alpha = .6f), AiPink.copy(alpha = .5f), AiViolet.copy(alpha = .6f)))), RoundedCornerShape(28.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (input.isEmpty()) Text(if (config.assistantEnabled) "Ask anything" + if (webOn && webAllowed) " - I can search the web" else "" else "Assistant is paused", color = Color.White.copy(alpha = .45f))
                    BasicTextField(
                        value = input, onValueChange = { input = it.take(4000) }, enabled = config.assistantEnabled && !thinking,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), cursorBrush = SolidColor(AiCyan),
                        maxLines = 5, modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(6.dp))
                val canSend = input.isNotBlank() && !thinking && config.assistantEnabled
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .background(if (canSend) Brush.linearGradient(listOf(AiViolet, AiPink)) else Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .12f))))
                        .clickable(enabled = canSend) { submit() },
                    contentAlignment = Alignment.Center
                ) {
                    if (thinking) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.ArrowUpward, "Send", tint = Color.White)
                }
            }
        }
    }

    if (showAiSetupGuide) {
        AlertDialog(
            onDismissRequest = { showAiSetupGuide = false },
            icon = { Icon(Icons.Default.TipsAndUpdates, null, tint = AiCyan) },
            title = { Text("AI setup guide", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. In Privacy controls, turn on Allow AI processing if you want to use the assistant. This controls your own consent; it does not send anything until you submit a prompt.")
                    Text("2. If AI is paused for everyone or the provider is not configured, only the admin can turn on the assistant and save a provider key. Ask the admin to review Admin tools → AI providers & API keys.")
                    Text("3. Return here and ask your question again. Friend requests and destructive actions must remain user-confirmed.")
                    Text("Your OVH image token, when enabled, is held in admin-only server configuration and is never included in this app request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { showAiSetupGuide = false; onOpenAiSettings() }) { Text("Open highlighted privacy setting") }
            },
            dismissButton = { TextButton(onClick = { showAiSetupGuide = false }) { Text("Close") } }
        )
    }

    if (showImageGenerator) {
        AlertDialog(
            onDismissRequest = { if (!imageGenerating) showImageGenerator = false },
            icon = { Icon(Icons.Default.Image, null, tint = AiCyan) },
            title = { Text("Create an image", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Describe the image. The request is sent through the authenticated Convo Worker to OVHcloud Stable Diffusion XL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { imagePrompt = it.take(1200) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Image description") },
                        minLines = 3,
                        maxLines = 5
                    )
                    if (imageGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())
                    generatedImage?.let { file ->
                        AsyncImage(model = file, contentDescription = "AI generated image", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(18.dp)))
                    }
                    if (imageGenerationError.isNotBlank()) Text(imageGenerationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = imagePrompt.isNotBlank() && !imageGenerating,
                    onClick = {
                        imageGenerating = true
                        imageGenerationError = ""
                        generatedImage = null
                        viewModel.generateAssistantImage(imagePrompt) { file, error ->
                            imageGenerating = false
                            generatedImage = file
                            imageGenerationError = error
                        }
                    }
                ) {
                    if (imageGenerating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (imageGenerating) "Generating" else "Generate")
                }
            },
            dismissButton = { TextButton(onClick = { if (!imageGenerating) showImageGenerator = false }) { Text("Close") } }
        )
    }

    confirmDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, title = { Text("Delete this post?") },
            text = { Text("This action cannot be undone. The Assistant never performs a destructive action without your confirmation.") },
            confirmButton = { Button(onClick = { viewModel.deletePost(post.id); deleteChoices = deleteChoices.filterNot { it.id == post.id }; confirmDelete = null; answer("The post was deleted.") }) { Text("Confirm delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

/** Animated AI orb: rotating gradient ring around a glowing core; spins faster while working. */
@Composable
private fun AiOrb(size: Int, active: Boolean) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 1400 else 6000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(.88f, 1.06f, infiniteRepeatable(tween(if (active) 520 else 1800), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.clip(CircleShape).background(Brush.sweepGradient(listOf(AiCyan, AiViolet, AiPink, AiCyan))))
        Box(Modifier.fillMaxSize(.82f).clip(CircleShape).background(AiInk))
        Box(Modifier.fillMaxSize(.56f).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, AiCyan, AiViolet))))
    }
}

@Composable
private fun AiChip(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(if (on) AiViolet.copy(alpha = .28f) else Color.White.copy(alpha = .06f))
            .border(BorderStroke(1.dp, if (on) AiCyan.copy(alpha = .55f) else Color.White.copy(alpha = .14f)), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) AiCyan else Color.White.copy(alpha = .6f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = if (on) .95f else .6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AiWelcome(name: String, enabled: Boolean, smartReplies: Boolean, tasks: Boolean, web: Boolean, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AiOrb(size = 96, active = false)
        Spacer(Modifier.height(18.dp))
        Text("Hi, I'm $name", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text("Ask in Bangla or English. I can explain, write, plan" + if (web) " and look things up online." else ".", color = Color.White.copy(alpha = .65f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp))
        if (!enabled) Text("The administrator has paused the Assistant.", color = Color(0xFFFF6B78), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        val ideas = buildList {
            if (web) add(Triple(Icons.Default.Newspaper, "Today's news", "What are the top news headlines today?"))
            add(Triple(Icons.Default.Insights, "My account", "my account stats"))
            add(Triple(Icons.Default.EditNote, "Write a caption", "Write a short, catchy caption for a photo of a rainy evening in Dhaka."))
            if (smartReplies) add(Triple(Icons.Default.Reply, "Smart replies", "Suggest three short, friendly replies to: 'Are you free tonight?'"))
            if (tasks) add(Triple(Icons.Default.TaskAlt, "Plan my day", "Make a simple plan for my day with 5 tasks and times."))
            add(Triple(Icons.Default.Translate, "Translate", "Translate to Bangla: 'Thank you for your help, see you tomorrow.'"))
            if (web) add(Triple(Icons.Default.TravelExplore, "Look it up", "Search the web: best free photo editing apps for Android"))
            add(Triple(Icons.Default.Lightbulb, "Explain simply", "Explain how a VPN works in simple words."))
        }
        ideas.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, title, prompt) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .06f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = .10f)), RoundedCornerShape(20.dp))
                            .clickable(enabled = enabled) { onPick(prompt) }.padding(14.dp)
                    ) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(AiViolet, AiPink))), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(prompt, color = Color.White.copy(alpha = .5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(Brush.linearGradient(listOf(AiViolet, Color(0xFFB24CFF), AiPink))).padding(horizontal = 14.dp, vertical = 10.dp)
        ) { SelectionContainer { Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp) } }
    }
}

@Composable
private fun AssistantBubble(line: AssistantLine, speaking: Boolean, onCopy: () -> Unit, onShare: () -> Unit, onSpeak: () -> Unit, onOpen: (String) -> Unit) {
    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 6 }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AiOrb(size = 28, active = false)
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f, fill = false).widthIn(max = 330.dp).clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                    .background(Color.White.copy(alpha = .08f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = .10f)), RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                    .padding(14.dp)
            ) {
                SelectionContainer { Text(aiFormatted(line.text), color = Color.White.copy(alpha = .92f), fontSize = 15.sp, lineHeight = 22.sp) }
                if (line.sources.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("SOURCES", color = AiCyan, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        line.sources.forEachIndexed { i, s ->
                            Row(
                                Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .07f)).clickable { onOpen(s.url) }.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}", color = AiInk, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(CircleShape).background(AiCyan).padding(horizontal = 5.dp))
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(s.title.ifBlank { s.url }, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(runCatching { Uri.parse(s.url).host.orEmpty().removePrefix("www.") }.getOrDefault(""), color = Color.White.copy(alpha = .5f), fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (!line.text.endsWith("\u258D")) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    AiAction(Icons.Default.ContentCopy, "Copy", onCopy)
                    AiAction(if (speaking) Icons.Default.StopCircle else Icons.Default.VolumeUp, if (speaking) "Stop" else "Listen", onSpeak)
                    AiAction(Icons.Default.Share, "Share", onShare)
                }
            }
        }
    }
}

@Composable
private fun AiAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
    }
}

/** Light formatting: bullet lines, inline `code`, and [n] citation markers. */
private fun aiFormatted(text: String) = buildAnnotatedString {
    val citation = Regex("\\[(\\d{1,2})]")
    text.lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        val bullet = raw.trimStart().let { it.startsWith("- ") || it.startsWith("* ") || it.startsWith("• ") }
        val line = if (bullet) "  •  " + raw.trimStart().drop(2) else raw
        var i = 0
        val parts = Regex("`[^`]+`|\\[\\d{1,2}]").findAll(line).toList()
        parts.forEach { m ->
            append(line.substring(i, m.range.first))
            if (m.value.startsWith("`")) withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = .12f), color = AiCyan)) { append(m.value.trim('`')) }
            else if (citation.matches(m.value)) withStyle(SpanStyle(color = AiCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)) { append(m.value) }
            i = m.range.last + 1
        }
        append(line.substring(i))
    }
}

@Composable
private fun AiThinking(phase: String) {
    val t = rememberInfiniteTransition(label = "think")
    val shimmer by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer")
    Row(verticalAlignment = Alignment.CenterVertically) {
        AiOrb(size = 28, active = true)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .07f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "$phase...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .35f), AiCyan, Color.White.copy(alpha = .35f)), start = Offset(shimmer * 600f - 200f, 0f), end = Offset(shimmer * 600f + 100f, 0f))
                ),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
