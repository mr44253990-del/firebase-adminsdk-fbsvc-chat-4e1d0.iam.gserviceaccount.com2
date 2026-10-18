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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
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
    val premiumMotion = rememberInfiniteTransition(label = "premium_hero_motion")
    val heroGlow by premiumMotion.animateFloat(
        initialValue = .20f,
        targetValue = .58f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "premium_hero_glow"
    )
    LaunchedEffect(methods) { if (methods.none { it.first == method }) method = methods.firstOrNull()?.first.orEmpty() }
    LaunchedEffect(plans) { if (plans.none { it.first == plan }) plan = plans.firstOrNull()?.first.orEmpty() }

    val ink = MaterialTheme.colorScheme.onSurface
    var openFaq by remember { mutableIntStateOf(-1) }
    val monthlyEquivalentYearly = if (config.premiumYearlyPrice > 0) config.premiumYearlyPrice / 12 else 0
    val yearlySavePercent = if (config.premiumMonthlyPrice > 0 && config.premiumYearlyPrice > 0)
        (100 - (config.premiumYearlyPrice * 100) / (config.premiumMonthlyPrice * 12)).coerceAtLeast(0) else 0
    val step = when { plan.isBlank() -> 1; transaction.length < 6 -> 2; else -> 3 }

    Scaffold(modifier = Modifier.modernBackdrop(), containerColor = Color.Transparent, topBar = { TopAppBar(colors = modernTopBarColors(), title = { ModernTitle("Convo Chat Premium", if (activePremium) "Active" else "Unlock every feature") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PremiumHero(activePremium, me, heroGlow) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFC233), Color(0xFFFF7A59)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Diamond, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text("Premium সুবিধাসমূহ", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = ink)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PREMIUM_PERKS.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { i, perk -> PremiumPerkTile(perk, rowIndex * 2 + i, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item { PremiumComparisonCard() }
            if (activePremium) {
                item { PremiumMemberCard(me) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("আপনার Premium টুলস", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = ink)
                        PremiumActionCard(Icons.Default.SupportAgent, "সরাসরি Admin-এর সাথে Chat", "Priority support, usually within hours", Color(0xFF6B4EFF)) {
                            users.find { it.role == "moderator" || it.username.equals("admin", true) }?.let(onChatAdmin) ?: Toast.makeText(context, "Admin profile is not available", Toast.LENGTH_SHORT).show()
                        }
                        PremiumActionCard(Icons.Default.MusicNote, if (customRingtoneSet) "Change custom call ringtone" else "Set custom call ringtone", if (customRingtoneSet) "Custom tone active • tap to change" else "Use any song or tone for incoming calls", Color(0xFFE64DAB)) {
                            ringtonePicker.launch(arrayOf("audio/*"))
                        }
                        if (customRingtoneSet) TextButton(onClick = { premiumPrefs.edit().remove("premium_ringtone_uri").apply(); customRingtoneSet = false }, modifier = Modifier.fillMaxWidth()) { Text("Use device default ringtone") }
                        Card(shape = RoundedCornerShape(22.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF00ACC1).copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.VisibilityOff, null, tint = Color(0xFF00ACC1)) }
                                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Anonymous story preview", fontWeight = FontWeight.Bold); Text("Your name will not be added to the viewer list", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Switch(anonymousStories, { anonymousStories = it; premiumPrefs.edit().putBoolean("premium_anonymous_story", it).apply() })
                            }
                        }
                    }
                }
            } else if (config.premiumEnabled) {
                item { PremiumStepper(step) }
                item {
                    Text("১. একটি Plan নির্বাচন করুন", fontWeight = FontWeight.ExtraBold, color = ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        plans.forEach { (id, label, price) ->
                            PremiumPlanCard(
                                selected = plan == id,
                                title = label,
                                price = price,
                                caption = when (id) { "monthly" -> "প্রতি মাস"; "yearly" -> if (monthlyEquivalentYearly > 0) "≈ ৳$monthlyEquivalentYearly/মাস" else "প্রতি বছর"; else -> "একবার, চিরকাল" },
                                badge = when { id == "yearly" && yearlySavePercent > 0 -> "$yearlySavePercent% সাশ্রয়"; id == "lifetime" -> "Best value"; else -> null },
                                modifier = Modifier.weight(1f)
                            ) { plan = id }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("২. Payment পাঠান", fontWeight = FontWeight.ExtraBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                methods.forEach { (id, label) -> PaymentMethodChip(id, label, method == id, Modifier.weight(1f)) { method = id } }
                            }
                            val brand = paymentBrand(method)
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background(Brush.linearGradient(listOf(brand, brand.copy(alpha = .72f))))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Send Money • ${methods.firstOrNull { it.first == method }?.second.orEmpty()}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(config.premiumPaymentNumber, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Payment number", config.premiumPaymentNumber))
                                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                        }) { Icon(Icons.Default.ContentCopy, "Copy number", tint = Color.White) }
                                    }
                                    val amount = plans.firstOrNull { it.first == plan }?.third ?: 0
                                    Text("Amount: ৳$amount", color = Color.White, fontWeight = FontWeight.Bold)
                                    ussdFor(method)?.let { code ->
                                        Surface(onClick = {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .22f)) {
                                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Dialpad, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp)); Text("Dial $code", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text("৩. নিশ্চিত করুন", fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(transaction, { transaction = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) }, label = { Text("Transaction ID") }, leadingIcon = { Icon(Icons.Default.Receipt, null) }, supportingText = { Text(if (transaction.isNotEmpty() && transaction.length < 6) "কমপক্ষে ৬ অক্ষর" else "SMS-এ পাওয়া TrxID লিখুন") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { proofPicker.launch("image/*") }, enabled = !proofUploading, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Icon(if (proofUrl.isBlank()) Icons.Default.AddPhotoAlternate else Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp))
                                Text(when { proofUploading -> "Uploading proof • $proofProgress%"; proofUrl.isNotBlank() -> "Payment screenshot attached"; else -> "Payment screenshot (optional)" })
                            }
                            if (proofUploading) LinearProgressIndicator(progress = { proofProgress / 100f }, modifier = Modifier.fillMaxWidth())
                            val canSend = !sending && !proofUploading && transaction.length >= 6 && method.isNotBlank() && plan.isNotBlank()
                            Box(
                                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (canSend) Brush.horizontalGradient(listOf(Color(0xFF6B4EFF), Color(0xFFE64DAB), Color(0xFFFFA726))) else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = .35f), Color.Gray.copy(alpha = .35f))))
                                    .clickable(enabled = canSend) {
                                        sending = true
                                        viewModel.submitPremiumRequest(plan, method, transaction, proofUrl) { ok, message ->
                                            sending = false; Toast.makeText(context, if (ok) "$message • Admin approval pending" else message, Toast.LENGTH_LONG).show(); if (ok) { transaction = ""; proofUrl = "" }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.WorkspacePremium, null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp)); Text("Request Premium", color = Color.White, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("টাকা পাঠানোর তথ্য Admin যাচাই করবেন। অনুমোদনের আগে Premium চালু হবে না। একই Transaction ID পুনরায় ব্যবহার করবেন না।", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else item { Text("Premium purchase বর্তমানে Admin বন্ধ রেখেছেন।", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            item {
                Text("সাধারণ প্রশ্ন", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = ink)
                Spacer(Modifier.height(8.dp))
                PREMIUM_FAQ.forEachIndexed { index, (q, a) ->
                    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { openFaq = if (openFaq == index) -1 else index }) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(q, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Icon(if (openFaq == index) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            AnimatedVisibility(openFaq == index) { Text(a, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private data class PremiumPerk(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val detail: String, val color: Color)

private val PREMIUM_PERKS = listOf(
    PremiumPerk(Icons.Default.Verified, "Verified badge", "প্রোফাইল ও চ্যাটে Premium ব্যাজ", Color(0xFF2F80ED)),
    PremiumPerk(Icons.Default.SmartToy, "AI অগ্রাধিকার", "AI Assistant-এ দ্রুত ও উন্নত সহায়তা", Color(0xFF7C4DFF)),
    PremiumPerk(Icons.Default.SupportAgent, "Admin support", "সরাসরি Admin-এর সাথে চ্যাট", Color(0xFF00ACC1)),
    PremiumPerk(Icons.Default.TrendingUp, "Search boost", "People search ও friends-এ অগ্রাধিকার", Color(0xFF43A047)),
    PremiumPerk(Icons.Default.Insights, "Story insights", "Rewatch ও উন্নত viewer তথ্য", Color(0xFFFB8C00)),
    PremiumPerk(Icons.Default.Favorite, "Super reactions", "Animated Super Heart ও প্রিমিয়াম রিঅ্যাকশন", Color(0xFFE53935)),
    PremiumPerk(Icons.Default.Palette, "Customization", "অতিরিক্ত theme, font ও sticker", Color(0xFFD81B60)),
    PremiumPerk(Icons.Default.PushPin, "More pins", "বেশি chat/post pin, profile-only post", Color(0xFF5E35B1)),
    PremiumPerk(Icons.Default.MusicNote, "Custom ringtone", "নিজের ringtone/ringback", Color(0xFF00897B)),
    PremiumPerk(Icons.Default.Videocam, "Call recording", "সম্মতিসহ call recording ও screenshot", Color(0xFF546E7A)),
    PremiumPerk(Icons.Default.Wallpaper, "Exclusive looks", "Premium chat theme ও animated cover", Color(0xFF8E24AA)),
    PremiumPerk(Icons.Default.Bolt, "Fast lane", "Upload ও AI request-এ অগ্রাধিকার", Color(0xFFF4511E))
)

private val PREMIUM_FAQ = listOf(
    "কতক্ষণে Premium চালু হয়?" to "Transaction ID জমা দেওয়ার পর Admin যাচাই করে অনুমোদন দিলেই চালু হয় — সাধারণত কয়েক ঘণ্টার মধ্যে। চালু হলে notification পাবেন।",
    "ভুল Transaction ID দিলে কী হবে?" to "Request বাতিল হতে পারে। সঠিক TrxID দিয়ে আবার request করুন অথবা Admin-কে message করুন।",
    "মেয়াদ শেষ হলে কী হয়?" to "মাসিক/বার্ষিক plan শেষ হলে Premium ফিচার বন্ধ হয়ে যায়; আপনার চ্যাট ও ডেটা সব ঠিক থাকে। Lifetime কখনো শেষ হয় না।"
)

private fun paymentBrand(method: String): Color = when (method) {
    "bkash" -> Color(0xFFE2136E)
    "nagad" -> Color(0xFFF7941D)
    "rocket" -> Color(0xFF8C3494)
    else -> Color(0xFF6B4EFF)
}

private fun ussdFor(method: String): String? = when (method) {
    "bkash" -> "*247#"
    "nagad" -> "*167#"
    "rocket" -> "*322#"
    else -> null
}

@Composable
private fun PremiumHero(activePremium: Boolean, me: User?, glow: Float) {
    val motion = rememberInfiniteTransition(label = "premium_hero")
    val shift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "hero_shift")
    val float by motion.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "crown_float")
    val sparkle by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "sparkles")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3D2BB8), Color(0xFF6B4EFF), Color(0xFFE64DAB), Color(0xFFFFA726)), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(900f * (0.6f + shift), 700f)))
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow * .7f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .15f), radius = size.width * .55f), size.width * .55f, androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .15f))
            repeat(18) { i ->
                val seed = (i * 37 % 100) / 100f
                val p = (sparkle + seed) % 1f
                val x = size.width * ((seed * 1.9f + i * .05f) % 1f)
                val y = size.height * (1f - p)
                drawCircle(Color.White.copy(alpha = (kotlin.math.sin(p * 3.1416f) * .7f).coerceAtLeast(0f)), radius = 1.6f + (i % 3) * 1.4f, center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumGem(Modifier.size(86.dp).offset(y = float.dp), sparkle)
            Text(if (activePremium) "Premium Active" else "আপনার Convo Chat আরও শক্তিশালী করুন", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            if (activePremium) {
                val lifetime = me?.premiumPlan == "lifetime"
                val expiry = if (lifetime) "Lifetime" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(me?.premiumUntil ?: 0L))
                Text("${me?.premiumPlan?.uppercase()} • $expiry পর্যন্ত", color = Color.White.copy(.92f))
                if (!lifetime) {
                    val left = (((me?.premiumUntil ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 86_400_000L).toInt() + 1
                    val total = when (me?.premiumPlan) { "yearly" -> 365; "monthly" -> 30; else -> maxOf(left, 30) }
                    LinearProgressIndicator(progress = { (left.toFloat() / total).coerceIn(0f, 1f) }, color = Color.White, trackColor = Color.White.copy(alpha = .25f), modifier = Modifier.fillMaxWidth(.8f).height(6.dp).clip(RoundedCornerShape(50)))
                    Text("$left দিন বাকি", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text("Verified badge, AI priority, custom ringtone ও আরও ১০টি সুবিধা", color = Color.White.copy(alpha = .9f), textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PremiumPerkTile(perk: PremiumPerk, index: Int, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 45L); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(380), label = "perk_alpha")
    val lift by animateFloatAsState(if (shown) 0f else 18f, tween(420, easing = FastOutSlowInEasing), label = "perk_lift")
    Card(shape = RoundedCornerShape(20.dp), modifier = modifier.alpha(alpha).offset(y = lift.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(perk.color, perk.color.copy(alpha = .62f)))), contentAlignment = Alignment.Center) { Icon(perk.icon, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
            Text(perk.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(perk.detail, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun PremiumStepper(step: Int) {
    val labels = listOf("Plan", "Payment", "Confirm")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val done = step > i + 1; val current = step == i + 1
            val color by animateColorAsState(if (done || current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "step_color")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    else Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(label, fontSize = 11.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
            }
            if (i < labels.lastIndex) Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(3.dp).clip(RoundedCornerShape(50)).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
private fun PremiumPlanCard(selected: Boolean, title: String, price: Int, caption: String, badge: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.03f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "plan_scale")
    val border = if (selected) Brush.linearGradient(listOf(Color(0xFF6B4EFF), Color(0xFFE64DAB))) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
    Box(modifier.scale(scale)) {
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(22.dp))
                .border(if (selected) 2.5.dp else 1.dp, border, RoundedCornerShape(22.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("৳$price", fontWeight = FontWeight.Black, fontSize = 22.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(caption, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (badge != null) Text(
            badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFFF6D00), Color(0xFFE64DAB)))).padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PaymentMethodChip(id: String, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val brand = paymentBrand(id)
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = if (selected) brand else brand.copy(alpha = .1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, brand.copy(alpha = if (selected) 1f else .4f))
    ) {
        Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (selected) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
            Text(label, color = if (selected) Color.White else brand, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PremiumActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(color, color.copy(alpha = .65f)))), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Coded crown-and-gem emblem (no emoji): gradient crown, faceted gem and a moving shine. */
@Composable
private fun PremiumGem(modifier: Modifier = Modifier, shine: Float) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(w / 2, h / 2), radius = w * .62f)
        drawCircle(glow, w * .62f, androidx.compose.ui.geometry.Offset(w / 2, h / 2))
        val crown = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .12f, h * .72f); lineTo(w * .08f, h * .28f); lineTo(w * .32f, h * .48f); lineTo(w * .5f, h * .16f)
            lineTo(w * .68f, h * .48f); lineTo(w * .92f, h * .28f); lineTo(w * .88f, h * .72f); close()
        }
        drawPath(crown, Brush.linearGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFFC233), Color(0xFFFF8F00)), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, h)))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFFE082), Color(0xFFFFB300))), topLeft = androidx.compose.ui.geometry.Offset(w * .12f, h * .72f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .04f))
        listOf(.08f to .28f, .5f to .16f, .92f to .28f).forEach { (fx, fy) -> drawCircle(Color.White, w * .045f, androidx.compose.ui.geometry.Offset(w * fx, h * fy)) }
        val gem = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .5f, h * .44f); lineTo(w * .6f, h * .54f); lineTo(w * .5f, h * .68f); lineTo(w * .4f, h * .54f); close()
        }
        drawPath(gem, Brush.linearGradient(listOf(Color(0xFFE1BEE7), Color(0xFF7C4DFF), Color(0xFF00E5FF))))
        // Shine sweep.
        val x = w * (-.3f + shine * 1.6f)
        drawLine(Color.White.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(x, h * .2f), androidx.compose.ui.geometry.Offset(x + w * .18f, h * .85f), strokeWidth = w * .05f)
    }
}

