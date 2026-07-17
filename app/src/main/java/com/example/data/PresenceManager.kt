package com.example.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Realtime presence system – exactly like Messenger:
 *  - online when the app is in foreground with internet
 *  - automatically offline (with last-seen timestamp) via onDisconnect()
 *  - respects the user's "Activity status" privacy toggle
 */
object PresenceManager {

    private var presenceRef = runCatching {
        FirebaseDatabase.getInstance(
            "https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).getReference("status")
    }.getOrElse {
        runCatching { FirebaseDatabase.getInstance().getReference("status") }.getOrNull()
    }

    private var connectedListener: ValueEventListener? = null
    private var currentUid: String? = null
    private var sharingEnabled = true

    /** Call when the user logs in. Hooks .info/connected so status follows the connection. */
    fun goOnline(uid: String, shareStatus: Boolean) {
        currentUid = uid
        sharingEnabled = shareStatus
        val ref = presenceRef ?: return
        try {
            val connectedRef = runCatching {
                FirebaseDatabase.getInstance(
                    "https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app"
                ).getReference(".info/connected")
            }.getOrElse {
                FirebaseDatabase.getInstance().getReference(".info/connected")
            }

            connectedListener?.let { connectedRef.removeEventListener(it) }
            connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (!connected) return
                    val myStatus = ref.child(uid)
                    // Whatever happens (kill app, lose network) -> offline + last seen
                    myStatus.onDisconnect().setValue(
                        mapOf("online" to false, "lastChanged" to ServerValue.TIMESTAMP)
                    )
                    myStatus.setValue(
                        mapOf(
                            "online" to sharingEnabled,
                            "lastChanged" to ServerValue.TIMESTAMP
                        )
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("Presence", "connected listener cancelled: ${error.message}")
                }
            }
            connectedListener?.let { connectedRef.addValueEventListener(it) }
        } catch (e: Exception) {
            Log.e("Presence", "goOnline failed: ${e.message}")
        }
    }

    /** Update privacy preference live (without re-login). */
    fun setSharing(enabled: Boolean) {
        sharingEnabled = enabled
        val uid = currentUid ?: return
        try {
            presenceRef?.child(uid)?.setValue(
                mapOf("online" to enabled, "lastChanged" to ServerValue.TIMESTAMP)
            )
        } catch (e: Exception) {
            Log.e("Presence", "setSharing failed: ${e.message}")
        }
    }

    fun goOffline() {
        val uid = currentUid ?: return
        try {
            presenceRef?.child(uid)?.setValue(
                mapOf("online" to false, "lastChanged" to ServerValue.TIMESTAMP)
            )
        } catch (e: Exception) {
            Log.e("Presence", "goOffline failed: ${e.message}")
        }
    }

    /** Observes another user's presence. */
    fun observe(uid: String, onChange: (Presence) -> Unit): ValueEventListener? {
        val ref = presenceRef ?: return null
        return try {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val last = snapshot.child("lastChanged").getValue(Long::class.java) ?: 0L
                    onChange(Presence(online, last))
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            ref.child(uid).addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            null
        }
    }

    fun stopObserving(uid: String, listener: ValueEventListener?) {
        if (listener == null) return
        try {
            presenceRef?.child(uid)?.removeEventListener(listener)
        } catch (_: Exception) {
        }
    }

    /** Human readable "Active now" / "Active 5m ago" label. */
    fun lastSeenLabel(presence: Presence): String {
        if (presence.online) return "Active now"
        if (presence.lastChanged <= 0) return "Offline"
        val diff = System.currentTimeMillis() - presence.lastChanged
        val minutes = diff / 60000
        val hours = diff / 3600000
        val days = diff / 86400000
        return when {
            minutes < 1 -> "Active just now"
            minutes < 60 -> "Active ${minutes}m ago"
            hours < 24 -> "Active ${hours}h ago"
            days < 7 -> "Active ${days}d ago"
            else -> "Offline"
        }
    }
}
