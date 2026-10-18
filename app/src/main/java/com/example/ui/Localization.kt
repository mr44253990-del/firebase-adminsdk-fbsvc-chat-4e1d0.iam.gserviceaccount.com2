package com.example.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLanguageOption(val code: String, val nativeName: String, val englishName: String, val flag: String, val sample: String)

/**
 * In-app language. The UI strings live in Kotlin (not resources), so translation goes through
 * [tr]; screens that call it switch instantly without restarting the app.
 */
object AppLanguage {
    private const val PREFS = "convo_language"
    val options = listOf(
        AppLanguageOption("en", "English", "English", "EN", "Hi! Ready to chat?"),
        AppLanguageOption("bn", "বাংলা", "Bengali", "বা", "হাই! চ্যাট করতে প্রস্তুত?")
    )
    private val _code = MutableStateFlow("en")
    val code: StateFlow<String> = _code.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _code.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("code", "en") ?: "en"
    }

    fun hasChosen(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("chosen", false)

    fun set(context: Context, code: String) {
        val clean = if (options.any { it.code == code }) code else "en"
        _code.value = clean
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("code", clean).putBoolean("chosen", true).apply()
    }

    fun translate(code: String, english: String): String =
        if (code == "bn") BENGALI[english] ?: english else english
}

/** Translate a UI string into the selected app language (falls back to English). */
@Composable
fun tr(english: String): String {
    val code by AppLanguage.code.collectAsState()
    return AppLanguage.translate(code, english)
}

/** Non-composable variant (toasts, notifications). */
fun trNow(english: String): String = AppLanguage.translate(AppLanguage.code.value, english)

