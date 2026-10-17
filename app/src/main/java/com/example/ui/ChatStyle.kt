package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Device-local look of chat bubbles. Every value is clamped by [sanitized]. */
@Immutable
data class ChatAppearance(
    /** auto, liquid, neon, outline, solid, minimal */
    val bubbleStyle: String = "auto",
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 20f,
    val tail: Boolean = true,
    val maxWidthFraction: Float = .80f,
    /** Space between the screen edge and bubbles. */
    val sidePadding: Float = 8f,
    /** Space between the text and the bubble edge. */
    val innerPadding: Float = 10f,
    val messageSpacing: Float = 6f,
    val use24h: Boolean = false,
    val backgroundDim: Float = 0f,
    val glow: Boolean = true,
    val animateReactions: Boolean = true,
    /** Entrance animation for new messages: none, fade, slide, pop, bounce, swing. */
    val messageAnimation: String = "pop"
) {
    fun sanitized() = copy(
        bubbleStyle = bubbleStyle.takeIf { it in STYLES } ?: "auto",
        borderWidth = borderWidth.coerceIn(0f, 3f),
        cornerRadius = cornerRadius.coerceIn(4f, 30f),
        maxWidthFraction = maxWidthFraction.coerceIn(.6f, .95f),
        sidePadding = sidePadding.coerceIn(0f, 24f),
        innerPadding = innerPadding.coerceIn(4f, 16f),
        messageSpacing = messageSpacing.coerceIn(1f, 16f),
        backgroundDim = backgroundDim.coerceIn(0f, .7f),
        messageAnimation = messageAnimation.takeIf { it in ANIMATIONS } ?: "pop"
    )

    fun resolvedStyle(spec: ChatThemeSpec?): String =
        if (bubbleStyle == "auto") spec?.defaultBubbleStyle ?: "liquid" else bubbleStyle

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat(if (use24h) "HH:mm" else "hh:mm a", Locale.getDefault()).format(Date(timestamp))

    companion object {
        val STYLES = listOf("auto", "liquid", "neon", "gradient", "frosted", "depth", "outline", "solid", "minimal")
        val ANIMATIONS = listOf("none", "fade", "slide", "pop", "bounce", "swing")
        fun animationLabel(id: String) = when (id) {
            "none" -> "Off"
            "fade" -> "Fade"
            "slide" -> "Slide in"
            "pop" -> "Pop"
            "bounce" -> "Bounce up"
            "swing" -> "Swing"
            else -> id
        }
        fun label(style: String) = when (style) {
            "gradient" -> "Gradient"
            "frosted" -> "Frosted glass"
            "depth" -> "3D depth"
            "auto" -> "Auto"
            "liquid" -> "Liquid glass"
            "neon" -> "Neon glow"
            "outline" -> "Outline"
            "solid" -> "Solid"
            "minimal" -> "No border"
            else -> style
        }
    }
}

/** Resolved colors for one bubble. */
@Immutable
data class BubblePalette(
    val fill: Color,
    val fillTop: Color,
    val border: Color,
    val text: Color,
    val meta: Color,
    val accent: Color,
    val glow: Color,
    val label: Color
)

