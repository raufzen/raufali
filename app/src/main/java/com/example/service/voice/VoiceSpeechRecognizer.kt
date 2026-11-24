package com.example.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Offline-first Speech Recognizer supporting Hindi (hi-IN), English (en-IN), and Hinglish.
 * Uses local Vosk models when installed + Android on-device SpeechRecognizer (EXTRA_PREFER_OFFLINE)
 * as a universal, low-latency fallback, with live partial results and RMS amplitude for orb visuals.
 */
class VoiceSpeechRecognizer(
    private val context: Context,
    private val voskModelManager: VoskModelManager
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _engineLabel = MutableStateFlow("On-Device Offline")
    val engineLabel: StateFlow<String> = _engineLabel.asStateFlow()

    private var onFinalCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var wakeWordMode = false
    private var activeWakeWord = "hey assistant"
    private var activeLocaleTag = "hi-IN"

    fun startSingleCommandListening(
        localeTag: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        wakeWordMode = false
        activeLocaleTag = localeTag
        onFinalCallback = onResult
        onErrorCallback = onError
        updateEngineLabel(localeTag)
        startRecognizerOnMainThread(preferOffline = true)
    }

    fun startWakeWordLoop(
        wakeWord: String,
        localeTag: String,
        onWakeWordDetected: (trailingCommand: String?) -> Unit
    ) {
        wakeWordMode = true
        activeWakeWord = wakeWord.trim().lowercase().ifEmpty { "hey assistant" }
        activeLocaleTag = localeTag
        onFinalCallback = { transcript ->
            val lower = transcript.lowercase().trim()
            if (lower.contains(activeWakeWord)) {
                val trailing = lower.substringAfter(activeWakeWord).trim().takeIf { it.isNotEmpty() }
                onWakeWordDetected(trailing)
            } else if (lower == "stop" || lower == "ruko" || lower == "रुको") {
                onWakeWordDetected(lower)
            }
            if (wakeWordMode) {
                mainHandler.postDelayed({ startRecognizerOnMainThread(preferOffline = true) }, 450L)
            }
        }
        onErrorCallback = {
            if (wakeWordMode) {
                mainHandler.postDelayed({ startRecognizerOnMainThread(preferOffline = true) }, 900L)
            }
        }
        updateEngineLabel(localeTag)
        startRecognizerOnMainThread(preferOffline = true)
    }

    fun stopListening() {
        wakeWordMode = false
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
            _isListening.value = false
            _rmsLevel.value = 0f
            _partialText.value = ""
        }
    }

    fun destroy() {
        stopListening()
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    private fun updateEngineLabel(localeTag: String) {
        val spec = if (localeTag.startsWith("hi", ignoreCase = true)) {
            VoskModelSpec.HINDI
        } else {
            VoskModelSpec.ENGLISH
        }
        val hasVosk = voskModelManager.getInstalledModelDir(spec) != null
        _engineLabel.value = if (hasVosk) {
            "Vosk Local (${spec.id.uppercase()}) + On-Device"
        } else {
            "Android On-Device Offline"
        }
    }

    private fun startRecognizerOnMainThread(preferOffline: Boolean) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _isListening.value = false
                onErrorCallback?.invoke("Speech recognition service unavailable on this device.")
                return@post
            }

            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialText.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f)
                        _rmsLevel.value = min(1f, max(0f, normalized))
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        // If offline pack is not yet downloaded by Android Speech Services, retry once without strict flag
                        if (preferOffline &&
                            (error == SpeechRecognizer.ERROR_NETWORK ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                        ) {
                            startRecognizerOnMainThread(preferOffline = false)
                            return
                        }
                        onErrorCallback?.invoke(errorCodeToMessage(error))
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        _partialText.value = best
                        if (best.isNotEmpty()) {
                            onFinalCallback?.invoke(best)
                        } else {
                            onErrorCallback?.invoke("Didn't catch that. Please speak again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialText.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLocaleTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLocaleTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            runCatching {
                speechRecognizer?.startListening(intent)
            }.onFailure { err ->
                _isListening.value = false
                onErrorCallback?.invoke(err.message ?: "Could not start microphone")
            }
        }
    }

    private fun errorCodeToMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, retrying…"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Offline speech pack unavailable; check network or download Vosk model"
        else -> "Speech recognition error ($error)"
    }
}
