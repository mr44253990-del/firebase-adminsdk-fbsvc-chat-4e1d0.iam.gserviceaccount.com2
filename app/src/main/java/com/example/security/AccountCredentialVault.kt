package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores an optional, user-approved password credential only as ciphertext protected by
 * an Android Keystore AES key. The vault never writes a plaintext password to preferences,
 * files, logs, or the remembered-account metadata.
 */
object AccountCredentialVault {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "convo_account_credential_key_v1"
    private const val PREFS = "convo_encrypted_account_credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun accountKey(email: String): String = email.trim().lowercase()

    fun save(context: Context, email: String, password: String) {
        val normalizedEmail = accountKey(email)
        if (normalizedEmail.isBlank() || password.isBlank()) return
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.iv + cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
            prefs(context).edit()
                .putString(normalizedEmail, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
        }
    }

    fun load(context: Context, email: String): String? {
        val encoded = prefs(context).getString(accountKey(email), null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_BYTES) return@runCatching null
            val iv = payload.copyOfRange(0, IV_BYTES)
            val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun remove(context: Context, email: String) {
        prefs(context).edit().remove(accountKey(email)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
