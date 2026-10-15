package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/** Colors for the two login looks: Neon night and Milk Liquid Glass. */
@Immutable
data class LoginPalette(
    val milk: Boolean,
    val background: Brush,
    val orbs: List<Color>,
    val text: Color,
    val textSoft: Color,
    val card: Color,
    val cardBorder: List<Color>,
    val glass: Color,
    val glassBorder: Color,
    val link: Color,
    val highlight: Color,
    val heroCard: Brush
)

fun loginPalette(milk: Boolean): LoginPalette = if (milk) {
    LoginPalette(
        milk = true,
        background = Brush.verticalGradient(listOf(Color(0xFFFFFDF8), Color(0xFFF3F0FF), Color(0xFFFFF1E8), Color(0xFFEAF4FF))),
        orbs = listOf(Color(0xFFB9C3FF), Color(0xFFFFC7DD), Color(0xFFFFD7B5), Color(0xFFB8E8FF)),
        text = Color(0xFF1E1B2E),
        textSoft = Color(0xFF6A667C),
        card = Color.White.copy(alpha = .68f),
        cardBorder = listOf(Color.White, Color(0xFFDCD8FF), Color(0xFFFFE1EE)),
        glass = Color.White.copy(alpha = .55f),
        glassBorder = Color.White.copy(alpha = .95f),
        link = Color(0xFF5B63E6),
        highlight = Color(0xFF6F78F0),
        heroCard = Brush.linearGradient(listOf(Color.White.copy(alpha = .9f), Color(0xFFF1EEFF).copy(alpha = .8f), Color(0xFFFFEFE6).copy(alpha = .85f)))
    )
} else {
    LoginPalette(
        milk = false,
        background = Brush.verticalGradient(listOf(Color(0xFF050A1D), Color(0xFF071A35), Color(0xFF160B2C), Color(0xFF050817))),
        orbs = listOf(Color(0xFF00C8FF), Color(0xFFFF4CCB), Color(0xFF8C3DFF), Color(0xFFFF70E8)),
        text = Color.White,
        textSoft = Color(0xFFD0CFE3),
        card = Color(0xFF11162E).copy(alpha = .9f),
        cardBorder = listOf(Color(0xFF673AB7), Color(0xFF00BCD4), Color(0xFFE91E63)),
        glass = Color.White.copy(alpha = .07f),
        glassBorder = Color.White.copy(alpha = .14f),
        link = Color(0xFF9D8CFF),
        highlight = Color(0xFF63E6FF),
        heroCard = Brush.linearGradient(listOf(Color(0xFF12142A), Color(0xFF27164C), Color(0xFF071D2B)))
    )
}

/**
 * Smoothed phone tilt in -1..1 from the accelerometer. Returns zero when the phone has
 * no sensor; the listener is removed as soon as the login screen leaves composition.
 */
@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var sx = 0f
        var sy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (-event.values[0] / 6f).coerceIn(-1f, 1f)
                val y = ((event.values[1] - 6.5f) / 6f).coerceIn(-1f, 1f)
                sx += (x - sx) * .12f
                sy += (y - sy) * .12f
                tilt.value = Offset(sx, sy)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}

/** Floating orbs that move with the tilt for a parallax depth effect. */
@Composable
fun BoxScope.LoginParallaxOrbs(palette: LoginPalette, tilt: Offset, drift: Float, pulse: Float) {
    val orbAlpha = if (palette.milk) .75f else 1f
    LoginOrb(150, Alignment.TopStart, -40f, 30f + drift, 1.2f, palette.orbs[0], pulse * orbAlpha, tilt)
    LoginOrb(190, Alignment.TopEnd, 60f, 200f - drift, .8f, palette.orbs[1], pulse * .75f * orbAlpha, tilt)
    LoginOrb(110, Alignment.CenterStart, -30f, 160f + drift / 2, 1.6f, palette.orbs[2], .7f * orbAlpha, tilt)
    LoginOrb(84, Alignment.CenterEnd, 24f, 90f, 2f, palette.orbs[3], pulse * orbAlpha, tilt)
    LoginOrb(220, Alignment.BottomCenter, -60f, 80f - drift, .5f, palette.orbs[0].copy(alpha = .8f), .55f * orbAlpha, tilt)
}

@Composable
private fun BoxScope.LoginOrb(size: Int, align: Alignment, x: Float, y: Float, depth: Float, color: Color, alpha: Float, tilt: Offset) {
    Box(
        Modifier
            .align(align)
            .size(size.dp)
            .graphicsLayer {
                translationX = (x + tilt.x * depth * 36f).dp.toPx()
                translationY = (y + tilt.y * depth * 36f).dp.toPx()
                this.alpha = alpha.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color, color.copy(alpha = .35f), Color.Transparent)))
    )
}

