package com.example.ui

/**
 * One place for "how long ago / how long" wording (v5.7). The unit grows with the amount, so the
 * app never shows things like "2000 minutes ago": minutes → hours → days → weeks → months → years.
 */
object SmartTime {
    /** "45 min", "3 hr", "2 days", "3 weeks", "5 months", "2 years". */
    fun span(millis: Long): String {
        val minutes = (millis.coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> "moments"
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> (minutes / 60).let { "$it hr" }
            minutes < 7 * 1440 -> (minutes / 1440).let { if (it == 1L) "1 day" else "$it days" }
            minutes < 30 * 1440 -> (minutes / (7 * 1440)).let { if (it == 1L) "1 week" else "$it weeks" }
            minutes < 365 * 1440 -> (minutes / (30 * 1440)).let { if (it == 1L) "1 month" else "$it months" }
            else -> (minutes / (365 * 1440)).let { if (it == 1L) "1 year" else "$it years" }
        }
    }

    /** "just now", "45 min ago", "3 hr ago", "2 days ago", … */
    fun ago(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "a while ago"
        val elapsed = (now - timestamp).coerceAtLeast(0L)
        return if (elapsed < 60_000L) "just now" else span(elapsed) + " ago"
    }

    /** A length in minutes as the best unit: 45 → "45 minutes", 120 → "2 hours", 1500 → "1 day 1 hour". */
    fun minutesLabel(totalMinutes: Int): String {
        val m = totalMinutes.coerceAtLeast(0)
        fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
        return when {
            m < 60 -> plural(m, "minute")
            m < 1440 -> plural(m / 60, "hour") + if (m % 60 != 0) " " + plural(m % 60, "minute") else ""
            else -> plural(m / 1440, "day") + if ((m % 1440) / 60 != 0) " " + plural((m % 1440) / 60, "hour") else ""
        }
    }
}
