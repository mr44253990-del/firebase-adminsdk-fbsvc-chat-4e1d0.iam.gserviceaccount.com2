package com.example.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ConvoStickerView
import com.example.ui.ConvoStickers
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Reactions shown in the in-call picker: Convo animated stickers first, then classic emoji. */
val CallReactionChoices: List<String> = ConvoStickers.all.take(6).map { it.token } + listOf("❤️", "😂", "👏", "🔥", "😮", "🎉")

private data class FloatingReaction(val id: Long, val token: String, val mine: Boolean, val startX: Float)

/** Measured from WebRTC stats every few seconds while connected. */
@Composable
fun CallQualityPill(connected: Boolean, accent: Color, modifier: Modifier = Modifier) {
    var rtt by remember { mutableStateOf<Int?>(null) }
    var loss by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(connected) {
        while (connected) {
            CallEngine.sampleQuality { r, l -> rtt = r; loss = l }
            delay(3000)
        }
    }
    if (!connected) return
    val level = when {
        rtt == null && loss == null -> 2
        (loss ?: 0.0) > 8.0 || (rtt ?: 0) > 450 -> 1
        (loss ?: 0.0) > 3.0 || (rtt ?: 0) > 220 -> 2
        else -> 3
    }
    val label = when (level) { 3 -> "Excellent"; 2 -> if (rtt == null) "Measuring" else "Good"; else -> "Low signal" }
    val color = when (level) { 3 -> Color(0xFF42E695); 2 -> Color(0xFFFFD166); else -> Color(0xFFFF6B6B) }
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = .38f)).border(1.dp, Color.White.copy(alpha = .16f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (bar in 1..3) {
                Box(Modifier.width(4.dp).height((5 + bar * 4).dp).clip(RoundedCornerShape(2.dp)).background(if (bar <= level) color else Color.White.copy(alpha = .25f)))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        rtt?.let { Text(" · ${it}ms", color = accent.copy(alpha = .9f), fontSize = 11.sp) }
    }
}

/** Listens for reactions in calls/{callId}/reactions and floats them up the screen on both phones. */
@Composable
fun CallReactionsLayer(active: Boolean, modifier: Modifier = Modifier) {
    val reactions = remember { mutableStateListOf<FloatingReaction>() }
    val haptics = LocalHapticFeedback.current
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    DisposableEffect(active) {
        val ref = if (active) CallEngine.reactionsRef() else null
        val startedAt = System.currentTimeMillis()
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val token = snapshot.child("token").getValue(String::class.java) ?: return
                val at = snapshot.child("at").getValue(Long::class.java) ?: 0L
                if (at in 1 until startedAt - 8000L) return
                val mine = snapshot.child("from").getValue(String::class.java) == myUid
                if (!mine) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (reactions.size > 18) reactions.removeAt(0)
                reactions.add(FloatingReaction(System.nanoTime(), token, mine, Random.nextFloat()))
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        val query = ref?.limitToLast(12)
        query?.addChildEventListener(listener)
        onDispose { query?.removeEventListener(listener) }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        reactions.toList().forEach { reaction ->
            key(reaction.id) {
                val rise = remember { Animatable(0f) }
                val sway = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    launch { sway.animateTo(1f, tween(2600, easing = LinearEasing)) }
                    rise.animateTo(1f, tween(2600, easing = FastOutSlowInEasing))
                    reactions.remove(reaction)
                }
                val baseX = if (reaction.mine) .55f + reaction.startX * .35f else .1f + reaction.startX * .35f
                val swing = kotlin.math.sin(sway.value * 6.28f * 1.5f) * 18f
                Box(
                    Modifier
                        .offset(x = width * baseX + swing.dp - 28.dp, y = height * (0.82f - rise.value * 0.62f))
                        .graphicsLayer {
                            val pop = if (rise.value < .12f) rise.value / .12f else 1f
                            scaleX = .6f + .55f * pop; scaleY = scaleX
                            alpha = if (rise.value > .7f) (1f - rise.value) / .3f else 1f
                        }
                ) { ReactionGlyph(reaction.token, 56) }
            }
        }
    }
}

@Composable
private fun ReactionGlyph(token: String, size: Int) {
    if (ConvoStickers.isSticker(token)) ConvoStickerView(token, size.dp)
    else Text(token, fontSize = (size * .7f).sp)
}

@Composable
fun CallReactionBar(accent: Color, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .20f), accent.copy(alpha = .22f), Color.White.copy(alpha = .14f))))
            .border(1.dp, Color.White.copy(alpha = .28f), RoundedCornerShape(30.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallReactionChoices.forEach { token ->
            Box(Modifier.size(46.dp).clip(CircleShape).clickable { onPick(token) }, contentAlignment = Alignment.Center) {
                ReactionGlyph(token, 38)
            }
        }
    }
}

/** Frosted circular control used in the call dock. */
@Composable
fun GlassCallButton(icon: ImageVector, label: String, active: Boolean = false, danger: Boolean = false, accent: Color = Color.White, size: Int = 50, onClick: () -> Unit) {
    val background = when {
        danger -> Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFE53935)))
        active -> Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = .86f)))
        else -> Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), accent.copy(alpha = .16f)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(background)
                .border(1.dp, Color.White.copy(alpha = if (danger) .0f else .30f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (active && !danger) Color(0xFF111827) else Color.White, modifier = Modifier.size((size * .45f).dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Slowly drifting aurora blobs behind the call UI. */
@Composable
fun CallAuroraBackground(accent: Color, modifier: Modifier = Modifier) {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "call_aurora")
    val t by motion.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "aurora_t")
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val angle = t * 6.2832f
        fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = androidx.compose.ui.geometry.Offset(cx, cy), radius = radius), radius, androidx.compose.ui.geometry.Offset(cx, cy))
        }
        blob(w * (.25f + .18f * kotlin.math.cos(angle)), h * (.22f + .08f * kotlin.math.sin(angle)), w * .75f, accent.copy(alpha = .32f))
        blob(w * (.78f + .14f * kotlin.math.sin(angle * 1.3f)), h * (.55f + .1f * kotlin.math.cos(angle)), w * .65f, Color(0xFFFF6AD5).copy(alpha = .18f))
        blob(w * (.4f + .2f * kotlin.math.sin(angle * .7f)), h * (.88f + .05f * kotlin.math.cos(angle * 1.6f)), w * .7f, Color(0xFF4DD0FF).copy(alpha = .2f))
    }
}
