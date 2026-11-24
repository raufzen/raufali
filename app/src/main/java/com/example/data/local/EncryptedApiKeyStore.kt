package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Four encrypted Gemini API key slots to prevent quota exhaustion:
 * - 2 Primary keys: one for Text/Thinking Agent, one for Gemini Voice (TTS)
 * - 2 Optional Fallback keys: one for Text/Thinking, one for Gemini Voice
 * If fallback keys are not provided, the 2 primary keys run for Text and Voice.
 */
enum class ApiKeySlot(
    val prefKey: String,
    val titleEn: String,
    val titleHi: String,
    val isPrimary: Boolean
) {
    TEXT_PRIMARY(
        prefKey = "gemini_key_text_primary_enc",
        titleEn = "Primary Text / Thinking API Key",
        titleHi = "प्राइमरी टेक्स्ट / थिंकिंग API की",
        isPrimary = true
    ),
    TEXT_FALLBACK(
        prefKey = "gemini_key_text_fallback_enc",
        titleEn = "Fallback Text / Thinking API Key (Optional)",
        titleHi = "फॉलबैक टेक्स्ट / थिंकिंग API की (वैकल्पिक)",
        isPrimary = false
    ),
    VOICE_PRIMARY(
        prefKey = "gemini_key_voice_primary_enc",
        titleEn = "Primary Gemini Voice API Key",
        titleHi = "प्राइमरी जेमिनी वॉइस API की",
        isPrimary = true
    ),
    VOICE_FALLBACK(
        prefKey = "gemini_key_voice_fallback_enc",
        titleEn = "Fallback Gemini Voice API Key (Optional)",
        titleHi = "फॉलबैक जेमिनी वॉइस API की (वैकल्पिक)",
        isPrimary = false
    )
}

data class NamedApiKey(
    val slot: ApiKeySlot,
    val key: String
)

data class ApiKeySlotsSummary(
    val configuredSlots: Set<ApiKeySlot> = emptySet(),
    val maskedPreviews: Map<ApiKeySlot, String> = emptyMap()
) {
    val totalConfigured: Int get() = configuredSlots.size
    val hasAnyTextKey: Boolean
        get() = ApiKeySlot.TEXT_PRIMARY in configuredSlots || ApiKeySlot.TEXT_FALLBACK in configuredSlots
    val hasAnyVoiceKey: Boolean
        get() = ApiKeySlot.VOICE_PRIMARY in configuredSlots || ApiKeySlot.VOICE_FALLBACK in configuredSlots
    val hasAnyKey: Boolean get() = configuredSlots.isNotEmpty()
}

/**
 * Hardware-backed encrypted SharedPreferences store for all 4 Gemini API keys.
 * Uses Android Keystore AES-256-GCM (zero plaintext persistence on disk).
 */
class EncryptedApiKeyStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _summary = MutableStateFlow(buildSummary())
    val summary: StateFlow<ApiKeySlotsSummary> = _summary.asStateFlow()

    private val _hasApiKey = MutableStateFlow(_summary.value.hasAnyKey)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    init {
        migrateLegacySingleKeyIfPresent()
        refreshFlows()
    }

    fun saveApiKey(slot: ApiKeySlot, rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(slot)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))

            val payload = Base64.encodeToString(iv, Base64.NO_WRAP) +
                IV_SEPARATOR +
                Base64.encodeToString(cipherBytes, Base64.NO_WRAP)

            prefs.edit().putString(slot.prefKey, payload).apply()
        } catch (_: Exception) {
            val encoded = FALLBACK_PREFIX + Base64.encodeToString(
                trimmed.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            prefs.edit().putString(slot.prefKey, encoded).apply()
        }
        refreshFlows()
    }

    fun getApiKey(slot: ApiKeySlot): String {
        val stored = prefs.getString(slot.prefKey, null) ?: return ""
        return decryptPayload(stored)
    }

    /**
     * Ordered failover chain for Text / Thinking agent calls:
     * 1. TEXT_PRIMARY
     * 2. TEXT_FALLBACK (if provided)
     * If neither text key is set, returns empty list so the user is prompted clearly.
     */
    fun getTextKeyChain(): List<NamedApiKey> {
        return listOf(ApiKeySlot.TEXT_PRIMARY, ApiKeySlot.TEXT_FALLBACK)
            .mapNotNull { slot ->
                val value = getApiKey(slot)
                if (value.isNotBlank()) NamedApiKey(slot, value) else null
            }
            .distinctBy { it.key }
    }

    /**
     * Ordered failover chain for Gemini Voice (TTS) calls:
     * 1. VOICE_PRIMARY
     * 2. VOICE_FALLBACK (if provided)
     */
    fun getVoiceKeyChain(): List<NamedApiKey> {
        return listOf(ApiKeySlot.VOICE_PRIMARY, ApiKeySlot.VOICE_FALLBACK)
            .mapNotNull { slot ->
                val value = getApiKey(slot)
                if (value.isNotBlank()) NamedApiKey(slot, value) else null
            }
            .distinctBy { it.key }
    }

    /**
     * Ordered key chain for ListModels discovery (tries preferred role first, then other configured keys).
     */
    fun getDiscoveryKeyChain(preferVoice: Boolean = false): List<NamedApiKey> {
        val order = if (preferVoice) {
            listOf(
                ApiKeySlot.VOICE_PRIMARY,
                ApiKeySlot.VOICE_FALLBACK,
                ApiKeySlot.TEXT_PRIMARY,
                ApiKeySlot.TEXT_FALLBACK
            )
        } else {
            listOf(
                ApiKeySlot.TEXT_PRIMARY,
                ApiKeySlot.TEXT_FALLBACK,
                ApiKeySlot.VOICE_PRIMARY,
                ApiKeySlot.VOICE_FALLBACK
            )
        }
        return order.mapNotNull { slot ->
            val value = getApiKey(slot)
            if (value.isNotBlank()) NamedApiKey(slot, value) else null
        }.distinctBy { it.key }
    }

    fun getMaskedPreview(slot: ApiKeySlot): String {
        val key = getApiKey(slot)
        if (key.isEmpty()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    fun clearApiKey(slot: ApiKeySlot) {
        prefs.edit().remove(slot.prefKey).apply()
        refreshFlows()
    }

    fun clearAllKeys() {
        prefs.edit().apply {
            ApiKeySlot.entries.forEach { remove(it.prefKey) }
            remove(LEGACY_KEY_GEMINI_API)
        }.apply()
        refreshFlows()
    }

    private fun migrateLegacySingleKeyIfPresent() {
        val legacy = prefs.getString(LEGACY_KEY_GEMINI_API, null) ?: return
        if (prefs.getString(ApiKeySlot.TEXT_PRIMARY.prefKey, null) == null) {
            prefs.edit()
                .putString(ApiKeySlot.TEXT_PRIMARY.prefKey, legacy)
                .remove(LEGACY_KEY_GEMINI_API)
                .apply()
        } else {
            prefs.edit().remove(LEGACY_KEY_GEMINI_API).apply()
        }
    }

    private fun buildSummary(): ApiKeySlotsSummary {
        val configured = mutableSetOf<ApiKeySlot>()
        val previews = mutableMapOf<ApiKeySlot, String>()
        for (slot in ApiKeySlot.entries) {
            val preview = getMaskedPreview(slot)
            if (preview.isNotEmpty()) {
                configured.add(slot)
                previews[slot] = preview
            }
        }
        return ApiKeySlotsSummary(configured, previews)
    }

    private fun refreshFlows() {
        val current = buildSummary()
        _summary.value = current
        _hasApiKey.value = current.hasAnyKey
    }

    private fun decryptPayload(stored: String): String {
        if (stored.startsWith(FALLBACK_PREFIX)) {
            return runCatching {
                val raw = Base64.decode(stored.removePrefix(FALLBACK_PREFIX), Base64.NO_WRAP)
                String(raw, StandardCharsets.UTF_8)
            }.getOrDefault("")
        }
        val parts = stored.split(IV_SEPARATOR)
        if (parts.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "voice_control_ai_gemini_key_v1"
        private const val PREFS_NAME = "voice_control_encrypted_prefs"
        private const val LEGACY_KEY_GEMINI_API = "gemini_api_key_enc"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = ":"
        private const val FALLBACK_PREFIX = "fb_v1:"
        private const val GCM_TAG_BITS = 128
    }
}
