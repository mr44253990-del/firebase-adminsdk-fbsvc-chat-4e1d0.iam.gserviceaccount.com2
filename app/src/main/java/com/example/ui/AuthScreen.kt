package com.example.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: ChatViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    suspend fun requestGoogleCredential(preferredEmail: String? = null): Credential {
        val serverClientId = context.getString(R.string.default_web_client_id)
        require(serverClientId.isNotBlank()) { "Missing default_web_client_id; download a fresh google-services.json" }
        if (preferredEmail.isNullOrBlank()) {
            val explicit = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build()).build()
            return try {
                credentialManager.getCredential(context, explicit).credential
            } catch (explicitError: Exception) {
                val fallback = GetCredentialRequest.Builder().addCredentialOption(
                    GetGoogleIdOption.Builder().setServerClientId(serverClientId)
                        .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
                ).build()
                credentialManager.getCredential(context, fallback).credential
            }
        }
        val hinted = GetCredentialRequest.Builder().addCredentialOption(
            GetGoogleIdOption.Builder().setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
        ).build()
        return credentialManager.getCredential(context, hinted).credential
    }
    // Google Password Manager: after a successful email/password login or sign-up, offer to
    // save the credential; navigation waits for the system sheet so it is not cancelled.
    fun savePasswordThen(savedEmail: String, savedPassword: String, then: () -> Unit) {
        val activity = context.findActivityForCredentials()
        if (activity == null || savedEmail.isBlank() || savedPassword.isBlank()) { then(); return }
        scope.launch {
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(90_000L) {
                    credentialManager.createCredential(activity, CreatePasswordRequest(id = savedEmail.trim(), password = savedPassword))
                }
            }.onFailure { android.util.Log.d("AUTH", "Password save skipped: ${it.javaClass.simpleName}") }
            then()
        }
    }
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val isFirebaseConfigured by viewModel.isFirebaseConfigured.collectAsState()
    val rememberedAccounts by viewModel.rememberedAccounts.collectAsState()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var savedPasswordOffered by rememberSaveable { mutableStateOf(false) }
    // Quick login: offer passwords already saved in Google Password Manager (no UI when none).
    fun offerSavedPasswords(silentIfNone: Boolean) {
        val activity = context.findActivityForCredentials() ?: return
        scope.launch {
            runCatching {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetPasswordOption())
                    .setPreferImmediatelyAvailableCredentials(silentIfNone)
                    .build()
                val credential = credentialManager.getCredential(activity, request).credential
                if (credential is PasswordCredential) {
                    email = credential.id
                    password = credential.password
                    viewModel.login(credential.id, credential.password, onAuthSuccess)
                }
            }.onFailure {
                if (!silentIfNone) Toast.makeText(context, "No saved passwords for Convo Chat yet", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!savedPasswordOffered) { savedPasswordOffered = true; offerSavedPasswords(silentIfNone = true) }
    }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    
    // Profile Picture fields for Sign Up
    var signupProfilePicUrl by remember { mutableStateOf("") }
    var signupProfilePicBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isUploadingPic by remember { mutableStateOf(false) }

    // Forgot Password states
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var showBackupRecovery by remember { mutableStateOf(false) }
    var resetLoading by remember { mutableStateOf(false) }
    var resetMessage by remember { mutableStateOf<String?>(null) }
    var resetError by remember { mutableStateOf<String?>(null) }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            dob = formattedDate
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Hold the optional signup photo locally until Firebase creates the account. The
    // authenticated storage Worker must never accept an anonymous service-role upload.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPic = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    signupProfilePicBytes = bytes
                    signupProfilePicUrl = uri.toString()
                    Toast.makeText(context, "Profile photo ready; it will upload securely after account creation.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not read the selected image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isUploadingPic = false
            }
        }
    }

    // Dynamic multi-color animated gradient that cycles beautifully
    val infiniteTransition = rememberInfiniteTransition(label = "gradient_colors")
    
    val color1 by infiniteTransition.animateColor(
        initialValue = Color(0xFF673AB7), // Deep Violet
        targetValue = Color(0xFFE91E63),  // Warm Magenta
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = Color(0xFF00BCD4), // Neon Teal/Cyan
        targetValue = Color(0xFFFF9800),  // Sunset Gold/Orange
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color2"
    )
    val color3 by infiniteTransition.animateColor(
        initialValue = Color(0xFF3F51B5), // Indigo
        targetValue = Color(0xFF9C27B0),  // Deep Purple
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color3"
    )

    val loginPrefs = remember { context.getSharedPreferences("convo_login", android.content.Context.MODE_PRIVATE) }
    var milkLook by remember { mutableStateOf(loginPrefs.getBoolean("milk_look", false)) }
    val lp = remember(milkLook) { loginPalette(milkLook) }
    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = color1,
        unfocusedBorderColor = lp.text.copy(alpha = .18f),
        focusedLabelColor = color1,
        unfocusedLabelColor = lp.textSoft,
        cursorColor = color1,
        focusedTextColor = lp.text,
        unfocusedTextColor = lp.text,
        focusedPlaceholderColor = lp.textSoft,
        unfocusedPlaceholderColor = lp.textSoft,
        focusedContainerColor = lp.text.copy(alpha = .035f),
        unfocusedContainerColor = lp.text.copy(alpha = .025f)
    )
    val orbTransition = rememberInfiniteTransition(label = "auth_orbs")
    val orbDrift by orbTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb_drift"
    )
    val orbPulse by orbTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb_pulse"
    )

    val startGoogle: () -> Unit = {
        scope.launch {
            try {
                val credential = requestGoogleCredential()
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogleCredential(GoogleAuthProvider.getCredential(googleToken.idToken, null), onAuthSuccess)
                } else Toast.makeText(context, "Unsupported Google credential", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val detail = e.localizedMessage.orEmpty()
                Toast.makeText(context, if (detail.contains("developer", true)) "Google OAuth SHA/package configuration is missing" else detail.ifBlank { "Google sign-in was cancelled" }, Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Calm aurora light-field background (no tilt / 3D).
        LoginAuroraBackground(lp.milk)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(lp.text.copy(alpha = .07f))
                        .border(1.dp, lp.text.copy(alpha = .12f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, tint = ConvoBrand.Cyan, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(tr("Private & secure"), color = lp.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                LoginLookToggle(lp, onToggle = {
                    milkLook = !milkLook
                    loginPrefs.edit().putBoolean("milk_look", milkLook).apply()
                })
            }
            Spacer(Modifier.height(6.dp))
            // Code-drawn brand mark (vector-sharp, no bitmap) with a soft orbit glow.
            LoginBrandHero(lp.text, lp.milk)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isLoginMode) "Welcome back" else "Create your account",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = lp.text
            )

            Text(
                text = if (isLoginMode) "Sign in to continue your secure conversations" else "Join us to start chatting with verified profiles securely",
                style = MaterialTheme.typography.bodyMedium,
                color = lp.textSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Firebase Configuration Warning banner
            if (!isFirebaseConfigured) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Configuration Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Firebase Unconfigured",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Please upload a valid 'google-services.json' to Firebase. (Running in warning mode)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Animated glass segmented control matching the supplied neon reference.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(lp.text.copy(alpha = 0.07f))
                    .border(1.dp, lp.text.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(4.dp)
            ) {
                val tabWidth = maxWidth / 2
                val pillOffset by animateDpAsState(
                    targetValue = if (isLoginMode) 0.dp else tabWidth,
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                    label = "auth_tab_pill"
                )
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .fillMaxHeight()
                        .offset(x = pillOffset)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1F8CFF), Color(0xFF8C3DFF))))
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { isLoginMode = true }
                            .testTag("login_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("Log In"), color = lp.text, fontWeight = if (isLoginMode) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 15.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { isLoginMode = false }
                            .testTag("signup_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("Sign Up"), color = lp.text.copy(alpha = if (isLoginMode) .64f else 1f), fontWeight = if (!isLoginMode) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 15.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Sign-in method switch (admin decides which methods exist).
            val methods by AuthMethodConfig.state.collectAsState()
            LaunchedEffect(Unit) { AuthMethodConfig.load(context); runCatching { AuthMethodConfig.refresh(context) } }
            var preferPhone by rememberSaveable { mutableStateOf(false) }
            val usePhone = methods.phone && (preferPhone || !methods.email)
            if (methods.phone && methods.email) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(lp.text.copy(alpha = .05f))
                        .border(1.dp, lp.text.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to tr("E-mail"), true to tr("Phone number")).forEach { (phone, label) ->
                        val selected = usePhone == phone
                        Row(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Brush.horizontalGradient(listOf(ConvoBrand.Cyan.copy(alpha = .30f), ConvoBrand.Pink.copy(alpha = .26f))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                                .border(1.dp, if (selected) lp.text.copy(alpha = .22f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { preferPhone = phone; viewModel.clearAuthError() },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (phone) Icons.Default.PhoneAndroid else Icons.Default.Email, null, tint = if (selected) lp.text else lp.textSoft, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = if (selected) lp.text else lp.textSoft, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (usePhone) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().border(
                        1.2.dp,
                        Brush.linearGradient(if (lp.milk) lp.cardBorder else listOf(ConvoBrand.Cyan.copy(alpha = .7f), Color.White.copy(alpha = .12f), ConvoBrand.Pink.copy(alpha = .7f))),
                        RoundedCornerShape(24.dp)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = lp.card),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (isLoginMode) tr("Sign in with your phone") else tr("Sign up with your phone"), color = lp.text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        PhoneVerifyPanel(
                            defaultCountryCode = methods.countryCode,
                            askName = !isLoginMode,
                            accent = color1,
                            textColor = lp.text,
                            softColor = lp.textSoft,
                            fieldColors = authFieldColors,
                            busy = authLoading,
                            submitLabel = if (isLoginMode) tr("Verify & sign in") else tr("Verify & create account"),
                            externalError = authError
                        ) { credential, e164, enteredName ->
                            viewModel.signInWithPhoneCredential(credential, enteredName, e164, onAuthSuccess)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("  OR  ", color = lp.textSoft, fontSize = 11.sp)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        OutlinedButton(onClick = startGoogle, enabled = !authLoading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                            Text("G", fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4), fontSize = 19.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(tr("Continue with Google"), fontWeight = FontWeight.Bold, color = lp.text)
                        }
                    }
                }
            } else {
            // Input Fields Card with neon animated border
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = if (lp.milk) lp.cardBorder else listOf(ConvoBrand.Cyan.copy(alpha = .7f), Color.White.copy(alpha = .12f), ConvoBrand.Pink.copy(alpha = .7f))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = lp.card
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(
                        visible = !isLoginMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Add a profile photo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Profile picture selector bubble as requested
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clickable { galleryLauncher.launch("image/*") }
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (signupProfilePicUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = signupProfilePicUrl,
                                        contentDescription = "Profile picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(86.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(86.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.AddAPhoto,
                                                contentDescription = "Profile picture",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Add photo",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                
                                if (isUploadingPic) {
                                    Box(
                                        modifier = Modifier
                                            .size(86.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Sign up Name
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Your Name") },
                                placeholder = { Text("John Doe") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                singleLine = true,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_name_input"),
                                colors = authFieldColors
                            )

                            // Birth date button picker
                            OutlinedTextField(
                                value = dob,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Date of Birth (YYYY-MM-DD)") },
                                placeholder = { Text(tr("Select Date")) },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    IconButton(onClick = { datePickerDialog.show() }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_dob_input"),
                                colors = authFieldColors
                            )
                        }
                    }

                    // Email field (for both login and signup)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(tr("Email Address")) },
                        placeholder = { Text("example@domain.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.EmailAddress + ContentType.Username }
                            .testTag("email_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = color1,
                            unfocusedBorderColor = lp.text.copy(alpha = .18f),
                            focusedLabelColor = color1,
                            unfocusedLabelColor = lp.textSoft,
                            cursorColor = color1,
                            focusedTextColor = lp.text,
                            unfocusedTextColor = lp.text,
                            focusedContainerColor = lp.text.copy(alpha = .035f),
                            unfocusedContainerColor = lp.text.copy(alpha = .025f)
                        )
                    )

                    // Password field (for both login and signup as requested)
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isLoginMode) "Password" else "Choose Password") },
                        placeholder = { Text(tr("At least 6 characters")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (passwordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = if (isLoginMode) ContentType.Password else ContentType.NewPassword }
                            .testTag("password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = color2,
                            unfocusedBorderColor = lp.text.copy(alpha = .18f),
                            focusedLabelColor = color2,
                            unfocusedLabelColor = lp.textSoft,
                            cursorColor = color2,
                            focusedTextColor = lp.text,
                            unfocusedTextColor = lp.text,
                            focusedContainerColor = lp.text.copy(alpha = .035f),
                            unfocusedContainerColor = lp.text.copy(alpha = .025f)
                        )
                    )

                    // Forgot password trigger (only in login mode)
                    if (isLoginMode) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            // Saved-password picker: icon only (no label); same Credential Manager flow.
                            IconButton(
                                onClick = { offerSavedPasswords(silentIfNone = false) },
                                modifier = Modifier.align(Alignment.CenterStart).size(40.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = "Saved passwords", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .8f))
                            }
                            TextButton(
                                onClick = { showForgotPasswordDialog = true },
                                modifier = Modifier.testTag("forgot_password_button")
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Error presentation
                    AnimatedVisibility(
                        visible = authError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = authError ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Gradient CTA with a soft animated neon edge.
                    val ctaGlow by infiniteTransition.animateFloat(
                        initialValue = .28f,
                        targetValue = .62f,
                        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "auth_cta_glow"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF168CFF), Color(0xFF7B3DFF), Color(0xFFFF3D9A))))
                            .border(1.dp, Color.White.copy(alpha = ctaGlow), RoundedCornerShape(22.dp))
                            .clickable(enabled = !authLoading && !isUploadingPic) {
                                if (isLoginMode) {
                                    val e = email; val pw = password
                                    viewModel.login(e, pw) { savePasswordThen(e, pw, onAuthSuccess) }
                                } else {
                                    val e = email; val pw = password
                                    viewModel.signup(
                                        email, name, dob, password,
                                        if (signupProfilePicBytes == null) signupProfilePicUrl else "",
                                        { savePasswordThen(e, pw, onAuthSuccess) },
                                        profileImageBytes = signupProfilePicBytes
                                    )
                                }
                            }
                            .testTag("submit_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (authLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text(
                                    text = if (isLoginMode) "Sign In" else "Sign Up & Register",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text("  OR  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        HorizontalDivider(Modifier.weight(1f))
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val credential = requestGoogleCredential()
                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                                        viewModel.signInWithGoogleCredential(
                                            GoogleAuthProvider.getCredential(googleToken.idToken, null),
                                            onAuthSuccess
                                        )
                                    } else {
                                        Toast.makeText(context, "Unsupported Google credential", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    val detail = e.localizedMessage.orEmpty()
                                    val message = when {
                                        detail.contains("10") || detail.contains("developer", true) -> "Google OAuth SHA/package configuration is missing"
                                        detail.contains("credential", true) -> "No Google account credential is available on this device"
                                        detail.isNotBlank() -> detail
                                        else -> "Google sign-in was cancelled"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !authLoading,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = CircleShape
                    ) {
                        Text("G", fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4), fontSize = 19.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(if (isLoginMode) "Continue with Google" else "Sign up with Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Switch Mode footer helper
            TextButton(
                onClick = { isLoginMode = !isLoginMode },
                modifier = Modifier.testTag("switch_mode_button")
            ) {
                Text(text = if (isLoginMode) "Don't have an account? Sign Up" else "Already have an account? Log In", color = lp.link, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Surface(
                color = lp.text.copy(alpha = .065f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Encrypted",
                        tint = lp.highlight.copy(alpha = orbPulse),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("End-to-end encrypted"), color = lp.textSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (rememberedAccounts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(tr("Previous accounts"), color = lp.text, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth())
                Text(
                    "Tap a saved account to continue. Secure credentials stay with Android and are never written to app files.",
                    fontSize = 10.sp,
                    color = lp.textSoft,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rememberedAccounts.forEach { account ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                isLoginMode = true
                                email = account.email
                                if (account.provider == "google") {
                                    scope.launch {
                                        runCatching { requestGoogleCredential(account.email) }
                                            .onSuccess { credential ->
                                                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                                                    viewModel.signInWithGoogleCredential(
                                                        GoogleAuthProvider.getCredential(google.idToken, null),
                                                        onAuthSuccess
                                                    )
                                                }
                                            }
                                            .onFailure {
                                                Toast.makeText(context, it.localizedMessage ?: "Google login failed", Toast.LENGTH_LONG).show()
                                            }
                                    }
                                } else {
                                    Toast.makeText(context, "Signing in securely…", Toast.LENGTH_SHORT).show()
                                    viewModel.loginRememberedAccount(account, onAuthSuccess)
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = lp.text.copy(alpha = 0.065f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = account.photoUrl.ifBlank { null },
                                    contentDescription = account.name,
                                    error = painterResource(R.drawable.img_app_logo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(account.name.ifBlank { account.email }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(account.email, fontSize = 11.sp, color = lp.textSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(account.provider.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = lp.highlight)
                                IconButton(onClick = { viewModel.forgetRememberedAccount(account.uid) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove account", tint = lp.textSoft, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBackupRecovery) {
        BackupCodeRecoveryDialog(initialEmail = resetEmail.ifBlank { email }, onDismiss = { showBackupRecovery = false })
    }
    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showForgotPasswordDialog = false
                resetEmail = ""
                resetMessage = null
                resetError = null
            },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your registered email address below, and we'll send you a link to reset your password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(tr("Email Address")) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (resetMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resetMessage!!,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    if (resetError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resetError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    TextButton(onClick = { showBackupRecovery = true; showForgotPasswordDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("No e-mail access? Use a backup code")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetLoading = true
                        resetError = null
                        resetMessage = null
                        viewModel.resetPassword(
                            email = resetEmail,
                            onSuccess = {
                                resetLoading = false
                                resetMessage = "Reset email sent! Please check your inbox."
                            },
                            onFailure = { error ->
                                resetLoading = false
                                resetError = error
                            }
                        )
                    },
                    enabled = !resetLoading && resetEmail.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (resetLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(tr("Send Reset Link"))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showForgotPasswordDialog = false
                        resetEmail = ""
                        resetMessage = null
                        resetError = null
                    }
                ) {
                    Text(tr("Cancel"))
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        )
    }
}


private fun android.content.Context.findActivityForCredentials(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
