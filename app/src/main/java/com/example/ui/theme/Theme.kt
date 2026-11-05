package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Translucent surface used throughout the app. Real device content remains visible below it. */
@Composable
fun Modifier.glassmorphic(
    isDark: Boolean? = null,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(28.dp)
): Modifier {
    val dark = isDark ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val background = backgroundColor ?: if (dark) {
        Color(0xFF151824).copy(alpha = .78f)
    } else {
        Color.White.copy(alpha = .72f)
    }
    val border = borderColor ?: if (dark) Color.White.copy(alpha = .13f) else Color.White.copy(alpha = .88f)
    return shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .10f))
        .background(background, shape)
        .border(1.dp, border, shape)
}

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val LightColors = lightColorScheme(
    primary = AuroraViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF24134F),
    secondary = AuroraBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEAFF),
    onSecondaryContainer = Color(0xFF092A58),
    tertiary = AuroraPink,
    background = Mist50,
    onBackground = Color(0xFF171823),
    surface = Color.White,
    onSurface = Color(0xFF191A23),
    surfaceVariant = Mist100,
    onSurfaceVariant = Mist700,
    outline = Color(0xFFBFC3D2),
    outlineVariant = Color(0xFFDDE0EA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEADFF),
    onPrimary = Color(0xFF281060),
    primaryContainer = Color(0xFF38246E),
    onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFF9FC7FF),
    onSecondary = Color(0xFF00315F),
    secondaryContainer = Color(0xFF163C68),
    onSecondaryContainer = Color(0xFFD7E7FF),
    tertiary = Color(0xFFFFAFCE),
    background = Ink900,
    onBackground = Color(0xFFF2F1FA),
    surface = Color(0xFF141720),
    onSurface = Color(0xFFF2F1FA),
    surfaceVariant = Ink800,
    onSurfaceVariant = Color(0xFFC8C9D5),
    outline = Color(0xFF8D8E9C),
    outlineVariant = Color(0xFF343744)
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF090A0F),
    surfaceVariant = Color(0xFF11131B)
)

