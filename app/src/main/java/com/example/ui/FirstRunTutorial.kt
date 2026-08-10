package com.example.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

private data class TourPage(val icon: ImageVector, val title: String, val body: String)

@Composable
fun FirstRunTutorial(onFinish: () -> Unit) {
    val context = LocalContext.current
    val pages = remember { listOf(
        TourPage(Icons.Default.Home, "Home & Stories", "See posts, stories, smart suggestions and your creator analytics."),
        TourPage(Icons.Default.Chat, "Chats", "Open conversations, message requests and Convo AI from the Chats home."),
        TourPage(Icons.Default.Add, "Create Hub", "Publish a photo, video, Reel, story or a polished AI-assisted post."),
        TourPage(Icons.Default.SmartDisplay, "Reels", "Swipe through full-screen videos, react, comment, share and open creators."),
        TourPage(Icons.Default.Tune, "Liquid Control Center", "Manage themes, privacy, devices, Premium, reports and account security.")
    ) }
    var page by remember { mutableIntStateOf(0) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { tts = TextToSpeech(context) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.US }; onDispose { tts?.stop(); tts?.shutdown() } }
    val pulse by rememberInfiniteTransition(label="tour").animateFloat(.92f,1.08f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="pulse")
    val item = pages[page]
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.82f)), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF15162A))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size((82*pulse).dp).background(MaterialTheme.colorScheme.primary.copy(.24f), CircleShape), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp)) }
                    Spacer(Modifier.height(18.dp)); Text(item.title, color=Color.White, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Black)
                    Spacer(Modifier.height(8.dp)); Text(item.body, color=Color.White.copy(.76f), textAlign=TextAlign.Center)
                    Spacer(Modifier.height(18.dp)); Row { pages.indices.forEach { i -> Box(Modifier.padding(3.dp).size(if(i==page) 22.dp else 7.dp,7.dp).background(if(i==page) MaterialTheme.colorScheme.primary else Color.White.copy(.25f),CircleShape)) } }
                    Spacer(Modifier.height(18.dp)); OutlinedButton(onClick={ tts?.speak("${item.title}. ${item.body}",TextToSpeech.QUEUE_FLUSH,null,"tour") },modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.VolumeUp,null); Spacer(Modifier.width(7.dp)); Text("Speak") }
                    Button(onClick={ if(page==pages.lastIndex) onFinish() else page++ },modifier=Modifier.fillMaxWidth()) { Text(if(page==pages.lastIndex) "Start Convo" else "Next") }
                    TextButton(onClick=onFinish) { Text("Skip tutorial") }
                }
            }
        }
    }
}