@Composable
fun rememberBubblePalette(isSent: Boolean, spec: ChatThemeSpec?, bubbleAccent: Color?, style: String): BubblePalette {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < .5f
    return remember(isSent, spec, bubbleAccent, style, scheme) {
        val accent = spec?.accent ?: bubbleAccent ?: scheme.primary
        val baseBackground = when {
            spec?.isScene == true -> if (spec.light) Color(0xFFF6F0FF) else Color(0xFF0B0B16)
            spec != null -> spec.colors.first().copy(alpha = 1f)
            else -> scheme.background
        }
        var fill = when {
            spec?.isScene == true -> (if (isSent) spec.outgoingFill else spec.incomingFill) ?: accent.copy(alpha = .6f)
            isSent -> bubbleAccent?.copy(alpha = .94f) ?: scheme.primary.copy(alpha = if (isDark) .32f else .86f)
            else -> scheme.surfaceVariant.copy(alpha = if (isDark) .62f else .92f)
        }
        var border = when {
            spec?.isScene == true -> accent.copy(alpha = if (isSent) .95f else .55f)
            isSent -> accent.copy(alpha = .28f)
            else -> scheme.onSurface.copy(alpha = .10f)
        }
        when (style) {
            "outline" -> {
                fill = fill.copy(alpha = (fill.alpha * .22f).coerceAtMost(.25f))
                border = if (isSent) accent else scheme.onSurface.copy(alpha = .38f).let { if (spec?.isScene == true) accent.copy(alpha = .7f) else it }
            }
            "solid" -> {
                fill = fill.copy(alpha = 1f).compositeOver(baseBackground)
                border = Color.Transparent
            }
            "minimal" -> {
                fill = if (isSent) fill.copy(alpha = (fill.alpha * .75f).coerceAtLeast(.45f)) else fill.copy(alpha = fill.alpha * .55f)
                border = Color.Transparent
            }
            "neon" -> border = accent.copy(alpha = if (isSent) 1f else .75f)
            "gradient", "depth" -> {
                fill = fill.copy(alpha = 1f).compositeOver(baseBackground)
                border = Color.Transparent
            }
            "frosted" -> {
                fill = fill.copy(alpha = (fill.alpha * .55f).coerceIn(.18f, .6f))
                border = Color.White.copy(alpha = if (isDark) .22f else .55f)
            }
            else -> Unit
        }
        val effective = fill.compositeOver(baseBackground)
        val text = if (effective.luminance() > .55f) Color(0xFF17142A) else Color.White
        BubblePalette(
            fill = fill,
            fillTop = lerpColor(fill, Color.White, if (effective.luminance() > .55f) .35f else .14f),
            border = border,
            text = text,
            meta = text.copy(alpha = .66f),
            accent = accent,
            glow = accent,
            label = if (spec?.isScene == true || spec != null) lerpColor(accent, text, .15f) else scheme.primary
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha
)

fun chatBubbleShape(appearance: ChatAppearance, isSent: Boolean): Shape {
    val r = appearance.cornerRadius.dp
    val tail = if (appearance.tail) (appearance.cornerRadius * .22f).coerceAtLeast(3f).dp else r
    return if (isSent) RoundedCornerShape(r, r, tail, r) else RoundedCornerShape(r, r, r, tail)
}

/**
 * Paints a bubble: liquid (gradient + specular highlight), neon (accent rim + soft halo),
 * outline, solid or minimal (no border). The halo is drawn by hand so it looks the same on
 * every Android version and never bleeds inside translucent glass.
 */
fun Modifier.chatBubble(style: String, shape: Shape, palette: BubblePalette, appearance: ChatAppearance): Modifier {
    val radius = appearance.cornerRadius.dp
    val borderWidth = appearance.borderWidth.dp
    var m = this
    if (style == "neon" && appearance.glow) {
        m = m.drawBehind {
            val r = radius.toPx()
            for (i in 1..4) {
                val spread = i * 2.2.dp.toPx()
                drawRoundRect(
                    color = palette.glow.copy(alpha = .16f / i),
                    topLeft = Offset(-spread / 2, -spread / 2),
                    size = Size(size.width + spread, size.height + spread),
                    cornerRadius = CornerRadius(r + spread / 2),
                    style = Stroke(width = spread)
                )
            }
        }
    }
    m = m.clip(shape)
    m = when (style) {
        "liquid" -> m.background(Brush.verticalGradient(listOf(palette.fillTop, palette.fill)))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), endY = size.height * .5f),
                    topLeft = Offset(size.width * .04f, 1.5.dp.toPx()),
                    size = Size(size.width * .92f, size.height * .42f),
                    cornerRadius = CornerRadius(radius.toPx())
                )
            }
        "gradient" -> m.background(
            Brush.linearGradient(listOf(lerpColor(palette.fill, palette.accent, .15f), palette.fill, lerpColor(palette.fill, Color(0xFFFF4FA3), .35f)))
        )
        "depth" -> m.background(Brush.verticalGradient(listOf(palette.fillTop, palette.fill, lerpColor(palette.fill, Color.Black, .22f))))
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = .18f),
                    topLeft = Offset(size.width * .06f, 1.dp.toPx()),
                    size = Size(size.width * .88f, 2.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        "frosted" -> m.background(palette.fill)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.Transparent, Color.White.copy(alpha = .05f))))
        else -> m.background(palette.fill)
    }
    if (borderWidth > 0.dp && palette.border.alpha > 0f) {
        m = if (style == "liquid") {
            m.border(borderWidth, Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), palette.border, Color.White.copy(alpha = .06f))), shape)
        } else m.border(borderWidth, palette.border, shape)
    }
    return m
}

