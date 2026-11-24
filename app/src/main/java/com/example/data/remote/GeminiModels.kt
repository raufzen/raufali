package com.example.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Dynamic model metadata returned by Gemini ListModels endpoint.
 * Zero model names are ever hardcoded in the app.
 */
@Serializable
data class ListModelsResponse(
    val models: List<GeminiModelInfo> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class GeminiModelInfo(
    val name: String, // e.g. "models/..."
    val displayName: String = "",
    val description: String = "",
    val version: String = "",
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val supportedGenerationMethods: List<String> = emptyList()
) {
    /** Normalized short ID without leading "models/" prefix for compact UI badges. */
    val shortId: String
        get() = name.removePrefix("models/")

    val supportsGenerateContent: Boolean
        get() = supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }

    val supportsBidiAudio: Boolean
        get() = supportedGenerationMethods.any { it.equals("bidiGenerateContent", ignoreCase = true) }

    /**
     * Heuristic capability indicator based solely on API-reported metadata
     * (supportedGenerationMethods + description), never hardcoded model names.
     */
    val isVoiceFriendly: Boolean
        get() = supportsBidiAudio ||
            (supportsGenerateContent && (
                description.contains("audio", ignoreCase = true) ||
                    description.contains("live", ignoreCase = true) ||
                    description.contains("speech", ignoreCase = true) ||
                    description.contains("multimodal", ignoreCase = true) ||
                    description.contains("fast", ignoreCase = true) ||
                    description.contains("low-latency", ignoreCase = true)
                ))
}

@Serializable
data class GenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTool>? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    val topP: Double = 0.95
)

@Serializable
data class GeminiContent(
    val role: String? = null, // "user", "model", "function"
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val error: GeminiApiErrorBody? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiApiErrorEnvelope(
    val error: GeminiApiErrorBody? = null
)

@Serializable
data class GeminiApiErrorBody(
    val code: Int = 0,
    val message: String = "",
    val status: String = ""
)

sealed class GeminiError(
    val messageEn: String,
    val messageHi: String
) : Exception(messageEn) {

    data object MissingApiKey : GeminiError(
        messageEn = "Please add your Gemini API key in Models & API Key settings.",
        messageHi = "कृपया मॉडल और एपीआई की सेटिंग्स में अपनी जेमिनी एपीआई की डालें।"
    )

    data object MissingModel : GeminiError(
        messageEn = "No Gemini model is selected. Open Models settings and choose an available model.",
        messageHi = "कोई जेमिनी मॉडल चुना नहीं गया है। मॉडल सेटिंग्स में जाकर उपलब्ध मॉडल चुनें।"
    )

    data object InvalidApiKey : GeminiError(
        messageEn = "Your Gemini API key is invalid or unauthorized. Please check and update it in Settings.",
        messageHi = "आपकी जेमिनी एपीआई की गलत या अमान्य है। कृपया सेटिंग्स में इसे जांचें।"
    )

    data object QuotaExceeded : GeminiError(
        messageEn = "Gemini API quota or rate limit exceeded. Wait a moment or check your billing quota.",
        messageHi = "जेमिनी एपीआई की सीमा (कोटा) पूरी हो गई है। कृपया थोड़ी देर बाद प्रयास करें।"
    )

    data object NoNetwork : GeminiError(
        messageEn = "No internet connection. Basic offline voice commands still work without internet.",
        messageHi = "इंटरनेट कनेक्शन नहीं है। सामान्य ऑफलाइन वॉइस कमांड बिना इंटरनेट के काम करते हैं।"
    )

    data class ModelUnavailable(val modelId: String) : GeminiError(
        messageEn = "The selected model ($modelId) is no longer available for your API key. Please pick another model.",
        messageHi = "चुना गया मॉडल ($modelId) अब आपकी एपीआई की के लिए उपलब्ध नहीं है। कृपया दूसरा मॉडल चुनें।"
    )

    data class ServerError(val detail: String) : GeminiError(
        messageEn = "Gemini service returned an error: $detail",
        messageHi = "जेमिनी सेवा में त्रुटि आई: $detail"
    )
}
