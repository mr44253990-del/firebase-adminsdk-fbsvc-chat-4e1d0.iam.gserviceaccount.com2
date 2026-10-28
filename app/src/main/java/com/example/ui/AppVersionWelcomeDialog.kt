package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppVersionWelcomeDialog(
    versionName: String,
    onStartTour: () -> Unit,
    onReadGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val language by AppLanguage.code.collectAsState()
    val updates = remember(language) {
        val asset = if (language == "bn") "app-update-bn.txt" else "app-update-en.txt"
        runCatching { context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readLines().filter(String::isNotBlank) } }
            .getOrElse { emptyList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (language == "bn") "Convo Chat-এ নতুন কী · $versionName" else "What’s new in Convo Chat · $versionName") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (language == "bn") "আপডেটের উল্লেখযোগ্য পরিবর্তন:" else "Here are the highlights in this update:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                updates.forEach { line ->
                    androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 5.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        Text(line.removePrefix("• "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    if (language == "bn") "আপনি কি পুরো অ্যাপটি ঘুরে দেখতে চান?" else "Would you like a guided tour of the whole app?",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onStartTour) { Text(if (language == "bn") "পুরো ট্যুর দেখুন" else "Take the app tour") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onReadGuide) { Text(if (language == "bn") "অফলাইন গাইড পড়ুন" else "Read offline guide") }
                TextButton(onClick = onDismiss) { Text(if (language == "bn") "এখন নয়" else "Not now") }
            }
        }
    )
}
