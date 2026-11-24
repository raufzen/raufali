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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
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
import com.example.data.local.ApiKeySlot
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
    onSaveApiKey: (ApiKeySlot, String) -> Unit,
    onClearApiKey: (ApiKeySlot) -> Unit,
    onRefreshModels: () -> Unit,
    onSelectReasoningModel: (String) -> Unit,
    onSelectVoiceModel: (String) -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)

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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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

                    OutlinedButton(
                        onClick = onRefreshModels,
                        enabled = uiState.keySummary.hasAnyKey && !uiState.isLoadingModels,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("refresh_models_btn")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = strings.refreshModels, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Quota Protection Overview Banner
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (language == AppLanguage.HINDI) {
                                    "4 API की कोटा प्रोटेक्शन (AES-256 एन्क्रिप्टेड)"
                                } else {
                                    "4-Key Quota Protection (AES-256 Encrypted)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    text = "${uiState.keySummary.totalConfigured} / 4",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = if (language == AppLanguage.HINDI) {
                                "2 प्राइमरी की (1 टेक्स्ट/थिंकिंग के लिए, 1 जेमिनी वॉइस के लिए) + 2 वैकल्पिक फॉलबैक की। अगर फॉलबैक की नहीं दी जाती, तो दोनों प्राइमरी की टेक्स्ट और वॉइस चलाएंगी।"
                            } else {
                                "2 Primary keys (1 for Text/Thinking, 1 for Gemini Voice) + 2 optional Fallback keys. If fallback keys are left blank, the 2 primary keys run cleanly for Text and Voice. When a primary key hits quota, its fallback takes over automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // SECTION 1: Text / Thinking API Keys (Primary + Optional Fallback)
            item {
                SectionHeaderRow(
                    icon = Icons.Rounded.Psychology,
                    title = if (language == AppLanguage.HINDI) {
                        "1. टेक्स्ट / थिंकिंग API की (एजेंट)"
                    } else {
                        "1. Text / Thinking API Keys (AI Agent)"
                    }
                )
            }

            item {
                ApiKeySlotCard(
                    slot = ApiKeySlot.TEXT_PRIMARY,
                    maskedPreview = uiState.keySummary.maskedPreviews[ApiKeySlot.TEXT_PRIMARY].orEmpty(),
                    language = language,
                    isBusy = uiState.isLoadingModels,
                    onSave = { onSaveApiKey(ApiKeySlot.TEXT_PRIMARY, it) },
                    onClear = { onClearApiKey(ApiKeySlot.TEXT_PRIMARY) }
                )
            }

            item {
                ApiKeySlotCard(
                    slot = ApiKeySlot.TEXT_FALLBACK,
                    maskedPreview = uiState.keySummary.maskedPreviews[ApiKeySlot.TEXT_FALLBACK].orEmpty(),
                    language = language,
                    isBusy = uiState.isLoadingModels,
                    onSave = { onSaveApiKey(ApiKeySlot.TEXT_FALLBACK, it) },
                    onClear = { onClearApiKey(ApiKeySlot.TEXT_FALLBACK) }
                )
            }

            // SECTION 2: Gemini Voice API Keys (Primary + Optional Fallback)
            item {
                SectionHeaderRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = if (language == AppLanguage.HINDI) {
                        "2. जेमिनी वॉइस API की (नो लोकल TTS)"
                    } else {
                        "2. Gemini Voice API Keys (No Local TTS)"
                    }
                )
            }

            item {
                ApiKeySlotCard(
                    slot = ApiKeySlot.VOICE_PRIMARY,
                    maskedPreview = uiState.keySummary.maskedPreviews[ApiKeySlot.VOICE_PRIMARY].orEmpty(),
                    language = language,
                    isBusy = uiState.isLoadingModels,
                    onSave = { onSaveApiKey(ApiKeySlot.VOICE_PRIMARY, it) },
                    onClear = { onClearApiKey(ApiKeySlot.VOICE_PRIMARY) }
                )
            }

            item {
                ApiKeySlotCard(
                    slot = ApiKeySlot.VOICE_FALLBACK,
                    maskedPreview = uiState.keySummary.maskedPreviews[ApiKeySlot.VOICE_FALLBACK].orEmpty(),
                    language = language,
                    isBusy = uiState.isLoadingModels,
                    onSave = { onSaveApiKey(ApiKeySlot.VOICE_FALLBACK, it) },
                    onClear = { onClearApiKey(ApiKeySlot.VOICE_FALLBACK) }
                )
            }

            // Loading / Status / Error / Missing-Model Warning
            if (uiState.isLoadingModels) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

            // SECTION 3: Dynamic Role-Based Model Selectors (zero hardcoded model IDs)
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
                        description = "Uses Primary Text API Key → Fallback Text API Key automatically.",
                        models = uiState.reasoningCandidates,
                        selectedModelId = uiState.selectedReasoningModelId,
                        testTag = "reasoning_model_dropdown",
                        onSelect = onSelectReasoningModel
                    )
                }

                item {
                    ModelRoleDropdownCard(
                        title = strings.voiceModelTitle,
                        description = "Uses Primary Voice API Key → Fallback Voice API Key for Gemini native audio replies (choose a TTS/audio-output capable model).",
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

@Composable
private fun SectionHeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ApiKeySlotCard(
    slot: ApiKeySlot,
    maskedPreview: String,
    language: AppLanguage,
    isBusy: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var draft by rememberSaveable(slot.name) { mutableStateOf("") }
    var visible by rememberSaveable(slot.name) { mutableStateOf(false) }
    val isSaved = maskedPreview.isNotBlank()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = if (slot.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (language == AppLanguage.HINDI) slot.titleHi else slot.titleEn,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (slot.isPrimary) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = if (slot.isPrimary) "PRIMARY" else "FALLBACK",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (isSaved) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Saved: $maskedPreview",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.testTag("clear_key_${slot.name.lowercase()}")
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear ${slot.titleEn}",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(if (isSaved) "Enter new key to replace…" else "Paste Gemini API key (AIza…)") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_key_${slot.name.lowercase()}")
                )
                Button(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSave(draft)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("save_key_${slot.name.lowercase()}")
                ) {
                    Text(if (language == AppLanguage.HINDI) "सेव" else "Save")
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
