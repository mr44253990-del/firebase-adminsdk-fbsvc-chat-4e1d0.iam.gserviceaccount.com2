package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val NOVA_VIOLET = Color(0xFF8B7BFF)
private val NOVA_CYAN = Color(0xFF36D8E6)
private val NOVA_PINK = Color(0xFFFF6FB5)

/**
 * "Nova" depth layer for the sign-in screen (v5.0): a slow constellation of nodes that link up
 * when they drift close together, plus a soft horizon grid — the 2050 look, drawn in code so it
 * stays sharp on phones and large tablets alike.
 */
@Composable
fun LoginNovaLayer(milk: Boolean, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "nova")
    val t by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(48_000, easing = LinearEasing)), label = "nova_t")
    val glow by motion.animateFloat(.55f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "nova_glow")
    val ink = if (milk) Color(0xFF3B2F8F) else Color.White
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        if (w < 2f || h < 2f) return@Canvas
        val count = if (w > 1400f) 34 else 22
        val points = List(count) { i ->
            val seedX = ((i * 73) % 100) / 100f
            val seedY = ((i * 37 + 11) % 100) / 100f
            val phase = t * 2f * PI.toFloat()
            Offset(
                w * (seedX + .06f * sin(phase * (1 + i % 3) + i)).mod(1f),
                h * (seedY + .05f * cos(phase * (1 + i % 2) + i * 1.7f)).mod(1f)
            )
        }
        val link = w * .24f
        for (a in points.indices) for (b in a + 1 until points.size) {
            val d = hypot(points[a].x - points[b].x, points[a].y - points[b].y)
            if (d < link) drawLine(
                color = (if ((a + b) % 2 == 0) NOVA_CYAN else NOVA_VIOLET).copy(alpha = (1f - d / link) * (if (milk) .16f else .22f)),
                start = points[a], end = points[b], strokeWidth = 1.2f, cap = StrokeCap.Round
            )
        }
        points.forEachIndexed { i, p ->
            val r = 1.6f + (i % 4) * .9f
            drawCircle(Brush.radialGradient(listOf((if (i % 3 == 0) NOVA_PINK else NOVA_CYAN).copy(alpha = .35f * glow), Color.Transparent), p, r * 7f), r * 7f, p)
            drawCircle(ink.copy(alpha = if (milk) .35f else .7f), r, p)
        }
        // Horizon grid fading into the bottom edge.
        val horizon = h * .80f
        repeat(7) { k ->
            val y = horizon + (h - horizon) * ((k + (t * 40f).mod(1f)) / 7f).let { it * it }
            drawLine(ink.copy(alpha = .05f + .04f * k / 7f), Offset(0f, y), Offset(w, y), 1f)
        }
        repeat(11) { k ->
            val x = w * k / 10f
            drawLine(ink.copy(alpha = .045f), Offset(w / 2f + (x - w / 2f) * .25f, horizon), Offset(w / 2f + (x - w / 2f) * 1.6f, h), 1f)
        }
    }
}

/** Time-aware greeting plus a typewriter line that keeps rewriting itself. */
@Composable
fun LoginGreetingTypewriter(textColor: Color, softColor: Color) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "শুভ সকাল ☀️"
            in 12..16 -> "শুভ দুপুর 🌤️"
            in 17..19 -> "শুভ সন্ধ্যা 🌇"
            else -> "শুভ রাত্রি 🌙"
        }
    }
    val phrases = remember {
        listOf(
            "Chat, call and share — all in one place.",
            "Group calls everyone can join with one tap.",
            "Convo AI that finds people and answers anything.",
            "Your calls follow you with a floating bubble.",
            "Private by design. Always yours."
        )
    }
    var index by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            val phrase = phrases[index]
            for (i in 1..phrase.length) { shown = i; delay(26) }
            delay(2100)
            for (i in phrase.length downTo 0 step 2) { shown = i; delay(10) }
            shown = 0
            index = (index + 1) % phrases.size
        }
    }
    val caret by rememberInfiniteTransition(label = "caret").animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "caret_a")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(greeting, color = softColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .4.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
            Text(phrases[index].take(shown.coerceAtMost(phrases[index].length)), color = textColor.copy(alpha = .86f), fontSize = 12.5.sp, textAlign = TextAlign.Center, maxLines = 1)
            Box(Modifier.padding(start = 2.dp).size(width = 2.dp, height = 14.dp).background(NOVA_CYAN.copy(alpha = caret)))
        }
    }
}

private data class LoginFeature(val icon: ImageVector, val title: String, val body: String, val tint: Color)

/** Auto-rotating "what's inside" card, so the sign-in screen sells the app while the user decides. */
@Composable
fun LoginFeatureCarousel(textColor: Color, softColor: Color, modifier: Modifier = Modifier) {
    val features = remember {
        listOf(
            LoginFeature(Icons.Default.Groups, "Smart group calls", "A Join bar appears for every member. Add people, switch audio → video.", NOVA_CYAN),
            LoginFeature(Icons.Default.AutoAwesome, "Convo AI with database tools", "Ask it to find any profile or summarise a busy group.", NOVA_VIOLET),
            LoginFeature(Icons.Default.PictureInPicture, "Calls that follow you", "A floating call bubble stays on top of every app.", Color(0xFF19C37D)),
            LoginFeature(Icons.Default.AutoFixHigh, "20 camera looks", "Portrait light, Golden hour, Film, Neon 2050 and a selfie light.", NOVA_PINK),
            LoginFeature(Icons.Default.SwitchAccount, "Many accounts, one phone", "Get a Switch prompt when another account receives a call or message.", Color(0xFFFFB03A))
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3600); index = (index + 1) % features.size } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = index,
            transitionSpec = { (slideInVertically(tween(420)) { it / 2 } + fadeIn(tween(420))) togetherWith (slideOutVertically(tween(320)) { -it / 2 } + fadeOut(tween(240))) },
            label = "login_feature"
        ) { i ->
            val f = features[i]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(f.tint.copy(alpha = .16f), textColor.copy(alpha = .04f))))
                    .border(1.dp, Brush.horizontalGradient(listOf(f.tint.copy(alpha = .55f), textColor.copy(alpha = .08f))), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(f.tint.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    Icon(f.icon, null, tint = f.tint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(f.body, color = softColor, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
                }
                Text("NEW", color = f.tint, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            features.indices.forEach { i ->
                Box(Modifier.size(width = if (i == index) 16.dp else 5.dp, height = 5.dp).clip(CircleShape).background(if (i == index) NOVA_CYAN else textColor.copy(alpha = .22f)))
            }
        }
    }
}
