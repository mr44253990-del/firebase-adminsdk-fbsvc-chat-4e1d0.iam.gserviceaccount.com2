package com.example.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
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
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Small-group WebRTC mesh engine. It deliberately uses the existing RTDB and TURN
 * gateway, so no new client secret or Worker binding is required. Mesh is capped
 * at four participants to keep mobile CPU/bandwidth predictable.
 */
data class GroupCallParticipant(
    val uid: String,
    val name: String = "Convo Chat user",
    val image: String = "",
    val video: Boolean = true,
    val muted: Boolean = false,
    val connected: Boolean = false,
    val hostMuted: Boolean = false,
    val quality: String = "unknown",
    val iceState: String = "new"
)

data class GroupCallState(
    val roomId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val status: String = "idle",
    val participants: List<GroupCallParticipant> = emptyList(),
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val video: Boolean = false,
    val cameraOff: Boolean = false,
    val screenSharing: Boolean = false,
    val connectedAt: Long = 0L,
    val hostId: String = "",
    val captionsEnabled: Boolean = false,
    val recording: Boolean = false,
    val error: String? = null
)

object GroupCallEngine {
    private const val TAG = "GROUP_CALL"
    private const val MAX_PARTICIPANTS = 4
    private const val GATEWAY = "https://solitary-hill-dcdc.mr44253990.workers.dev/turn-credentials"

    private val _state = MutableStateFlow(GroupCallState())
    val state: StateFlow<GroupCallState> = _state
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class PeerSlot(
        val uid: String,
        val peer: PeerConnection,
        var remoteVideo: VideoTrack? = null,
        var connected: Boolean = false
    )

    private var context: Context? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var localAudio: AudioTrack? = null
    private var localVideo: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private val remoteRenderers = ConcurrentHashMap<String, SurfaceViewRenderer>()
    // Compose may create a SurfaceViewRenderer before EGL is ready. Track successful
    // init calls by renderer identity so a failed bind can be retried safely.
    private val initializedRenderers = Collections.newSetFromMap(IdentityHashMap<SurfaceViewRenderer, Boolean>())
    // A remote track may arrive before the PeerSlot is published or before Compose creates its renderer.
    // Keep it briefly so the host never loses the first video track and renders a permanent black tile.
    private val pendingRemoteTracks = ConcurrentHashMap<String, VideoTrack>()
    private val peers = ConcurrentHashMap<String, PeerSlot>()
    // ICE candidates can arrive before the corresponding remote SDP on mobile networks.
    // Queue them per peer and flush only after setRemoteDescription succeeds.
    private val pendingCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private var roomRef: DatabaseReference? = null
    private var participantsListener: ChildEventListener? = null
    private var signalListener: ChildEventListener? = null
    private var participantRefs = mutableMapOf<String, DatabaseReference>()
    private var myUid: String = ""
    private var activeMemberIds: List<String> = emptyList()
    private var isClosing = false
    private var hostId: String = ""
    private var roomValueListener: ValueEventListener? = null

