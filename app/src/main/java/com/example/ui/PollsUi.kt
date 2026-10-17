package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay

/**
 * Admin polls.
 *
 * Firestore layout:
 *  polls/{pollId}               question, options[], counts{ "0": n, ... }, totalVotes, createdAt, endsAt, active, createdBy
 *  polls/{pollId}/votes/{uid}   uid, name, photo, optionIndex, votedAt   (readable by the voter and the admin only)
 *
 * Users only see polls that are active and not past [AppPoll.endsAt]; an expired poll disappears
 * by itself on every phone even if nobody closes it. Votes run in a transaction that also bumps
 * the aggregate counts, so users can see live percentages without seeing who voted.
 */
data class AppPoll(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val counts: Map<String, Long> = emptyMap(),
    val totalVotes: Long = 0L,
    val createdAt: Long = 0L,
    val endsAt: Long = 0L,
    val active: Boolean = true,
    val createdBy: String = ""
) {
    fun isLive(now: Long = System.currentTimeMillis()) = active && endsAt > now
    fun countFor(index: Int) = counts[index.toString()] ?: 0L
}

data class PollVote(
    val uid: String = "",
    val name: String = "",
    val photo: String = "",
    val optionIndex: Int = -1,
    val votedAt: Long = 0L
)

private const val POLL_ADMIN_EMAIL = "mr4425390@gmail.com"

object AppPolls {
    private val db get() = FirebaseFirestore.getInstance()

    @Suppress("UNCHECKED_CAST")
    fun parse(id: String, data: Map<String, Any?>): AppPoll = AppPoll(
        id = id,
        question = data["question"] as? String ?: "",
        options = (data["options"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        counts = (data["counts"] as? Map<String, Any?>)?.mapValues { (it.value as? Number)?.toLong() ?: 0L }.orEmpty(),
        totalVotes = (data["totalVotes"] as? Number)?.toLong() ?: 0L,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
        endsAt = (data["endsAt"] as? Number)?.toLong() ?: 0L,
        active = data["active"] as? Boolean ?: false,
        createdBy = data["createdBy"] as? String ?: ""
    )

    fun listen(activeOnly: Boolean, onChange: (List<AppPoll>) -> Unit): ListenerRegistration {
        val base = db.collection("polls")
        val query = if (activeOnly) base.whereEqualTo("active", true) else base.limit(60)
        return query.addSnapshotListener { snap, _ ->
            val list = snap?.documents.orEmpty().map { parse(it.id, it.data.orEmpty()) }.sortedByDescending { it.createdAt }
            onChange(list)
        }
    }

    fun listenMyVote(pollId: String, uid: String, onChange: (Int?) -> Unit): ListenerRegistration =
        db.collection("polls").document(pollId).collection("votes").document(uid)
            .addSnapshotListener { snap, _ -> onChange((snap?.get("optionIndex") as? Number)?.toInt()) }

    fun listenVotes(pollId: String, onChange: (List<PollVote>) -> Unit): ListenerRegistration =
        db.collection("polls").document(pollId).collection("votes").addSnapshotListener { snap, _ ->
            onChange(snap?.documents.orEmpty().map {
                PollVote(
                    uid = it.id,
                    name = it.getString("name").orEmpty(),
                    photo = it.getString("photo").orEmpty(),
                    optionIndex = (it.get("optionIndex") as? Number)?.toInt() ?: -1,
                    votedAt = (it.get("votedAt") as? Number)?.toLong() ?: 0L
                )
            }.sortedByDescending { it.votedAt })
        }

    /** One vote per user; changing the vote moves the count from the old option to the new one. */
    fun vote(poll: AppPoll, optionIndex: Int, name: String, photo: String, onDone: (String?) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone("Sign in first")
        if (!poll.isLive()) return onDone("This poll has ended")
        val pollRef = db.collection("polls").document(poll.id)
        val voteRef = pollRef.collection("votes").document(uid)
        db.runTransaction<Any?> { tx ->
            val pollSnap = tx.get(pollRef)
            val endsAt = (pollSnap.get("endsAt") as? Number)?.toLong() ?: 0L
            if (pollSnap.getBoolean("active") != true || endsAt <= System.currentTimeMillis()) throw IllegalStateException("This poll has ended")
            val previous = (tx.get(voteRef).get("optionIndex") as? Number)?.toInt()
            if (previous == optionIndex) return@runTransaction null
            val updates = mutableMapOf<String, Any>("counts.$optionIndex" to FieldValue.increment(1))
            if (previous != null) updates["counts.$previous"] = FieldValue.increment(-1) else updates["totalVotes"] = FieldValue.increment(1)
            tx.update(pollRef, updates)
            tx.set(voteRef, mapOf("uid" to uid, "name" to name, "photo" to photo, "optionIndex" to optionIndex, "votedAt" to System.currentTimeMillis()))
            null
        }.addOnSuccessListener { onDone(null) }.addOnFailureListener { onDone(it.message ?: "Vote failed") }
    }

    fun create(question: String, options: List<String>, durationMinutes: Long, onDone: (String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user?.email?.lowercase()?.trim() != POLL_ADMIN_EMAIL) return onDone("Admin only")
        val clean = options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (question.isBlank() || clean.size < 2) return onDone("Add a question and at least two options")
        val now = System.currentTimeMillis()
        db.collection("polls").add(
            mapOf(
                "question" to question.trim(),
                "options" to clean,
                "counts" to clean.indices.associate { it.toString() to 0L },
                "totalVotes" to 0L,
                "createdAt" to now,
                "endsAt" to now + durationMinutes.coerceIn(1L, 60L * 24 * 30) * 60_000L,
                "active" to true,
                "createdBy" to user.uid
            )
        ).addOnSuccessListener { onDone(null) }.addOnFailureListener { onDone(it.message ?: "Could not create poll") }
    }

    fun close(pollId: String) {
        db.collection("polls").document(pollId).update(mapOf("active" to false, "endsAt" to System.currentTimeMillis()))
    }

    fun delete(pollId: String) {
        val ref = db.collection("polls").document(pollId)
        ref.collection("votes").get().addOnSuccessListener { votes ->
            val batch = db.batch()
            votes.documents.forEach { batch.delete(it.reference) }
            batch.delete(ref)
            batch.commit()
        }
    }
}

fun formatPollRemaining(ms: Long): String {
    if (ms <= 0) return "Ended"
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${minutes}m left"
        else -> "%d:%02d left".format(minutes, seconds)
    }
}

@Composable
private fun rememberNowTicker(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); now = System.currentTimeMillis() } }
    return now
}

