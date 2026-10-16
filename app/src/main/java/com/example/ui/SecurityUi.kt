package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.WorkerRouter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Two-step verification (2FA) client.
 *
 * Secrets never live on the phone: the Worker keeps `mfa_state/{uid}` (TOTP secret, hashed
 * backup codes, hashed e-mail codes) in Firestore with no client access, verifies every code,
 * and remembers which device sessions passed verification. The app only shows the gate.
 */
data class MfaStatus(
    val enabled: Boolean = false,
    val method: String = "",
    val backupRemaining: Int = 0,
    val sessionVerified: Boolean = true,
    val email: String = "",
    val emailReady: Boolean = false
)

object MfaClient {
    private const val FALLBACK = "https://solitary-hill-dcdc.mr44253990.workers.dev"

    fun sessionId(context: Context): String =
        context.getSharedPreferences("convo_device_session", Context.MODE_PRIVATE).getString("id", null)
            ?: java.util.UUID.randomUUID().toString().also {
                context.getSharedPreferences("convo_device_session", Context.MODE_PRIVATE).edit()
                    .putString("id", it).putLong("firstSeen", System.currentTimeMillis()).apply()
            }

    private fun cacheKey(context: Context): String = "ok_${FirebaseAuth.getInstance().currentUser?.uid}_${sessionId(context)}"
    fun cachedVerified(context: Context): Boolean = context.getSharedPreferences("convo_mfa", Context.MODE_PRIVATE).getBoolean(cacheKey(context), false)
    fun rememberVerified(context: Context, value: Boolean) { context.getSharedPreferences("convo_mfa", Context.MODE_PRIVATE).edit().putBoolean(cacheKey(context), value).apply() }

    suspend fun call(context: Context, path: String, body: JSONObject = JSONObject()): Pair<Boolean, JSONObject> = runCatching {
        val (code, json) = convoWorkerPost("database", path, body.put("sessionId", sessionId(context)))
        val ok = code in 200..299 && json.optBoolean("success", false)
        if (!ok && code == 404 && json.optString("error").isBlank()) json.put("error", "Redeploy the Worker (v4.4) to use two-step verification")
        ok to json
    }.getOrElse { false to JSONObject().put("error", it.localizedMessage ?: "Network error") }

    suspend fun status(context: Context): MfaStatus? {
        val (ok, j) = call(context, "/mfa/status")
        if (!ok) return null
        return MfaStatus(j.optBoolean("enabled"), j.optString("method"), j.optInt("backupRemaining"), j.optBoolean("sessionVerified", true), j.optString("email"), j.optBoolean("emailReady"))
    }

    /** Unauthenticated: reset a forgotten password with a one-time backup code. */
    suspend fun recover(email: String, backupCode: String, newPassword: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = WorkerRouter.resolve("database", WorkerRouter.primary.value.ifBlank { FALLBACK })
            val body = JSONObject().put("email", email.trim()).put("backupCode", backupCode.trim()).put("newPassword", newPassword)
            val req = Request.Builder().url("$base/mfa/recover").post(body.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
            OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (r.isSuccessful && j.optBoolean("success")) true to "Password changed. ${j.optInt("backupRemaining")} backup codes left — sign in with your new password."
                else false to j.optString("error").ifBlank { "HTTP ${r.code}" }
            }
        }.getOrElse { false to (it.localizedMessage ?: "Network error") }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, label: String = "6-digit code", allowBackup: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(if (allowBackup) v.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(9) else v.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (allowBackup) KeyboardType.Ascii else KeyboardType.NumberPassword),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Backup codes shown exactly once, with copy/share and a "saved them" confirmation. */
@Composable
fun BackupCodesDialog(codes: List<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    val all = codes.joinToString("\n")
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Default.Key, null, tint = Color(0xFFFFB300)) },
        title = { Text("Save your backup codes", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Each code works once. Use one if you lose your phone or can't get a code — it also lets you reset a forgotten password. Keep them somewhere safe (not in a screenshot others can see).", style = MaterialTheme.typography.bodySmall)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        codes.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth()) {
                                pair.forEach { code -> Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(vertical = 3.dp)) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyText(context, "Convo Chat backup codes", "Convo Chat backup codes\n$all") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Convo Chat backup codes\n$all"), "Save backup codes"))
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saved = !saved }) {
                    Checkbox(saved, { saved = it }); Text("I saved these codes", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { Button(onClick = onDone, enabled = saved) { Text("Done") } }
    )
}