private fun themedDark(primary: Color, secondary: Color, background: Color, surface: Color) = darkColorScheme(
    primary = primary, secondary = secondary, tertiary = AuroraPink,
    background = background, surface = surface, surfaceVariant = androidx.compose.ui.graphics.lerp(surface, Color.White, .07f),
    onPrimary = Color(0xFF111218), onBackground = Color(0xFFF6F3FA),
    onSurface = Color(0xFFF6F3FA), onSurfaceVariant = Color(0xFFD9D5E0),
    onSecondary = Color(0xFF111218), onTertiary = Color(0xFF111218),
    secondaryContainer = androidx.compose.ui.graphics.lerp(surface, secondary, .28f), onSecondaryContainer = Color.White,
    tertiaryContainer = androidx.compose.ui.graphics.lerp(surface, AuroraPink, .26f), onTertiaryContainer = Color.White,
    primaryContainer = androidx.compose.ui.graphics.lerp(surface, primary, .3f), onPrimaryContainer = Color.White,
    // Material3 cards, sheets, menus and dialogs read the surfaceContainer roles; derive them from
    // the theme surface so they never fall back to the baseline purple-grey with dark text.
    surfaceContainerLowest = background,
    surfaceContainerLow = androidx.compose.ui.graphics.lerp(surface, background, .4f),
    surfaceContainer = surface,
    surfaceContainerHigh = androidx.compose.ui.graphics.lerp(surface, Color.White, .05f),
    surfaceContainerHighest = androidx.compose.ui.graphics.lerp(surface, Color.White, .09f),
    surfaceBright = androidx.compose.ui.graphics.lerp(surface, Color.White, .12f),
    surfaceDim = background,
    inverseSurface = Color(0xFFF1EEF6), inverseOnSurface = Color(0xFF1B1A22),
    outline = Color(0xFF9A96A6), outlineVariant = androidx.compose.ui.graphics.lerp(surface, Color.White, .16f)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeType: String = "default",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val mode = themeType.lowercase()
    val colors = when (mode) {
        "light" -> LightColors
        "aurora light" -> LightColors.copy(primary = Color(0xFF765BFF), secondary = Color(0xFF009EC4), tertiary = Color(0xFFE64BAA), background = Color(0xFFF7F5FF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDE9FF))
        "dark" -> DarkColors
        "amoled" -> AmoledColors
        // Smart follows the device's light/dark schedule and Android wallpaper palette when
        // available; older Android versions fall back to accessible app light/dark palettes.
        "smart" -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) DarkColors else LightColors
        "dynamic" -> if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) DarkColors else LightColors
        "chocolate" -> themedDark(Color(0xFFE8C7B5), Color(0xFFC89578), Color(0xFF100805), Color(0xFF21120D))
        "ocean" -> themedDark(Color(0xFF83E6F2), Color(0xFF68D6C9), Color(0xFF001017), Color(0xFF062532))
        "ocean calm" -> themedDark(Color(0xFF8CEBFF), Color(0xFF70D8C5), Color(0xFF00151E), Color(0xFF07313D)).copy(tertiary = Color(0xFFFFD28A), primaryContainer = Color(0xFF0B5262), onPrimaryContainer = Color.White)
        "forest" -> themedDark(Color(0xFFA7E3AE), Color(0xFF75D59A), Color(0xFF050D08), Color(0xFF102319))
        "midnight" -> themedDark(Color(0xFFE3B9FF), Color(0xFFB996FF), Color(0xFF07030D), Color(0xFF190D28))
        "liquid aurora" -> themedDark(Color(0xFFC5B4FF), Color(0xFF66E6FF), Color(0xFF070817), Color(0xFF17152E)).copy(tertiary = Color(0xFFFF79C9), primaryContainer = Color(0xFF352B68), onPrimaryContainer = Color.White)
        "glass ocean" -> themedDark(Color(0xFF79ECFF), Color(0xFF6B9CFF), Color(0xFF001018), Color(0xFF062A38)).copy(tertiary = Color(0xFF4FFFC1), primaryContainer = Color(0xFF074B60), onPrimaryContainer = Color.White)
        "glass rose" -> themedDark(Color(0xFFFFA9D6), Color(0xFFD9A5FF), Color(0xFF160711), Color(0xFF301327)).copy(tertiary = Color(0xFFFFD17A), primaryContainer = Color(0xFF672B55), onPrimaryContainer = Color.White)
        "obsidian neon" -> themedDark(Color(0xFFA88BFF), Color(0xFF00F0D0), Color.Black, Color(0xFF0A0B12)).copy(tertiary = Color(0xFFFF3DAA), primaryContainer = Color(0xFF281D55), onPrimaryContainer = Color.White)
        "neon pulse" -> themedDark(Color(0xFFFF8A65), Color(0xFF00E5FF), Color(0xFF0B0612), Color(0xFF1A0C27)).copy(tertiary = Color(0xFFFF4DA6), primaryContainer = Color(0xFF51205D), onPrimaryContainer = Color.White)
        "frosted light" -> LightColors.copy(primary = Color(0xFF6554D9), secondary = Color(0xFF007F9B), background = Color(0xFFF5F5FF), surface = Color(0xFFFCFAFF), surfaceVariant = Color(0xFFE9E8F4), onSurface = Color(0xFF171522), onBackground = Color(0xFF171522))
        "royal gold" -> themedDark(Color(0xFFFFD166), Color(0xFFFFA62B), Color(0xFF120C02), Color(0xFF241806)).copy(tertiary = Color(0xFFFFE8A3), primaryContainer = Color(0xFF5A4108), onPrimaryContainer = Color.White)
        "cyber lime" -> themedDark(Color(0xFFC8FF4D), Color(0xFF55E6A5), Color(0xFF071006), Color(0xFF102014)).copy(tertiary = Color(0xFFB2F7FF), primaryContainer = Color(0xFF315C12), onPrimaryContainer = Color.White)
        // Liquid glass family
        "milk liquid glass" -> LightColors.copy(primary = Color(0xFF6F78F0), onPrimary = Color.White, secondary = Color(0xFF9C8CF5), tertiary = Color(0xFFFFA97A), background = Color(0xFFFBF8F3), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF1EEF7), onBackground = Color(0xFF1D1B26), onSurface = Color(0xFF1D1B26), onSurfaceVariant = Color(0xFF5E5B6E), primaryContainer = Color(0xFFE6E7FF), onPrimaryContainer = Color(0xFF1E2370), outlineVariant = Color(0xFFE6E1EC))
        "neon glass" -> themedDark(Color(0xFFB794FF), Color(0xFF6E8BFF), Color(0xFF0A0718), Color(0xFF181332)).copy(tertiary = Color(0xFFFF6FD8), primaryContainer = Color(0xFF3A2580), onPrimaryContainer = Color.White)
        "matrix" -> themedDark(Color(0xFF39FF88), Color(0xFF00D26A), Color(0xFF000502), Color(0xFF07140C)).copy(tertiary = Color(0xFFB4FFCE), primaryContainer = Color(0xFF0B4A26), onPrimaryContainer = Color(0xFFD9FFE7), onPrimary = Color(0xFF00200E))
        "dream pink" -> LightColors.copy(primary = Color(0xFFE94B9A), onPrimary = Color.White, secondary = Color(0xFFB57BFF), tertiary = Color(0xFFFF9AC2), background = Color(0xFFFFF4F9), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFFCE4F0), primaryContainer = Color(0xFFFFD6EA), onPrimaryContainer = Color(0xFF4F0B2D), onSurfaceVariant = Color(0xFF6E4D60))
        "ocean glass" -> themedDark(Color(0xFF66C2FF), Color(0xFFFFC27A), Color(0xFF031523), Color(0xFF0A2840)).copy(tertiary = Color(0xFF7CF5E1), primaryContainer = Color(0xFF0E4C7A), onPrimaryContainer = Color.White)
        // New light themes
        "sky light" -> LightColors.copy(primary = Color(0xFF1E88E5), secondary = Color(0xFF00ACC1), tertiary = Color(0xFF7C4DFF), background = Color(0xFFF3F9FF), surface = Color.White, surfaceVariant = Color(0xFFE3F0FD), primaryContainer = Color(0xFFD3E8FF), onPrimaryContainer = Color(0xFF00305C))
        "mint light" -> LightColors.copy(primary = Color(0xFF0E9F75), secondary = Color(0xFF2F7D6B), tertiary = Color(0xFFF2994A), background = Color(0xFFF2FCF8), surface = Color.White, surfaceVariant = Color(0xFFDDF4EB), primaryContainer = Color(0xFFC7F2E2), onPrimaryContainer = Color(0xFF00382A))
        "peach light" -> LightColors.copy(primary = Color(0xFFE5674B), secondary = Color(0xFFC2185B), tertiary = Color(0xFFFFA000), background = Color(0xFFFFF6F2), surface = Color.White, surfaceVariant = Color(0xFFFBE6DE), primaryContainer = Color(0xFFFFDBD0), onPrimaryContainer = Color(0xFF4A1206))
        "lavender light" -> LightColors.copy(primary = Color(0xFF8E5CF7), secondary = Color(0xFFD25BB6), tertiary = Color(0xFF4F8DF7), background = Color(0xFFFAF6FF), surface = Color.White, surfaceVariant = Color(0xFFEEE6FB), primaryContainer = Color(0xFFE6DAFF), onPrimaryContainer = Color(0xFF2B0E66))
        "sand light" -> LightColors.copy(primary = Color(0xFFA0672C), secondary = Color(0xFF6F7D3C), tertiary = Color(0xFFC0503A), background = Color(0xFFFBF7F0), surface = Color(0xFFFFFDF9), surfaceVariant = Color(0xFFF1E7D8), primaryContainer = Color(0xFFF6DEC0), onPrimaryContainer = Color(0xFF3A2104))
        // New dark themes
        "berry night" -> themedDark(Color(0xFFFF7AA8), Color(0xFFC792FF), Color(0xFF14060E), Color(0xFF2A0F20)).copy(tertiary = Color(0xFFFFC46B), primaryContainer = Color(0xFF6A1E43), onPrimaryContainer = Color.White)
        "coffee" -> themedDark(Color(0xFFE2B98E), Color(0xFFB98A64), Color(0xFF110B07), Color(0xFF23170F)).copy(tertiary = Color(0xFFF2D4A4), primaryContainer = Color(0xFF55391F), onPrimaryContainer = Color.White)
        "lo-fi" -> themedDark(Color(0xFFFFB38A), Color(0xFFB79CFF), Color(0xFF110A1C), Color(0xFF221634)).copy(tertiary = Color(0xFF8FE3FF), primaryContainer = Color(0xFF4A2E63), onPrimaryContainer = Color.White)
        "slate" -> themedDark(Color(0xFF9EC1FF), Color(0xFF93A4C3), Color(0xFF0D1117), Color(0xFF161B22)).copy(tertiary = Color(0xFF7EE787), primaryContainer = Color(0xFF1F3A66), onPrimaryContainer = Color.White)
        "crimson" -> themedDark(Color(0xFFFF6B6B), Color(0xFFFFA07A), Color(0xFF120506), Color(0xFF250C0E)).copy(tertiary = Color(0xFFFFD166), primaryContainer = Color(0xFF6B1A1D), onPrimaryContainer = Color.White)
        "emerald" -> themedDark(Color(0xFF4EE3A2), Color(0xFF7FD1FF), Color(0xFF02100A), Color(0xFF0A2218)).copy(tertiary = Color(0xFFFFE08A), primaryContainer = Color(0xFF0E5238), onPrimaryContainer = Color.White)
        "sapphire" -> themedDark(Color(0xFF7FA8FF), Color(0xFF8CE0FF), Color(0xFF040A1C), Color(0xFF0D1836)).copy(tertiary = Color(0xFFFFB4E1), primaryContainer = Color(0xFF1C3A8A), onPrimaryContainer = Color.White)
        "galaxy" -> themedDark(Color(0xFFB69CFF), Color(0xFFFF8BD3), Color(0xFF05030F), Color(0xFF140C2A)).copy(tertiary = Color(0xFF7DF9FF), primaryContainer = Color(0xFF3B2580), onPrimaryContainer = Color.White)
        "diamond" -> themedDark(Color(0xFFA9B8FF), Color(0xFFE0E7FF), Color(0xFF070A1C), Color(0xFF131A3A)).copy(tertiary = Color(0xFF9FFFE0), primaryContainer = Color(0xFF2B3A85), onPrimaryContainer = Color.White)
        "rose quartz" -> themedDark(Color(0xFFFFB3CF), Color(0xFFE5A5FF), Color(0xFF140810), Color(0xFF2A1423)).copy(tertiary = Color(0xFFFFD1DC), primaryContainer = Color(0xFF69324F), onPrimaryContainer = Color.White)
        // Anime & vivid (new)
        "sakura night" -> themedDark(Color(0xFFFF9EC4), Color(0xFFC3A6FF), Color(0xFF120A16), Color(0xFF261729)).copy(tertiary = Color(0xFFFFE3A3), primaryContainer = Color(0xFF5E2A4A), onPrimaryContainer = Color.White)
        "neo tokyo" -> themedDark(Color(0xFFFF4FD8), Color(0xFF00F5FF), Color(0xFF05010F), Color(0xFF160B2C)).copy(tertiary = Color(0xFFFFE066), primaryContainer = Color(0xFF4E1A5E), onPrimaryContainer = Color.White)
        "sunset rooftop" -> themedDark(Color(0xFFFFB067), Color(0xFFC59BFF), Color(0xFF130C1F), Color(0xFF261A36)).copy(tertiary = Color(0xFFFF7AA8), primaryContainer = Color(0xFF5C3A2A), onPrimaryContainer = Color.White)
        "nordic" -> themedDark(Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFF242933), Color(0xFF2E3440)).copy(tertiary = Color(0xFFA3BE8C), primaryContainer = Color(0xFF3B4A5C), onPrimaryContainer = Color.White)
        "terminal" -> themedDark(Color(0xFF7CFC00), Color(0xFF00E5A0), Color(0xFF020402), Color(0xFF0C120C)).copy(tertiary = Color(0xFFFFD54F), primaryContainer = Color(0xFF1E3D10), onPrimaryContainer = Color.White, onPrimary = Color(0xFF0A1A00))
        "ruby" -> themedDark(Color(0xFFFF5C7A), Color(0xFFFF9F80), Color(0xFF110307), Color(0xFF240A12)).copy(tertiary = Color(0xFFFFD1DC), primaryContainer = Color(0xFF6A1428), onPrimaryContainer = Color.White)
        "anime sky" -> LightColors.copy(primary = Color(0xFF2F80ED), secondary = Color(0xFFFF8A65), tertiary = Color(0xFF7C4DFF), background = Color(0xFFF1F8FF), surface = Color.White, surfaceVariant = Color(0xFFE0EEFC), primaryContainer = Color(0xFFD6E8FF), onPrimaryContainer = Color(0xFF002B5C))
        "rose light" -> LightColors.copy(primary = Color(0xFFD6336C), secondary = Color(0xFF9C36B5), tertiary = Color(0xFFF08C00), background = Color(0xFFFFF5F8), surface = Color.White, surfaceVariant = Color(0xFFFBE4EC), primaryContainer = Color(0xFFFFD8E4), onPrimaryContainer = Color(0xFF4A0820))
        "teal light" -> LightColors.copy(primary = Color(0xFF00897B), secondary = Color(0xFF3949AB), tertiary = Color(0xFFFF7043), background = Color(0xFFF1FBFA), surface = Color.White, surfaceVariant = Color(0xFFDDF1EE), primaryContainer = Color(0xFFC4EDE7), onPrimaryContainer = Color(0xFF00332D))
        "mono light" -> LightColors.copy(primary = Color(0xFF212529), secondary = Color(0xFF495057), tertiary = Color(0xFF868E96), background = Color(0xFFF8F9FA), surface = Color.White, surfaceVariant = Color(0xFFECEEF0), primaryContainer = Color(0xFFDEE2E6), onPrimaryContainer = Color(0xFF111315))
        // v4.3.5 themes
        "aurora borealis" -> themedDark(Color(0xFF5CFFC8), Color(0xFF7AA7FF), Color(0xFF02101A), Color(0xFF0A2230)).copy(tertiary = Color(0xFFC39BFF), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF0A2230), Color(0xFF5CFFC8), .38f), onPrimaryContainer = Color.White)
        "midnight ocean" -> themedDark(Color(0xFF4FC3F7), Color(0xFF26E0C8), Color(0xFF020814), Color(0xFF0A1730)).copy(tertiary = Color(0xFFFFD180), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF0A1730), Color(0xFF4FC3F7), .38f), onPrimaryContainer = Color.White)
        "volcano" -> themedDark(Color(0xFFFF7043), Color(0xFFFFB74D), Color(0xFF120402), Color(0xFF2A0D06)).copy(tertiary = Color(0xFFFFE082), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF2A0D06), Color(0xFFFF7043), .38f), onPrimaryContainer = Color.White)
        "lavender dusk" -> themedDark(Color(0xFFC6A8FF), Color(0xFFFFA8D9), Color(0xFF110B1E), Color(0xFF221839)).copy(tertiary = Color(0xFF9EE7FF), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF221839), Color(0xFFC6A8FF), .38f), onPrimaryContainer = Color.White)
        "cyberpunk" -> themedDark(Color(0xFFFFE600), Color(0xFFFF2E88), Color(0xFF07010D), Color(0xFF1A0626)).copy(tertiary = Color(0xFF00F0FF), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF1A0626), Color(0xFFFFE600), .38f), onPrimaryContainer = Color.White)
        "deep forest" -> themedDark(Color(0xFF8BD17C), Color(0xFFC5E1A5), Color(0xFF030B06), Color(0xFF0D1F14)).copy(tertiary = Color(0xFFFFCC80), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF0D1F14), Color(0xFF8BD17C), .38f), onPrimaryContainer = Color.White)
        "graphite" -> themedDark(Color(0xFFE0E3E8), Color(0xFF9AA4B2), Color(0xFF0E0F11), Color(0xFF1B1D21)).copy(tertiary = Color(0xFF64B5F6), primaryContainer = androidx.compose.ui.graphics.lerp(Color(0xFF1B1D21), Color(0xFFE0E3E8), .38f), onPrimaryContainer = Color.White)
        "arctic light" -> LightColors.copy(primary = Color(0xFF0277BD), onPrimary = Color.White, secondary = Color(0xFF00897B), tertiary = Color(0xFF5E35B1), background = Color(0xFFF2F8FC), surface = Color(0xFFFFFFFF), surfaceVariant = androidx.compose.ui.graphics.lerp(Color(0xFFF2F8FC), Color(0xFF0277BD), .08f), primaryContainer = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF0277BD), .2f), onPrimaryContainer = androidx.compose.ui.graphics.lerp(Color.Black, Color(0xFF0277BD), .35f))
        "lemonade light" -> LightColors.copy(primary = Color(0xFFC28A00), onPrimary = Color.White, secondary = Color(0xFF43A047), tertiary = Color(0xFFE65100), background = Color(0xFFFFFCEE), surface = Color(0xFFFFFFFF), surfaceVariant = androidx.compose.ui.graphics.lerp(Color(0xFFFFFCEE), Color(0xFFC28A00), .08f), primaryContainer = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFFC28A00), .2f), onPrimaryContainer = androidx.compose.ui.graphics.lerp(Color.Black, Color(0xFFC28A00), .35f))
        "paper light" -> LightColors.copy(primary = Color(0xFF3E4A59), onPrimary = Color.White, secondary = Color(0xFF8D6E63), tertiary = Color(0xFF00796B), background = Color(0xFFFAF8F3), surface = Color(0xFFFFFFFF), surfaceVariant = androidx.compose.ui.graphics.lerp(Color(0xFFFAF8F3), Color(0xFF3E4A59), .08f), primaryContainer = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF3E4A59), .2f), onPrimaryContainer = androidx.compose.ui.graphics.lerp(Color.Black, Color(0xFF3E4A59), .35f))
        else -> if (darkTheme) DarkColors else LightColors
    }

    MaterialTheme(colorScheme = colors, typography = Typography, shapes = PremiumShapes) {
        // Text drawn outside a Surface would otherwise default to black, which made many labels
        // unreadable on dark themes. Provide the theme's on-background colour app-wide.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colors.onBackground,
            content = content
        )
    }
}
