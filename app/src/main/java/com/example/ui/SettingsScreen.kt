package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.*

/** Settings — dynamic themes, activity status, blocked users, webhook, about, logout. */
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    currentThemeId: String,
    onThemeChange: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val currentUser by viewModel.currentUserState.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()

    var showAbout by remember { mutableStateOf(false) }
    var showWebhook by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }

    val blockedUsers = users.filter { it.uid in (currentUser?.blockedUsers ?: emptyList()) }

    GlassBackground(bubbleCount = 8) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            item(key = "title") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientText("Settings", MaterialTheme.typography.headlineMedium)
                }
            }

            // ---- Theme picker ----
            item(key = "themes") {
                SectionTitle("App theme", modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AllPalettes.take(3).forEach { palette ->
                        ThemeCard(palette, palette.id == currentThemeId, Modifier.weight(1f)) {
                            onThemeChange(palette.id)
                            viewModel.saveTheme(palette.id)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AllPalettes.drop(3).forEach { palette ->
                        ThemeCard(palette, palette.id == currentThemeId, Modifier.weight(1f)) {
                            onThemeChange(palette.id)
                            viewModel.saveTheme(palette.id)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Privacy ----
            item(key = "privacy") {
                SectionTitle("Privacy", modifier = Modifier.padding(horizontal = 16.dp))
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Visibility, null, tint = LocalPalette.current.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Activity status", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Show when you're online & last seen",
                                    color = TextSecondary, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = currentUser?.activityStatusEnabled ?: true,
                                onCheckedChange = { viewModel.setActivityStatus(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = LocalPalette.current.primary)
                            )
                        }
                        Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showBlocked = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Block, null, tint = UnreadRed)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Blocked users", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                Text("${blockedUsers.size} blocked", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Advanced ----
            item(key = "advanced") {
                SectionTitle("Advanced", modifier = Modifier.padding(horizontal = 16.dp))
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showWebhook = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cloud, null, tint = LocalPalette.current.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Push webhook", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                webhookUrl.take(42) + if (webhookUrl.length > 42) "…" else "",
                                color = TextSecondary, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- About ----
            item(key = "about") {
                SectionTitle("About", modifier = Modifier.padding(horizontal = 16.dp))
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showAbout = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = LocalPalette.current.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("About FireChat", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Text("Developer & app info", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ---- Logout ----
            item(key = "logout") {
                GradientButton(
                    text = "Sign out",
                    colors = listOf(Color(0xFFFF3B5C), Color(0xFFDD2476)),
                    onClick = { viewModel.logout(onSignOut) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "FireChat v2.0 · Made with ❤️ by Rakibul Islam",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    // ---- Blocked users dialog ----
    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            containerColor = Color(0xFF151528),
            title = { GradientText("Blocked users", MaterialTheme.typography.titleLarge) },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("You haven't blocked anyone.", color = TextSecondary)
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(blockedUsers, key = { it.uid }) { user ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = user.name, photoUrl = user.photoUrl, size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(user.name, color = Color.White, modifier = Modifier.weight(1f))
                                GlassChip("Unblock") { viewModel.unblockUser(user.uid) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlocked = false }) { Text("Close", color = TextSecondary) } }
        )
    }

    // ---- Webhook dialog ----
    if (showWebhook) {
        var url by remember { mutableStateOf(webhookUrl) }
        AlertDialog(
            onDismissRequest = { showWebhook = false },
            containerColor = Color(0xFF151528),
            title = { GradientText("Push webhook", MaterialTheme.typography.titleLarge) },
            text = {
                GlassField(value = url, onValueChange = { url = it }, label = "Webhook URL", singleLine = false)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateWebhookUrl(url.trim()); showWebhook = false }) {
                    Text("Save", color = LocalPalette.current.primary)
                }
            },
            dismissButton = { TextButton(onClick = { showWebhook = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // ---- About dialog ----
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = Color(0xFF151528),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(LocalPalette.current.bubbleMine))
                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("⚡", style = MaterialTheme.typography.headlineMedium) }
                    Spacer(Modifier.height(10.dp))
                    GradientText("FireChat", MaterialTheme.typography.titleLarge)
                    Text("Version 2.0", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "A next-generation social chat experience — stories, posts, groups, voice notes and more, wrapped in a living glass design.",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(14.dp))
                    Text("Developer", color = LocalPalette.current.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("Rakibul Islam", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Student at Kapilmuni College, passionate about building beautiful mobile experiences. FireChat is crafted with love, Kotlin, Jetpack Compose, Firebase & Supabase.",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Close", color = LocalPalette.current.primary) }
            }
        )
    }
}

@Composable
private fun ThemeCard(
    palette: FireChatPalette,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(palette.gradient.take(3)))
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) palette.primary else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(palette.bubbleMine))
        )
        Spacer(Modifier.height(8.dp))
        Text(palette.displayName, color = Color.White, style = MaterialTheme.typography.labelLarge)
        if (selected) {
            Icon(Icons.Default.CheckCircle, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        }
    }
}
