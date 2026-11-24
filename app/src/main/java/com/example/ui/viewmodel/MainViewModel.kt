package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AppSettings
import com.example.data.local.CommandHistoryEntity
import com.example.domain.model.AgentTaskState
import com.example.domain.model.AppLanguage
import com.example.domain.model.AssistantState
import com.example.domain.model.ConfirmationRequest
import com.example.domain.model.PermissionType
import com.example.service.accessibility.AccessibilityBridge
import com.example.service.voice.VoiceAssistantForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val assistantState: AssistantState = AssistantState.IDLE,
    val isAccessibilityConnected: Boolean = false,
    val hasApiKey: Boolean = false,
    val partialTranscript: String = "",
    val lastTranscript: String = "",
    val lastReply: String = "",
    val rmsLevel: Float = 0f,
    val engineLabel: String = "On-Device Offline",
    val recentCommands: List<CommandHistoryEntity> = emptyList(),
    val agentTask: AgentTaskState = AgentTaskState(),
    val pendingConfirmation: ConfirmationRequest? = null,
    val missingPermission: PermissionType? = null
)

class MainViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val voiceStreamFlow = combine(
        container.commandDispatcher.assistantState,
        container.speechRecognizer.partialText,
        container.commandDispatcher.lastTranscript,
        container.commandDispatcher.lastReply,
        container.speechRecognizer.rmsLevel
    ) { state, partial, lastTxt, reply, rms ->
        VoiceTelemetry(state, partial, lastTxt, reply, rms)
    }

    private val systemStreamFlow = combine(
        AccessibilityBridge.isConnected,
        container.apiKeyStore.hasApiKey,
        container.speechRecognizer.engineLabel,
        container.historyRepository.recentHistory(6),
        container.commandDispatcher.missingPermissionPrompt
    ) { a11y, hasKey, engine, recent, missingPerm ->
        SystemTelemetry(a11y, hasKey, engine, recent, missingPerm)
    }

    val homeUiState: StateFlow<HomeUiState> = combine(
        settings,
        voiceStreamFlow,
        systemStreamFlow,
        container.agentOrchestrator.taskState,
        container.commandDispatcher.localPendingConfirmation
    ) { cfg, voice, sys, agent, localConfirm ->
        HomeUiState(
            settings = cfg,
            assistantState = voice.state,
            isAccessibilityConnected = sys.a11y || container.permissionChecker.isAccessibilityServiceEnabled(),
            hasApiKey = sys.hasKey,
            partialTranscript = voice.partial,
            lastTranscript = voice.lastTxt,
            lastReply = voice.reply,
            rmsLevel = voice.rms,
            engineLabel = sys.engine,
            recentCommands = sys.recent,
            agentTask = agent,
            pendingConfirmation = localConfirm ?: agent.pendingConfirmation,
            missingPermission = sys.missingPerm
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleMasterSwitch(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setMasterEnabled(enabled)
            if (!enabled) {
                container.commandDispatcher.stopActiveTask()
                VoiceAssistantForegroundService.stop(appContext)
            } else if (settings.value.overlayEnabled || settings.value.wakeWordEnabled) {
                VoiceAssistantForegroundService.start(appContext)
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.setAppLanguage(language) }
    }

    fun toggleListening() {
        container.commandDispatcher.toggleVoiceListening()
    }

    fun submitCommand(text: String) {
        container.commandDispatcher.submitTextCommand(text)
    }

    fun stopActiveTask() {
        container.commandDispatcher.stopActiveTask()
    }

    fun resolveConfirmation(approved: Boolean) {
        if (container.commandDispatcher.localPendingConfirmation.value != null) {
            container.commandDispatcher.confirmLocalSensitiveAction(approved)
        } else {
            container.agentOrchestrator.resolveConfirmation(approved)
        }
    }

    fun answerAgentQuestion(answer: String) {
        container.agentOrchestrator.submitUserAnswer(answer)
    }

    fun openPermissionSettings(type: PermissionType) {
        container.permissionChecker.openPermissionSettings(type)
        container.commandDispatcher.clearMissingPermissionPrompt()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            container.settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            container.settingsRepository.setOnboardingCompleted(false)
        }
    }

    private data class VoiceTelemetry(
        val state: AssistantState,
        val partial: String,
        val lastTxt: String,
        val reply: String,
        val rms: Float
    )

    private data class SystemTelemetry(
        val a11y: Boolean,
        val hasKey: Boolean,
        val engine: String,
        val recent: List<CommandHistoryEntity>,
        val missingPerm: PermissionType?
    )

    companion object {
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(container, context.applicationContext) as T
                }
            }
    }
}