/** Account security → Two-step verification card (settings). */
@Composable
fun TwoFactorCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<MfaStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var setupMethod by remember { mutableStateOf("") } // "", "email", "totp"
    var totpUri by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var codes by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmAction by remember { mutableStateOf("") } // "disable", "regen"
    fun refresh() = scope.launch { loading = true; status = MfaClient.status(context); if (status == null) error = "Could not reach the security server"; loading = false }
    LaunchedEffect(Unit) { refresh() }

    if (codes.isNotEmpty()) BackupCodesDialog(codes) { codes = emptyList(); refresh() }

    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val on = status?.enabled == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(if (on) listOf(Color(0xFF00C853), Color(0xFF00BFA5)) else listOf(Color(0xFF7C4DFF), Color(0xFF448AFF)))), contentAlignment = Alignment.Center) {
                    Icon(if (on) Icons.Default.VerifiedUser else Icons.Default.Security, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Two-step verification", fontWeight = FontWeight.ExtraBold)
                    Text(
                        when {
                            loading -> "Checking…"
                            on -> "On • ${if (status?.method == "totp") "Authenticator app" else "E-mail code to ${status?.email}"} • ${status?.backupRemaining} backup codes"
                            else -> "Off — add a second step when someone signs in on a new phone"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Switch(checked = on, onCheckedChange = { want ->
                    error = ""; code = ""
                    if (want) setupMethod = "choose" else confirmAction = "disable"
                })
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            if (setupMethod == "choose") {
                Text("How do you want to confirm new sign-ins?", fontWeight = FontWeight.Bold)
                listOf(
                    Triple("email", "E-mail code", "We send a 6-digit code to ${status?.email?.ifBlank { "your e-mail" }}" + if (status?.emailReady == false) " (admin must set up e-mail first)" else ""),
                    Triple("totp", "Authenticator app", "Google Authenticator, Microsoft Authenticator, 2FAS, Aegis… works offline")
                ).forEach { (id, title, sub) ->
                    Surface(onClick = {
                        error = ""; code = ""
                        if (id == "totp") scope.launch {
                            busy = true
                            val (ok, j) = MfaClient.call(context, "/mfa/totp/setup")
                            busy = false
                            if (ok) { totpUri = j.optString("uri"); totpSecret = j.optString("secret"); setupMethod = "totp" } else error = j.optString("error")
                        } else scope.launch {
                            busy = true
                            val (ok, j) = MfaClient.call(context, "/mfa/email/send", JSONObject().put("purpose", "enable"))
                            busy = false
                            if (ok) setupMethod = "email" else error = j.optString("error")
                        }
                    }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (id == "totp") Icons.Default.QrCode2 else Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
                TextButton(onClick = { setupMethod = "" }) { Text("Cancel") }
            }

            if (setupMethod == "totp") {
                Text("1. Scan this QR in your authenticator app", fontWeight = FontWeight.Bold)
                val qr = remember(totpUri) { runCatching { createQrBitmap(totpUri, 520).asImageBitmap() }.getOrNull() }
                qr?.let {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(it, "Authenticator QR", Modifier.size(190.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Or enter key: ", style = MaterialTheme.typography.bodySmall)
                    Text(totpSecret.chunked(4).joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { copyText(context, "Authenticator key", totpSecret) }) { Icon(Icons.Default.ContentCopy, "Copy key", Modifier.size(18.dp)) }
                }
                Text("2. Type the 6-digit code it shows", fontWeight = FontWeight.Bold)
            }
            if (setupMethod == "email") Text("Enter the code we sent to ${status?.email}. It expires in 10 minutes.", style = MaterialTheme.typography.bodySmall)

            if (setupMethod == "totp" || setupMethod == "email") {
                CodeField(code, { code = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { setupMethod = ""; code = "" }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(enabled = code.length == 6 && !busy, onClick = {
                        scope.launch {
                            busy = true
                            val (ok, j) = MfaClient.call(context, "/mfa/enable", JSONObject().put("method", setupMethod).put("code", code))
                            busy = false
                            if (ok) {
                                MfaClient.rememberVerified(context, true)
                                val arr = j.optJSONArray("backupCodes")
                                codes = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }
                                setupMethod = ""; code = ""
                            } else error = j.optString("error")
                        }
                    }, modifier = Modifier.weight(1f)) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Turn on") }
                }
            }

            if (status?.enabled == true && setupMethod.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmAction = "regen"; code = ""; error = "" }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Key, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("New backup codes") }
                }
                if ((status?.backupRemaining ?: 0) <= 3) Text("⚠️ Only ${status?.backupRemaining} backup codes left — make new ones.", color = Color(0xFFFF8F00), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirmAction.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { confirmAction = "" },
            title = { Text(if (confirmAction == "disable") "Turn off two-step verification?" else "Create new backup codes?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (status?.method == "email") "Confirm with the e-mail code or a backup code." else "Confirm with your authenticator code or a backup code.", style = MaterialTheme.typography.bodySmall)
                    if (status?.method == "email") TextButton(onClick = { scope.launch { val (ok, j) = MfaClient.call(context, "/mfa/email/send", JSONObject().put("purpose", if (confirmAction == "disable") "disable" else "verify")); Toast.makeText(context, if (ok) "Code sent" else j.optString("error"), Toast.LENGTH_SHORT).show() } }) { Text("Send e-mail code") }
                    CodeField(code, { code = it }, "Code or backup code", allowBackup = true)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(enabled = code.length >= 6 && !busy, onClick = {
                    scope.launch {
                        busy = true
                        val path = if (confirmAction == "disable") "/mfa/disable" else "/mfa/backup/regenerate"
                        val (ok, j) = MfaClient.call(context, path, JSONObject().put("code", code))
                        busy = false
                        if (ok) {
                            if (confirmAction == "regen") { val arr = j.optJSONArray("backupCodes"); codes = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) } }
                            confirmAction = ""; code = ""; refresh()
                        } else error = j.optString("error")
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Cancel") } }
        )
    }
}

/**
 * Full-screen sign-in gate shown after login when two-step verification is on and this device
 * session has not been verified yet. Cannot be dismissed — only verified or signed out.
 */
@Composable
fun MfaGateDialog(method: String, maskedEmail: String, onVerified: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var useBackup by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var sentOnce by remember { mutableStateOf(false) }
    fun sendEmail() = scope.launch {
        busy = true
        val (ok, j) = MfaClient.call(context, "/mfa/email/send", JSONObject().put("purpose", "verify"))
        busy = false
        if (ok) { info = "Code sent to ${j.optString("sentTo", maskedEmail)}"; error = "" } else error = j.optString("error")
    }
    LaunchedEffect(method) { if (method == "email" && !sentOnce) { sentOnce = true; sendEmail() } }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0C1F), Color(0xFF1A1450), Color(0xFF0B0C1F)))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(86.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF)))).border(3.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                Text("Two-step verification", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(
                    when {
                        useBackup -> "Enter one of your 8-character backup codes (like ABCD-1234)."
                        method == "totp" -> "Open your authenticator app and enter the 6-digit Convo Chat code."
                        else -> "We e-mailed a 6-digit code to $maskedEmail."
                    },
                    color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center
                )
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = .08f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            CodeField(code, { code = it; error = "" }, if (useBackup) "Backup code" else "6-digit code", allowBackup = useBackup)
                        }
                        if (info.isNotBlank()) Text(info, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
                        Button(
                            enabled = !busy && (if (useBackup) code.count { it.isLetterOrDigit() } == 8 else code.length == 6),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val (ok, j) = MfaClient.call(context, "/mfa/verify", JSONObject().put("code", code))
                                    busy = false
                                    if (ok) {
                                        MfaClient.rememberVerified(context, true)
                                        if (j.optBoolean("usedBackupCode")) Toast.makeText(context, "Backup code used • ${j.optInt("backupRemaining")} left", Toast.LENGTH_LONG).show()
                                        onVerified()
                                    } else error = j.optString("error")
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Verify", fontWeight = FontWeight.Bold) }
                    }
                }
                if (method == "email" && !useBackup) TextButton(onClick = { sendEmail() }, enabled = !busy) { Text("Resend code", color = Color.White) }
                TextButton(onClick = { useBackup = !useBackup; code = ""; error = "" }) {
                    Text(if (useBackup) "Use ${if (method == "totp") "authenticator" else "e-mail"} code instead" else "Lost access? Use a backup code", color = Color(0xFFB39DDB))
                }
                TextButton(onClick = onSignOut) { Icon(Icons.Default.Logout, null, tint = Color.White.copy(alpha = .7f)); Spacer(Modifier.width(6.dp)); Text("Sign out", color = Color.White.copy(alpha = .7f)) }
            }
        }
    }
}

