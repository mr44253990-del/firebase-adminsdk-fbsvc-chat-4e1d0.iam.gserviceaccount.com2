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
        // Premium
        ChatThemeSpec("Royal Gold", listOf(Color(0xFF120D05), Color(0xFF5B3A08), Color(0xFFFFD166).copy(.58f)), ThemeBrushStyle.Linear, texture = "diamonds", accent = Color(0xFFFFC233), premium = true, category = "Premium"),
        ChatThemeSpec("Cyber Lime", listOf(Color.Black, Color(0xFF102A20), Color(0xFFA7FF4F).copy(.42f)), ThemeBrushStyle.Linear, texture = "grid", accent = Color(0xFF9BEF3F), premium = true, category = "Premium"),
        ChatThemeSpec("Rose Quartz", listOf(Color(0xFF2A101F), Color(0xFF7A3157), Color(0xFFFFB4D7).copy(.55f)), ThemeBrushStyle.Linear, texture = "sparkle", accent = Color(0xFFFF8CC6), premium = true, category = "Premium"),
        ChatThemeSpec("Diamond", listOf(Color(0xFF0E1230), Color(0xFF26307A), Color(0xFF9FB4FF).copy(.45f)), ThemeBrushStyle.Linear, texture = "diamonds", accent = Color(0xFF8FA6FF), premium = true, category = "Premium"),
        ChatThemeSpec("Aurora Borealis", listOf(Color(0xFF02101A), Color(0xFF0B3D3A), Color(0xFF6BFFB8).copy(.38f), Color(0xFFB57CFF).copy(.30f)), ThemeBrushStyle.Linear, texture = "aurora", accent = Color(0xFF3EE3A0), premium = true, category = "Premium")
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
