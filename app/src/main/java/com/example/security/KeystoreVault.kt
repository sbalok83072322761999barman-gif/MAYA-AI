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
 * Hardware-backed AndroidKeyStore AES-256-GCM vault for storing user API keys,
 * SMTP/IMAP credentials, Telegram/Notion tokens, and smart-home secrets locally.
 * Never logs secret values.
 */
class KeystoreVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "maya_secure_vault",
        Context.MODE_PRIVATE
    )

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun putSecret(key: String, value: String) {
        if (value.isBlank()) {
            removeSecret(key)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(key, payload).apply()
    }

    fun getSecret(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        val parts = raw.split(":")
        if (parts.size != 2) return null
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun hasSecret(key: String): Boolean = !getSecret(key).isNullOrBlank()

    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "maya_ai_aes_gcm_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        // Well-known secret keys
        const val KEY_GEMINI_USER = "api_gemini_user"
        const val KEY_OPENAI = "api_openai"
        const val KEY_GROQ = "api_groq"
        const val KEY_DEEPSEEK = "api_deepseek"
        const val KEY_TOGETHER = "api_together"
        const val KEY_NVIDIA_NIM = "api_nvidia_nim"
        const val KEY_ANTHROPIC = "api_anthropic"
        const val KEY_OPENROUTER = "api_openrouter"
        const val KEY_DEEPGRAM = "api_deepgram"
        const val KEY_SERPAPI = "api_serpapi"
        const val KEY_GITHUB_TOKEN = "api_github"
        const val KEY_GITLAB_TOKEN = "api_gitlab"
        const val KEY_LINEAR_TOKEN = "api_linear"
        const val KEY_TELEGRAM_BOT = "api_telegram_bot"
        const val KEY_NOTION_TOKEN = "api_notion"
        const val KEY_SMTP_PASSWORD = "cred_smtp_password"
        const val KEY_IMAP_PASSWORD = "cred_imap_password"

        private val SENSITIVE_PATTERNS = listOf(
            Regex("""\b(?:otp|one[- ]?time[- ]?password|pin|cvv|cvc|passcode|password|secret[- ]?key|api[- ]?key|bearer)\b[:=\s]*\S+""", RegexOption.IGNORE_CASE),
            Regex("""\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b"""), // 16-digit card numbers
            Regex("""\b(?:sk|ghp|glpat|xoxb|AIza)[A-Za-z0-9_\-]{12,}\b""")
        )

        /**
         * Blocks storing passwords, OTPs, PINs, CVVs, card numbers, or API secrets in Yaadein memory.
         */
        fun containsSensitiveCredential(text: String): Boolean {
            return SENSITIVE_PATTERNS.any { it.containsMatchIn(text) }
        }
    }
}
