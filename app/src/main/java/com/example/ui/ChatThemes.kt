package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

enum class ThemeBrushStyle { Vertical, Linear, Radial }

@Immutable
data class ChatThemeSpec(
    val name: String,
    val colors: List<Color>,
    val style: ThemeBrushStyle = ThemeBrushStyle.Vertical,
    val texture: String = "none",
    val accent: Color,
    val light: Boolean = false,
    val premium: Boolean = false,
    val category: String = "Classic",
    /** Adaptive themes follow the current app theme instead of fixed colors. */
    val adaptive: Boolean = false,
    val textureTint: Color = if (light) Color(0xFF3B3355) else Color.White,
    /** Full-scene artwork bundled in res/drawable-nodpi (0 = gradient only). */
    @DrawableRes val backgroundRes: Int = 0,
    /** Glass fills for scene themes; null falls back to the classic accent bubbles. */
    val incomingFill: Color? = null,
    val outgoingFill: Color? = null,
    /** Bubble style used when the user leaves "Auto" selected. */
    val defaultBubbleStyle: String = "liquid",
    /** Draw the top bar and composer as frosted glass over the scene. */
    val glassBars: Boolean = false
) {
    val isScene: Boolean get() = backgroundRes != 0
    val brush: Brush
        get() = when (style) {
            ThemeBrushStyle.Vertical -> Brush.verticalGradient(colors)
            ThemeBrushStyle.Linear -> Brush.linearGradient(colors)
            ThemeBrushStyle.Radial -> Brush.radialGradient(colors)
        }
    val previewBrush: Brush get() = Brush.linearGradient(colors)
    /** Text color used on sent bubbles filled with [accent]. */
    val onAccent: Color get() = if (accent.luminance() > .55f) Color(0xFF1B1530) else Color.White
}

/** Background picked from the admin-managed gallery: stored per chat as `image:<https url>`. */
const val CHAT_IMAGE_THEME_PREFIX = "image:"

fun String.chatImageUrlOrNull(): String? =
    takeIf { it.startsWith(CHAT_IMAGE_THEME_PREFIX) }?.removePrefix(CHAT_IMAGE_THEME_PREFIX)?.takeIf { it.startsWith("https://") }

data class ChatBackgroundImage(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val premium: Boolean = false,
    val createdAt: Long = 0L
)