@Composable
private fun PremiumComparisonCard() {
    val rows = listOf(
        Triple("Chat, calls, stories", true, true), Triple("Verified Premium badge", false, true),
        Triple("Premium chat themes & covers", false, true), Triple("AI priority & smart tools", false, true),
        Triple("Custom call ringtone", false, true), Triple("Anonymous story preview", false, true),
        Triple("Direct Admin support", false, true)
    )
    Card(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Free vs Premium", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("Free", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                Text("Premium", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00), modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
            }
            rows.forEach { (label, free, premium) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                        Icon(if (free) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline, null, tint = if (free) Color(0xFF43A047) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                        Icon(if (premium) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline, null, tint = Color(0xFFFF8F00), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Glossy membership card shown to active Premium users. */
@Composable
private fun PremiumMemberCard(me: User?) {
    val motion = rememberInfiniteTransition(label = "member_card")
    val sweep by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "member_sweep")
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.72f).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1033), Color(0xFF3A1C71), Color(0xFF0F3B5F))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFF7AB6), Color(0xFF7FDBFF))), RoundedCornerShape(26.dp))
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val x = size.width * (-.4f + sweep * 1.8f)
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .14f), Color.Transparent), start = androidx.compose.ui.geometry.Offset(x - 120f, 0f), end = androidx.compose.ui.geometry.Offset(x + 120f, size.height)))
            drawCircle(Color(0xFFFFC233).copy(alpha = .16f), size.height * .7f, androidx.compose.ui.geometry.Offset(size.width * .95f, size.height * .05f))
        }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumGem(Modifier.size(34.dp), sweep)
                Spacer(Modifier.width(8.dp))
                Text("CONVO PREMIUM", color = Color(0xFFFFE082), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(me?.name.orEmpty().ifBlank { "Member" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("@" + me?.username.orEmpty(), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("PLAN", color = Color.White.copy(alpha = .55f), fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text(me?.premiumPlan.orEmpty().uppercase().ifBlank { "PREMIUM" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VALID UNTIL", color = Color.White.copy(alpha = .55f), fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text(if (me?.premiumPlan == "lifetime") "LIFETIME" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(me?.premiumUntil ?: 0L)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