/** Feed section for users: every live poll as a glass card. Expired polls vanish automatically. */
@Composable
fun ActivePollsSection(userName: String, userPhoto: String, modifier: Modifier = Modifier) {
    var polls by remember { mutableStateOf<List<AppPoll>>(emptyList()) }
    DisposableEffect(Unit) {
        val reg = AppPolls.listen(activeOnly = true) { polls = it }
        onDispose { reg.remove() }
    }
    val now = rememberNowTicker()
    val live = polls.filter { it.isLive(now) }
    if (live.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        live.forEach { poll -> key(poll.id) { PollCard(poll, now, userName, userPhoto) } }
    }
}

@Composable
private fun PollCard(poll: AppPoll, now: Long, userName: String, userPhoto: String) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var myVote by remember(poll.id) { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(poll.id, uid) {
        val reg = if (uid.isNotBlank()) AppPolls.listenMyVote(poll.id, uid) { myVote = it } else null
        onDispose { reg?.remove() }
    }
    val accent = MaterialTheme.colorScheme.primary
    val showResults = myVote != null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .16f), Color(0xFFFF4FA3).copy(alpha = .10f), MaterialTheme.colorScheme.surface.copy(alpha = .75f))))
            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, Color(0xFFFF4FA3)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.HowToVote, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Community poll", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                Text("${poll.totalVotes} vote${if (poll.totalVotes == 1L) "" else "s"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f)) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatPollRemaining(poll.endsAt - now), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(poll.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        poll.options.forEachIndexed { index, option ->
            val count = poll.countFor(index)
            val share = if (poll.totalVotes > 0) count.toFloat() / poll.totalVotes else 0f
            val animated by animateFloatAsState(if (showResults) share else 0f, tween(650), label = "poll_bar")
            val mine = myVote == index
            Box(
                Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .85f))
                    .border(1.dp, if (mine) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), RoundedCornerShape(16.dp))
                    .clickable(enabled = !busy) {
                        busy = true
                        AppPolls.vote(poll, index, userName, userPhoto) { error ->
                            busy = false
                            if (error != null) Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0f, 1f)).background(accent.copy(alpha = if (mine) .30f else .14f)))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mine) { Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
                    Text(option, Modifier.weight(1f), fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (showResults) Text("${(share * 100).toInt()}%", fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            if (showResults) "Tap another option to change your vote" else "Tap an option to vote • results appear after you vote",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Admin panel: create a timed poll, close/delete polls and see exactly who voted for what. */
@Composable
fun AdminPollsPanel() {
    val context = LocalContext.current
    var polls by remember { mutableStateOf<List<AppPoll>>(emptyList()) }
    DisposableEffect(Unit) {
        val reg = AppPolls.listen(activeOnly = false) { polls = it }
        onDispose { reg.remove() }
    }
    val now = rememberNowTicker()
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var minutes by remember { mutableStateOf("60") }
    var saving by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("New poll", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(question, { question = it.take(200) }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value, { options[index] = it.take(80) }, label = { Text("Option ${index + 1}") }, singleLine = true, modifier = Modifier.weight(1f))
                        if (options.size > 2) IconButton(onClick = { options.removeAt(index) }) { Icon(Icons.Default.Close, "Remove option") }
                    }
                }
                if (options.size < 6) TextButton(onClick = { options.add("") }) { Text("+ Add option") }
                Text("Time limit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("15" to "15 min", "60" to "1 hour", "360" to "6 hours", "1440" to "1 day", "4320" to "3 days", "10080" to "1 week").forEach { (value, label) ->
                        FilterChip(selected = minutes == value, onClick = { minutes = value }, label = { Text(label) })
                    }
                }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(5) }, label = { Text("Custom minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        saving = true
                        AppPolls.create(question, options.toList(), minutes.toLongOrNull() ?: 60L) { error ->
                            saving = false
                            if (error == null) {
                                question = ""; options.clear(); options.addAll(listOf("", ""))
                                Toast.makeText(context, "Poll is live for everyone", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.HowToVote, null); Spacer(Modifier.width(7.dp)); Text(if (saving) "Publishing…" else "Publish poll") }
            }
        }
        if (polls.isEmpty()) Text("No polls yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        polls.forEach { poll -> key(poll.id) { AdminPollResultCard(poll, now) } }
    }
}

@Composable
private fun AdminPollResultCard(poll: AppPoll, now: Long) {
    var votes by remember { mutableStateOf<List<PollVote>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    DisposableEffect(poll.id) {
        val reg = AppPolls.listenVotes(poll.id) { votes = it }
        onDispose { reg.remove() }
    }
    val live = poll.isLive(now)
    val total = votes.size.coerceAtLeast(0)
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = if (live) Color(0xFF16A34A).copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (live) "LIVE • ${formatPollRemaining(poll.endsAt - now)}" else "ENDED", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (live) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (live) TextButton(onClick = { AppPolls.close(poll.id) }) { Text("End now") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete poll", tint = MaterialTheme.colorScheme.error) }
            }
            Text(poll.question, fontWeight = FontWeight.ExtraBold)
            poll.options.forEachIndexed { index, option ->
                val count = votes.count { it.optionIndex == index }
                val share = if (total > 0) count.toFloat() / total else 0f
                Column {
                    Row { Text(option, Modifier.weight(1f), fontSize = 13.sp); Text("$count • ${(share * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                }
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide voters" else "Who voted ($total)") }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (votes.isEmpty()) Text("No votes yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    votes.forEach { vote ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                Text(vote.name.take(1).ifBlank { "?" }.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(vote.name.ifBlank { vote.uid.take(8) }, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("→ " + (poll.options.getOrNull(vote.optionIndex) ?: "?"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete poll?") },
        text = { Text("The poll and all its votes are removed for everyone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; AppPolls.delete(poll.id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}
