package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.location.LiveLocationService
import com.example.location.LocationShare
import com.example.location.SharedLocation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor

/** Live state of a shared location (Firestore live_locations/{shareId}). */
data class LiveLocationState(val lat: Double, val lng: Double, val accuracy: Double, val updatedAt: Long, val expiresAt: Long, val active: Boolean)

@Composable
fun rememberLiveLocation(loc: SharedLocation): State<LiveLocationState?> {
    val state = remember(loc.shareId) { mutableStateOf<LiveLocationState?>(null) }
    if (loc.isLive) {
        DisposableEffect(loc.shareId) {
            val reg = FirebaseFirestore.getInstance().collection("live_locations").document(loc.shareId)
                .addSnapshotListener { snap, _ ->
                    if (snap == null || !snap.exists()) return@addSnapshotListener
                    state.value = LiveLocationState(
                        snap.getDouble("lat") ?: loc.lat, snap.getDouble("lng") ?: loc.lng,
                        snap.getDouble("accuracy") ?: 0.0, snap.getLong("updatedAt") ?: 0L,
                        snap.getLong("expiresAt") ?: loc.until, snap.getBoolean("active") ?: false
                    )
                }
            onDispose { reg.remove() }
        }
    }
    return state
}

/** Canvas-drawn map pin. */
@Composable
fun MapPinGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w / 2, h * 0.98f)
            cubicTo(w * 0.08f, h * 0.55f, w * 0.06f, h * 0.36f, w * 0.06f, h * 0.36f)
            cubicTo(w * 0.06f, h * 0.12f, w * 0.26f, 0f, w / 2, 0f)
            cubicTo(w * 0.74f, 0f, w * 0.94f, h * 0.12f, w * 0.94f, h * 0.36f)
            cubicTo(w * 0.94f, h * 0.36f, w * 0.92f, h * 0.55f, w / 2, h * 0.98f)
            close()
        }
        drawCircle(Color.Black.copy(alpha = .22f), w * 0.16f, Offset(w / 2, h * 0.98f))
        drawPath(p, color)
        drawCircle(Color.White, w * 0.16f, Offset(w / 2, h * 0.36f))
    }
}

/** Static OpenStreetMap preview (3×3 tiles, centred on the point) — no API key needed. */
@Composable
fun OsmMapPreview(lat: Double, lng: Double, modifier: Modifier = Modifier, zoom: Int = 15, pinColor: Color = Color(0xFFE53935), pulse: Boolean = false) {
    val context = LocalContext.current
    val (fx, fy) = remember(lat, lng, zoom) { LocationShare.tileXY(lat, lng, zoom) }
    val tx = floor(fx).toInt()
    val ty = floor(fy).toInt()
    val tile = 104.dp
    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFFE8EEF2))) {
        val px = tile * (1f + (fx - tx).toFloat())
        val py = tile * (1f + (fy - ty).toFloat())
        val ox = maxWidth / 2 - px
        val oy = maxHeight / 2 - py
        Box(Modifier.requiredSize(tile * 3).offset(x = ox + tile * 1.5f - maxWidth / 2, y = oy + tile * 1.5f - maxHeight / 2)) {
            for (dy in -1..1) for (dx in -1..1) {
                val url = "https://tile.openstreetmap.org/$zoom/${tx + dx}/${ty + dy}.png"
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).addHeader("User-Agent", "ConvoChat/4.3 (Android; contact: app)").crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(tile).offset(x = tile * (dx + 1), y = tile * (dy + 1))
                )
            }
        }
        if (pulse) {
            val t = rememberInfiniteTransition(label = "pin_pulse")
            val r by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "pin_r")
            Canvas(Modifier.align(Alignment.Center).size(70.dp)) {
                drawCircle(pinColor.copy(alpha = (1f - r) * .45f), size.minDimension / 2 * r)
            }
        }
        MapPinGlyph(pinColor, Modifier.align(Alignment.Center).offset(y = (-14).dp), 30.dp)
        Text("© OpenStreetMap", fontSize = 8.sp, color = Color(0xFF37474F), modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(.7f)).padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/** Chat bubble content for a location message (current or live). */
