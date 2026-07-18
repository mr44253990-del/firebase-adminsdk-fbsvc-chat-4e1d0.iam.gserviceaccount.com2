package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Glassmorphism layout helper modifier
@Composable
fun Modifier.glassmorphic(
    isDark: Boolean = true,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
): Modifier {
    val finalBg = backgroundColor ?: if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f)
    val finalBorder = borderColor ?: if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    return this
        .background(finalBg, shape)
        .border(1.dp, finalBorder, shape)
}

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = Color(0xFF000000),      // Pure AMOLED Black
        surface = Color(0xFF0C0C0C),         // Deep Slate Dark Grey
        surfaceVariant = Color(0xFF161616),  // Card surfaces
        onPrimary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFE0E0E0)
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
        background = Color(0xFFF3F4F6),      // Premium light grey
        surface = Color.White,
        surfaceVariant = Color(0xFFF9FAFB),
        onPrimary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        onSurfaceVariant = Color(0xFF374151)
    )

// Custom Themes

// 1. Chocolate Cosmic Theme
private val ChocolateDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD7CCC8),       // Cream/Warm milk
    secondary = Color(0xFF8D6E63),     // Light chocolate
    tertiary = Color(0xFFFFCC80),      // Caramel / Golden Orange
    background = Color(0xFF0F0705),    // True Chocolate AMOLED dark background
    surface = Color(0xFF1C0D0A),       // Roasted Cocoa
    surfaceVariant = Color(0xFF2C1612),  // Cocoa-grey cards
    onPrimary = Color(0xFF3E2723),     // Bitter cacao
    onBackground = Color(0xFFEFEBE9),  // Creamy white
    onSurface = Color(0xFFF5F5F5),     // Pure silk cream
    onSurfaceVariant = Color(0xFFD7CCC8)
)

// 2. Ocean Breeze Theme
private val OceanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF80DEEA),       // Soft Cyan
    secondary = Color(0xFF26A69A),     // Sea Teal
    tertiary = Color(0xFF80CBC4),      // Aquamarine
    background = Color(0xFF000E14),    // Pure Ocean AMOLED Abyss background
    surface = Color(0xFF001F2D),       // Abyss Navy Blue
    surfaceVariant = Color(0xFF002D40),  // Deep Water cards
    onPrimary = Color(0xFF004D40),     // Dark Forest Teal
    onBackground = Color(0xFFE0F7FA),  // Coral foam
    onSurface = Color(0xFFE0F2F1),     // Aqua white
    onSurfaceVariant = Color(0xFFB2EBF2)
)

// 3. Forest Emerald Theme
private val ForestDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),       // Sage green
    secondary = Color(0xFF66BB6A),     // Leaf green
    tertiary = Color(0xFFC8E6C9),      // Mint sprig
    background = Color(0xFF050C07),    // Pure Forest AMOLED green-black
    surface = Color(0xFF0C1D13),       // Primeval forest deep green
    surfaceVariant = Color(0xFF152E1E),  // Moss stone cards
    onPrimary = Color(0xFF1B5E20),     // Dark pine
    onBackground = Color(0xFFE8F5E9),  // Light jade
    onSurface = Color(0xFFE8F5E9),     // Soft sage white
    onSurfaceVariant = Color(0xFFC8E6C9)
)

// 4. Midnight Violet Theme
private val VioletDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE1BEE7),       // Soft Lilac
    secondary = Color(0xFFAB47BC),     // Neon amethyst
    tertiary = Color(0xFFEA80FC),      // Brilliant violet magenta
    background = Color(0xFF040108),    // Pure Velvet AMOLED black-purple
    surface = Color(0xFF0D0314),       // Velvet black-purple
    surfaceVariant = Color(0xFF1B0A26),  // Deep nebula orchid cards
    onPrimary = Color(0xFF4A148C),     // Pure royal purple
    onBackground = Color(0xFFF3E5F5),  // Soft Lavender dust
    onSurface = Color(0xFFF5F5F5),     // Celestial violet
    onSurfaceVariant = Color(0xFFE1BEE7)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeType: String = "default", // default, chocolate, ocean, forest, midnight
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeType.lowercase()) {
        "chocolate" -> ChocolateDarkColorScheme
        "ocean" -> OceanDarkColorScheme
        "forest" -> ForestDarkColorScheme
        "midnight" -> VioletDarkColorScheme
        else -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
