package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.TestTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val featureBadge: String,
    val features: List<String>
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
            description = "Experience lightning-fast instant messaging with beautiful glassmorphism UI. Chat with anyone around the globe securely and instantly.",
            icon = Icons.Outlined.ChatBubbleOutline,
            gradientColors = listOf(ElectricPurple, CyanGlow),
            featureBadge = "⚡ Real-time",
            features = listOf("Instant delivery", "Read receipts", "Typing indicators")
        ),
        OnboardingPage(
            title = "Stories & Posts",
            description = "Share your moments with Stories that auto-expire in 24 hours. Create engaging posts with reactions, comments, and shares.",
            icon = Icons.Outlined.Article,
            gradientColors = listOf(PinkNeon, ElectricPurple),
            featureBadge = "📸 Stories",
            features = listOf("24h auto-delete", "View counts", "Reactions")
        ),
        OnboardingPage(
            title = "Groups & Notes",
            description = "Create groups for team collaboration or fun conversations. Keep your thoughts organized with colorful Notes.",
            icon = Icons.Outlined.Group,
            gradientColors = listOf(MintGreen, CyanGlow),
            featureBadge = "👥 Groups",
            features = listOf("Group chat", "Admin controls", "Notes")
        ),
        OnboardingPage(
            title = "Modern Design",
            description = "Immerse yourself in a stunning glassmorphism interface with smooth animations and dynamic theming.",
            icon = Icons.Outlined.Palette,
            gradientColors = listOf(GoldenYellow, PinkNeon),
            featureBadge = "✨ Beautiful",
            features = listOf("Glassmorphism", "Animations", "Themes")
        )
    )

    val activePage = pages[currentPageIndex]

    // Infinite transition for animations
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding")
    
    val floatAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val rotateAnimation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotate"
    )

    // Background gradient animation
    val backgroundColor1 by infiniteTransition.animateColor(
        initialValue = activePage.gradientColors[0].copy(alpha = 0.15f),
        targetValue = activePage.gradientColors[1].copy(alpha = 0.15f),
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg1"
    )

    val backgroundColor2 by infiniteTransition.animateColor(
        initialValue = activePage.gradientColors.getOrElse(1) { activePage.gradientColors[0] }.copy(alpha = 0.1f),
        targetValue = activePage.gradientColors[0].copy(alpha = 0.1f),
        animationSpec = infiniteRepeatable(
            animation = tween(4000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg2"
    )

    // Floating particles
    val particles = remember {
        List(30) { 
            Particle(
                offset = Offset(
                    Math.random().toFloat() * 400,
                    Math.random().toFloat() * 800
                ),
                size = (6..20).random().dp,
                alpha = (0.1f..0.3f).random(),
                duration = (2000..6000).random()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor1,
                        DarkBackground,
                        DarkBackground,
                        backgroundColor2
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Floating particles
        particles.forEach { particle ->
            Box(
                modifier = Modifier
                    .offset(
                        x = particle.offset.x.dp + (floatAnimation * 20).dp,
                        y = particle.offset.y.dp + ((1 - floatAnimation) * 30).dp
                    )
                    .size(particle.size)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                activePage.gradientColors[0].copy(alpha = particle.alpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Skip Button
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
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Feature Badge
            AnimatedContent(
                targetState = currentPageIndex,
                transitionSpec = {
                    fadeIn(tween(300)) + scaleIn(initialScale = 0.8f) togetherWith 
                    fadeOut(tween(300)) + scaleOut(targetScale = 1.2f)
                },
                label = "badge"
            ) { index ->
                Surface(
                    color = activePage.gradientColors[0].copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, activePage.gradientColors[0].copy(alpha = 0.5f)),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activePage.featureBadge,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = activePage.gradientColors[0]
                        )
                    }
                }
            }

            // Animated Icon Container
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(1f + (floatAnimation * 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .blur(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    activePage.gradientColors[0].copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Rotating ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .rotate(rotateAnimation)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    activePage.gradientColors[0],
                                    activePage.gradientColors[1],
                                    activePage.gradientColors[0]
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Main icon container
                Surface(
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    color = DarkSurface,
                    shadowElevation = 16.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(activePage.gradientColors)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = activePage.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Text Content
            AnimatedContent(
                targetState = activePage,
                transitionSpec = {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(300))
                },
                label = "content"
            ) { page ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        ),
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Features list
                    page.features.forEachIndexed { index, feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(activePage.gradientColors[0])
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Pagination Dots with Animation
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    val isActive = index == currentPageIndex
                    val width by animateDpAsState(
                        targetValue = if (isActive) 32.dp else 8.dp,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300),
                        label = "dotWidth"
                    )
                    
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isActive) activePage.gradientColors[0] 
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Navigation Button
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
                    .height(60.dp)
                    .testTag("onboarding_next"),
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
                            Brush.horizontalGradient(activePage.gradientColors),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (currentPageIndex == pages.size - 1) "Get Started" else "Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (currentPageIndex == pages.size - 1) 
                                Icons.Default.Celebration 
                            else 
                                Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }
            }

            // Page indicator text
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${currentPageIndex + 1} of ${pages.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Bottom decorative elements
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DarkBackground.copy(alpha = 0.8f)
                        )
                    )
                )
        )
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

private data class Particle(
    val offset: Offset,
    val size: androidx.compose.ui.unit.Dp,
    val alpha: Float,
    val duration: Int
)

private fun ClosedFloatingPointRange<Float>.random(): Float {
    return start + (endInclusive - start) * Math.random().toFloat()
}
