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
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloat
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

    /**
     * Returns the status when this signed-in session still has to pass two-step verification,
     * or null when it can go straight to Home (2FA off, or already verified on this device).
     */
    suspend fun precheck(context: Context): MfaStatus? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        if (cachedVerified(context)) return null
        val doc = runCatching { FirebaseFirestore.getInstance().collection("users").document(uid).get().awaitTask() }.getOrNull()
        val enabled = doc?.getBoolean("mfaEnabled") ?: return null
        if (!enabled) return null
        val status = status(context)
        return when {
            status == null -> MfaStatus(enabled = true, method = doc.getString("mfaMethod").orEmpty().ifBlank { "email" }, sessionVerified = false)
            status.enabled && !status.sessionVerified -> status
            else -> { rememberVerified(context, true); null }
        }
    }

    /** Unauthenticated: reset a forgotten password with a one-time backup code. */
    suspend fun recover(email: String, backupCode: String, newPassword: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("email", email.trim()).put("backupCode", backupCode.trim()).put("newPassword", newPassword)
            WorkerRouter.execute("database", OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(), WorkerRouter.primary.value.ifBlank { FALLBACK }) { base ->
                Request.Builder().url("$base/mfa/recover").post(body.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
            }.use { r ->
                val j = com.example.data.WorkerHealth.normalize(r.code, runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject()))
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
                    OutlinedButton(onClick = { copyText(context, "Convo Chat backup codes", "Convo Chat backup codes\n$all") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Copy")) }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Convo Chat backup codes\n$all"), "Save backup codes"))
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Save")) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saved = !saved }) {
                    Checkbox(saved, { saved = it }); Text("I saved these codes", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { Button(onClick = onDone, enabled = saved) { Text(tr("Done")) } }
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
                    Text(tr("Two-step verification"), fontWeight = FontWeight.ExtraBold)
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
                TextButton(onClick = { setupMethod = "" }) { Text(tr("Cancel")) }
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
                    OutlinedButton(onClick = { setupMethod = ""; code = "" }, modifier = Modifier.weight(1f)) { Text(tr("Cancel")) }
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
                    }, modifier = Modifier.weight(1f)) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Turn on")) }
                }
            }

            if (status?.enabled == true && setupMethod.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmAction = "regen"; code = ""; error = "" }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Key, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("New backup codes") }
                }
                if ((status?.backupRemaining ?: 0) <= 3) Text("Only ${status?.backupRemaining} backup codes left — make new ones.", color = Color(0xFFFF8F00), style = MaterialTheme.typography.bodySmall)
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
                }) { Text(tr("Confirm")) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text(tr("Cancel")) } }
        )
    }
}

/**
 * Full-screen sign-in gate shown after login when two-step verification is on and this device
 * session has not been verified yet. Cannot be dismissed — only verified or signed out.
 */
@Composable
fun MfaGateDialog(method: String, maskedEmail: String, onVerified: () -> Unit, onSignOut: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        MfaVerifyContent(method, maskedEmail, onVerified, onSignOut)
    }
}

/** Same verification UI as a normal screen (used right after sign-in, before Home is shown). */
@Composable
fun MfaGateScreen(method: String, maskedEmail: String, onVerified: () -> Unit, onSignOut: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = true) { /* must verify or sign out */ }
    MfaVerifyContent(method, maskedEmail, onVerified, onSignOut)
}

/**
 * Shown between sign-in/splash and Home: decides, before Home ever appears, whether this
 * session must pass two-step verification. Prevents Home flashing and then the 2FA screen.
 */
