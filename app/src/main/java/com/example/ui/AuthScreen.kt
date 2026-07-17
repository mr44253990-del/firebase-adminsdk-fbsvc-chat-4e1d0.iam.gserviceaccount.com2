package com.example.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.TestTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: ChatViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val isFirebaseConfigured by viewModel.isFirebaseConfigured.collectAsState()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetLoading by remember { mutableStateOf(false) }
    var resetMessage by remember { mutableStateOf<String?>(null) }

    var passwordVisible by remember { mutableStateOf(false) }
    
    // Animation states
    var isTabChanging by remember { mutableStateOf(false) }
    val tabScale by animateFloatAsState(
        targetValue = if (isTabChanging) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300),
        label = "tabScale"
    )

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            dob = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val profileImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profileImageUri = uri
    }

    // Animated gradient colors
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )

    val topGradientColor by infiniteTransition.animateColor(
        initialValue = ElectricPurple.copy(alpha = 0.3f),
        targetValue = CyanGlow.copy(alpha = 0.3f),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "topColor"
    )

    val bottomGradientColor by infiniteTransition.animateColor(
        initialValue = PinkNeon.copy(alpha = 0.2f),
        targetValue = MintGreen.copy(alpha = 0.2f),
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bottomColor"
    )

    // Floating particles animation
    val particles = remember {
        List(20) { ParticleData(
            offset = Offset(Math.random().toFloat(), Math.random().toFloat()),
            size = (8..24).random().dp,
            duration = (3000..8000).random()
        ) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        topGradientColor,
                        DarkBackground,
                        DarkBackground.copy(alpha = 0.95f),
                        bottomGradientColor
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Floating particles
        particles.forEach { particle ->
            val particleOffset by infiniteTransition.animateFloat(
                initialValue = particle.offset.y,
                targetValue = if (particle.offset.y > 0.5f) particle.offset.y - 0.2f else particle.offset.y + 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(particle.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "particleY"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (particle.offset.x * 400).dp,
                            y = (particleOffset * 700).dp
                        )
                        .size(particle.size)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricPurple.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Animated Logo/Brand
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(if (isLoginMode) 1f else 1.05f),
                contentAlignment = Alignment.Center
            ) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .blur(20.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(ElectricPurple, Color.Transparent)
                            )
                        )
                )
                
                // Main logo container
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = DarkSurface,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(GradientPurpleCyan)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Brand name with animation
            Text(
                text = "FireChat Pro",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(GradientPurpleCyan)
                ),
                fontSize = 32.sp
            )

            Text(
                text = if (isLoginMode) "Welcome back! Let's chat 💬" else "Join the community! 🚀",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Glass card for form
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(tabScale),
                shape = RoundedCornerShape(28.dp),
                color = GlassWhite,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tab selector with glass effect
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpacedBy(4.dp)
                        ) {
                            TabButton(
                                text = "Log In",
                                isSelected = isLoginMode,
                                onClick = {
                                    isTabChanging = true
                                    isLoginMode = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TabButton(
                                text = "Sign Up",
                                isSelected = !isLoginMode,
                                onClick = {
                                    isTabChanging = true
                                    isLoginMode = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Form fields with animations
                    AnimatedContent(
                        targetState = isLoginMode,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "formContent"
                    ) { isLogin ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!isLogin) {
                                // Profile image picker for sign up
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clickable { profileImagePicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profileImageUri != null) {
                                        AsyncImage(
                                            model = profileImageUri,
                                            contentDescription = "Profile",
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(CircleShape)
                                                .border(3.dp, ElectricPurple, CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(100.dp),
                                            shape = CircleShape,
                                            color = DarkSurface,
                                            border = BorderStroke(2.dp, ElectricPurple.copy(alpha = 0.5f))
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddAPhoto,
                                                    contentDescription = "Add Photo",
                                                    tint = ElectricPurple,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Camera icon overlay
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(32.dp),
                                        shape = CircleShape,
                                        color = ElectricPurple
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Name field
                                GlassTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "Full Name",
                                    leadingIcon = Icons.Default.Person,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Email field
                            GlassTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = "Email Address",
                                leadingIcon = Icons.Default.Email,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Password field
                            GlassTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Password",
                                leadingIcon = Icons.Default.Lock,
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide" else "Show",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (isLoginMode) {
                                            viewModel.login(email, password, onAuthSuccess)
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!isLogin) {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Date of Birth picker
                                GlassTextField(
                                    value = dob,
                                    onValueChange = { },
                                    label = "Date of Birth",
                                    leadingIcon = Icons.Default.CalendarMonth,
                                    trailingIcon = {
                                        IconButton(onClick = { datePickerDialog.show() }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "Select Date",
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    },
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { datePickerDialog.show() }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Bio field
                                GlassTextField(
                                    value = bio,
                                    onValueChange = { bio = it },
                                    label = "Bio (optional)",
                                    leadingIcon = Icons.Default.Info,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    singleLine = false,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Forgot password link (login mode only)
                            if (isLoginMode) {
                                TextButton(
                                    onClick = { showForgotPasswordDialog = true },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        color = CyanGlow,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Error message
                            AnimatedVisibility(
                                visible = authError != null,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = CoralRed.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = CoralRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = authError ?: "",
                                            color = CoralRed,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Submit button with animation
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (isLoginMode) {
                                        viewModel.login(email, password, onAuthSuccess)
                                    } else {
                                        viewModel.signup(
                                            email = email,
                                            name = name,
                                            dob = dob,
                                            password = password,
                                            profileImageUri = profileImageUri?.toString() ?: "",
                                            onSuccess = onAuthSuccess
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .then(
                                        if (authLoading) Modifier else Modifier.clickable { }
                                    ),
                                enabled = !authLoading,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(GradientPurpleCyan),
                                            RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (authLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = if (isLoginMode) "Log In" else "Create Account",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = if (isLoginMode) Icons.Default.Login else Icons.Default.PersonAdd,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Firebase warning banner
            if (!isFirebaseConfigured) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = GoldenYellow.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = GoldenYellow
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Firebase Unconfigured",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = GoldenYellow
                            )
                            Text(
                                text = "Running in demo mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldenYellow.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Social login options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialLoginButton(
                    icon = Icons.Default.Email,
                    label = "Google",
                    onClick = { /* Google Sign In */ },
                    color = Color(0xFFDB4437)
                )
                Spacer(modifier = Modifier.width(16.dp))
                SocialLoginButton(
                    icon = Icons.Default.Phone,
                    label = "Phone",
                    onClick = { /* Phone Sign In */ },
                    color = MintGreen
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "By continuing, you agree to our Terms & Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Enter your email to receive a reset link")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resetMessage!!,
                            color = if (resetLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetLoading = true
                        // Send password reset email via Firebase
                        FirebaseAuth.getInstance().sendPasswordResetEmail(resetEmail)
                            .addOnSuccessListener {
                                resetMessage = "Reset email sent!"
                                resetLoading = false
                            }
                            .addOnFailureListener {
                                resetMessage = it.message
                                resetLoading = false
                            }
                    },
                    enabled = !resetLoading && resetEmail.isNotBlank()
                ) {
                    if (resetLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) ElectricPurple else Color.Transparent,
        animationSpec = tween(300),
        label = "tabBg"
    )
    
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) ElectricPurple else Color.White.copy(alpha = 0.2f),
        animationSpec = tween(300),
        label = "borderColor"
    )
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (isFocused) ElectricPurple else Color.White.copy(alpha = 0.6f)
            )
        },
        trailingIcon = trailingIcon,
        modifier = modifier
            .focusable { isFocused = it },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = ElectricPurple,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = ElectricPurple,
            focusedContainerColor = DarkSurface.copy(alpha = 0.5f),
            unfocusedContainerColor = DarkSurface.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
    )
}

@Composable
private fun SocialLoginButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private data class ParticleData(
    val offset: Offset,
    val size: androidx.compose.ui.unit.Dp,
    val duration: Int
)

private object FirebaseAuth {
    fun getInstance() = com.google.firebase.auth.FirebaseAuth.getInstance()
}