/** Login screen: reset a forgotten password with e-mail + backup code. */
@Composable
fun BackupCodeRecoveryDialog(initialEmail: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var backup by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, null) },
        title = { Text("Recover with a backup code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Works if two-step verification was on and you saved your backup codes. Every device is signed out afterwards.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Account e-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(backup, { backup = it.uppercase().take(9) }, label = { Text("Backup code (ABCD-1234)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("New password (8+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                result?.let { (ok, msg) -> Text(msg, color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (result?.first == true) Button(onClick = onDismiss) { Text("Sign in") }
            else Button(enabled = !busy && email.contains("@") && backup.count { it.isLetterOrDigit() } == 8 && pass.length >= 8, onClick = {
                scope.launch { busy = true; result = MfaClient.recover(email, backup, pass); busy = false }
            }) { Text(if (busy) "Checking…" else "Reset password") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Admin → "Email & 2FA": e-mail provider for security codes, sign-in alerts and recovery mails. */
@Composable
fun AdminEmailSecurityPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = remember { FirebaseFirestore.getInstance().collection("admin_secrets").document("email") }
    var provider by remember { mutableStateOf("brevo") }
    var apiKey by remember { mutableStateOf("") }
    var fromEmail by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf("Convo Chat") }
    var enabled by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { doc.get().awaitTask() }.getOrNull()?.let { d ->
            provider = d.getString("provider") ?: "brevo"; apiKey = d.getString("apiKey").orEmpty()
            fromEmail = d.getString("fromEmail").orEmpty(); fromName = d.getString("fromName") ?: "Convo Chat"; enabled = d.getBoolean("enabled") != false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Used for two-step verification codes, new sign-in alerts and backup-code recovery e-mails. The key is stored in admin-only Firestore and used only by the Worker.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("brevo" to "Brevo (free 300/day)", "resend" to "Resend").forEach { (id, label) ->
                FilterChip(selected = provider == id, onClick = { provider = id }, label = { Text(label) })
            }
        }
        Text(
            if (provider == "brevo") "Brevo: Settings → SMTP & API → API keys (xkeysib-…). Verify your sender e-mail under Senders (a Gmail address works)."
            else "Resend: API Keys → Create (re_…). The From address must be on a domain you verified in Resend.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(apiKey, { apiKey = it.trim() }, label = { Text("API key") }, singleLine = true,
            visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fromEmail, { fromEmail = it.trim() }, label = { Text("From e-mail (verified sender)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fromName, { fromName = it.take(40) }, label = { Text("From name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Text("E-mail delivery on", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && apiKey.length > 10 && fromEmail.contains("@"), onClick = {
                scope.launch {
                    busy = true
                    status = runCatching {
                        doc.set(mapOf("provider" to provider, "apiKey" to apiKey, "fromEmail" to fromEmail, "fromName" to fromName, "enabled" to enabled, "updatedAt" to System.currentTimeMillis())).awaitTask()
                        AdminAudit.log("email_config_saved", provider)
                        val (code, j) = convoWorkerPost("database", "/admin/email/test", JSONObject())
                        if (code in 200..299 && j.optBoolean("success")) "✓ Saved. Test e-mail sent to ${j.optString("to")} via ${j.optString("provider")}"
                        else "Saved, but the test failed: ${j.optString("error").ifBlank { "HTTP $code" }}" + if (code == 404) " — redeploy the Worker" else ""
                    }.getOrElse { "✗ ${it.localizedMessage}" }
                    busy = false
                }
            }, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Save & send test") }
        }
        if (status.isNotBlank()) Text(status, color = if (status.startsWith("✓")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
