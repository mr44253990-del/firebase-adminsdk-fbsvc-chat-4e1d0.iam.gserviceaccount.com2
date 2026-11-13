package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuroraCyan
import com.example.ui.theme.AuroraPink
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.SuccessGreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private data class BroadcastEntry(val id: String, val text: String, val sentAt: Long, val type: String)
private data class AnalyticsCounters(val users: Long?, val onlineNow: Long?, val messagesToday: Long?)

/**
 * AdminAdvancedControls — admin → Advanced controls.
 *
 * High-impact admin actions wired as verified Firestore writes, mirroring the
 * executeAdminCommand pattern in AdminAiControlScreen. Includes: ban user,
 * wipe user posts (via worker command doc), force-logout (forceLogoutAt stamp
 * the client observes), min app version, feature flags, broadcast history,
 * and analytics cards (ShimmerBox placeholder when counters are absent).
 */
@Composable
fun AdminAdvancedControls(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetUid by remember { mutableStateOf("") }
    var minVersion by remember { mutableStateOf("1") }
    var minEnforced by remember { mutableStateOf(true) }
    var flagKey by remember { mutableStateOf("") }
    var flagValue by remember { mutableStateOf("true") }
    var busy by remember { mutableStateOf(false) }
    var broadcasts by remember { mutableStateOf<List<BroadcastEntry>?>(null) }
    var counters by remember { mutableStateOf<AnalyticsCounters?>(null) }
    var countersLoading by remember { mutableStateOf(true) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    LaunchedEffect(Unit) {
        runCatching {
            val db = FirebaseFirestore.getInstance()
            val bc = db.collection("adminBroadcasts").orderBy("sentAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(20).get().await()
            broadcasts = bc.documents.mapNotNull { doc ->
                val text = doc.getString("text") ?: return@mapNotNull null
                BroadcastEntry(doc.id, text, doc.getLong("sentAt") ?: 0L, doc.getString("type") ?: "admin_broadcast")
            }
            val snap = db.collection("analytics").document("counters").get().await()
            counters = AnalyticsCounters(
                users = snap.getLong("userCount"),
                onlineNow = snap.getLong("onlineNow"),
                messagesToday = snap.getLong("messagesToday")
            )
        }
        countersLoading = false
    }

    suspend fun runAction(label: String, block: suspend () -> String) {
        busy = true
        val result = withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { "⚠️ Failed: ${it.message}" } }
        busy = false
        toast(result)
    }

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    NeonText("Advanced Controls", style = androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp), glow = AuroraPink)
                    Text("High-impact admin actions · verified writes", color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
                if (busy) GlowDot(color = AuroraCyan)
            }

            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---- Target user actions ----
                item {
                    GlassCard {
                        Text("Target user actions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Target UID (or username):", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (targetUid.isEmpty()) Text("paste a user UID…", color = Color.White.copy(alpha = .4f), fontSize = 13.sp)
                            BasicTextField(
                                value = targetUid, onValueChange = { targetUid = it.take(120) }, enabled = !busy,
                                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(AuroraCyan), modifier = Modifier.fillMaxWidth(), maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradientButton(onClick = {
                                val uid = targetUid.trim()
                                if (uid.isBlank()) { toast("Enter a target UID first"); return@GradientButton }
                                scope.launch {
                                    runAction("ban") {
                                        val db = FirebaseFirestore.getInstance()
                                        val now = System.currentTimeMillis()
                                        val admin = FirebaseAuth.getInstance().currentUser?.uid ?: "admin"
                                        db.collection("users").document(uid).set(
                                            mapOf("banned" to true, "bannedAt" to now, "bannedBy" to admin, "forceLogoutAt" to now),
                                            com.google.firebase.firestore.SetOptions.merge()
                                        ).await()
                                        "✅ User $uid banned + force-logged out"
                                    }
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Block, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Ban user", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            GradientButton(onClick = {
                                val uid = targetUid.trim()
                                if (uid.isBlank()) { toast("Enter a target UID first"); return@GradientButton }
                                scope.launch {
                                    runAction("wipe") {
                                        val db = FirebaseFirestore.getInstance()
                                        val id = db.collection("adminCommands").document().id
                                        db.collection("adminCommands").document(id).set(mapOf(
                                            "type" to "wipe_user_posts", "targetUid" to uid,
                                            "requestedAt" to System.currentTimeMillis(),
                                            "requestedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "admin")
                                        )).await()
                                        "✅ Wipe queued ($id) — worker will delete this user's posts"
                                    }
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DeleteForever, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Wipe posts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GradientButton(onClick = {
                            val uid = targetUid.trim()
                            if (uid.isBlank()) { toast("Enter a target UID first"); return@GradientButton }
                            scope.launch {
                                runAction("logout") {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("users").document(uid).set(
                                        mapOf("forceLogoutAt" to System.currentTimeMillis()),
                                        com.google.firebase.firestore.SetOptions.merge()
                                    ).await()
                                    "✅ Force-logout stamp written — client will sign out on next resume"
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Logout, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Force logout user", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ---- Min app version ----
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Update, null, tint = AuroraCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Minimum app version", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Min versionCode (clients below are blocked):", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                            BasicTextField(
                                value = minVersion, onValueChange = { minVersion = it.filter { c -> c.isDigit() }.take(6) }, enabled = !busy,
                                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(AuroraCyan), modifier = Modifier.fillMaxWidth(), maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Switch(checked = minEnforced, onCheckedChange = { minEnforced = it }, enabled = !busy)
                            Spacer(Modifier.width(8.dp))
                            Text("Enforce (hard block)", color = if (minEnforced) SuccessGreen else Color.White.copy(alpha = .6f), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        GradientButton(onClick = {
                            val code = minVersion.toIntOrNull()
                            if (code == null || code <= 0) { toast("Enter a valid versionCode"); return@GradientButton }
                            scope.launch {
                                runAction("minversion") {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("config").document("appConfig").set(mapOf(
                                        "minVersionCode" to code, "minVersionEnforced" to minEnforced, "updatedAt" to System.currentTimeMillis()
                                    ), com.google.firebase.firestore.SetOptions.merge()).await()
                                    "✅ Min version set to $code (enforced=$minEnforced) — clients read on start"
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Save min version", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ---- Feature flags ----
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ToggleOn, null, tint = AuroraViolet, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Feature flags", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Stored at config/global.flags — clients merge on start.", color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                                if (flagKey.isEmpty()) Text("flag key e.g. reels_v2", color = Color.White.copy(alpha = .4f), fontSize = 12.sp)
                                BasicTextField(value = flagKey, onValueChange = { flagKey = it.take(40) }, enabled = !busy,
                                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(AuroraCyan), modifier = Modifier.fillMaxWidth(), maxLines = 1)
                            }
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                                BasicTextField(value = flagValue, onValueChange = { flagValue = it.take(20) }, enabled = !busy,
                                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(AuroraCyan), modifier = Modifier.fillMaxWidth(), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GradientButton(onClick = {
                            val key = flagKey.trim()
                            if (key.isBlank()) { toast("Enter a flag key"); return@GradientButton }
                            val value: Any = when (flagValue.trim().lowercase()) {
                                "true" -> true; "false" -> false
                                else -> flagValue.trim().toIntOrNull() ?: flagValue.trim()
                            }
                            scope.launch {
                                runAction("flag") {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("config").document("global").update("flags.$key", value).await()
                                    "✅ Flag $key = $value written"
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Set feature flag", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ---- Analytics cards ----
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, null, tint = AuroraPink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Analytics snapshot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.weight(1f))
                            GlowDot(color = AuroraCyan, size = 6.dp)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (countersLoading) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBox(Modifier.weight(1f).height(56.dp))
                                ShimmerBox(Modifier.weight(1f).height(56.dp))
                                ShimmerBox(Modifier.weight(1f).height(56.dp))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AnalyticsMiniCard("Users", counters?.users?.toString() ?: "—", AuroraViolet)
                                AnalyticsMiniCard("Online now", counters?.onlineNow?.toString() ?: "—", AuroraCyan)
                                AnalyticsMiniCard("Msgs today", counters?.messagesToday?.toString() ?: "—", AuroraPink)
                            }
                            if (counters?.users == null && counters?.onlineNow == null && counters?.messagesToday == null) {
                                Spacer(Modifier.height(6.dp))
                                Text("No counters doc yet (analytics/counters). Worker will populate.", color = Color.White.copy(alpha = .5f), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // ---- Broadcast history ----
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, null, tint = AuroraCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recent broadcasts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        val list = broadcasts
                        if (list == null) {
                            ShimmerBox(Modifier.fillMaxWidth().height(40.dp))
                            Spacer(Modifier.height(6.dp))
                            ShimmerBox(Modifier.fillMaxWidth().height(40.dp))
                        } else if (list.isEmpty()) {
                            Text("No broadcasts yet.", color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
                        } else {
                            list.take(8).forEach { entry ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                    GlowDot(color = AuroraCyan, size = 6.dp, modifier = Modifier.padding(top = 6.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.text, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
                                        Text(
                                            java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.sentAt)),
                                            color = Color.White.copy(alpha = .45f), fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AnalyticsMiniCard(label: String, value: String, accent: Color) {
    Box(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .07f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column {
            Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(label, color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
        }
    }
}
