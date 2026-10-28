package com.example.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/** One onboarding slide: a glass icon hero with orbiting satellites and feature chips. */
data class OnboardingPage(
    val hero: ImageVector,
    val satellites: List<ImageVector>,
    val title: String,
    val description: String,
    val chips: List<String>,
    val colors: List<Color>
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        Icons.Default.Forum, listOf(Icons.Default.Bolt, Icons.Default.Mic, Icons.Default.Favorite, Icons.Default.Done),
        "Make every conversation feel alive",
        "Fast messaging, voice notes, delivery ticks, smart replies and typing indicators — chat that feels instant.",
        listOf("Real-time", "Voice notes", "Photo albums"),
        listOf(Color(0xFF6A5CFF), Color(0xFF2EC5FF))
    ),
    OnboardingPage(
        Icons.Default.Call, listOf(Icons.Default.Videocam, Icons.Default.VolumeUp, Icons.Default.Language, Icons.Default.ScreenShare),
        "HD calls that just work",
        "Crystal-clear audio and video calls with swipe-to-answer, call reactions, screen share and picture-in-picture.",
        listOf("HD video", "Swipe to answer", "Screen share"),
        listOf(Color(0xFF00C389), Color(0xFF00A3FF))
    ),
    OnboardingPage(
        Icons.Default.AutoStories, listOf(Icons.Default.Movie, Icons.Default.Favorite, Icons.Default.Poll, Icons.Default.Brush),
        "Your story, your creativity",
        "Stories, posts, reels and polls with reactions and comments. Share moments with friends in style.",
        listOf("Stories", "Reels", "Polls"),
        listOf(Color(0xFFFF5F8F), Color(0xFFFFA24C))
    ),
    OnboardingPage(
        Icons.Default.Shield, listOf(Icons.Default.Lock, Icons.Default.Key, Icons.Default.NotificationsActive, Icons.Default.VisibilityOff),
        "Security stays in your control",
        "Two-step verification, backup codes, new-device alerts, app lock and screenshot-protected chats.",
        listOf("2-step login", "Device alerts", "Privacy"),
        listOf(Color(0xFF7B5CFF), Color(0xFFE14BFF))
    ),
    OnboardingPage(
        Icons.Default.AutoAwesome, listOf(Icons.Default.SmartToy, Icons.Default.Star, Icons.Default.Palette, Icons.Default.WorkspacePremium),
        "Welcome to Convo Chat",
        "Smart AI help, beautiful themes and a friendly community. Let's set up your account in seconds.",
        listOf("AI assistant", "Themes", "Premium"),
        listOf(Color(0xFFFFB300), Color(0xFFFF5F6D))
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES.size })
    val page = ONBOARDING_PAGES[pagerState.currentPage]
    val top by animateColorAsState(page.colors[0], tween(600), label = "bg_top")
    val bottom by animateColorAsState(page.colors[1], tween(600), label = "bg_bottom")
    val motion = rememberInfiniteTransition(label = "onboarding_motion")
    val drift by motion.animateFloat(-20f, 20f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val sparkle by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "sparkle")
    val finish = { saveOnboardingCompleted(context); onFinished() }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0B1A), Color(0xFF12122A))))
    ) {
        Box(Modifier.align(Alignment.TopStart).offset(x = (-70).dp, y = (60 + drift).dp).size(280.dp).blur(70.dp).background(top.copy(alpha = .55f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 80.dp, y = (-120 - drift).dp).size(300.dp).blur(80.dp).background(bottom.copy(alpha = .45f), CircleShape))
        Canvas(Modifier.fillMaxSize()) {
            repeat(28) { i ->
                val seed = (i * 47 % 100) / 100f
                val p = (sparkle + seed) % 1f
                drawCircle(
                    Color.White.copy(alpha = (sin(p * PI.toFloat()) * .45f).coerceAtLeast(0f)),
                    radius = 1.3f + (i % 3),
                    center = Offset(size.width * ((seed * 1.7f + i * .041f) % 1f), size.height * (1f - p))
                )
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Convo Chat", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (pagerState.currentPage < ONBOARDING_PAGES.lastIndex) TextButton(onClick = finish, modifier = Modifier.testTag("onboarding_skip")) {
                    Text(tr("Skip"), color = Color.White.copy(alpha = .8f))
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val item = ONBOARDING_PAGES[index]
                val offset = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Column(
                    Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset * .6f; scaleX = 1f - offset * .12f; scaleY = 1f - offset * .12f }.padding(horizontal = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmojiHero(item, active = pagerState.currentPage == index)
                    Spacer(Modifier.height(30.dp))
                    Text(tr(item.title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(tr(item.description), color = Color.White.copy(alpha = .78f), textAlign = TextAlign.Center, lineHeight = 21.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item.chips.forEachIndexed { i, chip ->
                            var shown by remember(pagerState.currentPage == index) { mutableStateOf(false) }
                            LaunchedEffect(pagerState.currentPage == index) { if (pagerState.currentPage == index) { kotlinx.coroutines.delay(150L + i * 110L); shown = true } }
                            val a by animateFloatAsState(if (shown) 1f else 0f, tween(320), label = "chip_a")
                            val dy by animateFloatAsState(if (shown) 0f else 16f, spring(dampingRatio = .55f), label = "chip_y")
                            Text(
                                tr(chip), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.alpha(a).offset(y = dy.dp).clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = .14f)).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            // Dots + action.
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(ONBOARDING_PAGES.size) { i ->
                        val selected = i == pagerState.currentPage
                        val width by animateDpAsState(if (selected) 28.dp else 8.dp, spring(dampingRatio = .6f), label = "dot_w")
                        Box(
                            Modifier.height(8.dp).width(width).clip(CircleShape)
                                .background(if (selected) Brush.horizontalGradient(listOf(top, bottom)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = .3f), Color.White.copy(alpha = .3f))))
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                        )
                    }
                }
                val last = pagerState.currentPage == ONBOARDING_PAGES.lastIndex
                val btnWidth by animateDpAsState(if (last) 170.dp else 62.dp, spring(dampingRatio = .7f), label = "btn_w")
                Box(
                    Modifier.height(62.dp).width(btnWidth).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(top, bottom)))
                        .clickable { if (last) finish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                        .testTag("onboarding_next"),
                    contentAlignment = Alignment.Center
                ) {
                    if (last) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Get started"), color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Check, null, tint = Color.White)
                    } else Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EmojiHero(page: OnboardingPage, active: Boolean) {
    val motion = rememberInfiniteTransition(label = "emoji_hero")
    val orbit by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "orbit")
    val bob by motion.animateFloat(-8f, 8f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val wobble by motion.animateFloat(-6f, 6f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wobble")
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(active) { if (active) { pop.snapTo(.4f); pop.animateTo(1f, spring(dampingRatio = .42f, stiffness = 240f)) } }
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(190.dp).clip(CircleShape).background(Brush.radialGradient(listOf(page.colors[0].copy(alpha = .55f), page.colors[1].copy(alpha = .15f), Color.Transparent))))
        Box(Modifier.size(210.dp).rotate(orbit).border(1.5.dp, Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = .6f), Color.Transparent)), CircleShape))
        page.satellites.forEachIndexed { i, icon ->
            val angle = (orbit * (if (i % 2 == 0) 1f else -.8f) + i * 90f) * (PI.toFloat() / 180f)
            val r = if (i % 2 == 0) 112f else 92f
            Box(
                Modifier.offset(x = (cos(angle) * r).dp, y = (sin(angle) * r).dp).scale(pop.value)
                    .size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .10f))))
                    .border(1.dp, Color.White.copy(alpha = .45f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Box(
            Modifier.offset(y = bob.dp).rotate(wobble).scale(pop.value).size(124.dp).clip(RoundedCornerShape(40.dp))
                .background(Brush.linearGradient(listOf(page.colors[0], page.colors[1])))
                .border(1.5.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(46.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .32f), Color.Transparent))))
            Icon(page.hero, null, tint = Color.White, modifier = Modifier.size(62.dp))
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
