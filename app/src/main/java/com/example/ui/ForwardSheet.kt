package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Message
import com.example.data.User

/**
 * Forward sheet: friends only, live search, multi-select (up to 10) and a preview of what is
 * being forwarded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardMessageSheet(
    message: Message,
    me: User?,
    users: List<User>,
    onDismiss: () -> Unit,
    onSend: (List<User>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val myUid = me?.uid.orEmpty()
    val friends = remember(users, me) {
        users.filter { u ->
            u.uid != myUid && (me?.friends?.contains(u.uid) == true || u.friends.contains(myUid))
        }.distinctBy { it.uid }.sortedBy { it.name.lowercase() }
    }
    val shown = remember(friends, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) friends else friends.filter { it.name.lowercase().contains(q) || it.username.lowercase().contains(q) }
    }
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, MaterialTheme.colorScheme.tertiary))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Forward", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(tr("Only your friends are shown"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Preview of the forwarded item.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val image = message.imageUrl?.takeIf { it.isNotBlank() }
                    if (image != null) {
                        AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        when {
                            message.text.isNotBlank() -> message.text
                            image != null -> "📷 Photo"
                            !message.voiceUrl.isNullOrBlank() -> "🎤 Voice message"
                            !message.fileName.isNullOrBlank() -> "📎 ${message.fileName}"
                            else -> "Attachment"
                        },
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                placeholder = { Text(tr("Search friends")) }
            )
            AnimatedVisibility(selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.forEach { uid ->
                        val u = friends.firstOrNull { it.uid == uid } ?: return@forEach
                        InputChip(
                            selected = true,
                            onClick = { selected.remove(uid) },
                            label = { Text(u.name.substringBefore(' '), maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (friends.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PeopleOutline, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("No friends yet", fontWeight = FontWeight.Bold)
                    Text("Add friends to forward messages to them.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    if (shown.isEmpty()) item {
                        Text("No friend matches \"$query\"", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(shown, key = { it.uid }) { u ->
                        val isSel = u.uid in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) accent.copy(alpha = .10f) else Color.Transparent)
                                .clickable {
                                    if (isSel) selected.remove(u.uid) else if (selected.size < 10) selected.add(u.uid)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                AsyncImage(
                                    u.profileImageUrl.ifBlank { null }, u.name, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = .15f))
                                        .then(if (isSel) Modifier.border(2.dp, accent, CircleShape) else Modifier)
                                )
                                if (u.name.isNotBlank() && u.profileImageUrl.isBlank()) {
                                    Text(u.name.take(1).uppercase(), Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold, color = accent)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.name.ifBlank { u.username }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (u.username.isNotBlank()) Text("@${u.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (isSel) accent else Color.Transparent)
                                    .border(2.dp, if (isSel) accent else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { if (isSel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onSend(friends.filter { it.uid in selected }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (selected.isEmpty()) "Select friends" else "Send to ${selected.size}")
            }
        }
    }
}
