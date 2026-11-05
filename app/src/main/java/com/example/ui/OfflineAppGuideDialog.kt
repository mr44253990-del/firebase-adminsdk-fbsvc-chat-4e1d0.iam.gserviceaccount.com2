package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun OfflineAppGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val language by AppLanguage.code.collectAsState()
    val guide = remember(language) {
        val asset = if (language == "bn") "app-guide-bn.txt" else "app-guide-en.txt"
        runCatching { context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }
            .getOrElse { "Convo Chat app guide is not available on this installation." }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "bn") "অফলাইন অ্যাপ গাইড" else "Offline app guide") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(guide, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (language == "bn") "বন্ধ করুন" else "Close") }
        }
    )
}
