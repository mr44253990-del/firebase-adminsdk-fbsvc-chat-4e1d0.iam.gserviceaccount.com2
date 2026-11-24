package com.example.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide design switch (v5.7): "classic" keeps the previous look and the user's own theme;
 * "new" puts every screen on the dark liquid-glass design. The admin sets the default for
 * everyone; each user can still pick for themselves in Settings.
 */
object DesignStyle {
    private const val PREFS = "convo_design"
    /** "" = follow the admin default, otherwise "classic" or "new". */
    private val _choice = MutableStateFlow("")
    val choice: StateFlow<String> = _choice.asStateFlow()
    private val _adminDefault = MutableStateFlow("classic")
    val adminDefault: StateFlow<String> = _adminDefault.asStateFlow()

    fun load(context: Context) { _choice.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("choice", "").orEmpty() }
    fun set(context: Context, value: String) {
        _choice.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("choice", value).apply()
    }
    fun setAdminDefault(value: String) { _adminDefault.value = if (value == "new") "new" else "classic" }
    fun isNew(choice: String, adminDefault: String): Boolean = (choice.ifBlank { adminDefault }) == "new"
}
