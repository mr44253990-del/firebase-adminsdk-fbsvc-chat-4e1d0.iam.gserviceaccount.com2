package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val featureBadge: String
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableStateOf(0) }

    val pages = listOf(
        OnboardingPage(
            title = "Real-Time Chatting",
            description = "Experience lightning-fast instant messaging. Chat with anyone around the globe securely, instantly, and with elegant visual ripples.",
            icon = Icons.Outlined.ChatBubbleOutline,
            accentColor = MaterialTheme.colorScheme.primary,
            featureBadge = "⚡ Real-time"
        ),
        OnboardingPage(
            title = "Sync Notifications via Webhooks",
            description = "Get notified instantly. FireChat syncs and publishes your push notification triggers dynamically through n8n webhooks and Firestore without manual setups.",
            icon = Icons.Outlined.Send,
            accentColor = MaterialTheme.colorScheme.tertiary,
            featureBadge = "🔗 Webhook Native"
        ),
        OnboardingPage(
            title = "Privacy & Encryption first",
            description = "All conversations and accounts are protected securely. Experience direct profile creation with dynamic password auth, and password recovery tools.",
            icon = Icons.Outlined.Lock,
            accentColor = MaterialTheme.colorScheme.secondary,
            featureBadge = "🔒 Secure Profile"
        )
    )

    val activePage = pages[currentPageIndex]

    // Deep dynamic gradient background based on page's accent color
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            activePage.accentColor.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Skip Button (Top Right)
        TextButton(
            onClick = {
                saveOnboardingCompleted(context)
                onFinished()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .testTag("onboarding_skip")
        ) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visual Badge
            Surface(
                color = activePage.accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = activePage.featureBadge,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = activePage.accentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Animated Visual Container for the Icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                activePage.accentColor,
                                activePage.accentColor.copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = activePage.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Text titles and descriptions with smooth animated fade/transitions
            AnimatedContent(
                targetState = activePage,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "pageContent"
            ) { page ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Pagination Dots Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    val isActive = index == currentPageIndex
                    val width = if (isActive) 24.dp else 8.dp
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isActive) activePage.accentColor else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Navigation CTA Button
            Button(
                onClick = {
                    if (currentPageIndex < pages.size - 1) {
                        currentPageIndex++
                    } else {
                        saveOnboardingCompleted(context)
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_next"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activePage.accentColor
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (currentPageIndex == pages.size - 1) "Get Started" else "Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun saveOnboardingCompleted(context: Context) {
    val prefs = context.getSharedPreferences("app_onboarding_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("has_completed_onboarding", true).apply()
}

fun isOnboardingCompleted(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_onboarding_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("has_completed_onboarding", false)
}