@Composable
fun SessionGateScreen(onHome: () -> Unit, onMfa: (MfaStatus) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val needed = kotlinx.coroutines.withTimeoutOrNull(9000) { MfaClient.precheck(context) }
        if (needed != null) onMfa(needed) else onHome()
    }
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "gate")
    val spin by motion.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "gate_spin")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070816), Color(0xFF15123A), Color(0xFF071622)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(92.dp).rotate(spin).border(3.dp, Brush.sweepGradient(listOf(Color.Transparent, Color(0xFF8A72FF), Color(0xFF36D8C4))), CircleShape))
                ConvoLogo(Modifier.size(56.dp), size = 56.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Securing your session…", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MfaVerifyContent(method: String, maskedEmail: String, onVerified: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var useBackup by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var sentOnce by remember { mutableStateOf(false) }
    var resendIn by remember { mutableIntStateOf(0) }
    LaunchedEffect(resendIn) { if (resendIn > 0) { kotlinx.coroutines.delay(1000); resendIn-- } }
    fun sendEmail() = scope.launch {
        busy = true
        val (ok, j) = MfaClient.call(context, "/mfa/email/send", JSONObject().put("purpose", "verify"))
        busy = false
        if (ok) { info = "Code sent to ${j.optString("sentTo", maskedEmail)}. Check Inbox, then Spam/Promotions."; error = ""; resendIn = 45 } else error = j.optString("error")
    }
    fun verify() = scope.launch {
        busy = true
        val (ok, j) = MfaClient.call(context, "/mfa/verify", JSONObject().put("code", code))
        busy = false
        if (ok) {
            MfaClient.rememberVerified(context, true)
            if (j.optBoolean("usedBackupCode")) Toast.makeText(context, "Backup code used • ${j.optInt("backupRemaining")} left", Toast.LENGTH_LONG).show()
            onVerified()
        } else { error = j.optString("error").ifBlank { "Wrong code. Try again." }; code = "" }
    }
    LaunchedEffect(method) { if (method == "email" && !sentOnce) { sentOnce = true; sendEmail() } }
    val ready = if (useBackup) code.count { it.isLetterOrDigit() } == 8 else code.length == 6
    LaunchedEffect(code) { if (!useBackup && code.length == 6 && !busy) verify() }
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "mfa")
    val pulse by motion.animateFloat(.92f, 1.08f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "mfa_pulse")
    val spin by motion.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.LinearEasing)), label = "mfa_spin")
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070816), Color(0xFF1A1450), Color(0xFF071622)))),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.TopStart).offset((-60).dp, 40.dp).size(240.dp).clip(CircleShape).background(Color(0xFF7C4DFF).copy(alpha = .16f)))
        Box(Modifier.align(Alignment.BottomEnd).offset(70.dp, 40.dp).size(260.dp).clip(CircleShape).background(Color(0xFF00E5FF).copy(alpha = .10f)))
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                Box(Modifier.size(128.dp).rotate(spin).border(2.dp, Brush.sweepGradient(listOf(Color.Transparent, Color(0xFFB388FF), Color(0xFF00E5FF), Color.Transparent)), CircleShape))
                Box(
                    Modifier.size((88 * pulse).dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00B8D4))))
                        .border(3.dp, Color.White.copy(alpha = .25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(if (useBackup) Icons.Default.Key else if (method == "totp") Icons.Default.PhonelinkLock else Icons.Default.MarkEmailRead, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            }
            Text(tr("Two-step verification"), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(
                when {
                    useBackup -> "Enter one of your 8-character backup codes (like ABCD-1234)."
                    method == "totp" -> "Open your authenticator app and enter the 6-digit Convo Chat code."
                    else -> "We e-mailed a 6-digit code to $maskedEmail."
                },
                color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center
            )
            Surface(shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = .08f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .14f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!useBackup) CodeBoxes(code)
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        CodeField(code, { code = it; error = "" }, if (useBackup) "Backup code" else "Type the 6-digit code", allowBackup = useBackup)
                    }
                    if (info.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF80CBC4), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(info, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
                    }
                    if (error.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(error, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = !busy && ready, onClick = { verify() },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else { Icon(Icons.Default.VerifiedUser, null); Spacer(Modifier.width(8.dp)); Text(tr("Verify"), fontWeight = FontWeight.Bold) } }
                }
            }
            if (method == "email" && !useBackup) TextButton(onClick = { sendEmail() }, enabled = !busy && resendIn == 0) {
                Icon(Icons.Default.Refresh, null, tint = Color.White.copy(alpha = if (resendIn == 0) 1f else .5f)); Spacer(Modifier.width(6.dp))
                Text(if (resendIn > 0) "Resend in ${resendIn}s" else tr("Resend code"), color = Color.White.copy(alpha = if (resendIn == 0) 1f else .5f))
            }
            TextButton(onClick = { useBackup = !useBackup; code = ""; error = "" }) {
                Text(if (useBackup) "Use ${if (method == "totp") "authenticator" else "e-mail"} code instead" else "Lost access? Use a backup code", color = Color(0xFFB39DDB))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = .45f), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp))
                Text("Protects your account even if your password leaks", color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
            }
            TextButton(onClick = onSignOut) { Icon(Icons.Default.Logout, null, tint = Color.White.copy(alpha = .7f)); Spacer(Modifier.width(6.dp)); Text(tr("Sign out"), color = Color.White.copy(alpha = .7f)) }
        }
    }
}

