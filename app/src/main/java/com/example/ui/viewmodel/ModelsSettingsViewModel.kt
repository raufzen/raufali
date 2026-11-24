package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.EncryptedApiKeyStore
import com.example.data.local.SettingsRepository
import com.example.data.remote.GeminiClient
import com.example.data.remote.GeminiError
import com.example.data.remote.GeminiModelInfo
import com.example.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ModelsSettingsUiState(
    val apiKeyMasked: String = "",
    val hasApiKey: Boolean = false,
    val isLoadingModels: Boolean = false,
    val allModels: List<GeminiModelInfo> = emptyList(),
    val reasoningCandidates: List<GeminiModelInfo> = emptyList(),
    val voiceCandidates: List<GeminiModelInfo> = emptyList(),
    val selectedReasoningModelId: String = "",
    val selectedVoiceModelId: String = "",
    val missingModelWarning: String? = null,
    val errorMessage: String? = null,
    val statusBanner: String? = null
)

/**
 * Dynamically discovers every model available to the user's Gemini API key via ListModels.
 * Zero model names are ever hardcoded. Automatically reconciles disappeared models.
 */
class ModelsSettingsViewModel(
    private val apiKeyStore: EncryptedApiKeyStore,
    private val geminiClient: GeminiClient,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ModelsSettingsUiState(
            apiKeyMasked = apiKeyStore.getMaskedPreview(),
            hasApiKey = apiKeyStore.getApiKey().isNotBlank()
        )
    )
    val uiState: StateFlow<ModelsSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(
                    selectedReasoningModelId = settings.reasoningModelId,
                    selectedVoiceModelId = settings.voiceModelId
                )
            }
        }
        if (apiKeyStore.getApiKey().isNotBlank()) {
            refreshModels()
        }
    }

    fun saveApiKeyAndRefresh(rawKey: String) {
        val cleaned = rawKey.trim()
        apiKeyStore.saveApiKey(cleaned)
        _uiState.value = _uiState.value.copy(
            apiKeyMasked = apiKeyStore.getMaskedPreview(),
            hasApiKey = cleaned.isNotBlank(),
            errorMessage = null,
            statusBanner = null,
            missingModelWarning = null
        )
        if (cleaned.isNotBlank()) {
            refreshModels()
        } else {
            _uiState.value = _uiState.value.copy(
                allModels = emptyList(),
                reasoningCandidates = emptyList(),
                voiceCandidates = emptyList()
            )
        }
    }

    fun clearApiKey() {
        apiKeyStore.clearApiKey()
        viewModelScope.launch {
            settingsRepository.setReasoningModelId("")
            settingsRepository.setVoiceModelId("")
        }
        _uiState.value = ModelsSettingsUiState(
            statusBanner = "API key cleared from encrypted storage."
        )
    }

    fun refreshModels() {
        val key = apiKeyStore.getApiKey()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoadingModels = false,
                errorMessage = GeminiError.MissingApiKey.messageEn
            )
            return
        }

        viewModelScope.launch {
            val lang = settingsRepository.settingsFlow.first().appLanguage
            _uiState.value = _uiState.value.copy(
                isLoadingModels = true,
                errorMessage = null,
                statusBanner = null
            )

            val result = geminiClient.fetchAvailableModels(key)
            result.fold(
                onSuccess = { models ->
                    // Filter strictly by API-reported supportedGenerationMethods
                    val reasoningList = models.filter { it.supportsGenerateContent }
                    val voiceList = models
                        .filter { it.supportsBidiAudio || it.supportsGenerateContent }
                        .sortedByDescending { it.isVoiceFriendly }

                    val currentSettings = settingsRepository.settingsFlow.first()
                    val availableIds = models.map { it.name }.toSet()
                    var disappearedNote: String? = null

                    var nextReasoning = currentSettings.reasoningModelId
                    if (nextReasoning.isNotBlank() && nextReasoning !in availableIds) {
                        disappearedNote = if (lang == AppLanguage.ENGLISH) {
                            "Previously selected model (${nextReasoning.removePrefix("models/")}) is no longer returned by your API key. Switched to an available model."
                        } else {
                            "पहले चुना गया मॉडल (${nextReasoning.removePrefix("models/")}) अब उपलब्ध नहीं है। नया उपलब्ध मॉडल चुना गया है।"
                        }
                        nextReasoning = reasoningList.firstOrNull()?.name.orEmpty()
                        settingsRepository.setReasoningModelId(nextReasoning)
                    } else if (nextReasoning.isBlank() && reasoningList.isNotEmpty()) {
                        nextReasoning = reasoningList.first().name
                        settingsRepository.setReasoningModelId(nextReasoning)
                    }

                    var nextVoice = currentSettings.voiceModelId
                    if (nextVoice.isNotBlank() && nextVoice !in availableIds) {
                        nextVoice = voiceList.firstOrNull()?.name.orEmpty()
                        settingsRepository.setVoiceModelId(nextVoice)
                    } else if (nextVoice.isBlank() && voiceList.isNotEmpty()) {
                        nextVoice = voiceList.first().name
                        settingsRepository.setVoiceModelId(nextVoice)
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        allModels = models,
                        reasoningCandidates = reasoningList,
                        voiceCandidates = voiceList,
                        selectedReasoningModelId = nextReasoning,
                        selectedVoiceModelId = nextVoice,
                        missingModelWarning = disappearedNote,
                        statusBanner = if (lang == AppLanguage.ENGLISH) {
                            "Loaded ${models.size} live models from Gemini API."
                        } else {
                            "Gemini API से ${models.size} लाइव मॉडल लोड हो गए।"
                        }
                    )
                },
                onFailure = { throwable ->
                    val errText = when (throwable) {
                        is GeminiError -> if (lang == AppLanguage.ENGLISH) throwable.messageEn else throwable.messageHi
                        else -> throwable.message ?: "Failed to load models."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        errorMessage = errText
                    )
                }
            )
        }
    }

    fun selectReasoningModel(modelName: String) {
        viewModelScope.launch {
            settingsRepository.setReasoningModelId(modelName)
            _uiState.value = _uiState.value.copy(missingModelWarning = null)
        }
    }

    fun selectVoiceModel(modelName: String) {
        viewModelScope.launch {
            settingsRepository.setVoiceModelId(modelName)
        }
    }

    companion object {
        fun factory(
            apiKeyStore: EncryptedApiKeyStore,
            geminiClient: GeminiClient,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ModelsSettingsViewModel(apiKeyStore, geminiClient, settingsRepository) as T
            }
        }
    }
}
