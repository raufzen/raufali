package com.example.data.remote

import android.util.Base64
import com.example.data.local.ApiKeySlot
import com.example.data.local.NamedApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class StreamChunk(
    val deltaText: String = "",
    val functionCalls: List<GeminiFunctionCall> = emptyList(),
    val isComplete: Boolean = false,
    val usedKeySlot: ApiKeySlot? = null
)

data class SynthesizedAudioResult(
    val audioBytes: ByteArray,
    val mimeType: String,
    val usedKeySlot: ApiKeySlot
)

/**
 * Resilient Gemini REST client with:
 * - Dynamic paginated ListModels discovery (zero hardcoded model names)
 * - Automatic Primary -> Fallback API key rotation for both Text/Thinking and Voice roles
 * - SSE streaming via streamGenerateContent?alt=sse
 * - Native Gemini API Audio/Voice synthesis via responseModalities=["AUDIO"] + speechConfig
 * - Friendly bilingual error classification (English + Hindi)
 */
class GeminiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(KotlinxSerializationConverterFactory(json))
        .build()
        .create(GeminiApiService::class.java)

    /**
     * Fetches every model available across the provided key chain (tries Primary, then Fallback on failure).
     */
    suspend fun fetchAvailableModelsWithFallback(
        keyChain: List<NamedApiKey>
    ): Result<List<GeminiModelInfo>> {
        if (keyChain.isEmpty()) return Result.failure(GeminiError.MissingApiKey)
        var lastFailure: Throwable = GeminiError.MissingApiKey
        for (entry in keyChain) {
            val result = fetchAvailableModels(entry.key)
            if (result.isSuccess) return result
            lastFailure = result.exceptionOrNull() ?: lastFailure
            if (lastFailure is GeminiError.NoNetwork) return Result.failure(lastFailure)
        }
        return Result.failure(lastFailure)
    }

    /**
     * Loads every model available to the provided API key using pagination.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<GeminiModelInfo>> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(GeminiError.MissingApiKey)
            }
            val collected = mutableListOf<GeminiModelInfo>()
            var pageToken: String? = null
            var pages = 0

            try {
                do {
                    val response = executeWithRetry {
                        apiService.listModels(
                            apiKey = apiKey.trim(),
                            pageSize = 100,
                            pageToken = pageToken
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            classifyHttpError(
                                code = response.code(),
                                errorBody = response.errorBody()?.string(),
                                modelId = ""
                            )
                        )
                    }
                    val body = response.body() ?: ListModelsResponse()
                    collected.addAll(body.models)
                    pageToken = body.nextPageToken
                    pages++
                } while (!pageToken.isNullOrBlank() && pages < 5)

                val sorted = collected
                    .distinctBy { it.name }
                    .sortedWith(
                        compareByDescending<GeminiModelInfo> { it.supportsGenerateContent }
                            .thenBy { it.displayName.ifBlank { it.shortId } }
                    )
                Result.success(sorted)
            } catch (e: GeminiError) {
                Result.failure(e)
            } catch (_: IOException) {
                Result.failure(GeminiError.NoNetwork)
            } catch (e: Exception) {
                Result.failure(GeminiError.ServerError(e.message ?: "Unexpected error"))
            }
        }

    /**
     * Streams a Gemini generation / function-calling turn with automatic Primary -> Fallback API key failover.
     */
    fun streamGenerateContentWithFallback(
        keyChain: List<NamedApiKey>,
        modelName: String,
        requestPayload: GenerateContentRequest
    ): Flow<StreamChunk> = flow {
        if (keyChain.isEmpty()) throw GeminiError.MissingApiKey
        if (modelName.isBlank()) throw GeminiError.MissingModel

        val normalizedModel = normalizeModelPath(modelName)
        val serializedBody = json.encodeToString(GenerateContentRequest.serializer(), requestPayload)
        var lastError: Throwable? = null

        for ((keyIndex, namedKey) in keyChain.withIndex()) {
            val hasNextKey = keyIndex < keyChain.lastIndex
            val url = "${BASE_URL}v1beta/$normalizedModel:streamGenerateContent?alt=sse&key=${namedKey.key.trim()}"

            var emittedAnythingForThisKey = false
            var shouldTryNextKey = false

            for (attempt in 0 until MAX_RETRIES) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .post(serializedBody.toRequestBody(JSON_MEDIA_TYPE))
                        .header("Accept", "text/event-stream")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errStr = response.body?.string()
                            val classified = classifyHttpError(response.code, errStr, normalizedModel)

                            // If quota/auth error and we have a fallback key, immediately failover!
                            if ((classified is GeminiError.QuotaExceeded || classified is GeminiError.InvalidApiKey) && hasNextKey) {
                                lastError = classified
                                shouldTryNextKey = true
                                return@use
                            }

                            if (isRetryableCode(response.code) && attempt < MAX_RETRIES - 1) {
                                lastError = classified
                                delay(RETRY_BASE_MS * (attempt + 1))
                                return@use
                            }
                            throw classified
                        }

                        val source = response.body?.source()
                            ?: throw GeminiError.ServerError("Empty response stream")

                        val collectedCalls = mutableListOf<GeminiFunctionCall>()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val dataPayload = line.removePrefix("data:").trim()
                            if (dataPayload.isEmpty() || dataPayload == "[DONE]") continue

                            val parsed = runCatching {
                                json.decodeFromString(GenerateContentResponse.serializer(), dataPayload)
                            }.getOrNull() ?: continue

                            val parts = parsed.candidates.firstOrNull()?.content?.parts.orEmpty()
                            val textChunk = parts.mapNotNull { it.text }.joinToString("")
                            val callsChunk = parts.mapNotNull { it.functionCall }
                            if (callsChunk.isNotEmpty()) {
                                collectedCalls.addAll(callsChunk)
                            }
                            if (textChunk.isNotEmpty() || callsChunk.isNotEmpty()) {
                                emittedAnythingForThisKey = true
                                emit(
                                    StreamChunk(
                                        deltaText = textChunk,
                                        functionCalls = callsChunk,
                                        isComplete = false,
                                        usedKeySlot = namedKey.slot
                                    )
                                )
                            }
                        }
                        emit(
                            StreamChunk(
                                deltaText = "",
                                functionCalls = collectedCalls,
                                isComplete = true,
                                usedKeySlot = namedKey.slot
                            )
                        )
                        return@flow
                    }
                } catch (e: GeminiError) {
                    lastError = e
                    if ((e is GeminiError.QuotaExceeded || e is GeminiError.InvalidApiKey) && hasNextKey && !emittedAnythingForThisKey) {
                        shouldTryNextKey = true
                        break
                    }
                    if (e is GeminiError.QuotaExceeded && attempt < MAX_RETRIES - 1) {
                        delay(RETRY_BASE_MS * (attempt + 1))
                    } else {
                        throw e
                    }
                } catch (_: UnknownHostException) {
                    throw GeminiError.NoNetwork
                } catch (_: SocketTimeoutException) {
                    lastError = GeminiError.NoNetwork
                    if (attempt < MAX_RETRIES - 1) {
                        delay(RETRY_BASE_MS * (attempt + 1))
                    } else {
                        throw GeminiError.NoNetwork
                    }
                } catch (_: IOException) {
                    lastError = GeminiError.NoNetwork
                    if (attempt < MAX_RETRIES - 1) {
                        delay(RETRY_BASE_MS * (attempt + 1))
                    } else {
                        throw GeminiError.NoNetwork
                    }
                }

                if (shouldTryNextKey) break
            }
        }
        throw lastError ?: GeminiError.ServerError("Request failed across configured API keys")
    }.flowOn(Dispatchers.IO)

    /**
     * Generates native spoken audio directly from the Gemini API using the user's selected
     * Voice Model and Gemini prebuilt voice (e.g., Kore, Aoede, Puck), with automatic
     * VOICE_PRIMARY -> VOICE_FALLBACK API key failover on quota or auth errors.
     */
    suspend fun synthesizeSpeechWithFallback(
        keyChain: List<NamedApiKey>,
        voiceModelName: String,
        textToSpeak: String,
        voiceName: String,
        speakingPaceHint: String
    ): Result<SynthesizedAudioResult> = withContext(Dispatchers.IO) {
        if (keyChain.isEmpty()) return@withContext Result.failure(GeminiError.MissingApiKey)
        if (voiceModelName.isBlank()) return@withContext Result.failure(GeminiError.MissingModel)

        val normalizedModel = normalizeModelPath(voiceModelName)
        val prompt = buildString {
            append("Read the following response aloud naturally")
            if (speakingPaceHint.isNotBlank()) {
                append(" at a $speakingPaceHint pace")
            }
            append(". Speak only the response text without adding extra commentary:\n")
            append(textToSpeak)
        }

        val requestPayload = GenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = GeminiSpeechConfig(
                    voiceConfig = GeminiVoiceConfig(
                        prebuiltVoiceConfig = GeminiPrebuiltVoiceConfig(
                            voiceName = voiceName.ifBlank { "Kore" }
                        )
                    )
                )
            )
        )

        var lastFailure: Throwable = GeminiError.MissingApiKey

        for ((index, namedKey) in keyChain.withIndex()) {
            val hasNextKey = index < keyChain.lastIndex
            try {
                val response = executeWithRetry {
                    apiService.generateContent(
                        modelPath = normalizedModel,
                        apiKey = namedKey.key.trim(),
                        request = requestPayload
                    )
                }
                if (!response.isSuccessful) {
                    val classified = classifyHttpError(
                        code = response.code(),
                        errorBody = response.errorBody()?.string(),
                        modelId = normalizedModel
                    )
                    lastFailure = classified
                    if ((classified is GeminiError.QuotaExceeded || classified is GeminiError.InvalidApiKey) && hasNextKey) {
                        continue
                    }
                    return@withContext Result.failure(classified)
                }

                val body = response.body()
                    ?: return@withContext Result.failure(GeminiError.ServerError("Empty audio response"))

                val inlineAudio = body.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull { it.inlineData != null }
                    ?.inlineData
                    ?: return@withContext Result.failure(
                        GeminiError.ModelUnavailable(normalizedModel.removePrefix("models/"))
                    )

                val decodedBytes = Base64.decode(inlineAudio.data, Base64.DEFAULT)
                if (decodedBytes.isEmpty()) {
                    return@withContext Result.failure(GeminiError.ServerError("Decoded audio buffer was empty"))
                }

                return@withContext Result.success(
                    SynthesizedAudioResult(
                        audioBytes = decodedBytes,
                        mimeType = inlineAudio.mimeType,
                        usedKeySlot = namedKey.slot
                    )
                )
            } catch (e: GeminiError) {
                lastFailure = e
                if ((e is GeminiError.QuotaExceeded || e is GeminiError.InvalidApiKey) && hasNextKey) {
                    continue
                }
                return@withContext Result.failure(e)
            } catch (_: IOException) {
                return@withContext Result.failure(GeminiError.NoNetwork)
            } catch (e: Exception) {
                lastFailure = GeminiError.ServerError(e.message ?: "Voice synthesis failed")
                if (!hasNextKey) return@withContext Result.failure(lastFailure)
            }
        }

        Result.failure(lastFailure)
    }

    private suspend fun <T> executeWithRetry(
        block: suspend () -> retrofit2.Response<T>
    ): retrofit2.Response<T> {
        var attempt = 0
        var lastException: Exception? = null
        while (attempt < MAX_RETRIES) {
            try {
                val res = block()
                if (!isRetryableCode(res.code()) || attempt == MAX_RETRIES - 1) {
                    return res
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt == MAX_RETRIES - 1) throw e
            }
            attempt++
            delay(RETRY_BASE_MS * attempt)
        }
        throw lastException ?: IOException("Network retry exhausted")
    }

    private fun isRetryableCode(code: Int): Boolean = code == 500 || code == 503

    private fun normalizeModelPath(modelName: String): String {
        val trimmed = modelName.trim()
        return if (trimmed.startsWith("models/")) trimmed else "models/$trimmed"
    }

    private fun classifyHttpError(code: Int, errorBody: String?, modelId: String): GeminiError {
        val parsedMsg = errorBody?.let { raw ->
            runCatching {
                json.decodeFromString(GeminiApiErrorEnvelope.serializer(), raw).error?.message
            }.getOrNull()
        }.orEmpty()

        val lower = parsedMsg.lowercase()
        return when {
            code == 401 || code == 403 ||
                lower.contains("api key not valid") ||
                lower.contains("api_key_invalid") ||
                lower.contains("permission_denied") -> GeminiError.InvalidApiKey

            code == 429 || lower.contains("quota") || lower.contains("resource_exhausted") ->
                GeminiError.QuotaExceeded

            code == 404 ||
                lower.contains("not found") ||
                lower.contains("is not supported") ||
                lower.contains("modality") ||
                lower.contains("audio output") ->
                GeminiError.ModelUnavailable(modelId.removePrefix("models/"))

            else -> GeminiError.ServerError(parsedMsg.ifBlank { "HTTP $code" })
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RETRIES = 2
        private const val RETRY_BASE_MS = 800L
    }
}
