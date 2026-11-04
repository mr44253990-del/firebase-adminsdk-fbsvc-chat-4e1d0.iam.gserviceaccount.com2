package com.example.ui

import android.Manifest
import android.content.Context
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.FlagshipConfig
import com.example.data.User
import com.example.data.WorkerRouter
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Phone number helpers shared by sign-in, profile linking and contact discovery. */
object PhoneNumbers {
    /** Best-effort E.164 ("+8801712345678") from what a user typed or a contact entry; "" if unusable. */
    fun normalize(raw: String, countryCode: String): String {
        val t = raw.trim()
        if (t.isBlank()) return ""
        val cc = countryCode.filter(Char::isDigit).ifBlank { "880" }
        var d = t.filter(Char::isDigit)
        if (d.isEmpty()) return ""
        d = when {
            t.startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.startsWith(cc) && d.length > cc.length + 6 -> d
            d.startsWith("0") -> cc + d.trimStart('0')
            else -> cc + d
        }
        return if (d.length in 8..15) "+$d" else ""
    }

    /** One-way hash; the raw number itself is never written to the public profile. */
    fun hash(e164: String): String {
        val digits = e164.filter(Char::isDigit)
        if (digits.length < 8) return ""
        return MessageDigest.getInstance("SHA-256").digest("convo-phone|$digits".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Which sign-in methods the admin enabled. Works before login: local cache first, then the public
 * Firestore doc `app_config/auth_public`, then the Worker `GET /public/auth-config`.
 */
object AuthMethodConfig {
    data class Methods(val phone: Boolean = false, val email: Boolean = true, val countryCode: String = "+880", val contacts: Boolean = true)

    private const val DEFAULT_WORKER = "https://solitary-hill-dcdc.mr44253990.workers.dev"
    private val _state = MutableStateFlow(Methods())
    val state: StateFlow<Methods> = _state.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("convo_auth_methods", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        _state.value = Methods(p.getBoolean("phone", false), p.getBoolean("email", true), p.getString("cc", "+880") ?: "+880", p.getBoolean("contacts", true))
    }

    private fun apply(ctx: Context, m: Methods) {
        val fixed = if (!m.phone && !m.email) m.copy(email = true) else m
        _state.value = fixed
        prefs(ctx).edit().putBoolean("phone", fixed.phone).putBoolean("email", fixed.email)
            .putString("cc", fixed.countryCode).putBoolean("contacts", fixed.contacts).apply()
    }

    /** Called by the ViewModel whenever the live flagship config arrives (signed in). */
    fun store(ctx: Context, cfg: FlagshipConfig) {
        apply(ctx, Methods(cfg.phoneAuthEnabled, cfg.emailAuthEnabled, cfg.defaultCountryCode.ifBlank { "+880" }, cfg.contactsDiscoveryEnabled))
        WorkerRouter.primary.value.takeIf { it.isNotBlank() }?.let { prefs(ctx).edit().putString("worker", it).apply() }
    }

    suspend fun refresh(ctx: Context) {
        load(ctx)
        val viaFirestore = runCatching {
            val doc = FirebaseFirestore.getInstance().collection("app_config").document("auth_public").get().awaitTask()
            if (doc.exists()) Methods(
                doc.getBoolean("phoneAuthEnabled") == true,
                doc.getBoolean("emailAuthEnabled") != false,
                doc.getString("defaultCountryCode")?.ifBlank { null } ?: "+880",
                _state.value.contacts
            ) else null
        }.getOrNull()
        if (viaFirestore != null) { apply(ctx, viaFirestore); return }
        val viaWorker = withContext(Dispatchers.IO) {
            runCatching {
                val base = WorkerRouter.primary.value.ifBlank { prefs(ctx).getString("worker", "").orEmpty() }.ifBlank { DEFAULT_WORKER }.trimEnd('/')
                OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
                    .newCall(Request.Builder().url("$base/public/auth-config").get().build()).execute().use { r ->
                        if (!r.isSuccessful) null else JSONObject(r.body?.string().orEmpty()).let { j ->
                            if (!j.has("phoneAuthEnabled")) null else Methods(
                                j.optBoolean("phoneAuthEnabled", false), j.optBoolean("emailAuthEnabled", true),
                                j.optString("defaultCountryCode").ifBlank { "+880" }, _state.value.contacts
                            )
                        }
                    }
            }.getOrNull()
        }
        if (viaWorker != null) apply(ctx, viaWorker)
    }
}

private fun Context.phoneAuthActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun friendlyPhoneError(e: Exception): String {
    val m = e.message.orEmpty()
    return when {
        m.contains("format", true) || m.contains("invalid phone", true) || m.contains("TOO_SHORT", true) || m.contains("TOO_LONG", true) -> "That phone number doesn't look right. Check the country code."
        m.contains("blocked", true) || m.contains("unusual activity", true) || m.contains("too many", true) -> "Too many attempts from this device. Please wait a while and try again."
        m.contains("quota", true) -> "SMS limit reached for today. Please try again later."
        m.contains("not authorized", true) || m.contains("app identifier", true) || m.contains("Play Integrity", true) || m.contains("SHA", true) || m.contains("missing a valid app", true) ->
            "Phone sign-in is not set up for this app build yet. Admin: enable Phone in Firebase Authentication and add the app's SHA-1 / SHA-256."
        m.contains("network", true) -> "No connection. Check your internet and try again."
        else -> m.ifBlank { "Could not send the code" }
    }
}

/**
 * Self-contained SMS verification flow: number -> code -> [onCredential]. Used for phone sign-in
 * and for adding a verified number to an existing account.
 */
@Composable
fun PhoneVerifyPanel(
    defaultCountryCode: String,
    askName: Boolean,
    accent: Color,
    textColor: Color,
    softColor: Color,
    fieldColors: TextFieldColors,
    busy: Boolean,
    submitLabel: String,
    externalError: String?,
    onCredential: (PhoneAuthCredential, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cc by rememberSaveable { mutableStateOf(defaultCountryCode.ifBlank { "+880" }) }
    var number by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var sentTo by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); countdown -= 1 } }

    fun send(resend: Boolean) {
        val e164 = PhoneNumbers.normalize(number, cc)
        if (e164.isBlank()) { error = "Enter a valid phone number"; return }
        if (askName && name.isBlank()) { error = "Enter your name"; return }
        val activity = context.phoneAuthActivity() ?: run { error = "Open the app screen and try again"; return }
        error = null; sending = true
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant verification / SMS auto-retrieval: no typing needed.
                sending = false
                credential.smsCode?.let { code = it }
                onCredential(credential, e164, name.trim())
            }
            override fun onVerificationFailed(e: FirebaseException) {
                sending = false
                error = friendlyPhoneError(e)
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                sending = false
                verificationId = id
                resendToken = token
                sentTo = e164
                countdown = 60
                Toast.makeText(context, "Code sent to $e164", Toast.LENGTH_SHORT).show()
            }
        }
        runCatching {
            val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(e164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            val token = resendToken
            if (resend && token != null) builder.setForceResendingToken(token)
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }.onFailure { sending = false; error = friendlyPhoneError(it as? Exception ?: Exception(it.message)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (verificationId == null) {
            if (askName) OutlinedTextField(
                name, { name = it.take(40) }, label = { Text(tr("Your name")) }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, null, tint = accent) },
                colors = fieldColors, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    cc, { v -> cc = ("+" + v.filter(Char::isDigit)).take(5) }, label = { Text(tr("Code")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(92.dp)
                )
                OutlinedTextField(
                    number, { v -> number = v.filter { it.isDigit() || it == ' ' || it == '-' }.take(18) }, label = { Text(tr("Phone number")) }, singleLine = true,
                    placeholder = { Text("1712 345678", color = softColor) },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, tint = accent) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                )
            }
            Text(tr("We'll send a 6-digit code by SMS. Carrier rates may apply."), color = softColor, fontSize = 11.sp)
            GradientActionButton(if (sending) tr("Sending code…") else tr("Send code"), accent, enabled = !sending && !busy, loading = sending) { send(false) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Sms, null, tint = accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Enter the code"), color = textColor, fontWeight = FontWeight.Bold)
                    Text(tr("Sent to") + " $sentTo", color = softColor, fontSize = 12.sp)
                }
                TextButton(onClick = { verificationId = null; code = ""; error = null }) { Text(tr("Change"), color = accent) }
            }
            OutlinedTextField(
                code, { v -> code = v.filter(Char::isDigit).take(6) }, label = { Text(tr("6-digit code")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Bold, color = textColor),
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        val otp = clip.filter(Char::isDigit).take(6)
                        if (otp.isNotBlank()) code = otp
                    }) { Icon(Icons.Default.ContentPaste, "Paste verification code", tint = accent) }
                },
                colors = fieldColors, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            GradientActionButton(if (busy) tr("Verifying…") else submitLabel, accent, enabled = code.length == 6 && !busy, loading = busy) {
                val id = verificationId ?: return@GradientActionButton
                onCredential(PhoneAuthProvider.getCredential(id, code), sentTo, name.trim())
            }
            TextButton(onClick = { send(true) }, enabled = countdown == 0 && !sending, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (countdown > 0) tr("Resend code in") + " ${countdown}s" else tr("Resend code"), color = if (countdown > 0) softColor else accent)
            }
        }
        val shown = error ?: externalError
        AnimatedVisibility(shown != null, enter = fadeIn(), exit = fadeOut()) {
            Text(shown.orEmpty(), color = Color(0xFFFF6B81), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GradientActionButton(label: String, accent: Color, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(Color(0xFF1F8CFF), Color(0xFF8C3DFF), accent)) else Brush.horizontalGradient(listOf(Color.Gray.copy(.35f), Color.Gray.copy(.25f))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Contacts who have a Convo account (matched by phone hash). Cached so the chat list shows them instantly. */
object ContactMatches {
    private val _uids = MutableStateFlow<Set<String>>(emptySet())
    val uids: StateFlow<Set<String>> = _uids.asStateFlow()
    @Volatile private var loaded = false
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("convo_contacts", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        _uids.value = prefs(ctx).getStringSet("uids", emptySet()).orEmpty().toSet()
    }

    fun hasPermission(ctx: Context) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun dismissed(ctx: Context) = prefs(ctx).getBoolean("dismissed", false)
    fun setDismissed(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("dismissed", v).apply()
    fun lastSync(ctx: Context) = prefs(ctx).getLong("synced", 0L)

    fun clear(ctx: Context) { _uids.value = emptySet(); prefs(ctx).edit().remove("uids").apply() }

    /** Reads the address book on-device, hashes every number and asks Firestore which hashes exist. */
    suspend fun sync(ctx: Context, countryCode: String, myUid: String): Int = withContext(Dispatchers.IO) {
        if (!hasPermission(ctx)) return@withContext 0
        val hashes = LinkedHashSet<String>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext() && hashes.size < 3000) {
                val raw = c.getString(1)?.takeIf { it.isNotBlank() } ?: c.getString(0).orEmpty()
                PhoneNumbers.normalize(raw, countryCode).takeIf { it.isNotBlank() }?.let { hashes += PhoneNumbers.hash(it) }
            }
        }
        val found = LinkedHashSet<String>()
        val db = FirebaseFirestore.getInstance()
        for (chunk in hashes.filter { it.isNotBlank() }.chunked(30)) {
            runCatching {
                db.collection("users").whereIn("phoneHash", chunk).get().awaitTask().documents
                    .mapNotNull { it.id.takeIf { id -> id != myUid } }.forEach { found += it }
            }
        }
        _uids.value = found
        prefs(ctx).edit().putStringSet("uids", found).putLong("synced", System.currentTimeMillis()).apply()
        found.size
    }
}

/**
 * Messages-tab card: asks for the contacts permission, shows contacts already on Convo Chat and
 * lets the user add their own verified number so friends can find them.
 */
@Composable
fun ContactsDiscoveryCard(viewModel: ChatViewModel, users: List<User>, onSelect: (User) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by viewModel.flagshipConfig.collectAsState()
    val me by viewModel.currentUserState.collectAsState()
    val matches by ContactMatches.uids.collectAsState()
    LaunchedEffect(Unit) { ContactMatches.load(context) }
    if (!config.contactsDiscoveryEnabled) return
    var dismissed by remember { mutableStateOf(ContactMatches.dismissed(context)) }
    var granted by remember { mutableStateOf(ContactMatches.hasPermission(context)) }
    var syncing by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    val myUid = me?.uid.orEmpty()
    fun runSync() {
        if (myUid.isBlank()) return
        syncing = true
        scope.launch {
            val n = runCatching { ContactMatches.sync(context, config.defaultCountryCode, myUid) }.getOrDefault(0)
            syncing = false
            Toast.makeText(context, if (n == 0) "None of your contacts are on Convo Chat yet" else "$n contacts are on Convo Chat", Toast.LENGTH_SHORT).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) runSync() else Toast.makeText(context, "Contacts permission is needed to find friends", Toast.LENGTH_SHORT).show()
    }
    // Quiet background refresh once a day while permission is granted.
    LaunchedEffect(granted, myUid) {
        if (granted && myUid.isNotBlank() && System.currentTimeMillis() - ContactMatches.lastSync(context) > 24L * 3_600_000L) {
            runCatching { ContactMatches.sync(context, config.defaultCountryCode, myUid) }
        }
    }
    val matchedUsers = remember(matches, users) { users.filter { it.uid in matches }.distinctBy { it.uid } }
    val needsNumber = me != null && me?.phoneHash.isNullOrBlank()
    if (dismissed && !needsNumber && matchedUsers.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF22D3EE).copy(.55f), Color(0xFF8B5CF6).copy(.45f)))),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF8B5CF6)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Contacts, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (matchedUsers.isNotEmpty()) tr("From your contacts") else tr("Find friends from contacts"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (matchedUsers.isNotEmpty()) "${matchedUsers.size} " + tr("people you know are on Convo Chat") else tr("Numbers are matched privately as one-way hashes"),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (matchedUsers.isEmpty()) IconButton(onClick = { dismissed = true; ContactMatches.setDismissed(context, true) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, tr("Hide"), modifier = Modifier.size(16.dp))
                }
            }
            if (matchedUsers.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(matchedUsers, key = { it.uid }) { u ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { onSelect(u) }) {
                            AsyncImage(
                                model = u.profileImageUrl.ifBlank { null }, contentDescription = u.name, contentScale = ContentScale.Crop,
                                error = androidx.compose.ui.res.painterResource(com.example.R.drawable.img_app_logo),
                                modifier = Modifier.size(50.dp).clip(CircleShape).border(2.dp, Color(0xFF22D3EE), CircleShape)
                            )
                            Text(u.name.substringBefore(' '), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!granted) Button(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(tr("Allow contacts"), fontSize = 12.sp)
                } else OutlinedButton(onClick = { runSync() }, enabled = !syncing, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    if (syncing) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Text(if (syncing) tr("Checking…") else tr("Refresh"), fontSize = 12.sp)
                }
                if (needsNumber) OutlinedButton(onClick = { showLink = true }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                    Text(tr("Add my number"), fontSize = 12.sp)
                }
            }
        }
    }

    if (showLink) LinkPhoneDialog(viewModel, config.defaultCountryCode) { showLink = false }
}

