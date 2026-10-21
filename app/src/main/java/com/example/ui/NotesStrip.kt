package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.User

/** Friends-only, expiring status bubbles; the user explicitly chooses what to publish. */
@Composable
fun NotesStrip(me: User?, friends: List<User>, onPublish: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val active = remember(friends, now) { friends.filter { it.note.isNotBlank() && it.noteAt >= now - 24L * 60 * 60 * 1000 }.distinctBy { it.uid } }
    var editor by remember { mutableStateOf(false) }
    var draft by remember(me?.note) { mutableStateOf(me?.note.orEmpty()) }
    var viewed by remember { mutableStateOf<User?>(null) }

    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "my_note") {
            Column(Modifier.width(62.dp).clickable { draft = me?.note.orEmpty(); editor = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    if (!me?.profileImageUrl.isNullOrBlank()) AsyncImage(me?.profileImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    else Text(me?.name?.take(1)?.uppercase().orEmpty().ifBlank { "+" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.align(Alignment.BottomEnd).size(17.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("+", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall) }
                }
                Text(if (me?.noteAt?.let { it >= now - 24L * 60 * 60 * 1000 } == true) "Your note" else "Add note", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (!me?.note.isNullOrBlank()) Text(me?.note.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(active, key = { it.uid }) { user ->
            Column(Modifier.width(70.dp).clickable { viewed = user }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    if (user.profileImageUrl.isNotBlank()) AsyncImage(user.profileImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    else Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
                Text(user.name.ifBlank { "Friend" }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (editor) AlertDialog(
        onDismissRequest = { editor = false },
        title = { Text("Your 24-hour note") },
        text = { OutlinedTextField(draft, { draft = it.take(80) }, label = { Text("Visible to your friends") }, supportingText = { Text("${draft.length}/80") }, singleLine = false, minLines = 2) },
        confirmButton = { TextButton(onClick = { onPublish(draft); editor = false }) { Text("Publish") } },
        dismissButton = { Row { if (!me?.note.isNullOrBlank()) TextButton(onClick = { onPublish(""); editor = false }) { Text("Clear") }; TextButton(onClick = { editor = false }) { Text("Cancel") } } }
    )
    viewed?.let { user -> AlertDialog(onDismissRequest = { viewed = null }, title = { Text(user.name) }, text = { Text(user.note) }, confirmButton = { TextButton(onClick = { viewed = null }) { Text("Close") } }) }
}
