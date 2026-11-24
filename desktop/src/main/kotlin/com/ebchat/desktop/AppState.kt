package com.ebchat.desktop

import com.ebchat.desktop.net.AuthClient
import com.ebchat.desktop.net.FirebaseConfig
import com.ebchat.desktop.net.FirestoreRest
import com.ebchat.desktop.net.RtdbClient
import com.ebchat.desktop.session.SessionStore
import com.ebchat.desktop.ui.AppearanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Desktop application state machine, mirroring the Android navigation:
 * Splash (loading) → Login → Home feed → Chat → Settings / Permissions.
 */
class AppState(private val scope: CoroutineScope) {

    sealed interface Route {
        data object Splash : Route
        data object Login : Route
        data object Home : Route
        data object Chat : Route
        data object Settings : Route
        data object Permissions : Route
    }

    var config: FirebaseConfig = FirebaseConfig.load()
        private set

    val auth = AuthClient(config)
    val firestore get() = FirestoreRest(config) { idToken }
    val rtdb get() = RtdbClient(config) { idToken }

    var route: Route = Route.Splash
        private set
    var idToken: String = ""
        private set
    var uid: String = ""
        private set
    var email: String = ""
        private set
    var displayName: String = ""
        private set

    var feed: List<FirestoreRest.Post> = emptyList()
        private set
    var messages: List<RtdbClient.ChatMessage> = emptyList()
        private set
    var chatPeerId: String = ""
        private set

    var appearance: AppearanceMode = AppearanceMode.DARK
    var errorMessage: String = ""
        private set
    var busy: Boolean = false
        private set

    /** Called once at startup to restore a saved session, like the Android loading screen. */
    fun start(onChange: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            val saved = SessionStore.load()
            if (saved != null) {
                val refresh = saved["refreshToken"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val savedUid = saved["uid"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (refresh.isNotBlank() && savedUid.isNotBlank()) {
                    val restored = runCatching { auth.refreshToken(refresh) }.getOrNull()
                    if (restored != null) {
                        applyAuth(restored.localId, restored.idToken, restored.refreshToken, restored.email, restored.displayName)
                        rtdb.setStatus(restored.localId, "online")
                        loadFeed()
                        route = Route.Home
                        onChange()
                        return@launch
                    }
                }
            }
            delay(900)
            route = Route.Login
            onChange()
        }
    }

    fun signIn(email: String, password: String, onChange: () -> Unit) {
        busy = true; errorMessage = ""; onChange()
        scope.launch(Dispatchers.IO) {
            runCatching { auth.signIn(email.trim(), password) }
                .onSuccess { result ->
                    applyAuth(result.localId, result.idToken, result.refreshToken, result.email, result.displayName)
                    SessionStore.save(result.refreshToken, result.localId, result.email, result.displayName)
                    rtdb.setStatus(result.localId, "online")
                    loadFeed()
                    route = Route.Home
                    busy = false
                }
                .onFailure { err -> errorMessage = friendly(err); busy = false }
            onChange()
        }
    }

    fun signUp(name: String, email: String, password: String, onChange: () -> Unit) {
        busy = true; errorMessage = ""; onChange()
        scope.launch(Dispatchers.IO) {
            runCatching { auth.signUp(email.trim(), password, name.trim()) }
                .onSuccess { result ->
                    applyAuth(result.localId, result.idToken, result.refreshToken, result.email, name.trim())
                    SessionStore.save(result.refreshToken, result.localId, result.email, name.trim())
                    rtdb.setStatus(result.localId, "online")
                    loadFeed()
                    route = Route.Home
                    busy = false
                }
                .onFailure { err -> errorMessage = friendly(err); busy = false }
            onChange()
        }
    }

    fun signOut(onChange: () -> Unit) {
        rtdb.setStatus(uid, "offline")
        SessionStore.clear()
        idToken = ""; uid = ""; email = ""; displayName = ""
        feed = emptyList(); messages = emptyList()
        route = Route.Login
        onChange()
    }

    fun loadFeed(onChange: () -> Unit = {}) {
        val current = uid
        scope.launch(Dispatchers.IO) {
            runCatching { firestore.recentPosts() }.onSuccess { feed = it }
            if (current == uid) onChange()
        }
    }

    fun openChat(peer: FirestoreRest.Post, onChange: () -> Unit) {
        chatPeerId = peer.senderId
        route = Route.Chat
        refreshChat(onChange)
    }

    fun refreshChat(onChange: () -> Unit) {
        val myUid = uid
        val peer = chatPeerId
        if (myUid.isBlank() || peer.isBlank()) return
        scope.launch(Dispatchers.IO) {
            runCatching { rtdb.messages(rtdb.chatIdFor(myUid, peer)) }
                .onSuccess { messages = it }
            onChange()
        }
    }

    fun sendMessage(text: String, onChange: () -> Unit) {
        val myUid = uid
        val peer = chatPeerId
        if (text.isBlank() || myUid.isBlank() || peer.isBlank()) return
        val msgId = "desktop-${System.currentTimeMillis()}-${(0..99999).random()}"
        val name = displayName.ifBlank { email }
        scope.launch(Dispatchers.IO) {
            runCatching {
                rtdb.sendMessage(
                    chatId = rtdb.chatIdFor(myUid, peer),
                    messageId = msgId,
                    senderId = myUid,
                    senderName = name,
                    senderUsername = "",
                    text = text.trim()
                )
            }.onSuccess { refreshChat(onChange) }
                .onFailure { errorMessage = friendly(it) }
        }
    }

    fun go(target: Route, onChange: () -> Unit) {
        route = target
        onChange()
    }

    fun setAppearance(mode: AppearanceMode, onChange: () -> Unit) {
        appearance = mode
        onChange()
    }

    private fun applyAuth(localId: String, token: String, refresh: String, mail: String, name: String) {
        uid = localId; idToken = token; email = mail; displayName = name
    }

    @Suppress("UNUSED_PARAMETER")
    private fun friendly(err: Throwable): String = when {
        err is com.ebchat.desktop.net.Rest.HttpError && err.status == 400 -> "Wrong email or password."
        err is com.ebchat.desktop.net.Rest.HttpError && err.status == 401 -> "Sign-in expired. Please sign in again."
        else -> err.message ?: "Network error. Check your connection."
    }
}
