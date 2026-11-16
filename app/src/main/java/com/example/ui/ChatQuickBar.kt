package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Search box that sits inside the top bar on the Chats tab. */
@Composable
fun CompactTopSearch(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(end = 6.dp).height(42.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
            .border(1.dp, Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .45f), MaterialTheme.colorScheme.tertiary.copy(alpha = .2f))), CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) Icon(Icons.Default.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp).clip(CircleShape).clickable { onQuery("") }.padding(2.dp))
    }
}

/**
 * One slim, sideways-scrolling row that replaces the old "Tools & filters" card: chat filters
 * first, then the tools as small glass pills.
 */
@Composable
fun ChatQuickBar(
    filter: String, onFilter: (String) -> Unit,
    discoveryOpen: Boolean, onToggleDiscovery: () -> Unit,
    onSaved: () -> Unit, onCalls: () -> Unit, onSearchChats: () -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple("all", "All", Icons.Default.Forum), Triple("unread", "Unread", Icons.Default.MarkChatUnread),
            Triple("archived", "Archived", Icons.Default.Archive), Triple("locked", "Locked", Icons.Default.Lock)
        ).forEach { (key, label, icon) -> QuickPill(label, icon, selected = filter == key, accent = MaterialTheme.colorScheme.primary) { onFilter(key) } }
        Box(Modifier.height(22.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        QuickPill("Saved", Icons.Default.Star, false, Color(0xFFFFB03A), onSaved)
        QuickPill("Calls", Icons.Default.Call, false, Color(0xFF19C37D), onCalls)
        QuickPill("Find in chats", Icons.Default.ManageSearch, false, Color(0xFF3AA0FF), onSearchChats)
        QuickPill("Find friends", Icons.Default.PersonSearch, discoveryOpen, Color(0xFFFF6FB5), onToggleDiscovery)
    }
}

@Composable
private fun QuickPill(label: String, icon: ImageVector, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), label = "pill_bg")
    val ink by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurface, label = "pill_ink")
    Row(
        Modifier.clip(CircleShape).background(bg).border(1.dp, accent.copy(alpha = if (selected) 0f else .28f), CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Color.White else accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = ink, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}


/** Full-screen writing page for long messages; the text goes back into the normal message box. */
@Composable
fun ExpandedComposerDialog(text: String, title: String, onText: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloseFullscreen, "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                    Text("$words words · ${text.length} characters", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Done", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 9.dp))
            }
            val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            BasicTextField(
                value = text, onValueChange = onText,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, lineHeight = 25.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp).focusRequester(focus),
                decorationBox = { inner -> Box { if (text.isEmpty()) Text("Write your message…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp); inner() } }
            )
        }
    }
}

