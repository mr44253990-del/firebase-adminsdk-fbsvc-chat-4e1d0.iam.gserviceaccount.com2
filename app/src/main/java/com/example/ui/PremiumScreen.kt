package com.example.ui

import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.User
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(viewModel: ChatViewModel, onBack: () -> Unit, onChatAdmin: (User) -> Unit) {
    val context = LocalContext.current
    val config by viewModel.flagshipConfig.collectAsState()
    val me by viewModel.currentUserState.collectAsState()
    val users by viewModel.usersState.collectAsState()
    val activePremium = me?.isPremium == true && (me?.premiumPlan == "lifetime" || (me?.premiumUntil ?: 0L) > System.currentTimeMillis())
    var plan by remember { mutableStateOf("monthly") }
    var method by remember { mutableStateOf("bkash") }
    var transaction by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var proofUploading by remember { mutableStateOf(false) }
    var proofProgress by remember { mutableIntStateOf(0) }
    var proofUrl by remember { mutableStateOf("") }
    val premiumPrefs = remember { context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE) }
    var anonymousStories by remember { mutableStateOf(premiumPrefs.getBoolean("premium_anonymous_story", false)) }
    var customRingtoneSet by remember { mutableStateOf(premiumPrefs.getString("premium_ringtone_uri", null) != null) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.getSharedPreferences("firechat_prefs", Context.MODE_PRIVATE).edit().putString("premium_ringtone_uri", uri.toString()).apply()
        customRingtoneSet = true
        Toast.makeText(context, "Premium incoming-call ringtone saved", Toast.LENGTH_LONG).show()
    }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) size = it.getLong(0) }
        proofUploading = true; proofProgress = 0
        viewModel.uploadUriToSupabase(uri, "premium_proof_${System.currentTimeMillis()}.jpg", context.contentResolver.getType(uri) ?: "image/jpeg", size,
            onProgress = { p, _ -> proofProgress = p },
            onSuccess = { proofUploading = false; proofUrl = it },
            onFailure = { proofUploading = false; Toast.makeText(context, it, Toast.LENGTH_LONG).show() })
    }
    val plans = buildList {
        if (config.premiumMonthlyEnabled) add(Triple("monthly", "মাসিক", config.premiumMonthlyPrice))
        if (config.premiumYearlyEnabled) add(Triple("yearly", "বার্ষিক", config.premiumYearlyPrice))
        if (config.premiumLifetimeEnabled) add(Triple("lifetime", "লাইফটাইম", config.premiumLifetimePrice))
    }
    val methods = buildList {
        if (config.premiumBkashEnabled) add("bkash" to "বিকাশ")
        if (config.premiumNagadEnabled) add("nagad" to "নগদ")
        if (config.premiumRocketEnabled) add("rocket" to "রকেট")
    }
    val premiumBenefits = listOf(
        "✅ প্রোফাইল ও চ্যাটে Premium verified badge",
        "🤖 AI Assistant-এ অগ্রাধিকার ও উন্নত সহায়তা",
        "💬 সরাসরি Admin support chat",
        "🔝 People search ও friends তালিকায় অগ্রাধিকার",
        "📊 Story rewatch ও উন্নত viewer insights-এর entitlement",
        "❤️ Animated Super Heart ও Premium reactions-এর entitlement",
        "🎨 অতিরিক্ত theme, font, sticker ও customization entitlement",
        "📌 বেশি chat/post pin এবং profile-only publishing entitlement",
        "🎵 Custom ringtone/ringback entitlement",
        "📹 সম্মতিসহ call recording ও screenshot entitlement"
    )
    val premiumMotion = rememberInfiniteTransition(label = "premium_hero_motion")
    val heroGlow by premiumMotion.animateFloat(
        initialValue = .20f,
        targetValue = .58f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "premium_hero_glow"
    )
    LaunchedEffect(methods) { if (methods.none { it.first == method }) method = methods.firstOrNull()?.first.orEmpty() }
    LaunchedEffect(plans) { if (plans.none { it.first == plan }) plan = plans.firstOrNull()?.first.orEmpty() }

    Scaffold(topBar = { TopAppBar(title = { Text("Convo Chat Premium", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(30.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF6B4EFF), Color(0xFFE64DAB), Color(0xFFFFA726))))
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(heroGlow)
                                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = .64f), Color.Transparent)))
                        )
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(58.dp))
                            Text(if (activePremium) "Premium Active ✨" else "আপনার Convo Chat আরও শক্তিশালী করুন", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            if (activePremium) {
                                val expiry = if (me?.premiumPlan == "lifetime") "Lifetime" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(me?.premiumUntil ?: 0L))
                                Text("${me?.premiumPlan?.uppercase()} • $expiry পর্যন্ত", color = Color.White.copy(.9f))
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🌟 Premium সুবিধাসমূহ", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        premiumBenefits.forEachIndexed { index, benefit ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(420, delayMillis = index * 55)) +
                                    slideInHorizontally(tween(420, delayMillis = index * 55), initialOffsetX = { it / 3 })
                            ) {
                                Text(benefit, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            if (activePremium) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = { users.find { it.role == "moderator" || it.username.equals("admin", true) }?.let(onChatAdmin) ?: Toast.makeText(context, "Admin profile is not available", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.SupportAgent, null); Spacer(Modifier.width(8.dp)); Text("সরাসরি Admin-এর সাথে Chat")
                        }
                        OutlinedButton(onClick = { ringtonePicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.MusicNote, null); Spacer(Modifier.width(8.dp)); Text(if (customRingtoneSet) "Change custom call ringtone" else "Set custom call ringtone")
                        }
                        if (customRingtoneSet) TextButton(onClick = { premiumPrefs.edit().remove("premium_ringtone_uri").apply(); customRingtoneSet = false }, modifier = Modifier.fillMaxWidth()) { Text("Use device default ringtone") }
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text("Anonymous story preview", fontWeight = FontWeight.Bold); Text("Your name will not be added to the viewer list", fontSize = 10.sp) }
                                Switch(anonymousStories, { anonymousStories = it; premiumPrefs.edit().putBoolean("premium_anonymous_story", it).apply() })
                            }
                        }
                    }
                }
            } else if (config.premiumEnabled) {
                item {
                    Card(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("১. একটি Plan নির্বাচন করুন", fontWeight = FontWeight.ExtraBold)
                            plans.forEach { (id, label, price) ->
                                FilterChip(selected = plan == id, onClick = { plan = id }, label = { Text("$label • ৳$price") }, leadingIcon = { Icon(if (plan == id) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null) })
                            }
                            HorizontalDivider()
                            Text("২. Payment method", fontWeight = FontWeight.ExtraBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Send Money: ${config.premiumPaymentNumber}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Payment number", config.premiumPaymentNumber))
                                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, "Copy number") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                methods.forEach { (id, label) -> FilterChip(selected = method == id, onClick = { method = id }, label = { Text(label) }) }
                            }
                            OutlinedTextField(transaction, { transaction = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) }, label = { Text("Transaction ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { proofPicker.launch("image/*") }, enabled = !proofUploading, modifier = Modifier.fillMaxWidth()) {
                                Icon(if (proofUrl.isBlank()) Icons.Default.AddPhotoAlternate else Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp))
                                Text(when { proofUploading -> "Uploading proof • $proofProgress%"; proofUrl.isNotBlank() -> "Payment screenshot attached"; else -> "Payment screenshot (optional)" })
                            }
                            Button(onClick = {
                                sending = true
                                viewModel.submitPremiumRequest(plan, method, transaction, proofUrl) { ok, message ->
                                    sending = false; Toast.makeText(context, if (ok) "$message • Admin approval pending" else message, Toast.LENGTH_LONG).show(); if (ok) { transaction = ""; proofUrl = "" }
                                }
                            }, enabled = !sending && !proofUploading && transaction.length >= 6 && method.isNotBlank() && plan.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, null)
                                Spacer(Modifier.width(8.dp)); Text("Request Premium")
                            }
                            Text("⚠️ টাকা পাঠানোর তথ্য Admin যাচাই করবেন। অনুমোদনের আগে Premium চালু হবে না। একই Transaction ID পুনরায় ব্যবহার করবেন না।", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else item { Text("Premium purchase বর্তমানে Admin বন্ধ রেখেছেন।", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        }
    }
}
