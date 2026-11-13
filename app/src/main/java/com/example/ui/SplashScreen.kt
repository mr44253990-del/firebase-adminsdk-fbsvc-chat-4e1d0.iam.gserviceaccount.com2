package com.example.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SPLASH_INK = Color(0xFF07070F)
private val SPLASH_VIOLET = Color(0xFF8B7BFF)
private val SPLASH_CYAN = Color(0xFF5AC8FA)
private val SPLASH_PINK = Color(0xFFFF6FB5)
private val SPLASH_MINT = Color(0xFF42E695)

private fun splashGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "শুভ সকাল"
    in 12..16 -> "শুভ দুপুর"
    in 17..19 -> "শুভ সন্ধ্যা"
    else -> "শুভ রাত্রি"
}

/** The live checklist shown under the wordmark; each row ticks as loading progresses. */
private data class SplashStep(val icon: ImageVector, val label: String)

private fun splashSteps(): List<SplashStep> = listOf(
    SplashStep(Icons.Default.CloudDone, "Restoring your chats"),
    SplashStep(Icons.Default.Lock, "Securing your session"),
    SplashStep(Icons.Default.AutoAwesome, "Syncing friends & stories"),
    SplashStep(Icons.Default.Check, "Almost there")
)

/**
 * Launch screen — the whole boot experience lives here now.
 *
 *  - The MFA/session precheck that used to run on a separate "Securing your session" gate screen
 *    runs IN PARALLEL with this loading screen, so that extra full-screen step is gone.
 *  - The loading bar tracks real progress (brand intro + precheck), not a fake timer.
 *  - A live checklist, orbiting particles, greeting by time of day and a build stamp keep it
 *    feeling premium while the real work completes underneath.
 *
 * [gateReady] is false while the background precheck is still running; the splash waits for it
 * (with a hard cap of ~9 s) before calling [onFinished], so Home never flashes and no extra
 * "sequence" screen is ever shown.
 */
