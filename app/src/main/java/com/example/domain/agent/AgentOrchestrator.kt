package com.example.domain.agent

import com.example.data.local.EncryptedApiKeyStore
import com.example.data.local.SettingsRepository
import com.example.data.remote.GeminiClient
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiError
import com.example.data.remote.GeminiFunctionCall
import com.example.data.remote.GeminiFunctionDeclaration
import com.example.data.remote.GeminiFunctionResponse
import com.example.data.remote.GeminiGenerationConfig
import com.example.data.remote.GeminiPart
import com.example.data.remote.GeminiTool
import com.example.data.remote.GenerateContentRequest
import com.example.data.repository.CommandHistoryRepository
import com.example.domain.model.AgentStepLog
import com.example.domain.model.AgentTaskState
import com.example.domain.model.AgentTaskStatus
import com.example.domain.model.AppLanguage
import com.example.domain.model.CommandResult
import com.example.domain.model.ConfirmationRequest
import com.example.domain.model.StepOutcome
import com.example.domain.parser.GlobalNavAction
import com.example.domain.parser.ScrollDirection
import com.example.domain.safety.SensitiveActionGuard
import com.example.service.accessibility.AccessibilityBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Multi-step Gemini Function-Calling AI Agent Loop:
 * 1. Sends user command + live sanitized ScreenTree to the user's chosen Gemini model (never hardcoded).
 * 2. Executes tool calls via AccessibilityService & SystemActionExecutor.
 * 3. Re-reads screen state after each action and feeds results back until finish() is called.
 * 4. Enforces safety limits: max 20 steps, loop detection, sensitive action confirmations, and instant stop.
 */