/** Full-screen chat backdrop: scene artwork, admin photo or gradient + texture, with optional dim. */
@Composable
fun BoxScope.ChatBackdrop(spec: ChatThemeSpec?, imageUrl: String?, fallback: Brush, dim: Float) {
    Box(Modifier.matchParentSize().background(spec?.brush ?: fallback))
    when {
        imageUrl != null -> {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Transparent, Color.Black.copy(alpha = .38f)))))
        }
        spec != null && spec.backgroundRes != 0 -> {
            Image(painterResource(spec.backgroundRes), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            val veil = if (spec.light) Color.White else Color.Black
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(veil.copy(alpha = .30f), Color.Transparent, veil.copy(alpha = .22f), veil.copy(alpha = .40f)))))
        }
        spec != null -> ThemeTextureLayer(spec.texture, spec.textureTint)
    }
    if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
}

/** Settings editor with a live preview. Used in Settings → Messages and in each chat. */
@Composable
fun ChatAppearanceEditor(spec: ChatThemeSpec? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appearance by ChatPreferences.appearance.collectAsState()
    fun update(block: (ChatAppearance) -> ChatAppearance) = ChatPreferences.setAppearance(context, block(appearance))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChatAppearancePreview(appearance, spec)
        Text("Bubble style", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatAppearance.STYLES.forEach { style ->
                FilterChip(
                    selected = appearance.bubbleStyle == style,
                    onClick = { update { it.copy(bubbleStyle = style) } },
                    label = { Text(ChatAppearance.label(style)) },
                    leadingIcon = if (appearance.bubbleStyle == style) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
        Text("Message animation", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatAppearance.ANIMATIONS.forEach { id ->
                FilterChip(
                    selected = appearance.messageAnimation == id,
                    onClick = { update { it.copy(messageAnimation = id) } },
                    label = { Text(ChatAppearance.animationLabel(id)) },
                    leadingIcon = if (appearance.messageAnimation == id) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
        AppearanceSlider("Side space (screen edge)", appearance.sidePadding, 0f..24f, "${appearance.sidePadding.roundToInt()} dp") { v -> update { it.copy(sidePadding = v) } }
        AppearanceSlider("Text padding inside bubble", appearance.innerPadding, 4f..16f, "${appearance.innerPadding.roundToInt()} dp") { v -> update { it.copy(innerPadding = v) } }
        AppearanceSlider("Border thickness", appearance.borderWidth, 0f..3f, if (appearance.borderWidth < .1f) "Hidden" else "%.1f dp".format(appearance.borderWidth)) { v -> update { it.copy(borderWidth = v) } }
        AppearanceSlider("Corner roundness", appearance.cornerRadius, 4f..30f, "${appearance.cornerRadius.roundToInt()} dp") { v -> update { it.copy(cornerRadius = v) } }
        AppearanceSlider("Maximum bubble width", appearance.maxWidthFraction, 0.6f..0.95f, "${(appearance.maxWidthFraction * 100).roundToInt()}%") { v -> update { it.copy(maxWidthFraction = v) } }
        AppearanceSlider("Space between messages", appearance.messageSpacing, 1f..16f, "${appearance.messageSpacing.roundToInt()} dp") { v -> update { it.copy(messageSpacing = v) } }
        AppearanceSlider("Dim background", appearance.backgroundDim, 0f..0.7f, "${(appearance.backgroundDim * 100).roundToInt()}%") { v -> update { it.copy(backgroundDim = v) } }
        AppearanceSwitch("Bubble tail", "Small pointed corner next to the sender", appearance.tail) { v -> update { it.copy(tail = v) } }
        AppearanceSwitch("Neon glow", "Soft light around neon bubbles", appearance.glow) { v -> update { it.copy(glow = v) } }
        AppearanceSwitch("24-hour time", "Show 18:30 instead of 06:30 PM", appearance.use24h) { v -> update { it.copy(use24h = v) } }
        AppearanceSwitch("Animated stickers", "Play Convo reaction sticker animations", appearance.animateReactions) { v -> update { it.copy(animateReactions = v) } }
        TextButton(onClick = { ChatPreferences.resetAppearance(context) }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Refresh, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Reset to default")
        }
    }
}

@Composable
private fun AppearanceSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun AppearanceSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

/** Mini conversation that reflects the current settings instantly. */
@Composable
fun ChatAppearancePreview(appearance: ChatAppearance, spec: ChatThemeSpec?) {
    val previewSpec = spec ?: ChatThemeCatalog.find("Neon Glass")
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), RoundedCornerShape(24.dp))
    ) {
        ChatBackdrop(previewSpec, null, Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)), appearance.backgroundDim)
        Column(
            Modifier.fillMaxSize().padding(horizontal = appearance.sidePadding.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(appearance.messageSpacing.dp, Alignment.Bottom)
        ) {
            PreviewBubble("Did you see the new theme? 😍", false, appearance, previewSpec, "Maya")
            PreviewBubble("Yes! The liquid glass looks amazing", true, appearance, previewSpec, null)
            PreviewBubble("Let's call tonight", false, appearance, previewSpec, "Maya")
        }
    }
}

@Composable
private fun ColumnScope.PreviewBubble(text: String, sent: Boolean, appearance: ChatAppearance, spec: ChatThemeSpec?, sender: String?) {
    val style = appearance.resolvedStyle(spec)
    val palette = rememberBubblePalette(sent, spec, spec?.accent, style)
    val shape = chatBubbleShape(appearance, sent)
    val width by animateFloatAsState(appearance.maxWidthFraction, label = "preview_width")
    Box(Modifier.fillMaxWidth(width).align(if (sent) Alignment.End else Alignment.Start), contentAlignment = if (sent) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.chatBubble(style, shape, palette, appearance)
                .padding(horizontal = appearance.innerPadding.dp, vertical = (appearance.innerPadding * .62f).dp)
        ) {
            if (sender != null) Text(sender, color = palette.label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text, color = palette.text, fontSize = 13.sp)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(if (appearance.use24h) "18:30" else "06:30 PM", color = palette.meta, fontSize = 9.sp)
                if (sent) { Spacer(Modifier.width(3.dp)); Icon(Icons.Default.DoneAll, null, tint = Color(0xFF55D6FF), modifier = Modifier.size(13.dp)) }
            }
        }
    }
}

