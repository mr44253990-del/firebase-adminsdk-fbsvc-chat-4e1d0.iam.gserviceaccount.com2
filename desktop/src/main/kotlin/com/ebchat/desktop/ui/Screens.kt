package com.ebchat.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebchat.desktop.AppState
import com.ebchat.desktop.platform.PermissionCenter
import java.text.SimpleDateFormat
import java.util.Date

private val GlassShape = RoundedCornerShape(24.dp)

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(GlassShape)
            .background(AuroraPalette.Glass)
            .border(1.dp, AuroraPalette.GlassEdge, GlassShape)
    ) { content() }
}

@Composable
private fun Currency(topText: String, bottomText: String) = Column {
    Text(topText, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Text(bottomText, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
}

/** Loading screen — the same Aurora branded splash the Android app shows. */
@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "aurora")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("C", fontSize = 56.sp, color = Color.White,
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = pulse,
                        scaleY = pulse,
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(AuroraPalette.Accent)
                    .padding(20.dp))
            Spacer(Modifier.height(18.dp))
            Text("Convo Chat", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("Desktop · Windows & Linux", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.width(220.dp),
                color = AuroraPalette.AccentAlt,
                strokeWidth = 3.dp
            )
        }
    }
}

/** Login / sign-up screen with the desktop permission notice, like the Android onboarding. */
@Composable
fun LoginScreen(state: AppState, onChange: () -> Unit) {
    var mode by remember { mutableStateOf("login") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.width(420.dp).padding(2.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Currency(
                    if (mode == "login") "Welcome back" else "Create your account",
                    if (mode == "login") "Sign in to Convo Chat"
                    else "A few details and you are in"
                )
                Spacer(Modifier.height(24.dp))
                if (mode == "signup") {
                    OutlinedTextField(name, { name = it }, label = { Text("Display name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(email, { email = it }, label = { Text("Email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (mode == "login") state.signIn(email, password, onChange) else state.signUp(name, email, password, onChange)
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraPalette.Accent),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (mode == "login") "Sign in" else "Sign up", fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                TextButton {
                    mode = if (mode == "login") "signup" else "login"
                }
                if (state.errorMessage.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.errorMessage, color = Color(0xFFFF7A90), fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Convo Desktop automatically uses only the permissions your OS allows: " +
                        "microphone, notifications and file picker. Nothing is requested before sign-in.",
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(0.75f),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun TextButton(onClick: () -> Unit) {
    Text(
        "Switch mode",
        color = AuroraPalette.AccentAlt,
        modifier = Modifier.clickable(onClick = onClick).padding(6.dp),
        fontSize = 14.sp
    )
}

@Composable
fun HomeScreen(state: AppState, onChange: () -> Unit) {
    LaunchedEffect(Unit) { state.loadFeed(onChange) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Currency("Home", "Welcome${state.displayName.take(16).let { if (it.isBlank()) "" else " , $it" }}")
            Spacer(Modifier.weight(1f))
            NavPill("Chat") { state.go(AppState.Route.Chat, onChange) }
            Spacer(Modifier.width(8.dp))
            NavPill("Profile & appearance") { state.go(AppState.Route.Settings, onChange) }
            Spacer(Modifier.width(8.dp))
            NavPill("App permissions") { state.go(AppState.Route.Permissions, onChange) }
            Spacer(Modifier.width(8.dp))
            NavPill("Log out") { state.signOut(onChange) }
        }
        Spacer(Modifier.height(22.dp))
        if (state.feed.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    if (state.errorMessage.isNotBlank()) state.errorMessage
                    else "Loading your feed…\nAndroid posts (text-only) appear here in real time.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            val scroll = rememberScrollState()
            Column(Modifier.verticalScroll(scroll).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.feed.reversed().forEach { post ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(post.senderName.ifBlank { "Convo user" },
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(post.timestamp), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                            if (post.feeling.isNotBlank()) {
                                Text("feeling: ${post.feeling}", fontSize = 12.sp,
                                    color = AuroraPalette.AccentAlt)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(post.text, color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 15.sp, lineHeight = 21.sp)
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun NavPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AuroraPalette.Glass)
            .border(1.dp, AuroraPalette.GlassEdge, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp) }
}

/** Direct 1:1 chat using the same RTDB envelope as the Android app. */
@Composable
fun ChatScreen(state: AppState, onChange: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavPill("← Home") { state.go(AppState.Route.Home, onChange) }
            Spacer(Modifier.width(12.dp))
            Currency("Chat", "Peer: ${state.chatPeerId.ifBlank { "pick a contact" }}")
        }
        Spacer(Modifier.height(18.dp))
        val chatScroll = rememberScrollState()
        LaunchedEffect(state.messages.size, state.chatPeerId) { state.refreshChat(onChange); chatScroll.scrollTo(chatScroll.maxValue) }
        GlassCard(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.verticalScroll(chatScroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.messages.isEmpty()) {
                    Text("No messages yet — say hello. Messages use the same secure envelope as Android.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                state.messages.takeLast(200).forEach { msg ->
                    val mine = msg.senderId == state.uid
                    Box(Modifier.fillMaxWidth()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (mine) AuroraPalette.Accent else Color(0x22FFFFFF)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                                .widthIn(max = 560.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(if (mine) "You" else msg.senderName.ifBlank { "Peer" },
                                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                                Spacer(Modifier.height(4.dp))
                                Text(msg.text, color = Color.White, fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(formatTime(msg.timestamp), fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, placeholder = { Text("Write a message…") },
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { state.sendMessage(draft, onChange); draft = "" },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AuroraPalette.Accent)
            ) { Text("Send") }
        }
    }
}

/** Profile & appearance — same Light / Dark / AMOLED modes as the Android app. */
@Composable
fun SettingsScreen(state: AppState, onChange: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavPill("← Home") { state.go(AppState.Route.Home, onChange) }
            Spacer(Modifier.width(12.dp))
            Currency("Profile & appearance", "Signed in as ${state.email.ifBlank { "user" }}")
        }
        Spacer(Modifier.height(26.dp))
        GlassCard(Modifier.width(560.dp)) {
            Column(Modifier.padding(24.dp)) {
                AppearanceMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { state.setAppearance(mode, onChange) }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        RadioButton(selected = state.appearance == mode) { state.setAppearance(mode, onChange) }
                        Spacer(Modifier.width(10.dp))
                        Text(mode.label, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("The desktop client mirrors the Android Aurora glass theme on every OS.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun RadioButton(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .border(
                2.dp,
                if (selected) AuroraPalette.AccentAlt else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
            .background(if (selected) AuroraPalette.AccentAlt else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {}
}

/** Desktop permission center — the OS-aware equivalent of the Android Permission Center. */
@Composable
fun PermissionsScreen(state: AppState, onChange: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavPill("← Home") { state.go(AppState.Route.Home, onChange) }
            Spacer(Modifier.width(12.dp))
            Currency("App permissions", "${if (PermissionCenter.isWindows()) "Windows" else "Linux"} · ${PermissionCenter.arch()}")
        }
        Spacer(Modifier.height(24.dp))
        val scroll = rememberScrollState()
        Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionCenter.permissions().forEach { perm ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(perm.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.weight(1f))
                            Text(
                                when (perm.status.name) {
                                    "GRANTED" -> "Allowed"
                                    "NOT_REQUIRED" -> "Not restricted on desktop"
                                    else -> "Manual step"
                                },
                                fontSize = 12.sp,
                                color = if (perm.status.name == "GRANTED") AuroraPalette.AccentAlt else Color(0xFFE8A33D)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Where: ${perm.howToGrant}", fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                        Text("Needed for: ${perm.requiredFor}", fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                        Text("Android equivalent: ${perm.androidEquivalent}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun formatTime(epochMs: Long): String =
    if (epochMs <= 0) "just now"
    else SimpleDateFormat("d MMM, h:mm a").format(Date(epochMs))
