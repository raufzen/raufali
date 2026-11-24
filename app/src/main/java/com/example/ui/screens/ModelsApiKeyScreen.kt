package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.GeminiModelInfo
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.ModelsSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsApiKeyScreen(
    uiState: ModelsSettingsUiState,
    language: AppLanguage,
    onBack: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onRefreshModels: () -> Unit,
    onSelectReasoningModel: (String) -> Unit,
    onSelectVoiceModel: (String) -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)
    var keyDraft by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }

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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("models_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = strings.modelsTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Encrypted API Key Entry Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
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
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = strings.apiKeyLabel,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (uiState.hasApiKey) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Saved (AES-256): ${uiState.apiKeyMasked}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    IconButton(onClick = onClearApiKey, modifier = Modifier.testTag("clear_api_key_btn")) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Clear key",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = { Text("Paste Gemini API key (AIza…)") },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle key visibility"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (keyDraft.isNotBlank()) {
                                        onSaveApiKey(keyDraft)
                                        keyDraft = ""
                                    }
                                },
                                enabled = keyDraft.isNotBlank() && !uiState.isLoadingModels,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_api_key_btn")
                            ) {
                                Text(strings.saveAndLoadModels)
                            }

                            OutlinedButton(
                                onClick = onRefreshModels,
                                enabled = uiState.hasApiKey && !uiState.isLoadingModels,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("refresh_models_btn")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = strings.refreshModels)
                            }
                        }
                    }
                }
            }

            // Loading / Status / Error / Missing-Model Warning
            if (uiState.isLoadingModels) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("Fetching live models via ListModels…")
                    }
                }
            }

            uiState.missingModelWarning?.let { warning ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                            Text(warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            uiState.statusBanner?.let { banner ->
                item {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Role-based Dynamic Model Selectors
            if (uiState.reasoningCandidates.isEmpty() && !uiState.isLoadingModels) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(strings.noModelsHint, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                item {
                    ModelRoleDropdownCard(
                        title = strings.reasoningModelTitle,
                        description = "Used for the 20-step screen-control Gemini agent (filtered by generateContent).",
                        models = uiState.reasoningCandidates,
                        selectedModelId = uiState.selectedReasoningModelId,
                        testTag = "reasoning_model_dropdown",
                        onSelect = onSelectReasoningModel
                    )
                }

                item {
                    ModelRoleDropdownCard(
                        title = strings.voiceModelTitle,
                        description = "Used for fast replies and audio/multimodal understanding (prioritizes bidiGenerateContent & fast models).",
                        models = uiState.voiceCandidates,
                        selectedModelId = uiState.selectedVoiceModelId,
                        testTag = "voice_model_dropdown",
                        onSelect = onSelectVoiceModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelRoleDropdownCard(
    title: String,
    description: String,
    models: List<GeminiModelInfo>,
    selectedModelId: String,
    testTag: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedObj = models.firstOrNull { it.name == selectedModelId }

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
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedObj?.displayName?.ifBlank { selectedObj.shortId }
                        ?: selectedModelId.removePrefix("models/").ifBlank { "Select a model…" },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .testTag(testTag)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = model.displayName.ifBlank { model.shortId },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${model.shortId} • ${model.supportedGenerationMethods.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSelect(model.name)
                                expanded = false
                            }
                        )
                    }
                }
            }

            selectedObj?.let { model ->
                Text(
                    text = "Capabilities: ${model.supportedGenerationMethods.joinToString(", ")} • Input: ${model.inputTokenLimit} tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
