package com.example.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps sharing the user's live location after they leave the chat or the app, as a
 * location-type foreground service with an ongoing "your location is being shared"
 * notification (Android requires this to be visible). Updates are written to Firestore
 * `live_locations/{shareId}`; the share stops automatically at its end time or via the
 * notification's Stop action.
 */
class LiveLocationService : Service() {

    data class ActiveShare(val shareId: String, val recipientId: String, val recipientName: String, val until: Long, val lastFixAt: Long = 0L)

    companion object {
        private const val CHANNEL = "live_location"
        private const val NOTIFICATION_ID = 7341
        private const val ACTION_START = "com.example.location.START"
        private const val ACTION_STOP = "com.example.location.STOP"
        private const val MIN_WRITE_GAP_MS = 8_000L

        private val _active = MutableStateFlow<ActiveShare?>(null)
        val active: StateFlow<ActiveShare?> = _active.asStateFlow()

        fun start(context: Context, shareId: String, senderId: String, recipientId: String, recipientName: String, chatId: String, until: Long, first: Location?) {
            val intent = Intent(context, LiveLocationService::class.java).setAction(ACTION_START)
                .putExtra("shareId", shareId).putExtra("senderId", senderId).putExtra("recipientId", recipientId)
                .putExtra("recipientName", recipientName).putExtra("chatId", chatId).putExtra("until", until)
            first?.let { intent.putExtra("lat", it.latitude).putExtra("lng", it.longitude).putExtra("acc", it.accuracy) }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LiveLocationService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var lastWrite = 0L
    private var shareId = ""
    private var recipientName = ""
    private var until = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = publish(location)
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    private val expiry = Runnable { finish("expired") }
    private val ticker = object : Runnable {
        override fun run() { if (shareId.isNotBlank()) { notifyProgress(); handler.postDelayed(this, 60_000L) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish("stopped"); return START_NOT_STICKY }
        val id = intent?.getStringExtra("shareId").orEmpty()
        if (id.isBlank()) { stopSelf(); return START_NOT_STICKY }
        // A new share replaces the previous one.
        if (shareId.isNotBlank() && shareId != id) markInactive(shareId, "replaced")
        shareId = id
        recipientName = intent?.getStringExtra("recipientName").orEmpty()
        until = intent?.getLongExtra("until", 0L) ?: 0L
        val senderId = intent?.getStringExtra("senderId").orEmpty()
        val recipientId = intent?.getStringExtra("recipientId").orEmpty()
        val chatId = intent?.getStringExtra("chatId").orEmpty()

        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }

        val now = System.currentTimeMillis()
        val base = mutableMapOf<String, Any>(
            "shareId" to id, "senderId" to senderId, "recipientId" to recipientId, "chatId" to chatId,
            "startedAt" to now, "expiresAt" to until, "active" to true, "updatedAt" to now
        )
        if (intent?.hasExtra("lat") == true) {
            base["lat"] = intent.getDoubleExtra("lat", 0.0); base["lng"] = intent.getDoubleExtra("lng", 0.0)
            base["accuracy"] = intent.getFloatExtra("acc", 0f).toDouble()
        }
        FirebaseFirestore.getInstance().collection("live_locations").document(id).set(base, SetOptions.merge())
        _active.value = ActiveShare(id, recipientId, recipientName, until)

        requestUpdates()
        handler.removeCallbacks(expiry)
        handler.postDelayed(expiry, (until - now).coerceAtLeast(5_000L))
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 60_000L)
        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!LocationShare.hasPermission(this)) { finish("permission"); return }
        val lm = locationManager ?: (getSystemService(Context.LOCATION_SERVICE) as LocationManager).also { locationManager = it }
        runCatching { lm.removeUpdates(listener) }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            if (runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) {
                runCatching { lm.requestLocationUpdates(provider, 10_000L, 5f, listener, Looper.getMainLooper()) }
            }
        }
    }

    private fun publish(location: Location) {
        val now = System.currentTimeMillis()
        if (shareId.isBlank()) return
        if (now >= until) { finish("expired"); return }
        if (now - lastWrite < MIN_WRITE_GAP_MS) return
        lastWrite = now
        FirebaseFirestore.getInstance().collection("live_locations").document(shareId).set(
            mapOf(
                "lat" to location.latitude, "lng" to location.longitude, "accuracy" to location.accuracy.toDouble(),
                "speed" to (if (location.hasSpeed()) location.speed.toDouble() else 0.0),
                "bearing" to (if (location.hasBearing()) location.bearing.toDouble() else 0.0),
                "updatedAt" to now, "active" to true
            ), SetOptions.merge()
        )
        _active.value = _active.value?.copy(lastFixAt = now)
    }

    private fun markInactive(id: String, reason: String) {
        FirebaseFirestore.getInstance().collection("live_locations").document(id)
            .set(mapOf("active" to false, "endedAt" to System.currentTimeMillis(), "endReason" to reason), SetOptions.merge())
    }

    private fun finish(reason: String) {
        if (shareId.isNotBlank()) markInactive(shareId, reason)
        shareId = ""
        _active.value = null
        handler.removeCallbacksAndMessages(null)
        runCatching { locationManager?.removeUpdates(listener) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { locationManager?.removeUpdates(listener) }
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live location", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while your live location is being shared"
                })
            }
        }
    }

    private fun remainingLabel(): String {
        val mins = ((until - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        return if (mins >= 60) "${mins / 60} h ${mins % 60} min left" else "$mins min left"
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_convo_foreground)
            .setContentTitle("Your location is being shared")
            .setContentText("Live with ${recipientName.ifBlank { "your chat" }} · ${remainingLabel()}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .addAction(0, "Stop sharing", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notifyProgress() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
    }
}
