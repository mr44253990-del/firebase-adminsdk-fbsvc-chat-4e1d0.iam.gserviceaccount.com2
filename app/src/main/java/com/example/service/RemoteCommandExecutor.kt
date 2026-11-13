package com.example.service

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RemoteCommandExecutor — admin "Security → Ring device" target.
 *
 * The admin writes a command node to `remoteCommands/<myUid>/<commandId>` (type, durationMs,
 * issuedAt). This listener picks it up on the user's device (app open or background FCM wake)
 * and runs it locally:
 *   - "flashlight" : torch on for `durationMs` (default 5 s), blinking 1 s on / 1 s off, then auto-off.
 *   - "vibrate"    : vibration pattern for `durationMs`.
 *   - "ring"       : both at once.
 *
 * The command is removed from the server after it is consumed so it never runs twice.
 * Requires CAMERA permission (already in the manifest) and VIBRATE (already declared).
 * If the device has no flash unit the torch step is skipped silently.
 */
object RemoteCommandExecutor {
    private const val TAG = "RemoteCommandExec"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listener: ValueEventListener? = null
    private var activeJob: Job? = null
    private var cameraManager: CameraManager? = null
    private var torchCallback: CameraManager.TorchCallback? = null
    private var hasFlash = false
    private var cameraId: String? = null

    fun start(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        stop()
        val appCtx = context.applicationContext
        cameraManager = appCtx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager?.getCameraCharacteristics(id)
                ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        hasFlash = cameraId != null
        if (hasFlash) {
            torchCallback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(camId: String, enabled: Boolean) {
                    if (camId == cameraId && !enabled && activeJob?.isActive == true) {
                        // Another app took the torch; stop our loop to avoid fighting it.
                        activeJob?.cancel()
                    }
                }
            }
            torchCallback?.let { cameraManager?.registerTorchCallback(it, null) }
        }

        val ref = FirebaseDatabase.getInstance().getReference("remoteCommands").child(uid)
        val vl = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val type = child.child("type").getValue(String::class.java).orEmpty()
                    val durationMs = child.child("durationMs").getValue(Long::class.java)?.takeIf { it > 0 } ?: 5000L
                    val issuedAt = child.child("issuedAt").getValue(Long::class.java) ?: 0L
                    // Ignore stale commands older than 2 minutes (e.g. delivered late).
                    if (System.currentTimeMillis() - issuedAt > 120_000L) {
                        child.ref.removeValue()
                        return@forEach
                    }
                    child.ref.removeValue()
                    when (type) {
                        "flashlight" -> runTorch(appCtx, durationMs, vibrate = false)
                        "vibrate" -> runVibrate(appCtx, durationMs)
                        "ring" -> { runTorch(appCtx, durationMs, vibrate = true); runVibrate(appCtx, durationMs) }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "remoteCommands listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(vl)
        listener = vl
    }

    fun stop() {
        listener?.let {
            runCatching {
                FirebaseDatabase.getInstance().getReference("remoteCommands")
                    .child(FirebaseAuth.getInstance().currentUser?.uid ?: return).removeEventListener(it)
            }
        }
        listener = null
        activeJob?.cancel()
        setTorch(false)
        torchCallback?.let { runCatching { cameraManager?.unregisterTorchCallback(it) } }
        torchCallback = null
    }

    private fun runTorch(context: Context, durationMs: Long, vibrate: Boolean) {
        if (!hasFlash) { if (vibrate) runVibrate(context, durationMs); return }
        activeJob?.cancel()
        activeJob = scope.launch {
            val end = System.currentTimeMillis() + durationMs.coerceAtMost(30_000L)
            var on = true
            while (System.currentTimeMillis() < end) {
                setTorch(on)
                if (vibrate) pulseVibrate(context, 400L)
                delay(1000L)
                on = !on
            }
            setTorch(false)
        }
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        runCatching { cameraManager?.setTorchMode(id, on) }
            .onFailure { Log.w(TAG, "torch toggle failed: ${it.message}") }
    }

    private fun runVibrate(context: Context, durationMs: Long) {
        scope.launch {
            val end = System.currentTimeMillis() + durationMs.coerceAtMost(30_000L)
            while (System.currentTimeMillis() < end) {
                pulseVibrate(context, 600L)
                delay(1000L)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pulseVibrate(context: Context, ms: Long) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else v?.vibrate(ms)
            }
        }
    }
}
