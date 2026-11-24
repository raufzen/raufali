package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AppSettings
import com.example.domain.model.AppLanguage
import com.example.domain.model.OemAutostartGuide
import com.example.domain.model.PermissionStatusItem
import com.example.domain.model.PermissionType
import com.example.domain.model.ThemeMode
import com.example.service.voice.ModelInstallState
import com.example.service.voice.TtsVoiceOption
import com.example.service.voice.VoiceAssistantForegroundService
import com.example.service.voice.VoskModelSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoicePermissionsViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val voskStates: StateFlow<Map<VoskModelSpec, ModelInstallState>> = container.voskModelManager.states
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val ttsVoices: StateFlow<List<TtsVoiceOption>> = container.ttsManager.availableVoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionStatuses = MutableStateFlow(container.permissionChecker.getAllStatuses())
    val permissionStatuses: StateFlow<List<PermissionStatusItem>> = _permissionStatuses.asStateFlow()

    val detectedManufacturer: String = container.permissionChecker.detectedManufacturer()
    val oemGuides: List<OemAutostartGuide> = container.permissionChecker.getOemAutostartGuides()

    fun refreshPermissions() {
        _permissionStatuses.value = container.permissionChecker.getAllStatuses()
        container.ttsManager.refreshAvailableVoices()
    }

    fun runtimePermissionFor(type: PermissionType): String? =
        container.permissionChecker.runtimePermissionString(type)

    fun openPermissionSettings(type: PermissionType) {
        container.permissionChecker.openPermissionSettings(type)
    }

    fun openOemAutostart(manufacturerKey: String) {
        container.permissionChecker.openOemAutostartSettings(manufacturerKey)
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.setAppLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setWakeWord(phrase: String) {
        viewModelScope.launch { container.settingsRepository.setWakeWord(phrase) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setWakeWordEnabled(enabled)
            syncForegroundService(enabled || settings.value.overlayEnabled)
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !container.permissionChecker.isGranted(PermissionType.OVERLAY)) {
                container.permissionChecker.openPermissionSettings(PermissionType.OVERLAY)
                return@launch
            }
            container.settingsRepository.setOverlayEnabled(enabled)
            syncForegroundService(enabled || settings.value.wakeWordEnabled)
        }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAutoStartOnBoot(enabled) }
    }

    fun setConfirmSensitiveActions(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setConfirmSensitiveActions(enabled) }
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setOfflineOnlyMode(enabled) }
    }

    fun setTtsLanguageCode(code: String) {
        viewModelScope.launch { container.settingsRepository.setTtsLanguageCode(code) }
    }

    fun setTtsVoice(voiceName: String) {
        viewModelScope.launch { container.settingsRepository.setTtsVoiceName(voiceName) }
    }

    fun setTtsSpeed(rate: Float) {
        viewModelScope.launch { container.settingsRepository.setTtsSpeechRate(rate) }
    }

    fun setTtsPitch(pitch: Float) {
        viewModelScope.launch { container.settingsRepository.setTtsPitch(pitch) }
    }

    fun testTtsVoice() {
        val sample = when (settings.value.appLanguage) {
            AppLanguage.ENGLISH -> "Hello! Voice Control AI is ready to assist you offline and online."
            AppLanguage.HINDI -> "नमस्ते! वॉइस कंट्रोल एआई आपकी मदद के लिए पूरी तरह तैयार है।"
            AppLanguage.HINGLISH -> "Namaste! Voice Control AI aapki madad ke liye ready hai."
        }
        container.ttsManager.speak(sample)
    }

    fun downloadVoskModel(spec: VoskModelSpec) {
        viewModelScope.launch { container.voskModelManager.downloadAndInstall(spec) }
    }

    fun deleteVoskModel(spec: VoskModelSpec) {
        viewModelScope.launch { container.voskModelManager.deleteModel(spec) }
    }

    private fun syncForegroundService(shouldRun: Boolean) {
        if (shouldRun && settings.value.masterEnabled) {
            VoiceAssistantForegroundService.start(appContext)
        } else if (!shouldRun) {
            VoiceAssistantForegroundService.stop(appContext)
        }
    }

    companion object {
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VoicePermissionsViewModel(container, context.applicationContext) as T
                }
            }
    }
}
