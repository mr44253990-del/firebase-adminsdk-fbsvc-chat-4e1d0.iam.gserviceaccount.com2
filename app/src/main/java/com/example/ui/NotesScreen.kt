package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.*

private val NoteColors = listOf(
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    listOf(Color(0xFF0575E6), Color(0xFF021B79)),
    listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFEC008C), Color(0xFFFC6767)),
    listOf(Color(0xFF36D1DC), Color(0xFF5B86E5))
)

/** Personal quick notes — colorful glass tiles, pinnable, synced to Firestore. */
@Composable
fun NotesScreen(
    feedViewModel: FeedViewModel,
    onBack: () -> Unit
) {
    val notes by feedViewModel.notes.collectAsState()
    var text by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { feedViewModel.start() }

    GlassBackground(bubbleCount = 8) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                GradientText("My Notes", MaterialTheme.typography.headlineMedium)
            }

            // composer
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    GlassField(
                        value = text, onValueChange = { text = it },
                        label = "Write a note…", singleLine = false
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteColors.forEachIndexed { i, colors ->
                            Box(
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(colors))
                                    .border(
                                        width = if (colorIndex == i) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { colorIndex = i }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        GradientButton(
                            text = "Save",
                            onClick = {
                                if (text.isNotBlank()) {
                                    feedViewModel.addNote(text.trim(), colorIndex)
                                    text = ""
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            val sorted = notes.sortedWith(compareByDescending<com.example.data.Note> { it.pinned }.thenByDescending { it.createdAt })
            if (sorted.isEmpty()) {
                EmptyState("📝", "No notes yet", "Jot down ideas, reminders, anything!")
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    items(sorted, key = { it.noteId }) { note ->
                        val colors = NoteColors[note.colorIndex % NoteColors.size]
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.75f) }))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                                .padding(14.dp)
                        ) {
                            if (note.pinned) {
                                Icon(Icons.Default.PushPin, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(note.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                Text(
                                    timeAgo(note.createdAt),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.PushPin, null, tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp).clickable { feedViewModel.togglePinNote(note) }
                                )
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp).clickable { feedViewModel.deleteNote(note) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
