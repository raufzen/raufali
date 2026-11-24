package com.example.data.remote

import kotlinx.serialization.Serializable
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
    val name: String,
    val displayName: String = "",
    val description: String = "",
    val version: String = "",
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val supportedGenerationMethods: List<String> = emptyList()
) {
    val shortId: String
        get() = name.removePrefix("models/")

    val supportsGenerateContent: Boolean
        get() = supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }

    val supportsBidiAudio: Boolean
        get() = supportedGenerationMethods.any { it.equals("bidiGenerateContent", ignoreCase = true) }

    /**
     * Capability heuristic derived solely from API-returned metadata
     * (supportedGenerationMethods, displayName, description) — never hardcoded model IDs.
     */
    val isVoiceFriendly: Boolean
        get() {
            val meta = "$displayName $description $shortId".lowercase()
            return supportsBidiAudio ||
                (supportsGenerateContent && (
                    meta.contains("tts") ||
                        meta.contains("audio") ||
                        meta.contains("voice") ||
                        meta.contains("speech") ||
                        meta.contains("live") ||
                        meta.contains("multimodal")
                    ))
        }
}

/**
 * Official Gemini API prebuilt voices for native Gemini audio output (responseModalities = ["AUDIO"]).
 * No local Android TTS engine is used.
 */
data class GeminiVoiceOption(
    val voiceName: String,
    val styleEn: String,
    val styleHi: String
)

object GeminiVoiceCatalog {
    val voices: List<GeminiVoiceOption> = listOf(
        GeminiVoiceOption("Kore", "Firm & Clear", "स्पष्ट और संतुलित"),
        GeminiVoiceOption("Aoede", "Warm & Conversational", "सहज और मधुर"),
        GeminiVoiceOption("Puck", "Upbeat & Lively", "ऊर्जावान और जीवंत"),
        GeminiVoiceOption("Charon", "Calm & Informative", "शांत और गंभीर"),
        GeminiVoiceOption("Fenrir", "Deep & Expressive", "गहरी और प्रभावशाली"),
        GeminiVoiceOption("Zephyr", "Bright & Friendly", "उजली और दोस्ताना"),
        GeminiVoiceOption("Leda", "Gentle & Youthful", "सौम्य और हल्की"),
        GeminiVoiceOption("Orus", "Steady & Direct", "स्थिर और सीधी")
    )
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
    val temperature: Double? = null,
    val topP: Double? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: GeminiSpeechConfig? = null
)

@Serializable
data class GeminiSpeechConfig(
    val voiceConfig: GeminiVoiceConfig
)

@Serializable
data class GeminiVoiceConfig(
    val prebuiltVoiceConfig: GeminiPrebuiltVoiceConfig
)

@Serializable
data class GeminiPrebuiltVoiceConfig(
    val voiceName: String
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
        messageEn = "Please add your Gemini API keys in Models & API Key settings.",
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
        messageEn = "Gemini API quota exceeded across configured keys. Add a fallback key or wait a moment.",
        messageHi = "जेमिनी एपीआई की सीमा (कोटा) पूरी हो गई है। फॉलबैक की जोड़ें या थोड़ी देर बाद प्रयास करें।"
    )

    data object NoNetwork : GeminiError(
        messageEn = "No internet connection. Basic offline voice commands still work without internet.",
        messageHi = "इंटरनेट कनेक्शन नहीं है। सामान्य ऑफलाइन वॉइस कमांड बिना इंटरनेट के काम करते हैं।"
    )

    data class ModelUnavailable(val modelId: String) : GeminiError(
        messageEn = "The selected model ($modelId) is not available or does not support this modality. Pick another model in Settings.",
        messageHi = "चुना गया मॉडल ($modelId) उपलब्ध नहीं है या इस मोड को सपोर्ट नहीं करता। कृपया सेटिंग्स में दूसरा मॉडल चुनें।"
    )

    data class ServerError(val detail: String) : GeminiError(
        messageEn = "Gemini service returned an error: $detail",
        messageHi = "जेमिनी सेवा में त्रुटि आई: $detail"
    )
}
