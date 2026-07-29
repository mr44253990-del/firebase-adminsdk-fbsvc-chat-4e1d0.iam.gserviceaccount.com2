package com.example.call

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*

object EmergencySignalController {
    private var torchId: String? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    @Synchronized fun start(context: Context): Boolean = runCatching {
        val app = context.applicationContext
        val camera = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        torchId = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        torchId?.let { camera.setTorchMode(it, true) }
        tone = ToneGenerator(AudioManager.STREAM_ALARM, 78).also { it.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 15000) }
        vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 320, 180, 320, 180, 650)
        if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        true
    }.getOrDefault(false)

    @Synchronized fun stop(context: Context) {
        runCatching { torchId?.let { (context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(it, false) } }; torchId = null
        runCatching { tone?.stopTone(); tone?.release() }; tone = null
        runCatching { vibrator?.cancel() }; vibrator = null
    }
}