@Composable
fun LocationMessageCard(loc: SharedLocation, isMine: Boolean, textColor: Color, accent: Color) {
    val context = LocalContext.current
    val live by rememberLiveLocation(loc)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(loc.shareId) { while (loc.isLive) { delay(15_000); now = System.currentTimeMillis() } }
    val lat = live?.lat ?: loc.lat
    val lng = live?.lng ?: loc.lng
    val ends = live?.expiresAt?.takeIf { it > 0 } ?: loc.until
    val isLiveNow = loc.isLive && (live?.active ?: true) && ends > now
    var showMap by remember { mutableStateOf(false) }
    val activeShare by LiveLocationService.active.collectAsState()

    Column(Modifier.width(250.dp).clip(RoundedCornerShape(18.dp)).clickable { showMap = true }) {
        OsmMapPreview(lat, lng, Modifier.fillMaxWidth().height(130.dp), pinColor = if (isLiveNow) Color(0xFF1E88E5) else Color(0xFFE53935), pulse = isLiveNow)
        Column(Modifier.fillMaxWidth().background(textColor.copy(alpha = .06f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLiveNow) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF43A047)))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when { isLiveNow -> "Live location"; loc.isLive -> "Live location ended"; else -> "Current location" },
                    fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp
                )
            }
            Text("%.5f, %.5f".format(Locale.US, lat, lng) + if ((live?.accuracy ?: loc.accuracyM.toDouble()) > 0) "  ±${(live?.accuracy ?: loc.accuracyM.toDouble()).toInt()} m" else "",
                fontSize = 11.sp, color = textColor.copy(alpha = .75f))
            if (loc.isLive) {
                val updated = live?.updatedAt ?: 0L
                Text(
                    if (isLiveNow) "Updated ${ago(updated, now)} · ends in ${minutesLeft(ends, now)}" else "Sharing has stopped",
                    fontSize = 11.sp, color = if (isLiveNow) accent else textColor.copy(.6f)
                )
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open map", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { showMap = true })
                Text("Google Maps", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))) }
                })
                if (isMine && isLiveNow && activeShare?.shareId == loc.shareId) {
                    Text("Stop", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable { LiveLocationService.stop(context) })
                }
            }
        }
    }
    if (showMap) LiveMapDialog(loc, onDismiss = { showMap = false })
}

private fun ago(t: Long, now: Long): String {
    if (t <= 0) return "just now"
    val s = ((now - t) / 1000).coerceAtLeast(0)
    return when { s < 20 -> "just now"; s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60} min ago"; else -> "${s / 3600} h ago" }
}

