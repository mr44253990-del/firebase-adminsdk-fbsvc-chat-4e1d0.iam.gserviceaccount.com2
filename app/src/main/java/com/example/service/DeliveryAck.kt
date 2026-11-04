package com.example.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Collections

/**
 * Delivery acknowledgement ("✓✓ delivered") for messages the device received while the chat
 * itself was NOT open.
 *
 * Before: a message sent to an offline friend stayed on one tick forever unless the push woke
 * FirebaseMessagingService or the friend opened that exact chat. Now, whenever this device is
 * reachable — app opened, unread counter changed, background presence probe or any push — it
 * looks at the senders with unread messages and writes `delivered` receipts for their pending
 * messages. `seen` is still written only when the chat is actually opened.
 */
object DeliveryAck {
    private val acked = Collections.synchronizedSet(LinkedHashSet<String>())

    private fun db(): FirebaseDatabase = runCatching { FirebaseDatabase.getInstance() }
        .getOrElse { FirebaseDatabase.getInstance("https://chat-4e1d0-default-rtdb.asia-southeast1.firebasedatabase.app") }

    /** Acknowledge delivery of everything [senderId] sent to the signed-in user that is still relayed. */
    fun ackFrom(senderId: String, seen: Boolean = false) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (senderId.isBlank() || senderId == me) return
        val chatId = listOf(me, senderId).sorted().joinToString("_")
        db().getReference("chats").child(chatId).child("messages")
            .orderByChild("timestamp").limitToLast(80).get()
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val updates = HashMap<String, Any>()
                snapshot.children.forEach { child ->
                    val id = child.key ?: return@forEach
                    if (child.child("senderId").getValue(String::class.java) != senderId) return@forEach
                    if (!acked.add("$chatId/$id${if (seen) "/s" else ""}")) return@forEach
                    updates["$id/delivered"] = true
                    updates["$id/deliveredAt"] = now
                    if (seen) { updates["$id/seen"] = true; updates["$id/seenAt"] = now }
                }
                if (acked.size > 4000) synchronized(acked) { acked.iterator().let { repeat(1000) { _ -> if (it.hasNext()) { it.next(); it.remove() } } } }
                if (updates.isNotEmpty()) {
                    db().getReference("delivery_receipts").child(senderId).child(chatId).updateChildren(updates)
                        .addOnFailureListener { updates.keys.forEach { k -> acked.remove("$chatId/${k.substringBefore('/')}${if (seen) "/s" else ""}") } }
                }
            }
    }

    /** One-shot scan of unread counters (used from background pushes / presence probes). */
    fun ackAllPending() {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db().getReference("unread_counts").child(me).get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { child ->
                val count = runCatching { child.getValue(Long::class.java) ?: 0L }.getOrDefault(0L)
                if (count > 0L) child.key?.let { ackFrom(it) }
            }
        }
    }
}
