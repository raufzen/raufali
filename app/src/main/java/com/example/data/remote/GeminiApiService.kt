package com.example.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.Type

/**
 * Retrofit service for the Gemini REST API (ListModels + GenerateContent).
 * Streaming SSE calls use OkHttp directly via GeminiClient for low-latency token delivery.
 */
interface GeminiApiService {

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): Response<ListModelsResponse>

    @POST("v1beta/{modelPath}:generateContent")
    suspend fun generateContent(
        @Path(value = "modelPath", encoded = true) modelPath: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>
}

/**
 * Lightweight Retrofit Converter.Factory powered by kotlinx.serialization.
 */
class KotlinxSerializationConverterFactory(
    private val json: Json
) : Converter.Factory() {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = kotlinx.serialization.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            val raw = body.string()
            json.decodeFromString(serializer, raw)
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = kotlinx.serialization.serializer(type)
        return Converter<Any, RequestBody> { value ->
            val encoded = json.encodeToString(serializer, value)
            encoded.toRequestBody(jsonMediaType)
        }
    }
}
