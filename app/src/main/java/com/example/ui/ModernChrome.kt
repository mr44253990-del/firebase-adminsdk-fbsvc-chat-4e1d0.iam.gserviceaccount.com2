package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * Shared "modern" page chrome so older screens match the current app theme: a soft, theme-tinted
 * gradient backdrop with two glow blobs (derived from the active colour scheme, so it follows
 * every app theme and dark mode) and a transparent top bar that blends into it.
 */
@Composable
fun Modifier.modernBackdrop(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val tertiary = scheme.tertiary
    val background = scheme.background
    return this.drawBehind {
        drawRect(Brush.verticalGradient(listOf(primary.copy(alpha = .13f), background, background, tertiary.copy(alpha = .07f))))
        drawCircle(
            Brush.radialGradient(listOf(primary.copy(alpha = .20f), Color.Transparent), center = Offset(size.width * .9f, size.height * .06f), radius = size.width * .7f),
            radius = size.width * .7f, center = Offset(size.width * .9f, size.height * .06f)
        )
        drawCircle(
            Brush.radialGradient(listOf(tertiary.copy(alpha = .14f), Color.Transparent), center = Offset(size.width * .05f, size.height * .55f), radius = size.width * .6f),
            radius = size.width * .6f, center = Offset(size.width * .05f, size.height * .55f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun modernTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onBackground
)

@Composable
fun ModernTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
