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
    VIVID("Vivid", "🌈"), MONO("Mono", "🖤"), SEPIA("Vintage", "📜"), NIGHT("Night boost", "🌙"),
    // v5.0 looks, modelled on the popular Google Photos / Meet styles.
    PORTRAIT("Portrait light", "💡"), GOLDEN("Golden hour", "🌇"), ROSE("Bloom", "🌷"), FILM("Film", "🎞️"),
    DRAMA("Drama", "🎭"), NOIR("Noir", "🎬"), PASTEL("Pastel", "🍬"), OCEAN("Ocean", "🌊"),
    EMERALD("Emerald", "🌿"), SUNSET("Sunset", "🔥"), CYBER("Neon 2050", "🪩"), CRISP("Crisp HD", "🔎")
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
        CallFilter.PORTRAIT -> shift(-3, 6)
        CallFilter.GOLDEN -> shift(-18, 16)
        CallFilter.ROSE -> shift(4, 14)
        CallFilter.FILM -> saturate(8, -6, 8)
        CallFilter.DRAMA -> saturate(12, 0, 2)
        CallFilter.NOIR -> ({ _: Byte -> 128.toByte() }) to ({ _: Byte -> 128.toByte() })
        CallFilter.PASTEL -> saturate(6, 3, 5)
        CallFilter.OCEAN -> shift(20, -14)
        CallFilter.EMERALD -> shift(-10, -14)
        CallFilter.SUNSET -> saturate(12, -14, 22)
        CallFilter.CYBER -> saturate(16, 14, 10)
        CallFilter.CRISP -> saturate(12, 0, 0)
        else -> ({ b: Byte -> b }) to ({ b: Byte -> b })
    }

    /** Push the two chroma planes (U = blue-yellow, V = red-green axis). */
    private fun shift(u: Int, v: Int): Pair<(Byte) -> Byte, (Byte) -> Byte> =
        ({ b: Byte -> clamp((b.toInt() and 0xFF) + u) }) to ({ b: Byte -> clamp((b.toInt() and 0xFF) + v) })

    /** Scale saturation by [tenths]/10 around neutral, then shift. */
    private fun saturate(tenths: Int, u: Int, v: Int): Pair<(Byte) -> Byte, (Byte) -> Byte> =
        ({ b: Byte -> clamp(128 + (((b.toInt() and 0xFF) - 128) * tenths) / 10 + u) }) to ({ b: Byte -> clamp(128 + (((b.toInt() and 0xFF) - 128) * tenths) / 10 + v) })

    /** Smooth S-curve contrast (strength 0..1) with an optional lift for faded looks. */
    private fun curve(strength: Double, lift: Int = 0, gamma: Double = 1.0): ByteArray = ByteArray(256) { i ->
        val x = Math.pow(i / 255.0, gamma)
        val s = x * x * (3 - 2 * x)
        clamp(((x + (s - x) * strength) * (255 - lift) + lift).toInt())
    }

    private fun buildLut(f: CallFilter): ByteArray? = when (f) {
        CallFilter.PORTRAIT -> curve(.25, lift = 10, gamma = .82)
        CallFilter.GOLDEN -> curve(.2, lift = 8, gamma = .92)
        CallFilter.ROSE -> curve(.1, lift = 22, gamma = .9)
        CallFilter.FILM -> curve(.45, lift = 18)
        CallFilter.DRAMA -> curve(.9)
        CallFilter.NOIR -> curve(.8, gamma = 1.08)
        CallFilter.PASTEL -> curve(-.25, lift = 34, gamma = .88)
        CallFilter.OCEAN -> curve(.3)
        CallFilter.EMERALD -> curve(.3, lift = 6)
        CallFilter.SUNSET -> curve(.4, gamma = 1.05)
        CallFilter.CYBER -> curve(.7, gamma = .95)
        CallFilter.CRISP -> curve(.5, gamma = .96)
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
