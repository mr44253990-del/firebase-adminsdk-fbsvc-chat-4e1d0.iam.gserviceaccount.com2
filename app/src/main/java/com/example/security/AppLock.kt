package com.example.security

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockManager {
    private const val PREFS = "firechat_app_lock"
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun isBiometricEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("biometric", false)
    fun pinLength(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("pinLength", 4)
    fun canUseBiometric(context: Context) = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun initialize(context: Context) { _locked.value = isEnabled(context) }
    fun lock(context: Context) { if (isEnabled(context)) _locked.value = true }
    fun unlock() { _locked.value = false }

    fun setLock(context: Context, pin: String, biometric: Boolean): Boolean {
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = hash(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putBoolean("biometric", biometric).putInt("pinLength", pin.length)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP)).apply()
        _locked.value = false
        return true
    }

    fun verify(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val salt = runCatching { Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(prefs.getString("hash", ""), Base64.NO_WRAP) }.getOrNull() ?: return false
        return hash(pin, salt).contentEquals(expected)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _locked.value = false
    }

    fun setBiometric(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("biometric", enabled).apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 40_000, 256)).encoded
}
