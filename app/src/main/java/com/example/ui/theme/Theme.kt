package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** CompositionLocal exposing the currently selected dynamic palette everywhere. */
val LocalPalette = staticCompositionLocalOf { AuroraPalette }

/** CompositionLocal for switching theme at runtime (persisted by the caller). */
val LocalThemeChanger = staticCompositionLocalOf<(String) -> Unit> { {} }

private fun schemeFor(palette: FireChatPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    secondary = palette.secondary,
    onSecondary = Color.White,
    tertiary = palette.storyRing.first(),
    background = palette.gradient.first(),
    onBackground = TextPrimary,
    surface = palette.gradient.first(),
    onSurface = TextPrimary,
    surfaceVariant = GlassWhite,
    onSurfaceVariant = TextSecondary,
    error = UnreadRed,
    outline = GlassBorder
)

/**
 * App-wide theme. Wraps everything with the dynamic palette so every screen
 * recolors instantly when the user picks a different theme in Settings.
 */
@Composable
fun MyApplicationTheme(
    paletteId: String = "aurora",
    darkTheme: Boolean = true, // glass design is dark-first
    content: @Composable () -> Unit
) {
    val palette = paletteById(paletteId)
    val colorScheme = schemeFor(palette)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            try {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            } catch (_: Exception) {
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