object ChatThemeCatalog {
    val all: List<ChatThemeSpec> = listOf(
        // Scenes: full-screen artwork with glass bubbles (reference designs)
        ChatThemeSpec(
            "Neon Glass", listOf(Color(0xFF0B0820), Color(0xFF2A1A5E), Color(0xFF5B3CC4).copy(.5f)),
            accent = Color(0xFFA66BFF), category = "Scenes", backgroundRes = R.drawable.chat_bg_neon_glass,
            incomingFill = Color(0xFF1A1640).copy(alpha = .58f), outgoingFill = Color(0xFF6A3FD9).copy(alpha = .62f),
            defaultBubbleStyle = "neon", glassBars = true
        ),
        ChatThemeSpec(
            "Matrix Code", listOf(Color.Black, Color(0xFF021A0C), Color(0xFF00C853).copy(.2f)),
            accent = Color(0xFF00FF7A), category = "Scenes", backgroundRes = R.drawable.chat_bg_matrix,
            incomingFill = Color(0xFF020B05).copy(alpha = .72f), outgoingFill = Color(0xFF003D1C).copy(alpha = .78f),
            defaultBubbleStyle = "neon", glassBars = true, textureTint = Color(0xFF69F0AE)
        ),
        ChatThemeSpec(
            "Dream Pink", listOf(Color(0xFFFFF1F7), Color(0xFFFFD3E6), Color(0xFFE9C6FF)),
            accent = Color(0xFFFF4FA3), light = true, category = "Scenes", backgroundRes = R.drawable.chat_bg_dream_pink,
            incomingFill = Color.White.copy(alpha = .70f), outgoingFill = Color(0xFFFF8CC6).copy(alpha = .78f),
            defaultBubbleStyle = "liquid", glassBars = true
        ),
        ChatThemeSpec(
            "Ocean Glass", listOf(Color(0xFF0B3A5C), Color(0xFF2F7DB5), Color(0xFFFFC27A).copy(.5f)),
            accent = Color(0xFF2E9BFF), category = "Scenes", backgroundRes = R.drawable.chat_bg_ocean_glass,
            incomingFill = Color(0xFF0B2A44).copy(alpha = .55f), outgoingFill = Color(0xFF1C7BD8).copy(alpha = .70f),
            defaultBubbleStyle = "liquid", glassBars = true
        ),
        ChatThemeSpec(
            "Milk Liquid Glass", listOf(Color(0xFFFFFDF8), Color(0xFFF2F0FF), Color(0xFFFFEFE4)),
            accent = Color(0xFF7D86FF), light = true, category = "Scenes", backgroundRes = R.drawable.chat_bg_milk_glass,
            incomingFill = Color.White.copy(alpha = .74f), outgoingFill = Color(0xFFDCDDFF).copy(alpha = .86f),
            defaultBubbleStyle = "liquid", glassBars = true
        ),
        ChatThemeSpec(
            "Anime Rooftop", listOf(Color(0xFF2B1F4F), Color(0xFF7A4A8C), Color(0xFFFFB067).copy(.5f)),
            accent = Color(0xFFFF9E57), category = "Scenes", backgroundRes = R.drawable.chat_bg_anime_rooftop,
            incomingFill = Color(0xFF1E1638).copy(alpha = .62f), outgoingFill = Color(0xFFE07A3F).copy(alpha = .74f),
            defaultBubbleStyle = "liquid", glassBars = true
        ),
        ChatThemeSpec(
            "Rainy Window", listOf(Color(0xFF071318), Color(0xFF14323A), Color(0xFFE0A458).copy(.35f)),
            accent = Color(0xFF3FC1C9), category = "Scenes", backgroundRes = R.drawable.chat_bg_rain_window,
            incomingFill = Color(0xFF0A1C22).copy(alpha = .62f), outgoingFill = Color(0xFF16808A).copy(alpha = .72f),
            defaultBubbleStyle = "frosted", glassBars = true
        ),
        ChatThemeSpec(
            "Starry Lake", listOf(Color(0xFF050A1F), Color(0xFF1B1E4B), Color(0xFF7B6CF6).copy(.4f)),
            accent = Color(0xFF8C7CFF), category = "Scenes", backgroundRes = R.drawable.chat_bg_starry_lake,
            incomingFill = Color(0xFF0C1030).copy(alpha = .60f), outgoingFill = Color(0xFF5B4BD6).copy(alpha = .70f),
            defaultBubbleStyle = "neon", glassBars = true
        ),
        ChatThemeSpec(
            "Sakura Street", listOf(Color(0xFFFFF5F8), Color(0xFFFFD9E6), Color(0xFFCFE6FF)),
            accent = Color(0xFFE85A8C), light = true, category = "Scenes", backgroundRes = R.drawable.chat_bg_sakura_street,
            incomingFill = Color.White.copy(alpha = .78f), outgoingFill = Color(0xFFFF9CC0).copy(alpha = .82f),
            defaultBubbleStyle = "liquid", glassBars = true
        ),
        // Classic
        // "Aurora" is the default and follows the app theme colors (see [ChatThemeSpec.adaptive]).
        ChatThemeSpec("Aurora", listOf(Color(0xFF0E1230), Color(0xFF3B2A7A), Color(0xFF6C63FF).copy(.45f)), accent = Color(0xFF7C6CFF), adaptive = true),
        ChatThemeSpec("Sunset", listOf(Color(0xFF2B1025), Color(0xFF7B2D45), Color(0xFFFF8A5B).copy(alpha = .55f)), accent = Color(0xFFFF7A59)),
        ChatThemeSpec("Ocean", listOf(Color(0xFF001B2E), Color(0xFF004E64), Color(0xFF00A5CF).copy(alpha = .45f)), texture = "waves", accent = Color(0xFF0FA3D1)),
        ChatThemeSpec("Forest", listOf(Color(0xFF071A12), Color(0xFF174A35), Color(0xFF5BC88A).copy(alpha = .35f)), texture = "leaves", accent = Color(0xFF34B36F)),
        ChatThemeSpec("Midnight", listOf(Color.Black, Color(0xFF171029), Color(0xFF35205E)), accent = Color(0xFF7B4DFF)),
        ChatThemeSpec("Sakura", listOf(Color(0xFF351625), Color(0xFF8E3E62), Color(0xFFFFA9C6).copy(alpha = .62f)), ThemeBrushStyle.Linear, texture = "petals", accent = Color(0xFFFF7EB0)),
        ChatThemeSpec("Neon", listOf(Color(0xFF050816), Color(0xFF172554), Color(0xFF00FFD5).copy(alpha = .38f), Color(0xFFFF3DF2).copy(alpha = .28f)), ThemeBrushStyle.Linear, accent = Color(0xFF00D9C0)),
        ChatThemeSpec("Desert", listOf(Color(0xFF2C1A12), Color(0xFF875B3A), Color(0xFFE8B26A).copy(alpha = .48f)), texture = "dots", accent = Color(0xFFD9954A)),
        ChatThemeSpec("Galaxy", listOf(Color(0xFF7D4DFF).copy(.55f), Color(0xFF14102A), Color(0xFF03040B)), ThemeBrushStyle.Radial, texture = "stars", accent = Color(0xFF8D63FF)),
        ChatThemeSpec("Pearl", listOf(Color(0xFFF7F1FF), Color(0xFFDDEEFF), Color(0xFFFCE8F3)), ThemeBrushStyle.Linear, accent = Color(0xFF6F7CFF), light = true),
        // Glass and texture
        ChatThemeSpec("Liquid Aurora", listOf(Color(0xFF080A1C), Color(0xFF32256B), Color(0xFF00D9E8).copy(.42f), Color(0xFFFF3CA6).copy(.28f)), ThemeBrushStyle.Linear, texture = "glow", accent = Color(0xFF00C2D8), category = "Glass & texture"),
        ChatThemeSpec("Crystal Ocean", listOf(Color(0xFF00141F), Color(0xFF07506A), Color(0xFF4CE6FF).copy(.35f)), texture = "bubbles", accent = Color(0xFF2BC7E6), category = "Glass & texture"),
        ChatThemeSpec("Velvet Rose", listOf(Color(0xFF170711), Color(0xFF5A1945), Color(0xFFFF72BC).copy(.46f)), ThemeBrushStyle.Linear, accent = Color(0xFFFF5FAE), category = "Glass & texture"),
        ChatThemeSpec("Obsidian", listOf(Color(0xFF302060), Color(0xFF07070D), Color.Black), ThemeBrushStyle.Radial, accent = Color(0xFF5B45C7), category = "Glass & texture"),
        ChatThemeSpec("Frost", listOf(Color(0xFFF8F6FF), Color(0xFFDDEEFF), Color(0xFFEBDFFF)), ThemeBrushStyle.Linear, texture = "sparkle", accent = Color(0xFF5E8BFF), light = true, category = "Glass & texture"),
        ChatThemeSpec("Glassmorphism", listOf(Color(0xFF101827), Color(0xFF304E68), Color(0xFF7AC7C4).copy(alpha = .40f)), ThemeBrushStyle.Linear, texture = "glow", accent = Color(0xFF4FB6B2), category = "Glass & texture"),
        ChatThemeSpec("Paper Texture", listOf(Color(0xFFFFF8E7), Color(0xFFF3D9B1), Color(0xFFD99A6C).copy(alpha = .48f)), ThemeBrushStyle.Linear, texture = "paper", accent = Color(0xFFB86B3C), light = true, category = "Glass & texture"),
        ChatThemeSpec("Aurora Mist", listOf(Color(0xFFB8FFF9).copy(.64f), Color(0xFF6C63FF).copy(.38f), Color(0xFF11142B)), ThemeBrushStyle.Radial, texture = "glow", accent = Color(0xFF6C63FF), category = "Glass & texture"),
        ChatThemeSpec("Monochrome Grid", listOf(Color(0xFF0B0D12), Color(0xFF252936), Color(0xFF6B7280).copy(.30f)), texture = "grid", accent = Color(0xFF8B93A7), category = "Glass & texture"),
        ChatThemeSpec("Cherry Blossom", listOf(Color(0xFF32162C), Color(0xFF8C4A72), Color(0xFFFFD3E1).copy(.58f)), ThemeBrushStyle.Linear, texture = "petals", accent = Color(0xFFF08BB4), category = "Glass & texture"),
        ChatThemeSpec("Monsoon", listOf(Color(0xFF071B2D), Color(0xFF0D5470), Color(0xFF58C4C9).copy(.40f)), texture = "rain", accent = Color(0xFF2FB2BD), category = "Glass & texture"),
        ChatThemeSpec("Starlight", listOf(Color(0xFFB9A8FF).copy(.50f), Color(0xFF17102E), Color(0xFF05040A)), ThemeBrushStyle.Radial, texture = "stars", accent = Color(0xFF9C88FF), category = "Glass & texture"),
        // Fresh and bright (new)
        ChatThemeSpec("Sky Blue", listOf(Color(0xFFF2F9FF), Color(0xFFD6ECFF), Color(0xFFB3D9FF)), texture = "bubbles", accent = Color(0xFF1E88E5), light = true, category = "Fresh & bright"),
        ChatThemeSpec("Lavender Dream", listOf(Color(0xFFFAF5FF), Color(0xFFEBDDFF), Color(0xFFD8C2FF)), ThemeBrushStyle.Linear, texture = "sparkle", accent = Color(0xFF8E5CF7), light = true, category = "Fresh & bright"),
        ChatThemeSpec("Mint Fresh", listOf(Color(0xFFF1FFF9), Color(0xFFD3F8E8), Color(0xFFB9F3DC)), texture = "leaves", accent = Color(0xFF12A579), light = true, category = "Fresh & bright"),
        ChatThemeSpec("Citrus", listOf(Color(0xFFFFFBEA), Color(0xFFFFE6A8), Color(0xFFFFC46B)), ThemeBrushStyle.Linear, texture = "dots", accent = Color(0xFFF08C00), light = true, category = "Fresh & bright"),
        ChatThemeSpec("Bubblegum", listOf(Color(0xFFFFF3FA), Color(0xFFFFD6EC), Color(0xFFFFB8DC)), texture = "bubbles", accent = Color(0xFFFF4FA3), light = true, category = "Fresh & bright"),
        ChatThemeSpec("Arctic", listOf(Color(0xFFF4FBFF), Color(0xFFDDF1FF), Color(0xFFC7E7FF)), texture = "rings", accent = Color(0xFF3A8DDE), light = true, category = "Fresh & bright"),
        ChatThemeSpec("Sunrise", listOf(Color(0xFFFFF1E6), Color(0xFFFFD0B5), Color(0xFFFF9A8B)), ThemeBrushStyle.Linear, texture = "rings", accent = Color(0xFFFF6A5C), light = true, category = "Fresh & bright"),
        // Mood and pattern (new)
        ChatThemeSpec("Love", listOf(Color(0xFF2A0612), Color(0xFF8E1035), Color(0xFFFF6B8B).copy(.65f)), ThemeBrushStyle.Linear, texture = "hearts", accent = Color(0xFFFF3D6E), category = "Mood & pattern"),
        ChatThemeSpec("Lo-Fi", listOf(Color(0xFF1C1033), Color(0xFF4B2A6B), Color(0xFFFFB38A).copy(.55f)), texture = "stars", accent = Color(0xFFFF9E7A), category = "Mood & pattern"),
        ChatThemeSpec("Berry Blast", listOf(Color(0xFF2A0A2E), Color(0xFF8E1E5E), Color(0xFFFF5C8A).copy(.55f)), ThemeBrushStyle.Linear, texture = "bokeh", accent = Color(0xFFE6407A), category = "Mood & pattern"),
        ChatThemeSpec("Tie-Dye", listOf(Color(0xFF6A11CB), Color(0xFFFF6FD8), Color(0xFFFFD86F)), ThemeBrushStyle.Linear, texture = "waves", accent = Color(0xFF7B2FF7), category = "Mood & pattern"),
        ChatThemeSpec("Honeycomb", listOf(Color(0xFF1E1404), Color(0xFF6B4300), Color(0xFFFFB627).copy(.55f)), texture = "hex", accent = Color(0xFFF2A516), category = "Mood & pattern"),
        ChatThemeSpec("Coffee", listOf(Color(0xFF1E130C), Color(0xFF5B3A22), Color(0xFFC89B6D).copy(.55f)), texture = "dots", accent = Color(0xFFB07A4A), category = "Mood & pattern"),
        ChatThemeSpec("Circuit", listOf(Color(0xFF020B12), Color(0xFF00313B), Color(0xFF00E5FF).copy(.22f)), texture = "circuit", accent = Color(0xFF00BCD4), textureTint = Color(0xFF7DF9FF), category = "Mood & pattern"),
        ChatThemeSpec("Matrix", listOf(Color.Black, Color(0xFF02180D), Color(0xFF00C853).copy(.25f)), texture = "rain", accent = Color(0xFF00C853), textureTint = Color(0xFF69F0AE), category = "Mood & pattern"),
        ChatThemeSpec("Party", listOf(Color(0xFF140A2E), Color(0xFF2C1460), Color(0xFF5A2BA8).copy(.6f)), ThemeBrushStyle.Radial, texture = "confetti", accent = Color(0xFFFFB703), category = "Mood & pattern"),
        ChatThemeSpec("Zigzag Retro", listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFFE94560).copy(.35f)), texture = "zigzag", accent = Color(0xFFE94560), category = "Mood & pattern"),
        // Anime & art (new)
        ChatThemeSpec("Anime Sky", listOf(Color(0xFFEAF6FF), Color(0xFFB9DDFF), Color(0xFF8EC5FC)), texture = "clouds", accent = Color(0xFF2F80ED), light = true, category = "Anime & art"),
        ChatThemeSpec("Sakura Night", listOf(Color(0xFF1B0F24), Color(0xFF4A2245), Color(0xFFFF9EC4).copy(.5f)), ThemeBrushStyle.Linear, texture = "petals", accent = Color(0xFFFF8FB8), category = "Anime & art"),
        ChatThemeSpec("Kyoto Waves", listOf(Color(0xFF0E2A47), Color(0xFF1D4E7A), Color(0xFF7FB3D5).copy(.45f)), texture = "seigaiha", accent = Color(0xFF5DADE2), textureTint = Color(0xFFE8F4FF), category = "Anime & art"),
        ChatThemeSpec("Fuji Dawn", listOf(Color(0xFFFFE3D8), Color(0xFFFFB8A8), Color(0xFF9D8CD9)), texture = "mountains", accent = Color(0xFFE3646B), light = true, category = "Anime & art"),
        ChatThemeSpec("Neo Tokyo", listOf(Color(0xFF05010F), Color(0xFF240046), Color(0xFFFF00C8).copy(.35f)), ThemeBrushStyle.Linear, texture = "skyline", accent = Color(0xFFFF2BD6), textureTint = Color(0xFF00F5FF), category = "Anime & art"),
        ChatThemeSpec("Lo-Fi Study", listOf(Color(0xFF2A1E3F), Color(0xFF5C3D6E), Color(0xFFFFB38A).copy(.5f)), texture = "music", accent = Color(0xFFFFA07A), category = "Anime & art"),
        ChatThemeSpec("Doodle Light", listOf(Color(0xFFFFFBF2), Color(0xFFF5EEDF), Color(0xFFEDE3CF)), texture = "doodle", accent = Color(0xFF00A884), light = true, category = "Anime & art"),
        ChatThemeSpec("Doodle Dark", listOf(Color(0xFF0B141A), Color(0xFF111B21), Color(0xFF1F2C34)), texture = "doodle", accent = Color(0xFF00A884), category = "Anime & art"),
        // Textures (new)
        ChatThemeSpec("Topographic", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)), texture = "topo", accent = Color(0xFF4DD0E1), category = "Textures"),
        ChatThemeSpec("Linen", listOf(Color(0xFFFAF6EF), Color(0xFFF0E6D6), Color(0xFFE4D5BC)), texture = "linen", accent = Color(0xFF9C6B3C), light = true, category = "Textures"),
        ChatThemeSpec("Film Grain", listOf(Color(0xFF1A1A1A), Color(0xFF2D2A26), Color(0xFF4A4036)), texture = "grain", accent = Color(0xFFE0B872), category = "Textures"),
        ChatThemeSpec("Marble", listOf(Color(0xFFF8F8F8), Color(0xFFECECEC), Color(0xFFDADADA)), ThemeBrushStyle.Linear, texture = "marble", accent = Color(0xFF6D6875), light = true, category = "Textures"),
        ChatThemeSpec("Black Marble", listOf(Color(0xFF0A0A0A), Color(0xFF1C1C1C), Color(0xFF2E2A24)), ThemeBrushStyle.Linear, texture = "marble", accent = Color(0xFFD4AF37), textureTint = Color(0xFFF5E6B8), category = "Textures"),
        ChatThemeSpec("Tartan", listOf(Color(0xFF3B0A0A), Color(0xFF6B1212), Color(0xFF1B4332).copy(.6f)), texture = "plaid", accent = Color(0xFFE63946), category = "Textures"),
        ChatThemeSpec("Polygon", listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF4B79A1).copy(.5f)), ThemeBrushStyle.Linear, texture = "triangles", accent = Color(0xFF64B5F6), category = "Textures"),
        ChatThemeSpec("Winter", listOf(Color(0xFF1C3B57), Color(0xFF3E6A92), Color(0xFFB3D4F0).copy(.5f)), texture = "snow", accent = Color(0xFF90CAF9), category = "Textures"),
        ChatThemeSpec("Pet Lover", listOf(Color(0xFFFFF7ED), Color(0xFFFFE8CC), Color(0xFFFFD8A8)), texture = "paws", accent = Color(0xFFE8590C), light = true, category = "Textures"),
        ChatThemeSpec("Night City", listOf(Color(0xFF020617), Color(0xFF1E1B4B), Color(0xFF6366F1).copy(.4f)), texture = "skyline", accent = Color(0xFF818CF8), textureTint = Color(0xFFFBBF24), category = "Textures"),
        ChatThemeSpec("Cloud Nine", listOf(Color(0xFFFDFBFF), Color(0xFFE9E4FF), Color(0xFFD6CCFF)), texture = "clouds", accent = Color(0xFF7C4DFF), light = true, category = "Textures"),
        ChatThemeSpec("Emerald Hills", listOf(Color(0xFF052E1C), Color(0xFF0B5D3B), Color(0xFF52B788).copy(.5f)), texture = "mountains", accent = Color(0xFF40C057), category = "Textures"),
        // Premium
        ChatThemeSpec("Royal Gold", listOf(Color(0xFF120D05), Color(0xFF5B3A08), Color(0xFFFFD166).copy(.58f)), ThemeBrushStyle.Linear, texture = "diamonds", accent = Color(0xFFFFC233), premium = true, category = "Premium"),
        ChatThemeSpec("Cyber Lime", listOf(Color.Black, Color(0xFF102A20), Color(0xFFA7FF4F).copy(.42f)), ThemeBrushStyle.Linear, texture = "grid", accent = Color(0xFF9BEF3F), premium = true, category = "Premium"),
        ChatThemeSpec("Rose Quartz", listOf(Color(0xFF2A101F), Color(0xFF7A3157), Color(0xFFFFB4D7).copy(.55f)), ThemeBrushStyle.Linear, texture = "sparkle", accent = Color(0xFFFF8CC6), premium = true, category = "Premium"),
        ChatThemeSpec("Diamond", listOf(Color(0xFF0E1230), Color(0xFF26307A), Color(0xFF9FB4FF).copy(.45f)), ThemeBrushStyle.Linear, texture = "diamonds", accent = Color(0xFF8FA6FF), premium = true, category = "Premium"),
        ChatThemeSpec("Aurora Borealis", listOf(Color(0xFF02101A), Color(0xFF0B3D3A), Color(0xFF6BFFB8).copy(.38f), Color(0xFFB57CFF).copy(.30f)), ThemeBrushStyle.Linear, texture = "aurora", accent = Color(0xFF3EE3A0), premium = true, category = "Premium"),
        // New in 4.3.5
        ChatThemeSpec("Eid Night", listOf(Color(0xFF0B1030), Color(0xFF1B2A6B), Color(0xFF3A1C71).copy(.6f)), texture = "crescent", accent = Color(0xFFFFD36E), textureTint = Color(0xFFFFF3C4), category = "New"),
        ChatThemeSpec("Constellation", listOf(Color(0xFF050816), Color(0xFF0F1B3D), Color(0xFF233A7A).copy(.6f)), texture = "constellation", accent = Color(0xFF7FB2FF), category = "New"),
        ChatThemeSpec("Lantern Festival", listOf(Color(0xFF2A0A0A), Color(0xFF6B1A12), Color(0xFFFF8F3D).copy(.4f)), texture = "lanterns", accent = Color(0xFFFF9F43), textureTint = Color(0xFFFFD36E), category = "New"),
        ChatThemeSpec("Candy Stripes", listOf(Color(0xFFFFF5FA), Color(0xFFFFE1EF), Color(0xFFE6F0FF)), ThemeBrushStyle.Linear, texture = "stripes", accent = Color(0xFFFF5FA2), light = true, category = "New"),
        ChatThemeSpec("Galaxy Swirl", listOf(Color(0xFF07021A), Color(0xFF1E0B4B), Color(0xFF0B3A5C).copy(.7f)), ThemeBrushStyle.Radial, texture = "galaxy", accent = Color(0xFFC77DFF), category = "New"),
        ChatThemeSpec("Sakura Breeze", listOf(Color(0xFFFFF8FB), Color(0xFFFFE4EF), Color(0xFFF7D6FF)), texture = "sakura", accent = Color(0xFFE64C8C), light = true, category = "New"),
        ChatThemeSpec("Retro Polka", listOf(Color(0xFFFFF4D6), Color(0xFFFFE3A3), Color(0xFFFFC46B)), texture = "polka", accent = Color(0xFFE76F51), light = true, textureTint = Color(0xFF7A3E12), category = "New"),
        ChatThemeSpec("Neon Circuit", listOf(Color(0xFF020B10), Color(0xFF042A2E), Color(0xFF00E5FF).copy(.18f)), texture = "circuitry_glow", accent = Color(0xFF00E5FF), textureTint = Color(0xFF64FFDA), category = "New"),
        ChatThemeSpec("Mint Cream", listOf(Color(0xFFF3FFF9), Color(0xFFD9FBEA), Color(0xFFC3F2DD)), texture = "dots", accent = Color(0xFF12B886), light = true, category = "New"),
        ChatThemeSpec("Sunset Boulevard", listOf(Color(0xFF2B1055), Color(0xFF7597DE).copy(.6f), Color(0xFFFF8A65).copy(.7f)), ThemeBrushStyle.Linear, texture = "skyline", accent = Color(0xFFFF8A65), textureTint = Color(0xFFFFE0B2), category = "New"),
        ChatThemeSpec("Ocean Depth", listOf(Color(0xFF001B2E), Color(0xFF00384F), Color(0xFF00708A).copy(.7f)), texture = "bubbles", accent = Color(0xFF4FC3F7), category = "New"),
        ChatThemeSpec("Lavender Fields", listOf(Color(0xFFF8F4FF), Color(0xFFE9DDFF), Color(0xFFD4C1FF)), texture = "leaves", accent = Color(0xFF8B5CF6), light = true, category = "New"),
        ChatThemeSpec("Obsidian Gold", listOf(Color(0xFF000000), Color(0xFF14110A), Color(0xFF3D2F0D).copy(.8f)), ThemeBrushStyle.Linear, texture = "constellation", accent = Color(0xFFE6B85C), textureTint = Color(0xFFFFE08A), premium = true, category = "Premium"),
        ChatThemeSpec("Holo Galaxy", listOf(Color(0xFF0A0322), Color(0xFF2D0B5A), Color(0xFFFF4FD8).copy(.35f), Color(0xFF3DD6FF).copy(.30f)), ThemeBrushStyle.Linear, texture = "galaxy", accent = Color(0xFFFF7AE0), premium = true, category = "Premium")
    )

    private val byName = all.associateBy { it.name }

    fun find(name: String): ChatThemeSpec? = byName[name]
    fun categories(): List<String> = all.map { it.category }.distinct()
}