/**
 * 3D hero: a glass card that tilts with the phone (or a finger drag), with the logo,
 * an orbit ring and chat bubbles floating at different depths.
 */
@Composable
fun Login3DHero(palette: LoginPalette, deviceTilt: Offset, modifier: Modifier = Modifier) {
    var dragTilt by remember { mutableStateOf(Offset.Zero) }
    val transition = rememberInfiniteTransition(label = "hero3d")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "hero_spin")
    val float by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hero_float")
    val tilt = Offset((deviceTilt.x + dragTilt.x).coerceIn(-1f, 1f), (deviceTilt.y + dragTilt.y).coerceIn(-1f, 1f))

    // Gesture node stays untransformed; only the inner layers rotate.
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { dragTilt = Offset.Zero },
                    onDragCancel = { dragTilt = Offset.Zero }
                ) { change, amount ->
                    change.consume()
                    dragTilt = Offset(
                        (dragTilt.x + amount.x / size.width * 2.4f).coerceIn(-1f, 1f),
                        (dragTilt.y + amount.y / size.height * 2.4f).coerceIn(-1f, 1f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth(.94f)
                .height(168.dp)
                .graphicsLayer {
                    rotationY = tilt.x * 14f
                    rotationX = -tilt.y * 12f
                    cameraDistance = 14f * density
                    shadowElevation = 22.dp.toPx()
                    shape = RoundedCornerShape(34.dp)
                    clip = true
                    ambientShadowColor = palette.highlight
                    spotShadowColor = palette.highlight
                }
                .background(palette.heroCard)
                .border(1.2.dp, Brush.linearGradient(listOf(palette.glassBorder, palette.highlight.copy(alpha = .5f), palette.glassBorder.copy(alpha = .2f))), RoundedCornerShape(34.dp))
        ) {
            // Moving specular sheen follows the tilt like light on real glass
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (palette.milk) .55f else .16f), Color.Transparent),
                        start = Offset(200f + tilt.x * 260f, 0f),
                        end = Offset(520f + tilt.x * 260f, 520f)
                    )
                )
            )
            // Orbit ring (back layer)
            Canvas(
                Modifier.align(Alignment.Center).size(150.dp)
                    .graphicsLayer { translationX = -tilt.x * 10f; translationY = -tilt.y * 8f; rotationZ = spin; rotationX = 62f; cameraDistance = 10f * density }
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(palette.highlight, Color.Transparent, palette.orbs[1], Color.Transparent, palette.highlight)),
                    radius = size.minDimension / 2.1f,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(palette.orbs[1], radius = 5.dp.toPx(), center = Offset(size.width / 2, size.height / 2 - size.minDimension / 2.1f))
            }
            // Logo (middle layer)
            Image(
                painter = painterResource(R.drawable.img_app_logo),
                contentDescription = "Convo Chat",
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).size(118.dp)
                    .graphicsLayer { translationX = tilt.x * 14f; translationY = tilt.y * 12f + float * 6f }
            )
            // Floating chat bubbles (front layer, strongest parallax)
            HeroBubble("Hey! 👋", palette, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 18.dp)
                .graphicsLayer { translationX = tilt.x * 34f; translationY = tilt.y * 26f - float * 5f }, sent = false)
            HeroBubble("Liquid glass ✨", palette, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 40.dp)
                .graphicsLayer { translationX = tilt.x * 40f; translationY = tilt.y * 30f + float * 5f }, sent = true)
            Row(
                Modifier.align(Alignment.BottomStart).padding(14.dp)
                    .clip(RoundedCornerShape(14.dp)).background(palette.glass).border(1.dp, palette.glassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = palette.highlight, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("Secure chat", color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroBubble(text: String, palette: LoginPalette, modifier: Modifier, sent: Boolean) {
    val shape = if (sent) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    Box(
        modifier.clip(shape)
            .background(if (sent) Brush.linearGradient(listOf(Color(0xFF6C7BFF), Color(0xFFB06BFF))) else Brush.linearGradient(listOf(palette.glass, palette.glass)))
            .border(1.dp, if (sent) Color.White.copy(alpha = .45f) else palette.glassBorder, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (sent) Color.White else palette.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Switch between the Neon and Milk Liquid Glass login looks. */
@Composable
fun LoginLookToggle(palette: LoginPalette, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(palette.glass).border(1.dp, palette.glassBorder, CircleShape)
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (palette.milk) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = palette.highlight, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (palette.milk) "Neon look" else "Milk glass", color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
