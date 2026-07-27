package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Post
import com.example.data.User
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

private data class AssistantLine(val role: String, val text: String, val time: Long = System.currentTimeMillis())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpenUser: (User) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val posts by viewModel.postsState.collectAsState()
    val config by viewModel.flagshipConfig.collectAsState()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val prefs = remember(uid) { context.getSharedPreferences("assistant_memory_$uid", Context.MODE_PRIVATE) }
    val initial = remember(uid) {
        runCatching {
            val array = JSONArray(prefs.getString("lines", "[]"))
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                AssistantLine(item.optString("role"), item.optString("text"), item.optLong("time"))
            }
        }.getOrDefault(emptyList())
    }
    var lines by remember(uid) { mutableStateOf(initial) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<User>>(emptyList()) }
    var deleteChoices by remember { mutableStateOf<List<Post>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf<Post?>(null) }

    fun persist(updated: List<AssistantLine>) {
        lines = updated.takeLast(80)
        val array = JSONArray()
        lines.forEach { array.put(org.json.JSONObject().put("role", it.role).put("text", it.text).put("time", it.time)) }
        prefs.edit().putString("lines", array.toString()).apply()
    }
    fun answer(text: String) = persist(lines + AssistantLine("assistant", text))

    fun submit() {
        val prompt = input.trim(); if (prompt.isBlank() || thinking) return
        input = ""; matches = emptyList(); deleteChoices = emptyList()
        persist(lines + AssistantLine("user", prompt))
        val normalized = prompt.lowercase()
        val myPosts = posts.filter { it.senderId == uid }
        when {
            listOf("my account", "আমার অ্যাকাউন্ট", "আমার পোস্ট", "total like", "মোট লাইক", "কবে অ্যাকাউন্ট").any(normalized::contains) -> {
                val likes = myPosts.sumOf { post -> post.reactions.size + post.mediaReactions.values.sumOf { reactions -> reactions.size } }
                val joined = currentUser?.createdAt?.takeIf { it > 0 }?.let { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "unknown"
                answer("📊 আপনার অ্যাকাউন্ট রিপোর্ট\n• পোস্ট: ${myPosts.size}\n• মোট লাইক/রিঅ্যাকশন: $likes\n• Followers: ${currentUser?.followers?.size ?: 0}\n• Friends: ${currentUser?.friends?.size ?: 0}\n• অ্যাকাউন্ট খোলা: $joined")
            }
            normalized.contains("delete post") || normalized.contains("পোস্ট ডিলিট") || normalized.contains("পোস্ট মুছ") -> {
                deleteChoices = myPosts.sortedByDescending { it.timestamp }.take(12)
                answer(if (deleteChoices.isEmpty()) "আপনার কোনো পোস্ট পাওয়া যায়নি।" else "নিরাপত্তার জন্য নিচের তালিকা থেকে পোস্ট নির্বাচন করে Confirm করুন।")
            }
            normalized.contains("find ") || normalized.contains("search ") || normalized.contains("খুঁজ") || normalized.contains("ইউজার") -> {
                val query = normalized.replace(Regex("find|search|user|ইউজার|খুঁজে|খুঁজ|বের|করো|দাও"), " ").trim()
                matches = users.filter { query.isNotBlank() && (it.name.contains(query, true) || it.username.contains(query, true)) }.take(15)
                answer(if (matches.isEmpty()) "‘$query’ নামে কোনো ব্যবহারকারী পাইনি।" else "${matches.size} জন ব্যবহারকারী পেয়েছি। প্রোফাইল বা Message খুলতে নিচে ট্যাপ করুন।")
            }
            else -> {
                thinking = true
                viewModel.askAssistant(prompt, lines.takeLast(20).map { "${it.role}: ${it.text}" }) { reply ->
                    thinking = false; answer(reply)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(config.aiDisplayName, fontWeight = FontWeight.ExtraBold); Text(config.aiModel, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { prefs.edit().clear().apply(); lines = emptyList() }) { Icon(Icons.Default.DeleteSweep, "Clear assistant memory") } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it.take(4000) }, enabled = config.assistantEnabled && !thinking, placeholder = { Text("Ask FireChat Assistant…") }, modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(24.dp))
                    Spacer(Modifier.width(7.dp)); FilledIconButton(onClick = ::submit, enabled = input.isNotBlank() && !thinking) { Icon(Icons.Default.AutoAwesome, "Ask") }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C5CFC), Color(0xFF19D5C5), Color(0xFFFF65B3)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White) }
                            Spacer(Modifier.width(10.dp)); Text("আমি কী করতে পারি", fontWeight = FontWeight.ExtraBold)
                        }
                        Text("📊 Account stats ও মোট likes  •  🔎 User search  •  🗑️ নিজের post delete (confirmation সহ)  •  💬 FireChat help  •  🧠 এই ডিভাইসে chat memory", fontSize = 12.sp)
                        if (!config.assistantEnabled) Text("Admin বর্তমানে Assistant বন্ধ রেখেছেন।", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (lines.isEmpty()) item { Text("উদাহরণ: “আমার অ্যাকাউন্টে কয়টি পোস্ট এবং মোট লাইক কত?”", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(lines) { line ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (line.role == "user") Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(color = if (line.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                        Text(line.text, Modifier.padding(12.dp))
                    }
                }
            }
            if (thinking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(matches, key = { it.uid }) { user ->
                ListItem(
                    headlineContent = { Text(user.name, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("@${user.username}") },
                    leadingContent = { AsyncImage(user.profileImageUrl.ifBlank { null }, user.name, Modifier.size(46.dp).clip(CircleShape)) },
                    trailingContent = { Icon(Icons.Default.Chat, "Open chat") },
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onOpenUser(user) }
                )
            }
            items(deleteChoices, key = { it.id }) { post ->
                ListItem(
                    headlineContent = { Text(post.title.ifBlank { post.text.ifBlank { "Media post" } }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${post.reactions.size} reactions") },
                    trailingContent = { IconButton(onClick = { confirmDelete = post }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) } }
                )
            }
        }
    }

    confirmDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, title = { Text("পোস্টটি ডিলিট করবেন?") },
            text = { Text("এই কাজটি ফিরিয়ে আনা যাবে না। AI কখনো confirmation ছাড়া destructive action করবে না।") },
            confirmButton = { Button(onClick = { viewModel.deletePost(post.id); deleteChoices = deleteChoices.filterNot { it.id == post.id }; confirmDelete = null; answer("✅ পোস্টটি ডিলিট করা হয়েছে।") }) { Text("Confirm delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}
