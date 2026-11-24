package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val GOLD_A = Color(0xFFFFE08A)
private val GOLD_B = Color(0xFFFF9D2E)

/** Rotating spotlight: one benefit at a time, with a falling gold-dust backdrop. */
@Composable
fun PremiumSpotlightCard() {
    val items = remember {
        listOf(
            Triple(Icons.Default.Verified, "Verified badge", "A gold tick beside your name in chats, posts and calls."),
            Triple(Icons.Default.AutoAwesome, "Priority Convo AI", "Faster answers, all 12 AI Studio tools and smart group summaries."),
            Triple(Icons.Default.AutoFixHigh, "Every camera look", "All 20 call filters including Portrait light and Neon 2050."),
            Triple(Icons.Default.Palette, "Exclusive themes", "Liquid-glass chat themes, call skins and your own ringtone."),
            Triple(Icons.Default.Insights, "Creator insights", "See how your stories and posts perform."),
            Triple(Icons.Default.SupportAgent, "Priority support", "Talk to the admin directly and get answers first.")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3200); index = (index + 1) % items.size } }
    val fall by rememberInfiniteTransition(label = "gold_dust").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "fall")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1B05), Color(0xFF5A3A08), Color(0xFF2E1A4D))))
            .border(1.dp, Brush.linearGradient(listOf(GOLD_A, GOLD_B, Color(0xFFB07CFF))), RoundedCornerShape(28.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            repeat(26) { i ->
                val seed = (i * 53 % 100) / 100f
                val p = (fall + seed) % 1f
                drawCircle(GOLD_A.copy(alpha = (sin(p * PI.toFloat()) * .7f).coerceAtLeast(0f)), 1.2f + i % 3, Offset(size.width * ((seed * 1.9f + i * .043f) % 1f), size.height * p))
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text("WHY PREMIUM", color = GOLD_A, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(index, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(220)) }, label = "spotlight") { i ->
                val (icon, title, body) = items[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(GOLD_A, GOLD_B))), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color(0xFF3A2A06), modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(body, color = Color.White.copy(alpha = .8f), fontSize = 12.5.sp, lineHeight = 17.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.indices.forEach { i -> Box(Modifier.size(width = if (i == index) 18.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (i == index) GOLD_A else Color.White.copy(alpha = .25f)).clickable { index = i }) }
            }
        }
    }
}

/** Honest price helper: what each plan costs per day and how much the longer plans save. */
@Composable
fun PremiumSavingsCard(monthly: Int, yearly: Int, lifetime: Int) {
    if (monthly <= 0) return
    var months by remember { mutableFloatStateOf(12f) }
    val m = months.toInt().coerceAtLeast(1)
    val payMonthly = monthly * m
    val payYearly = if (yearly > 0) yearly * ((m + 11) / 12) else 0
    val best = listOfNotNull(
        "Monthly" to payMonthly,
        if (yearly > 0) "Yearly" to payYearly else null,
        if (lifetime > 0) "Lifetime" to lifetime else null
    ).minByOrNull { it.second }
    val saved by animateFloatAsState(((payMonthly - (best?.second ?: payMonthly)).coerceAtLeast(0)).toFloat(), tween(450, easing = FastOutSlowInEasing), label = "saved")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
            .border(1.dp, GOLD_B.copy(alpha = .4f), RoundedCornerShape(26.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Calculate, null, tint = GOLD_B)
            Spacer(Modifier.width(8.dp))
            Text("Smart plan picker", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Text("How long do you plan to use Premium?  $m month${if (m == 1) "" else "s"}", fontSize = 13.sp)
        Slider(value = months, onValueChange = { months = it }, valueRange = 1f..36f, steps = 34, colors = SliderDefaults.colors(thumbColor = GOLD_B, activeTrackColor = GOLD_B))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceCell("Monthly", payMonthly, best?.first == "Monthly", Modifier.weight(1f))
            if (yearly > 0) PriceCell("Yearly", payYearly, best?.first == "Yearly", Modifier.weight(1f))
            if (lifetime > 0) PriceCell("Lifetime", lifetime, best?.first == "Lifetime", Modifier.weight(1f))
        }
        Text(
            if (saved >= 1f) "Best for you: ${best?.first} — you keep ৳${saved.toInt()} compared with paying monthly." else "For this period the monthly plan is the cheapest.",
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A)
        )
        Text("≈ ৳${"%.1f".format((best?.second ?: payMonthly) / (m * 30f))} per day", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PriceCell(label: String, total: Int, best: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (best) Brush.linearGradient(listOf(GOLD_A, GOLD_B)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .border(1.dp, if (best) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = .3f), RoundedCornerShape(16.dp)).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (best) Color(0xFF3A2A06) else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("৳$total", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (best) Color(0xFF3A2A06) else MaterialTheme.colorScheme.onSurface)
        if (best) Text("BEST", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A2A06), letterSpacing = 1.sp)
    }
}

/** Shortcuts to the smart tools of the app, shown on the Premium page as "what you can do today". */
@Composable
fun PremiumToolShelf() {
    data class Tool(val icon: ImageVector, val title: String, val route: String, val tint: Color)
    val tools = listOf(
        Tool(Icons.Default.AutoFixHigh, "AI Studio", "ai_studio", Color(0xFF8B7BFF)),
        Tool(Icons.Default.AutoAwesome, "Convo AI", "assistant", Color(0xFF36D8E6)),
        Tool(Icons.Default.TravelExplore, "Smart Hub", "smart_hub", Color(0xFF19C37D)),
        Tool(Icons.Default.Shield, "Permissions", "permissions", Color(0xFFFF8A3D)),
        Tool(Icons.Default.NewReleases, "What's new", "whats_new", Color(0xFFFF6FB5))
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Smart tools", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tools.forEach { t ->
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(t.tint.copy(alpha = .22f), t.tint.copy(alpha = .06f))))
                        .border(1.dp, t.tint.copy(alpha = .45f), RoundedCornerShape(22.dp)).clickable { AppNav.open(t.route) }.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(t.tint.copy(alpha = .28f)), contentAlignment = Alignment.Center) { Icon(t.icon, null, tint = t.tint) }
                    Spacer(Modifier.height(8.dp))
                    Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
