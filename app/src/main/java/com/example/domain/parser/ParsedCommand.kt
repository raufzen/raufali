package com.example.domain.parser

enum class GlobalNavAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    POWER_DIALOG,
    LOCK_SCREEN,
    SCREENSHOT
}

enum class ScrollDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

enum class VolumeAction {
    UP,
    DOWN,
    MUTE,
    MAX,
    SET_PERCENT
}

enum class BrightnessAction {
    UP,
    DOWN,
    SET_PERCENT
}

enum class SettingsPage {
    MAIN,
    WIFI,
    BLUETOOTH,
    MOBILE_DATA,
    AIRPLANE_MODE,
    DISPLAY,
    SOUND,
    BATTERY,
    ACCESSIBILITY,
    APPS
}

/**
 * Structured output of the 100% offline Hindi/English/Hinglish rule-based parser.
 */
sealed class ParsedCommand {
    data object StopTask : ParsedCommand()
    data class OpenApp(val appQuery: String) : ParsedCommand()
    data class GlobalAction(val action: GlobalNavAction) : ParsedCommand()
    data class Scroll(val direction: ScrollDirection) : ParsedCommand()
    data class Swipe(val direction: ScrollDirection) : ParsedCommand()
    data class ClickTarget(val target: String) : ParsedCommand()
    data class LongClickTarget(val target: String) : ParsedCommand()
    data class TypeText(val text: String) : ParsedCommand()
    data class VolumeChange(val action: VolumeAction, val percent: Int? = null) : ParsedCommand()
    data class BrightnessChange(val action: BrightnessAction, val percent: Int? = null) : ParsedCommand()
    data class Flashlight(val turnOn: Boolean) : ParsedCommand()
    data class DoNotDisturb(val enable: Boolean) : ParsedCommand()
    data object OpenCamera : ParsedCommand()
    data class OpenSettings(val page: SettingsPage) : ParsedCommand()
    data class CallContact(val contactName: String) : ParsedCommand()
    data class SendSms(val recipient: String, val message: String) : ParsedCommand()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "") : ParsedCommand()
    data class SetTimer(val durationSeconds: Int, val label: String = "") : ParsedCommand()
    data object ReadScreen : ParsedCommand()
    data class ComplexAiTask(val rawCommand: String) : ParsedCommand()
}
