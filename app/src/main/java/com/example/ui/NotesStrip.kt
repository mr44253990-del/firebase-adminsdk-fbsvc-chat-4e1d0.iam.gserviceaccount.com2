package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.User

private const val NOTE_LIFE_MS = 24L * 60 * 60 * 1000

private fun noteTimeLeft(updatedAt: Long, now: Long): String {
    val left = (updatedAt + NOTE_LIFE_MS - now).coerceAtLeast(0L)
    val hours = left / 3_600_000L
    return if (hours >= 1) "${hours}h left" else "${(left / 60_000L).coerceAtLeast(1)}m left"
}

/** Colour pair picked from the note text, so every note gets its own mood. */
private fun noteColors(seed: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF8B7BFF), Color(0xFF36D8E6)), listOf(Color(0xFFFF6FB5), Color(0xFFFFB03A)), listOf(Color(0xFF19C37D), Color(0xFF36D8E6)),
        listOf(Color(0xFFFF8A3D), Color(0xFFFF4D7E)), listOf(Color(0xFF3AA0FF), Color(0xFFB07CFF)), listOf(Color(0xFF2DD4BF), Color(0xFF8B7BFF))
    )
    return palettes[(seed.hashCode() and 0x7fffffff) % palettes.size]
}

/**
 * Friends-only, expiring notes (v5.2 redesign): each note floats as a thought bubble above the
 * person's picture inside a glowing mood ring, with the time left. Note text is never copied
 * into the broadly readable user profile.
 */
@Composable
fun NotesStrip(me: User?, myNote: UserNote?, friends: List<Pair<User, UserNote>>, onPublish: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val active = remember(friends, now / 60_000L) { friends.filter { it.second.text.isNotBlank() && it.second.updatedAt >= now - NOTE_LIFE_MS }.distinctBy { it.first.uid }.sortedByDescending { it.second.updatedAt } }
    var editor by remember { mutableStateOf(false) }
    var draft by remember(myNote?.text) { mutableStateOf(myNote?.text.orEmpty()) }
    var viewed by remember { mutableStateOf<Pair<User, UserNote>?>(null) }
    val myNoteLive = myNote?.takeIf { it.text.isNotBlank() && it.updatedAt >= now - NOTE_LIFE_MS }
    val motion = rememberInfiniteTransition(label = "notes")
    val spin by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "ring")
    val bob by motion.animateFloat(-2f, 2f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "my_note") {
            NoteBubbleItem(
                name = if (myNoteLive != null) "Your note" else "Add note", image = me?.profileImageUrl.orEmpty(), initial = me?.name?.take(1)?.uppercase().orEmpty().ifBlank { "+" },
                note = myNoteLive?.text ?: "Share a thought…", colors = if (myNoteLive != null) noteColors(myNoteLive.text) else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                live = myNoteLive != null, spin = spin, bob = bob, showAdd = myNoteLive == null
            ) { draft = myNote?.text.orEmpty(); editor = true }
        }
        items(active, key = { it.first.uid }) { (user, note) ->
            NoteBubbleItem(
                name = user.name.substringBefore(' ').ifBlank { "Friend" }, image = user.profileImageUrl, initial = user.name.take(1).uppercase(),
                note = note.text, colors = noteColors(note.text), live = true, spin = spin, bob = bob, showAdd = false, online = user.isOnline
            ) { viewed = user to note }
        }
    }

    if (editor) Dialog(onDismissRequest = { editor = false }) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your 24-hour note", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(noteColors(draft.ifBlank { "note" }))).padding(18.dp),
                    contentAlignment = Alignment.Center
                ) { Text(draft.ifBlank { "Your note preview" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center) }
                OutlinedTextField(draft, { draft = it.take(80) }, label = { Text("Visible only to your friends") }, supportingText = { Text("${draft.length}/80") }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("😊 Feeling good", "😴 Sleepy", "📚 Studying", "🎧 Listening to music", "🍕 Hungry", "💼 Busy at work", "🏏 Watching the match", "✈️ Travelling", "🤒 Not well").forEach { mood ->
                        AssistChip(onClick = { draft = mood }, label = { Text(mood, fontSize = 12.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!myNote?.text.isNullOrBlank()) TextButton(onClick = { onPublish(""); editor = false }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editor = false }) { Text("Cancel") }
                    Button(enabled = draft.isNotBlank(), onClick = { onPublish(draft.trim()); editor = false }, shape = CircleShape) { Text("Share for 24h") }
                }
            }
        }
    }
    viewed?.let { (user, note) ->
        Dialog(onDismissRequest = { viewed = null }) {
            Column(
                Modifier.clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(noteColors(note.text))).padding(22.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)).border(2.dp, Color.White.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    if (user.profileImageUrl.isNotBlank()) AsyncImage(user.profileImageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                }
                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(note.text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(relativeTimeLabel(note.updatedAt) + "  ·  " + noteTimeLeft(note.updatedAt, System.currentTimeMillis()), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
                TextButton(onClick = { viewed = null }) { Text("Close", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun NoteBubbleItem(
    name: String, image: String, initial: String, note: String, colors: List<Color>, live: Boolean,
    spin: Float, bob: Float, showAdd: Boolean, online: Boolean = false, onClick: () -> Unit
) {
    Column(Modifier.width(86.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        // Thought bubble
        Box(
            Modifier.graphicsLayer { translationY = bob * density }
                .clip(RoundedCornerShape(16.dp))
                .background(if (live) Brush.linearGradient(colors) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
                .padding(horizontal = 8.dp, vertical = 5.dp).widthIn(min = 44.dp, max = 86.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(note, color = if (live) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Row(Modifier.padding(start = 10.dp).align(Alignment.Start)) {
            Box(Modifier.padding(top = 1.dp).size(6.dp).clip(CircleShape).background(if (live) colors.first() else MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.padding(top = 5.dp, start = 1.dp).size(3.dp).clip(CircleShape).background(if (live) colors.first() else MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            if (live) Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.clip(CircleShape).background(Brush.sweepGradient(colors + colors.first())))
            Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Box(Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(initial, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (image.isNotBlank()) AsyncImage(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
                }
            }
            if (showAdd) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, MaterialTheme.colorScheme.background, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(13.dp))
            } else if (online) Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2EE86F)))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