class AgentOrchestrator(
    private val geminiClient: GeminiClient,
    private val apiKeyStore: EncryptedApiKeyStore,
    private val settingsRepository: SettingsRepository,
    private val systemExecutor: SystemActionExecutor,
    private val sensitiveActionGuard: SensitiveActionGuard,
    private val historyRepository: CommandHistoryRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _taskState = MutableStateFlow(AgentTaskState())
    val taskState: StateFlow<AgentTaskState> = _taskState.asStateFlow()

    @Volatile
    private var pendingConfirmationDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingUserAnswerDeferred: CompletableDeferred<String>? = null

    fun startTask(
        userCommand: String,
        onSpeak: (String) -> Unit,
        onFinished: (Boolean, String) -> Unit
    ) {
        stopTask(silent = true)
        val taskId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        activeJob = scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val language = settings.appLanguage
            val textKeyChain = apiKeyStore.getTextKeyChain()
            val modelId = settings.reasoningModelId

            _taskState.value = AgentTaskState(
                taskId = taskId,
                userCommand = userCommand,
                status = AgentTaskStatus.RUNNING,
                currentStep = 0,
                maxSteps = MAX_STEPS,
                activeActionLabel = if (language == AppLanguage.ENGLISH) {
                    "Analyzing screen & planning…"
                } else {
                    "स्क्रीन समझकर प्लान बना रहा हूँ…"
                }
            )

            if (textKeyChain.isEmpty()) {
                failTask(
                    userCommand,
                    language,
                    startTime,
                    GeminiError.MissingApiKey.localized(language),
                    onSpeak,
                    onFinished
                )
                return@launch
            }

            if (modelId.isBlank()) {
                failTask(
                    userCommand,
                    language,
                    startTime,
                    GeminiError.MissingModel.localized(language),
                    onSpeak,
                    onFinished
                )
                return@launch
            }

            val conversation = mutableListOf<GeminiContent>()
            val initialScreen = AccessibilityBridge.captureScreenTree()
            val initialScreenText = initialScreen?.toCompactPromptText()
                ?: "Accessibility Service is NOT connected. Only system tools (open_app, call_contact, send_sms, wait, ask_user, finish) can work."

            conversation.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(
                            text = buildString {
                                appendLine("User Command: $userCommand")
                                appendLine("Preferred Reply Language: ${language.displayName}")
                                appendLine()
                                appendLine("Current Screen State:")
                                appendLine(initialScreenText)
                            }
                        )
                    )
                )
            )

            val recentSignatures = ArrayDeque<String>()
            var stepsExecuted = 0

            try {
                while (stepsExecuted < MAX_STEPS) {
                    stepsExecuted++
                    _taskState.value = _taskState.value.copy(
                        currentStep = stepsExecuted,
                        status = AgentTaskStatus.RUNNING,
                        streamingThought = "",
                        activeActionLabel = "Step $stepsExecuted/$MAX_STEPS • Contacting Gemini…"
                    )

                    val request = GenerateContentRequest(
                        contents = conversation.toList(),
                        systemInstruction = buildSystemInstruction(language),
                        tools = listOf(buildAgentTools()),
                        generationConfig = GeminiGenerationConfig(temperature = 0.2)
                    )

                    val thoughtBuffer = StringBuilder()
                    val toolCalls = mutableListOf<GeminiFunctionCall>()

                    geminiClient.streamGenerateContentWithFallback(textKeyChain, modelId, request).collect { chunk ->
                        if (chunk.deltaText.isNotEmpty()) {
                            thoughtBuffer.append(chunk.deltaText)
                            _taskState.value = _taskState.value.copy(
                                streamingThought = thoughtBuffer.toString()
                            )
                        }
                        if (chunk.isComplete && chunk.functionCalls.isNotEmpty()) {
                            toolCalls.addAll(chunk.functionCalls)
                        }
                    }

                    val modelParts = buildList {
                        if (thoughtBuffer.isNotEmpty()) add(GeminiPart(text = thoughtBuffer.toString()))
                        toolCalls.forEach { add(GeminiPart(functionCall = it)) }
                    }
                    if (modelParts.isNotEmpty()) {
                        conversation.add(GeminiContent(role = "model", parts = modelParts))
                    }

                    // If Gemini returned plain text without tool calls, treat it as final answer
                    if (toolCalls.isEmpty()) {
                        val reply = thoughtBuffer.toString().trim().ifEmpty {
                            if (language == AppLanguage.ENGLISH) "Task completed." else "काम पूरा हो गया।"
                        }
                        completeTask(userCommand, language, stepsExecuted, startTime, reply, onSpeak, onFinished)
                        return@launch
                    }

                    val functionResponseParts = mutableListOf<GeminiPart>()

                    for (call in toolCalls) {
                        val toolName = call.name
                        val args = call.args

                        if (toolName == "finish") {
                            val msg = args.str("message").ifBlank {
                                if (language == AppLanguage.ENGLISH) "Task completed." else "टास्क पूरा हो गया।"
                            }
                            appendStepLog(
                                AgentStepLog(
                                    stepNumber = stepsExecuted,
                                    toolName = "finish",
                                    actionSummary = "Completed",
                                    detail = msg,
                                    outcome = StepOutcome.SUCCESS
                                )
                            )
                            completeTask(userCommand, language, stepsExecuted, startTime, msg, onSpeak, onFinished)
                            return@launch
                        }

                        if (toolName == "ask_user") {
                            val question = args.str("question").ifBlank {
                                if (language == AppLanguage.ENGLISH) "How would you like to proceed?" else "आप आगे क्या करना चाहते हैं?"
                            }
                            val answer = requestUserAnswer(question, stepsExecuted, onSpeak)
                            functionResponseParts.add(
                                GeminiPart(
                                    functionResponse = GeminiFunctionResponse(
                                        name = "ask_user",
                                        response = buildJsonObject {
                                            put("answer", answer)
                                        }
                                    )
                                )
                            )
                            continue
                        }

                        // Loop detection before executing
                        val preScreen = AccessibilityBridge.captureScreenTree()
                        val signature = "$toolName|${args.toString()}|${preScreen?.structuralSignature().orEmpty()}"
                        recentSignatures.addLast(signature)
                        if (recentSignatures.size > 6) recentSignatures.removeFirst()
                        if (recentSignatures.count { it == signature } >= 3) {
                            val loopMsg = if (language == AppLanguage.ENGLISH) {
                                "Stopped safely because the same screen action repeated 3 times without progress."
                            } else {
                                "लूप डिटेक्शन: एक ही एक्शन 3 बार दोहराया गया, इसलिए टास्क सुरक्षित रूप से रोक दिया गया।"
                            }
                            failTask(userCommand, language, startTime, loopMsg, onSpeak, onFinished)
                            return@launch
                        }

                        // Sensitive action confirmation check
                        val targetDesc = args.str("target")
                            .ifBlank { args.str("recipient") }
                            .ifBlank { args.str("name") }
                            .ifBlank { args.str("text") }

                        if (settings.confirmSensitiveActions) {
                            val confirmReq = sensitiveActionGuard.inspectToolAction(toolName, targetDesc, preScreen)
                            if (confirmReq != null) {
                                val approved = requestUserConfirmation(confirmReq, stepsExecuted, onSpeak)
                                if (!approved) {
                                    appendStepLog(
                                        AgentStepLog(
                                            stepNumber = stepsExecuted,
                                            toolName = toolName,
                                            actionSummary = confirmReq.actionSummary,
                                            detail = "Cancelled by user",
                                            outcome = StepOutcome.SKIPPED
                                        )
                                    )
                                    functionResponseParts.add(
                                        GeminiPart(
                                            functionResponse = GeminiFunctionResponse(
                                                name = toolName,
                                                response = buildJsonObject {
                                                    put("status", "USER_DENIED")
                                                    put("message", "The user declined this sensitive action. Stop or ask the user.")
                                                }
                                            )
                                        )
                                    )
                                    continue
                                }
                            }
                        }

                        // Execute the tool call
                        _taskState.value = _taskState.value.copy(
                            activeActionLabel = "Step $stepsExecuted/$MAX_STEPS • $toolName($targetDesc)"
                        )
                        val result = executeToolCall(toolName, args)
                        val outcome = if (result is CommandResult.Success) StepOutcome.SUCCESS else StepOutcome.FAILED
                        appendStepLog(
                            AgentStepLog(
                                stepNumber = stepsExecuted,
                                toolName = toolName,
                                actionSummary = "$toolName ${targetDesc.take(40)}".trim(),
                                detail = result.localizedMessage(language),
                                outcome = outcome
                            )
                        )

                        // Brief settle delay so UI transitions finish before re-reading the screen
                        delay(650L)
                        val updatedScreen = AccessibilityBridge.captureScreenTree()?.toCompactPromptText()
                            ?: "Accessibility Service not connected."

                        functionResponseParts.add(
                            GeminiPart(
                                functionResponse = GeminiFunctionResponse(
                                    name = toolName,
                                    response = buildJsonObject {
                                        put("status", if (result is CommandResult.Success) "OK" else "ERROR")
                                        put("message", result.localizedMessage(AppLanguage.ENGLISH))
                                        put("screen", updatedScreen)
                                    }
                                )
                            )
                        )
                    }

                    if (functionResponseParts.isNotEmpty()) {
                        conversation.add(GeminiContent(role = "function", parts = functionResponseParts))
                    }
                }

                // Reached 20-step safety cap
                val limitMsg = if (language == AppLanguage.ENGLISH) {
                    "Reached the 20-step safety limit. Please check the screen and continue if needed."
                } else {
                    "सुरक्षा सीमा (20 चरण) पूरी हो गई है। कृपया स्क्रीन जांचें।"
                }
                failTask(userCommand, language, startTime, limitMsg, onSpeak, onFinished)
            } catch (_: CancellationException) {
                val stoppedMsg = if (language == AppLanguage.ENGLISH) "Task stopped." else "टास्क रोक दिया गया।"
                _taskState.value = _taskState.value.copy(
                    status = AgentTaskStatus.STOPPED,
                    finalMessage = stoppedMsg,
                    pendingConfirmation = null,
                    pendingQuestion = null
                )
                historyRepository.record(
                    commandText = userCommand,
                    language = language.name,
                    executionMode = "AI_AGENT",
                    status = "CANCELLED",
                    responseSummary = stoppedMsg,
                    stepCount = stepsExecuted,
                    durationMs = System.currentTimeMillis() - startTime
                )
            } catch (e: GeminiError) {
                failTask(userCommand, language, startTime, e.localized(language), onSpeak, onFinished)
            } catch (e: Exception) {
                val msg = e.message ?: "Unexpected agent error"
                failTask(userCommand, language, startTime, msg, onSpeak, onFinished)
            }
        }
    }

    fun resolveConfirmation(approved: Boolean) {
        pendingConfirmationDeferred?.complete(approved)
        pendingConfirmationDeferred = null
    }

    fun submitUserAnswer(answer: String) {
        pendingUserAnswerDeferred?.complete(answer.trim())
        pendingUserAnswerDeferred = null
    }

    fun stopTask(silent: Boolean = false) {
        pendingConfirmationDeferred?.complete(false)
        pendingConfirmationDeferred = null
        pendingUserAnswerDeferred?.cancel()
        pendingUserAnswerDeferred = null
        activeJob?.cancel()
        activeJob = null
        if (!silent && _taskState.value.isActive) {
            _taskState.value = _taskState.value.copy(
                status = AgentTaskStatus.STOPPED,
                activeActionLabel = "Stopped by user",
                pendingConfirmation = null,
                pendingQuestion = null
            )
        }
    }

    private suspend fun requestUserConfirmation(
        request: ConfirmationRequest,
        stepNumber: Int,
        onSpeak: (String) -> Unit
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmationDeferred = deferred
        val lang = settingsRepository.settingsFlow.first().appLanguage
        val prompt = if (lang == AppLanguage.ENGLISH) request.promptEn else request.promptHi

        _taskState.value = _taskState.value.copy(
            status = AgentTaskStatus.WAITING_CONFIRMATION,
            pendingConfirmation = request,
            activeActionLabel = "Waiting for confirmation…"
        )
        appendStepLog(
            AgentStepLog(
                stepNumber = stepNumber,
                toolName = "safety_confirm",
                actionSummary = request.category.titleEn,
                detail = prompt,
                outcome = StepOutcome.WAITING_CONFIRMATION
            )
        )
        onSpeak(prompt)
        val approved = deferred.await()
        _taskState.value = _taskState.value.copy(
            status = AgentTaskStatus.RUNNING,
            pendingConfirmation = null
        )
        return approved
    }

    private suspend fun requestUserAnswer(
        question: String,
        stepNumber: Int,
        onSpeak: (String) -> Unit
    ): String {
        val deferred = CompletableDeferred<String>()
        pendingUserAnswerDeferred = deferred
        _taskState.value = _taskState.value.copy(
            status = AgentTaskStatus.WAITING_USER_ANSWER,
            pendingQuestion = question,
            activeActionLabel = "Waiting for your reply…"
        )
        appendStepLog(
            AgentStepLog(
                stepNumber = stepNumber,
                toolName = "ask_user",
                actionSummary = "Question for you",
                detail = question,
                outcome = StepOutcome.WAITING_CONFIRMATION
            )
        )
        onSpeak(question)
        val answer = deferred.await()
        _taskState.value = _taskState.value.copy(
            status = AgentTaskStatus.RUNNING,
            pendingQuestion = null
        )
        return answer
    }

    private suspend fun executeToolCall(toolName: String, args: JsonObject): CommandResult {
        return when (toolName) {
            "open_app" -> systemExecutor.openApp(args.str("name"))
            "call_contact" -> systemExecutor.callContact(args.str("name"))
            "send_sms" -> systemExecutor.sendSms(args.str("recipient"), args.str("message"))
            "wait" -> {
                val ms = args.int("ms", 1200).coerceIn(200, 5000).toLong()
                delay(ms)
                CommandResult.Success("Waited ${ms}ms.", "${ms}ms इंतज़ार किया गया।")
            }
            "read_screen" -> {
                val tree = AccessibilityBridge.captureScreenTree()
                    ?: return AccessibilityBridge.missingServiceResult()
                CommandResult.Success(
                    messageEn = "Read ${tree.nodes.size} visible elements in ${tree.packageName}.",
                    messageHi = "${tree.nodes.size} स्क्रीन एलिमेंट पढ़े गए।"
                )
            }
            "click" -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.clickNode(args.str("target"), args.str("by").ifBlank { "auto" })
            }
            "long_click" -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.longClickNode(args.str("target"))
            }
            "type_text" -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                ctrl.setText(args.str("text"), args.str("target").takeIf { it.isNotBlank() })
            }
            "scroll" -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                val dir = parseDirection(args.str("direction"))
                ctrl.scrollDirection(dir)
            }
            "swipe" -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                val dir = parseDirection(args.str("direction"))
                ctrl.swipeDirection(dir)
            }
            "press_global" -> {
                val ctrl = AccessibilityBridge.controllerOrNull()
                    ?: return AccessibilityBridge.missingServiceResult()
                val action = runCatching {
                    GlobalNavAction.valueOf(args.str("action").uppercase())
                }.getOrDefault(GlobalNavAction.BACK)
                ctrl.performGlobal(action)
            }
            else -> CommandResult.Failed(
                reasonEn = "Unknown tool requested: $toolName",
                reasonHi = "अज्ञात टूल: $toolName"
            )
        }
    }

    private suspend fun completeTask(
        command: String,
        language: AppLanguage,
        steps: Int,
        startTime: Long,
        message: String,
        onSpeak: (String) -> Unit,
        onFinished: (Boolean, String) -> Unit
    ) {
        _taskState.value = _taskState.value.copy(
            status = AgentTaskStatus.COMPLETED,
            finalMessage = message,
            activeActionLabel = "Done"
        )
        historyRepository.record(
            commandText = command,
            language = language.name,
            executionMode = "AI_AGENT",
            status = "SUCCESS",
            responseSummary = message,
            stepCount = steps,
            durationMs = System.currentTimeMillis() - startTime
        )
        onSpeak(message)
        onFinished(true, message)
    }

    private suspend fun failTask(
        command: String,
        language: AppLanguage,
        startTime: Long,
        errorMessage: String,
        onSpeak: (String) -> Unit,
        onFinished: (Boolean, String) -> Unit
    ) {
        _taskState.value = _taskState.value.copy(
            status = AgentTaskStatus.FAILED,
            finalMessage = errorMessage,
            activeActionLabel = "Failed",
            pendingConfirmation = null,
            pendingQuestion = null
        )
        historyRepository.record(
            commandText = command,
            language = language.name,
            executionMode = "AI_AGENT",
            status = "FAILED",
            responseSummary = errorMessage,
            stepCount = _taskState.value.currentStep,
            durationMs = System.currentTimeMillis() - startTime
        )
        onSpeak(errorMessage)
        onFinished(false, errorMessage)
    }

    private fun appendStepLog(log: AgentStepLog) {
        _taskState.value = _taskState.value.copy(
            steps = _taskState.value.steps + log
        )
    }

    private fun parseDirection(raw: String): ScrollDirection = when (raw.lowercase()) {
        "up" -> ScrollDirection.UP
        "left" -> ScrollDirection.LEFT
        "right" -> ScrollDirection.RIGHT
        else -> ScrollDirection.DOWN
    }

    private fun GeminiError.localized(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) messageEn else messageHi

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: default

    private fun buildSystemInstruction(language: AppLanguage): GeminiContent {
        val langHint = when (language) {
            AppLanguage.ENGLISH -> "Respond concisely in clear English."
            AppLanguage.HINDI -> "Respond concisely in natural Hindi (Devanagari)."
            AppLanguage.HINGLISH -> "Respond concisely in friendly Hinglish (Hindi written in English script)."
        }
        return GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = """
                        You are Voice Control AI, a helpful Android accessibility phone-control agent.
                        Goal: Complete the user's voice command safely using the provided Android tools.
                        Rules:
                        - Inspect the numbered [#N] UI elements in Current Screen State before clicking or typing.
                        - Prefer exact text, contentDescription, or #N index when calling click() or type_text().
                        - Take one logical step at a time, wait for the updated screen tree, and stop as soon as the task is complete by calling finish(message).
                        - If information is missing (e.g., which contact or message text), call ask_user(question).
                        - Never bypass security, payments, or password fields.
                        - $langHint
                    """.trimIndent()
                )
            )
        )
    }

    private fun buildAgentTools(): GeminiTool = GeminiTool(
        functionDeclarations = listOf(
            decl("click", "Click a UI element by text, contentDescription, viewId, or #index.", mapOf(
                "target" to "string",
                "by" to "string"
            ), listOf("target")),
            decl("long_click", "Long-press a UI element.", mapOf("target" to "string"), listOf("target")),
            decl("scroll", "Scroll the screen up, down, left, or right.", mapOf("direction" to "string"), listOf("direction")),
            decl("swipe", "Swipe across the screen in direction up, down, left, or right.", mapOf("direction" to "string"), listOf("direction")),
            decl("type_text", "Type text into a focused or target editable field.", mapOf(
                "text" to "string",
                "target" to "string"
            ), listOf("text")),
            decl("open_app", "Launch an installed app by Hindi, English, or Hinglish name.", mapOf("name" to "string"), listOf("name")),
            decl("press_global", "Execute global Android navigation: BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, SCREENSHOT.", mapOf("action" to "string"), listOf("action")),
            decl("wait", "Wait briefly for animations or loading screens (milliseconds).", mapOf("ms" to "integer"), listOf("ms")),
            decl("read_screen", "Capture a fresh snapshot of the visible UI elements.", emptyMap(), emptyList()),
            decl("call_contact", "Call a saved contact by name or phone number.", mapOf("name" to "string"), listOf("name")),
            decl("send_sms", "Send an SMS message to a contact or number (requires user confirmation).", mapOf(
                "recipient" to "string",
                "message" to "string"
            ), listOf("recipient", "message")),
            decl("ask_user", "Ask the user a clarifying question and wait for their voice or text reply.", mapOf("question" to "string"), listOf("question")),
            decl("finish", "Finish the task and announce the final summary to the user.", mapOf("message" to "string"), listOf("message"))
        )
    )

    private fun decl(
        name: String,
        description: String,
        props: Map<String, String>,
        required: List<String>
    ): GeminiFunctionDeclaration {
        val params = buildJsonObject {
            put("type", "OBJECT")
            put("properties", buildJsonObject {
                props.forEach { (k, v) ->
                    put(k, buildJsonObject {
                        put("type", v.uppercase())
                    })
                }
            })
            if (required.isNotEmpty()) {
                put("required", buildJsonArray {
                    required.forEach { add(JsonPrimitive(it)) }
                })
            }
        }
        return GeminiFunctionDeclaration(name, description, params)
    }

    companion object {
        private const val MAX_STEPS = 20
    }
}
