package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFFBDF2FA),
    secondary = SaffronAmber,
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainerLight,
    tertiary = EmeraldPulse,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = EmeraldContainerLight,
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralContainerLight,
    background = SlateObsidian,
    onBackground = SlateOnSurface,
    surface = SlateSurface,
    onSurface = SlateOnSurface,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = SlateOnSurfaceVariant,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = Color(0xFF1F2D42)
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = Color(0xFF001F24),
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF002116),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF4C0519),
    background = AlabasterBg,
    onBackground = InkPrimary,
    surface = AlabasterSurface,
    onSurface = InkPrimary,
    surfaceVariant = AlabasterElevated,
    onSurfaceVariant = InkSecondary,
    surfaceContainer = AlabasterElevated,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun VoiceControlTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoiceControlTypography,
        content = content
    )
}
