package com.example.ui

import android.widget.Toast
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
    val plans = listOf(
        Triple("monthly", "মাসিক", config.premiumMonthlyPrice),
        Triple("yearly", "বার্ষিক", config.premiumYearlyPrice),
        Triple("lifetime", "লাইফটাইম", config.premiumLifetimePrice)
    )
    val methods = buildList {
        if (config.premiumBkashEnabled) add("bkash" to "বিকাশ")
        if (config.premiumNagadEnabled) add("nagad" to "নগদ")
        if (config.premiumRocketEnabled) add("rocket" to "রকেট")
    }
    LaunchedEffect(methods) { if (methods.none { it.first == method }) method = methods.firstOrNull()?.first.orEmpty() }

    Scaffold(topBar = { TopAppBar(title = { Text("FireChat Premium", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF6B4EFF), Color(0xFFE64DAB), Color(0xFFFFA726)))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(58.dp))
                        Text(if (activePremium) "Premium Active ✨" else "আপনার FireChat আরও শক্তিশালী করুন", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        if (activePremium) {
                            val expiry = if (me?.premiumPlan == "lifetime") "Lifetime" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(me?.premiumUntil ?: 0L))
                            Text("${me?.premiumPlan?.uppercase()} • $expiry পর্যন্ত", color = Color.White.copy(.9f))
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🌟 Premium সুবিধাসমূহ", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        listOf(
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
                        ).forEach { Text(it, fontSize = 13.sp) }
                    }
                }
            }
            if (activePremium) {
                item {
                    Button(onClick = { users.find { it.role == "moderator" || it.username.equals("admin", true) }?.let(onChatAdmin) ?: Toast.makeText(context, "Admin profile is not available", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.SupportAgent, null); Spacer(Modifier.width(8.dp)); Text("সরাসরি Admin-এর সাথে Chat")
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
                            Text("Send Money করুন: ${config.premiumPaymentNumber}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                methods.forEach { (id, label) -> FilterChip(selected = method == id, onClick = { method = id }, label = { Text(label) }) }
                            }
                            OutlinedTextField(transaction, { transaction = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) }, label = { Text("Transaction ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                sending = true
                                viewModel.submitPremiumRequest(plan, method, transaction) { ok, message ->
                                    sending = false; Toast.makeText(context, if (ok) "$message • Admin approval pending" else message, Toast.LENGTH_LONG).show(); if (ok) transaction = ""
                                }
                            }, enabled = !sending && transaction.length >= 6 && method.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
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
