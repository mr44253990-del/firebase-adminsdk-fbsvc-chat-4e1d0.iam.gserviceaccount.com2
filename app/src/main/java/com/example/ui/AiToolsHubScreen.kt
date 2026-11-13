package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AiTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val soon: Boolean = false
)

/**
 * AiToolsHubScreen — new AI pages hub. A launchpad of AI tools that grows with releases.
 * Each card opens the matching feature (or shows a "coming soon" state for the next drop).
 */
@Composable
fun AiToolsHubScreen(onBack: () -> Unit, onOpenTool: (String) -> Unit, modifier: Modifier = Modifier) {
    val tools = listOf(
        AiTool("AI Assistant", "Chat, search & memory", Icons.Default.Psychology, listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8))),
        AiTool("Image Creator", "Generate images from text", Icons.Default.Image, listOf(Color(0xFF29D3FF), Color(0xFF7C5CFF))),
        AiTool("Smart Reply", "One-tap context replies", Icons.Default.Reply, listOf(Color(0xFF12B886), Color(0xFF3BC9DB))),
        AiTool("Summarize Chat", "TL;DR of any conversation", Icons.Default.Summarize, listOf(Color(0xFFFFB020), Color(0xFFFF6B6B))),
        AiTool("Translator", "Translate messages live", Icons.Default.Translate, listOf(Color(0xFF2E5BFF), Color(0xFF00E5FF))),
        AiTool("Voice to Text", "Transcribe voice notes", Icons.Default.RecordVoiceOver, listOf(Color(0xFFFF4FD8), Color(0xFFFFB020))),
        AiTool("Auto Post", "AI-written social posts", Icons.Default.CloudSync, listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)), soon = true),
        AiTool("Mood Coach", "Daily check-in & tips", Icons.Default.AutoAwesome, listOf(Color(0xFF0FB5C9), Color(0xFF2F80ED)), soon = true)
    )

    Column(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF070616), Color(0xFF100B2A), Color(0xFF070616)), start = Offset.Zero, end = Offset(900f, 1500f)))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("AI Studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Text("${tools.count { !it.soon }} live · ${tools.count { it.soon }} coming soon", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools, key = { it.title }) { tool ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(tool.colors, start = Offset.Zero, end = Offset(300f, 300f)))
                        .border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(22.dp))
                        .springPress(enabled = !tool.soon) { if (!tool.soon) onOpenTool(tool.title) }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .22f)).padding(8.dp)) {
                            Icon(tool.icon, null, tint = Color.White, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.weight(1f))
                        if (tool.soon) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("soon", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(tool.subtitle, color = Color.White.copy(alpha = .85f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                }
            }
        }
    }
}
