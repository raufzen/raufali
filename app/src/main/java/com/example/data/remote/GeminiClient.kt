package com.example.data.remote

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
    val isComplete: Boolean = false
)

/**
 * Resilient Gemini REST client with:
 * - Dynamic paginated ListModels discovery (zero hardcoded model names)
 * - SSE streaming via streamGenerateContent?alt=sse
 * - Automatic exponential-backoff retries on transient 429/503/network blips
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
            } catch (e: IOException) {
                Result.failure(GeminiError.NoNetwork)
            } catch (e: Exception) {
                Result.failure(GeminiError.ServerError(e.message ?: "Unexpected error"))
            }
        }

    /**
     * Streams a Gemini generation / function-calling turn over Server-Sent Events (SSE).
     */
    fun streamGenerateContent(
        apiKey: String,
        modelName: String,
        requestPayload: GenerateContentRequest
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw GeminiError.MissingApiKey
        if (modelName.isBlank()) throw GeminiError.MissingModel

        val normalizedModel = normalizeModelPath(modelName)
        val url = "${BASE_URL}v1beta/$normalizedModel:streamGenerateContent?alt=sse&key=${apiKey.trim()}"
        val serializedBody = json.encodeToString(GenerateContentRequest.serializer(), requestPayload)

        var lastError: Throwable? = null
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
                            emit(
                                StreamChunk(
                                    deltaText = textChunk,
                                    functionCalls = callsChunk,
                                    isComplete = false
                                )
                            )
                        }
                    }
                    emit(
                        StreamChunk(
                            deltaText = "",
                            functionCalls = collectedCalls,
                            isComplete = true
                        )
                    )
                    return@flow
                }
            } catch (e: GeminiError) {
                if (e is GeminiError.QuotaExceeded && attempt < MAX_RETRIES - 1) {
                    lastError = e
                    delay(RETRY_BASE_MS * (attempt + 1))
                } else {
                    throw e
                }
            } catch (e: UnknownHostException) {
                throw GeminiError.NoNetwork
            } catch (e: SocketTimeoutException) {
                lastError = GeminiError.NoNetwork
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_MS * (attempt + 1))
                } else {
                    throw GeminiError.NoNetwork
                }
            } catch (e: IOException) {
                lastError = GeminiError.NoNetwork
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_MS * (attempt + 1))
                } else {
                    throw GeminiError.NoNetwork
                }
            }
        }
        throw lastError ?: GeminiError.ServerError("Request failed after retries")
    }.flowOn(Dispatchers.IO)

    /**
     * Convenience non-streaming single-turn helper for quick voice/text Q&A using the user's chosen model.
     */
    suspend fun generateSingleReply(
        apiKey: String,
        modelName: String,
        requestPayload: GenerateContentRequest
    ): Result<GenerateContentResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(GeminiError.MissingApiKey)
        if (modelName.isBlank()) return@withContext Result.failure(GeminiError.MissingModel)

        val normalizedModel = normalizeModelPath(modelName)
        try {
            val response = executeWithRetry {
                apiService.generateContent(
                    modelPath = normalizedModel,
                    apiKey = apiKey.trim(),
                    request = requestPayload
                )
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    classifyHttpError(
                        code = response.code(),
                        errorBody = response.errorBody()?.string(),
                        modelId = normalizedModel
                    )
                )
            }
            val body = response.body()
                ?: return@withContext Result.failure(GeminiError.ServerError("Empty response"))
            Result.success(body)
        } catch (e: GeminiError) {
            Result.failure(e)
        } catch (_: IOException) {
            Result.failure(GeminiError.NoNetwork)
        } catch (e: Exception) {
            Result.failure(GeminiError.ServerError(e.message ?: "Unexpected error"))
        }
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

    private fun isRetryableCode(code: Int): Boolean = code == 429 || code == 500 || code == 503

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

            code == 404 || lower.contains("not found") || lower.contains("is not supported") ->
                GeminiError.ModelUnavailable(modelId.removePrefix("models/"))

            else -> GeminiError.ServerError(
                parsedMsg.ifBlank { "HTTP $code" }
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_MS = 1000L
    }
}
