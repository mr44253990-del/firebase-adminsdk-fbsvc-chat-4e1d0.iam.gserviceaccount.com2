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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
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
    suspend fun requestGoogleCredential(): Credential {
        val serverClientId = context.getString(R.string.default_web_client_id)
        require(serverClientId.isNotBlank()) { "Missing default_web_client_id; download a fresh google-services.json" }
        val explicit = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build()).build()
        return try {
            credentialManager.getCredential(context, explicit).credential
        } catch (explicitError: Exception) {
            // Some OEM Credential Manager providers do not support the explicit button option.
            // Fall back to the broad account chooser without filtering authorized accounts.
            val fallback = GetCredentialRequest.Builder().addCredentialOption(
                GetGoogleIdOption.Builder().setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
            ).build()
            credentialManager.getCredential(context, fallback).credential
        }
    }
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val isFirebaseConfigured by viewModel.isFirebaseConfigured.collectAsState()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    
    // Profile Picture fields for Sign Up
    var signupProfilePicUrl by remember { mutableStateOf("") }
    var isUploadingPic by remember { mutableStateOf(false) }

    // Forgot Password states
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
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

    // Gallery Picker for Profile Pic
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPic = true
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val fileExtension = "jpg"
                    val tempFileName = "signup_${UUID.randomUUID()}_${System.currentTimeMillis()}.$fileExtension"
                    
                    viewModel.uploadFileToSupabase(
                        bucket = "avatars",
                        fileName = tempFileName,
                        fileBytes = bytes,
                        contentType = "image/jpeg",
                        onSuccess = { publicUrl ->
                            isUploadingPic = false
                            signupProfilePicUrl = publicUrl
                            Toast.makeText(context, "Profile picture selected successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { errorMsg ->
                            isUploadingPic = false
                            Toast.makeText(context, "Upload failed: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    isUploadingPic = false
                }
            } catch (e: Exception) {
                isUploadingPic = false
                Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
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

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            color1.copy(alpha = 0.25f),
            color2.copy(alpha = 0.15f),
            color3.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Illustration Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.1f))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_chat_banner),
                    contentDescription = "Hero chat banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Ambient gradient overlay over the hero
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
                // Brand tag over image
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "🔒 Secure Chat",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isLoginMode) "Welcome Back" else "Create Account",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isLoginMode) "Sign in to continue your secure conversations" else "Join us to start chatting with verified profiles securely",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Custom Styled Tabs
            TabRow(
                selectedTabIndex = if (isLoginMode) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .padding(bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[if (isLoginMode) 0 else 1]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = isLoginMode,
                    onClick = { isLoginMode = true },
                    text = { 
                        Text(
                            text = "Log In", 
                            fontWeight = if (isLoginMode) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 15.sp
                        ) 
                    },
                    modifier = Modifier.testTag("login_tab")
                )
                Tab(
                    selected = !isLoginMode,
                    onClick = { isLoginMode = false },
                    text = { 
                        Text(
                            text = "Sign Up", 
                            fontWeight = if (!isLoginMode) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 15.sp
                        ) 
                    },
                    modifier = Modifier.testTag("signup_tab")
                )
            }

            // Input Fields Card with neon animated border
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(color1, color2, color3)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
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
                                    .testTag("signup_name_input")
                            )

                            // Birth date button picker
                            OutlinedTextField(
                                value = dob,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Date of Birth (YYYY-MM-DD)") },
                                placeholder = { Text("Select Date") },
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
                                    .testTag("signup_dob_input")
                            )
                        }
                    }

                    // Email field (for both login and signup)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("example@domain.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    // Password field (for both login and signup as requested)
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isLoginMode) "Password" else "Choose Password") },
                        placeholder = { Text("At least 6 characters") },
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
                            .testTag("password_input")
                    )

                    // Forgot password trigger (only in login mode)
                    if (isLoginMode) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
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

                    // Submission Button
                    Button(
                        onClick = {
                            if (isLoginMode) {
                                viewModel.login(email, password, onAuthSuccess)
                            } else {
                                viewModel.signup(email, name, dob, password, signupProfilePicUrl, onAuthSuccess)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = !authLoading && !isUploadingPic
                    ) {
                        if (authLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                              ) {
                                Text(
                                    text = if (isLoginMode) "Sign In" else "Sign Up & Register",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
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

            Spacer(modifier = Modifier.height(20.dp))

            // Switch Mode footer helper
            TextButton(
                onClick = { isLoginMode = !isLoginMode },
                modifier = Modifier.testTag("switch_mode_button")
            ) {
                Text(
                    text = if (isLoginMode) "Don't have an account? Sign Up" else "Already have an account? Log In",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
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
                        label = { Text("Email Address") },
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
                        Text("Send Reset Link")
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
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        )
    }
}
