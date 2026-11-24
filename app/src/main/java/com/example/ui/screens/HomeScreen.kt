package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CommandHistoryEntity
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.AssistantState
import com.example.domain.model.PermissionType
import com.example.ui.components.MicOrb
import com.example.ui.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleMaster: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onTapMicOrb: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onStopTask: () -> Unit,
    onOpenLiveTask: () -> Unit,
    onOpenPermission: (PermissionType) -> Unit,
    onOpenModelsSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val language = uiState.settings.appLanguage
    val strings = LocalizedStrings.forLanguage(language)
    var typedCommand by rememberSaveable { mutableStateOf("") }

    val quickChips = when (language) {
        AppLanguage.HINDI -> listOf(
            "व्हाट्सएप खोलो",
            "आवाज़ बढ़ाओ",
            "टॉर्च जलाओ",
            "स्क्रीनशॉट लो",
            "नीचे स्क्रॉल करो",
            "वाईफाई सेटिंग्स"
        )
        AppLanguage.HINGLISH -> listOf(
            "WhatsApp kholo",
            "Volume badhao",
            "Torch jalao",
            "Screenshot lo",
            "Neeche scroll karo",
            "Wi-Fi settings"
        )
        AppLanguage.ENGLISH -> listOf(
            "Open WhatsApp",
            "Volume up",
            "Flashlight on",
            "Take screenshot",
            "Scroll down",
            "Open Wi-Fi"
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header bar with brand title & Master On/Off Switch
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.settings.masterEnabled) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                            Text(
                                text = if (!uiState.settings.masterEnabled) {
                                    strings.masterOff
                                } else if (uiState.hasApiKey && uiState.settings.reasoningModelId.isNotBlank()) {
                                    strings.readyWithAi
                                } else {
                                    strings.readyOffline
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = "Master Switch",
                                tint = if (uiState.settings.masterEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Switch(
                                checked = uiState.settings.masterEnabled,
                                onCheckedChange = onToggleMaster,
                                modifier = Modifier.testTag("home_master_switch")
                            )
                        }
                    }
                }
            }

            // 2. Quick language switcher pills (Hindi / English / Hinglish)
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.nativeLabel) },
                            modifier = Modifier.testTag("home_lang_${lang.name.lowercase()}")
                        )
                    }
                }
            }

            // 3. Accessibility warning banner if service is not active
            item {
                AnimatedVisibility(visible = !uiState.isAccessibilityConnected) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.accessibilityWarningTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = strings.accessibilityWarningBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Button(
                                onClick = { onOpenPermission(PermissionType.ACCESSIBILITY) },
                                modifier = Modifier.testTag("home_enable_a11y_btn")
                            ) {
                                Text(strings.fixNow)
                            }
                        }
                    }
                }
            }

            // 4. Missing specific permission prompt from last command
            item {
                val missingPerm = uiState.missingPermission
                AnimatedVisibility(visible = missingPerm != null) {
                    if (missingPerm != null) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (language == AppLanguage.HINDI) missingPerm.titleHi else missingPerm.titleEn,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onOpenPermission(missingPerm) }) {
                                    Text(strings.fixNow)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Hero Voice Orb Stage with atmospheric gradient card
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val orbStatusLabel = when (uiState.assistantState) {
                            AssistantState.LISTENING -> strings.listening
                            AssistantState.THINKING -> strings.thinking
                            AssistantState.SPEAKING -> strings.speaking
                            AssistantState.CONFIRMING -> strings.confirming
                            AssistantState.ERROR -> uiState.lastReply.ifBlank { strings.tapToSpeak }
                            AssistantState.IDLE -> strings.tapToSpeak
                        }

                        MicOrb(
                            state = uiState.assistantState,
                            rmsLevel = uiState.rmsLevel,
                            enabled = uiState.settings.masterEnabled,
                            contentDescriptionText = orbStatusLabel,
                            onClick = onTapMicOrb,
                            size = 176.dp
                        )

                        Text(
                            text = orbStatusLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Engine & model telemetry badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = uiState.engineLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.clickable(onClick = onOpenModelsSettings)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = uiState.settings.reasoningModelId
                                            .removePrefix("models/")
                                            .ifBlank { "Set AI Key" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Live transcript & reply display
                        val displayTranscript = uiState.partialTranscript.ifBlank { uiState.lastTranscript }
                        if (displayTranscript.isNotBlank() || uiState.lastReply.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (displayTranscript.isNotBlank()) {
                                        Text(
                                            text = "“$displayTranscript”",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (uiState.lastReply.isNotBlank()) {
                                        Text(
                                            text = uiState.lastReply,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Emergency Stop button visible whenever AI task is active
                        AnimatedVisibility(visible = uiState.agentTask.isActive) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenLiveTask,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Step ${uiState.agentTask.currentStep}/${uiState.agentTask.maxSteps} • View Live")
                                }
                                Button(
                                    onClick = onStopTask,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("home_stop_task_btn")
                                ) {
                                    Icon(Icons.Rounded.StopCircle, contentDescription = strings.stopTask)
                                }
                            }
                        }
                    }
                }
            }

            // 6. Typed command input bar (great for quiet environments & emulator testing)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = typedCommand,
                        onValueChange = { typedCommand = it },
                        placeholder = { Text(strings.typeCommandHint) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (typedCommand.isNotBlank()) {
                                onSubmitCommand(typedCommand)
                                typedCommand = ""
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_command_input")
                    )
                    FilledIconButton(
                        onClick = {
                            if (typedCommand.isNotBlank()) {
                                onSubmitCommand(typedCommand)
                                typedCommand = ""
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("home_command_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = strings.send
                        )
                    }
                }
            }

            // 7. Quick Voice Action Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.quickActionsTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickChips.forEachIndexed { idx, chipText ->
                            AssistChip(
                                onClick = { onSubmitCommand(chipText) },
                                label = { Text(chipText) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (idx) {
                                            1 -> Icons.Rounded.VolumeUp
                                            2 -> Icons.Rounded.FlashlightOn
                                            3 -> Icons.Rounded.Screenshot
                                            else -> Icons.Rounded.Bolt
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("quick_chip_$idx")
                            )
                        }
                    }
                }
            }

            // 8. Recent Commands Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.recentCommandsTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.recentCommands.isNotEmpty()) {
                        TextButton(onClick = onOpenHistory) {
                            Text(if (language == AppLanguage.HINDI) "सभी देखें" else "View all")
                        }
                    }
                }
            }

            if (uiState.recentCommands.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = strings.noCommandsYet,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = strings.noCommandsTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.recentCommands, key = { it.id }) { item ->
                    RecentCommandRow(
                        item = item,
                        onRerun = { onSubmitCommand(item.commandText) }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun RecentCommandRow(
    item: CommandHistoryEntity,
    onRerun: () -> Unit
) {
    val isSuccess = item.status == "SUCCESS"
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRerun)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = item.status,
                tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.commandText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.responseSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (item.executionMode == "AI_AGENT") {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = if (item.executionMode == "AI_AGENT") "AI" else "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
