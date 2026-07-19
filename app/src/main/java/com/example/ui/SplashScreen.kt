package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "liquid_glass_motion")
    val pulse by motion.animateFloat(
        .94f, 1.06f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo_pulse"
    )
    val rotation by motion.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(8500, easing = LinearEasing)),
        label = "orbital_rotation"
    )
    val drift by motion.animateFloat(
        -18f, 22f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liquid_drift"
    )

    LaunchedEffect(Unit) {
        delay(1750)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF060710), Color(0xFF101329), Color(0xFF080A12)))
        ),
        contentAlignment = Alignment.Center
    ) {
        // Animated color masses are deliberately blurred behind the glass, keeping GPU work bounded.
        Box(
            Modifier.align(Alignment.TopStart).offset(x = (-55).dp, y = (90 + drift).dp)
                .size(230.dp).blur(54.dp).background(Color(0xFF755CFF).copy(alpha = .55f), CircleShape)
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 70.dp, y = (-90 - drift).dp)
                .size(260.dp).blur(64.dp).background(Color(0xFF1DD6C0).copy(alpha = .38f), CircleShape)
        )
        Box(
            Modifier.align(Alignment.Center).offset(x = 120.dp, y = (-170).dp)
                .size(150.dp).blur(48.dp).background(Color(0xFFFF68B2).copy(alpha = .34f), CircleShape)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().rotate(rotation)
                        .border(1.dp, Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(.7f), Color.Transparent)), CircleShape)
                )
                Box(
                    Modifier.size(150.dp).rotate(-rotation * .7f)
                        .border(2.dp, Brush.sweepGradient(listOf(Color(0xFF8A72FF), Color.Transparent, Color(0xFF36D8C4))), CircleShape)
                )
                Box(
                    Modifier.size(124.dp).scale(pulse).clip(RoundedCornerShape(38.dp))
                        .background(Color.White.copy(alpha = .13f))
                        .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(38.dp))
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_app_logo),
                        contentDescription = "FireChat logo",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                    )
                    Box(
                        Modifier.align(Alignment.TopStart).fillMaxWidth().height(36.dp)
                            .background(Brush.verticalGradient(listOf(Color.White.copy(.22f), Color.Transparent)))
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("FireChat", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("Connect • Share • Belong", color = Color.White.copy(alpha = .66f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(34.dp))
            Box(
                Modifier.width(170.dp).clip(CircleShape).background(Color.White.copy(alpha = .09f))
                    .border(1.dp, Color.White.copy(alpha = .16f), CircleShape).padding(5.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Color(0xFFBBAAFF),
                    trackColor = Color.Transparent
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("আপনার জগৎ প্রস্তুত হচ্ছে…", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelMedium)
        }

        Text(
            "Designed by Rakibul Islam",
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(22.dp),
            color = Color.White.copy(alpha = .42f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
