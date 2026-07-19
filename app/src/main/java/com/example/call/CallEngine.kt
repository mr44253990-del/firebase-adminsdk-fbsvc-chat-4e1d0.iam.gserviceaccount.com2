package com.example.call

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    val error: String? = null
)

/** 1:1 audio engine. RTDB transports signaling only; Cloudflare TURN transports media when P2P fails. */
object CallEngine {
    private const val GATEWAY = "https://solitary-hill-dcdc.mr44253990.workers.dev/turn-credentials"
    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
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
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            factory != null
        } catch (error: Throwable) {
            Log.e("CALL_ENGINE", "WebRTC initialization failed", error)
            fail("Calling is unavailable on this device: ${error.message ?: error.javaClass.simpleName}")
            false
        }
    }

    fun startOutgoing(context: Context, remoteUid: String, remoteName: String, remoteImage: String, onInviteReady: (String) -> Unit) {
        if (!initialize(context)) return
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return fail("Please sign in again")
        val callId = UUID.randomUUID().toString()
        _state.value = CallState(callId, remoteUid, remoteName, remoteImage, "outgoing", "connecting")
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
                        "status" to "ringing", "offerType" to sdp.type.canonicalForm(),
                        "offerSdp" to sdp.description, "createdAt" to System.currentTimeMillis(),
                        "expiresAt" to System.currentTimeMillis() + 30_000
                    )).addOnSuccessListener {
                        _state.value = _state.value.copy(status = "ringing")
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

    fun acceptIncoming(context: Context, callId: String, callerUid: String, callerName: String, callerImage: String) {
        if (!initialize(context)) return
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return fail("Please sign in again")
        _state.value = CallState(callId, callerUid, callerName, callerImage, "incoming", "connecting")
        val ref = FirebaseDatabase.getInstance().getReference("calls").child(callId)
        callRef = ref
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.child("status").getValue(String::class.java) != "ringing") return@addOnSuccessListener fail("Call is no longer available")
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
                                _state.value = _state.value.copy(status = "connected")
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

    fun toggleSpeaker() {
        val context = appContext ?: return
        val enabled = !_state.value.speaker
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        run { manager.mode = AudioManager.MODE_IN_COMMUNICATION; manager.isSpeakerphoneOn = enabled }
        _state.value = _state.value.copy(speaker = enabled)
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
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> _state.value = _state.value.copy(status = "connected", error = null)
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
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
        if (peer == null) throw IllegalStateException("WebRTC peer connection could not be created")
        val source = factory?.createAudioSource(MediaConstraints())
            ?: throw IllegalStateException("WebRTC audio source could not be created")
        audioTrack = factory?.createAudioTrack("firechat_audio_$myUid", source)?.also { peer?.addTrack(it, listOf("firechat")) }
            ?: throw IllegalStateException("WebRTC audio track could not be created")
        true
        } catch (error: Throwable) {
            Log.e("CALL_ENGINE", "Peer creation failed", error)
            fail("Could not start secure audio: ${error.message ?: error.javaClass.simpleName}")
            false
        }
    }

    private fun listenCall(ref: DatabaseReference, isCaller: Boolean) {
        callListener?.let { ref.removeEventListener(it) }
        callListener = ref.addValueEventListener(object : ValueEventListener {
            private var answerApplied = false
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java) ?: return
                if (isCaller && status == "connected" && !answerApplied) {
                    val answerSdp = snapshot.child("answerSdp").getValue(String::class.java)
                    if (!answerSdp.isNullOrBlank()) {
                        answerApplied = true
                        peer?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
                    }
                }
                _state.value = _state.value.copy(status = status)
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

    private fun cleanup(finalStatus: String) {
        candidateListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }; candidateListeners.clear()
        callListener?.let { listener -> callRef?.removeEventListener(listener) }; callListener = null
        peer?.close(); peer?.dispose(); peer = null
        audioTrack?.dispose(); audioTrack = null
        _state.value = _state.value.copy(status = finalStatus)
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
