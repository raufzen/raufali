package com.example.domain.model

/**
 * Every command execution across offline actions, accessibility actions, and AI agent tools
 * returns a structured CommandResult so the user always gets bilingual voice & visual feedback.
 */
sealed class CommandResult {
    data class Success(
        val messageEn: String,
        val messageHi: String
    ) : CommandResult()

    data class Failed(
        val reasonEn: String,
        val reasonHi: String
    ) : CommandResult()

    data class NeedsPermission(
        val permissionType: PermissionType,
        val messageEn: String,
        val messageHi: String
    ) : CommandResult()

    data class NeedsConfirmation(
        val request: ConfirmationRequest
    ) : CommandResult()

    fun localizedMessage(language: AppLanguage): String = when (this) {
        is Success -> if (language == AppLanguage.ENGLISH) messageEn else messageHi
        is Failed -> if (language == AppLanguage.ENGLISH) reasonEn else reasonHi
        is NeedsPermission -> if (language == AppLanguage.ENGLISH) messageEn else messageHi
        is NeedsConfirmation -> if (language == AppLanguage.ENGLISH) request.promptEn else request.promptHi
    }
}
