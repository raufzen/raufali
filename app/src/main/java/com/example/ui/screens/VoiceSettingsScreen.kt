package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import com.example.data.local.AppSettings
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.service.voice.ModelInstallState
import com.example.service.voice.TtsVoiceOption
import com.example.service.voice.VoskModelSpec
import kotlin.math.roundToInt

@Composable
fun VoiceSettingsScreen(
    settings: AppSettings,
    voskStates: Map<VoskModelSpec, ModelInstallState>,
    ttsVoices: List<TtsVoiceOption>,
    onBack: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSaveWakeWord: (String) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onSelectTtsLocale: (String) -> Unit,
    onSelectTtsVoice: (String) -> Unit,
    onChangeTtsSpeed: (Float) -> Unit,
    onChangeTtsPitch: (Float) -> Unit,
    onTestVoice: () -> Unit,
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
                            subtitle = "Block all cloud Gemini calls and only execute local offline commands.",
                            checked = settings.offlineOnlyMode,
                            testTag = "toggle_offline_only",
                            onCheckedChange = onToggleOfflineOnly
                        )
                    }
                }
            }

            // Text-to-Speech (TTS) Controls
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
                            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Voice Reply (Text-to-Speech)", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.ttsLanguageCode == "hi-IN",
                                onClick = { onSelectTtsLocale("hi-IN") },
                                label = { Text("Hindi (hi-IN)") }
                            )
                            FilterChip(
                                selected = settings.ttsLanguageCode == "en-IN",
                                onClick = { onSelectTtsLocale("en-IN") },
                                label = { Text("English (en-IN)") }
                            )
                        }

                        if (ttsVoices.isNotEmpty()) {
                            Text("Available System Voices", style = MaterialTheme.typography.labelLarge)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ttsVoices.take(5).forEach { voice ->
                                    FilterChip(
                                        selected = settings.ttsVoiceName == voice.name,
                                        onClick = { onSelectTtsVoice(voice.name) },
                                        label = { Text(voice.label) }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Speech Speed: ${(settings.ttsSpeechRate * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = settings.ttsSpeechRate,
                            onValueChange = onChangeTtsSpeed,
                            valueRange = 0.6f..1.8f
                        )

                        Text(
                            text = "Voice Pitch: ${(settings.ttsPitch * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = settings.ttsPitch,
                            onValueChange = onChangeTtsPitch,
                            valueRange = 0.7f..1.5f
                        )

                        OutlinedButton(
                            onClick = onTestVoice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_tts_voice_btn")
                        ) {
                            Text("Test Voice Reply")
                        }
                    }
                }
            }

            // Offline Vosk Models Manager
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