/** Verifies a number by SMS and links it to the signed-in account (so contacts can find it). */
@Composable
fun LinkPhoneDialog(viewModel: ChatViewModel, defaultCountryCode: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val accent = Color(0xFF22D3EE)
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Add your phone number"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(tr("Friends who have your number saved will see you in their messages. Your number is stored only as a private hash."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PhoneVerifyPanel(
                    defaultCountryCode = defaultCountryCode, askName = false, accent = accent,
                    textColor = MaterialTheme.colorScheme.onSurface, softColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fieldColors = OutlinedTextFieldDefaults.colors(), busy = busy, submitLabel = tr("Verify & add"), externalError = error
                ) { credential, e164, _ ->
                    val user = FirebaseAuth.getInstance().currentUser ?: return@PhoneVerifyPanel
                    busy = true; error = null
                    val done: () -> Unit = {
                        viewModel.savePhoneHash(e164) { ok ->
                            busy = false
                            if (ok) { Toast.makeText(context, "Number added", Toast.LENGTH_SHORT).show(); onDismiss() } else error = "Could not save. Try again."
                        }
                    }
                    if (user.phoneNumber == e164) done()
                    else user.updatePhoneNumber(credential)
                        .addOnSuccessListener { done() }
                        .addOnFailureListener { e ->
                            busy = false
                            error = if (e.message?.contains("already", true) == true) "This number already belongs to another account." else friendlyPhoneError(e)
                        }
                }
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text(tr("Close")) }
            }
        }
    }
}