private fun minutesLeft(until: Long, now: Long): String {
    val m = ((until - now) / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/**
 * Full-screen interactive map: native pinch-zoom/pan tiles with style picker, distance to the
 * person, turn-by-turn route (OSRM, best-effort) with time and km, and compass-follow mode.
 */
@Composable
fun LiveMapDialog(loc: SharedLocation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by rememberLiveLocation(loc)
    val lat = live?.lat ?: loc.lat
    val lng = live?.lng ?: loc.lng
    val acc = live?.accuracy ?: loc.accuracyM.toDouble()
    val controller = remember { OsmMapController(16.0).also { it.centerOn(loc.lat, loc.lng) } }
    var style by remember { mutableStateOf(MapPrefs.style(context)) }
    var showStyles by remember { mutableStateOf(false) }
    var showDistance by remember { mutableStateOf(MapPrefs.showDistance(context)) }
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var compass by remember { mutableStateOf(false) }
    val heading by rememberCompassHeading(compass)
    var routeMode by remember { mutableStateOf("car") }
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var routing by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf("") }
    var showSteps by remember { mutableStateOf(false) }
    // My position (best-effort, only with location permission), refreshed every 20 s.
    LaunchedEffect(showDistance, route != null) {
        if (!showDistance && route == null) return@LaunchedEffect
        while (true) {
            if (com.example.location.LocationShare.hasPermission(context)) {
                com.example.location.LocationShare.currentLocation(context, 8_000L)?.let { me = it.latitude to it.longitude }
            }
            kotlinx.coroutines.delay(20_000)
        }
    }
    fun loadRoute(mode: String) {
        val from = me
        if (from == null) { routeError = if (com.example.location.LocationShare.hasPermission(context)) "Finding your location…" else "Allow location permission to get a route"; return }
        routing = true; routeError = ""; routeMode = mode
        scope.launch {
            val r = OsrmRouter.route(from.first, from.second, lat, lng, mode)
            routing = false
            if (r == null) routeError = "No route found (routing server busy or unreachable). Try again or use Directions."
            else { route = r; controller.follow = false }
        }
    }
    val distance = me?.let { haversineMeters(it.first, it.second, lat, lng) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFFE8EEF2))) {
            InteractiveOsmMap(
                lat = lat, lng = lng, controller = controller,
                modifier = Modifier.fillMaxSize(),
                pinColor = if (loc.isLive) Color(0xFF1E88E5) else Color(0xFFE53935),
                pulse = loc.isLive,
                accuracyMeters = acc,
                tileStyle = style,
                myLocation = if (showDistance || route != null) me else null,
                route = route?.points.orEmpty(),
                bearing = if (compass) heading else 0f
            )
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 4.dp, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(if (loc.isLive) "Live location" else "Shared location", fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                            Text("%.5f, %.5f".format(Locale.US, lat, lng) + if (acc > 0) "  ±${acc.toInt()} m" else "", fontSize = 11.sp, color = Color(0xFF444444))
                        }
                    }
                }
                if (showDistance && distance != null && route == null) Surface(shape = CircleShape, color = Color(0xFF1A73E8), shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SocialDistance, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text("${formatDistance(distance)} away (straight line)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                route?.let { r ->
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .97f), shadowElevation = 6.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            val next = r.steps.firstOrNull { it.type != "depart" } ?: r.steps.firstOrNull()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (next != null) Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A73E8)), contentAlignment = Alignment.Center) {
                                    Icon(turnIcon(next), null, tint = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(next?.instruction ?: "Route ready", fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), maxLines = 2)
                                    Text("${formatDuration(r.duration)} · ${formatDistance(r.distance)}", color = Color(0xFF1A73E8), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                IconButton(onClick = { route = null; routeError = "" }) { Icon(Icons.Default.Close, "Clear route", tint = Color(0xFF444444)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(Triple("car", Icons.Default.DirectionsCar, "Car"), Triple("bike", Icons.Default.DirectionsBike, "Bike"), Triple("foot", Icons.Default.DirectionsWalk, "Walk")).forEach { (m, icon, label) ->
                                    FilterChip(selected = r.mode == m, onClick = { loadRoute(m) }, label = { Text(label, fontSize = 11.sp) }, leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) })
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showSteps = true }) { Text("Steps (${r.steps.size})") }
                            }
                        }
                    }
                }
                if (routeError.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3E0)) { Text(routeError, Modifier.padding(8.dp), fontSize = 12.sp, color = Color(0xFF6D4C41)) }
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .95f), shadowElevation = 6.dp) {
                    Column {
                        IconButton(onClick = { controller.zoomBy(1.0) }) { Icon(Icons.Default.Add, "Zoom in", tint = Color(0xFF1B1B1F)) }
                        HorizontalDivider(Modifier.width(48.dp), color = Color(0x22000000))
                        IconButton(onClick = { controller.zoomBy(-1.0) }) { Icon(Icons.Default.Remove, "Zoom out", tint = Color(0xFF1B1B1F)) }
                    }
                }
                MapRoundButton(Icons.Default.Layers, "Map style", false) { showStyles = !showStyles }
                MapRoundButton(Icons.Default.SocialDistance, "Show distance", showDistance) { showDistance = !showDistance; MapPrefs.setShowDistance(context, showDistance) }
                MapRoundButton(Icons.Default.Explore, if (compass) "Compass on" else "Follow phone direction", compass) { compass = !compass }
                MapRoundButton(Icons.Default.MyLocation, "Center on pin", controller.follow) { controller.follow = true; controller.centerOn(lat, lng) }
                if (me != null) MapRoundButton(Icons.Default.NearMe, "Center on me", false) { controller.follow = false; me?.let { controller.centerOn(it.first, it.second) } }
            }
            if (showStyles) Surface(
                Modifier.align(Alignment.CenterEnd).padding(end = 72.dp),
                shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .98f), shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(8.dp).width(170.dp)) {
                    Text("Map style", fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), modifier = Modifier.padding(6.dp))
                    MapTileStyle.values().forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { style = s; MapPrefs.setStyle(context, s); showStyles = false }
                                .background(if (s == style) Color(0xFF1A73E8).copy(alpha = .12f) else Color.Transparent).padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(if (s.dark) Color(0xFF263238) else Color(0xFFB3E5FC)))
                            Spacer(Modifier.width(8.dp))
                            Text(s.label, color = Color(0xFF1B1B1F), fontSize = 13.sp, fontWeight = if (s == style) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            if (compass) Surface(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 70.dp, end = 14.dp), shape = CircleShape, color = Color.White.copy(alpha = .95f), shadowElevation = 4.dp) {
                Icon(Icons.Default.Navigation, "North", tint = Color(0xFFE53935), modifier = Modifier.padding(8.dp).size(22.dp).rotate(-heading))
            }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = { loadRoute(routeMode) }, enabled = !routing) {
                    if (routing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.Route, null)
                    Spacer(Modifier.width(6.dp)); Text(if (route == null) "Route" else "Update route")
                }
                FilledTonalButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))) } }) {
                    Icon(Icons.Default.Directions, null); Spacer(Modifier.width(6.dp)); Text("Open in app")
                }
            }
            if (showSteps) route?.let { r ->
                AlertDialog(
                    onDismissRequest = { showSteps = false },
                    title = { Text("${formatDuration(r.duration)} · ${formatDistance(r.distance)}") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(r.steps.size) { i ->
                                val st = r.steps[i]
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(turnIcon(st), null, tint = Color(0xFF1A73E8))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(st.instruction, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        if (st.distance > 0) Text("${formatDistance(st.distance)} · ${formatDuration(st.duration)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSteps = false }) { Text("Close") } }
                )
            }
            Text("Routes: OSRM / FOSSGIS (best-effort)", fontSize = 8.sp, color = Color(0xFF37474F), modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().background(Color.White.copy(.7f)).padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun MapRoundButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = if (active) Color(0xFF1E88E5) else Color.White.copy(alpha = .95f), shadowElevation = 6.dp) {
        IconButton(onClick = onClick) { Icon(icon, description, tint = if (active) Color.White else Color(0xFF1B1B1F)) }
    }
}

/** Bottom sheet: share current location or live location with a duration picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareSheet(
    recipientName: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onShareCurrent: () -> Unit,
    onShareLive: (minutes: Int, label: String) -> Unit
) {
    var minutes by remember { mutableIntStateOf(15) }
    val activeShare by LiveLocationService.active.collectAsState()
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MapPinGlyph(Color(0xFFE53935), size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Share location", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("with ${recipientName.ifBlank { "this chat" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            activeShare?.let { share ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1E88E5).copy(alpha = .12f)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF43A047)))
                        Spacer(Modifier.width(8.dp))
                        Text("Live sharing with ${share.recipientName.ifBlank { "a chat" }} · ${minutesLeft(share.until, System.currentTimeMillis())} left", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { LiveLocationService.stop(context) }) { Text("Stop", color = Color(0xFFE53935)) }
                    }
                }
            }
            LocationOptionCard(
                icon = Icons.Default.MyLocation, color = Color(0xFFE53935),
                title = "Send current location", subtitle = "One-time pin with coordinates and a map link",
                loading = loading, onClick = onShareCurrent
            )
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5).copy(alpha = .1f))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF1E88E5)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LocationOn, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share live location", fontWeight = FontWeight.Bold)
                            Text("Updates every ~10 s, keeps running in the background with a notification", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRowCompat {
                        LocationShare.DURATIONS.forEach { (m, label) ->
                            FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(label) })
                        }
                    }
                    Button(
                        onClick = { onShareLive(minutes, LocationShare.DURATIONS.first { it.first == minutes }.second) },
                        enabled = !loading, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.NearMe, null)
                        Spacer(Modifier.width(6.dp)); Text("Start live sharing")
                    }
                }
            }
            Text("Location turns on only when you choose. Live sharing stops automatically at the end time or from the notification.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LocationOptionCard(icon: ImageVector, color: Color, title: String, subtitle: String, loading: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth().clickable(enabled = !loading, onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(if (loading) "Getting your location…" else subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

/** One tile of the redesigned attachment sheet. */
data class AttachAction(val key: String, val label: String, val icon: ImageVector, val colors: List<Color>)

/** Redesigned attachment sheet: colourful gradient tiles for every share type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAttachSheet(actions: List<AttachAction>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 26.dp)) {
            Text("Share", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("Pick what you want to send", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            actions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { a ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable { onPick(a.key) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(a.colors))
                                    .border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                            Spacer(Modifier.height(6.dp))
                            Text(a.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(76.dp)) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
