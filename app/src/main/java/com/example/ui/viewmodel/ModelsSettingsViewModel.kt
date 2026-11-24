package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ApiKeySlot
import com.example.data.local.ApiKeySlotsSummary
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
    val keySummary: ApiKeySlotsSummary = ApiKeySlotsSummary(),
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
 * Manages all 4 encrypted Gemini API key slots (2 Primary: Text & Voice, 2 Optional Fallbacks)
 * and dynamically discovers every model available via Gemini ListModels (zero hardcoded models).
 */
class ModelsSettingsViewModel(
    private val apiKeyStore: EncryptedApiKeyStore,
    private val geminiClient: GeminiClient,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ModelsSettingsUiState(keySummary = apiKeyStore.summary.value)
    )
    val uiState: StateFlow<ModelsSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            apiKeyStore.summary.collectLatest { summary ->
                _uiState.value = _uiState.value.copy(keySummary = summary)
            }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(
                    selectedReasoningModelId = settings.reasoningModelId,
                    selectedVoiceModelId = settings.voiceModelId
                )
            }
        }
        if (apiKeyStore.summary.value.hasAnyKey) {
            refreshModels()
        }
    }

    fun saveApiKey(slot: ApiKeySlot, rawKey: String) {
        val cleaned = rawKey.trim()
        apiKeyStore.saveApiKey(slot, cleaned)
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            statusBanner = null,
            missingModelWarning = null
        )
        if (apiKeyStore.summary.value.hasAnyKey) {
            refreshModels()
        }
    }

    fun clearApiKey(slot: ApiKeySlot) {
        apiKeyStore.clearApiKey(slot)
        val remaining = apiKeyStore.summary.value
        if (!remaining.hasAnyKey) {
            viewModelScope.launch {
                settingsRepository.setReasoningModelId("")
                settingsRepository.setVoiceModelId("")
            }
            _uiState.value = ModelsSettingsUiState(
                keySummary = remaining,
                statusBanner = "API key cleared."
            )
        } else {
            refreshModels()
        }
    }

    fun clearAllApiKeys() {
        apiKeyStore.clearAllKeys()
        viewModelScope.launch {
            settingsRepository.setReasoningModelId("")
            settingsRepository.setVoiceModelId("")
        }
        _uiState.value = ModelsSettingsUiState(
            keySummary = apiKeyStore.summary.value,
            statusBanner = "All 4 API key slots cleared from encrypted storage."
        )
    }

    fun refreshModels() {
        val textChain = apiKeyStore.getDiscoveryKeyChain(preferVoice = false)
        val voiceChain = apiKeyStore.getDiscoveryKeyChain(preferVoice = true)
        if (textChain.isEmpty() && voiceChain.isEmpty()) {
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

            val textResult = geminiClient.fetchAvailableModelsWithFallback(textChain)
            val voiceResult = if (voiceChain.firstOrNull()?.key != textChain.firstOrNull()?.key && voiceChain.isNotEmpty()) {
                geminiClient.fetchAvailableModelsWithFallback(voiceChain)
            } else {
                textResult
            }

            val textModels = textResult.getOrNull().orEmpty()
            val voiceModels = voiceResult.getOrNull().orEmpty()

            if (textModels.isEmpty() && voiceModels.isEmpty()) {
                val err = textResult.exceptionOrNull() ?: voiceResult.exceptionOrNull()
                val errText = when (err) {
                    is GeminiError -> if (lang == AppLanguage.ENGLISH) err.messageEn else err.messageHi
                    else -> err?.message ?: "Failed to load models."
                }
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    errorMessage = errText
                )
                return@launch
            }

            val mergedModels = (textModels + voiceModels).distinctBy { it.name }
            val reasoningList = (if (textModels.isNotEmpty()) textModels else mergedModels)
                .filter { it.supportsGenerateContent }
            val voiceList = (if (voiceModels.isNotEmpty()) voiceModels else mergedModels)
                .filter { it.supportsGenerateContent || it.supportsBidiAudio }
                .sortedByDescending { it.isVoiceFriendly }

            val currentSettings = settingsRepository.settingsFlow.first()
            val availableReasoningIds = reasoningList.map { it.name }.toSet()
            val availableVoiceIds = voiceList.map { it.name }.toSet()
            var disappearedNote: String? = null

            var nextReasoning = currentSettings.reasoningModelId
            if (nextReasoning.isNotBlank() && nextReasoning !in availableReasoningIds) {
                disappearedNote = if (lang == AppLanguage.ENGLISH) {
                    "Previously selected Text model (${nextReasoning.removePrefix("models/")}) is no longer returned by your API key. Switched automatically."
                } else {
                    "पहले चुना गया टेक्स्ट मॉडल (${nextReasoning.removePrefix("models/")}) अब उपलब्ध नहीं है। नया उपलब्ध मॉडल चुना गया है।"
                }
                nextReasoning = reasoningList.firstOrNull()?.name.orEmpty()
                settingsRepository.setReasoningModelId(nextReasoning)
            } else if (nextReasoning.isBlank() && reasoningList.isNotEmpty()) {
                nextReasoning = reasoningList.first().name
                settingsRepository.setReasoningModelId(nextReasoning)
            }

            var nextVoice = currentSettings.voiceModelId
            if (nextVoice.isNotBlank() && nextVoice !in availableVoiceIds) {
                nextVoice = voiceList.firstOrNull()?.name.orEmpty()
                settingsRepository.setVoiceModelId(nextVoice)
            } else if (nextVoice.isBlank() && voiceList.isNotEmpty()) {
                nextVoice = voiceList.first().name
                settingsRepository.setVoiceModelId(nextVoice)
            }

            _uiState.value = _uiState.value.copy(
                isLoadingModels = false,
                allModels = mergedModels,
                reasoningCandidates = reasoningList,
                voiceCandidates = voiceList,
                selectedReasoningModelId = nextReasoning,
                selectedVoiceModelId = nextVoice,
                missingModelWarning = disappearedNote,
                statusBanner = if (lang == AppLanguage.ENGLISH) {
                    "Loaded ${mergedModels.size} live models from Gemini API."
                } else {
                    "Gemini API से ${mergedModels.size} लाइव मॉडल लोड हो गए।"
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
