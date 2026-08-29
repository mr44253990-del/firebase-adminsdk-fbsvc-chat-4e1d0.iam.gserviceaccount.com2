package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NexusBackground
import com.example.ui.theme.auroraRing
import com.example.ui.theme.liquidGlass

private data class AboutRow(val label: String, val value: String)

/**
 * Settings → About. Living aurora canvas, glass cards, zero static screenshots.
 */
@Composable
fun AboutNexusScreen(
    versionName: String,
    onBack: () -> Unit
) {
    val identity = listOf(
        AboutRow("Creator", "Rakibul Islam"),
        AboutRow("Studies at", "Kapilmuni College"),
        AboutRow("Role", "Android engineer · product designer"),
        AboutRow("Focus", "Realtime social, Compose motion design, on-device performance"),
        AboutRow("Build", "Convo Nexus $versionName"),
        AboutRow("Stack", "Jetpack Compose · Firebase · Supabase Storage · WebRTC")
    )
    val credits = listOf(
        AboutRow("Text & graph data", "Firebase Firestore + Realtime Database"),
        AboutRow("Media & files", "Supabase Storage buckets"),
        AboutRow("Offline cache", "Room + Media3 disk cache"),
        AboutRow("Calls", "Google WebRTC with adaptive bitrate"),
        AboutRow("Motion", "GlassKit draw-phase animation engine")
    )

    NexusBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "About",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(32.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        Modifier
                            .size(96.dp)
                            .auroraRing(3.dp, RoundedCornerShape(50)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("RI", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Rakibul Islam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Kapilmuni College",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "I build fast, expressive Android products. Convo Nexus is my flagship " +
                            "social messenger: stories, a public feed, private groups, encrypted " +
                            "backups and WebRTC calling — all wrapped in a liquid-glass interface " +
                            "that stays at 60fps on budget hardware.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { SectionLabel("Identity") }
            items(identity) { row -> AboutCard(row) }
            item { SectionLabel("Under the hood") }
            items(credits) { row -> AboutCard(row) }
            item {
                Text(
                    "Made with care in Bangladesh.",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp)
    )
}

@Composable
private fun AboutCard(row: AboutRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(22.dp), elevation = 10.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            row.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(row.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
