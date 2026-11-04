package com.example.call

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.example.service.CallForegroundService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CallState(
    val callId: String = "",
    val remoteUid: String = "",
    val remoteName: String = "",
    val remoteImage: String = "",
    val direction: String = "",
    val status: String = "idle",
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val video: Boolean = false,
    val cameraOff: Boolean = false,
    val remoteCameraOff: Boolean = false,
    val screenSharing: Boolean = false,
    val remoteScreenSharing: Boolean = false,
    val connectedAt: Long = 0L,
    val error: String? = null,
    val frontCamera: Boolean = true,
    val torchOn: Boolean = false,
    val filter: CallFilter = CallFilter.NONE
)

/** 1:1 audio engine. RTDB transports signaling only; Cloudflare TURN transports media when P2P fails. */
object CallEngine {
    private const val DEFAULT_WORKER = "https://solitary-hill-dcdc.mr44253990.workers.dev"
    /** TURN credentials follow the admin's "calls" worker routing; primary/default otherwise. */
    private val GATEWAY: String get() = com.example.data.WorkerRouter.resolve("calls", com.example.data.WorkerRouter.primary.value.ifBlank { DEFAULT_WORKER }) + "/turn-credentials"
    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var peer: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var startedAsVideoCall = false
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var ringbackTone: ToneGenerator? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var callCpuWakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var callRef: DatabaseReference? = null
    private var callListener: ValueEventListener? = null
    private val candidateListeners = mutableListOf<Pair<DatabaseReference, ChildEventListener>>()

