package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val WelcomeGradientStart = Color(0xFF0B84F3)
private val WelcomeGradientEnd = Color(0xFF6D28D9)

@Composable
fun AppVersionWelcomeDialog(
    versionName: String,
    onStartTour: () -> Unit,
    onReadGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val language by AppLanguage.code.collectAsState()
    val bn = language == "bn"
    val updates = remember(language) {
        val asset = if (bn) "app-update-bn.txt" else "app-update-en.txt"
        runCatching { context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readLines().filter(String::isNotBlank) } }
            .getOrElse { emptyList() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                /* ---- Gradient hero header ---- */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(WelcomeGradientStart, WelcomeGradientEnd)))
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    if (bn) "নতুন আপডেট এসেছে" else "A fresh update is here",
                                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = .22f))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Convo Chat · v$versionName",
                                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                /* ---- What's new list ---- */
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (bn) "এই আপডেটে যা যা এসেছে" else "What’s new in this release",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    updates.forEachIndexed { index, line ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(WelcomeGradientStart.copy(alpha = .15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = WelcomeGradientStart, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                line.removePrefix("• "),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    /* ---- Tour call-out ---- */
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(WelcomeGradientStart.copy(alpha = .12f), WelcomeGradientEnd.copy(alpha = .12f))))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = WelcomeGradientEnd, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (bn) "পুরো অ্যাপটি ঘুরে দেখতে চান? মাত্র কয়েক মিনিটের ট্যুর সব শিখিয়ে দেবে।"
                            else "Want to see the whole app? A short tour shows you everything in minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp
                        )
                    }
                }

                /* ---- Actions ---- */
                Column(
                    Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartTour,
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WelcomeGradientStart),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (bn) "পুরো অ্যাপের ট্যুর দেখুন" else "Take the app tour", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = onReadGuide,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (bn) "অফলাইন গাইড পড়ুন" else "Read the offline guide", fontSize = 13.sp)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (bn) "এখন নয়, পরে দেখব" else "Maybe later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
