package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** App-wide aurora canvas. Keeps every route visually connected to one design language. */
@Composable
fun PremiumBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
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
    Box(modifier.fillMaxSize().background(gradient), content = content)
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
