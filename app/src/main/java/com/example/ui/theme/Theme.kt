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
    background = background, surface = surface, surfaceVariant = surface.copy(alpha = .92f),
    onPrimary = Color(0xFF111218), onBackground = Color(0xFFF6F3FA),
    onSurface = Color(0xFFF6F3FA), onSurfaceVariant = Color(0xFFD4D0DA)
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
        "rose quartz" -> themedDark(Color(0xFFFFB3CF), Color(0xFFE5A5FF), Color(0xFF140810), Color(0xFF2A1423)).copy(tertiary = Color(0xFFFFD1DC), primaryContainer = Color(0xFF69324F), onPrimaryContainer = Color.White)
        else -> if (darkTheme) DarkColors else LightColors
    }

    MaterialTheme(colorScheme = colors, typography = Typography, shapes = PremiumShapes, content = content)
}
