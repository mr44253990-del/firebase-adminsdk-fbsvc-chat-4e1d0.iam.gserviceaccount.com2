package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Legacy palette (kept so older references keep compiling) ----
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ---- Glass primitives ----
val GlassWhite = Color(0x1AFFFFFF)
val GlassWhiteStrong = Color(0x33FFFFFF)
val GlassBorder = Color(0x40FFFFFF)
val GlassBorderSoft = Color(0x22FFFFFF)
val DeepSpace = Color(0xFF070714)
val TextPrimary = Color(0xFFF5F5FF)
val TextSecondary = Color(0xB3E6E6F5)
val UnreadRed = Color(0xFFFF3B5C)
val OnlineGreen = Color(0xFF2DE38B)

/** A complete dynamic color scheme for one selectable app theme. */
data class FireChatPalette(
    val id: String,
    val displayName: String,
    val gradient: List<Color>,     // animated background gradient
    val primary: Color,            // accent
    val secondary: Color,
    val bubbleMine: List<Color>,   // my chat bubble gradient
    val bubbleTheirs: Color,       // their bubble glass tint
    val storyRing: List<Color>     // instagram style ring
)

val AuroraPalette = FireChatPalette(
    id = "aurora",
    displayName = "Aurora",
    gradient = listOf(Color(0xFF1A0533), Color(0xFF3B0F6E), Color(0xFF0D1B4C), Color(0xFF071022)),
    primary = Color(0xFFB45CFF),
    secondary = Color(0xFF4CC9F0),
    bubbleMine = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    bubbleTheirs = Color(0x26FFFFFF),
    storyRing = listOf(Color(0xFFF953C6), Color(0xFFB91D73), Color(0xFF4CC9F0))
)

val OceanPalette = FireChatPalette(
    id = "ocean",
    displayName = "Ocean",
    gradient = listOf(Color(0xFF021B3A), Color(0xFF053B6E), Color(0xFF046A8E), Color(0xFF031224)),
    primary = Color(0xFF29B6F6),
    secondary = Color(0xFF26E0C8),
    bubbleMine = listOf(Color(0xFF0575E6), Color(0xFF021B79)),
    bubbleTheirs = Color(0x26FFFFFF),
    storyRing = listOf(Color(0xFF26E0C8), Color(0xFF0575E6), Color(0xFF90F7EC)
    )
)

val SunsetPalette = FireChatPalette(
    id = "sunset",
    displayName = "Sunset",
    gradient = listOf(Color(0xFF2B0A1E), Color(0xFF5C1030), Color(0xFF8E2A1E), Color(0xFF1A0812)),
    primary = Color(0xFFFF6E7F),
    secondary = Color(0xFFFFB75E),
    bubbleMine = listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
    bubbleTheirs = Color(0x26FFFFFF),
    storyRing = listOf(Color(0xFFFFB75E), Color(0xFFED4264), Color(0xFFFFEDBC))
)

val EmeraldPalette = FireChatPalette(
    id = "emerald",
    displayName = "Emerald",
    gradient = listOf(Color(0xFF04150F), Color(0xFF0B3B2E), Color(0xFF0E5E46), Color(0xFF02100B)),
    primary = Color(0xFF2DE38B),
    secondary = Color(0xFF7BF1A8),
    bubbleMine = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    bubbleTheirs = Color(0x26FFFFFF),
    storyRing = listOf(Color(0xFF38EF7D), Color(0xFF11998E), Color(0xFFB2FEFA))
)

val RosePalette = FireChatPalette(
    id = "rose",
    displayName = "Rose",
    gradient = listOf(Color(0xFF26041C), Color(0xFF570A3B), Color(0xFF83104E), Color(0xFF160310)),
    primary = Color(0xFFFF5C8A),
    secondary = Color(0xFFFF9A8B),
    bubbleMine = listOf(Color(0xFFEC008C), Color(0xFFFC6767)),
    bubbleTheirs = Color(0x26FFFFFF),
    storyRing = listOf(Color(0xFFFF9A8B), Color(0xFFFF5C8A), Color(0xFFFFDDE1))
)

val MidnightPalette = FireChatPalette(
    id = "midnight",
    displayName = "Midnight",
    gradient = listOf(Color(0xFF0B0C10), Color(0xFF1F2833), Color(0xFF2C3E50), Color(0xFF050608)),
    primary = Color(0xFF66FCF1),
    secondary = Color(0xFF45A29E),
    bubbleMine = listOf(Color(0xFF36D1DC), Color(0xFF5B86E5)),
    bubbleTheirs = Color(0x26FFFFFF),
    storyRing = listOf(Color(0xFF66FCF1), Color(0xFF45A29E), Color(0xFFC5C6C7))
)

val AllPalettes = listOf(
    AuroraPalette, OceanPalette, SunsetPalette,
    EmeraldPalette, RosePalette, MidnightPalette
)

fun paletteById(id: String): FireChatPalette =
    AllPalettes.firstOrNull { it.id == id } ?: AuroraPalette
