package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.User
import kotlin.math.cos
import kotlin.math.sin

private val AdminGold = Color(0xFFFFD36E)
private val AdminViolet = Color(0xFF8E6BFF)
private val AdminCyan = Color(0xFF4FE0FF)

/** True for the Convo owner/administrator account (role "moderator" is reserved for the admin). */
fun User.isConvoAdmin(): Boolean = role == "moderator"

/**
 * Distinct hero for the administrator profile: an animated constellation cover over the user's
 * own cover, a rotating gold/violet halo with orbiting nodes, and a shield crest under the avatar.
 */
@Composable
fun AdminProfileHero(user: User, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "admin_hero")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val glow by t.animateFloat(.55f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    Box(modifier.fillMaxWidth().height(232.dp)) {
        ProfileCoverArt(user.coverImageUrl, user.uid, Modifier.fillMaxSize(), user.coverScale, user.coverOffsetY)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xE60B0820), Color(0xB3140E3A), MaterialTheme.colorScheme.background))))
        Canvas(Modifier.fillMaxSize()) {
            // Constellation grid
            val seed = user.uid.hashCode()
            val pts = (0 until 26).map { i ->
                val a = ((seed shr (i % 16)) and 0xFF) / 255f
                Offset(size.width * ((i * 0.137f + a * .3f) % 1f), size.height * .78f * ((i * 0.293f + a * .5f) % 1f))
            }
            pts.forEachIndexed { i, p ->
                val q = pts[(i * 7 + 3) % pts.size]
                if ((p - q).getDistance() < size.width * .32f) drawLine(AdminViolet.copy(alpha = .22f * glow), p, q, strokeWidth = 1.dp.toPx())
                drawCircle(if (i % 4 == 0) AdminGold.copy(alpha = glow) else Color.White.copy(alpha = .55f), radius = if (i % 4 == 0) 2.2.dp.toPx() else 1.3.dp.toPx(), center = p)
            }
            // Diagonal light sweep
            rotate(-18f) {
                val x = size.width * ((spin / 360f) * 1.6f - .3f)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .07f), Color.Transparent), startX = x - 80f, endX = x + 80f))
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            shape = CircleShape, color = Color.Black.copy(alpha = .35f)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = AdminGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("OFFICIAL CONVO ADMINISTRATOR", color = AdminGold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).offset(y = 64.dp).size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2 - 8.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(AdminViolet.copy(alpha = .45f * glow), Color.Transparent), center = c, radius = r + 8.dp.toPx()), radius = r + 8.dp.toPx(), center = c)
                rotate(spin, c) {
                    drawCircle(Brush.sweepGradient(listOf(AdminGold, AdminViolet, AdminCyan, AdminGold), center = c), radius = r, center = c, style = Stroke(width = 4.dp.toPx()))
                }
                for (k in 0 until 3) {
                    val a = Math.toRadians((spin * 1.5 + k * 120.0) % 360.0)
                    drawCircle(listOf(AdminGold, AdminCyan, Color.White)[k], radius = 3.5.dp.toPx(), center = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()))
                }
            }
            AsyncImage(
                model = user.profileImageUrl.ifBlank { null }, contentDescription = user.name,
                error = painterResource(R.drawable.img_app_logo), contentScale = ContentScale.Crop,
                modifier = Modifier.size(124.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
            )
            Box(
                Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).size(34.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AdminGold, Color(0xFFFF9E3D)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Shield, "Administrator", tint = Color(0xFF3A2400), modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Gradient badge shown next to the admin's name. */
@Composable
fun AdminNameBadge() {
    Surface(shape = RoundedCornerShape(50), color = Color.Transparent) {
        Row(
            Modifier.background(Brush.horizontalGradient(listOf(AdminGold, Color(0xFFFF9E3D), AdminViolet)), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Verified, null, Modifier.size(15.dp).rotate(0f), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("Admin", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}
