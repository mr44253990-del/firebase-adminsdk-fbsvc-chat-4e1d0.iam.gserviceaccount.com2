package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SPLASH_EMOJI = listOf("💬", "❤️", "📞", "🎉", "📸", "🔒", "😊", "✨")

private fun splashGreeting(): Pair<String, String> = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "শুভ সকাল" to "☀️"
    in 12..16 -> "শুভ দুপুর" to "🌤️"
    in 17..19 -> "শুভ সন্ধ্যা" to "🌇"
    else -> "শুভ রাত্রি" to "🌙"
}

/**
 * Animated launch screen: logo pops in, emoji orbit the logo, title letters cascade in,
 * emoji bubbles float up and a determinate progress bar fills before navigating on.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "splash_motion")
    val orbit by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orbit")
    val ring by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "ring")
    val drift by motion.animateFloat(-18f, 22f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val bubbles by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "bubbles")

    val logoScale = remember { Animatable(0.2f) }
    val logoTilt = remember { Animatable(-25f) }
    val emojiReveal = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    var lettersShown by remember { mutableIntStateOf(0) }
    val title = "Convo Chat"
    val (greeting, greetingEmoji) = remember { splashGreeting() }

    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = .45f, stiffness = 260f)) }
        launch { logoTilt.animateTo(0f, spring(dampingRatio = .5f, stiffness = 200f)) }
        launch { delay(220); emojiReveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        launch { delay(300); for (i in 1..title.length) { lettersShown = i; delay(55) } }
        progress.animateTo(1f, tween(1900, easing = FastOutSlowInEasing))
        delay(120)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF070816), Color(0xFF15123A), Color(0xFF071622)))),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.TopStart).offset(x = (-55).dp, y = (90 + drift).dp).size(230.dp).blur(54.dp).background(Color(0xFF755CFF).copy(alpha = .55f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 70.dp, y = (-90 - drift).dp).size(260.dp).blur(64.dp).background(Color(0xFF1DD6C0).copy(alpha = .38f), CircleShape))
        Box(Modifier.align(Alignment.Center).offset(x = 120.dp, y = (-170).dp).size(150.dp).blur(48.dp).background(Color(0xFFFF68B2).copy(alpha = .34f), CircleShape))

        // Rising emoji bubbles + twinkles.
        Canvas(Modifier.fillMaxSize()) {
            repeat(30) { i ->
                val seed = (i * 61 % 100) / 100f
                val p = (bubbles + seed) % 1f
                val x = size.width * ((seed * 2.3f + i * .031f) % 1f)
                val y = size.height * (1.05f - p * 1.1f)
                drawCircle(Color.White.copy(alpha = (sin(p * PI.toFloat()) * .5f).coerceAtLeast(0f)), radius = 1.2f + (i % 4) * .9f, center = Offset(x, y))
            }
        }
        SPLASH_EMOJI.take(5).forEachIndexed { i, e ->
            val seed = (i * 0.21f + 0.07f) % 1f
            val p = (bubbles * 0.8f + seed) % 1f
            Text(
                e, fontSize = (16 + i * 3).sp,
                modifier = Modifier.align(Alignment.BottomStart)
                    .offset(x = (40 + i * 68).dp, y = (-(p * 760)).dp)
                    .alpha((sin(p * PI.toFloat()) * .55f).coerceAtLeast(0f) * emojiReveal.value)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(196.dp).rotate(ring).border(1.5.dp, Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(.75f), Color.Transparent)), CircleShape))
                Box(Modifier.size(160.dp).rotate(-ring * .7f).border(2.dp, Brush.sweepGradient(listOf(Color(0xFF8A72FF), Color.Transparent, Color(0xFF36D8C4))), CircleShape))
                // Emoji orbit.
                SPLASH_EMOJI.forEachIndexed { i, e ->
                    val angle = (orbit + i * (360f / SPLASH_EMOJI.size)) * (PI.toFloat() / 180f)
                    val radius = 112f * emojiReveal.value
                    Text(
                        e, fontSize = 22.sp,
                        modifier = Modifier.offset(x = (cos(angle) * radius).dp, y = (sin(angle) * radius * .92f).dp)
                            .scale(.6f + .4f * emojiReveal.value + .12f * sin(angle * 2f))
                            .alpha(emojiReveal.value)
                    )
                }
                Box(
                    Modifier.size(124.dp).scale(logoScale.value).rotate(logoTilt.value).clip(RoundedCornerShape(38.dp))
                        .background(Color.White.copy(alpha = .13f))
                        .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(38.dp))
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ConvoLogo(Modifier.fillMaxSize(), size = 94.dp)
                    Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(36.dp).background(Brush.verticalGradient(listOf(Color.White.copy(.22f), Color.Transparent))))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row {
                title.forEachIndexed { index, c ->
                    val shown = index < lettersShown
                    val a by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "letter_a")
                    val dy by animateFloatAsState(if (shown) 0f else 14f, spring(dampingRatio = .5f), label = "letter_y")
                    Text(c.toString(), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.alpha(a).offset(y = dy.dp))
                }
            }
            Text("$greeting $greetingEmoji  •  Connect • Share • Belong", color = Color.White.copy(alpha = .7f * emojiReveal.value), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier.width(190.dp).clip(CircleShape).background(Color.White.copy(alpha = .09f))
                    .border(1.dp, Color.White.copy(alpha = .16f), CircleShape).padding(5.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.value).height(5.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF8A72FF), Color(0xFFFF68B2), Color(0xFF36D8C4))))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when { progress.value < .35f -> "Waking up your chats… 💬"; progress.value < .7f -> "Loading friends & stories… 📸"; else -> "Almost there… ✨" },
                color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelMedium
            )
        }

        Text(
            "Designed for better connections",
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(22.dp),
            color = Color.White.copy(alpha = .42f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