/** Colors for the glass top bar and composer on scene themes. */
@Immutable
data class ChatBarColors(val container: Color, val content: Color, val border: Color)

@Composable
fun chatBarColors(spec: ChatThemeSpec?, hasImage: Boolean): ChatBarColors? {
    if (spec?.glassBars != true && !hasImage) return null
    val light = spec?.light == true
    return ChatBarColors(
        container = if (light) Color.White.copy(alpha = .52f) else Color(0xFF07070F).copy(alpha = .46f),
        content = if (light) Color(0xFF1C1530) else Color.White,
        border = (spec?.accent ?: MaterialTheme.colorScheme.primary).copy(alpha = .35f)
    )
}

/** Small round glass button used on the scene-theme composer. */
@Composable
fun GlassCircle(size: Dp, colors: ChatBarColors, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(colors.container).border(1.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}


/**
 * Entrance animation for a newly arrived/sent message bubble ([ChatAppearance.messageAnimation]).
 * [animate] should be true only once per message (new messages, not history or scroll-back).
 */
@Composable
fun Modifier.messageEntrance(style: String, animate: Boolean, fromEnd: Boolean): Modifier {
    if (!animate || style == "none") return this
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            when (style) {
                "bounce" -> androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 380f)
                "pop", "swing" -> androidx.compose.animation.core.spring(dampingRatio = .58f, stiffness = 520f)
                else -> androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            }
        )
    }
    return this.graphicsLayer {
        val p = progress.value
        val pivot = androidx.compose.ui.graphics.TransformOrigin(if (fromEnd) 1f else 0f, 1f)
        alpha = p.coerceIn(0f, 1f)
        when (style) {
            "slide" -> translationX = (1f - p) * 90f * (if (fromEnd) 1f else -1f)
            "pop" -> { val sc = .55f + .45f * p; scaleX = sc; scaleY = sc; transformOrigin = pivot }
            "bounce" -> translationY = (1f - p) * 70f
            "swing" -> { rotationZ = (1f - p) * (if (fromEnd) 9f else -9f); transformOrigin = pivot }
            else -> Unit
        }
    }
}
