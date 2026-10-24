package com.example.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Controller for [InteractiveOsmMap]. Positions are kept in normalised Web-Mercator
 * coordinates (0..1), so they stay valid at every zoom level.
 */
class OsmMapController(initialZoom: Double = 16.0) {
    var zoom by mutableDoubleStateOf(initialZoom)
    var centerX by mutableDoubleStateOf(.5)
    var centerY by mutableDoubleStateOf(.5)
    /** When true the map keeps following the pin (live location). Panning turns it off. */
    var follow by mutableStateOf(true)

    fun zoomBy(delta: Double) { zoom = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM) }
    fun centerOn(lat: Double, lng: Double) {
        val (x, y) = mercator(lat, lng)
        centerX = x; centerY = y
    }

    companion object {
        const val MIN_ZOOM = 2.0
        const val MAX_ZOOM = 19.0
        fun mercator(lat: Double, lng: Double): Pair<Double, Double> {
            val x = (lng + 180.0) / 360.0
            val la = lat.coerceIn(-85.0, 85.0) * PI / 180.0
            val y = (1.0 - ln(tan(la) + 1.0 / cos(la)) / PI) / 2.0
            return x to y
        }
    }
}

/**
 * Native pinch-zoom / pan / double-tap map built from OpenStreetMap tiles. No WebView and no
 * JavaScript CDN, so it never shows a blank white page when unpkg/Leaflet is blocked.
 */
