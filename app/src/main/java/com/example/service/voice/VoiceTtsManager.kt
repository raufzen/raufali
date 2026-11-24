package com.example.service.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.local.ApiKeySlot
import com.example.data.local.EncryptedApiKeyStore
import com.example.data.local.SettingsRepository
import com.example.data.remote.GeminiClient
import com.example.data.remote.GeminiError
import com.example.data.remote.GeminiVoiceCatalog
import com.example.data.remote.GeminiVoiceOption
import com.example.domain.model.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 100% Gemini API Native Voice Engine (NO local Android TextToSpeech engine).
 * Synthesizes natural Hindi/English/Hinglish speech using the user's Gemini Voice API keys
 * (VOICE_PRIMARY -> automatic failover to VOICE_FALLBACK on quota limit) and plays PCM/WAV
 * directly via AudioTrack with instant user barge-in interruption.
 */
class VoiceTtsManager(
    private val geminiClient: GeminiClient,
    private val apiKeyStore: EncryptedApiKeyStore,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeSpeakJob: Job? = null

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _lastVoiceError = MutableStateFlow<String?>(null)
    val lastVoiceError: StateFlow<String?> = _lastVoiceError.asStateFlow()

    private val _lastUsedKeySlot = MutableStateFlow<ApiKeySlot?>(null)
    val lastUsedKeySlot: StateFlow<ApiKeySlot?> = _lastUsedKeySlot.asStateFlow()

    val availableVoices: StateFlow<List<GeminiVoiceOption>> =
        MutableStateFlow(GeminiVoiceCatalog.voices).asStateFlow()

    fun speak(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        interrupt()

        activeSpeakJob = scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.offlineOnlyMode) {
                // Local Android TTS is intentionally disabled; skip speech in offline-only mode
                return@launch
            }

            val voiceKeyChain = apiKeyStore.getVoiceKeyChain()
            if (voiceKeyChain.isEmpty()) {
                _lastVoiceError.value = if (settings.appLanguage == AppLanguage.ENGLISH) {
                    "Add a Primary Voice API Key in Models & API Key settings to hear Gemini Voice replies."
                } else {
                    "जेमिनी वॉइस सुनने के लिए सेटिंग्स में प्राइमरी वॉइस API की जोड़ें।"
                }
                return@launch
            }

            val voiceModel = settings.voiceModelId
            if (voiceModel.isBlank()) {
                _lastVoiceError.value = if (settings.appLanguage == AppLanguage.ENGLISH) {
                    "Select a Gemini Voice model in Models & API Key settings."
                } else {
                    "सेटिंग्स में जेमिनी वॉइस मॉडल चुनें।"
                }
                return@launch
            }

            val paceHint = when {
                settings.voiceSpeed < 0.9f -> "calm and slightly slower"
                settings.voiceSpeed > 1.15f -> "brisk and energetic"
                else -> "natural"
            }

            _lastVoiceError.value = null
            _isSpeaking.value = true

            try {
                val result = geminiClient.synthesizeSpeechWithFallback(
                    keyChain = voiceKeyChain,
                    voiceModelName = voiceModel,
                    textToSpeak = cleaned,
                    voiceName = settings.geminiVoiceName,
                    speakingPaceHint = paceHint
                )

                result.fold(
                    onSuccess = { audio ->
                        _lastUsedKeySlot.value = audio.usedKeySlot
                        playAudioBytes(audio.audioBytes, audio.mimeType)
                    },
                    onFailure = { throwable ->
                        val errMsg = when (throwable) {
                            is GeminiError -> if (settings.appLanguage == AppLanguage.ENGLISH) {
                                throwable.messageEn
                            } else {
                                throwable.messageHi
                            }
                            else -> throwable.message ?: "Gemini Voice playback failed."
                        }
                        _lastVoiceError.value = errMsg
                    }
                )
            } catch (_: CancellationException) {
                // Interrupted by user barge-in or Stop button
            } finally {
                releaseAudioTrack()
                _isSpeaking.value = false
            }
        }
    }

    /** Immediately interrupts active Gemini audio synthesis or AudioTrack playback. */
    fun interrupt() {
        activeSpeakJob?.cancel()
        activeSpeakJob = null
        releaseAudioTrack()
        _isSpeaking.value = false
    }

    fun clearError() {
        _lastVoiceError.value = null
    }

    fun shutdown() {
        interrupt()
        scope.cancel()
    }

    private suspend fun playAudioBytes(rawBytes: ByteArray, mimeType: String) =
        withContext(Dispatchers.IO) {
            val (pcmData, sampleRate, channels) = decodePcmPayload(rawBytes, mimeType)
            if (pcmData.isEmpty()) return@withContext

            val channelMask = if (channels >= 2) {
                AudioFormat.CHANNEL_OUT_STEREO
            } else {
                AudioFormat.CHANNEL_OUT_MONO
            }
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
                .coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf * 2)
                .build()

            activeAudioTrack = track
            track.play()

            var offset = 0
            val chunkSize = 4096
            while (offset < pcmData.size) {
                val toWrite = minOf(chunkSize, pcmData.size - offset)
                val written = track.write(pcmData, offset, toWrite)
                if (written <= 0) break
                offset += written
            }

            runCatching { track.stop() }
        }

    private fun decodePcmPayload(rawBytes: ByteArray, mimeType: String): Triple<ByteArray, Int, Int> {
        // Handle RIFF/WAVE header if Gemini returned audio/wav
        if (rawBytes.size > 44 &&
            rawBytes[0] == 'R'.code.toByte() &&
            rawBytes[1] == 'I'.code.toByte() &&
            rawBytes[2] == 'F'.code.toByte() &&
            rawBytes[3] == 'F'.code.toByte()
        ) {
            val header = ByteBuffer.wrap(rawBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val channels = header.getShort(22).toInt().coerceIn(1, 2)
            val sampleRate = header.getInt(24).coerceIn(8000, 48000)
            // Locate "data" chunk safely
            var dataStart = 44
            for (i in 12 until minOf(rawBytes.size - 8, 256)) {
                if (rawBytes[i] == 'd'.code.toByte() &&
                    rawBytes[i + 1] == 'a'.code.toByte() &&
                    rawBytes[i + 2] == 't'.code.toByte() &&
                    rawBytes[i + 3] == 'a'.code.toByte()
                ) {
                    dataStart = i + 8
                    break
                }
            }
            val payload = if (dataStart < rawBytes.size) {
                rawBytes.copyOfRange(dataStart, rawBytes.size)
            } else {
                ByteArray(0)
            }
            return Triple(payload, sampleRate, channels)
        }

        // Standard Gemini raw linear PCM (e.g. "audio/L16;codec=pcm;rate=24000")
        val rateMatch = Regex("""rate=(\d+)""", RegexOption.IGNORE_CASE).find(mimeType)
        val parsedRate = rateMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(8000, 48000) ?: 24000
        return Triple(rawBytes, parsedRate, 1)
    }

    private fun releaseAudioTrack() {
        val track = activeAudioTrack ?: return
        activeAudioTrack = null
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }
}
