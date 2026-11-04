package com.example.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Detects links, e-mails and phone numbers inside a chat message. */
object MessageTextTools {
    fun links(text: String): List<String> {
        val m = Patterns.WEB_URL.matcher(text)
        val out = LinkedHashSet<String>()
        while (m.find()) {
            val raw = m.group().trimEnd('.', ',', ')', '!', '?')
            if (raw.contains('@') && !raw.startsWith("http")) continue
            if (!raw.contains('.')) continue
            out += if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
        }
        return out.take(4)
    }

    fun emails(text: String): List<String> {
        val m = Patterns.EMAIL_ADDRESS.matcher(text)
        val out = LinkedHashSet<String>()
        while (m.find()) out += m.group()
        return out.take(3)
    }

    fun phones(text: String): List<String> =
        Regex("""(?<![\w])(\+?\d[\d\s-]{7,15}\d)(?![\w])""").findAll(text).map { it.value.replace(" ", "").replace("-", "") }
            .filter { it.count(Char::isDigit) in 8..15 }.distinct().take(3).toList()

    fun copy(context: Context, label: String, value: String, toast: String = "Copied") {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ shows its own clipboard confirmation.
        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    fun open(context: Context, intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show() }
    }

    fun share(context: Context, text: String) {
        open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share message"))
    }
}

private fun shortLabel(value: String, max: Int = 34): String =
    value.removePrefix("https://").removePrefix("http://").removePrefix("www.").let { if (it.length > max) it.take(max - 1) + "…" else it }

/**
 * Extra long-press actions for a text message: select part of the text, share, and smart
 * actions for every link, e-mail and phone number found in it. Use inside a DropdownMenu.
 */
@Composable
fun MessageSmartTextActions(text: String, onSelectText: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text("Select text") },
        leadingIcon = { Icon(Icons.Default.TextFields, null) },
        onClick = { onDone(); onSelectText() }
    )
    DropdownMenuItem(
        text = { Text("Share") },
        leadingIcon = { Icon(Icons.Default.Share, null) },
        onClick = { onDone(); MessageTextTools.share(context, text) }
    )
    MessageTextTools.links(text).forEach { url ->
        DropdownMenuItem(
            text = { Text("Open ${shortLabel(url)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.OpenInBrowser, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDone(); MessageTextTools.open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        )
        DropdownMenuItem(
            text = { Text("Copy link", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDone(); MessageTextTools.copy(context, "Link", url, "Link copied") }
        )
    }
    MessageTextTools.emails(text).forEach { mail ->
        DropdownMenuItem(
            text = { Text("E-mail ${shortLabel(mail, 28)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            onClick = { onDone(); MessageTextTools.open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail"))) }
        )
        DropdownMenuItem(
            text = { Text("Copy e-mail") },
            leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
            onClick = { onDone(); MessageTextTools.copy(context, "E-mail", mail, "E-mail copied") }
        )
    }
    MessageTextTools.phones(text).forEach { phone ->
        DropdownMenuItem(
            text = { Text("Call $phone") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            onClick = { onDone(); MessageTextTools.open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        )
        DropdownMenuItem(
            text = { Text("Copy number") },
            leadingIcon = { Icon(Icons.Default.Dialpad, null) },
            onClick = { onDone(); MessageTextTools.copy(context, "Phone", phone, "Number copied") }
        )
    }
    if (text.length in 2..200) DropdownMenuItem(
        text = { Text("Search the web") },
        leadingIcon = { Icon(Icons.Default.TravelExplore, null) },
        onClick = { onDone(); MessageTextTools.open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(text.trim())))) }
    )
}

/** Full message in a dialog where any part can be selected and copied with the system handles. */
@Composable
fun SelectableMessageDialog(text: String, sender: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.TextFields, null) },
        title = { Text(if (sender.isBlank()) "Select text" else "Message from $sender", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Long-press a word, then drag the handles to choose what to copy.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)) {
                    SelectionContainer {
                        Text(text, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
                    }
                }
                val links = MessageTextTools.links(text)
                if (links.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Links", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    links.forEach { url ->
                        Row {
                            AssistChip(onClick = { MessageTextTools.copy(context, "Link", url, "Link copied") }, label = { Text(shortLabel(url, 30), maxLines = 1) }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { MessageTextTools.copy(context, "Message", text, "Message copied"); onDismiss() }) { Text("Copy all") } },
        dismissButton = {
            Row {
                TextButton(onClick = { MessageTextTools.share(context, text) }) { Text("Share") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