/** Draws a texture on its own layer so it is not re-recorded when messages change. */
@Composable
fun BoxScope.ThemeTextureLayer(texture: String, tint: Color, intensity: Float = 1f) {
    if (texture == "none") return
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer()
            .drawBehind { drawThemeTexture(texture, tint, intensity) }
    )
}

@Composable
fun ChatThemePreviewTile(
    spec: ChatThemeSpec,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        val scheme = MaterialTheme.colorScheme
        val tileBrush = if (spec.adaptive) Brush.verticalGradient(listOf(scheme.background, scheme.primary.copy(alpha = .18f), scheme.surfaceVariant)) else spec.brush
        val tileAccent = if (spec.adaptive) scheme.primary else spec.accent
        val tileLight = if (spec.adaptive) scheme.background.luminance() > .5f else spec.light
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(.72f)
                .clip(RoundedCornerShape(18.dp))
                .background(tileBrush)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(.6f),
                    RoundedCornerShape(18.dp)
                )
        ) {
            if (spec.backgroundRes != 0) {
                androidx.compose.foundation.Image(
                    painter = painterResource(spec.backgroundRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            ThemeTextureLayer(spec.texture, spec.textureTint)
            // Mini conversation preview
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)
            ) {
                val incoming = spec.incomingFill ?: if (tileLight) Color.White.copy(.85f) else Color.White.copy(.18f)
                val outgoing = spec.outgoingFill ?: tileAccent
                val edge = if (spec.isScene) spec.accent.copy(alpha = .9f) else Color.Transparent
                Box(
                    Modifier.fillMaxWidth(.62f).height(14.dp).clip(RoundedCornerShape(8.dp))
                        .background(incoming).border(if (spec.isScene) 1.dp else 0.dp, edge.copy(alpha = .55f), RoundedCornerShape(8.dp))
                )
                Box(
                    Modifier.align(Alignment.End).fillMaxWidth(.7f).height(14.dp).clip(RoundedCornerShape(8.dp))
                        .background(outgoing).border(if (spec.isScene) 1.dp else 0.dp, edge, RoundedCornerShape(8.dp))
                )
                Box(
                    Modifier.fillMaxWidth(.45f).height(14.dp).clip(RoundedCornerShape(8.dp))
                        .background(incoming).border(if (spec.isScene) 1.dp else 0.dp, edge.copy(alpha = .55f), RoundedCornerShape(8.dp))
                )
            }
            if (selected || locked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (selected) Icons.Default.Check else Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(spec.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun ChatImagePreviewTile(image: ChatBackgroundImage, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(.72f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(.6f),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f))
            AsyncImage(model = image.url, contentDescription = image.name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (selected || locked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (selected) Icons.Default.Check else Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(image.name.ifBlank { "Photo" }, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/**
 * Full chat theme gallery: built-in themes grouped by mood plus admin-managed photo
 * backgrounds. Used by the chat screen picker and by Settings → Chat backgrounds.
 */
@Composable
fun ChatThemeGallery(
    selected: String,
    premiumActive: Boolean,
    images: List<ChatBackgroundImage>,
    onSelect: (String) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (images.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GallerySectionTitle("Photo backgrounds", "Full-screen images added by Convo") }
            items(images, key = { "img_" + it.id }) { image ->
                val value = CHAT_IMAGE_THEME_PREFIX + image.url
                val locked = image.premium && !premiumActive
                ChatImagePreviewTile(image, selected == value, locked) { if (locked) onLocked() else onSelect(value) }
            }
        }
        ChatThemeCatalog.categories().forEach { category ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                GallerySectionTitle(category, when (category) {
                    "Scenes" -> "Full-screen artwork with liquid glass and neon bubbles"
                    "Classic" -> "Smooth gradients"
                    "Glass & texture" -> "Soft glass effects and patterns"
                    "Fresh & bright" -> "Light and airy"
                    "Mood & pattern" -> "Bold textures with personality"
                    "New" -> "Fresh themes added in Convo 4.3.5"
                    "Textures" -> "Hand-drawn vector patterns"
                    else -> "Exclusive to Convo Premium"
                })
            }
            items(ChatThemeCatalog.all.filter { it.category == category }, key = { it.name }) { spec ->
                val locked = spec.premium && !premiumActive
                ChatThemePreviewTile(spec, selected == spec.name, locked) { if (locked) onLocked() else onSelect(spec.name) }
            }
        }
    }
}

@Composable
private fun GallerySectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
