package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppSettings
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.ThemeMode

@Composable
fun SettingsHubScreen(
    settings: AppSettings,
    grantedPermissionsCount: Int,
    totalPermissionsCount: Int,
    hasApiKey: Boolean,
    configuredKeysCount: Int = 0,
    onSelectTheme: (ThemeMode) -> Unit,
    onOpenModels: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val strings = LocalizedStrings.forLanguage(settings.appLanguage)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = strings.navSettings,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.AutoAwesome,
                    title = strings.modelsTitle,
                    subtitle = if (hasApiKey) {
                        "$configuredKeysCount / 4 keys saved • Text: ${settings.reasoningModelId.removePrefix("models/").ifBlank { "Pick model" }}"
                    } else {
                        "Add 2 Primary + 2 Fallback Gemini API keys (Text & Voice)"
                    },
                    testTag = "settings_nav_models",
                    onClick = onOpenModels
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.GraphicEq,
                    title = strings.voiceSettingsTitle,
                    subtitle = "${settings.appLanguage.nativeLabel} • Gemini Voice: ${settings.geminiVoiceName} • Wake: \"${settings.wakeWord}\"",
                    testTag = "settings_nav_voice",
                    onClick = onOpenVoice
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.VerifiedUser,
                    title = strings.permissionsTitle,
                    subtitle = "$grantedPermissionsCount / $totalPermissionsCount permissions granted • OEM Autostart Guide",
                    testTag = "settings_nav_permissions",
                    onClick = onOpenPermissions
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.PrivacyTip,
                    title = strings.privacyTitle,
                    subtitle = if (settings.appLanguage == AppLanguage.HINDI) {
                        "गोपनीयता नीति, मास्टर स्विच और सुरक्षा नियंत्रण"
                    } else {
                        "Zero screen logging, safety confirmations, master switch"
                    },
                    testTag = "settings_nav_privacy",
                    onClick = onOpenPrivacy
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (settings.appLanguage == AppLanguage.HINDI) "थीम चुनें" else "Appearance Theme",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onSelectTheme(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
