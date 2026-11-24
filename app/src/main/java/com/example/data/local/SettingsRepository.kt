package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voice_control_settings"
)

data class AppSettings(
    val masterEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.HINGLISH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wakeWord: String = "Hey Assistant",
    val wakeWordEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val autoStartOnBoot: Boolean = true,
    val ttsLanguageCode: String = "hi-IN",
    val ttsVoiceName: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val reasoningModelId: String = "",
    val voiceModelId: String = "",
    val confirmSensitiveActions: Boolean = true,
    val offlineOnlyMode: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val TTS_LANGUAGE_CODE = stringPreferencesKey("tts_language_code")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val REASONING_MODEL_ID = stringPreferencesKey("reasoning_model_id")
        val VOICE_MODEL_ID = stringPreferencesKey("voice_model_id")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive_actions")
        val OFFLINE_ONLY_MODE = booleanPreferencesKey("offline_only_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            masterEnabled = prefs[Keys.MASTER_ENABLED] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            appLanguage = AppLanguage.fromKey(prefs[Keys.APP_LANGUAGE]),
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            wakeWord = prefs[Keys.WAKE_WORD] ?: "Hey Assistant",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            overlayEnabled = prefs[Keys.OVERLAY_ENABLED] ?: false,
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: true,
            ttsLanguageCode = prefs[Keys.TTS_LANGUAGE_CODE] ?: "hi-IN",
            ttsVoiceName = prefs[Keys.TTS_VOICE_NAME] ?: "",
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
            reasoningModelId = prefs[Keys.REASONING_MODEL_ID] ?: "",
            voiceModelId = prefs[Keys.VOICE_MODEL_ID] ?: "",
            confirmSensitiveActions = prefs[Keys.CONFIRM_SENSITIVE] ?: true,
            offlineOnlyMode = prefs[Keys.OFFLINE_ONLY_MODE] ?: false
        )
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.settingsDataStore.edit {
            it[Keys.APP_LANGUAGE] = language.name
            it[Keys.TTS_LANGUAGE_CODE] = when (language) {
                AppLanguage.HINDI, AppLanguage.HINGLISH -> "hi-IN"
                AppLanguage.ENGLISH -> "en-IN"
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setWakeWord(phrase: String) {
        val cleaned = phrase.trim().ifEmpty { "Hey Assistant" }
        context.settingsDataStore.edit { it[Keys.WAKE_WORD] = cleaned }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.OVERLAY_ENABLED] = enabled }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_START_ON_BOOT] = enabled }
    }

    suspend fun setTtsLanguageCode(code: String) {
        context.settingsDataStore.edit { it[Keys.TTS_LANGUAGE_CODE] = code }
    }

    suspend fun setTtsVoiceName(voiceName: String) {
        context.settingsDataStore.edit { it[Keys.TTS_VOICE_NAME] = voiceName }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        context.settingsDataStore.edit { it[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setTtsPitch(pitch: Float) {
        context.settingsDataStore.edit { it[Keys.TTS_PITCH] = pitch.coerceIn(0.5f, 1.8f) }
    }

    suspend fun setReasoningModelId(modelId: String) {
        context.settingsDataStore.edit { it[Keys.REASONING_MODEL_ID] = modelId }
    }

    suspend fun setVoiceModelId(modelId: String) {
        context.settingsDataStore.edit { it[Keys.VOICE_MODEL_ID] = modelId }
    }

    suspend fun setConfirmSensitiveActions(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CONFIRM_SENSITIVE] = enabled }
    }

    suspend fun setOfflineOnlyMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.OFFLINE_ONLY_MODE] = enabled }
    }
}
