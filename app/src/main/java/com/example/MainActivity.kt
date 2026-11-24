package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.i18n.LocalizedStrings
import com.example.ui.components.SensitiveConfirmationDialog
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.CommandsHelpScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ModelsApiKeyScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PermissionsDashboardScreen
import com.example.ui.screens.PrivacyAboutScreen
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.screens.TaskLiveViewScreen
import com.example.ui.screens.VoiceSettingsScreen
import com.example.ui.theme.VoiceControlTheme
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ModelsSettingsViewModel
import com.example.ui.viewmodel.VoicePermissionsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as VoiceControlApp).container

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(container, applicationContext)
            )
            val historyViewModel: HistoryViewModel = viewModel(
                factory = HistoryViewModel.factory(container.historyRepository)
            )
            val modelsViewModel: ModelsSettingsViewModel = viewModel(
                factory = ModelsSettingsViewModel.factory(
                    container.apiKeyStore,
                    container.geminiClient,
                    container.settingsRepository
                )
            )
            val voicePermViewModel: VoicePermissionsViewModel = viewModel(
                factory = VoicePermissionsViewModel.factory(container, applicationContext)
            )

            val homeState by mainViewModel.homeUiState.collectAsStateWithLifecycle()
            val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
            val modelsState by modelsViewModel.uiState.collectAsStateWithLifecycle()
            val voskStates by voicePermViewModel.voskStates.collectAsStateWithLifecycle()
            val geminiVoices by voicePermViewModel.geminiVoices.collectAsStateWithLifecycle()
            val isVoiceSpeaking by voicePermViewModel.isVoiceSpeaking.collectAsStateWithLifecycle()
            val voiceError by voicePermViewModel.voiceError.collectAsStateWithLifecycle()
            val lastVoiceKeySlot by voicePermViewModel.lastVoiceKeySlot.collectAsStateWithLifecycle()
            val permissionStatuses by voicePermViewModel.permissionStatuses.collectAsStateWithLifecycle()

            VoiceControlTheme(themeMode = homeState.settings.themeMode) {
                if (!homeState.settings.onboardingCompleted) {
                    OnboardingScreen(
                        language = homeState.settings.appLanguage,
                        permissionStatuses = permissionStatuses,
                        runtimePermissionFor = voicePermViewModel::runtimePermissionFor,
                        onSelectLanguage = voicePermViewModel::setLanguage,
                        onOpenSettings = voicePermViewModel::openPermissionSettings,
                        onRefreshPermissions = voicePermViewModel::refreshPermissions,
                        onFinishOnboarding = mainViewModel::completeOnboarding
                    )
                } else {
                    VoiceControlMainShell(
                        homeState = homeState,
                        historyState = historyState,
                        modelsState = modelsState,
                        voskStates = voskStates,
                        geminiVoices = geminiVoices,
                        isVoiceSpeaking = isVoiceSpeaking,
                        voiceError = voiceError,
                        lastVoiceKeySlot = lastVoiceKeySlot,
                        permissionStatuses = permissionStatuses,
                        mainViewModel = mainViewModel,
                        historyViewModel = historyViewModel,
                        modelsViewModel = modelsViewModel,
                        voicePermViewModel = voicePermViewModel
                    )
                }

                // Global safety confirmation dialog (works over any screen)
                homeState.pendingConfirmation?.let { req ->
                    SensitiveConfirmationDialog(
                        request = req,
                        language = homeState.settings.appLanguage,
                        onConfirm = { mainViewModel.resolveConfirmation(true) },
                        onCancel = { mainViewModel.resolveConfirmation(false) }
                    )
                }
            }
        }
    }
}

private data class TopLevelNavItem(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val showLiveBadge: Boolean = false,
    val testTag: String
)

