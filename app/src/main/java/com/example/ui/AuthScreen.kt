package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    viewModel: ChatViewModel,
    onAuthSuccess: () -> Unit
) {
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val isFirebaseConfigured by viewModel.isFirebaseConfigured.collectAsState()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> photoUri = uri }

    GlassBackground(bubbleCount = 18) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ---- Animated glowing logo ----
            AnimateIn {
                PulsingLogo()
            }

            Spacer(Modifier.height(18.dp))

            AnimateIn(delayMillis = 120) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GradientText("FireChat", MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect · Share · Vibe",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ---- Glass auth card ----
            AnimateIn(delayMillis = 220) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp)) {
                        // Segmented toggle
                        AuthToggle(isLoginMode) { isLoginMode = it }

                        Spacer(Modifier.height(22.dp))

                        AnimatedContent(
                            targetState = isLoginMode,
                            transitionSpec = {
                                (slideInHorizontally { if (targetState) -it else it } + fadeIn(tween(280)))
                                    .togetherWith(slideOutHorizontally { if (targetState) it else -it } + fadeOut(tween(220)))
                            },
                            label = "authSwitch"
                        ) { loginMode ->
                            Column {
                                if (!loginMode) {
                                    // Avatar picker (saved to Supabase on signup)
                                    Box(
                                        Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .size(96.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(GlassWhite, GlassWhiteStrong)
                                                    )
                                                )
                                                .border(
                                                    2.dp,
                                                    Brush.linearGradient(LocalPalette.current.storyRing),
                                                    CircleShape
                                                )
                                                .clickable {
                                                    photoPicker.launch(
                                                        PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (photoUri != null) {
                                                AsyncImage(
                                                    model = photoUri,
                                                    contentDescription = "Profile photo",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        Icons.Default.AddPhotoAlternate,
                                                        contentDescription = null,
                                                        tint = LocalPalette.current.primary,
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                    Text(
                                                        "Add photo",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(18.dp))

                                    GlassField(
                                        value = name, onValueChange = { name = it },
                                        label = "Full name",
                                        leading = { FieldIcon(Icons.Default.Person) }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    GlassField(
                                        value = dob, onValueChange = { dob = it },
                                        label = "Date of birth (DD/MM/YYYY)",
                                        leading = { FieldIcon(Icons.Default.Cake) }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }

                                GlassField(
                                    value = email, onValueChange = { email = it },
                                    label = "Email address",
                                    keyboardType = KeyboardType.Email,
                                    leading = { FieldIcon(Icons.Default.Email) }
                                )
                                Spacer(Modifier.height(12.dp))
                                GlassField(
                                    value = password, onValueChange = { password = it },
                                    label = "Password",
                                    leading = { FieldIcon(Icons.Default.Lock) },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailing = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                )

                                if (loginMode) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        TextButton(onClick = { showResetDialog = true }) {
                                            Text(
                                                "Forgot password?",
                                                color = LocalPalette.current.secondary,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.height(16.dp))
                                }

                                // Error bubble
                                AnimatedVisibility(visible = authError != null) {
                                    authError?.let {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0x33FF3B5C))
                                                .border(1.dp, Color(0x66FF3B5C), RoundedCornerShape(14.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(it, color = Color(0xFFFFB3C0), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }

                                GradientButton(
                                    text = if (loginMode) "Sign In" else "Create Account",
                                    loading = authLoading,
                                    onClick = {
                                        if (loginMode) viewModel.login(email.trim(), password, onAuthSuccess)
                                        else viewModel.signup(email.trim(), name.trim(), dob.trim(), password, photoUri, onAuthSuccess)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            if (!isFirebaseConfigured) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Firebase is not configured. Add a valid google-services.json",
                    color = UnreadRed,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "By continuing you agree to our Terms & Privacy Policy",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showResetDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetMessage by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Color(0xFF151528),
            title = { Text("Reset password", color = Color.White) },
            text = {
                Column {
                    GlassField(
                        value = resetEmail, onValueChange = { resetEmail = it },
                        label = "Email address",
                        keyboardType = KeyboardType.Email,
                        leading = { FieldIcon(Icons.Default.Email) }
                    )
                    resetMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = LocalPalette.current.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetPassword(
                        resetEmail.trim(),
                        onSuccess = { resetMessage = "Reset link sent! Check your inbox." },
                        onFailure = { resetMessage = it }
                    )
                }) { Text("Send link", color = LocalPalette.current.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Close", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun PulsingLogo() {
    val palette = LocalPalette.current
    val infinite = rememberInfiniteTransition(label = "logo")
    val scale by infinite.animateFloat(
        1f, 1.08f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s"
    )
    val glow by infinite.animateFloat(
        0.35f, 0.75f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer { alpha = glow }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(palette.primary.copy(alpha = 0.5f), Color.Transparent)))
        )
        Box(
            Modifier
                .size(92.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.bubbleMine))
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("⚡", style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun AuthToggle(isLogin: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(GlassWhite)
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Sign In" to true, "Sign Up" to false).forEach { (label, mode) ->
                val selected = isLogin == mode
                val bg by animateColorAsState(
                    if (selected) palette.primary else Color.Transparent,
                    tween(280), label = "tab"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable { onChange(mode) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
        placeholder = { Text(label, color = TextSecondary) },
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = GlassWhite,
            unfocusedContainerColor = GlassWhite,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = LocalPalette.current.primary
        )
    )
}

@Composable
private fun FieldIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, tint = LocalPalette.current.primary)
}
