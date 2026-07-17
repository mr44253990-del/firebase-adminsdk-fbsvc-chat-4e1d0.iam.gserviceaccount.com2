package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ==================== Brand Colors ====================
val ElectricPurple = Color(0xFF6C63FF)
val CyanGlow = Color(0xFF00D9FF)
val PinkNeon = Color(0xFFFF6B9D)
val MintGreen = Color(0xFF00FF88)
val GoldenYellow = Color(0xFFFFD700)
val CoralRed = Color(0xFFFF6B6B)

// ==================== Light Theme Colors ====================
val Purple40 = Color(0xFF6C63FF)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val LightPrimary = Color(0xFF6C63FF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8E0FF)
val LightOnPrimaryContainer = Color(0xFF1D0F4E)

val LightSecondary = Color(0xFF00D9FF)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0F8FF)
val LightOnSecondaryContainer = Color(0xFF004D5A)

val LightTertiary = Color(0xFFFF6B9D)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFE0EB)
val LightOnTertiaryContainer = Color(0xFF5E112E)

val LightBackground = Color(0xFFF8F6FF)
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurface = Color(0xFFF8F6FF)
val LightOnSurface = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)

// ==================== Dark Theme Colors ====================
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val DarkPrimary = Color(0xFF9D8FFF)
val DarkOnPrimary = Color(0xFF2D0080)
val DarkPrimaryContainer = Color(0xFF4200A8)
val DarkOnPrimaryContainer = Color(0xFFE8E0FF)

val DarkSecondary = Color(0xFF00E5FF)
val DarkOnSecondary = Color(0xFF003640)
val DarkSecondaryContainer = Color(0xFF004F5C)
val DarkOnSecondaryContainer = Color(0xFFB8EAFF)

val DarkTertiary = Color(0xFFFF8AB4)
val DarkOnTertiary = Color(0xFF5E1133)
val DarkTertiaryContainer = Color(0xFF7B2950)
val DarkOnTertiaryContainer = Color(0xFFFFD9E5)

val DarkBackground = Color(0xFF0D0D1A)
val DarkOnBackground = Color(0xFFE6E1E5)
val DarkSurface = Color(0xFF121225)
val DarkOnSurface = Color(0xFFE6E1E5)
val DarkSurfaceVariant = Color(0xFF1E1E38)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline = Color(0xFF938F99)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)

// ==================== Glassmorphism Colors ====================
val GlassWhite = Color(0x1AFFFFFF)
val GlassDark = Color(0x1A000000)
val GlassBorderLight = Color(0x33FFFFFF)
val GlassBorderDark = Color(0x1AFFFFFF)
val GlassShadow = Color(0x40000000)

// ==================== Gradient Colors ====================
val GradientPurpleCyan = listOf(ElectricPurple, CyanGlow)
val GradientPinkPurple = listOf(PinkNeon, ElectricPurple)
val GradientCyanMint = listOf(CyanGlow, MintGreen)
val GradientSunset = listOf(PinkNeon, GoldenYellow)
val GradientOcean = listOf(CyanGlow, ElectricPurple)

// ==================== Online Status Colors ====================
val OnlineGreen = Color(0xFF00FF88)
val OfflineGray = Color(0xFF9E9E9E)
val AwayYellow = Color(0xFFFFEB3B)

// ==================== Reaction Colors ====================
val ReactionLike = Color(0xFF6C63FF)
val ReactionLove = Color(0xFFE91E63)
val ReactionHaha = Color(0xFFFFEB3B)
val ReactionWow = Color(0xFF00BCD4)
val ReactionSad = Color(0xFF9C27B0)
val ReactionAngry = Color(0xFFFF5722)

// ==================== Local Accent Color ====================
val LocalAccentColor = compositionLocalOf { ElectricPurple }

// ==================== Light Color Scheme ====================
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError
)

// ==================== Dark Color Scheme ====================
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError
)

// ==================== Theme Composable ====================
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: Color = ElectricPurple,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme.copy(primary = accentColor, secondary = accentColor)
        else -> LightColorScheme.copy(primary = accentColor, secondary = accentColor)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAccentColor provides accentColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// ==================== Additional Theme Extensions ====================
@Composable
fun getGlassBackgroundColor(): Color {
    return if (isSystemInDarkTheme()) {
        DarkSurface.copy(alpha = 0.7f)
    } else {
        LightSurface.copy(alpha = 0.85f)
    }
}

@Composable
fun getGlassBorderColor(): Color {
    return if (isSystemInDarkTheme()) {
        GlassBorderDark
    } else {
        GlassBorderLight
    }
}