    @Synchronized
    private fun initialize(app: Context): Boolean {
        if (factory != null) return true
        return runCatching {
            context = app.applicationContext
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(app.applicationContext)
                    .setEnableInternalTracer(false).createInitializationOptions()
            )
            eglBase = EglBase.create()
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
                .createPeerConnectionFactory()
            factory != null
        }.onFailure { Log.e(TAG, "WebRTC initialization failed", it) }.getOrDefault(false)
    }

    fun start(
        app: Context,
        groupId: String,
        groupName: String,
        memberIds: List<String>,
        video: Boolean,
        onReady: (Boolean) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { onReady(false); return }
        val selected = memberIds.distinct().toMutableList().apply {
            if (!contains(user.uid)) add(0, user.uid)
        }.take(MAX_PARTICIPANTS)
        val roomId = UUID.randomUUID().toString()
        joinInternal(app, roomId, groupId, groupName, selected, video, createRoom = true, onReady)
    }

    fun join(
        app: Context,
        roomId: String,
        groupId: String,
        groupName: String,
        memberIds: List<String>,
        video: Boolean,
        onReady: (Boolean) -> Unit
    ) {
        val selected = memberIds.distinct().take(MAX_PARTICIPANTS)
        joinInternal(app, roomId, groupId, groupName, selected, video, createRoom = false, onReady)
    }

    private fun joinInternal(
        app: Context,
        roomId: String,
        groupId: String,
        groupName: String,
        memberIds: List<String>,
        video: Boolean,
        createRoom: Boolean,
        onReady: (Boolean) -> Unit
    ) {
        if (!initialize(app)) { onReady(false); return }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { onReady(false); return }
        if (_state.value.status !in listOf("idle", "ended", "failed")) { onReady(true); return }
        myUid = user.uid
        activeMemberIds = memberIds
        isClosing = false
        val ref = FirebaseDatabase.getInstance().getReference("groupCalls").child(roomId)
        roomRef = ref
        hostId = if (createRoom) myUid else ""
        _state.value = GroupCallState(roomId, groupId, groupName, "connecting", video = video, hostId = hostId)
        fetchIceServers { servers, error ->
            if (error != null) { fail(error); onReady(false); return@fetchIceServers }
            if (!createLocalMedia(video)) { fail("Could not start microphone/camera"); onReady(false); return@fetchIceServers }
            val me = mapOf(
                "uid" to myUid, "name" to (user.displayName ?: "Convo Chat user"),
                "image" to (user.photoUrl?.toString() ?: ""), "video" to video,
                "muted" to false, "joinedAt" to System.currentTimeMillis()
            )
            val roomData = mapOf(
                "roomId" to roomId, "groupId" to groupId, "groupName" to groupName,
                "hostId" to myUid, "status" to "active", "video" to video,
                "memberIds" to memberIds.associateWith { true },
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to System.currentTimeMillis() + 60 * 60 * 1000L
            )
            val write = if (createRoom) ref.setValue(roomData) else ref.get()
            write.addOnSuccessListener { result ->
                if (!createRoom) {
                    hostId = (result as? DataSnapshot)?.child("hostId")?.getValue(String::class.java).orEmpty()
                    _state.value = _state.value.copy(hostId = hostId)
                }
                ref.child("participants").child(myUid).setValue(me).addOnSuccessListener {
                    if (!createRoom && memberIds.isNotEmpty()) {
                        ref.child("participants").get().addOnSuccessListener { snapshot ->
                            snapshot.children.forEach { participant -> participantFrom(participant)?.let(::upsertParticipant) }
                            watchRoom(ref, servers)
                            onReady(true)
                        }
                    } else {
                        watchRoom(ref, servers)
                        onReady(true)
                    }
                    ref.child("status").onDisconnect().setValue("ended")
                    ref.child("participants").child(myUid).onDisconnect().removeValue()
                    ref.child("lastActiveAt").setValue(ServerValue.TIMESTAMP)
                }.addOnFailureListener { fail(it.localizedMessage ?: "Could not join group call"); onReady(false) }
            }.addOnFailureListener { fail(it.localizedMessage ?: "Could not open group call"); onReady(false) }
        }
    }

    private fun watchRoom(ref: DatabaseReference, servers: List<PeerConnection.IceServer>) {
        roomValueListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteHostId = snapshot.child("hostId").getValue(String::class.java).orEmpty()
                if (remoteHostId.isNotBlank()) {
                    hostId = remoteHostId
                    _state.value = _state.value.copy(hostId = remoteHostId)
                }
                val meeting = snapshot.child("meetingState")
                _state.value = _state.value.copy(
                    captionsEnabled = meeting.child("captionsEnabled").getValue(Boolean::class.java) ?: false,
                    recording = meeting.child("recording").getValue(Boolean::class.java) ?: false
                )
            }
            override fun onCancelled(error: DatabaseError) { Log.w(TAG, "Room state listener cancelled: ${error.message}") }
        })
        participantsListener = ref.child("participants").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.child("removed").getValue(Boolean::class.java) == true) {
                    handleHostRemoval(snapshot.key)
                    return
                }
                participantFrom(snapshot)?.let { participant ->
                    applyParticipantState(participant)
                    if (participant.uid != myUid) {
                        val offerer = myUid < participant.uid
                        ensurePeer(participant.uid, servers, offerer)
                    }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.child("removed").getValue(Boolean::class.java) == true) {
                    handleHostRemoval(snapshot.key)
                    return
                }
                participantFrom(snapshot)?.let { participant ->
                    applyParticipantState(participant)
                    if (participant.uid != myUid) ensurePeer(participant.uid, servers, myUid < participant.uid)
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val uid = snapshot.key ?: return
                removePeer(uid)
                _state.value = _state.value.copy(participants = _state.value.participants.filterNot { it.uid == uid })
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) { fail(error.message) }
        })
        signalListener = ref.child("signals").child(myUid).addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleSignal(snapshot, servers)
                snapshot.ref.removeValue()
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) { fail(error.message) }
        })
        _state.value = _state.value.copy(status = "connected", connectedAt = System.currentTimeMillis())
        activateRuntime()
    }

    private fun participantFrom(snapshot: DataSnapshot): GroupCallParticipant? {
        val uid = snapshot.key ?: snapshot.child("uid").getValue(String::class.java) ?: return null
        return GroupCallParticipant(
            uid = uid,
            name = snapshot.child("name").getValue(String::class.java) ?: "Convo Chat user",
            image = snapshot.child("image").getValue(String::class.java).orEmpty(),
            video = snapshot.child("video").getValue(Boolean::class.java) ?: true,
            muted = snapshot.child("muted").getValue(Boolean::class.java) ?: false,
            connected = uid == myUid || peers[uid]?.connected == true,
            hostMuted = snapshot.child("hostMuted").getValue(Boolean::class.java) ?: false,
            quality = if (uid == myUid) "good" else "unknown",
            iceState = if (uid == myUid) "local" else "new"
        )
    }

    private fun applyParticipantState(participant: GroupCallParticipant) {
        if (participant.uid == myUid) {
            val effectiveMuted = participant.muted || participant.hostMuted
            localAudio?.setEnabled(!effectiveMuted)
            _state.value = _state.value.copy(muted = effectiveMuted)
        }
        upsertParticipant(participant)
    }

    private fun handleHostRemoval(uid: String?) {
        if (uid.isNullOrBlank()) return
        if (uid == myUid) {
            cleanup("removed")
        } else {
            removePeer(uid)
            _state.value = _state.value.copy(participants = _state.value.participants.filterNot { it.uid == uid })
        }
    }

    private fun upsertParticipant(participant: GroupCallParticipant) {
        val old = _state.value.participants
        val next = (old.filterNot { it.uid == participant.uid } + participant)
            .sortedWith(compareByDescending<GroupCallParticipant> { it.uid == myUid }.thenBy { it.name })
        _state.value = _state.value.copy(participants = next)
    }

    private fun createLocalMedia(video: Boolean): Boolean = runCatching {
        val f = factory ?: error("WebRTC factory unavailable")
        val audioSource = f.createAudioSource(MediaConstraints())
        localAudio = f.createAudioTrack("group_audio_$myUid", audioSource)
        if (video) {
            val app = context ?: error("Missing context")
            videoCapturer = createCameraCapturer(app) ?: error("No camera available")
            surfaceTextureHelper = SurfaceTextureHelper.create("Convo Group Camera", eglBase!!.eglBaseContext)
            videoSource = f.createVideoSource(false)
            // Follow the proven 1:1 call order: start the capturer before creating and
            // publishing the track. Several Camera2 devices do not deliver frames when
            // the track is created/sent before capture has been initialized.
            videoCapturer!!.initialize(surfaceTextureHelper, app, videoSource!!.capturerObserver)
            videoCapturer!!.startCapture(720, 1280, 24)
            localVideo = f.createVideoTrack("group_video_$myUid", videoSource)
            localVideo?.setEnabled(true)
            localVideo?.let { track -> localRenderer?.let(track::addSink) }
        }
        true
    }.onFailure { Log.e(TAG, "Local media failed", it) }.getOrDefault(false)

    private fun ensurePeer(remoteUid: String, servers: List<PeerConnection.IceServer>, offerer: Boolean) {
        if (remoteUid == myUid || peers.containsKey(remoteUid)) return
        val f = factory ?: return
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                sendSignal(remoteUid, mapOf("kind" to "candidate", "from" to myUid, "sdpMid" to candidate.sdpMid, "sdpMLineIndex" to candidate.sdpMLineIndex, "sdp" to candidate.sdp))
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                val connected = state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED
                peers[remoteUid]?.connected = connected
                updateParticipantConnection(remoteUid, connected, qualityForIceState(state), state.name.lowercase())
                when (state) {
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CHECKING -> {
                        if (!isClosing) _state.value = _state.value.copy(status = "reconnecting")
                    }
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        if (!isClosing) _state.value = _state.value.copy(status = "connected", error = null)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        removePeer(remoteUid)
                        if (!isClosing) _state.value = _state.value.copy(status = "reconnecting", error = "Connection to ${remoteUid.take(8)} was interrupted")
                    }
                    else -> Unit
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) {
                // Older WebRTC builds/devices may deliver the remote video through
                // onAddStream instead of Unified Plan's onAddTrack. Support both so
                // the caller never gets a black remote tile when the peer is connected.
                stream?.videoTracks?.firstOrNull()?.let { attachRemoteVideo(remoteUid, it) }
            }
            override fun onRemoveStream(p0: MediaStream?) = Unit
            override fun onDataChannel(p0: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                (receiver?.track() as? VideoTrack)?.let { attachRemoteVideo(remoteUid, it) }
                streams?.firstOrNull()?.videoTracks?.firstOrNull()?.let { attachRemoteVideo(remoteUid, it) }
            }
        }) ?: return
        localAudio?.let { pc.addTrack(it, listOf("group")) }
        localVideo?.let { track ->
            track.setEnabled(true)
            pc.addTrack(track, listOf("group"))
        }
        peers[remoteUid] = PeerSlot(remoteUid, pc)
        pendingRemoteTracks.remove(remoteUid)?.let { track -> attachRemoteVideo(remoteUid, track) }
        if (offerer) pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                sendSignal(remoteUid, mapOf("kind" to "offer", "from" to myUid, "sdp" to sdp.description))
            }
            override fun onCreateFailure(error: String) { Log.w(TAG, "Offer failed for $remoteUid: $error") }
        }, MediaConstraints())
    }

    private fun handleSignal(snapshot: DataSnapshot, servers: List<PeerConnection.IceServer>) {
        val from = snapshot.child("from").getValue(String::class.java) ?: return
        val kind = snapshot.child("kind").getValue(String::class.java) ?: return
        ensurePeer(from, servers, offerer = false)
        val slot = peers[from] ?: return
        when (kind) {
            "offer" -> {
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                slot.peer.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        flushPendingCandidates(from, slot.peer)
                        slot.peer.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answer: SessionDescription) {
                                slot.peer.setLocalDescription(SimpleSdpObserver(), answer)
                                sendSignal(from, mapOf("kind" to "answer", "from" to myUid, "sdp" to answer.description))
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> snapshot.child("sdp").getValue(String::class.java)?.let {
                slot.peer.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        flushPendingCandidates(from, slot.peer)
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, it))
            }
            "candidate" -> {
                val mid = snapshot.child("sdpMid").getValue(String::class.java)
                val index = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                val candidate = IceCandidate(mid, index, sdp)
                if (slot.peer.remoteDescription == null) {
                    pendingCandidates.getOrPut(from) { mutableListOf() }.add(candidate)
                } else {
                    slot.peer.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun attachRemoteVideo(uid: String, track: VideoTrack) {
        mainHandler.post {
            val slot = peers[uid]
            if (slot == null) {
                pendingRemoteTracks[uid] = track
                return@post
            }
            pendingRemoteTracks.remove(uid)
            if (slot.remoteVideo !== track) {
                slot.remoteVideo?.let { old -> remoteRenderers[uid]?.let { old.removeSink(it) } }
                slot.remoteVideo = track
            }
            remoteRenderers[uid]?.let { renderer ->
                runCatching { track.setEnabled(true); track.addSink(renderer) }
                    .onFailure { Log.w(TAG, "Remote renderer bind failed for $uid", it) }
            }
        }
    }

    private fun sendSignal(targetUid: String, values: Map<String, Any?>) {
        roomRef?.child("signals")?.child(targetUid)?.push()?.setValue(values)
    }

    private fun flushPendingCandidates(uid: String, peer: PeerConnection) {
        val queued = pendingCandidates.remove(uid) ?: return
        queued.forEach { candidate ->
            runCatching { peer.addIceCandidate(candidate) }
                .onFailure { Log.w(TAG, "Queued ICE candidate failed for $uid", it) }
        }
    }

    private fun updateParticipantConnection(uid: String, connected: Boolean, quality: String = "unknown", iceState: String = "new") {
        val old = _state.value.participants.firstOrNull { it.uid == uid } ?: return
        upsertParticipant(old.copy(connected = connected, quality = quality, iceState = iceState))
    }

    private fun qualityForIceState(state: PeerConnection.IceConnectionState): String = when (state) {
        PeerConnection.IceConnectionState.CONNECTED,
        PeerConnection.IceConnectionState.COMPLETED -> "good"
        PeerConnection.IceConnectionState.CHECKING,
        PeerConnection.IceConnectionState.NEW -> "fair"
        PeerConnection.IceConnectionState.DISCONNECTED -> "poor"
        PeerConnection.IceConnectionState.FAILED,
        PeerConnection.IceConnectionState.CLOSED -> "offline"
        else -> "unknown"
    }

    fun isHost(): Boolean = hostId.isNotBlank() && hostId == myUid

    fun toggleCaptions() {
        val enabled = !_state.value.captionsEnabled
        roomRef?.child("meetingState")?.child("captionsEnabled")?.setValue(enabled)
        _state.value = _state.value.copy(captionsEnabled = enabled)
    }

    fun toggleRecordingHook() {
        if (!isHost()) return
        val enabled = !_state.value.recording
        roomRef?.child("meetingState")?.child("recording")?.setValue(enabled)
        _state.value = _state.value.copy(recording = enabled)
    }

    fun hostMuteParticipant(uid: String, muted: Boolean) {
        if (!isHost() || uid == myUid) return
        roomRef?.child("participants")?.child(uid)?.child("hostMuted")?.setValue(muted)
    }

    fun removeParticipant(uid: String) {
        if (!isHost() || uid == myUid) return
        roomRef?.child("participants")?.child(uid)?.child("removed")?.setValue(true)
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        localAudio?.setEnabled(!muted)
        roomRef?.child("participants")?.child(myUid)?.child("muted")?.setValue(muted)
        _state.value = _state.value.copy(muted = muted)
    }

    fun toggleCamera() {
        if (_state.value.screenSharing) return
        val off = !_state.value.cameraOff
        localVideo?.setEnabled(!off)
        roomRef?.child("participants")?.child(myUid)?.child("video")?.setValue(!off)
        _state.value = _state.value.copy(cameraOff = off)
    }

    fun switchCamera() {
        runCatching { (videoCapturer as? CameraVideoCapturer)?.switchCamera(null) }
    }

    /** Recover a camera capture after a transient device/background renderer failure. */
    fun recoverLocalVideo(): Boolean {
        val app = context ?: return false
        val source = videoSource ?: return false
        if (!_state.value.video || _state.value.screenSharing) return false
        return runCatching {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            val camera = createCameraCapturer(app) ?: error("No camera available")
            camera.initialize(surfaceTextureHelper, app, source.capturerObserver)
            camera.startCapture(720, 1280, 24)
            videoCapturer = camera
            localVideo?.setEnabled(true)
            localVideo?.let(::replacePublishedVideoTrack)
            roomRef?.child("participants")?.child(myUid)?.child("video")?.setValue(true)
            _state.value = _state.value.copy(cameraOff = false, error = null)
            CallForegroundService.start(
                app,
                "group:${_state.value.roomId}",
                _state.value.groupName,
                _state.value.video,
                groupId = _state.value.groupId,
                roomId = _state.value.roomId,
                groupName = _state.value.groupName,
                memberIds = activeMemberIds
            )
            localRenderer?.let { renderer -> localVideo?.addSink(renderer) }
            true
        }.onFailure { Log.e(TAG, "Local video recovery failed", it) }.getOrDefault(false)
    }

    fun toggleSpeaker() {
        val app = context ?: return
        val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val enabled = !_state.value.speaker
        @Suppress("DEPRECATION") manager.isSpeakerphoneOn = enabled
        _state.value = _state.value.copy(speaker = enabled)
    }

    fun startScreenShare(permissionData: Intent): Boolean {
        val app = context ?: return false
        val source = videoSource ?: return false
        return runCatching {
            videoCapturer?.stopCapture(); videoCapturer?.dispose()
            CallForegroundService.start(
                app,
                "group:${_state.value.roomId}",
                _state.value.groupName,
                _state.value.video,
                screenShare = true,
                groupId = _state.value.groupId,
                roomId = _state.value.roomId,
                groupName = _state.value.groupName,
                memberIds = activeMemberIds
            )
            val capturer = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {
                override fun onStop() { mainHandler.post { stopScreenShare() } }
            })
            val metrics = app.resources.displayMetrics
            capturer.initialize(surfaceTextureHelper, app, source.capturerObserver)
            capturer.startCapture(metrics.widthPixels.coerceAtLeast(720), metrics.heightPixels.coerceAtLeast(1280), 15)
            videoCapturer = capturer
            localVideo?.setEnabled(true)
            roomRef?.child("participants")?.child(myUid)?.child("video")?.setValue(true)
            _state.value = _state.value.copy(screenSharing = true, cameraOff = false)
            true
        }.onFailure { Log.e(TAG, "Screen share failed", it) }.getOrDefault(false)
    }

    fun stopScreenShare(): Boolean {
        val app = context ?: return false
        val source = videoSource ?: return false
        if (!_state.value.screenSharing) return true
        return runCatching {
            videoCapturer?.stopCapture(); videoCapturer?.dispose()
            val camera = createCameraCapturer(app) ?: error("No camera available")
            camera.initialize(surfaceTextureHelper, app, source.capturerObserver)
            camera.startCapture(720, 1280, 24)
            videoCapturer = camera
            localVideo?.setEnabled(true)
            localVideo?.let(::replacePublishedVideoTrack)
            roomRef?.child("participants")?.child(myUid)?.child("video")?.setValue(true)
            _state.value = _state.value.copy(screenSharing = false, cameraOff = false)
            CallForegroundService.start(
                app,
                "group:${_state.value.roomId}",
                _state.value.groupName,
                _state.value.video,
                screenShare = false,
                groupId = _state.value.groupId,
                roomId = _state.value.roomId,
                groupName = _state.value.groupName,
                memberIds = activeMemberIds
            )
            localRenderer?.let { renderer -> localVideo?.addSink(renderer) }
            true
        }.onFailure { Log.e(TAG, "Camera restore failed", it) }.getOrDefault(false)
    }

    private fun replacePublishedVideoTrack(track: VideoTrack) {
        track.setEnabled(true)
        peers.values.forEach { slot ->
            val videoSender = slot.peer.senders.firstOrNull { sender -> sender.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND }
            runCatching {
                if (videoSender != null) videoSender.setTrack(track, false)
                else slot.peer.addTrack(track, listOf("group"))
            }.onFailure { Log.w(TAG, "Video sender replacement failed for ${slot.uid}", it) }
        }
        mainHandler.post { localRenderer?.let { track.addSink(it) } }
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        mainHandler.post {
            if (localRenderer !== renderer) {
                localRenderer?.let { old ->
                    runCatching { localVideo?.removeSink(old) }
                    initializedRenderers.remove(old)
                }
                localRenderer = renderer
            }
            val eglContext = eglBase?.eglBaseContext
            if (eglContext == null) {
                // Keep the reference; the next retry binds the track after EGL is ready.
                mainHandler.postDelayed({
                    if (localRenderer === renderer && !isClosing) attachLocalRenderer(renderer)
                }, 100L)
                return@post
            }
            runCatching {
                if (initializedRenderers.add(renderer)) {
                    renderer.init(eglContext, null)
                    renderer.setEnableHardwareScaler(true)
                    renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    renderer.setMirror(true)
                }
                localVideo?.let { track ->
                    track.setEnabled(true)
                    track.removeSink(renderer)
                    track.addSink(renderer)
                }
            }.onFailure {
                initializedRenderers.remove(renderer)
                Log.w(TAG, "Local renderer bind failed", it)
                if (localRenderer === renderer && !isClosing) {
                    mainHandler.postDelayed({ attachLocalRenderer(renderer) }, 250L)
                }
            }
        }
    }

    fun refreshLocalRenderer() {
        mainHandler.post {
            val renderer = localRenderer ?: return@post
            val track = localVideo ?: return@post
            runCatching {
                track.setEnabled(true)
                track.removeSink(renderer)
                track.addSink(renderer)
            }.onFailure { Log.w(TAG, "Local renderer refresh failed", it) }
        }
    }

    fun attachRemoteRenderer(uid: String, renderer: SurfaceViewRenderer) {
        mainHandler.post {
            if (remoteRenderers[uid] !== renderer) {
                remoteRenderers[uid]?.let { old ->
                    runCatching { peers[uid]?.remoteVideo?.removeSink(old) }
                    initializedRenderers.remove(old)
                }
                remoteRenderers[uid] = renderer
            }
            val eglContext = eglBase?.eglBaseContext
            if (eglContext == null) {
                mainHandler.postDelayed({
                    if (remoteRenderers[uid] === renderer && !isClosing) attachRemoteRenderer(uid, renderer)
                }, 100L)
                return@post
            }
            runCatching {
                if (initializedRenderers.add(renderer)) {
                    renderer.init(eglContext, null)
                    renderer.setEnableHardwareScaler(true)
                    renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    renderer.setMirror(false)
                }
                val track = peers[uid]?.remoteVideo ?: pendingRemoteTracks.remove(uid)
                track?.let { videoTrack ->
                    peers[uid]?.remoteVideo = videoTrack
                    videoTrack.setEnabled(true)
                    videoTrack.addSink(renderer)
                }
            }.onFailure {
                initializedRenderers.remove(renderer)
                Log.w(TAG, "Remote renderer bind failed for $uid", it)
                if (remoteRenderers[uid] === renderer && !isClosing) {
                    mainHandler.postDelayed({ attachRemoteRenderer(uid, renderer) }, 250L)
                }
            }
        }
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        mainHandler.post {
            runCatching { localVideo?.removeSink(renderer) }
            if (localRenderer === renderer) localRenderer = null
            initializedRenderers.remove(renderer)
            runCatching { renderer.release() }
        }
    }

    fun detachRemoteRenderer(uid: String, renderer: SurfaceViewRenderer) {
        mainHandler.post {
            runCatching { peers[uid]?.remoteVideo?.removeSink(renderer) }
            if (remoteRenderers[uid] === renderer) remoteRenderers.remove(uid)
            initializedRenderers.remove(renderer)
            runCatching { renderer.release() }
        }
    }

    fun end() {
        roomRef?.child("participants")?.child(myUid)?.removeValue()
        roomRef?.child("status")?.setValue("ended")
        cleanup("ended")
    }

    private fun removePeer(uid: String) {
        peers.remove(uid)?.peer?.let { runCatching { it.close(); it.dispose() } }
        pendingCandidates.remove(uid)
        pendingRemoteTracks.remove(uid)
        remoteRenderers.remove(uid)
        updateParticipantConnection(uid, false)
    }

    private fun activateRuntime() {
        context?.let { app ->
            CallForegroundService.start(
                app,
                "group:${_state.value.roomId}",
                _state.value.groupName,
                _state.value.video,
                screenShare = false,
                groupId = _state.value.groupId,
                roomId = _state.value.roomId,
                groupName = _state.value.groupName,
                memberIds = activeMemberIds
            )
            val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
    }

    private fun cleanup(finalStatus: String) {
        if (isClosing) return
        isClosing = true
        roomRef?.child("participants")?.child(myUid)?.removeValue()
        participantsListener?.let { roomRef?.child("participants")?.removeEventListener(it) }
        signalListener?.let { roomRef?.child("signals")?.child(myUid)?.removeEventListener(it) }
        roomValueListener?.let { roomRef?.removeEventListener(it) }
        participantsListener = null; signalListener = null; roomValueListener = null
        peers.values.forEach { slot ->
            runCatching { slot.remoteVideo?.let { track -> remoteRenderers[slot.uid]?.let(track::removeSink) } }
            runCatching { slot.peer.close(); slot.peer.dispose() }
        }
        runCatching { localVideo?.let { track -> localRenderer?.let(track::removeSink) } }
        remoteRenderers.values.forEach { renderer -> runCatching { renderer.release() } }
        localRenderer?.let { renderer -> runCatching { renderer.release() } }
        initializedRenderers.clear()
        peers.clear(); remoteRenderers.clear(); pendingRemoteTracks.clear(); localRenderer = null
        runCatching { videoCapturer?.stopCapture() }; videoCapturer?.dispose(); videoCapturer = null
        surfaceTextureHelper?.dispose(); surfaceTextureHelper = null
        localVideo?.dispose(); localVideo = null; videoSource?.dispose(); videoSource = null
        localAudio?.dispose(); localAudio = null
        context?.let(CallForegroundService::stop)
        activeMemberIds = emptyList()
        hostId = ""
        _state.value = _state.value.copy(status = finalStatus, hostId = "", captionsEnabled = false, recording = false)
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        _state.value = _state.value.copy(status = "failed", error = message)
        cleanup("failed")
    }

    private fun createCameraCapturer(app: Context): VideoCapturer? {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(app)) Camera2Enumerator(app) else Camera1Enumerator(false)
        enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let { enumerator.createCapturer(it, null)?.let { cap -> return cap } }
        return enumerator.deviceNames.firstOrNull()?.let { name -> enumerator.createCapturer(name, null) }
    }

    private fun fetchIceServers(callback: (List<PeerConnection.IceServer>, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return callback(emptyList(), "Please sign in again")
        user.getIdToken(false).addOnSuccessListener { result ->
            val token = result.token ?: return@addOnSuccessListener callback(emptyList(), "Could not authenticate TURN request")
            val request = Request.Builder().url(GATEWAY).header("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
            OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { mainHandler.post { callback(emptyList(), e.localizedMessage ?: "TURN unavailable") } }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) { mainHandler.post { callback(emptyList(), "TURN gateway HTTP ${it.code}") }; return }
                        val root = JSONObject(it.body?.string().orEmpty()); val array = root.optJSONArray("iceServers") ?: JSONArray(); val list = mutableListOf<PeerConnection.IceServer>()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i); val urlsJson = item.optJSONArray("urls")
                            val urls = if (urlsJson != null) (0 until urlsJson.length()).map { n -> urlsJson.getString(n) } else listOf(item.optString("urls"))
                            list += PeerConnection.IceServer.builder(urls).setUsername(item.optString("username")).setPassword(item.optString("credential")).createIceServer()
                        }
                        mainHandler.post { callback(list.ifEmpty { listOf(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()) }, null) }
                    }
                }
            })
        }.addOnFailureListener { callback(emptyList(), it.localizedMessage ?: "TURN authentication failed") }
    }
}

