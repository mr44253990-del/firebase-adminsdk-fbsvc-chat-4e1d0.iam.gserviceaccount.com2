package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

/**
 * AdminSecurityScreen — admin → Security.
 *
 * Lists every user with their live online status. Tapping "Ring device" writes a one-shot
 * command to `remoteCommands/<uid>` which the target device picks up via
 * [com.example.service.RemoteCommandExecutor]: the camera flashlight blinks (1 s on / 1 s off)
 * and the phone vibrates, auto-stopping after the chosen duration. The command is consumed once.
 */
@Composable
fun AdminSecurityScreen(
    users: List<User>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val online = remember { mutableStateMapOf<String, Boolean>() }
    val lastActive = remember { mutableStateMapOf<String, Long>() }
    var ringing by remember { mutableStateOf<String?>(null) }
    var durationSec by remember { mutableStateOf(5) }

    // Subscribe to live presence for every listed user.
    LaunchedEffect(users.map { it.uid }.hashCode()) {
        val listeners = mutableListOf<ValueEventListener>()
        users.forEach { user ->
            val ref = FirebaseDatabase.getInstance().getReference("status").child(user.uid)
            val vl = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    online[user.uid] = snap.child("isOnline").getValue(Boolean::class.java) == true
                    lastActive[user.uid] = snap.child("lastActive").getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(vl)
            listeners.add(vl)
        }
    }

    val transition = rememberInfiniteTransition(label = "sec_bg")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "sec_drift")

    Column(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0B0A1F), Color(0xFF131033), Color(0xFF0A0F24)), start = Offset.Zero, end = Offset(1000f * (0.5f + drift), 1600f)))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF2E5BFF), Color(0xFF7C4DFF)))).padding(8.dp)) {
                Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Security", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("Ring any active device remotely", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
            }
        }

        // Duration picker.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ring duration:", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
            listOf(3, 5, 10).forEach { sec ->
                val selected = durationSec == sec
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (selected) Brush.linearGradient(listOf(Color(0xFF2E5BFF), Color(0xFF7C4DFF))) else Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .12f))))
                        .clickable { durationSec = sec }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("${sec}s", color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
            }
            Spacer(Modifier.weight(1f))
            Text("${users.size} users", color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.uid }) { user ->
                val isOnline = online[user.uid] == true
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = .07f))
                        .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AsyncImage(
                            model = user.profileImageUrl.ifBlank { null },
                            contentDescription = user.name,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF7C4DFF).copy(alpha = .4f))
                        )
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape)
                                .background(if (isOnline) Color(0xFF2EE86F) else Color(0xFF8A8A9E))
                                .border(2.dp, Color(0xFF0B0A1F), CircleShape)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name.ifBlank { "User" }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "@${user.username} · ${if (isOnline) "Active now" else "Offline"}",
                            color = if (isOnline) Color(0xFF2EE86F) else Color.White.copy(alpha = .55f),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (ringing == user.uid) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFFFFB020), strokeWidth = 2.5.dp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { sendRemoteCommand(user.uid, "flashlight", durationSec) { ringing = null } },
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFFB020).copy(alpha = .22f))
                            ) { Icon(Icons.Default.FlashlightOn, "Flashlight", tint = Color(0xFFFFB020), modifier = Modifier.size(18.dp)) }
                            Button(
                                onClick = { ringing = user.uid; sendRemoteCommand(user.uid, "ring", durationSec) { ringing = null } },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5BFF)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Vibration, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ring", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Writes a consumed-once remote command. [onDone] runs after the write attempt. */
private fun sendRemoteCommand(targetUid: String, type: String, durationSec: Int, onDone: () -> Unit) {
    val ref = FirebaseDatabase.getInstance().getReference("remoteCommands").child(targetUid).push()
    val payload = mapOf(
        "type" to type,
        "durationMs" to (durationSec * 1000L),
        "issuedAt" to System.currentTimeMillis(),
        "issuedBy" to (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "admin")
    )
    ref.setValue(payload).addOnCompleteListener { onDone() }
}
