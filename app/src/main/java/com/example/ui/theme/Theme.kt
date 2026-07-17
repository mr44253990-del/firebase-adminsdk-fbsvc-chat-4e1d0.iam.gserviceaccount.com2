package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onPrimary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
        background = Color(0xFFF9F9FA),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black
    )

// Custom Themes

// 1. Chocolate Cosmic Theme
private val ChocolateDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD7CCC8),       // Cream/Warm milk
    secondary = Color(0xFF8D6E63),     // Light chocolate
    tertiary = Color(0xFFFFCC80),      // Caramel / Golden Orange
    background = Color(0xFF1C0D0A),    // Rich Dark Chocolate
    surface = Color(0xFF2C1612),       // Roasted Cocoa
    onPrimary = Color(0xFF3E2723),     // Bitter cacao
    onBackground = Color(0xFFEFEBE9),  // Creamy white
    onSurface = Color(0xFFF5F5F5)      // Pure silk cream
)

// 2. Ocean Breeze Theme
private val OceanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF80DEEA),       // Soft Cyan
    secondary = Color(0xFF26A69A),     // Sea Teal
    tertiary = Color(0xFF80CBC4),      // Aquamarine
    background = Color(0xFF001F2D),    // Abyss Navy Blue
    surface = Color(0xFF002D40),       // Deep Water
    onPrimary = Color(0xFF004D40),     // Dark Forest Teal
    onBackground = Color(0xFFE0F7FA),  // Coral foam
    onSurface = Color(0xFFE0F2F1)      // Aqua white
)

// 3. Forest Emerald Theme
private val ForestDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),       // Sage green
    secondary = Color(0xFF66BB6A),     // Leaf green
    tertiary = Color(0xFFC8E6C9),      // Mint sprig
    background = Color(0xFF0C1D13),    // Primeval forest deep green
    surface = Color(0xFF152E1E),       // Moss stone
    onPrimary = Color(0xFF1B5E20),     // Dark pine
    onBackground = Color(0xFFE8F5E9),  // Light jade
    onSurface = Color(0xFFE8F5E9)      // Soft sage white
)

// 4. Midnight Violet Theme
private val VioletDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE1BEE7),       // Soft Lilac
    secondary = Color(0xFFAB47BC),     // Neon amethyst
    tertiary = Color(0xFFEA80FC),      // Brilliant violet magenta
    background = Color(0xFF0D0314),    // Velvet black-purple
    surface = Color(0xFF1B0A26),       // Deep nebula orchid
    onPrimary = Color(0xFF4A148C),     // Pure royal purple
    onBackground = Color(0xFFF3E5F5),  // Soft Lavender dust
    onSurface = Color(0xFFF5F5F5)      // Celestial violet
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
