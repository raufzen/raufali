package com.example.domain.model

enum class AppLanguage(
    val displayName: String,
    val nativeLabel: String,
    val localeTag: String
) {
    ENGLISH("English", "English", "en-IN"),
    HINDI("Hindi", "हिन्दी", "hi-IN"),
    HINGLISH("Hinglish", "Hinglish", "hi-IN");

    companion object {
        fun fromKey(key: String?): AppLanguage {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: HINGLISH
        }
    }
}

enum class ThemeMode {
    SYSTEM, DARK, LIGHT;

    companion object {
        fun fromKey(key: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}