/** Beautiful first-launch picker; also used from Settings → Language. */
@Composable
fun LanguagePickerScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLanguage.load(context) }
    val current by AppLanguage.code.collectAsState()
    var selected by remember { mutableStateOf(current) }
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF1A1145), Color(0xFF3A1C71), Color(0xFF0F3B5F)))
        )
    ) {
        // Soft glow blobs.
        Box(Modifier.size(260.dp).offset((-60).dp, (-40).dp).clip(CircleShape).background(Color(0xFFFF4FD8).copy(alpha = .18f)))
        Box(Modifier.size(300.dp).align(Alignment.BottomEnd).offset(80.dp, 60.dp).clip(CircleShape).background(Color(0xFF3DD6FF).copy(alpha = .16f)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)
                .graphicsLayer { alpha = entrance.value; translationY = (1f - entrance.value) * 60f },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF4FD8)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Translate, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.height(22.dp))
            Text("Choose your language", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("আপনার ভাষা বেছে নিন", color = Color.White.copy(alpha = .85f), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("You can change it any time in Settings → Language", color = Color.White.copy(alpha = .65f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            AppLanguage.options.forEach { option ->
                val isSel = option.code == selected
                val scale by animateFloatAsState(if (isSel) 1.03f else 1f, label = "lang_scale")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSel) Color.White.copy(alpha = .20f) else Color.White.copy(alpha = .08f))
                        .border(if (isSel) 2.dp else 1.dp, if (isSel) Color(0xFFFFD36E) else Color.White.copy(alpha = .18f), RoundedCornerShape(24.dp))
                        .clickable { selected = option.code; AppLanguage.set(context, option.code) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageBadge(option.flag, isSel, 52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.nativeName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(option.sample, color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                    }
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(if (isSel) Color(0xFFFFD36E) else Color.Transparent)
                            .border(2.dp, if (isSel) Color(0xFFFFD36E) else Color.White.copy(alpha = .4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { if (isSel) Icon(Icons.Default.Check, null, tint = Color(0xFF1A1145), modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = { AppLanguage.set(context, selected); onDone() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2A1660))
            ) { Text(if (selected == "bn") "চালিয়ে যান" else "Continue", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            Spacer(Modifier.height(12.dp))
            Text(
                if (selected == "bn") "মূল স্ক্রিনগুলো বাংলায় দেখাবে; কিছু উন্নত/অ্যাডমিন অংশ এখনো ইংরেজিতে।"
                else "Main screens are translated; some advanced/admin parts stay in English.",
                color = Color.White.copy(alpha = .55f), fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LanguageBadge(text: String, selected: Boolean, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 3).dp))
            .background(Brush.linearGradient(if (selected) listOf(Color(0xFFFFD36E), Color(0xFFFF7AB6)) else listOf(Color(0xFF7C5CFF), Color(0xFF3DD6FF))))
            .border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape((sizeDp / 3).dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (sizeDp / 2.6f).sp) }
}

/** Compact list for Settings → Language. */
@Composable
fun LanguageSettingsPage() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppLanguage.load(context) }
    val current by AppLanguage.code.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppLanguage.options.forEach { option ->
            val isSel = option.code == current
            Surface(
                onClick = { AppLanguage.set(context, option.code) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = if (isSel) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LanguageBadge(option.flag, isSel, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.nativeName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(option.englishName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isSel) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(tr("Main screens are translated; some advanced/admin parts stay in English."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val BENGALI: Map<String, String> = mapOf(
    // Settings hub (extra)
    "Account & messages" to "অ্যাকাউন্ট ও মেসেজ", "Choose the look of Convo Chat" to "Convo Chat-এর লুক বেছে নিন",
    "Convo Chat Premium" to "Convo Chat প্রিমিয়াম", "Default background, photo backgrounds and themes" to "ডিফল্ট ব্যাকগ্রাউন্ড, ছবি ও থিম",
    "Download my data" to "আমার ডেটা ডাউনলোড", "Edit your photo, bio and account information" to "ছবি, বায়ো ও অ্যাকাউন্টের তথ্য এডিট করুন",
    "Export your profile and posts as JSON" to "প্রোফাইল ও পোস্ট JSON হিসেবে এক্সপোর্ট", "Live preview & style" to "লাইভ প্রিভিউ ও স্টাইল",
    "Manage who can contact you" to "কে যোগাযোগ করতে পারবে ঠিক করুন", "Messages" to "মেসেজ", "More for chats" to "চ্যাটের জন্য আরও",
    "Open conversations and message requests" to "কথোপকথন ও মেসেজ রিকোয়েস্ট", "Personalization" to "পার্সোনালাইজেশন",
    "Pick a style for audio and video calls" to "অডিও ও ভিডিও কলের স্টাইল", "Plans and exclusive features" to "প্ল্যান ও বিশেষ ফিচার",
    "Privacy" to "প্রাইভেসি", "QR & contacts" to "QR ও কন্টাক্ট", "Quick reaction" to "দ্রুত রিঅ্যাকশন",
    "Quick reaction, text size, Enter to send, link previews" to "দ্রুত রিঅ্যাকশন, লেখার সাইজ, Enter-এ পাঠান, লিংক প্রিভিউ",
    "Reading & typing" to "পড়া ও টাইপিং", "Share your profile or connect with friends" to "প্রোফাইল শেয়ার বা বন্ধু যোগ করুন",
    "Manage account" to "অ্যাকাউন্ট ম্যানেজ",
    // Onboarding
    "Get started" to "শুরু করুন",
    "Make every conversation feel alive" to "প্রতিটি কথোপকথন হোক প্রাণবন্ত",
    "Fast messaging, voice notes, delivery ticks, smart replies and typing indicators — chat that feels instant." to "দ্রুত মেসেজ, ভয়েস নোট, ডেলিভারি টিক, স্মার্ট রিপ্লাই ও টাইপিং ইন্ডিকেটর — চ্যাট হবে মুহূর্তেই।",
    "HD calls that just work" to "ঝামেলাহীন HD কল",
    "Crystal-clear audio and video calls with swipe-to-answer, call reactions, screen share and picture-in-picture." to "পরিষ্কার অডিও ও ভিডিও কল — সোয়াইপে রিসিভ, কল রিঅ্যাকশন, স্ক্রিন শেয়ার ও ছোট উইন্ডো।",
    "Your story, your creativity" to "আপনার গল্প, আপনার সৃজনশীলতা",
    "Stories, posts, reels and polls with reactions and comments. Share moments with friends in style." to "স্টোরি, পোস্ট, রিলস ও পোল — রিঅ্যাকশন ও কমেন্টসহ। বন্ধুদের সাথে মুহূর্ত শেয়ার করুন।",
    "Security stays in your control" to "নিরাপত্তা থাকবে আপনার হাতে",
    "Two-step verification, backup codes, new-device alerts, app lock and screenshot-protected chats." to "২-ধাপ যাচাই, ব্যাকআপ কোড, নতুন ডিভাইস অ্যালার্ট, অ্যাপ লক ও স্ক্রিনশট-সুরক্ষিত চ্যাট।",
    "Welcome to Convo Chat" to "Convo Chat-এ স্বাগতম",
    "Smart AI help, beautiful themes and a friendly community. Let's set up your account in seconds." to "স্মার্ট AI সহায়তা, সুন্দর থিম ও বন্ধুত্বপূর্ণ কমিউনিটি। কয়েক সেকেন্ডেই অ্যাকাউন্ট সেট করুন।",
    "Sent · waiting for" to "পাঠানো হয়েছে · অপেক্ষা", "them" to "তার", "Search messages" to "মেসেজ খুঁজুন",
    "Waiting for confirmation…" to "অনুমোদনের অপেক্ষা…", "Type a message…" to "মেসেজ লিখুন…",
    // Auto-wrapped common strings
    "Sign out" to "সাইন আউট",
    "Discover people" to "মানুষ খুঁজুন",
    "My profile" to "আমার প্রোফাইল",
    "Profile & settings" to "প্রোফাইল ও সেটিংস",
    "No posts yet" to "এখনো কোনো পোস্ট নেই",
    "Add" to "যোগ করুন",
    "Edit post" to "পোস্ট এডিট",
    "Hashtags" to "হ্যাশট্যাগ",
    "AI translation" to "AI অনুবাদ",
    "Translating to Bengali…" to "বাংলায় অনুবাদ হচ্ছে…",
    "Email Address" to "ইমেইল ঠিকানা",
    "Two-step verification" to "২-ধাপ যাচাই",
    "Share a moment with your community" to "কমিউনিটির সাথে একটি মুহূর্ত শেয়ার করুন",
    "Share the first update with your community." to "কমিউনিটির সাথে প্রথম আপডেট শেয়ার করুন।",
    "Search users by name..." to "নাম দিয়ে খুঁজুন...",
    "No conversations yet — search to find people" to "এখনো কোনো কথোপকথন নেই — খুঁজে মানুষ যোগ করুন",
    "Create Group" to "গ্রুপ তৈরি",
    "You haven't joined any groups yet" to "আপনি এখনো কোনো গ্রুপে যোগ দেননি",
    "Create a new group above to invite friends!" to "উপরে নতুন গ্রুপ তৈরি করে বন্ধুদের আমন্ত্রণ জানান!",
    "Your account, messages and preferences" to "আপনার অ্যাকাউন্ট, মেসেজ ও পছন্দ",
    "Report a problem" to "সমস্যা জানান",
    "Any other emoji" to "অন্য যেকোনো ইমোজি",
    "Use" to "ব্যবহার করুন",
    "Message text size" to "মেসেজের লেখার সাইজ",
    "This is how your messages look" to "আপনার মেসেজ এভাবে দেখাবে",
    "Enter key sends" to "Enter চাপলে পাঠাবে",
    "Press Enter on the keyboard to send instead of adding a new line" to "নতুন লাইনের বদলে Enter চাপলে মেসেজ যাবে",
    "Link previews" to "লিংক প্রিভিউ",
    "Show a title, image and site icon for links in chats and posts" to "চ্যাট ও পোস্টে লিংকের শিরোনাম, ছবি ও আইকন দেখাও",
    "Like sound" to "লাইক সাউন্ড",
    "Play a soft pop when you like or react to a message" to "লাইক বা রিঅ্যাক্ট করলে হালকা শব্দ",
    "Reset" to "রিসেট",
    "Get Premium" to "প্রিমিয়াম নিন",
    "Clear" to "মুছুন",
    "Bio" to "বায়ো",
    "Tell people about yourself" to "নিজের সম্পর্কে লিখুন",
    "Automatic away reply" to "স্বয়ংক্রিয় অ্যাওয়ে রিপ্লাই",
    "Reply when you are unavailable." to "আপনি ব্যস্ত থাকলে স্বয়ংক্রিয় উত্তর।",
    "Away reply message" to "অ্যাওয়ে রিপ্লাই মেসেজ",
    "Cover photo" to "কভার ছবি",
    "Zoom" to "জুম",
    "Vertical position" to "উল্লম্ব অবস্থান",
    "Save Changes" to "পরিবর্তন সেভ করুন",
    "App lock" to "অ্যাপ লক",
    "Biometric unlock" to "বায়োমেট্রিক আনলক",
    "Privacy & Security" to "প্রাইভেসি ও নিরাপত্তা",
    "Screenshot protection" to "স্ক্রিনশট সুরক্ষা",
    "Disappearing messages" to "অদৃশ্য মেসেজ",
    "Outgoing messages hide after the selected time" to "নির্দিষ্ট সময় পর পাঠানো মেসেজ লুকিয়ে যাবে",
    "Sounds & activity" to "সাউন্ড ও অ্যাক্টিভিটি",
    "Control notification and live typing feedback." to "নোটিফিকেশন ও টাইপিং সাউন্ড নিয়ন্ত্রণ।",
    "Notification sounds" to "নোটিফিকেশন সাউন্ড",
    "Typing indicator sound" to "টাইপিং সাউন্ড",
    "Enable app notifications" to "অ্যাপ নোটিফিকেশন চালু করুন",
    "Enable incoming-call channel" to "ইনকামিং কল চ্যানেল চালু করুন",
    "Allow full-screen incoming calls" to "ফুল-স্ক্রিন ইনকামিং কল অনুমতি দিন",
    "Devices & sessions" to "ডিভাইস ও সেশন",
    "Load signed-in devices" to "সাইন-ইন ডিভাইস দেখুন",
    "Delete all device history" to "সব ডিভাইস হিস্টরি মুছুন",
    "Switch account" to "অ্যাকাউন্ট বদলান",
    "Log out all devices" to "সব ডিভাইস থেকে লগ আউট",
    "All signed-in devices" to "সব সাইন-ইন ডিভাইস",
    "Delete all" to "সব মুছুন",
    "Unblock" to "আনব্লক",
    "Find new accounts and build your circle." to "নতুন মানুষ খুঁজে আপনার সার্কেল গড়ুন।",
    "Search people" to "মানুষ খুঁজুন",
    "PIN" to "পিন",
    "Confirm PIN" to "পিন নিশ্চিত করুন",
    "Enable lock" to "লক চালু করুন",
    "Create a group" to "গ্রুপ তৈরি করুন",
    "Group Name" to "গ্রুপের নাম",
    "Create" to "তৈরি করুন",
    "Unlock Convo Premium" to "Convo প্রিমিয়াম আনলক করুন",
    "View Premium" to "প্রিমিয়াম দেখুন",
    "Explore Premium" to "প্রিমিয়াম ঘুরে দেখুন",
    "What happened?" to "কী হয়েছে?",
    "Submit report" to "রিপোর্ট জমা দিন",
    "Sign in again" to "আবার সাইন ইন করুন",
    "Account suspended" to "অ্যাকাউন্ট স্থগিত",
    "Recent" to "সাম্প্রতিক",
    "Clear all" to "সব মুছুন",
    "Trending this week" to "এই সপ্তাহের ট্রেন্ডিং",
    "Search everything" to "সব কিছু খুঁজুন",
    "People" to "মানুষ",
    "Posts & media" to "পোস্ট ও মিডিয়া",
    "Activity" to "অ্যাক্টিভিটি",
    "No activity yet" to "এখনো কোনো অ্যাক্টিভিটি নেই",
    "Friend requests" to "ফ্রেন্ড রিকোয়েস্ট",
    "sent you a friend request" to "আপনাকে ফ্রেন্ড রিকোয়েস্ট পাঠিয়েছে",
    "Accept" to "গ্রহণ",
    "Message requests" to "মেসেজ রিকোয়েস্ট",
    "Recent activity" to "সাম্প্রতিক অ্যাক্টিভিটি",
    "No more stories" to "আর কোনো স্টোরি নেই",
    "Reply to story…" to "স্টোরিতে উত্তর দিন…",
    "No reels yet" to "এখনো কোনো রিল নেই",
    "Create Reel" to "রিল তৈরি",
    "Videos" to "ভিডিও",
    "Comments" to "কমেন্ট",
    "No comments yet — start the conversation" to "এখনো কমেন্ট নেই — প্রথম কমেন্ট করুন",
    "Suggested for you" to "আপনার জন্য প্রস্তাবিত",
    "Follow" to "ফলো",
    "Add Story" to "স্টোরি যোগ করুন",
    "Photo Story" to "ছবির স্টোরি",
    "Video Story" to "ভিডিও স্টোরি",
    "Send to a chat" to "চ্যাটে পাঠান",
    "Report post" to "পোস্ট রিপোর্ট",
    "Delete post" to "পোস্ট মুছুন",
    "Write comment..." to "কমেন্ট লিখুন...",
    "Send post to chat" to "পোস্ট চ্যাটে পাঠান",
    "Report this post" to "এই পোস্ট রিপোর্ট করুন",
    "Submit" to "জমা দিন",
    "People who reacted" to "যারা রিঅ্যাক্ট করেছে",
    "No reactions yet." to "এখনো রিঅ্যাকশন নেই।",
    "Title" to "শিরোনাম",
    "Description" to "বিবরণ",
    "Private post" to "প্রাইভেট পোস্ট",
    "Themes" to "থিম",
    "Bubbles" to "বাবল",
    "What happened? (optional)" to "কী হয়েছে? (ঐচ্ছিক)",
    "No matching messages" to "মিল পাওয়া যায়নি",
    "Latest messages" to "সর্বশেষ মেসেজ",
    "Editing Message" to "মেসেজ এডিট হচ্ছে",
    "Message request pending" to "মেসেজ রিকোয়েস্ট অপেক্ষমাণ",
    "Schedule message" to "মেসেজ শিডিউল",
    "Choose when to send this text message." to "কখন মেসেজটি যাবে বেছে নিন।",
    "In 10 minutes" to "১০ মিনিট পর",
    "In 1 hour" to "১ ঘণ্টা পর",
    "Tomorrow at 9:00 AM" to "আগামীকাল সকাল ৯টায়",
    "Turn on location" to "লোকেশন চালু করুন",
    "Open settings" to "সেটিংস খুলুন",
    "Share a contact" to "কন্টাক্ট শেয়ার",
    "You’ll hear it when it’s sent" to "পাঠানো হলে শুনতে পাবেন",
    "is composing a message" to "মেসেজ লিখছে",
    "Convo stickers" to "Convo স্টিকার",
    "Convo animated stickers" to "Convo অ্যানিমেটেড স্টিকার",
    "Pinch to zoom • drag to move" to "পিঞ্চ করে জুম • টেনে সরান",
    "Private & secure" to "প্রাইভেট ও সুরক্ষিত",
    "Log In" to "লগ ইন",
    "Sign Up" to "সাইন আপ",
    "Select Date" to "তারিখ বেছে নিন",
    "At least 6 characters" to "কমপক্ষে ৬ অক্ষর",
    "End-to-end encrypted" to "এন্ড-টু-এন্ড সুরক্ষিত",
    "Previous accounts" to "আগের অ্যাকাউন্ট",
    "Send Reset Link" to "রিসেট লিংক পাঠান",
    "Group call available" to "গ্রুপ কল চলছে",
    "Join Call" to "কলে যোগ দিন",
    "Search group messages" to "গ্রুপ মেসেজ খুঁজুন",
    "No matching group messages" to "মিল পাওয়া যায়নি",
    "Try another keyword" to "অন্য শব্দ দিয়ে চেষ্টা করুন",
    "Message the group" to "গ্রুপে মেসেজ লিখুন",
    "You" to "আপনি",
    "Add group members" to "সদস্য যোগ করুন",
    "Everyone is already in this group." to "সবাই ইতিমধ্যে এই গ্রুপে আছে।",
    "Copy profile link" to "প্রোফাইল লিংক কপি",
    "Share profile" to "প্রোফাইল শেয়ার",
    "Hide" to "লুকান",
    "Requested" to "রিকোয়েস্ট পাঠানো হয়েছে",
    "Add friend" to "বন্ধু যোগ করুন",
    "Send message request" to "মেসেজ রিকোয়েস্ট পাঠান",
    "About" to "সম্পর্কে",
    "Posts" to "পোস্ট",
    "Shared moments will appear here" to "শেয়ার করা মুহূর্তগুলো এখানে দেখাবে",
    "Create post" to "পোস্ট তৈরি",
    "Post" to "পোস্ট",
    "Reel" to "রিল",
    "Add a title (optional)" to "শিরোনাম যোগ করুন (ঐচ্ছিক)",
    "Video" to "ভিডিও",
    "Tag people" to "মানুষ ট্যাগ করুন",
    "AI caption" to "AI ক্যাপশন",
    "Clear draft" to "ড্রাফট মুছুন",
    "Not now" to "এখন না",
    "Create something" to "কিছু তৈরি করুন",
    "One smart hub for every format" to "সব ফরম্যাটের জন্য এক স্মার্ট হাব",
    "Photo story" to "ছবির স্টোরি",
    "Video story" to "ভিডিও স্টোরি",
    "Enabled" to "চালু",
    "Reduce motion" to "মোশন কমান",
    "Stop sharing" to "শেয়ার বন্ধ করুন",
    "Confirm" to "নিশ্চিত করুন",
    "Verify" to "যাচাই করুন",
    "Resend code" to "আবার কোড পাঠান",
    "Sign in" to "সাইন ইন",
    "Turn on" to "চালু করুন",
    "Pick a color canvas for text posts" to "টেক্সট পোস্টের জন্য রঙিন ক্যানভাস বেছে নিন",
    // Tour
    "A quick tour of the main buttons. It takes 20 seconds." to "মূল বাটনগুলোর একটি ছোট্ট ট্যুর। মাত্র ২০ সেকেন্ড লাগবে।",
    "Home feed" to "হোম ফিড", "Posts, stories and updates from your friends." to "বন্ধুদের পোস্ট, স্টোরি ও আপডেট।",
    "All your conversations. In a chat, type / for quick replies." to "আপনার সব কথোপকথন। চ্যাটে / লিখলে দ্রুত উত্তর পাবেন।",
    "Post, story, reel or poll — everything starts here." to "পোস্ট, স্টোরি, রিল বা পোল — সব এখান থেকে শুরু।",
    "Reels & groups" to "রিলস ও গ্রুপ", "Watch reels or open your groups from here." to "এখান থেকে রিলস দেখুন বা গ্রুপ খুলুন।",
    "Friend requests and people you may know." to "ফ্রেন্ড রিকোয়েস্ট ও পরিচিত হতে পারে এমন মানুষ।",
    "You're all set!" to "সব প্রস্তুত!", "Change the language or replay this tour any time in Settings." to "সেটিংসে যেকোনো সময় ভাষা বদলাতে বা ট্যুর আবার দেখতে পারবেন।",
    "Replay app tour" to "অ্যাপ ট্যুর আবার দেখুন", "Show the guided tour of the main buttons again" to "মূল বাটনগুলোর গাইডেড ট্যুর আবার দেখান",
    "Surprise me" to "চমক দিন", "Uppercase" to "বড় হাতের", "Title case" to "টাইটেল কেস", "Clean spacing" to "স্পেস ঠিক করুন", "characters" to "অক্ষর",
    // Navigation
    "Home" to "হোম", "Chats" to "চ্যাট", "Reels" to "রিলস", "Groups" to "গ্রুপ", "Friends" to "বন্ধুরা",
    "Settings" to "সেটিংস", "Profile" to "প্রোফাইল", "Search" to "খুঁজুন", "Notifications" to "নোটিফিকেশন",
    // Common
    "Save" to "সেভ", "Cancel" to "বাতিল", "Close" to "বন্ধ", "Delete" to "মুছুন", "Edit" to "এডিট", "Done" to "সম্পন্ন",
    "Next" to "পরবর্তী", "Skip" to "এড়িয়ে যান", "Back" to "ফিরে যান", "Continue" to "চালিয়ে যান", "Retry" to "আবার চেষ্টা",
    "Send" to "পাঠান", "Reply" to "উত্তর", "Copy" to "কপি", "Forward" to "ফরওয়ার্ড", "Share" to "শেয়ার",
    "Copied" to "কপি হয়েছে", "Select text" to "লেখা সিলেক্ট করুন", "Delete Message" to "মেসেজ মুছুন",
    "Translate to Bengali (AI)" to "বাংলায় অনুবাদ (AI)", "Message copied" to "মেসেজ কপি হয়েছে", "Link copied" to "লিংক কপি হয়েছে",
    "Language" to "ভাষা", "Choose your app language" to "অ্যাপের ভাষা বেছে নিন",
    "Main screens are translated; some advanced/admin parts stay in English." to "মূল স্ক্রিনগুলো বাংলায় দেখাবে; কিছু উন্নত/অ্যাডমিন অংশ এখনো ইংরেজিতে।",
    // Message status
    "Seen" to "দেখেছে", "Delivered · not seen yet" to "পৌঁছেছে · এখনো দেখেনি", "Sending…" to "পাঠানো হচ্ছে…",
    "Not sent — tap ! to retry" to "পাঠানো যায়নি — ! চাপুন", "Finishing…" to "শেষ হচ্ছে…",
    // Forward / quick replies
    "Only your friends are shown" to "শুধু আপনার বন্ধুরা দেখাচ্ছে", "Search friends" to "বন্ধু খুঁজুন", "Select friends" to "বন্ধু বেছে নিন",
    "No friends yet" to "এখনো কোনো বন্ধু নেই", "Quick replies · tap to send" to "দ্রুত উত্তর · চাপলেই যাবে", "Manage" to "ম্যানেজ",
    "Quick replies" to "দ্রুত উত্তর", "Chat emoji · changes for both of you" to "চ্যাট ইমোজি · দুজনেরই বদলাবে",
    // Settings hub groups
    "Account" to "অ্যাকাউন্ট", "Appearance" to "চেহারা", "Privacy & permissions" to "প্রাইভেসি ও অনুমতি", "Smart tools" to "স্মার্ট টুলস", "More" to "আরও",
    "Chat & calls" to "চ্যাট ও কল", "Look & feel" to "লুক ও ফিল",
    // Settings rows
    "Profile details" to "প্রোফাইলের তথ্য", "App theme" to "অ্যাপ থিম", "Call appearance" to "কলের চেহারা", "Chat settings" to "চ্যাট সেটিংস",
    "Bubbles & spacing" to "বাবল ও স্পেসিং", "Sounds" to "সাউন্ড", "Privacy controls" to "প্রাইভেসি কন্ট্রোল",
    "Notifications & calls" to "নোটিফিকেশন ও কল", "Profile viewers" to "প্রোফাইল ভিজিটর", "Account security" to "অ্যাকাউন্ট নিরাপত্তা",
    "Blocked accounts" to "ব্লক করা অ্যাকাউন্ট", "Chat backgrounds" to "চ্যাট ব্যাকগ্রাউন্ড", "Premium" to "প্রিমিয়াম",
    "Your own notification and typing sounds" to "আপনার নিজের নোটিফিকেশন ও টাইপিং সাউন্ড",
    "Presence, receipts, AI and screen protection" to "অনলাইন স্ট্যাটাস, রিসিপ্ট, AI ও স্ক্রিন সুরক্ষা",
    "Sounds, alerts and incoming-call access" to "সাউন্ড, অ্যালার্ট ও ইনকামিং কল",
    "See who visited your profile" to "কে আপনার প্রোফাইল দেখেছে",
    "Quiet hours, motion, live location and storage" to "নীরব সময়, মোশন, লাইভ লোকেশন ও স্টোরেজ",
    "Two-step verification, backup codes, devices & alerts" to "২-ধাপ যাচাই, ব্যাকআপ কোড, ডিভাইস ও অ্যালার্ট",
    "Liquid, neon, outline or no border · side space · 24h time" to "লিকুইড, নিয়ন, আউটলাইন · পাশের জায়গা · ২৪ ঘণ্টা সময়",
    "English or বাংলা" to "English অথবা বাংলা",
    "Photo, bio, links and account details" to "ছবি, বায়ো, লিংক ও অ্যাকাউন্টের তথ্য",
    "Light, dark and premium looks for the whole app" to "পুরো অ্যাপের জন্য লাইট, ডার্ক ও প্রিমিয়াম লুক",
    "Skins and textures for audio and video calls" to "অডিও ও ভিডিও কলের স্কিন",
    "How messages look and send" to "মেসেজ কেমন দেখাবে ও যাবে",
    "Make chat bubbles yours: style, border, padding and time" to "বাবল আপনার মতো করুন: স্টাইল, বর্ডার, সময়",
    "Custom MP3 sounds saved on this phone" to "এই ফোনে সেভ করা কাস্টম MP3",
    "Themes and photo backgrounds for chats" to "চ্যাটের থিম ও ছবি ব্যাকগ্রাউন্ড",
    "App lock, presence, receipts and AI" to "অ্যাপ লক, স্ট্যাটাস, রিসিপ্ট ও AI",
    "Alerts, sounds and incoming-call access" to "অ্যালার্ট, সাউন্ড ও ইনকামিং কল",
    "Stored only on this phone" to "শুধু এই ফোনে সংরক্ষিত",
    "2-step verification, devices & alerts" to "২-ধাপ যাচাই, ডিভাইস ও অ্যালার্ট",
    "People who cannot contact you" to "যারা আপনার সাথে যোগাযোগ করতে পারবে না",
    "Smart notifications" to "স্মার্ট নোটিফিকেশন", "Tell me when they come online" to "অনলাইনে এলে আমাকে জানাও",
    "Show photos in notifications" to "নোটিফিকেশনে ছবি দেখাও",
    // Tour
    "Welcome to Convo" to "Convo-তে স্বাগতম", "Got it" to "বুঝেছি", "Start chatting" to "চ্যাট শুরু করুন",
    // Chat screen
    "Message" to "মেসেজ", "Type a message" to "মেসেজ লিখুন", "Online" to "অনলাইন", "Active now" to "এখন সক্রিয়", "Typing…" to "লিখছে…",
    "Photo" to "ছবি", "Voice message" to "ভয়েস মেসেজ", "File" to "ফাইল", "Location" to "লোকেশন", "Camera" to "ক্যামেরা", "Gallery" to "গ্যালারি",
    "Open map" to "ম্যাপ খুলুন", "Directions" to "দিকনির্দেশ", "Live location" to "লাইভ লোকেশন", "Shared location" to "শেয়ার করা লোকেশন",
    // Calls
    "Mute" to "মিউট", "Speaker" to "স্পিকার", "Camera off" to "ক্যামেরা বন্ধ", "Flip" to "ঘোরান", "Filters" to "ফিল্টার", "Torch" to "টর্চ",
    "End" to "শেষ", "Share screen" to "স্ক্রিন শেয়ার", "Minimize" to "ছোট করুন"
)
