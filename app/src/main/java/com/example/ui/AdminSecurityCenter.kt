package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.User
import com.example.service.SecurityLocate

/**
 * Admin Security Center — the "Security" page inside the admin control center.
 *
 * Lists every user with their live status; tapping a user opens their security card where the
 * admin can trigger "Find this device": the phone flashes its flashlight and vibrates for a
 * few seconds (auto-off). The device owner can revoke this permission in their own settings,
 * and the locate signal never reads or transmits location — it only rings the hardware.
 */
@Composable
fun AdminSecurityCenterScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val users by viewModel.adminDirectoryUsers.collectAsState()
    val emails by viewModel.adminAuthEmails.collectAsState()
    val loading by viewModel.adminDirectoryLoading.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<User?>(null) }
    var locating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAdminUsersPage(reset = true)
        viewModel.loadAdminAuthEmails()
    }

    val filtered = remember(users, query) {
        if (query.isBlank()) users
        else users.filter {
            it.name.contains(query, true) || it.username.contains(query, true) ||
                (emails[it.uid] ?: "").contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Security center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "Every account with live status. Tap a user to run a safe \"find this device\" check: their phone flashes and vibrates for a few seconds — location is never read or shared.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search name, username or e-mail") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${users.size} accounts loaded${if (loading) " • loading more…" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (filtered.isEmpty() && !loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No accounts match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.uid }) { user ->
                    val online = user.isOnline
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                        onClick = { selected = user }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = user.profileImageUrl.ifBlank { null },
                                    contentDescription = user.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(11.dp)
                                        .clip(CircleShape)
                                        .background(if (online) Color(0xFF2E9E5B) else Color(0xFF9AA3AF))
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name.ifBlank { "Convo Chat user" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "@${user.username.ifBlank { "user" }}  ·  ${emails[user.uid] ?: "e-mail not loaded"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (online) Color(0xFF2E9E5B).copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (online) "Active" else "Offline",
                                    color = if (online) Color(0xFF1B6B3A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { user ->
        AlertDialog(
            onDismissRequest = { if (!locating) selected = null },
            icon = { Icon(Icons.Default.FlashlightOn, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(user.name.ifBlank { "User" }, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@${user.username.ifBlank { "user" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("E-mail: ${emails[user.uid] ?: "not loaded yet"}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (user.isOnline) "Status: Active — the device is reachable." else "Status: Offline — the locate signal needs the device to be online.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (user.isOnline) Color(0xFF1B6B3A) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Find this device sends a silent signal: the phone's flashlight turns on and it vibrates for about six seconds, then everything turns off automatically. It works only while the app can receive push messages, and the owner can turn the permission off in their settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (locating) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = !locating,
                    onClick = {
                        locating = true
                        viewModel.adminLocateUser(user) { ok, message ->
                            locating = false
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (ok) selected = null
                        }
                    }
                ) {
                    Icon(Icons.Default.FlashlightOn, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Find this device")
                }
            },
            dismissButton = { TextButton(enabled = !locating, onClick = { selected = null }) { Text("Close") } }
        )
    }
}
