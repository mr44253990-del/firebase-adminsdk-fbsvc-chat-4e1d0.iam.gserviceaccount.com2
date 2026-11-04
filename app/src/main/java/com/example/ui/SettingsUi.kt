package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Liquid-glass card: theme-tinted gradient with a bright glass rim. */
@Composable
fun Modifier.liquidCard(
    accent: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = RoundedCornerShape(26.dp)
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(
                    accent.copy(alpha = if (dark) .20f else .12f),
                    scheme.surface.copy(alpha = if (dark) .86f else .92f),
                    scheme.tertiary.copy(alpha = if (dark) .12f else .08f)
                )
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .20f else .95f), accent.copy(alpha = .35f), Color.White.copy(alpha = .05f))),
            shape
        )
}

/** Per-page colors and icon used by [SettingsPageHero]. */
private data class PageStyle(val colors: List<Color>, val icon: ImageVector)

private fun pageStyle(page: String): PageStyle = when (page) {
    "profile" -> PageStyle(listOf(Color(0xFF637BFF), Color(0xFF8B5CF6), Color(0xFFE755B6)), Icons.Outlined.Person)
    "theme" -> PageStyle(listOf(Color(0xFF765BFF), Color(0xFFB06BFF), Color(0xFFFF8CC6)), Icons.Outlined.Palette)
    "call" -> PageStyle(listOf(Color(0xFF0FB5C9), Color(0xFF2F80ED), Color(0xFF7B61FF)), Icons.Outlined.Call)
    "chat" -> PageStyle(listOf(Color(0xFF12B886), Color(0xFF0E9F75), Color(0xFF3BC9DB)), Icons.Outlined.Forum)
    "bubbles" -> PageStyle(listOf(Color(0xFFA66BFF), Color(0xFF6E8BFF), Color(0xFF00D9E8)), Icons.Outlined.ChatBubbleOutline)
    "sounds" -> PageStyle(listOf(Color(0xFFFF8A5B), Color(0xFFE5674B), Color(0xFFFFB86B)), Icons.Outlined.MusicNote)
    "backgrounds" -> PageStyle(listOf(Color(0xFF8B62D9), Color(0xFFFF5FAE), Color(0xFFFFC27A)), Icons.Outlined.Wallpaper)
    "privacy" -> PageStyle(listOf(Color(0xFF3287D5), Color(0xFF2D5BD8), Color(0xFF6C63FF)), Icons.Outlined.Security)
    "notifications" -> PageStyle(listOf(Color(0xFFFFB020), Color(0xFFE79024), Color(0xFFFF6B6B)), Icons.Outlined.NotificationsActive)
    "viewers" -> PageStyle(listOf(Color(0xFFD18B12), Color(0xFFFFC233), Color(0xFFFF8A5B)), Icons.Outlined.Visibility)
    "devices" -> PageStyle(listOf(Color(0xFF397BCC), Color(0xFF4FACFE), Color(0xFF00F2FE)), Icons.Outlined.Devices)
    "blocked" -> PageStyle(listOf(Color(0xFF718096), Color(0xFF4A5568), Color(0xFFE53E3E)), Icons.Outlined.Block)
    else -> PageStyle(listOf(Color(0xFF637BFF), Color(0xFF8B5CF6), Color(0xFFE755B6)), Icons.Outlined.Settings)
}

/** Colorful animated header shown at the top of every settings page. */
@Composable
fun SettingsPageHero(page: String, title: String, subtitle: String, onBack: () -> Unit) {
    val style = pageStyle(page)
    val transition = rememberInfiniteTransition(label = "settings_hero")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hero_drift")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(style.colors, start = Offset(0f, 0f), end = Offset(900f * (0.6f + drift * .4f), 420f)))
            .drawBehind {
                // Glass bubbles drifting inside the banner
                drawCircle(Color.White.copy(alpha = .16f), radius = size.height * .55f, center = Offset(size.width * (.86f - drift * .05f), size.height * .1f))
                drawCircle(Color.White.copy(alpha = .10f), radius = size.height * .35f, center = Offset(size.width * (.62f + drift * .06f), size.height * 1.05f))
                drawCircle(Color.White.copy(alpha = .08f), radius = size.height * .22f, center = Offset(size.width * .08f, size.height * (.2f + drift * .1f)))
            }
            .border(1.dp, Color.White.copy(alpha = .28f), RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .20f))) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings", tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = .86f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.padding(end = 6.dp).size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = .22f)).border(1.dp, Color.White.copy(alpha = .4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        }
    }
}

/** Gradient ring used around profile photos in settings. */
val ProfileRingBrush: Brush = Brush.sweepGradient(listOf(Color(0xFF637BFF), Color(0xFFE755B6), Color(0xFFFFB86B), Color(0xFF3BC9DB), Color(0xFF637BFF)))
