package com.example.ui

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CoachStep(val key: String?, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val body: String)

/** First-launch guided tour: spotlight on a real UI element, blurred/dimmed surroundings. */
object CoachTour {
    private const val PREFS = "convo_tour"
    val targets = mutableStateMapOf<String, Rect>()
    /** Key of the element highlighted right now (read by [coachBlurHost]). */
    val activeKey = mutableStateOf<String?>(null)
    /**
     * One-shot replay request. It is CONSUMED by Home when the tour starts; a plain counter
     * used to stay > 0 forever, so every return to Home (e.g. leaving a chat) replayed the tour.
     */
    private val _replay = MutableStateFlow(false)
    val replay: StateFlow<Boolean> = _replay.asStateFlow()
    fun consumeReplay(): Boolean { val pending = _replay.value; _replay.value = false; return pending }

    fun isDone(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("done_v1", false)

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done_v1", true).apply()
    }

    fun requestReplay() { _replay.value = true }

    val homeSteps = listOf(
        CoachStep(null, Icons.Default.AutoAwesome, "Welcome to Convo", "A quick tour of the main buttons. It takes 20 seconds."),
        CoachStep("nav_home", Icons.Default.Home, "Home feed", "Posts, stories and updates from your friends."),
        CoachStep("nav_chats", Icons.Default.Forum, "Chats", "All your conversations. In a chat, type / for quick replies."),
        CoachStep("nav_create", Icons.Default.AddCircle, "Create", "Post, story, reel or poll — everything starts here."),
        CoachStep("nav_slot4", Icons.Default.SmartDisplay, "Reels & groups", "Watch reels or open your groups from here."),
        CoachStep("nav_friends", Icons.Default.PeopleAlt, "Friends", "Friend requests and people you may know."),
        CoachStep(null, Icons.Default.Verified, "You're all set!", "Change the language or replay this tour any time in Settings.")
    )
}

/** Registers this element as a tour target (bounds in root coordinates). */
fun Modifier.coachTarget(key: String): Modifier = this.onGloballyPositioned { CoachTour.targets[key] = it.boundsInRoot() }

/**
 * Applied to the screen root while the tour runs: on Android 12+ the whole screen is blurred
 * except the highlighted element, which is redrawn sharp on top.
 */
@Composable
fun Modifier.coachBlurHost(active: Boolean, spotlightInRoot: Rect?): Modifier {
    val layer = rememberGraphicsLayer()
    var hostOffset by remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return this
        .onGloballyPositioned { hostOffset = it.positionInRoot() }
        .then(
            if (active && canBlur) Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                layer.renderEffect = BlurEffect(34f, 34f, TileMode.Clamp)
                drawLayer(layer)
                val spot = spotlightInRoot
                if (spot != null) {
                    val local = spot.translate(-hostOffset).inflate(10f)
                    val path = Path().apply { addRoundRect(RoundRect(local, CornerRadius(26f, 26f))) }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
            } else Modifier
        )
}

