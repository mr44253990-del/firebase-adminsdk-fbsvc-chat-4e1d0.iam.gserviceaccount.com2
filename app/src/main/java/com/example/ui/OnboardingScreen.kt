package com.example.ui

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.LocalPalette
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class OnboardPage(val emoji: String, val title: String, val body: String)

private val pages = listOf(
    OnboardPage("⚡", "Welcome to FireChat", "A next-gen social universe — chat, share posts, stories and moments in stunning glass style."),
    OnboardPage("📸", "Stories & Posts", "Share stories that vanish in 12 hours, post photos & videos, react, like and comment — just like your favorite apps."),
    OnboardPage("🎤", "Rich Messaging", "Swipe to reply, send voice notes & photos, create groups, and see the red glow when new messages arrive.")
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    GlassBackground(bubbleCount = 16) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    saveOnboardingCompleted(context)
                    onFinished()
                }) {
                    Text("Skip", color = TextSecondary)
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val p = pages[page]
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val infinite = rememberInfiniteTransition(label = "float")
                    val dy by infinite.animateFloat(
                        0f, -14f,
                        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "y"
                    )
                    Box(
                        Modifier
                            .offset(y = dy.dp)
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(LocalPalette.current.bubbleMine.map { it.copy(alpha = 0.5f) })),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(p.emoji, style = MaterialTheme.typography.displaySmall, modifier = Modifier.graphicsLayer { scaleX = 1.6f; scaleY = 1.6f })
                    }
                    Spacer(Modifier.height(34.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GradientText(p.title, MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                p.body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    val active = pagerState.currentPage == i
                    val width by animateDpAsState(if (active) 26.dp else 8.dp, tween(300), label = "dot")
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) LocalPalette.current.primary else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
            Spacer(Modifier.height(26.dp))

            GradientButton(
                text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Next",
                onClick = {
                    if (pagerState.currentPage == pages.size - 1) {
                        saveOnboardingCompleted(context)
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun saveOnboardingCompleted(context: Context) {
    context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarding_completed", true)
        .apply()
}

fun isOnboardingCompleted(context: Context): Boolean {
    return context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE)
        .getBoolean("onboarding_completed", false)
}
