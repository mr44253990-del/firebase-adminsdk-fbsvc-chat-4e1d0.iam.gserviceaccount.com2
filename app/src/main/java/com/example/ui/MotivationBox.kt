package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * Dynamic daily motivation card: a fresh quote every day (rotates automatically), a personal
 * streak counter that grows with every consecutive day the app is opened, one-tap shuffle for
 * a new quote, and instant share. Quotes are a curated mix of well-known sayings and Bangla
 * motivational lines — nothing is attributed unless the attribution is certain.
 */
object MotivationEngine {
    private const val PREFS = "convo_motivation"

    data class Quote(val text: String, val author: String)

    val quotes = listOf(
        Quote("প্রতিদিন এক ধাপ এগোনোই সাফল্যের আসল গল্প।", ""),
        Quote("ছোট ছোট পরিশ্রম মিলেই বড় স্বপ্ন পূরণ হয়।", ""),
        Quote("সবুর করা মানে হাল ছাড়া নয়, সঠিক সময়ের অপেক্ষা করা।", ""),
        Quote("নিজের উপর বিশ্বাস রাখো — বাকিটা সময় ঠিক করে দেবে।", ""),
        Quote("আজ যে কষ্ট, কাল সেটাই তোমার শক্তি।", ""),
        Quote("মন ভালো রাখো, কাজ ভালো হবে।", ""),
        Quote("লক্ষ্য যত বড়ই হোক, শুরুটা সবসময় ছোট।", ""),
        Quote("পরিবর্তন চাইলে নিজেই প্রথম পরিবর্তন হও।", ""),
        Quote("Stay hungry, stay foolish.", "Steve Jobs"),
        Quote("The best way to get started is to quit talking and begin doing.", "Walt Disney"),
        Quote("Simplicity is the ultimate sophistication.", "Leonardo da Vinci"),
        Quote("It always seems impossible until it's done.", "Nelson Mandela"),
        Quote("Quality is not an act, it is a habit.", "Aristotle"),
        Quote("Well done is better than well said.", "Benjamin Franklin"),
        Quote("Your future is created by what you do today, not tomorrow.", "Robert Kiyosaki"),
        Quote("Discipline is choosing between what you want now and what you want most.", ""),
        Quote("Small steps every day still take you somewhere great.", ""),
        Quote("Rest, but never quit.", ""),
        Quote("একটু একটু করে অভ্যাস, দিন শেষে বিশাল পরিবর্তন।", ""),
        Quote("ভালোবাসা দিয়ে শুরু করো, কাজ দিয়ে চালিয়ে যাও।", "")
    )

    /** Deterministic quote of the day: everyone sees the same one, changing at midnight. */
    fun quoteForToday(): Quote {
        val dayIndex = System.currentTimeMillis() / 86_400_000L
        return quotes[(dayIndex % quotes.size).toInt()]
    }

    fun randomQuote(except: Quote): Quote =
        quotes.filter { it != except }.randomOrNull() ?: quotes.random()

    fun streak(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / 86_400_000L
        val last = prefs.getLong("last_day", 0L)
        val streak = prefs.getInt("streak", 0)
        return when {
            last == today -> streak
            last == today - 1 -> streak + 1
            else -> 1
    }
    }

    /** Call once per app open; grows the streak on consecutive days. */
    fun markToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = System.currentTimeMillis() / 86_400_000L
        val last = prefs.getLong("last_day", 0L)
        val current = prefs.getInt("streak", 0)
        when {
            last == today -> Unit
            last == today - 1 -> prefs.edit().putLong("last_day", today).putInt("streak", current + 1).apply()
            else -> prefs.edit().putLong("last_day", today).putInt("streak", 1).apply()
        }
    }
}

@Composable
fun MotivationBox(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val daily = remember { MotivationEngine.quoteForToday() }
    var quote by remember { androidx.compose.runtime.mutableStateOf(daily) }
    val streak = remember { MotivationEngine.streak(context) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A1E5C), Color(0xFF123A5C), Color(0xFF3C1657)),
                    )
                )
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color(0xFFFFC24B), Color(0xFFFF8A3D), Color(0xFFFFC24B)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.WbSunny, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (quote.text.any { it in '\u0980'..'\u09FF' } && quote.author.isBlank()) "আজকের অনুপ্রেরণা" else "Daily motivation",
                            color = Color(0xFFFFD08A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .12f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment, null,
                                    tint = Color(0xFFFF8A3D), modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text("$streak day streak", color = Color.White.copy(alpha = .85f), fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(Modifier.padding(top = 3.dp))
                    Text(
                        "“${quote.text}”",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (quote.author.isNotBlank()) {
                        Text(
                            "— ${quote.author}",
                            color = Color.White.copy(alpha = .65f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { quote = MotivationEngine.randomQuote(quote) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🎲", fontSize = 15.sp)
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, "\"${quote.text}\"${if (quote.author.isNotBlank()) " — ${quote.author}" else ""}\n— Convo Chat"),
                                        "Share motivation"
                                    )
                                )
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) { Icon(Icons.Default.Share, "Share", tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}
