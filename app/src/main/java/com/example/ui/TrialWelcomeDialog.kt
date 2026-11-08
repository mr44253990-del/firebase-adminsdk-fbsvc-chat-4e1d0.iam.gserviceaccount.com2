package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 14-day trial welcome — beautiful + vibrates, shown on first login.
 */
@Composable
fun TrialWelcomeDialog(
    userName: String,
    onStartTrial: () -> Unit,
    onMaybeLater: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val infinite = rememberInfiniteTransition(label = "trial-pulse")
    val scale by infinite.animateFloat(initialValue = 1f, targetValue = 1.06f, animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")

    LaunchedEffect(Unit) {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 40, 80, 100, 120), -1))
        } else @Suppress("DEPRECATION") vib.vibrate(longArrayOf(0, 80, 40, 80), -1)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460)))),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF6B35)))).padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(72.dp).scale(scale).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(42.dp), tint = Color(0xFFFFA500))
                        }
                        Text("Welcome, " + userName + "!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Text("You have 14 days of Premium - free!", color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FeatureRow(Icons.Default.Star, "Unlimited friends & groups")
                    FeatureRow(Icons.Default.Star, "HD video & voice messages")
                    FeatureRow(Icons.Default.Star, "Custom themes & exclusive badges")
                    FeatureRow(Icons.Default.Star, "Priority support")
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onStartTrial,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500), contentColor = Color.White)
                    ) { Text("Start my 14-day free trial", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    TextButton(onClick = onMaybeLater, modifier = Modifier.fillMaxWidth()) { Text("Maybe later", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                    Text("No card required - Cancel anytime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFA500).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFA500))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
    }
}
