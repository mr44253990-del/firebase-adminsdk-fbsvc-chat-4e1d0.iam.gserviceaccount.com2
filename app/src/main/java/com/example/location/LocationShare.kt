package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** A location parsed from a chat message (current or live). */
data class SharedLocation(
    val lat: Double,
    val lng: Double,
    val accuracyM: Int = 0,
    val shareId: String = "",
    val until: Long = 0L
) {
    val isLive: Boolean get() = shareId.isNotBlank()
    val mapsUrl: String get() = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lng)
    val coords: String get() = "%.5f, %.5f".format(Locale.US, lat, lng)
}

/**
 * Location sharing helpers built only on the platform LocationManager (no Play Services
 * dependency). Messages stay human readable for older app versions: a title line, a Google
 * Maps link and the coordinates. Newer versions detect the link and render a map card.
 */
object LocationShare {
    private val LINK = Regex("""maps\.google\.com/\?q=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)(?:&live=([A-Za-z0-9_-]+)&until=(\d+))?""")
    private val ACC = Regex("""±\s?(\d+)\s?m""")

    val DURATIONS = listOf(
        10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", 480 to "8 hours"
    )

    fun parse(text: String?): SharedLocation? {
        if (text.isNullOrBlank() || !text.contains("maps.google.com/?q=")) return null
        val m = LINK.find(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        val acc = ACC.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return SharedLocation(lat, lng, acc, m.groupValues.getOrNull(3).orEmpty(), m.groupValues.getOrNull(4)?.toLongOrNull() ?: 0L)
    }

    fun currentMessage(loc: Location): String {
        val s = SharedLocation(loc.latitude, loc.longitude, loc.accuracy.toInt())
        return "📍 Current location\n${s.mapsUrl}\n${s.coords} (±${s.accuracyM} m)"
    }

    fun liveMessage(loc: Location, shareId: String, until: Long, label: String): String {
        val s = SharedLocation(loc.latitude, loc.longitude, loc.accuracy.toInt())
        return "📡 Live location · $label\n${s.mapsUrl}&live=$shareId&until=$until\n${s.coords} (±${s.accuracyM} m)"
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** One fresh fix (GPS → network), falling back to the newest last-known fix. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context, timeoutMs: Long = 9_000L): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        val fresh = withTimeoutOrNull(timeoutMs) {
            val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return@withTimeoutOrNull null
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc)) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) { if (cont.isActive) cont.resumeWith(Result.success(location)) }
                        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
        return fresh ?: lastKnown?.takeIf { System.currentTimeMillis() - it.time < 30 * 60_000L } ?: lastKnown
    }

    /** OSM slippy-map tile maths (Web Mercator). Returns x, y as fractional tile coordinates. */
    fun tileXY(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val n = (1 shl zoom).toDouble()
        val x = (lng + 180.0) / 360.0 * n
        val latRad = lat * PI / 180.0
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return x to y
    }
}
