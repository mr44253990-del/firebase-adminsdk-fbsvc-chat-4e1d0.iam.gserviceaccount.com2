package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Raster tile providers for the native map. All are free for light, attributed use. */
enum class MapTileStyle(val label: String, val template: String, val attribution: String, val maxZoom: Int = 19, val dark: Boolean = false) {
    STANDARD("Standard", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors"),
    VOYAGER("Voyager", "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png", "© OpenStreetMap contributors © CARTO"),
    LIGHT("Light", "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png", "© OpenStreetMap contributors © CARTO"),
    DARK("Dark", "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png", "© OpenStreetMap contributors © CARTO", dark = true),
    TERRAIN("Terrain", "https://a.tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors, SRTM © OpenTopoMap (CC-BY-SA)", maxZoom = 17),
    SATELLITE("Satellite", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "Imagery © Esri, Maxar, Earthstar Geographics", dark = true),
    STREETS("Esri Streets", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}", "© Esri, HERE, Garmin, OpenStreetMap contributors");

    fun url(z: Int, x: Int, y: Int): String = template.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())

    companion object {
        fun from(id: String?): MapTileStyle = values().firstOrNull { it.name == id } ?: STANDARD
    }
}

object MapPrefs {
    private fun p(context: Context) = context.getSharedPreferences("convo_map", Context.MODE_PRIVATE)
    fun style(context: Context) = MapTileStyle.from(p(context).getString("style", null))
    fun setStyle(context: Context, s: MapTileStyle) = p(context).edit().putString("style", s.name).apply()
    fun showDistance(context: Context) = p(context).getBoolean("distance", true)
    fun setShowDistance(context: Context, v: Boolean) = p(context).edit().putBoolean("distance", v).apply()
}

/** Great-circle distance in metres. */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m"
    meters < 100_000 -> String.format(Locale.US, "%.1f km", meters / 1000)
    else -> "${(meters / 1000).toInt()} km"
}

fun formatDuration(seconds: Double): String {
    val m = (seconds / 60).toInt()
    return if (m < 60) "${m.coerceAtLeast(1)} min" else "${m / 60} h ${m % 60} min"
}

data class RouteStep(val instruction: String, val type: String, val modifier: String, val distance: Double, val duration: Double, val lat: Double, val lng: Double)
data class RouteResult(val points: List<Pair<Double, Double>>, val distance: Double, val duration: Double, val steps: List<RouteStep>, val mode: String)

/**
 * Turn-by-turn routes from the public OSRM servers (best-effort, no key). FOSSGIS
 * routing.openstreetmap.de offers car / bike / foot; router.project-osrm.org is the car fallback.
 */
object OsrmRouter {
    private val http by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }

    suspend fun route(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String = "car"): RouteResult? = withContext(Dispatchers.IO) {
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", fromLng, fromLat, toLng, toLat)
        val q = "?overview=full&geometries=geojson&steps=true"
        val profile = when (mode) { "foot" -> "routed-foot"; "bike" -> "routed-bike"; else -> "routed-car" }
        val urls = listOf("https://routing.openstreetmap.de/$profile/route/v1/driving/$coords$q") +
            if (mode == "car") listOf("https://router.project-osrm.org/route/v1/driving/$coords$q") else emptyList()
        for (url in urls) {
            val parsed = runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "ConvoChat/4.3 (Android)").build()).execute().use { r ->
                    if (!r.isSuccessful) null else parse(JSONObject(r.body?.string().orEmpty()), mode)
                }
            }.getOrNull()
            if (parsed != null) return@withContext parsed
        }
        null
    }

    private fun parse(j: JSONObject, mode: String): RouteResult? {
        if (j.optString("code") != "Ok") return null
        val route = j.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val points = (0 until coords.length()).mapNotNull { i -> coords.optJSONArray(i)?.let { it.optDouble(1) to it.optDouble(0) } }
        val steps = mutableListOf<RouteStep>()
        val legs = route.optJSONArray("legs")
        if (legs != null) for (l in 0 until legs.length()) {
            val arr = legs.optJSONObject(l)?.optJSONArray("steps") ?: continue
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val m = s.optJSONObject("maneuver") ?: JSONObject()
                val loc = m.optJSONArray("location")
                val type = m.optString("type"); val mod = m.optString("modifier")
                steps += RouteStep(instruction(type, mod, s.optString("name"), m.optInt("exit", 0)), type, mod, s.optDouble("distance"), s.optDouble("duration"), loc?.optDouble(1) ?: 0.0, loc?.optDouble(0) ?: 0.0)
            }
        }
        return RouteResult(points, route.optDouble("distance"), route.optDouble("duration"), steps, mode)
    }

    private fun instruction(type: String, modifier: String, road: String, exit: Int): String {
        val onto = if (road.isNotBlank()) " onto $road" else ""
        val dir = when (modifier) {
            "left" -> "left"; "right" -> "right"; "slight left" -> "slightly left"; "slight right" -> "slightly right"
            "sharp left" -> "sharp left"; "sharp right" -> "sharp right"; "uturn" -> "a U-turn"; "straight" -> "straight"; else -> modifier
        }
        return when (type) {
            "depart" -> "Start" + if (road.isNotBlank()) " on $road" else ""
            "arrive" -> "Arrive at the destination"
            "turn", "end of road" -> if (modifier == "uturn") "Make a U-turn$onto" else "Turn $dir$onto"
            "continue", "new name" -> if (modifier == "straight" || modifier.isBlank()) "Continue straight$onto" else "Keep $dir$onto"
            "merge" -> "Merge $dir$onto"
            "on ramp" -> "Take the ramp $dir$onto"
            "off ramp" -> "Take the exit $dir$onto"
            "fork" -> "Keep $dir at the fork$onto"
            "roundabout", "rotary" -> "At the roundabout take exit ${exit.coerceAtLeast(1)}$onto"
            "roundabout turn" -> "At the roundabout turn $dir$onto"
            else -> "Go $dir$onto".replace("Go  ", "Go ")
        }
    }
}

fun turnIcon(step: RouteStep): ImageVector = when {
    step.type == "arrive" -> Icons.Default.Flag
    step.type == "depart" -> Icons.Default.TripOrigin
    step.type.startsWith("roundabout") || step.type == "rotary" -> Icons.Default.RotateRight
    step.modifier == "uturn" -> Icons.Default.UTurnLeft
    step.modifier.contains("left") -> if (step.modifier.startsWith("slight")) Icons.Default.TurnSlightLeft else Icons.Default.TurnLeft
    step.modifier.contains("right") -> if (step.modifier.startsWith("slight")) Icons.Default.TurnSlightRight else Icons.Default.TurnRight
    else -> Icons.Default.Straight
}

/**
 * Phone heading in degrees (0 = north) from the rotation-vector sensor, low-pass filtered.
 * Returns 0 when [enabled] is false or the device has no such sensor.
 */
@Composable
fun rememberCompassHeading(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val heading = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sm == null || sensor == null) { heading.floatValue = 0f; return@DisposableEffect onDispose { } }
        val rot = FloatArray(9); val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                SensorManager.getOrientation(rot, orient)
                val deg = ((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0).toFloat()
                // Smooth across the 359 -> 0 wrap.
                var diff = deg - heading.floatValue
                if (diff > 180f) diff -= 360f else if (diff < -180f) diff += 360f
                heading.floatValue = (heading.floatValue + diff * .15f + 360f) % 360f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}