@Composable
fun SplashScreen(gateReady: Boolean = true, onFinished: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "splash_motion")
    val orbit by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(7200, easing = LinearEasing)), label = "orbit")
    val counter by motion.animateFloat(360f, 0f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "counter")
    val breathe by motion.animateFloat(.94f, 1.06f, infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val sparkle by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "sparkle")

    val markScale = remember { Animatable(.55f) }
    val markAlpha = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    var lettersShown by remember { mutableIntStateOf(0) }
    val title = "Convo Chat"
    val greeting = remember { splashGreeting() }
    val steps = remember { splashSteps() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember { appVersionLabel(context) }

    LaunchedEffect(gateReady) {
        launch {
            launch { markAlpha.animateTo(1f, tween(260)) }
            launch { markScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f)) }
            launch { delay(90); halo.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
            launch {
                delay(140)
                for (i in 1..title.length) { lettersShown = i; delay(32) }
            }
            // Real progress: the brand intro animates to 88%, and the last stretch completes
            // only when the background session precheck is done (capped at 9 s).
            launch {
                progress.animateTo(.88f, tween(900, easing = FastOutSlowInEasing))
                val started = System.currentTimeMillis()
                while (!gateReady && System.currentTimeMillis() - started < 9000L) delay(90)
                progress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            }
        }
        // Minimum brand time (1.15 s) AND the real work done before we continue.
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < 1150L) delay(60)
        val gateStart = System.currentTimeMillis()
        while (!gateReady && System.currentTimeMillis() - gateStart < 9000L) delay(90)
        onFinished()
    }

    val settled by animateFloatAsState(if (markAlpha.value > .5f) 1f else 0f, tween(400), label = "settled")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF080711), Color(0xFF151238), Color(0xFF0A1024), Color(0xFF06060F)),
                    start = Offset.Zero,
                    end = Offset(1400f, 1800f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Aurora depth.
        Box(
            Modifier.align(Alignment.TopStart).padding(top = 60.dp)
                .size(240.dp).blur(70.dp)
                .alpha(.55f * settled)
                .background(SPLASH_VIOLET, CircleShape)
        )
        Box(
            Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp)
                .size(260.dp).blur(80.dp)
                .alpha(.38f * settled)
                .background(SPLASH_CYAN, CircleShape)
        )
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
                .size(150.dp).blur(60.dp)
                .alpha(.30f * settled)
                .background(SPLASH_PINK, CircleShape)
        )

        // Particle field: tiny rising sparks plus a slow twinkle in the corners.
        Canvas(Modifier.fillMaxSize().alpha(settled)) {
            repeat(40) { index ->
                val seed = ((index * 61) % 100) / 100f
                val phase = (sparkle + seed) % 1f
                val x = size.width * ((seed * 2.3f + index * .029f) % 1f)
                val y = size.height * (1.04f - phase * 1.08f)
                val alpha = (sin(phase * PI.toFloat()) * .45f).coerceAtLeast(0f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 1.1f + (index % 4) * .8f,
                    center = Offset(x, y)
                )
            }
            val center = Offset(size.width / 2f, size.height * .38f)
            repeat(3) { ray ->
                val radius = 120f + ray * 46f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(SPLASH_VIOLET.copy(alpha = .12f - ray * .03f), Color.Transparent),
                        center,
                        radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(218.dp), contentAlignment = Alignment.Center) {
                // Progress ring: draws once, tracks the real loading progress.
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f - 6f
                    drawCircle(
                        color = Color.White.copy(alpha = .10f),
                        radius = radius,
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(SPLASH_VIOLET, SPLASH_CYAN, SPLASH_PINK, SPLASH_VIOLET)),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                // Two counter-rotating orbit arcs.
                Canvas(Modifier.fillMaxSize().scale(breathe)) {
                    val c = center
                    val r = size.minDimension / 2f - 26f
                    rotate(orbit) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.Transparent),
                                c
                            ),
                            startAngle = 0f, sweepAngle = 150f, useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    rotate(counter) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, SPLASH_CYAN.copy(alpha = .75f), Color.Transparent),
                                c
                            ),
                            startAngle = 0f, sweepAngle = 96f, useCenter = false,
                            topLeft = Offset(c.x - r * .82f, c.y - r * .82f),
                            size = androidx.compose.ui.geometry.Size(r * 1.64f, r * 1.64f),
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                // Orbiting glass dots.
                repeat(3) { index ->
                    val angle = (orbit + index * 120f) * (PI.toFloat() / 180f)
                    val radius = 88f
                    Box(
                        Modifier
                            .offsetPx(x = cos(angle) * radius, y = sin(angle) * radius * .9f)
                            .size(9.dp)
                            .alpha(.75f * settled)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(SPLASH_VIOLET, SPLASH_PINK),
                                    start = Offset.Zero,
                                    end = Offset(20f, 20f)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = .55f), CircleShape)
                    )
                }
                // The mark itself.
                Box(
                    modifier = Modifier
                        .size(122.dp)
                        .scale(markScale.value * (0.99f + (breathe - 1f) * .1f))
                        .alpha(markAlpha.value)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .05f)),
                                start = Offset.Zero,
                                end = Offset(180f, 180f)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = .34f), RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ConvoLogo(Modifier.fillMaxSize().padding(16.dp), size = 92.dp)
                    Box(
                        Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth(.82f)
                            .height(34.dp)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .alpha(.5f)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = .34f), Color.Transparent)
                                )
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Wordmark: letters cascade, the second word carries the gradient.
            Row(verticalAlignment = Alignment.Bottom) {
                title.forEachIndexed { index, char ->
                    val shown = index < lettersShown
                    val letterAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "letter_alpha")
                    val letterOffset by animateFloatAsState(if (shown) 0f else 12f, spring(dampingRatio = .6f, stiffness = 520f), label = "letter_y")
                    Text(
                        text = char.toString(),
                        color = if (index >= 6) Color.Transparent else Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp,
                            letterSpacing = (-1).sp,
                            brush = if (index >= 6) {
                                Brush.linearGradient(listOf(SPLASH_CYAN, SPLASH_VIOLET, SPLASH_PINK))
                            } else null
                        ),
                        modifier = Modifier.alpha(letterAlpha).offsetPx(y = letterOffset)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(18.dp).height(1.5.dp).background(SPLASH_CYAN.copy(alpha = .8f), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = greeting,
                    color = Color.White.copy(alpha = .82f * settled),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .4.sp
                )
                Spacer(Modifier.width(7.dp))
                Box(Modifier.width(18.dp).height(1.5.dp).background(SPLASH_PINK.copy(alpha = .8f), CircleShape))
            }

            Spacer(Modifier.height(22.dp))

            // Live checklist: ticks follow the real progress, replacing the old gate screen.
                steps.forEachIndexed { index, step ->
                val done = progress.value >= (index + 1) / steps.size.toFloat()
                val alpha by animateFloatAsState(if (index == 0 || progress.value > index * .25f) 1f else .35f, tween(300), label = "step_alpha")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp).alpha(alpha)
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .background(if (done) SPLASH_MINT.copy(alpha = .22f) else Color.White.copy(alpha = .10f))
                            .border(1.dp, if (done) SPLASH_MINT.copy(alpha = .8f) else Color.White.copy(alpha = .22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) Icon(Icons.Default.Check, null, tint = SPLASH_MINT, modifier = Modifier.size(13.dp))
                        else Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .5f)))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        step.label,
                        color = if (done) Color.White.copy(alpha = .9f) else Color.White.copy(alpha = .55f),
                        fontSize = 12.sp,
                        fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Slim determinate bar mirrors the ring so progress is obvious at a glance.
            Box(
                Modifier.width(200.dp).height(4.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = .10f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.value.coerceIn(0.02f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(SPLASH_VIOLET, SPLASH_CYAN, SPLASH_PINK)))
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(SPLASH_CYAN))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Private · Secure · Always yours",
                    color = Color.White.copy(alpha = .40f),
                    fontSize = 10.sp,
                    letterSpacing = .5.sp
                )
            }
            Text(
                versionName,
                color = Color.White.copy(alpha = .28f),
                fontSize = 9.sp,
                letterSpacing = .6.sp
            )
        }
    }
}

/** Reads the installed version label for the splash footer (e.g. "Convo Chat · v4.9.3"). */
private fun appVersionLabel(context: android.content.Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "Convo Chat · v${info.versionName ?: ""}".trim()
}.getOrDefault("Convo Chat")

/** Offset in pixels without converting to dp, so orbit radii stay physically constant. */
private fun Modifier.offsetPx(x: Float = 0f, y: Float = 0f) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(x.toInt(), y.toInt())
        }
    }
)