    @Synchronized
    fun initialize(context: Context): Boolean {
        if (factory != null) return true
        return try {
            appContext = context.applicationContext
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false).createInitializationOptions()
            )
            eglBase = EglBase.create()
            val eglContext = eglBase!!.eglBaseContext
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
                .createPeerConnectionFactory()
            factory != null
        } catch (error: Throwable) {
            Log.e("CALL_ENGINE", "WebRTC initialization failed", error)
            fail("Calling is unavailable on this device: ${error.message ?: error.javaClass.simpleName}")
            false
        }
    }

    fun startOutgoing(context: Context, remoteUid: String, remoteName: String, remoteImage: String, video: Boolean = false, onInviteReady: (String) -> Unit) {
        if (!initialize(context)) return
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return fail("Please sign in again")
        val callId = UUID.randomUUID().toString()
        startedAsVideoCall = video
        _state.value = CallState(callId, remoteUid, remoteName, remoteImage, "outgoing", "connecting", video = video)
        fetchIceServers { servers, error ->
            if (error != null) return@fetchIceServers fail(error)
            if (!createPeer(servers, callId, me, true)) return@fetchIceServers
            peer?.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                    val ref = FirebaseDatabase.getInstance().getReference("calls").child(callId)
                    callRef = ref
                    ref.setValue(mapOf(
                        "callId" to callId, "callerId" to me, "calleeId" to remoteUid,
                        "status" to "calling", "callType" to if (video) "video" else "audio", "offerType" to sdp.type.canonicalForm(),
                        "offerSdp" to sdp.description, "createdAt" to System.currentTimeMillis(),
                        "expiresAt" to System.currentTimeMillis() + 30_000
                    )).addOnSuccessListener {
                        _state.value = _state.value.copy(status = "calling")
                        listenCall(ref, isCaller = true)
                        listenRemoteCandidates(ref.child("calleeCandidates"))
                        onInviteReady(callId)
                        ref.child("status").onDisconnect().setValue("ended")
                    }
                }
                override fun onCreateFailure(error: String) = fail(error)
            }, MediaConstraints())
        }
    }

    fun acceptIncoming(context: Context, callId: String, callerUid: String, callerName: String, callerImage: String, video: Boolean = false) {
        if (!initialize(context)) return
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return fail("Please sign in again")
        _state.value = CallState(callId, callerUid, callerName, callerImage, "incoming", "connecting", video = video)
        val ref = FirebaseDatabase.getInstance().getReference("calls").child(callId)
        callRef = ref
        ref.get().addOnSuccessListener { snapshot ->
            val incomingStatus = snapshot.child("status").getValue(String::class.java).orEmpty()
            // The caller initially writes `calling`; the FCM service may promote it to
            // `ringing` on the callee device. Accept both to avoid an answer race.
            if (!snapshot.exists() || incomingStatus !in setOf("calling", "ringing")) {
                return@addOnSuccessListener fail("Call is no longer available")
            }
            val isVideo = video || snapshot.child("callType").getValue(String::class.java) == "video"
            startedAsVideoCall = isVideo
            _state.value = _state.value.copy(video = isVideo)
            fetchIceServers { servers, error ->
                if (error != null) return@fetchIceServers fail(error)
                if (!createPeer(servers, callId, me, false)) return@fetchIceServers
                val offer = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(snapshot.child("offerType").getValue(String::class.java) ?: "offer"),
                    snapshot.child("offerSdp").getValue(String::class.java) ?: return@fetchIceServers fail("Missing call offer")
                )
                peer?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        peer?.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answer: SessionDescription) {
                                peer?.setLocalDescription(SimpleSdpObserver(), answer)
                                ref.updateChildren(mapOf("answerType" to answer.type.canonicalForm(), "answerSdp" to answer.description, "status" to "connected", "answeredAt" to System.currentTimeMillis()))
                                listenCall(ref, isCaller = false)
                                listenRemoteCandidates(ref.child("callerCandidates"))
                                _state.value = _state.value.copy(status = "connected", connectedAt = System.currentTimeMillis())
                                ref.child("status").onDisconnect().setValue("ended")
                            }
                            override fun onCreateFailure(error: String) = fail(error)
                        }, MediaConstraints())
                    }
                    override fun onSetFailure(error: String) = fail(error)
                }, offer)
            }
        }.addOnFailureListener { fail(it.localizedMessage ?: "Could not load call") }
    }

    fun decline(callId: String) {
        FirebaseDatabase.getInstance().getReference("calls").child(callId).updateChildren(mapOf("status" to "declined", "endedAt" to System.currentTimeMillis()))
        cleanup("declined")
    }

    fun end() {
        callRef?.updateChildren(mapOf("status" to "ended", "endedAt" to System.currentTimeMillis()))
        cleanup("ended")
    }

    fun timeout() {
        callRef?.updateChildren(mapOf("status" to "missed", "endedAt" to System.currentTimeMillis()))
        cleanup("missed")
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        audioTrack?.setEnabled(!muted)
        _state.value = _state.value.copy(muted = muted)
    }

    private val filterProcessor = CallFilterProcessor()

    fun switchCamera() {
        if (_state.value.screenSharing) return
        runCatching {
            (videoCapturer as? CameraVideoCapturer)?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    mainHandler.post {
                        // Only the selfie camera is mirrored; the back camera shows the real world as-is.
                        localRenderer?.setMirror(isFrontCamera)
                        _state.value = _state.value.copy(frontCamera = isFrontCamera, torchOn = false)
                    }
                }
                override fun onCameraSwitchError(errorDescription: String?) { Log.w("CALL_ENGINE", "Camera switch failed: $errorDescription") }
            })
        }.onFailure { Log.w("CALL_ENGINE", "Camera switch failed", it) }
    }

    /** Flashlight for the back camera (best effort, depends on the phone). */
    fun toggleTorch(): Boolean {
        val st = _state.value
        if (st.screenSharing || st.cameraOff) return false
        if (st.frontCamera) { notice("Switch to the back camera to use the flashlight"); return false }
        val target = !st.torchOn
        val ok = CallTorch.set(videoCapturer, target)
        if (ok) _state.value = st.copy(torchOn = target) else notice("Flashlight isn't available on this phone during calls")
        return ok
    }

    fun setFilter(filter: CallFilter) {
        filterProcessor.filter = filter
        _state.value = _state.value.copy(filter = filter)
    }

    fun toggleCamera() {
        if (_state.value.screenSharing) return
        val off = !_state.value.cameraOff
        localVideoTrack?.setEnabled(!off)
        _state.value = _state.value.copy(cameraOff = off)
        callRef?.child(if (_state.value.direction == "outgoing") "callerCameraOff" else "calleeCameraOff")?.setValue(off)
    }

    /**
     * Starts screen sharing. Android 14+ requires the mediaProjection foreground service to be
     * running *before* the projection is created, and startForegroundService() is asynchronous,
     * so capture begins once the service reports it is in the foreground (max ~4 s).
     */
    fun startScreenShare(permissionData: Intent): Boolean {
        val context = appContext ?: return false
        if (videoSource == null || surfaceTextureHelper == null) return false
        val requestedAt = System.currentTimeMillis()
        runCatching { CallForegroundService.start(context, _state.value.callId, _state.value.remoteName, true, screenShare = true) }
            .onFailure { Log.e("CALL_ENGINE", "Could not start projection service", it); return false }
        fun attempt(triesLeft: Int) {
            val ready = Build.VERSION.SDK_INT < 34 || CallForegroundService.projectionForegroundAt >= requestedAt
            if (!ready && triesLeft > 0) { mainHandler.postDelayed({ attempt(triesLeft - 1) }, 100); return }
            if (!beginScreenCapture(context, permissionData)) {
                notice("Screen sharing could not start on this phone")
                CallForegroundService.start(context, _state.value.callId, _state.value.remoteName, _state.value.video, screenShare = false)
            }
        }
        mainHandler.postDelayed({ attempt(40) }, 120)
        return true
    }

    private fun beginScreenCapture(context: Context, permissionData: Intent): Boolean {
        val source = videoSource ?: return false
        return runCatching {
            runCatching { videoCapturer?.stopCapture() }
            videoCapturer?.dispose()
            videoCapturer = null
            val screenCapturer = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {
                override fun onStop() { mainHandler.post { stopScreenShare() } }
            })
            val metrics = context.resources.displayMetrics
            // Keep the encoder light: ~720p on the long edge-aware short side.
            val scale = (720f / metrics.widthPixels.coerceAtLeast(1)).coerceAtMost(1f)
            val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(360) / 2 * 2
            val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(640) / 2 * 2
            screenCapturer.initialize(surfaceTextureHelper, context, source.capturerObserver)
            screenCapturer.startCapture(width, height, 15)
            videoCapturer = screenCapturer
            localRenderer?.setMirror(false)
            localVideoTrack?.setEnabled(true)
            _state.value = _state.value.copy(video = true, screenSharing = true, cameraOff = false)
            callRef?.child(if (_state.value.direction == "outgoing") "callerCameraOff" else "calleeCameraOff")?.setValue(false)
            callRef?.child(if (_state.value.direction == "outgoing") "callerScreenSharing" else "calleeScreenSharing")?.setValue(true)
            true
        }.onFailure { Log.e("CALL_ENGINE", "Screen share failed", it) }.getOrDefault(false)
    }

    private fun notice(message: String) {
        val context = appContext ?: return
        mainHandler.post { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show() }
    }

    /** Wired/Bluetooth headsets win over the loudspeaker. */
    private fun headsetConnected(manager: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            )
        }
    }

    /** Video calls start on the loudspeaker (like Messenger/WhatsApp) unless a headset is in use. */
    private fun autoSpeakerForVideo() {
        val context = appContext ?: return
        if (!_state.value.video || _state.value.speaker) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (headsetConnected(manager)) return
        toggleSpeaker()
    }

    fun stopScreenShare(): Boolean {
        val context = appContext ?: return false
        val source = videoSource ?: return false
        if (!_state.value.screenSharing) return true
        return runCatching {
            runCatching { videoCapturer?.stopCapture() }
            videoCapturer?.dispose()
            videoCapturer = null
            if (startedAsVideoCall) {
                val camera = createCameraCapturer(context) ?: error("No camera available")
                camera.initialize(surfaceTextureHelper, context, source.capturerObserver)
                camera.startCapture(720, 1280, 24)
                videoCapturer = camera
                localVideoTrack?.setEnabled(true)
                _state.value = _state.value.copy(video = true, screenSharing = false, cameraOff = false)
            } else {
                localVideoTrack?.setEnabled(false)
                _state.value = _state.value.copy(video = false, screenSharing = false, cameraOff = true)
            }
            CallForegroundService.start(context, _state.value.callId, _state.value.remoteName, _state.value.video, screenShare = false)
            callRef?.child(if (_state.value.direction == "outgoing") "callerScreenSharing" else "calleeScreenSharing")?.setValue(false)
            true
        }.onFailure { Log.e("CALL_ENGINE", "Could not restore camera", it) }.getOrDefault(false)
    }

    fun toggleSpeaker() {
        val context = appContext ?: return
        val enabled = !_state.value.speaker
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) manager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let(manager::setCommunicationDevice)
            else manager.clearCommunicationDevice()
        } else @Suppress("DEPRECATION") run { manager.isSpeakerphoneOn = enabled }
        if (enabled) {
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val minimum = (max * .70f).toInt().coerceAtLeast(1)
            if (manager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) < minimum) manager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, minimum, 0)
        }
        _state.value = _state.value.copy(speaker = enabled)
        updateProximityLock()
    }

    private fun createPeer(servers: List<PeerConnection.IceServer>, callId: String, myUid: String, caller: Boolean): Boolean {
        return try {
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val side = if (caller) "callerCandidates" else "calleeCandidates"
                FirebaseDatabase.getInstance().getReference("calls").child(callId).child(side).push().setValue(mapOf("sdpMid" to candidate.sdpMid, "sdpMLineIndex" to candidate.sdpMLineIndex, "sdp" to candidate.sdp))
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> _state.value = _state.value.copy(status = "connected", connectedAt = _state.value.connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis(), error = null)
                    PeerConnection.IceConnectionState.FAILED -> fail("Call connection failed")
                    PeerConnection.IceConnectionState.DISCONNECTED -> _state.value = _state.value.copy(status = "reconnecting")
                    else -> Unit
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                (receiver?.track() as? VideoTrack)?.let { track -> remoteVideoTrack = track; remoteRenderer?.let(track::addSink) }
            }
        })
        if (peer == null) throw IllegalStateException("WebRTC peer connection could not be created")
        val source = factory?.createAudioSource(MediaConstraints())
            ?: throw IllegalStateException("WebRTC audio source could not be created")
        audioTrack = factory?.createAudioTrack("firechat_audio_$myUid", source)?.also { peer?.addTrack(it, listOf("firechat")) }
            ?: throw IllegalStateException("WebRTC audio track could not be created")
        // Always negotiate a video m-line so either participant can start screen sharing
        // later, including calls that began as audio-only.
        val context = appContext ?: throw IllegalStateException("Missing call context")
        surfaceTextureHelper = SurfaceTextureHelper.create("Convo ChatVideo", eglBase!!.eglBaseContext)
        videoSource = factory?.createVideoSource(false) ?: throw IllegalStateException("Could not create video source")
        filterProcessor.filter = CallFilter.NONE
        videoSource?.setVideoProcessor(filterProcessor)
        localVideoTrack = factory?.createVideoTrack("firechat_video_$myUid", videoSource)?.also { track ->
            track.setEnabled(false)
            peer?.addTrack(track, listOf("firechat"))
            localRenderer?.let(track::addSink)
        } ?: throw IllegalStateException("Could not create video track")
        if (_state.value.video) {
            videoCapturer = createCameraCapturer(context) ?: throw IllegalStateException("No usable camera found")
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
            videoCapturer?.startCapture(720, 1280, 24)
            localVideoTrack?.setEnabled(true)
        }
        true
        } catch (error: Throwable) {
            Log.e("CALL_ENGINE", "Peer creation failed", error)
            fail("Could not start secure audio: ${error.message ?: error.javaClass.simpleName}")
            false
        }
    }

    private fun createCameraCapturer(context: Context): VideoCapturer? {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(false)
        val names = enumerator.deviceNames
        names.firstOrNull { enumerator.isFrontFacing(it) }?.let { enumerator.createCapturer(it, null)?.let { capturer ->
            _state.value = _state.value.copy(frontCamera = true, torchOn = false); localRenderer?.setMirror(true); return capturer
        } }
        names.firstOrNull()?.let { name ->
            val front = enumerator.isFrontFacing(name)
            _state.value = _state.value.copy(frontCamera = front, torchOn = false); localRenderer?.setMirror(front)
            return enumerator.createCapturer(name, null)
        }
        return null
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        try { localRenderer = renderer; renderer.init(eglBase?.eglBaseContext, null); renderer.setMirror(_state.value.frontCamera && !_state.value.screenSharing); localVideoTrack?.addSink(renderer) } catch (e: Throwable) { Log.w("CALL_ENGINE", "Local renderer unavailable", e) }
    }
    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        try { remoteRenderer = renderer; renderer.init(eglBase?.eglBaseContext, null); renderer.setMirror(false); remoteVideoTrack?.addSink(renderer) } catch (e: Throwable) { Log.w("CALL_ENGINE", "Remote renderer unavailable", e) }
    }
    fun detachRenderer(renderer: SurfaceViewRenderer) {
        runCatching { localVideoTrack?.removeSink(renderer); remoteVideoTrack?.removeSink(renderer); if (localRenderer === renderer) localRenderer = null; if (remoteRenderer === renderer) remoteRenderer = null; renderer.release() }
    }

    private fun listenCall(ref: DatabaseReference, isCaller: Boolean) {
        callListener?.let { ref.removeEventListener(it) }
        callListener = ref.addValueEventListener(object : ValueEventListener {
            private var answerApplied = false
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java) ?: return
                val remoteCameraOff = snapshot.child(if (isCaller) "calleeCameraOff" else "callerCameraOff").getValue(Boolean::class.java) ?: false
                val remoteScreenSharing = snapshot.child(if (isCaller) "calleeScreenSharing" else "callerScreenSharing").getValue(Boolean::class.java) ?: false
                if (_state.value.remoteCameraOff != remoteCameraOff || _state.value.remoteScreenSharing != remoteScreenSharing) {
                    _state.value = _state.value.copy(remoteCameraOff = remoteCameraOff, remoteScreenSharing = remoteScreenSharing, video = _state.value.video || remoteScreenSharing)
                }
                if (isCaller && status == "connected" && !answerApplied) {
                    val answerSdp = snapshot.child("answerSdp").getValue(String::class.java)
                    if (!answerSdp.isNullOrBlank()) {
                        answerApplied = true
                        peer?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
                    }
                }
                val previous = _state.value.status
                if (status == "ringing" && isCaller) startRingback() else if (status != "ringing") stopRingback()
                if (status == "connected" && previous != "connected") vibrateConnected()
                _state.value = _state.value.copy(
                    status = status,
                    connectedAt = if (status == "connected") _state.value.connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis() else _state.value.connectedAt
                )
                if (status == "connected") {
                    activateCallRuntime()
                    if (previous != "connected") autoSpeakerForVideo()
                    updateProximityLock()
                }
                if (status in listOf("declined", "ended", "missed")) cleanup(status)
            }
            override fun onCancelled(error: DatabaseError) = fail(error.message)
        })
    }

    private fun listenRemoteCandidates(ref: DatabaseReference) {
        val listener = ref.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val mid = snapshot.child("sdpMid").getValue(String::class.java)
                val index = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                peer?.addIceCandidate(IceCandidate(mid, index, sdp))
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(error: DatabaseError) = fail(error.message)
        })
        candidateListeners += ref to listener
    }

    private fun fetchIceServers(callback: (List<PeerConnection.IceServer>, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return callback(emptyList(), "Please sign in again")
        user.getIdToken(false).addOnSuccessListener { result ->
            val token = result.token ?: return@addOnSuccessListener callback(emptyList(), "Could not authenticate TURN request")
            val request = Request.Builder().url(GATEWAY).header("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
            OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    mainHandler.post { callback(emptyList(), e.localizedMessage ?: "TURN unavailable") }
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            mainHandler.post { callback(emptyList(), "TURN gateway HTTP ${it.code}") }
                            return
                        }
                        val root = JSONObject(it.body?.string().orEmpty())
                        val list = mutableListOf<PeerConnection.IceServer>()
                        val array = root.optJSONArray("iceServers") ?: JSONArray()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i); val urlsJson = item.optJSONArray("urls")
                            val urls = if (urlsJson != null) (0 until urlsJson.length()).map { index -> urlsJson.getString(index) } else listOf(item.optString("urls"))
                            list += PeerConnection.IceServer.builder(urls).setUsername(item.optString("username")).setPassword(item.optString("credential")).createIceServer()
                        }
                        val resolved = list.ifEmpty { listOf(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()) }
                        mainHandler.post { callback(resolved, null) }
                    }
                }
            })
        }.addOnFailureListener { callback(emptyList(), it.localizedMessage ?: "TURN authentication failed") }
    }

    private fun activateCallRuntime() {
        val context = appContext ?: return
        runCatching { CallForegroundService.start(context, _state.value.callId, _state.value.remoteName, _state.value.video) }
        if (callCpuWakeLock?.isHeld != true) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            callCpuWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Convo Chat:ActiveCallCpu").apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        }
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audioManager?.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun startRingback() {
        if (ringbackTone != null) return
        runCatching {
            ringbackTone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }
    }

    private fun stopRingback() {
        runCatching { ringbackTone?.stopTone(); ringbackTone?.release() }
        ringbackTone = null
    }

    private fun vibrateConnected() {
        val context = appContext ?: return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(90)
    }

    private fun vibrateCallEnded() {
        val context = appContext ?: return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 160), -1))
        else @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 80, 60, 160), -1)
    }

    private fun updateProximityLock() {
        val context = appContext ?: return
        val shouldUse = _state.value.status == "connected" && !_state.value.video && !_state.value.speaker
        if (shouldUse && proximityWakeLock?.isHeld != true) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Convo Chat:CallProximity").apply { acquire() }
            }
        } else if (!shouldUse && proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release(); proximityWakeLock = null
        }
    }

    private fun cleanup(finalStatus: String) {
        val wasConnected = _state.value.connectedAt > 0L
        stopRingback()
        appContext?.let(CallForegroundService::stop)
        if (proximityWakeLock?.isHeld == true) runCatching { proximityWakeLock?.release() }
        proximityWakeLock = null
        if (callCpuWakeLock?.isHeld == true) runCatching { callCpuWakeLock?.release() }
        callCpuWakeLock = null
        @Suppress("DEPRECATION")
        runCatching { audioManager?.abandonAudioFocus(null); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager?.clearCommunicationDevice(); audioManager?.mode = AudioManager.MODE_NORMAL }
        audioManager = null
        candidateListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }; candidateListeners.clear()
        callListener?.let { listener -> callRef?.removeEventListener(listener) }; callListener = null
        runCatching { videoCapturer?.stopCapture() }; videoCapturer?.dispose(); videoCapturer = null
        surfaceTextureHelper?.dispose(); surfaceTextureHelper = null
        localVideoTrack?.dispose(); localVideoTrack = null; remoteVideoTrack = null
        videoSource?.dispose(); videoSource = null
        peer?.close(); peer?.dispose(); peer = null
        audioTrack?.dispose(); audioTrack = null
        if (wasConnected) vibrateCallEnded()
        startedAsVideoCall = false
        filterProcessor.filter = CallFilter.NONE
        lastPacketsLost = 0L; lastPacketsReceived = 0L
        _state.value = _state.value.copy(status = finalStatus, video = false, cameraOff = false, screenSharing = false, remoteScreenSharing = false, torchOn = false, filter = CallFilter.NONE)
    }

    /** Live reactions travel through the existing calls/{callId} node, so no extra RTDB rules are needed. */
    private var lastReactionAt = 0L
    fun sendReaction(token: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val now = System.currentTimeMillis()
        if (now - lastReactionAt < 450L) return false
        lastReactionAt = now
        val ref = callRef ?: return false
        ref.child("reactions").push().setValue(mapOf("from" to uid, "token" to token.take(40), "at" to ServerValue.TIMESTAMP))
        return true
    }

    fun reactionsRef(): DatabaseReference? = callRef?.child("reactions")

    private var lastPacketsLost = 0L
    private var lastPacketsReceived = 0L
    /** Samples WebRTC stats: round-trip time in ms and recent packet loss %. Values are null when unknown. */
    fun sampleQuality(onResult: (Int?, Double?) -> Unit) {
        val pc = peer ?: run { onResult(null, null); return }
        runCatching {
            pc.getStats(RTCStatsCollectorCallback { report ->
                var rttSeconds: Double? = null
                var lost = 0L
                var received = 0L
                report.statsMap.values.forEach { stat ->
                    val members = stat.members
                    when (stat.type) {
                        "candidate-pair" -> if (members["state"] == "succeeded" && members["nominated"] != false) {
                            (members["currentRoundTripTime"] as? Number)?.toDouble()?.let { rttSeconds = it }
                        }
                        "inbound-rtp" -> {
                            (members["packetsLost"] as? Number)?.toLong()?.let { lost += it }
                            (members["packetsReceived"] as? Number)?.toLong()?.let { received += it }
                        }
                    }
                }
                val deltaLost = (lost - lastPacketsLost).coerceAtLeast(0L)
                val deltaReceived = (received - lastPacketsReceived).coerceAtLeast(0L)
                lastPacketsLost = lost; lastPacketsReceived = received
                val loss = if (deltaLost + deltaReceived > 0L) deltaLost * 100.0 / (deltaLost + deltaReceived) else null
                val rttMs = rttSeconds?.let { (it * 1000).toInt() }
                mainHandler.post { onResult(rttMs, loss) }
            })
        }.onFailure { onResult(null, null) }
    }

    fun reportFailure(message: String) = fail(message)
    fun clearError() { _state.value = _state.value.copy(error = null, status = if (_state.value.status == "failed") "idle" else _state.value.status) }

    private fun fail(message: String) {
        Log.e("CALL_ENGINE", message)
        _state.value = _state.value.copy(status = "failed", error = message)
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}
