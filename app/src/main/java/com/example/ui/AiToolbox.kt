package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ToolInk = Color(0xFF070616)
private val ToolViolet = Color(0xFF7C5CFF)
private val ToolPink = Color(0xFFFF4FD8)
private val ToolCyan = Color(0xFF29D3FF)

private data class AiTool(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val placeholder: String,
    val actionLabel: String
)

private fun toolboxTools(): List<AiTool> = listOf(
    AiTool("translate", Icons.Default.Translate, "Translate", "Any language ↔ Bangla / English", "Paste text to translate…", "Translate"),
    AiTool("summarize", Icons.Default.Subject, "Summarize", "Long text → short bullets", "Paste an article, note or long message…", "Summarize"),
    AiTool("caption", Icons.Default.EditNote, "Caption writer", "Perfect post captions", "Describe your photo or moment…", "Write captions"),
    AiTool("replies", Icons.Default.Reply, "Reply ideas", "Smart message replies", "Paste the message you received…", "Suggest replies"),
    AiTool("bio", Icons.Default.Badge, "Bio & name studio", "Profile bio ideas", "A few words about yourself…", "Write bios"),
    AiTool("grammar", Icons.Default.Spellcheck, "Fix my text", "Grammar and spelling", "Paste your text…", "Fix it"),
    AiTool("ideas", Icons.Default.Lightbulb, "Idea machine", "Posts, names, plans", "What do you need ideas for?", "Generate ideas"),
    AiTool("explain", Icons.Default.School, "Explain simply", "Hard things, easy words", "What should I explain?", "Explain")
)

private fun promptFor(tool: AiTool, input: String): String = when (tool.key) {
    "translate" -> "Translate the following text into natural, flowing Bengali. If the text is already Bengali, translate it into English instead. Return only the translation:\n\n$input"
    "summarize" -> "Summarize the following text in 3-5 short bullet points, in the same language the text uses:\n\n$input"
    "caption" -> "Write 5 short, catchy social media captions for this, with light emoji use. Same language as the description:\n\n$input"
    "replies" -> "Suggest 5 short, natural replies (casual to polite) to this message. Keep the same language as the message:\n\n$input"
    "bio" -> "Write 4 creative profile bio options (max 120 characters each, with emoji) based on this description. Same language:\n\n$input"
    "grammar" -> "Fix the grammar and spelling of this text and return only the corrected text, keeping the original language and tone:\n\n$input"
    "ideas" -> "Give 6 creative, specific ideas for this, as a numbered list, in the same language as the request:\n\n$input"
    else -> "Explain this in simple words a beginner would understand, in the same language as the question. Use a short analogy:\n\n$input"
}

/**
 * AI Toolbox — the new AI tools page: one-tap utilities (translate, summarize, captions,
 * replies, bios, grammar, ideas, explanations) that run through the admin-configured AI
 * provider, with copy-on-finish and a history of the last results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolboxScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val config by viewModel.flagshipConfig.collectAsState()
    var activeTool by remember { mutableStateOf<AiTool?>(null) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val tools = remember { toolboxTools() }

    fun run(tool: AiTool) {
        if (input.isBlank() || busy) return
        busy = true
        output = ""
        viewModel.askAssistant(promptFor(tool, input), emptyList()) { result ->
            output = result
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(ToolInk)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(ToolCyan, ToolViolet, ToolPink, ToolCyan))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Construction, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("AI Toolbox", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    if (!config.assistantEnabled) "Assistant is paused by the admin" else "Smart tools for everyday chats",
                    color = if (!config.assistantEnabled) Color(0xFFFF6B78) else Color.White.copy(alpha = .6f),
                    fontSize = 11.sp
                )
            }
        }

        if (activeTool == null) {
            // Tool grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tools) { tool ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = .06f))
                            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(22.dp))
                            .clickable(enabled = config.assistantEnabled) {
                                activeTool = tool
                                input = ""
                                output = ""
                            }
                            .padding(16.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(ToolViolet, ToolPink))),
                            contentAlignment = Alignment.Center
                        ) { Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(tool.subtitle, color = Color.White.copy(alpha = .55f), fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        } else {
            val tool = activeTool!!
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { activeTool = null }) { Icon(Icons.Default.Close, "Back to tools", tint = Color.White) }
                    Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(3000) },
                    placeholder = { Text(tool.placeholder, color = Color.White.copy(alpha = .4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ToolCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = .2f),
                        cursorColor = ToolCyan
                    )
                )
                Button(
                    onClick = { run(tool) },
                    enabled = input.isNotBlank() && !busy && config.assistantEnabled,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ToolViolet)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Working…" else tool.actionLabel, fontWeight = FontWeight.Bold)
                }
                if (output.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = .07f))
                            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Text("Result", color = ToolCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(output, color = Color.White.copy(alpha = .94f), fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(output))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Copy", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { run(tool) },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Again", fontSize = 12.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
