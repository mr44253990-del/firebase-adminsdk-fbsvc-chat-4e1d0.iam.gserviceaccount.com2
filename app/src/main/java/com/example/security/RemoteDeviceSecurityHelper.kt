package com.example.security

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Remote device security helper: allows remotely pinging a misplaced or lost phone
 * by pulsing the camera flashlight (1s interval) and buzzing the vibration motor.
 */
object RemoteDeviceSecurityHelper {

    @Volatile
    private var isRunning = false

    fun triggerPingFlashlightAndVibrate(context: Context, durationSec: Int = 10) {
        if (isRunning) return
        isRunning = true

        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "Remote Device Finder: Flashlight & Vibration active", Toast.LENGTH_LONG).show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = runCatching {
                cameraManager?.cameraIdList?.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }.getOrNull()

            val cycles = (durationSec / 2).coerceAtLeast(3)
            try {
                for (i in 0 until cycles) {
                    // Turn torch ON & vibrate 1 sec
                    runCatching {
                        if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            cameraManager?.setTorchMode(cameraId, true)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(1000L)
                        }
                    }
                    delay(1000L)

                    // Turn torch OFF for 1 sec
                    runCatching {
                        if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            cameraManager?.setTorchMode(cameraId, false)
                        }
                    }
                    delay(1000L)
                }
            } finally {
                // Ensure torch is cleanly turned off
                runCatching {
                    if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cameraManager?.setTorchMode(cameraId, false)
                    }
                    vibrator?.cancel()
                }
                isRunning = false
            }
        }
    }
}
