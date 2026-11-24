package com.example.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose route keys covering all 9 Voice Control AI screens + Settings hub.
 */
@Serializable
sealed interface AppRoute {
    @Serializable data object Onboarding : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object TaskLive : AppRoute
    @Serializable data object History : AppRoute
    @Serializable data object CommandsHelp : AppRoute
    @Serializable data object SettingsHub : AppRoute
    @Serializable data object ModelsApiKey : AppRoute
    @Serializable data object VoiceSettings : AppRoute
    @Serializable data object PermissionsDashboard : AppRoute
    @Serializable data object PrivacyAbout : AppRoute
}
