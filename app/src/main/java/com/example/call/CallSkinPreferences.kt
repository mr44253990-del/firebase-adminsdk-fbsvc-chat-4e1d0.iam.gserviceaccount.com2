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
    val surface: Color
)

/** A lightweight, local-only preference shared by private and group call screens. */
object CallSkinPreferences {
    const val DEFAULT_ID = "aurora"
    private const val PREFS_NAME = "firechat_prefs"
    private const val PREF_KEY = "call_skin"

    val options = listOf(
        CallSkinOption("aurora", "Aurora", listOf(Color(0xFF080A12), Color(0xFF28204B), Color(0xFF071B26)), Color(0xFF9E8BFF), Color(0xEE121421)),
        CallSkinOption("ocean", "Deep Ocean", listOf(Color(0xFF00131F), Color(0xFF004357), Color(0xFF031923)), Color(0xFF5DE7F4), Color(0xEE062433)),
        CallSkinOption("sunset", "Sunset", listOf(Color(0xFF160B1A), Color(0xFF4A1C39), Color(0xFF211323)), Color(0xFFFFA271), Color(0xEE2B1729)),
        CallSkinOption("forest", "Forest", listOf(Color(0xFF06130C), Color(0xFF153B2A), Color(0xFF061610)), Color(0xFF69E1A5), Color(0xEE10271C)),
        CallSkinOption("rose", "Rose", listOf(Color(0xFF1A0A17), Color(0xFF541D3C), Color(0xFF201225)), Color(0xFFFF81C4), Color(0xEE301629)),
        CallSkinOption("amoled", "AMOLED", listOf(Color.Black, Color(0xFF07070A), Color(0xFF0D1018)), Color(0xFFA6B6FF), Color(0xEE090A0F))
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
