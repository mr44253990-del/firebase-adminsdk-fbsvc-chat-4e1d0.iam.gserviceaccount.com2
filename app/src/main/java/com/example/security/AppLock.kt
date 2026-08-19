package com.example.security

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local app-lock storage. Existing v1 PINs remain readable and are upgraded after a successful unlock. */
object AppLockManager {
    private const val PREFS = "firechat_app_lock"
    private const val HASH_VERSION = 2
    private const val NEW_ITERATIONS = 120_000
    private const val LEGACY_ITERATIONS = 40_000
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MS = 30_000L

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun isBiometricEnabled(context: Context) = prefs(context).getBoolean("biometric", false)
    fun pinLength(context: Context) = prefs(context).getInt("pinLength", 4)
    fun canUseBiometric(context: Context) = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS

    fun initialize(context: Context) {
        _locked.value = isEnabled(context)
    }

    fun lock(context: Context) {
        if (isEnabled(context)) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    fun canAttemptPin(context: Context): Boolean = System.currentTimeMillis() >= prefs(context).getLong("blockedUntil", 0L)

    fun remainingLockoutMs(context: Context): Long = (prefs(context).getLong("blockedUntil", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Returns the new lockout duration when a failure triggers throttling, otherwise zero. */
    fun recordFailedAttempt(context: Context): Long {
        val p = prefs(context)
        val attempts = p.getInt("failedAttempts", 0) + 1
        if (attempts < MAX_FAILED_ATTEMPTS) {
            p.edit().putInt("failedAttempts", attempts).apply()
            return 0L
        }
        val previousLockout = p.getLong("blockedUntil", 0L)
        val previousDuration = (previousLockout - System.currentTimeMillis()).coerceAtLeast(0L)
        val duration = if (previousDuration > 0L) (previousDuration * 2).coerceAtMost(10 * 60_000L) else BASE_LOCKOUT_MS
        p.edit().putInt("failedAttempts", 0).putLong("blockedUntil", System.currentTimeMillis() + duration).apply()
        return duration
    }

    fun clearFailedAttempts(context: Context) {
        prefs(context).edit().remove("failedAttempts").remove("blockedUntil").apply()
    }

    fun setLock(context: Context, pin: String, biometric: Boolean): Boolean {
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = hash(pin, salt, NEW_ITERATIONS, "PBKDF2WithHmacSHA256")
        prefs(context).edit()
            .putBoolean("enabled", true)
            .putBoolean("biometric", biometric && canUseBiometric(context))
            .putInt("pinLength", pin.length)
            .putInt("hashVersion", HASH_VERSION)
            .putInt("iterations", NEW_ITERATIONS)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove("failedAttempts")
            .remove("blockedUntil")
            .apply()
        _locked.value = false
        return true
    }

    fun verify(context: Context, pin: String): Boolean {
        if (!canAttemptPin(context)) return false
        val p = prefs(context)
        val salt = runCatching { Base64.decode(p.getString("salt", ""), Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(p.getString("hash", ""), Base64.NO_WRAP) }.getOrNull() ?: return false
        val version = p.getInt("hashVersion", 1)
        val iterations = p.getInt("iterations", if (version >= HASH_VERSION) NEW_ITERATIONS else LEGACY_ITERATIONS)
        val algorithm = if (version >= HASH_VERSION) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val valid = MessageDigest.isEqual(hash(pin, salt, iterations, algorithm), expected)
        if (valid) {
            clearFailedAttempts(context)
            if (version < HASH_VERSION) setLock(context, pin, isBiometricEnabled(context))
        }
        return valid
    }

    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
        _locked.value = false
    }

    fun setBiometric(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("biometric", enabled && canUseBiometric(context)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(pin: String, salt: ByteArray, iterations: Int, algorithm: String): ByteArray =
        SecretKeyFactory.getInstance(algorithm)
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded
}