@Composable
fun InteractiveOsmMap(
    lat: Double,
    lng: Double,
    controller: OsmMapController,
    modifier: Modifier = Modifier,
    pinColor: Color = Color(0xFFE53935),
    pulse: Boolean = false,
    accuracyMeters: Double = 0.0,
    tileStyle: MapTileStyle = MapTileStyle.STANDARD,
    myLocation: Pair<Double, Double>? = null,
    route: List<Pair<Double, Double>> = emptyList(),
    /** Map rotation in degrees (compass heading). 0 = north up. */
    bearing: Float = 0f
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val basePx = with(density) { 150.dp.toPx() }
    LaunchedEffect(lat, lng) { if (controller.follow) controller.centerOn(lat, lng) }

    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFFE8EEF2))) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        // When rotating, render a square that covers the viewport at any angle.
        val rotating = bearing != 0f
        val side = kotlin.math.hypot(viewW, viewH)
        val w = if (rotating) side else viewW
        val h = if (rotating) side else viewH
        val bearingState = rememberUpdatedState(bearing)
        val gestureModifier = Modifier
            .pointerInput(basePx, rotating) {
                detectTransformGestures { rawCentroid, rawPan, zoomChange, _ ->
                    // Screen gestures -> map space (undo the compass rotation).
                    val rad = Math.toRadians(bearingState.value.toDouble())
                    val cs = cos(rad).toFloat(); val sn = kotlin.math.sin(rad).toFloat()
                    fun toMap(v: androidx.compose.ui.geometry.Offset) = androidx.compose.ui.geometry.Offset(v.x * cs - v.y * sn, v.x * sn + v.y * cs)
                    val c0 = rawCentroid - androidx.compose.ui.geometry.Offset(viewW / 2, viewH / 2)
                    val centroid = toMap(c0) + androidx.compose.ui.geometry.Offset(w / 2, h / 2)
                    val pan = toMap(rawPan)
                    val worldPx = basePx * 2.0.pow(controller.zoom)
                    // Keep the point under the fingers fixed while zooming.
                    val wx = controller.centerX + (centroid.x - w / 2) / worldPx
                    val wy = controller.centerY + (centroid.y - h / 2) / worldPx
                    val newZoom = (controller.zoom + ln(zoomChange.toDouble()) / ln(2.0)).coerceIn(OsmMapController.MIN_ZOOM, OsmMapController.MAX_ZOOM)
                    val newWorld = basePx * 2.0.pow(newZoom)
                    controller.zoom = newZoom
                    controller.centerX = (wx - (centroid.x - w / 2) / newWorld - pan.x / newWorld).let { ((it % 1.0) + 1.0) % 1.0 }
                    controller.centerY = (wy - (centroid.y - h / 2) / newWorld - pan.y / newWorld).coerceIn(0.0, 1.0)
                    if (pan.getDistance() > 2f) controller.follow = false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { controller.zoomBy(1.0) })
            }

        Box(Modifier.matchParentSize().then(gestureModifier), contentAlignment = Alignment.Center) {
        Box(
            Modifier.wrapContentSize(unbounded = true).requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer { rotationZ = -bearing }
        ) {
            val z = floor(controller.zoom).toInt().coerceIn(2, tileStyle.maxZoom)
            val n = 1 shl z
            val tilePx = (basePx * 2.0.pow(controller.zoom - z)).toFloat()
            val tileDp = with(density) { tilePx.toDp() }
            val ctx = controller.centerX * n
            val cty = controller.centerY * n
            val minX = floor(ctx - (w / 2) / tilePx).toInt()
            val maxX = floor(ctx + (w / 2) / tilePx).toInt()
            val minY = floor(cty - (h / 2) / tilePx).toInt().coerceAtLeast(0)
            val maxY = floor(cty + (h / 2) / tilePx).toInt().coerceAtMost(n - 1)
            for (iy in minY..maxY) for (ix in minX..maxX) {
                val wrappedX = ((ix % n) + n) % n
                val url = tileStyle.url(z, wrappedX, iy)
                val sx = ((ix - ctx) * tilePx + w / 2).roundToInt()
                val sy = ((iy - cty) * tilePx + h / 2).roundToInt()
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url)
                        .addHeader("User-Agent", "ConvoChat/4.3 (Android; contact: app)")
                        .crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.offset { IntOffset(sx, sy) }.size(tileDp + 0.5.dp)
                )
            }

            // Pin + accuracy circle.
            val (px, py) = OsmMapController.mercator(lat, lng)
            val worldPx = basePx * 2.0.pow(controller.zoom)
            var dx = px - controller.centerX
            if (dx > .5) dx -= 1.0 else if (dx < -.5) dx += 1.0
            val pinX = (dx * worldPx + w / 2).toFloat()
            val pinY = ((py - controller.centerY) * worldPx + h / 2).toFloat()
            val metersPerPx = 156543.03392 * cos(lat * PI / 180.0) / (2.0.pow(controller.zoom) * basePx / 256.0)
            val accPx = if (accuracyMeters > 0) (accuracyMeters / metersPerPx).toFloat() else 0f
            val pulseT = if (pulse) {
                val t = rememberInfiniteTransition(label = "map_pulse")
                t.animateFloat(.3f, 1f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "map_pulse_r").value
            } else 0f
            fun screenOf(la: Double, lo: Double): androidx.compose.ui.geometry.Offset {
                val (mx, my) = OsmMapController.mercator(la, lo)
                var ddx = mx - controller.centerX
                if (ddx > .5) ddx -= 1.0 else if (ddx < -.5) ddx += 1.0
                return androidx.compose.ui.geometry.Offset((ddx * worldPx + w / 2).toFloat(), ((my - controller.centerY) * worldPx + h / 2).toFloat())
            }
            Canvas(Modifier.matchParentSize()) {
                if (route.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
                    route.forEachIndexed { i, (la, lo) -> val o = screenOf(la, lo); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawPath(path, Color(0xFF1A73E8), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                myLocation?.let { (la, lo) ->
                    val o = screenOf(la, lo)
                    drawCircle(Color(0xFF1A73E8).copy(alpha = .18f), 18.dp.toPx(), o)
                    drawCircle(Color.White, 8.dp.toPx(), o)
                    drawCircle(Color(0xFF1A73E8), 5.5.dp.toPx(), o)
                }
                if (accPx > 4f) {
                    drawCircle(Color(0xFF1E88E5).copy(alpha = .12f), accPx, androidx.compose.ui.geometry.Offset(pinX, pinY))
                }
                if (pulse) drawCircle(pinColor.copy(alpha = (1f - pulseT) * .45f), 34.dp.toPx() * pulseT, androidx.compose.ui.geometry.Offset(pinX, pinY))
                drawCircle(Color.White, 9.dp.toPx(), androidx.compose.ui.geometry.Offset(pinX, pinY))
                drawCircle(pinColor, 6.5.dp.toPx(), androidx.compose.ui.geometry.Offset(pinX, pinY))
            }
            MapPinGlyph(
                pinColor,
                Modifier.offset { IntOffset((pinX - 18.dp.toPx()).roundToInt(), (pinY - 44.dp.toPx()).roundToInt()) },
                36.dp
            )
        }
        }
        Text(
            tileStyle.attribution,
            fontSize = 9.sp,
            color = Color(0xFF37474F),
            modifier = Modifier.align(Alignment.BottomStart).background(Color.White.copy(.75f)).padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
