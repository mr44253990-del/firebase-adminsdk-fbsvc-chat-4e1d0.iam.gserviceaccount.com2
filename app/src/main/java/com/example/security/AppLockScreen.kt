package com.example.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun AppLockScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockoutUntil by remember { mutableLongStateOf(AppLockManager.remainingLockoutMs(context) + System.currentTimeMillis()) }
    val requiredLength = remember { AppLockManager.pinLength(context) }

    fun biometric() {
        if (!AppLockManager.isBiometricEnabled(context) || !AppLockManager.canUseBiometric(context)) return
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLockManager.clearFailedAttempts(context)
                AppLockManager.unlock()
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Convo Chat")
                .setSubtitle("Use fingerprint, face, or device credential")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        )
    }
    LaunchedEffect(Unit) { biometric() }

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(.35f), MaterialTheme.colorScheme.background))),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(22.dp)); Text("Convo Chat locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val remainingLockout = (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)
            Text(
                when {
                    remainingLockout > 0L -> "Too many attempts. Try again in ${((remainingLockout + 999L) / 1000L)}s"
                    error -> "Incorrect PIN"
                    else -> "Enter your PIN"
                },
                color = if (error || remainingLockout > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(requiredLength) { index -> Box(Modifier.size(if (index < pin.length) 12.dp else 9.dp).clip(CircleShape).background(if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("bio","0","del")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "del" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        "bio" -> biometric()
                                        else -> if (pin.length < requiredLength) {
                                            if (!AppLockManager.canAttemptPin(context)) {
                                                error = true
                                                lockoutUntil = System.currentTimeMillis() + AppLockManager.remainingLockoutMs(context)
                                            } else {
                                                pin += key
                                                error = false
                                                if (AppLockManager.verify(context, pin)) {
                                                    AppLockManager.unlock()
                                                } else if (pin.length == requiredLength) {
                                                    val duration = AppLockManager.recordFailedAttempt(context)
                                                    lockoutUntil = System.currentTimeMillis() + duration
                                                    error = true
                                                    pin = ""
                                                }
                                            }
                                        }
                                    }
                                }, modifier = Modifier.size(70.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)
                            ) {
                                when (key) { "del" -> Icon(Icons.Default.Backspace, "Delete"); "bio" -> Text("BIO", fontWeight = FontWeight.Bold); else -> Text(key, style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                    }
                }
            }
        }
    }
}
