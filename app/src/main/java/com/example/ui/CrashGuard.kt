package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records the stack trace of an app crash so it can be shown (and copied/shared) on the next
 * launch. If a crash came from an optional feature that runs at start-up (the guided tour),
 * that feature is switched off so the app cannot get stuck in a crash loop.
 */
object CrashGuard {
    private const val FILE = "last_crash.txt"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter(); error.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()
                val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                File(app.filesDir, FILE).writeText(
                    "Convo ${info?.versionName ?: "?"} • Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) • ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n" + trace.take(12_000)
                )
                if (trace.contains("CoachMark") || trace.contains("CoachTour")) CoachTour.markDone(app)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) { runCatching { File(context.filesDir, FILE).delete() } }
}

/** Shown once after a crash: lets the user copy or share the technical details. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashGuard.pending(context)) }
    val text = report ?: return
    val dismiss = { CrashGuard.clear(context); report = null }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("The app closed unexpectedly last time") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Sorry about that. You can copy or share these technical details with the developer so it can be fixed.", fontSize = 13.sp)
                Text(text.take(3000), fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.heightIn(min = 40.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Convo crash report"); putExtra(Intent.EXTRA_TEXT, text)
                    }, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                dismiss()
            }) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Convo crash", text))
                android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                dismiss()
            }) { Text("Copy") }
        }
    )
}
