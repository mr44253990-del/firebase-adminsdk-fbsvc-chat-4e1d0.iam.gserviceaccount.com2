package com.example.service

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Which conversation is visible on screen right now. While a chat is open and resumed,
 * new messages from that same person (or group) do not raise a system notification:
 * the user is already reading them. Messages from anyone else still notify normally,
 * and everything returns to normal as soon as the chat is left or the app goes to the
 * background.
 */
object ActiveChatState {
    @Volatile var openPeerUid: String? = null
        private set
    @Volatile var openGroupId: String? = null
        private set
    /** True while any Convo Chat activity is started (visible). */
    @Volatile var appVisible: Boolean = false

    fun isViewingPeer(uid: String?): Boolean = !uid.isNullOrBlank() && uid == openPeerUid
    fun isViewingGroup(groupId: String?): Boolean = !groupId.isNullOrBlank() && groupId == openGroupId

    internal fun setPeer(uid: String?) { openPeerUid = uid }
    internal fun setGroup(id: String?) { openGroupId = id }
}

/** Marks a 1:1 chat as visible only while its screen is RESUMED. */
@Composable
fun TrackOpenChat(peerUid: String?, groupId: String? = null) {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(owner, peerUid, groupId) {
        fun open() {
            if (peerUid != null) ActiveChatState.setPeer(peerUid)
            if (groupId != null) ActiveChatState.setGroup(groupId)
            // Clear notifications that are already sitting in the shade for this chat.
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                peerUid?.let { manager.cancel(("chat_" + it).hashCode()) }
            }
            peerUid?.let { MessageReminder.markChatOpened(context, it) }
        }
        fun close() {
            if (peerUid != null && ActiveChatState.openPeerUid == peerUid) ActiveChatState.setPeer(null)
            if (groupId != null && ActiveChatState.openGroupId == groupId) ActiveChatState.setGroup(null)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> open()
                Lifecycle.Event.ON_PAUSE -> close()
                else -> Unit
            }
        }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) open()
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            close()
        }
    }
}
