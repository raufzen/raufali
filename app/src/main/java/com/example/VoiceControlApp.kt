package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedApiKeyStore
import com.example.data.local.SettingsRepository
import com.example.data.remote.GeminiClient
import com.example.data.repository.CommandHistoryRepository
import com.example.domain.agent.AgentOrchestrator
import com.example.domain.agent.CommandDispatcher
import com.example.domain.agent.SystemActionExecutor
import com.example.domain.parser.AppLauncherMatcher
import com.example.domain.parser.LocalCommandParser
import com.example.domain.safety.SensitiveActionGuard
import com.example.service.permissions.PermissionChecker
import com.example.service.voice.VoiceSpeechRecognizer
import com.example.service.voice.VoiceTtsManager
import com.example.service.voice.VoskModelManager

/**
 * Clean Constructor-Injection Dependency Container scoped to the Application lifecycle.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Data Layer
    val database: AppDatabase = AppDatabase.create(appContext)
    val historyRepository: CommandHistoryRepository = CommandHistoryRepository(database.commandHistoryDao())
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    val apiKeyStore: EncryptedApiKeyStore = EncryptedApiKeyStore(appContext)
    val geminiClient: GeminiClient = GeminiClient()

    // Permissions & Voice Engines
    val permissionChecker: PermissionChecker = PermissionChecker(appContext)
    val voskModelManager: VoskModelManager = VoskModelManager(appContext)
    val ttsManager: VoiceTtsManager = VoiceTtsManager(
        geminiClient = geminiClient,
        apiKeyStore = apiKeyStore,
        settingsRepository = settingsRepository
    )
    val speechRecognizer: VoiceSpeechRecognizer = VoiceSpeechRecognizer(appContext, voskModelManager)

    // Domain Parsers & Safety
    val commandParser: LocalCommandParser = LocalCommandParser()
    val appMatcher: AppLauncherMatcher = AppLauncherMatcher(appContext)
    val sensitiveActionGuard: SensitiveActionGuard = SensitiveActionGuard()

    // Execution & AI Agent Loop
    val systemActionExecutor: SystemActionExecutor = SystemActionExecutor(
        context = appContext,
        appMatcher = appMatcher,
        permissionChecker = permissionChecker
    )

    val agentOrchestrator: AgentOrchestrator = AgentOrchestrator(
        geminiClient = geminiClient,
        apiKeyStore = apiKeyStore,
        settingsRepository = settingsRepository,
        systemExecutor = systemActionExecutor,
        sensitiveActionGuard = sensitiveActionGuard,
        historyRepository = historyRepository
    )

    val commandDispatcher: CommandDispatcher = CommandDispatcher(
        parser = commandParser,
        systemExecutor = systemActionExecutor,
        agentOrchestrator = agentOrchestrator,
        sensitiveActionGuard = sensitiveActionGuard,
        settingsRepository = settingsRepository,
        historyRepository = historyRepository,
        speechRecognizer = speechRecognizer,
        ttsManager = ttsManager
    )
}

class VoiceControlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.speechRecognizer.destroy()
        container.ttsManager.shutdown()
        super.onTerminate()
    }
}
