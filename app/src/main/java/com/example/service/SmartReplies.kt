package com.example.service

/**
 * Tiny on-device smart-reply suggestions for notification chips and the in-app composer.
 * Pure heuristics (no network, nothing leaves the phone); Bengali replies for Bengali text.
 */
object SmartReplies {
    private val bengali = Regex("[\\u0980-\\u09FF]")

    fun suggest(message: String): List<String> {
        val text = message.lowercase().trim()
        val bn = bengali.containsMatchIn(text)
        fun has(vararg words: String) = words.any { text.contains(it) }
        return when {
            text.isBlank() -> listOf("👍", "Okay", "Talk later")
            has("thank", "thanks", "thx", "ধন্যবাদ", "থ্যাংক") -> if (bn) listOf("স্বাগতম 😊", "কোনো ব্যাপার না", "❤️") else listOf("You're welcome 😊", "Anytime!", "❤️")
            has("assalam", "salam", "সালাম", "আসসালামু") -> listOf("ওয়ালাইকুম আসসালাম", "Walaikum assalam 🤲", "কেমন আছেন?")
            has("কেমন আছ", "how are you", "how r u", "kemon acho", "kemon aso") -> if (bn) listOf("ভালো আছি, তুমি?", "আলহামদুলিল্লাহ ভালো", "এই তো চলছে 🙂") else listOf("I'm good, you?", "All good! 😊", "Doing great")
            has("hi", "hello", "hey", "হাই", "হ্যালো") && text.length < 20 -> if (bn) listOf("হাই! 👋", "বলো", "কী খবর?") else listOf("Hi! 👋", "Hey, what's up?", "Hello 😊")
            has("where", "কোথায়", "kothay") -> if (bn) listOf("বাসায় আছি", "রাস্তায় আছি", "একটু পরে বলছি") else listOf("At home", "On my way", "Will tell you soon")
            has("call", "কল", "phone", "ফোন") -> if (bn) listOf("একটু পরে কল দিচ্ছি", "এখন দাও", "ব্যস্ত আছি") else listOf("I'll call you soon", "Call me now", "Busy right now")
            has("sorry", "দুঃখিত", "সরি") -> if (bn) listOf("সমস্যা নেই 🙂", "ঠিক আছে", "❤️") else listOf("No worries 🙂", "It's okay", "❤️")
            has("congrat", "অভিনন্দন", "শুভেচ্ছা", "happy birthday", "জন্মদিন") -> if (bn) listOf("ধন্যবাদ! 🥰", "অনেক ধন্যবাদ ❤️", "🎉") else listOf("Thank you! 🥰", "Thanks a lot ❤️", "🎉")
            text.endsWith("?") || has("কি ", "কী ", "কেন", "কখন", "when", "what", "why", "can you", "পারবে") ->
                if (bn) listOf("হ্যাঁ", "না", "দেখে জানাচ্ছি") else listOf("Yes", "No", "Let me check")
            else -> if (bn) listOf("👍", "ঠিক আছে", "পরে কথা বলি") else listOf("👍", "Okay", "Talk later")
        }
    }
}
