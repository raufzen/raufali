package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ApiKeySlot
import com.example.data.local.AppSettings
import com.example.data.remote.GeminiVoiceOption
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.service.voice.ModelInstallState
import com.example.service.voice.VoskModelSpec
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(
    settings: AppSettings,
    voskStates: Map<VoskModelSpec, ModelInstallState>,
    geminiVoices: List<GeminiVoiceOption>,
    isVoiceSpeaking: Boolean,
    voiceError: String?,
    lastVoiceKeySlot: ApiKeySlot?,
    onBack: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSaveWakeWord: (String) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onSelectGeminiVoice: (String) -> Unit,
    onChangeVoiceSpeed: (Float) -> Unit,
    onTestGeminiVoice: () -> Unit,
    onStopGeminiVoice: () -> Unit,
    onDownloadVosk: (VoskModelSpec) -> Unit,
    onDeleteVosk: (VoskModelSpec) -> Unit
) {
    val strings = LocalizedStrings.forLanguage(settings.appLanguage)
    var wakeWordDraft by rememberSaveable(settings.wakeWord) { mutableStateOf(settings.wakeWord) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("voice_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = strings.voiceSettingsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // UI & Voice Language
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
                        Text("UI & Voice Language / भाषा", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.appLanguage == lang,
                                    onClick = { onSelectLanguage(lang) },
                                    label = { Text(lang.nativeLabel) },
                                    modifier = Modifier.testTag("voice_lang_${lang.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Gemini API Native Voice Selector (NO Local Android TTS)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text(
                                    text = if (settings.appLanguage == AppLanguage.HINDI) {
                                        "जेमिनी API वॉइस (कोई लोकल TTS नहीं)"
                                    } else {
                                        "Gemini API Native Voice (No Local TTS)"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (settings.appLanguage == AppLanguage.HINDI) {
                                        "आपकी प्राइमरी/फॉलबैक वॉइस API की से सीधे जेमिनी की आवाज़ें चलती हैं।"
                                    } else {
                                        "Speaks directly using Gemini API prebuilt voices via your Primary & Fallback Voice API keys."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            geminiVoices.forEach { voice ->
                                val styleText = if (settings.appLanguage == AppLanguage.HINDI) {
                                    voice.styleHi
                                } else {
                                    voice.styleEn
                                }
                                FilterChip(
                                    selected = settings.geminiVoiceName == voice.voiceName,
                                    onClick = { onSelectGeminiVoice(voice.voiceName) },
                                    label = { Text("${voice.voiceName} • $styleText") },
                                    modifier = Modifier.testTag("gemini_voice_${voice.voiceName.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "Speaking Pace: ${(settings.voiceSpeed * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = settings.voiceSpeed,
                            onValueChange = onChangeVoiceSpeed,
                            valueRange = 0.75f..1.4f
                        )

                        lastVoiceKeySlot?.let { slot ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "Last played with: ${if (settings.appLanguage == AppLanguage.HINDI) slot.titleHi else slot.titleEn}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        voiceError?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onTestGeminiVoice,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("test_gemini_voice_btn")
                            ) {
                                Text(
                                    if (settings.appLanguage == AppLanguage.HINDI) {
                                        "जेमिनी वॉइस टेस्ट करें"
                                    } else {
                                        "Test Gemini Voice (${settings.geminiVoiceName})"
                                    }
                                )
                            }
                            if (isVoiceSpeaking) {
                                OutlinedButton(
                                    onClick = onStopGeminiVoice,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.StopCircle, contentDescription = "Stop speaking")
                                }
                            }
                        }
                    }
                }
            }

            // Wake Word, Overlay & Background Service
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.wakeWordLabel, style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = wakeWordDraft,
                                onValueChange = { wakeWordDraft = it },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wake_word_input")
                            )
                            Button(
                                onClick = { onSaveWakeWord(wakeWordDraft) },
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("wake_word_save_btn")
                            ) {
                                Text("Save")
                            }
                        }

                        ToggleRow(
                            title = "Always-On Wake Word Service",
                            subtitle = "Runs a persistent microphone foreground service listening for \"${settings.wakeWord}\".",
                            checked = settings.wakeWordEnabled,
                            testTag = "toggle_wake_word",
                            onCheckedChange = onToggleWakeWord
                        )

                        ToggleRow(
                            title = strings.floatingMicOverlay,
                            subtitle = "Draggable mic button visible over any app. Long-press for quick menu.",
                            checked = settings.overlayEnabled,
                            testTag = "toggle_overlay",
                            onCheckedChange = onToggleOverlay
                        )

                        ToggleRow(
                            title = "Restart Automatically on Boot",
                            subtitle = "Keeps assistant alive after phone restart.",
                            checked = settings.autoStartOnBoot,
                            testTag = "toggle_autostart",
                            onCheckedChange = onToggleAutoStart
                        )

                        ToggleRow(
                            title = "Offline-Only Mode",
                            subtitle = "Block all cloud Gemini calls (both AI Agent and Gemini Voice) and run silent local commands only.",
                            checked = settings.offlineOnlyMode,
                            testTag = "toggle_offline_only",
                            onCheckedChange = onToggleOfflineOnly
                        )
                    }
                }
            }

            // Offline Vosk Speech-to-Text Models Manager
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.offlineSpeechModels, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Downloaded once and stored locally for offline Hindi & English speech recognition (with automatic on-device SpeechRecognizer fallback).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        VoskModelSpec.entries.forEach { spec ->
                            val installState = voskStates[spec] ?: ModelInstallState.NotInstalled
                            VoskModelRow(
                                spec = spec,
                                state = installState,
                                language = settings.appLanguage,
                                onDownload = { onDownloadVosk(spec) },
                                onDelete = { onDeleteVosk(spec) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun VoskModelRow(
    spec: VoskModelSpec,
    state: ModelInstallState,
    language: AppLanguage,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (language == AppLanguage.HINDI) spec.titleHi else spec.titleEn,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (state) {
                        ModelInstallState.NotInstalled -> "Not downloaded • ${spec.sizeLabel}"
                        is ModelInstallState.Downloading -> "Downloading ${(state.progress * 100).roundToInt()}%…"
                        ModelInstallState.Extracting -> "Extracting offline model…"
                        is ModelInstallState.Installed -> "Installed locally (%.1f MB)".format(state.sizeMb)
                        is ModelInstallState.Error -> "Error: ${state.message}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (state) {
                is ModelInstallState.Installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete model")
                        }
                    }
                }
                is ModelInstallState.Downloading, ModelInstallState.Extracting -> Unit
                else -> {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.testTag("download_vosk_${spec.id}")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download ${spec.titleEn}")
                    }
                }
            }
        }

        if (state is ModelInstallState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (state is ModelInstallState.Extracting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
