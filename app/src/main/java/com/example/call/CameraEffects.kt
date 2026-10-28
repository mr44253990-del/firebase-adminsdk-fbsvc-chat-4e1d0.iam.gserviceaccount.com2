package com.example.call

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.util.Log
import android.view.Surface
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.nio.ByteBuffer

/** Live camera filters applied to the outgoing video (and therefore the local preview too). */
enum class CallFilter(val label: String, val emoji: String) {
    NONE("Normal", "✨"), BEAUTY("Soft glow", "🌸"), WARM("Warm", "🌅"), COOL("Cool", "❄️"),
    VIVID("Vivid", "🌈"), MONO("Mono", "🖤"), SEPIA("Vintage", "📜"), NIGHT("Night boost", "🌙")
}

/**
 * Pass-through when [filter] is NONE (zero cost). Otherwise converts the frame to I420 once and
 * edits the planes: chroma for colour looks, a luma lookup table for glow / night boost.
 */
class CallFilterProcessor : VideoProcessor {
    @Volatile var filter: CallFilter = CallFilter.NONE
        set(value) { field = value; lut = buildLut(value) }
    @Volatile private var lut: ByteArray? = null
    private var sink: VideoSink? = null

    override fun setSink(sink: VideoSink?) { this.sink = sink }
    override fun onCapturerStarted(success: Boolean) {}
    override fun onCapturerStopped() {}

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        val current = filter
        if (current == CallFilter.NONE) { out.onFrame(frame); return }
        val processed = runCatching { apply(frame, current) }.getOrNull()
        if (processed == null) { out.onFrame(frame); return }
        out.onFrame(processed)
        processed.release()
    }

    private fun apply(frame: VideoFrame, f: CallFilter): VideoFrame {
        val src = frame.buffer.toI420() ?: error("no i420")
        try {
            val w = src.width; val h = src.height
            val dst = JavaI420Buffer.allocate(w, h)
            val table = lut
            copyPlane(src.dataY, src.strideY, dst.dataY, dst.strideY, w, h) { if (table != null) table[it.toInt() and 0xFF] else it }
            val cw = (w + 1) / 2; val ch = (h + 1) / 2
            val (uMap, vMap) = chromaMaps(f)
            copyPlane(src.dataU, src.strideU, dst.dataU, dst.strideU, cw, ch, uMap)
            copyPlane(src.dataV, src.strideV, dst.dataV, dst.strideV, cw, ch, vMap)
            return VideoFrame(dst, frame.rotation, frame.timestampNs)
        } finally { src.release() }
    }

    private inline fun copyPlane(src: ByteBuffer, srcStride: Int, dst: ByteBuffer, dstStride: Int, width: Int, height: Int, map: (Byte) -> Byte) {
        val row = ByteArray(width)
        for (y in 0 until height) {
            src.position(y * srcStride); src.get(row, 0, width)
            for (x in 0 until width) row[x] = map(row[x])
            dst.position(y * dstStride); dst.put(row, 0, width)
        }
        src.rewind(); dst.rewind()
    }

    private fun clamp(v: Int): Byte = v.coerceIn(0, 255).toByte()

    private fun chromaMaps(f: CallFilter): Pair<(Byte) -> Byte, (Byte) -> Byte> = when (f) {
        CallFilter.MONO -> ({ _: Byte -> 128.toByte() }) to ({ _: Byte -> 128.toByte() })
        CallFilter.SEPIA -> ({ _: Byte -> 112.toByte() }) to ({ _: Byte -> 146.toByte() })
        CallFilter.WARM -> ({ b: Byte -> clamp((b.toInt() and 0xFF) - 12) }) to ({ b: Byte -> clamp((b.toInt() and 0xFF) + 14) })
        CallFilter.COOL -> ({ b: Byte -> clamp((b.toInt() and 0xFF) + 16) }) to ({ b: Byte -> clamp((b.toInt() and 0xFF) - 10) })
        CallFilter.VIVID -> ({ b: Byte -> clamp(128 + (((b.toInt() and 0xFF) - 128) * 14) / 10) }) to ({ b: Byte -> clamp(128 + (((b.toInt() and 0xFF) - 128) * 14) / 10) })
        CallFilter.BEAUTY -> ({ b: Byte -> b }) to ({ b: Byte -> clamp((b.toInt() and 0xFF) + 4) })
        else -> ({ b: Byte -> b }) to ({ b: Byte -> b })
    }

    private fun buildLut(f: CallFilter): ByteArray? = when (f) {
        // Lifts shadows and softens contrast for a flattering "glow".
        CallFilter.BEAUTY -> ByteArray(256) { i -> clamp((i * 0.86 + 30).toInt()) }
        // Gamma brighten for dark rooms.
        CallFilter.NIGHT -> ByteArray(256) { i -> clamp((Math.pow(i / 255.0, 0.62) * 255).toInt()) }
        CallFilter.VIVID -> ByteArray(256) { i -> clamp(((i - 128) * 1.08 + 128).toInt()) }
        else -> null
    }
}

/**
 * Best-effort torch for the WebRTC Camera2 capturer. WebRTC does not expose its capture
 * request, so this re-issues the repeating request of the live Camera2Session with
 * FLASH_MODE_TORCH. Returns false when the phone/session doesn't allow it.
 */
object CallTorch {
    fun set(capturer: Any?, on: Boolean): Boolean = runCatching {
        val session = findField(capturer, "currentSession") ?: return false
        if (session.javaClass.simpleName != "Camera2Session") return false
        val captureSession = findField(session, "captureSession") as? CameraCaptureSession ?: return false
        val device = findField(session, "cameraDevice") as? CameraDevice ?: return false
        val surface = findField(session, "surface") as? Surface ?: return false
        val handler = findField(session, "cameraThreadHandler") as? Handler
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        builder.addTarget(surface)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        builder.set(CaptureRequest.FLASH_MODE, if (on) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        val run = Runnable { runCatching { captureSession.setRepeatingRequest(builder.build(), null, handler) }.onFailure { Log.w("CALL_TORCH", "torch failed", it) } }
        if (handler != null) handler.post(run) else run.run()
        true
    }.onFailure { Log.w("CALL_TORCH", "Torch unavailable", it) }.getOrDefault(false)

    private fun findField(target: Any?, name: String): Any? {
        var cls: Class<*>? = target?.javaClass ?: return null
        while (cls != null) {
            runCatching { return cls!!.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            cls = cls.superclass
        }
        return null
    }
}
