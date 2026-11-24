package com.example.ui.theme

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** App-wide aurora canvas. Keeps every route visually connected to one design language. */
@Composable
fun PremiumBackground(modifier: Modifier = Modifier, liquid: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    if (liquid) {
        // "New design": the dark liquid-glass backdrop sits behind every page.
        Box(modifier.fillMaxSize().background(com.example.ui.LiquidInk)) {
            com.example.ui.LiquidGlassBackground(beads = false)
            content()
        }
        return
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = MaterialTheme.colorScheme.background
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = if (dark) .20f else .13f),
            base,
            MaterialTheme.colorScheme.secondary.copy(alpha = if (dark) .13f else .10f),
            base,
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (dark) .10f else .07f)
        )
    )
    val orbA = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) .16f else .14f)
    val orbB = MaterialTheme.colorScheme.tertiary.copy(alpha = if (dark) .10f else .12f)
    val orbC = MaterialTheme.colorScheme.secondary.copy(alpha = if (dark) .10f else .10f)
    // v5.0: the light pools drift very slowly, so the whole app feels alive. Only the draw
    // phase re-runs (no recomposition, no layout), which keeps it cheap.
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "premium_bg").animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(16000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "premium_bg_drift"
    )
    Box(
        modifier.fillMaxSize().background(gradient).drawBehind {
            val r = size.minDimension
            val d = drift.value
            val a = Offset(size.width * (.05f + .22f * d), size.height * (.12f + .05f * d))
            val b = Offset(size.width * (1.0f - .25f * d), size.height * (.45f + .08f * d))
            val c = Offset(size.width * (.3f + .2f * d), size.height * (.95f - .08f * d))
            drawCircle(Brush.radialGradient(listOf(orbA, Color.Transparent), center = a, radius = r * .75f), radius = r * .75f, center = a)
            drawCircle(Brush.radialGradient(listOf(orbB, Color.Transparent), center = b, radius = r * .7f), radius = r * .7f, center = b)
            drawCircle(Brush.radialGradient(listOf(orbC, Color.Transparent), center = c, radius = r * .8f), radius = r * .8f, center = c)
            // v6.3 Prism: faint grid over the aurora mesh.
            val step = 54.dp.toPx()
            var gx = 0f
            while (gx <= size.width) { drawLine(Color.White.copy(alpha = .04f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
            var gy = 0f
            while (gy <= size.height) { drawLine(Color.White.copy(alpha = .04f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
        },
        content = content
    )
}

fun premiumScreenBrush(
    background: Color,
    primary: Color,
    secondary: Color,
    tertiary: Color
): Brush = Brush.linearGradient(
    listOf(
        primary.copy(alpha = .18f),
        background,
        secondary.copy(alpha = .11f),
        background,
        tertiary.copy(alpha = .08f)
    )
)
