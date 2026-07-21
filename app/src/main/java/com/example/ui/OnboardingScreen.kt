package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
            title = "কথা হোক আরও জীবন্ত",
            description = "দ্রুত মেসেজ, ভয়েস নোট, HD অডিও-ভিডিও কল, স্মার্ট টাইপিং ইন্ডিকেটর এবং ব্যক্তিগত চ্যাট থিম—প্রিয় মানুষদের সঙ্গে সবসময় সহজে যুক্ত থাকুন।",
            icon = Icons.Outlined.ChatBubbleOutline,
            accentColor = MaterialTheme.colorScheme.primary,
            featureBadge = "⚡ স্মার্ট রিয়েল-টাইম চ্যাট"
        ),
        OnboardingPage(
            title = "আপনার গল্প, আপনার সৃজনশীলতা",
            description = "স্টোরি, অ্যানিমেটেড পোস্ট, বহু ছবির ক্যারোসেল ও ফুল-স্ক্রিন রিল শেয়ার করুন। R2 streaming cache দ্রুত playback দেয় এবং প্রতিটি reaction Activity Center-এ পৌঁছে দেয়।",
            icon = Icons.Outlined.Send,
            accentColor = MaterialTheme.colorScheme.tertiary,
            featureBadge = "✨ সম্পূর্ণ সোশ্যাল অভিজ্ঞতা"
        ),
        OnboardingPage(
            title = "নিরাপত্তা আপনার নিয়ন্ত্রণে",
            description = "PIN ও বায়োমেট্রিক লক, লুকানো notification content, screenshot-protected calls, local Room history এবং privacy controls আপনার FireChat-কে রাখে ব্যক্তিগত।",
            icon = Icons.Outlined.Lock,
            accentColor = MaterialTheme.colorScheme.secondary,
            featureBadge = "🔒 Privacy First"
        ),
        OnboardingPage(
            title = "রাকিবুল ইসলামের তৈরি FireChat",
            description = "মানুষকে সুন্দর, দ্রুত ও নিরাপদভাবে যুক্ত করার স্বপ্নে তৈরি একটি আধুনিক বাংলাদেশি প্ল্যাটফর্ম। Flagship updates, নতুন feature requests এবং community feedback-এর মাধ্যমে FireChat প্রতিনিয়ত আরও উন্নত হবে।",
            icon = Icons.Outlined.ChatBubbleOutline,
            accentColor = MaterialTheme.colorScheme.primary,
            featureBadge = "🚀 নির্মাতা • রাকিবুল ইসলাম"
        )
    )

    val activePage = pages[currentPageIndex]
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val motion = rememberInfiniteTransition(label = "onboarding_motion")
    val pulse by motion.animateFloat(.96f, 1.05f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val orbit by motion.animateFloat(-4f, 4f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orbit")

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
            .pointerInput(currentPageIndex) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            horizontalDrag < -80f && currentPageIndex < pages.lastIndex -> currentPageIndex++
                            horizontalDrag > 80f && currentPageIndex > 0 -> currentPageIndex--
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f },
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount }
                )
            }
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
                text = "এড়িয়ে যান",
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
                shape = RoundedCornerShape(18.dp),
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
                    .scale(pulse)
                    .rotate(orbit)
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
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
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

            Spacer(modifier = Modifier.height(12.dp))
            Text("←  Swipe to explore  →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
            Spacer(modifier = Modifier.height(32.dp))

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
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activePage.accentColor
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (currentPageIndex == pages.size - 1) "শুরু করুন" else "পরবর্তী",
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
