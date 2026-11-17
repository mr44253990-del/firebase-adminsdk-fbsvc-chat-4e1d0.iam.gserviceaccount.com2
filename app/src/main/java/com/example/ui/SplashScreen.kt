package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SPLASH_INK = Color(0xFF07070F)
private val SPLASH_VIOLET = Color(0xFF8B7BFF)
private val SPLASH_CYAN = Color(0xFF5AC8FA)
private val SPLASH_PINK = Color(0xFFFF6FB5)

private fun splashGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "শুভ সকাল"
    in 12..16 -> "শুভ দুপুর"
    in 17..19 -> "শুভ সন্ধ্যা"
    else -> "শুভ রাত্রি"
}

private fun splashTip(): String = listOf(
    "Tip · Long-press a message for reactions, reply and translate",
    "Tip · Ask Convo AI to find any profile for you",
    "Tip · Group calls now show a Join bar for everyone in the group",
    "Tip · Minimise a call — the floating bubble follows you everywhere",
    "Tip · Swipe up on the green button to answer a call",
    "Tip · Try the new camera looks inside a video call"
).random()

/** Small rotating status line so the wait feels intentional and short. */
private fun splashStatus(progress: Float, securing: Boolean = false): String = when {
    securing && progress >= .80f -> "Securing your session…"
    progress < .22f -> "Waking up your workspace…"
    progress < .45f -> "Restoring your chats…"
    progress < .68f -> "Checking secure sign-in…"
    progress < .86f -> "Syncing friends & stories…"
    else -> "Almost there…"
}

/**
 * Launch screen — "dark liquid glass" (v5.6): flowing violet / blue glass on black, the glossy
 * app tile with a floor reflection, the two-tone wordmark with a live waveform, one wide
 * glowing progress bar and a frosted card whose three stages (Connect · Secure · Sync) light
 * up as the launch moves forward. The optional [work] (session / 2FA check) runs inside it.
 */
@Composable
fun SplashScreen(work: (suspend () -> Unit)? = null, onFinished: () -> Unit) {
    var workDone by remember { mutableStateOf(work == null) }
    val progress = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    val tip = remember { splashTip() }

    LaunchedEffect(Unit) {
        launch { delay(60); content.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { progress.animateTo(if (work == null) 1f else .86f, tween(820, easing = FastOutSlowInEasing)) }
        val job = if (work != null) launch { runCatching { work() }; workDone = true } else null
        delay(1050)
        job?.join()
        if (work != null) progress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        onFinished()
    }
    val shimmer by rememberInfiniteTransition(label = "splash_bar").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer")

    Box(Modifier.fillMaxSize().background(LiquidInk), contentAlignment = Alignment.Center) {
        LiquidGlassBackground()
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            // v5.8: no app icon on the loading screen — straight to the connecting state.
            Spacer(Modifier.height(18.dp))

            // One wide glowing progress bar.
            Box(
                Modifier.fillMaxWidth(.82f).height(9.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = .06f)).border(1.dp, Color.White.copy(alpha = .16f), CircleShape)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.value.coerceIn(0.03f, 1f)).height(9.dp).clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(LiquidViolet, LiquidBlue, LiquidCyan, Color.White, LiquidCyan),
                                startX = shimmer * 500f - 250f, endX = shimmer * 500f + 420f
                            )
                        )
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(if (progress.value >= .999f) "Connecting…" else "Loading…", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(30.dp))

            // Frosted status card with the three launch stages.
            Column(
                Modifier.fillMaxWidth().alpha(content.value).liquidGlass(RoundedCornerShape(30.dp)).padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(splashStatus(progress.value, securing = !workDone), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("Connect", .20f, LiquidCyan), Triple("Secure", .62f, LiquidViolet), Triple("Sync", .99f, LiquidBlue)).forEachIndexed { index, (label, at, tint) ->
                        val on = progress.value >= at
                        val a by animateFloatAsState(if (on) 1f else .42f, tween(260), label = "stage_alpha")
                        Row(
                            Modifier.weight(1f).alpha(a).clip(CircleShape)
                                .background(Color.White.copy(alpha = if (on) .10f else .04f))
                                .border(1.dp, if (on) tint.copy(alpha = .8f) else Color.White.copy(alpha = .14f), CircleShape)
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (index) { 0 -> Icons.Default.Bolt; 1 -> Icons.Default.VerifiedUser; else -> Icons.Default.Sync },
                                null, tint = tint, modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.alpha(content.value), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(tip, color = Color.White.copy(alpha = .55f), fontSize = 11.5.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Text(
            "—  v" + com.example.BuildConfig.VERSION_NAME + "  —", color = Color.White.copy(alpha = .45f), fontSize = 12.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 20.dp)
        )
    }
}
