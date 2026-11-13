package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FlagshipConfig

/** Opens [rawUrl] in the phone's browser; if no browser answers, the link is copied instead. */
fun openInBrowser(context: Context, rawUrl: String) {
    val url = rawUrl.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(view); true }
        .recoverCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        .getOrDefault(false)
    if (!opened) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Convo Chat Web", url))
        Toast.makeText(context, "No browser found — the link was copied: $url", Toast.LENGTH_LONG).show()
    }
}

/**
 * "Convo Chat Web" card (v5.4). Shows the web address the admin configured, the benefits, and a
 * button that really opens the browser (the old banner only showed a toast).
 */
@Composable
fun WebPromoCard(config: FlagshipConfig, bangla: Boolean, modifier: Modifier = Modifier, onOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val url = config.webVersionUrl.trim().ifBlank { "https://convo-chat-companion.pages.dev" }
    val benefits = remember(config.webPromoBenefits) { config.webPromoBenefits.lines().map { it.trim() }.filter { it.isNotBlank() }.take(5) }
    val sheen by rememberInfiniteTransition(label = "web_promo").animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "sheen")
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF121A5C), Color(0xFF3949AB), Color(0xFF0F7F94)), start = Offset(sheen * 500f, 0f), end = Offset(sheen * 500f + 700f, 400f)))
            .border(1.4.dp, Brush.linearGradient(listOf(Color(0xFF7986CB), Color(0xFF36D8E6))), RoundedCornerShape(22.dp))
            .clickable { openInBrowser(context, url); onOpened() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LaptopChromebook, null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(config.webPromoTitle.ifBlank { "Convo Chat Web" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (bangla) "এখন পিসি এবং ব্রাউজারেও চালু করুন" else "Now use on PC and browser", color = Color(0xFFC5CAE9), fontSize = 11.5.sp)
            }
        }
        benefits.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF7EF0B8), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(line, color = Color.White.copy(alpha = .92f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .28f)).padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Language, null, tint = Color(0xFF9FE8FF), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(url.removePrefix("https://").removePrefix("http://"), color = Color(0xFF9FE8FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Convo Chat Web", url))
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ContentCopy, "Copy link", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(Color.White).clickable { openInBrowser(context, url); onOpened() }.padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (bangla) "ব্রাউজারে খুলুন" else "Open in browser", color = Color(0xFF1A237E), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.OpenInNew, null, tint = Color(0xFF1A237E), modifier = Modifier.size(16.dp))
        }
    }
}

/** Admin page for the web card: on/off, where it shows, the address and the benefit lines. */
@Composable
fun AdminWebPromoPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val config by viewModel.flagshipConfig.collectAsState()
    var enabled by remember(config.updatedAt) { mutableStateOf(config.webPromoEnabled) }
    var onHome by remember(config.updatedAt) { mutableStateOf(config.webPromoOnHome) }
    var url by remember(config.updatedAt) { mutableStateOf(config.webVersionUrl) }
    var title by remember(config.updatedAt) { mutableStateOf(config.webPromoTitle) }
    var benefits by remember(config.updatedAt) { mutableStateOf(config.webPromoBenefits) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Show the web card", fontWeight = FontWeight.Bold); Text("Off = hidden from every user", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Also show on the Home feed", fontWeight = FontWeight.Bold); Text("Otherwise only in the profile-picture menu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = onHome && enabled, enabled = enabled, onCheckedChange = { onHome = it })
        }
        OutlinedTextField(url, { url = it.trim().take(200) }, label = { Text("Web version address (URL)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it.take(50) }, label = { Text("Card title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(benefits, { benefits = it.take(400) }, label = { Text("Benefits (one per line, up to 5)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        WebPromoCard(config.copy(webVersionUrl = url, webPromoTitle = title, webPromoBenefits = benefits), bangla = false)
        Button(onClick = {
            val clean = url.trim().let { if (it.isBlank()) "https://convo-chat-companion.pages.dev" else if (it.startsWith("http")) it else "https://$it" }
            viewModel.updateFlagshipFields(mapOf("webPromoEnabled" to enabled, "webPromoOnHome" to (onHome && enabled), "webVersionUrl" to clean, "webPromoTitle" to title.trim().ifBlank { "Convo Chat Web" }, "webPromoBenefits" to benefits.trim())) { ok ->
                Toast.makeText(context, if (ok) "Saved — everyone sees the change at once" else "Could not save", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
    }
}