/** Six glowing boxes that mirror the typed code. */
@Composable
private fun CodeBoxes(code: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        repeat(6) { i ->
            val ch = code.getOrNull(i)
            val active = i == code.length
            Box(
                Modifier.size(width = 42.dp, height = 52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (ch != null) .16f else .06f))
                    .border(if (active) 2.dp else 1.dp, if (active) Color(0xFF00E5FF) else Color.White.copy(alpha = .2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text(ch?.toString() ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace) }
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
            if (result?.first == true) Button(onClick = onDismiss) { Text(tr("Sign in")) }
            else Button(enabled = !busy && email.contains("@") && backup.count { it.isLetterOrDigit() } == 8 && pass.length >= 8, onClick = {
                scope.launch { busy = true; result = MfaClient.recover(email, backup, pass); busy = false }
            }) { Text(if (busy) "Checking…" else "Reset password") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

/** One sending provider in the e-mail pool (admin_secrets/email.providers). */
private data class MailProviderEntry(
    val id: String,
    val type: String,
    val apiKey: String = "",
    val secret: String = "",
    val domain: String = "",
    val region: String = "",
    val clientId: String = "",
    val fromEmail: String = "",
    val fromName: String = "",
    val enabled: Boolean = true,
    val weight: Int = 1
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id, "type" to type, "apiKey" to apiKey, "secret" to secret, "domain" to domain, "region" to region,
        "clientId" to clientId, "fromEmail" to fromEmail, "fromName" to fromName, "enabled" to enabled, "weight" to weight
    )
}

private data class MailProviderInfo(
    val type: String,
    val label: String,
    val free: String,
    val keyLabel: String,
    val help: String,
    val secretLabel: String? = null,
    val domainLabel: String? = null,
    val clientIdLabel: String? = null,
    val regions: List<String> = emptyList()
)

private val MAIL_PROVIDERS = listOf(
    MailProviderInfo("brevo", "Brevo", "300/day", "API key (xkeysib-…)", "Settings → SMTP & API → API keys. Verify the sender under Senders, and authenticate your domain for best inbox placement."),
    MailProviderInfo("resend", "Resend", "3,000/month", "API key (re_…)", "API Keys → Create. The From address must be on a domain verified in Resend."),
    MailProviderInfo("gmail_oauth", "Gmail API (personal Gmail)", "~500/day", "OAuth refresh token", "Google Cloud → enable Gmail API → OAuth client (Web) → get a refresh token with scope gmail.send for the sender account (OAuth Playground works). From e-mail = that Gmail address.", secretLabel = "OAuth client secret", clientIdLabel = "OAuth client ID"),
    MailProviderInfo("gmail_sa", "Gmail API (Workspace + service account)", "Workspace quota", "", "Google Workspace only: Admin console → Security → API controls → Domain-wide delegation → add the Firebase service-account client ID with scope https://www.googleapis.com/auth/gmail.send. From e-mail = a Workspace mailbox. Uses the Worker's existing FIREBASE_SERVICE_ACCOUNT, no key needed here."),
    MailProviderInfo("mailjet", "Mailjet", "200/day", "API key", "Account → API keys. Verify the sender address or domain.", secretLabel = "Secret key"),
    MailProviderInfo("smtp2go", "SMTP2GO", "1,000/month", "API key (api-…)", "Sending → API keys. Verify the sender domain."),
    MailProviderInfo("mailtrap", "Mailtrap", "4,000/month", "API token", "Sending Domains → verify domain → API Tokens."),
    MailProviderInfo("mailgun", "Mailgun", "100/day", "Private API key", "Sending → Domains → your domain. Use the EU region if your domain is in the EU.", domainLabel = "Sending domain (mg.example.com)", regions = listOf("us", "eu")),
    MailProviderInfo("sendgrid", "SendGrid", "trial", "API key (SG.…)", "Settings → API Keys (Mail Send). Verify a single sender or domain."),
    MailProviderInfo("postmark", "Postmark", "100/month", "Server API token", "Servers → your server → API Tokens. Add a sender signature."),
    MailProviderInfo("mailersend", "MailerSend", "500/month", "API token (mlsn.…)", "Domains → verify → API tokens."),
    MailProviderInfo("zeptomail", "ZeptoMail (Zoho)", "credits", "Send Mail token", "Mail Agents → SMTP/API → Send Mail token.", regions = listOf("com", "eu", "in")),
    MailProviderInfo("sequenzy", "Sequenzy", "2,500/month", "API key", "Settings → API keys. Verify your sending domain."),
    MailProviderInfo("plunk", "Plunk", "1,000/month", "Secret API key (sk_…)", "Settings → API keys. Verify your domain to send from it.")
)

private fun mailInfo(type: String) = MAIL_PROVIDERS.firstOrNull { it.type == type } ?: MAIL_PROVIDERS.first()

/** Admin → "Email & 2FA": a pool of e-mail providers used at random, with automatic failover. */
@Composable
fun AdminEmailSecurityPanel() {
    val scope = rememberCoroutineScope()
    val doc = remember { FirebaseFirestore.getInstance().collection("admin_secrets").document("email") }
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var strategy by remember { mutableStateOf("random") }
    var fromEmail by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf("Convo Chat") }
    var replyTo by remember { mutableStateOf("") }
    var brandUrl by remember { mutableStateOf("") }
    var sponsorLine by remember { mutableStateOf("") }
    var supportEmail by remember { mutableStateOf("") }
    val providers = remember { mutableStateListOf<MailProviderEntry>() }
    var editing by remember { mutableStateOf<MailProviderEntry?>(null) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }

    suspend fun refreshLive() {
        val (code, j) = runCatching { convoWorkerPost("database", "/admin/email/providers", JSONObject()) }.getOrElse { 0 to JSONObject() }
        if (code in 200..299) {
            val arr = j.optJSONArray("providers")
            live = buildMap<String, JSONObject> { if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { put(it.optString("id"), it) } }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { doc.get().awaitTask() }.getOrNull()?.let { d ->
            enabled = d.getBoolean("enabled") != false
            strategy = d.getString("strategy") ?: "random"
            fromEmail = d.getString("fromEmail").orEmpty(); fromName = d.getString("fromName") ?: "Convo Chat"
            replyTo = d.getString("replyTo").orEmpty(); brandUrl = d.getString("brandUrl").orEmpty()
            sponsorLine = d.getString("sponsorLine").orEmpty(); supportEmail = d.getString("supportEmail").orEmpty()
            providers.clear()
            // Legacy single provider becomes the first pool entry so nothing stops working.
            val legacyKey = d.getString("apiKey").orEmpty()
            if (legacyKey.isNotBlank()) providers += MailProviderEntry("primary", d.getString("provider") ?: "brevo", apiKey = legacyKey, fromEmail = fromEmail, fromName = fromName)
            @Suppress("UNCHECKED_CAST")
            (d.get("providers") as? List<Map<String, Any?>>)?.forEach { m ->
                val id = m["id"]?.toString().orEmpty().ifBlank { "p" + System.nanoTime().toString().takeLast(6) }
                if (providers.none { it.id == id }) providers += MailProviderEntry(
                    id, m["type"]?.toString() ?: "brevo", m["apiKey"]?.toString().orEmpty(), m["secret"]?.toString().orEmpty(),
                    m["domain"]?.toString().orEmpty(), m["region"]?.toString().orEmpty(), m["clientId"]?.toString().orEmpty(),
                    m["fromEmail"]?.toString().orEmpty(), m["fromName"]?.toString().orEmpty(), m["enabled"] as? Boolean ?: true,
                    (m["weight"] as? Number)?.toInt() ?: 1
                )
            }
        }
        loaded = true
        refreshLive()
    }

    fun save(test: String?) = scope.launch {
        busy = true
        val primary = providers.firstOrNull { it.id == "primary" }
        val data = mutableMapOf<String, Any>(
            "enabled" to enabled, "strategy" to strategy, "fromEmail" to fromEmail, "fromName" to fromName,
            "replyTo" to replyTo, "brandName" to fromName.ifBlank { "Convo Chat" }, "brandUrl" to brandUrl,
            "sponsorLine" to sponsorLine, "supportEmail" to supportEmail,
            "providers" to providers.filter { it.id != "primary" }.map { it.toMap() },
            "updatedAt" to System.currentTimeMillis()
        )
        // Keep the legacy fields for older Workers.
        if (primary != null) { data["provider"] = primary.type; data["apiKey"] = primary.apiKey } else { data["provider"] = ""; data["apiKey"] = "" }
        val result = runCatching {
            doc.set(data).awaitTask()
            AdminAudit.log("email_config_saved", "${providers.size} providers")
            if (test == null) "Saved ${providers.size} provider(s)."
            else {
                val body = JSONObject(); if (test.isNotBlank()) body.put("providerId", test)
                val (code, j) = convoWorkerPost("database", "/admin/email/test", body)
                if (code in 200..299 && j.optBoolean("success")) "Saved. Test e-mail sent to ${j.optString("to")} via ${j.optString("provider")}" + if (j.optInt("tried") > 1) " (after ${j.optInt("tried") - 1} failover)" else ""
                else throw IllegalStateException("Saved, but the test failed: ${j.optString("error").ifBlank { "HTTP $code" }}" + if (code == 404) ". Redeploy the Worker." else "")
            }
        }
        statusOk = result.isSuccess
        status = result.getOrElse { it.message ?: "Error" }
        refreshLive()
        busy = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Security codes, new sign-in alerts and recovery e-mails. Add several providers: each e-mail goes through a random one, and if it fails or hits its limit the next one is tried automatically. Keys stay in admin-only Firestore and are used only by the Worker.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) { Text("E-mail delivery on", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Switch(enabled, { enabled = it }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = strategy == "random", onClick = { strategy = "random" }, label = { Text("Random + failover") }, leadingIcon = { Icon(Icons.Default.Shuffle, null, Modifier.size(16.dp)) })
            FilterChip(selected = strategy == "ordered", onClick = { strategy = "ordered" }, label = { Text("In order") }, leadingIcon = { Icon(Icons.Default.FormatListNumbered, null, Modifier.size(16.dp)) })
        }

        Text("Providers (${providers.count { it.enabled }} active)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (loaded && providers.isEmpty()) Text("No provider yet. Tap Add provider. Brevo or Gmail API are the quickest to start.", style = MaterialTheme.typography.bodySmall)
        providers.forEachIndexed { index, p ->
            val info = mailInfo(p.type)
            val stat = live[p.id]
            val st = stat?.optJSONObject("stats")
            val cooling = stat?.optInt("coolingDownSec") ?: 0
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(
                            Brush.linearGradient(if (p.type.startsWith("gmail")) listOf(Color(0xFFEA4335), Color(0xFFFBBC05)) else listOf(Color(0xFF6C4DFF), Color(0xFF2D9CDB)))
                        ), contentAlignment = Alignment.Center
                    ) { Text(info.label.take(1), color = Color.White, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.label + if (p.id == "primary") "  ·  primary" else "", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(p.fromEmail.ifBlank { fromEmail }.ifBlank { "No sender e-mail" } + "  ·  free ${info.free}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        val line = when {
                            stat == null -> "Not loaded by the Worker yet (save, or redeploy the Worker)"
                            cooling > 0 -> "Resting ${cooling / 60 + 1} min after an error: ${st?.optString("lastError").orEmpty().take(70)}"
                            st != null -> "Today ${st.optInt("sentToday")} sent · ${st.optInt("failedToday")} failed · total ${st.optInt("sentTotal")}"
                            else -> "Ready"
                        }
                        Text(line, style = MaterialTheme.typography.labelSmall, color = if (cooling > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32), maxLines = 2)
                    }
                    Switch(p.enabled, { on -> providers[index] = p.copy(enabled = on) })
                    IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(enabled = !busy, onClick = { save(p.id) }) { Icon(Icons.Default.Send, "Test this provider") }
                }
            }
        }
        OutlinedButton(onClick = { editing = MailProviderEntry("p" + System.currentTimeMillis().toString().takeLast(7), "brevo") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add provider")
        }

        Text("Sender & branding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(fromEmail, { fromEmail = it.trim() }, label = { Text("Default From e-mail (verified sender)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fromName, { fromName = it.take(40) }, label = { Text("From / brand name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(replyTo, { replyTo = it.trim() }, label = { Text("Reply-To (optional, a real inbox helps deliverability)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(supportEmail, { supportEmail = it.trim() }, label = { Text("Support e-mail shown in the footer (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(brandUrl, { brandUrl = it.trim() }, label = { Text("Website link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sponsorLine, { sponsorLine = it.take(120) }, label = { Text("Sponsor / partner line (optional)") }, modifier = Modifier.fillMaxWidth())

        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF8E1)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Why codes land in Spam", fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
                Text(
                    "Sending from a @gmail.com address through Brevo/Resend fails Gmail's DMARC check, so Gmail marks it as spam. Fix: use your own domain with SPF + DKIM (+ DMARC) records from the provider, or add Gmail API so mail from your Gmail really comes from Gmail. Every e-mail now has a plain-text part, a Reply-To and a unique reference, and no tracking links.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D4037)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy && loaded, onClick = { save(null) }, modifier = Modifier.weight(1f)) { Text("Save") }
            Button(enabled = !busy && loaded && providers.any { it.enabled }, onClick = { save("") }, modifier = Modifier.weight(1f)) { Text(if (busy) "Working…" else "Save & test pool") }
        }
        if (status.isNotBlank()) Text(status, color = if (statusOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    editing?.let { entry ->
        MailProviderEditor(
            initial = entry,
            defaultFrom = fromEmail,
            isNew = providers.none { it.id == entry.id },
            onDismiss = { editing = null },
            onDelete = { providers.removeAll { it.id == entry.id }; editing = null },
            onSave = { updated ->
                val i = providers.indexOfFirst { it.id == updated.id }
                if (i >= 0) providers[i] = updated else providers += updated
                editing = null
            }
        )
    }
}

@Composable
private fun MailProviderEditor(initial: MailProviderEntry, defaultFrom: String, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (MailProviderEntry) -> Unit) {
    var e by remember(initial.id) { mutableStateOf(initial) }
    var showKey by remember { mutableStateOf(false) }
    val info = mailInfo(e.type)
    val hidden = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    val valid = (info.type == "gmail_sa" || e.apiKey.length > 8) &&
        (info.secretLabel == null || e.secret.isNotBlank()) && (info.domainLabel == null || e.domain.isNotBlank()) &&
        (info.clientIdLabel == null || e.clientId.isNotBlank()) && e.fromEmail.ifBlank { defaultFrom }.contains("@")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add e-mail provider" else "Edit ${info.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.id != "primary") {
                    Text("Provider", style = MaterialTheme.typography.labelLarge)
                    MAIL_PROVIDERS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { m ->
                                FilterChip(selected = e.type == m.type, onClick = { e = e.copy(type = m.type, region = m.regions.firstOrNull().orEmpty()) },
                                    label = { Text(m.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(info.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (info.type != "gmail_sa") OutlinedTextField(e.apiKey, { e = e.copy(apiKey = it.trim()) }, label = { Text(info.keyLabel) }, singleLine = true, visualTransformation = hidden,
                    trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }, modifier = Modifier.fillMaxWidth())
                info.clientIdLabel?.let { l -> OutlinedTextField(e.clientId, { e = e.copy(clientId = it.trim()) }, label = { Text(l) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                info.secretLabel?.let { l -> OutlinedTextField(e.secret, { e = e.copy(secret = it.trim()) }, label = { Text(l) }, singleLine = true, visualTransformation = hidden, modifier = Modifier.fillMaxWidth()) }
                info.domainLabel?.let { l -> OutlinedTextField(e.domain, { e = e.copy(domain = it.trim()) }, label = { Text(l) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                if (info.regions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.regions.forEach { r -> FilterChip(selected = e.region.ifBlank { info.regions.first() } == r, onClick = { e = e.copy(region = r) }, label = { Text(r.uppercase()) }) }
                }
                OutlinedTextField(e.fromEmail, { e = e.copy(fromEmail = it.trim()) }, label = { Text("From e-mail" + if (defaultFrom.isNotBlank()) " (blank = $defaultFrom)" else "") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(e.fromName, { e = e.copy(fromName = it.take(40)) }, label = { Text("From name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Share of traffic: ${e.weight}x", style = MaterialTheme.typography.labelLarge)
                Slider(e.weight.toFloat(), { e = e.copy(weight = it.toInt().coerceIn(1, 10)) }, valueRange = 1f..10f, steps = 8)
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { onSave(e) }) { Text(if (isNew) "Add" else "Done") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
            }
        }
    )
}
