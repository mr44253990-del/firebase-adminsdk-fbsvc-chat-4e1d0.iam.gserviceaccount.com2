package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = .92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    LaunchedEffect(Unit) {
        delay(1100)
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(MaterialTheme.colorScheme.primary.copy(alpha = .32f), MaterialTheme.colorScheme.background)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp).scale(pulse).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Forum, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("FireChat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(120.dp).height(4.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
