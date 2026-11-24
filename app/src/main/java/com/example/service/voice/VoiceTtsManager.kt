package com.example.service.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class TtsVoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val isOffline: Boolean
)

/**
 * Bilingual Hindi + English TextToSpeech controller supporting selectable voices,
 * configurable speech rate & pitch, and instant user barge-in interruption.
 */
class VoiceTtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private var currentLocaleTag: String = "hi-IN"
    private var currentVoiceName: String = ""
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        _isSpeaking.value = false
                    }
                })
                refreshAvailableVoices()
                applyVoiceConfig(currentLocaleTag, currentVoiceName, currentRate, currentPitch)
            }
        }
    }

    fun applyVoiceConfig(
        localeTag: String,
        voiceName: String,
        speechRate: Float,
        pitch: Float
    ) {
        currentLocaleTag = localeTag
        currentVoiceName = voiceName
        currentRate = speechRate.coerceIn(0.5f, 2.0f)
        currentPitch = pitch.coerceIn(0.5f, 1.8f)

        val engine = tts ?: return
        if (!isReady) return

        val locale = Locale.forLanguageTag(currentLocaleTag)
        engine.language = locale
        engine.setSpeechRate(currentRate)
        engine.setPitch(currentPitch)

        if (currentVoiceName.isNotBlank()) {
            val match = runCatching {
                engine.voices?.firstOrNull { it.name == currentVoiceName }
            }.getOrNull()
            if (match != null) {
                engine.voice = match
            }
        }
    }

    fun refreshAvailableVoices() {
        val engine = tts ?: return
        if (!isReady) return
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        val filtered = voices
            .filter { v ->
                val lang = v.locale?.language?.lowercase().orEmpty()
                lang == "hi" || lang == "en"
            }
            .sortedWith(
                compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                    .thenBy { it.locale?.toLanguageTag().orEmpty() }
                    .thenBy { it.name }
            )
            .take(24)
            .map { v ->
                val tag = v.locale?.toLanguageTag() ?: "en-IN"
                val offlineTag = if (!v.isNetworkConnectionRequired) "Offline" else "Online"
                TtsVoiceOption(
                    name = v.name,
                    label = "${v.locale?.displayName ?: tag} ($offlineTag)",
                    localeTag = tag,
                    isOffline = !v.isNetworkConnectionRequired
                )
            }
        _availableVoices.value = filtered
    }

    fun speak(text: String, localeOverride: String? = null) {
        val cleaned = text.trim()
        if (cleaned.isEmpty() || !isReady) return
        val engine = tts ?: return

        if (localeOverride != null) {
            engine.language = Locale.forLanguageTag(localeOverride)
        } else {
            val hasDevanagari = cleaned.any { it in '\u0900'..'\u097F' }
            engine.language = if (hasDevanagari) Locale.forLanguageTag("hi-IN")
            else Locale.forLanguageTag(currentLocaleTag)
        }

        engine.setSpeechRate(currentRate)
        engine.setPitch(currentPitch)
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Immediately interrupts active speech (user barge-in). */
    fun interrupt() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        _isSpeaking.value = false
    }
}
