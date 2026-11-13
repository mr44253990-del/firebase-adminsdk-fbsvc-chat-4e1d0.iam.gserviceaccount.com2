package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** One row in the admin user-management table. */
private data class ManagedUser(
    val uid: String,
    val name: String,
    val username: String,
    val email: String,
    val photo: String
)

/**
 * UserManagementScreen — admin → Settings → User management.
 *
 * Lists every registered user with their email. Tapping "Send reset link" calls
 * FirebaseAuth.sendPasswordResetEmail(email): Firebase mails the user a signed, expiring
 * password-reset link. Nothing is stored or logged on the device.
 */
@Composable
fun UserManagementScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sendingTo by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        runCatching {
            val snap = db.collection("users").get().await()
            users = snap.documents.mapNotNull { doc ->
                val email = doc.getString("email").orEmpty()
                if (email.isBlank()) return@mapNotNull null
                ManagedUser(
                    uid = doc.id,
                    name = doc.getString("name").orEmpty().ifBlank { "User" },
                    username = doc.getString("username").orEmpty(),
                    email = email,
                    photo = doc.getString("profileImageUrl").orEmpty()
                )
            }.sortedBy { it.name.lowercase() }
        }
        loading = false
    }

    val filtered = remember(users, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) users else users.filter { it.name.lowercase().contains(q) || it.username.lowercase().contains(q) || it.email.lowercase().contains(q) }
    }

    Column(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0A0F1E), Color(0xFF111A33), Color(0xFF0A0F1E)), start = Offset.Zero, end = Offset(900f, 1500f)))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF12B886), Color(0xFF3BC9DB)))).padding(8.dp)) {
                Icon(Icons.Default.ManageAccounts, null, tint = Color.White, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("User Management", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text("Send password-reset links to any user", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = .6f)) },
            placeholder = { Text("Search name, @username or email…", color = Color.White.copy(alpha = .45f)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = .08f),
                unfocusedContainerColor = Color.White.copy(alpha = .06f),
                focusedIndicatorColor = Color(0xFF3BC9DB),
                unfocusedIndicatorColor = Color.White.copy(alpha = .2f),
                cursorColor = Color.White
            )
        )
        Spacer(Modifier.height(8.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF3BC9DB)) }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.uid }) { user ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = .07f))
                            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = user.photo.ifBlank { null }, contentDescription = user.name,
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF12B886).copy(alpha = .4f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (user.username.isNotBlank()) Text("@${user.username}", color = Color.White.copy(alpha = .6f), fontSize = 11.sp, maxLines = 1)
                            Text(user.email, color = Color(0xFF3BC9DB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (sendingTo == user.uid) {
                            CircularProgressIndicator(Modifier.size(26.dp), color = Color(0xFF12B886), strokeWidth = 2.5.dp)
                        } else {
                            Button(
                                onClick = {
                                    sendingTo = user.uid
                                    FirebaseAuth.getInstance().sendPasswordResetEmail(user.email)
                                        .addOnSuccessListener { toast = "Reset link sent to ${user.email}" }
                                        .addOnFailureListener { e -> toast = "Failed: ${e.message}" }
                                        .addOnCompleteListener { sendingTo = null }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12B886)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Key, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        toast?.let { msg ->
            androidx.compose.material3.Snackbar(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                containerColor = Color(0xFF12B886),
                contentColor = Color.White
            ) { Text(msg, fontSize = 12.sp) }
            LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); toast = null }
        }
        Spacer(Modifier.height(8.dp))
    }
}
