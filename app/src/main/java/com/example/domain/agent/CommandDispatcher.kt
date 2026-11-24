package com.example.domain.agent

import com.example.data.local.SettingsRepository
import com.example.data.repository.CommandHistoryRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.AssistantState
import com.example.domain.model.CommandResult
import com.example.domain.model.ConfirmationRequest
import com.example.domain.model.PermissionType
import com.example.domain.parser.GlobalNavAction
import com.example.domain.parser.LocalCommandParser
import com.example.domain.parser.ParsedCommand
import com.example.domain.safety.SensitiveActionGuard
import com.example.service.accessibility.AccessibilityBridge
import com.example.service.voice.VoiceSpeechRecognizer
import com.example.service.voice.VoiceTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Central coordinator that handles voice capture, TTS interruption, offline-first rule execution,
 * sensitive-action confirmations, and delegation to [AgentOrchestrator] for complex tasks.
 */
class CommandDispatcher(
    private val parser: LocalCommandParser,
    private val systemExecutor: SystemActionExecutor,
    private val agentOrchestrator: AgentOrchestrator,
    private val sensitiveActionGuard: SensitiveActionGuard,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: CommandHistoryRepository,
    private val speechRecognizer: VoiceSpeechRecognizer,
    private val ttsManager: VoiceTtsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastReply = MutableStateFlow("")
    val lastReply: StateFlow<String> = _lastReply.asStateFlow()

    private val _missingPermissionPrompt = MutableStateFlow<PermissionType?>(null)
    val missingPermissionPrompt: StateFlow<PermissionType?> = _missingPermissionPrompt.asStateFlow()

    private val _localPendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val localPendingConfirmation: StateFlow<ConfirmationRequest?> = _localPendingConfirmation.asStateFlow()

    private var pendingLocalAction: (suspend () -> CommandResult)? = null
    private var pendingLocalCommandText: String = ""

    init {
        scope.launch {
            ttsManager.isSpeaking.collectLatest { speaking ->
                if (speaking) {
                    _assistantState.value = AssistantState.SPEAKING
                } else if (_assistantState.value == AssistantState.SPEAKING) {
                    _assistantState.value = if (agentOrchestrator.taskState.value.isActive) {
                        AssistantState.THINKING
                    } else {
                        AssistantState.IDLE
                    }
                }
            }
        }

        scope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                ttsManager.applyVoiceConfig(
                    localeTag = settings.ttsLanguageCode,
                    voiceName = settings.ttsVoiceName,
                    speechRate = settings.ttsSpeechRate,
                    pitch = settings.ttsPitch
                )
            }
        }
    }

    fun toggleVoiceListening() {
        // User barge-in: tapping while speaking interrupts TTS immediately
        if (ttsManager.isSpeaking.value) {
            ttsManager.interrupt()
            return
        }
        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            _assistantState.value = AssistantState.IDLE
            return
        }

        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.masterEnabled) {
                val msg = if (settings.appLanguage == AppLanguage.ENGLISH) {
                    "Voice Control AI is paused. Turn on the master switch first."
                } else {
                    "वॉइस कंट्रोल AI बंद है। कृपया पहले मास्टर स्विच चालू करें।"
                }
                _lastReply.value = msg
                return@launch
            }

            _assistantState.value = AssistantState.LISTENING
            speechRecognizer.startSingleCommandListening(
                localeTag = settings.appLanguage.localeTag,
                onResult = { transcript ->
                    submitTextCommand(transcript)
                },
                onError = { errMsg ->
                    _assistantState.value = AssistantState.ERROR
                    _lastReply.value = errMsg
                }
            )
        }
    }

    fun submitTextCommand(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        _lastTranscript.value = text
        _missingPermissionPrompt.value = null

        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val lang = settings.appLanguage

            if (!settings.masterEnabled) {
                val msg = if (lang == AppLanguage.ENGLISH) {
                    "Assistant is turned off. Enable the Master Switch to run commands."
                } else {
                    "असिस्टेंट बंद है। कमांड चलाने के लिए मास्टर स्विच चालू करें।"
                }
                _lastReply.value = msg
                return@launch
            }

            // 1. Check if answering a pending sensitive action confirmation (local or agent)
            if (_localPendingConfirmation.value != null) {
                if (parser.isAffirmative(text)) {
                    confirmLocalSensitiveAction(true)
                    return@launch
                } else if (parser.isNegative(text)) {
                    confirmLocalSensitiveAction(false)
                    return@launch
                }
            }

            if (agentOrchestrator.taskState.value.pendingConfirmation != null) {
                if (parser.isAffirmative(text)) {
                    agentOrchestrator.resolveConfirmation(true)
                    return@launch
                } else if (parser.isNegative(text)) {
                    agentOrchestrator.resolveConfirmation(false)
                    return@launch
                }
            }

            // 2. Check if answering an AI agent ask_user question
            if (agentOrchestrator.taskState.value.pendingQuestion != null) {
                agentOrchestrator.submitUserAnswer(text)
                return@launch
            }

            // 3. Parse offline first
            val startTime = System.currentTimeMillis()
            val parsed = parser.parse(text)

            if (parsed is ParsedCommand.StopTask) {
                stopActiveTask()
                val msg = if (lang == AppLanguage.ENGLISH) "Stopped." else "रोक दिया गया।"
                announce(msg)
                return@launch
            }

            if (parsed is ParsedCommand.ComplexAiTask) {
                if (settings.offlineOnlyMode) {
                    val msg = if (lang == AppLanguage.ENGLISH) {
                        "Offline-Only Mode is enabled. Disable it in Settings to use Gemini AI for complex tasks."
                    } else {
                        "ऑफलाइन-ओनली मोड चालू है। जटिल काम के लिए सेटिंग्स में Gemini AI चालू करें।"
                    }
                    announce(msg)
                    return@launch
                }
                _assistantState.value = AssistantState.THINKING
                agentOrchestrator.startTask(
                    userCommand = text,
                    onSpeak = { spoken -> announce(spoken) },
                    onFinished = { ok, summary ->
                        _lastReply.value = summary
                        _assistantState.value = if (ok) AssistantState.IDLE else AssistantState.ERROR
                    }
                )
                return@launch
            }

            // 4. Execute offline command immediately
            val result = executeOfflineCommand(parsed, settings.confirmSensitiveActions, text)
            if (result is CommandResult.NeedsConfirmation) {
                _localPendingConfirmation.value = result.request
                _assistantState.value = AssistantState.CONFIRMING
                announce(result.localizedMessage(lang))
                return@launch
            }

            handleOfflineResult(text, lang, result, System.currentTimeMillis() - startTime)
        }
    }

    fun confirmLocalSensitiveAction(approved: Boolean) {
        val action = pendingLocalAction
        val cmdText = pendingLocalCommandText
        pendingLocalAction = null
        pendingLocalCommandText = ""
        _localPendingConfirmation.value = null

        scope.launch {
            val lang = settingsRepository.settingsFlow.first().appLanguage
            if (!approved || action == null) {
                val msg = if (lang == AppLanguage.ENGLISH) "Action cancelled." else "एक्शन रद्द कर दिया गया।"
                _assistantState.value = AssistantState.IDLE
                announce(msg)
                historyRepository.record(cmdText, lang.name, "OFFLINE", "CANCELLED", msg)
                return@launch
            }
            val start = System.currentTimeMillis()
            val result = action()
            handleOfflineResult(cmdText, lang, result, System.currentTimeMillis() - start)
        }
    }

    suspend fun executeDirectGlobal(action: GlobalNavAction) {
        val ctrl = AccessibilityBridge.controllerOrNull()
        val res = ctrl?.performGlobal(action) ?: AccessibilityBridge.missingServiceResult()
        val lang = settingsRepository.settingsFlow.first().appLanguage
        announce(res.localizedMessage(lang))
    }

    fun stopActiveTask() {
        ttsManager.interrupt()
        speechRecognizer.stopListening()
        agentOrchestrator.stopTask(silent = false)
        _localPendingConfirmation.value = null
        pendingLocalAction = null
        _assistantState.value = AssistantState.IDLE
    }

    fun clearMissingPermissionPrompt() {
        _missingPermissionPrompt.value = null
    }

    private suspend fun executeOfflineCommand(
        parsed: ParsedCommand,
        confirmSensitive: Boolean,
        rawText: String
    ): CommandResult {
        return when (parsed) {
            is ParsedCommand.OpenApp -> systemExecutor.openApp(parsed.appQuery)
            is ParsedCommand.VolumeChange -> systemExecutor.changeVolume(parsed.action, parsed.percent)
            is ParsedCommand.BrightnessChange -> systemExecutor.changeBrightness(parsed.action, parsed.percent)
            is ParsedCommand.Flashlight -> systemExecutor.setFlashlight(parsed.turnOn)
            is ParsedCommand.DoNotDisturb -> systemExecutor.setDoNotDisturb(parsed.enable)
            is ParsedCommand.OpenCamera -> systemExecutor.openCamera()
            is ParsedCommand.OpenSettings -> systemExecutor.openSettingsPage(parsed.page)
            is ParsedCommand.CallContact -> systemExecutor.callContact(parsed.contactName)
            is ParsedCommand.SetAlarm -> systemExecutor.setAlarm(parsed.hour, parsed.minute, parsed.label)
            is ParsedCommand.SetTimer -> systemExecutor.setTimer(parsed.durationSeconds, parsed.label)
            is ParsedCommand.SendSms -> {
                if (confirmSensitive) {
                    val req = sensitiveActionGuard.inspectToolAction(
                        toolName = "send_sms",
                        targetOrText = "${parsed.recipient}: ${parsed.message}",
                        screenTree = null
                    )
                    if (req != null) {
                        pendingLocalCommandText = rawText
                        pendingLocalAction = { systemExecutor.sendSms(parsed.recipient, parsed.message) }
                        return CommandResult.NeedsConfirmation(req)
                    }
                }
                systemExecutor.sendSms(parsed.recipient, parsed.message)
            }
            is ParsedCommand.GlobalAction -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.performGlobal(parsed.action)
            }
            is ParsedCommand.Scroll -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.scrollDirection(parsed.direction)
            }
            is ParsedCommand.Swipe -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.swipeDirection(parsed.direction)
            }
            is ParsedCommand.ClickTarget -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                if (confirmSensitive) {
                    val tree = AccessibilityBridge.captureScreenTree()
                    val req = sensitiveActionGuard.inspectToolAction("click", parsed.target, tree)
                    if (req != null) {
                        pendingLocalCommandText = rawText
                        pendingLocalAction = { ctrl.clickNode(parsed.target) }
                        return CommandResult.NeedsConfirmation(req)
                    }
                }
                ctrl.clickNode(parsed.target)
            }
            is ParsedCommand.LongClickTarget -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.longClickNode(parsed.target)
            }
            is ParsedCommand.TypeText -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.setText(parsed.text)
            }
            is ParsedCommand.ReadScreen -> {
                val tree = AccessibilityBridge.captureScreenTree()
                    ?: return AccessibilityBridge.missingServiceResult()
                val summary = tree.nodes
                    .mapNotNull { it.text.takeIf { t -> t.isNotBlank() } ?: it.contentDescription.takeIf { d -> d.isNotBlank() } }
                    .distinct()
                    .take(8)
                    .joinToString(", ")
                if (summary.isBlank()) {
                    CommandResult.Success("No readable text on this screen.", "इस स्क्रीन पर पढ़ने योग्य टेक्स्ट नहीं मिला।")
                } else {
                    CommandResult.Success("On screen: $summary", "स्क्रीन पर दिख रहा है: $summary")
                }
            }
            else -> CommandResult.Failed("Unsupported local command.", "यह कमांड समर्थित नहीं है।")
        }
    }

    private suspend fun handleOfflineResult(
        commandText: String,
        language: AppLanguage,
        result: CommandResult,
        durationMs: Long
    ) {
        val message = result.localizedMessage(language)
        val statusStr = when (result) {
            is CommandResult.Success -> {
                _assistantState.value = AssistantState.IDLE
                "SUCCESS"
            }
            is CommandResult.NeedsPermission -> {
                _missingPermissionPrompt.value = result.permissionType
                _assistantState.value = AssistantState.ERROR
                "NEEDS_PERMISSION"
            }
            is CommandResult.Failed -> {
                _assistantState.value = AssistantState.ERROR
                "FAILED"
            }
            is CommandResult.NeedsConfirmation -> "CONFIRMING"
        }

        historyRepository.record(
            commandText = commandText,
            language = language.name,
            executionMode = "OFFLINE",
            status = statusStr,
            responseSummary = message,
            stepCount = 1,
            durationMs = durationMs
        )
        announce(message)
    }

    private fun announce(text: String) {
        _lastReply.value = text
        ttsManager.speak(text)
    }
}