@Composable
private fun VoiceControlMainShell(
    homeState: com.example.ui.viewmodel.HomeUiState,
    historyState: com.example.ui.viewmodel.HistoryUiState,
    modelsState: com.example.ui.viewmodel.ModelsSettingsUiState,
    voskStates: Map<com.example.service.voice.VoskModelSpec, com.example.service.voice.ModelInstallState>,
    geminiVoices: List<com.example.data.remote.GeminiVoiceOption>,
    isVoiceSpeaking: Boolean,
    voiceError: String?,
    lastVoiceKeySlot: com.example.data.local.ApiKeySlot?,
    permissionStatuses: List<com.example.domain.model.PermissionStatusItem>,
    mainViewModel: MainViewModel,
    historyViewModel: HistoryViewModel,
    modelsViewModel: ModelsSettingsViewModel,
    voicePermViewModel: VoicePermissionsViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val strings = LocalizedStrings.forLanguage(homeState.settings.appLanguage)

    val topDestinations = listOf(
        TopLevelNavItem(
            route = AppRoute.Home,
            label = strings.navHome,
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "nav_home"
        ),
        TopLevelNavItem(
            route = AppRoute.TaskLive,
            label = strings.navLive,
            selectedIcon = Icons.Rounded.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome,
            showLiveBadge = homeState.agentTask.isActive,
            testTag = "nav_live"
        ),
        TopLevelNavItem(
            route = AppRoute.History,
            label = strings.navHistory,
            selectedIcon = Icons.Rounded.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "nav_history"
        ),
        TopLevelNavItem(
            route = AppRoute.CommandsHelp,
            label = strings.navCommands,
            selectedIcon = Icons.Rounded.Help,
            unselectedIcon = Icons.Outlined.HelpOutline,
            testTag = "nav_help"
        ),
        TopLevelNavItem(
            route = AppRoute.SettingsHub,
            label = strings.navSettings,
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_settings"
        )
    )

    fun navigateTopLevel(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        topDestinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (item.showLiveBadge) Badge()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        topDestinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    BadgedBox(
                                        badge = { if (item.showLiveBadge) Badge() }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home
                    ) {
                        composable<AppRoute.Home> {
                            HomeScreen(
                                uiState = homeState,
                                onToggleMaster = mainViewModel::toggleMasterSwitch,
                                onSelectLanguage = mainViewModel::setLanguage,
                                onTapMicOrb = mainViewModel::toggleListening,
                                onSubmitCommand = { cmd ->
                                    mainViewModel.submitCommand(cmd)
                                },
                                onStopTask = mainViewModel::stopActiveTask,
                                onOpenLiveTask = { navigateTopLevel(AppRoute.TaskLive) },
                                onOpenPermission = mainViewModel::openPermissionSettings,
                                onOpenModelsSettings = { navController.navigate(AppRoute.ModelsApiKey) },
                                onOpenHistory = { navigateTopLevel(AppRoute.History) }
                            )
                        }

                        composable<AppRoute.TaskLive> {
                            TaskLiveViewScreen(
                                taskState = homeState.agentTask,
                                language = homeState.settings.appLanguage,
                                onStopTask = mainViewModel::stopActiveTask,
                                onAnswerQuestion = mainViewModel::answerAgentQuestion,
                                onStartSampleTask = mainViewModel::submitCommand
                            )
                        }

                        composable<AppRoute.History> {
                            HistoryScreen(
                                uiState = historyState,
                                language = homeState.settings.appLanguage,
                                onSearchChange = historyViewModel::updateSearch,
                                onSelectFilter = historyViewModel::selectFilter,
                                onRerunCommand = { cmd ->
                                    mainViewModel.submitCommand(cmd)
                                    navigateTopLevel(AppRoute.Home)
                                },
                                onDeleteItem = historyViewModel::deleteItem,
                                onClearAll = historyViewModel::clearAll
                            )
                        }

                        composable<AppRoute.CommandsHelp> {
                            CommandsHelpScreen(
                                language = homeState.settings.appLanguage,
                                onTryCommand = { sample ->
                                    mainViewModel.submitCommand(sample)
                                    navigateTopLevel(AppRoute.Home)
                                }
                            )
                        }

                        composable<AppRoute.SettingsHub> {
                            SettingsHubScreen(
                                settings = homeState.settings,
                                grantedPermissionsCount = permissionStatuses.count { it.isGranted },
                                totalPermissionsCount = permissionStatuses.size,
                                hasApiKey = homeState.hasApiKey,
                                configuredKeysCount = modelsState.keySummary.totalConfigured,
                                onSelectTheme = voicePermViewModel::setThemeMode,
                                onOpenModels = { navController.navigate(AppRoute.ModelsApiKey) },
                                onOpenVoice = { navController.navigate(AppRoute.VoiceSettings) },
                                onOpenPermissions = { navController.navigate(AppRoute.PermissionsDashboard) },
                                onOpenPrivacy = { navController.navigate(AppRoute.PrivacyAbout) }
                            )
                        }

                        composable<AppRoute.ModelsApiKey> {
                            ModelsApiKeyScreen(
                                uiState = modelsState,
                                language = homeState.settings.appLanguage,
                                onBack = { navController.popBackStack() },
                                onSaveApiKey = modelsViewModel::saveApiKey,
                                onClearApiKey = modelsViewModel::clearApiKey,
                                onRefreshModels = modelsViewModel::refreshModels,
                                onSelectReasoningModel = modelsViewModel::selectReasoningModel,
                                onSelectVoiceModel = modelsViewModel::selectVoiceModel
                            )
                        }

                        composable<AppRoute.VoiceSettings> {
                            VoiceSettingsScreen(
                                settings = homeState.settings,
                                voskStates = voskStates,
                                geminiVoices = geminiVoices,
                                isVoiceSpeaking = isVoiceSpeaking,
                                voiceError = voiceError,
                                lastVoiceKeySlot = lastVoiceKeySlot,
                                onBack = { navController.popBackStack() },
                                onSelectLanguage = voicePermViewModel::setLanguage,
                                onSaveWakeWord = voicePermViewModel::setWakeWord,
                                onToggleWakeWord = voicePermViewModel::setWakeWordEnabled,
                                onToggleOverlay = voicePermViewModel::setOverlayEnabled,
                                onToggleAutoStart = voicePermViewModel::setAutoStartOnBoot,
                                onToggleOfflineOnly = voicePermViewModel::setOfflineOnlyMode,
                                onSelectGeminiVoice = voicePermViewModel::setGeminiVoiceName,
                                onChangeVoiceSpeed = voicePermViewModel::setVoiceSpeed,
                                onTestGeminiVoice = voicePermViewModel::testGeminiVoice,
                                onStopGeminiVoice = voicePermViewModel::stopGeminiVoice,
                                onDownloadVosk = voicePermViewModel::downloadVoskModel,
                                onDeleteVosk = voicePermViewModel::deleteVoskModel
                            )
                        }

                        composable<AppRoute.PermissionsDashboard> {
                            PermissionsDashboardScreen(
                                language = homeState.settings.appLanguage,
                                statuses = permissionStatuses,
                                detectedManufacturer = voicePermViewModel.detectedManufacturer,
                                oemGuides = voicePermViewModel.oemGuides,
                                runtimePermissionFor = voicePermViewModel::runtimePermissionFor,
                                onBack = { navController.popBackStack() },
                                onOpenSettings = voicePermViewModel::openPermissionSettings,
                                onOpenOemAutostart = voicePermViewModel::openOemAutostart,
                                onRefresh = voicePermViewModel::refreshPermissions
                            )
                        }

                        composable<AppRoute.PrivacyAbout> {
                            PrivacyAboutScreen(
                                settings = homeState.settings,
                                onBack = { navController.popBackStack() },
                                onToggleMaster = mainViewModel::toggleMasterSwitch,
                                onToggleConfirmSensitive = voicePermViewModel::setConfirmSensitiveActions,
                                onResetOnboarding = mainViewModel::resetOnboarding
                            )
                        }
                    }
                }
            }
        }
    }
}