@Composable
fun CoachMarkOverlay(steps: List<CoachStep>, onFinish: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var index by remember { mutableIntStateOf(0) }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    var cardHeight by remember { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    LaunchedEffect(step.key) { CoachTour.activeKey.value = step.key }
    DisposableEffect(Unit) { onDispose { CoachTour.activeKey.value = null } }
    val target = step.key?.let { CoachTour.targets[it] }?.translate(-overlayOffset)?.inflate(12f)
    val centre = Rect(Offset(overlaySize.width / 2f, overlaySize.height / 2f), 0f)
    val animated by animateRectAsState(target ?: centre, tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "coach_rect")
    val spotAlpha by animateFloatAsState(if (target != null) 1f else 0f, tween(320), label = "coach_alpha")
    val progress by animateFloatAsState((index + 1f) / steps.size, tween(480), label = "coach_progress")
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "coach_fx")
    val ripple by infinite.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1600)), label = "ripple")
    val spin by infinite.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(5200, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val drift by infinite.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(7000), androidx.compose.animation.core.RepeatMode.Reverse), label = "drift")
    val finish = {
        CoachTour.markDone(context)
        onFinish()
    }
    val accentA = Color(0xFF7C5CFF); val accentB = Color(0xFFFF4FD8); val accentC = Color(0xFF29D3FF)
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInRoot(); overlaySize = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* swallow taps */ }
    ) {
        // Tinted frosted scrim with drifting aurora light, a cut-out spotlight, pulse rings and a spinning gradient frame.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            drawRect(Brush.verticalGradient(listOf(Color(0xFF07061A).copy(alpha = if (blurOk) .50f else .80f), Color(0xFF1A0B33).copy(alpha = if (blurOk) .62f else .86f))))
            val w = size.width; val h = size.height
            if (w > 1f && h > 1f) {
                val r1 = (w * .55f).coerceAtLeast(1f)
                drawCircle(Brush.radialGradient(listOf(accentA.copy(alpha = .30f), Color.Transparent), Offset(w * (.15f + .2f * drift), h * .18f), r1), r1, Offset(w * (.15f + .2f * drift), h * .18f))
                drawCircle(Brush.radialGradient(listOf(accentB.copy(alpha = .22f), Color.Transparent), Offset(w * (.9f - .25f * drift), h * .78f), r1), r1, Offset(w * (.9f - .25f * drift), h * .78f))
            }
            if (spotAlpha > 0f && animated.width > 1f) {
                val r = CornerRadius(30f, 30f)
                drawRoundRect(Color.Black, animated.topLeft, animated.size, r, blendMode = BlendMode.Clear, alpha = spotAlpha)
                // Two expanding pulse rings.
                for (k in 0..1) {
                    val t = (ripple + k * .5f) % 1f
                    val grow = 4.dp.toPx() + t * 22.dp.toPx()
                    drawRoundRect(accentC.copy(alpha = (1f - t) * .55f * spotAlpha), animated.topLeft - Offset(grow, grow), Size(animated.width + grow * 2, animated.height + grow * 2), CornerRadius(30f + grow, 30f + grow), style = Stroke(width = 2.dp.toPx()))
                }
                val rad = Math.toRadians(spin.toDouble())
                val c = animated.center
                val reach = maxOf(animated.width, animated.height)
                val from = Offset(c.x + (kotlin.math.cos(rad) * reach).toFloat(), c.y + (kotlin.math.sin(rad) * reach).toFloat())
                val to = Offset(c.x - (kotlin.math.cos(rad) * reach).toFloat(), c.y - (kotlin.math.sin(rad) * reach).toFloat())
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFD36E), accentB, accentA, accentC), from, to), animated.topLeft, animated.size, r, style = Stroke(width = 3.dp.toPx()), alpha = spotAlpha)
            }
        }
        // Tooltip card: below the spotlight when it is in the top half, otherwise above it.
        val marginPx = with(density) { 22.dp.toPx() }
        val cardY: Float = when {
            target == null -> (overlaySize.height - cardHeight) / 2f
            target.center.y < overlaySize.height / 2f -> target.bottom + marginPx
            else -> target.top - marginPx - cardHeight
        }.let { y ->
            // Before the first layout pass overlaySize is 0, so the range could be empty
            // (min > max). coerceIn() throws on an empty range, which crashed Home on launch.
            val minY = with(density) { 40.dp.toPx() }
            val maxY = overlaySize.height - cardHeight - marginPx
            if (maxY <= minY) minY.coerceAtMost(maxOf(0f, maxY)) else y.coerceIn(minY, maxY)
        }
        Box(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, cardY.toInt()) }
                .onGloballyPositioned { cardHeight = it.size.height }
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xF2171433), Color(0xF2251B47), Color(0xF2121A33))))
                .border(androidx.compose.foundation.BorderStroke(1.2.dp, Brush.linearGradient(listOf(accentC.copy(alpha = .7f), accentB.copy(alpha = .55f), accentA.copy(alpha = .7f)))), RoundedCornerShape(30.dp))
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(52.dp).graphicsLayer { rotationZ = spin }.clip(RoundedCornerShape(18.dp)).background(Brush.sweepGradient(listOf(accentA, accentB, accentC, accentA))))
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF1B1638)), contentAlignment = Alignment.Center) {
                            Icon(step.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "%02d / %02d".format(index + 1, steps.size),
                            fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
                            color = accentC, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        androidx.compose.animation.AnimatedContent(step.title, label = "coach_title") { title ->
                            Text(tr(title), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color.White)
                        }
                    }
                    if (index < steps.lastIndex) TextButton(onClick = finish) { Text(tr("Skip"), color = Color.White.copy(alpha = .7f)) }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.animation.AnimatedContent(step.body, label = "coach_body") { body ->
                    Text(tr(body), fontSize = 14.sp, color = Color.White.copy(alpha = .82f), lineHeight = 21.sp)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(accentC, accentB, accentA))))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .10f)).clickable { index-- },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.ArrowBack, tr("Back"), tint = Color.White) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(accentA, accentB)))
                            .clickable { if (index < steps.lastIndex) index++ else finish() }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (index < steps.lastIndex) tr("Next") else tr("Got it"), fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Icon(if (index < steps.lastIndex) Icons.Default.ArrowForward else Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
