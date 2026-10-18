package com.example.ui

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    private val _replay = MutableStateFlow(0)
    val replay: StateFlow<Int> = _replay.asStateFlow()

    fun isDone(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("done_v1", false)

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done_v1", true).apply()
    }

    fun requestReplay() { _replay.value = _replay.value + 1 }

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
                layer.renderEffect = BlurEffect(22f, 22f, TileMode.Clamp)
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
    val target = step.key?.let { CoachTour.targets[it] }?.translate(-overlayOffset)?.inflate(10f)
    val centre = Rect(Offset(overlaySize.width / 2f, overlaySize.height / 2f), 0f)
    val animated by animateRectAsState(target ?: centre, tween(420), label = "coach_rect")
    val spotAlpha by animateFloatAsState(if (target != null) 1f else 0f, tween(300), label = "coach_alpha")
    val finish = {
        CoachTour.markDone(context)
        onFinish()
    }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInRoot(); overlaySize = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* swallow taps */ }
    ) {
        // Dim scrim with a cut-out spotlight and a glowing ring.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val scrim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 0.74f
            drawRect(Color(0xFF0B0820).copy(alpha = scrim))
            if (spotAlpha > 0f) {
                val r = CornerRadius(26f, 26f)
                drawRoundRect(Color.Black, animated.topLeft, animated.size, r, blendMode = BlendMode.Clear, alpha = spotAlpha)
                drawRoundRect(
                    Brush.linearGradient(listOf(Color(0xFFFFD36E), Color(0xFFFF4FD8), Color(0xFF7C5CFF)), animated.topLeft, animated.bottomRight),
                    animated.topLeft, animated.size, r, style = Stroke(width = 3.dp.toPx()), alpha = spotAlpha
                )
            }
        }
        // Tooltip card: below the spotlight when it is in the top half, otherwise above it.
        val marginPx = with(density) { 18.dp.toPx() }
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
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, cardY.toInt()) }
                .onGloballyPositioned { cardHeight = it.size.height }
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8)))),
                        contentAlignment = Alignment.Center
                    ) { Icon(step.icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(step.title), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("${index + 1} / ${steps.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(tr(step.body), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier.padding(end = 5.dp).height(6.dp).width(if (i == index) 18.dp else 6.dp).clip(CircleShape)
                                .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (index < steps.lastIndex) TextButton(onClick = finish) { Text(tr("Skip")) }
                    if (index > 0) TextButton(onClick = { index-- }) { Text(tr("Back")) }
                    Button(onClick = { if (index < steps.lastIndex) index++ else finish() }, shape = RoundedCornerShape(16.dp)) {
                        Text(if (index < steps.lastIndex) tr("Next") else tr("Got it"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
