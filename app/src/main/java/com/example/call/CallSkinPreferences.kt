package com.example.call

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallSkinOption(
    val id: String,
    val label: String,
    val background: List<Color>,
    val accent: Color,
    val surface: Color,
    /** Vector texture drawn over the gradient (see com.example.ui.drawThemeTexture). */
    val texture: String = "none",
    val category: String = "Classic"
)

/** A lightweight, local-only preference shared by private and group call screens. */
object CallSkinPreferences {
    const val DEFAULT_ID = "liquid_glass"
    private const val PREFS_NAME = "firechat_prefs"
    private const val PREF_KEY = "call_skin"

    val options = listOf(
        // Premium (v4.5.0 call skin refresh)
        CallSkinOption("liquid_glass", "Liquid Glass", listOf(Color(0xFF070B1A), Color(0xFF1B2A5C), Color(0xFF0A1F2E)), Color(0xFF8FB8FF), Color(0xEE101A33), "aurora", "Premium"),
        CallSkinOption("midnight_pro", "Midnight Pro", listOf(Color(0xFF05060C), Color(0xFF151B33), Color(0xFF060810)), Color(0xFF7C9CFF), Color(0xEE0C1020), "stars", "Premium"),
        CallSkinOption("emerald_pro", "Emerald Pro", listOf(Color(0xFF02110C), Color(0xFF0B4634), Color(0xFF03140F)), Color(0xFF4CF0B0), Color(0xEE062B20), "waves", "Premium"),
        CallSkinOption("sakura", "Sakura Night", listOf(Color(0xFF14070F), Color(0xFF4B1A3A), Color(0xFF1A0A18)), Color(0xFFFFA6D1), Color(0xEE2A1024), "petals", "Premium"),
        CallSkinOption("cosmic_violet", "Cosmic Violet", listOf(Color(0xFF07031A), Color(0xFF34106B), Color(0xFF0B0424)), Color(0xFFC79BFF), Color(0xEE180A36), "sparkle", "Premium"),
        CallSkinOption("bangla_green", "Bangla Green", listOf(Color(0xFF02130A), Color(0xFF006A4E), Color(0xFF03150C)), Color(0xFFF42A41), Color(0xEE053A28), "rings", "Premium"),
        CallSkinOption("sunrise_glow", "Sunrise Glow", listOf(Color(0xFF1A0A06), Color(0xFF6B2A12), Color(0xFF1C0D08)), Color(0xFFFFB86B), Color(0xEE3A180C), "bokeh", "Premium"),
        // Classic
        CallSkinOption("aurora", "Aurora", listOf(Color(0xFF080A12), Color(0xFF28204B), Color(0xFF071B26)), Color(0xFF9E8BFF), Color(0xEE121421), "aurora"),
        CallSkinOption("ocean", "Deep Ocean", listOf(Color(0xFF00131F), Color(0xFF004357), Color(0xFF031923)), Color(0xFF5DE7F4), Color(0xEE062433), "waves"),
        CallSkinOption("sunset", "Sunset", listOf(Color(0xFF160B1A), Color(0xFF4A1C39), Color(0xFF211323)), Color(0xFFFFA271), Color(0xEE2B1729), "rings"),
        CallSkinOption("forest", "Forest", listOf(Color(0xFF06130C), Color(0xFF153B2A), Color(0xFF061610)), Color(0xFF69E1A5), Color(0xEE10271C), "leaves"),
        CallSkinOption("rose", "Rose", listOf(Color(0xFF1A0A17), Color(0xFF541D3C), Color(0xFF201225)), Color(0xFFFF81C4), Color(0xEE301629), "petals"),
        CallSkinOption("amoled", "AMOLED", listOf(Color.Black, Color(0xFF07070A), Color(0xFF0D1018)), Color(0xFFA6B6FF), Color(0xEE090A0F)),
        // Textured
        // Call Received Skin — more future
        CallSkinOption("neon_storm", "Neon Storm", listOf(Color(0xFF0A0018), Color(0xFF3A0A6B), Color(0xFF050010)), Color(0xFFFF3BFF), Color(0xEE1A0030), "grid", "Premium"),
        CallSkinOption("aurora_pulse", "Aurora Pulse", listOf(Color(0xFF001A14), Color(0xFF00B896), Color(0xFF002B1F)), Color(0xFF4CFFD0), Color(0xEE003328), "aurora", "Premium"),
        CallSkinOption("midnight_bloom", "Midnight Bloom", listOf(Color(0xFF0F001A), Color(0xFF5B0A4A), Color(0xFF120018)), Color(0xFFFF8AC6), Color(0xEE2A001F), "petals", "Premium"),
        CallSkinOption("galaxy", "Galaxy", listOf(Color(0xFF03040B), Color(0xFF1B1240), Color(0xFF05030F)), Color(0xFFB69CFF), Color(0xEE100C24), "stars", "Textured"),
        CallSkinOption("neon_grid", "Neon Grid", listOf(Color(0xFF05010F), Color(0xFF1A0838), Color(0xFF02101C)), Color(0xFFFF4FD8), Color(0xEE140826), "grid", "Textured"),
        CallSkinOption("bokeh", "Bokeh Lights", listOf(Color(0xFF120A00), Color(0xFF3D2405), Color(0xFF140B02)), Color(0xFFFFC46B), Color(0xEE24170A), "bokeh", "Textured"),
        CallSkinOption("honeycomb", "Honeycomb", listOf(Color(0xFF110B00), Color(0xFF3A2600), Color(0xFF120C00)), Color(0xFFFFB627), Color(0xEE231802), "hex", "Textured"),
        CallSkinOption("monsoon", "Monsoon Rain", listOf(Color(0xFF041420), Color(0xFF0C3A52), Color(0xFF04121C)), Color(0xFF7FDDF0), Color(0xEE082231), "rain", "Textured"),
        CallSkinOption("circuit", "Cyber Circuit", listOf(Color(0xFF00080C), Color(0xFF002A33), Color(0xFF000A0E)), Color(0xFF00E5FF), Color(0xEE001A20), "circuit", "Textured"),
        CallSkinOption("bubbles", "Bubbles", listOf(Color(0xFF041026), Color(0xFF0F2E66), Color(0xFF051230)), Color(0xFF7FB2FF), Color(0xEE0B1D40), "bubbles", "Textured"),
        CallSkinOption("diamond", "Diamond", listOf(Color(0xFF0B0E24), Color(0xFF272F73), Color(0xFF0A0D20)), Color(0xFFA9B8FF), Color(0xEE151A40), "diamonds", "Textured"),
        CallSkinOption("retro", "Retro Wave", listOf(Color(0xFF140A22), Color(0xFF3B0F45), Color(0xFF0E0A1E)), Color(0xFFFF6E9C), Color(0xEE22102E), "zigzag", "Textured"),
        CallSkinOption("sparkle", "Sparkle", listOf(Color(0xFF0D0717), Color(0xFF2E1A4F), Color(0xFF0C0816)), Color(0xFFE6D2FF), Color(0xEE1C1230), "sparkle", "Textured"),
        // Mood
        CallSkinOption("love", "Love", listOf(Color(0xFF1A0410), Color(0xFF5E0D2C), Color(0xFF1C0510)), Color(0xFFFF6B8B), Color(0xEE2E0A1C), "hearts", "Mood"),
        CallSkinOption("party", "Party", listOf(Color(0xFF0E0624), Color(0xFF2A1260), Color(0xFF0D0620)), Color(0xFFFFD166), Color(0xEE1C0E3E), "confetti", "Mood"),
        CallSkinOption("matrix", "Matrix", listOf(Color.Black, Color(0xFF02180D), Color.Black), Color(0xFF00E676), Color(0xEE021008), "rain", "Mood"),
        CallSkinOption("coffee", "Coffee House", listOf(Color(0xFF140C07), Color(0xFF3D2616), Color(0xFF120B06)), Color(0xFFD7A26C), Color(0xEE24170D), "dots", "Mood"),
        CallSkinOption("lofi", "Lo-Fi Night", listOf(Color(0xFF120A22), Color(0xFF3A2056), Color(0xFF1C0F24)), Color(0xFFFFB38A), Color(0xEE231436), "stars", "Mood"),
        CallSkinOption("arctic", "Arctic", listOf(Color(0xFF051320), Color(0xFF1C4A6B), Color(0xFF071826)), Color(0xFFC8ECFF), Color(0xEE0D2638), "rings", "Mood"),
        CallSkinOption("gold", "Royal Gold", listOf(Color(0xFF0E0903), Color(0xFF3F2A06), Color(0xFF0E0903)), Color(0xFFFFD166), Color(0xEE201604), "diagonal", "Mood"),
        CallSkinOption("mint", "Mint Breeze", listOf(Color(0xFF02130E), Color(0xFF0E3F31), Color(0xFF03140F)), Color(0xFF7CF5C8), Color(0xEE07241C), "waves", "Mood")
    )

    private val _selectedId = MutableStateFlow(DEFAULT_ID)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    fun load(context: Context) {
        val saved = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY, DEFAULT_ID)
            .orEmpty()
        _selectedId.value = options.firstOrNull { it.id == saved }?.id ?: DEFAULT_ID
    }

    fun select(context: Context, id: String): Boolean {
        val option = options.firstOrNull { it.id == id } ?: return false
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, option.id).apply()
        _selectedId.value = option.id
        return true
    }

    fun palette(id: String): CallSkinOption = options.firstOrNull { it.id == id }
        ?: options.first { it.id == DEFAULT_ID }
}
