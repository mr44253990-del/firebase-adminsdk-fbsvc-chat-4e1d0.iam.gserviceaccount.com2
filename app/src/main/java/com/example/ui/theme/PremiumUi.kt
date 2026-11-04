package com.example.ui.theme

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

/** App-wide aurora canvas. Keeps every route visually connected to one design language. */
@Composable
fun PremiumBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
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
    Box(
        modifier.fillMaxSize().background(gradient).drawBehind {
            // Soft liquid-glass light pools; static so they cost nothing per frame.
            val r = size.minDimension
            drawCircle(Brush.radialGradient(listOf(orbA, Color.Transparent), center = Offset(size.width * .05f, size.height * .12f), radius = r * .75f), radius = r * .75f, center = Offset(size.width * .05f, size.height * .12f))
            drawCircle(Brush.radialGradient(listOf(orbB, Color.Transparent), center = Offset(size.width * 1.0f, size.height * .45f), radius = r * .7f), radius = r * .7f, center = Offset(size.width * 1.0f, size.height * .45f))
            drawCircle(Brush.radialGradient(listOf(orbC, Color.Transparent), center = Offset(size.width * .3f, size.height * .95f), radius = r * .8f), radius = r * .8f, center = Offset(size.width * .3f, size.height * .95f))
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
