package com.ebchat.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora glass theme — the same visual language as the Android "Aurora" skin:
 * deep gradient canvas, translucent glass surfaces, pill-shaped controls,
 * and Light / Dark / AMOLED appearance modes.
 */
object AuroraPalette {
    val AuroraTop = Color(0xFF1B1F3B)
    val AuroraMid = Color(0xFF2A1E5C)
    val AuroraDeep = Color(0xFF10233F)
    val AmoledBlack = Color(0xFF000000)
    val Accent = Color(0xFF7C5CFF)
    val AccentAlt = Color(0xFF3FD0C8)
    val Glass = Color(0x14FFFFFF)
    val GlassEdge = Color(0x33FFFFFF)
}

enum class AppearanceMode(val label: String) { LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED") }

@Composable
fun ConvoDesktopTheme(mode: AppearanceMode, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        AppearanceMode.LIGHT -> lightColorScheme(
            primary = AuroraPalette.Accent,
            secondary = AuroraPalette.AccentAlt,
            surface = Color(0xFFF6F6FB),
            background = Color(0xFFEEEFF7)
        )
        AppearanceMode.DARK -> darkColorScheme(
            primary = AuroraPalette.Accent,
            secondary = AuroraPalette.AccentAlt,
            surface = Color(0xFF151827),
            background = Color(0xFF0E1018)
        )
        AppearanceMode.AMOLED -> darkColorScheme(
            primary = AuroraPalette.Accent,
            secondary = AuroraPalette.AccentAlt,
            surface = AuroraPalette.AmoledBlack,
            background = AuroraPalette.AmoledBlack
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        when (mode) {
                            AppearanceMode.LIGHT -> listOf(Color(0xFFDCE3FF), Color(0xFFEFDDFF), Color(0xFFD9F2F0))
                            AppearanceMode.DARK -> listOf(AuroraPalette.AuroraDeep, AuroraPalette.AuroraMid, AuroraPalette.AuroraTop)
                            AppearanceMode.AMOLED -> listOf(AuroraPalette.AmoledBlack, Color(0xFF0A0A12), AuroraPalette.AmoledBlack)
                        }
                    )
                )
        ) { content() }
    }
}
