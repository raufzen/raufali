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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AgentStepLog
import com.example.domain.model.AgentTaskState
import com.example.domain.model.AgentTaskStatus
import com.example.domain.model.AppLanguage
import com.example.domain.model.StepOutcome
import com.example.ui.theme.JetBrainsMonoFamily

/**
 * Real-time AI Agent Task Live View showing:
 * - Current step progress (max 20 steps)
 * - Streaming AI reasoning / plan
 * - Prominent STOP button + "say stop to cancel" hint
 * - Interactive ask_user reply bar
 * - Step-by-step action execution timeline
 */
@Composable
fun TaskLiveViewScreen(
    taskState: AgentTaskState,
    language: AppLanguage,
    onStopTask: () -> Unit,
    onAnswerQuestion: (String) -> Unit,
    onStartSampleTask: (String) -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)
    var userReply by rememberSaveable { mutableStateOf("") }

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
                Text(
                    text = strings.navLive,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (taskState.status == AgentTaskStatus.IDLE && taskState.steps.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (language == AppLanguage.HINDI) {
                                    "कोई सक्रिय AI टास्क नहीं है"
                                } else {
                                    "No Active AI Agent Task"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (language == AppLanguage.HINDI) {
                                    "जटिल मल्टी-स्टेप कमांड (जैसे 'WhatsApp पर रोहित को गुड मॉर्निंग भेजो') यहाँ लाइव दिखते हैं।"
                                } else {
                                    "Complex multi-step voice commands (like 'Send Rohit a good morning message on WhatsApp') execute step-by-step right here with full safety limits."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    onStartSampleTask("Read what is on my screen and summarize it")
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("live_try_sample_task")
                            ) {
                                Text(
                                    if (language == AppLanguage.HINDI) {
                                        "स्क्रीन विश्लेषण आज़माएं"
                                    } else {
                                        "Try: Analyze Current Screen"
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Active / Recent task status card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Step ${taskState.currentStep} / ${taskState.maxSteps}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                StatusPill(status = taskState.status)
                            }

                            Text(
                                text = taskState.userCommand,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            LinearProgressIndicator(
                                progress = {
                                    (taskState.currentStep.toFloat() / taskState.maxSteps.coerceAtLeast(1))
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )

                            if (taskState.activeActionLabel.isNotBlank()) {
                                Text(
                                    text = taskState.activeActionLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (taskState.streamingThought.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = taskState.streamingThought,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (taskState.finalMessage.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = taskState.finalMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            // Prominent emergency Stop button + voice cancel hint
                            if (taskState.isActive) {
                                Button(
                                    onClick = onStopTask,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("live_stop_task_btn")
                                ) {
                                    Icon(Icons.Rounded.StopCircle, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(strings.stopTask, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = strings.sayStopHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ask_user interactive reply prompt
                val question = taskState.pendingQuestion
                if (question != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "AI Agent asks:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = question,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = userReply,
                                        onValueChange = { userReply = it },
                                        placeholder = { Text("Type or speak reply…") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("live_ask_user_input")
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            if (userReply.isNotBlank()) {
                                                onAnswerQuestion(userReply)
                                                userReply = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .testTag("live_ask_user_send")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send reply")
                                    }
                                }
                            }
                        }
                    }
                }

                // Step-by-step Action Log
                item {
                    Text(
                        text = if (language == AppLanguage.HINDI) "एक्शन लॉग" else "Action Log",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(taskState.steps.reversed()) { step ->
                    StepLogCard(step = step)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: AgentTaskStatus) {
    val (label, container) = when (status) {
        AgentTaskStatus.RUNNING -> "RUNNING" to MaterialTheme.colorScheme.primaryContainer
        AgentTaskStatus.WAITING_CONFIRMATION -> "CONFIRM" to MaterialTheme.colorScheme.errorContainer
        AgentTaskStatus.WAITING_USER_ANSWER -> "QUESTION" to MaterialTheme.colorScheme.secondaryContainer
        AgentTaskStatus.COMPLETED -> "DONE" to MaterialTheme.colorScheme.tertiaryContainer
        AgentTaskStatus.STOPPED -> "STOPPED" to MaterialTheme.colorScheme.surfaceVariant
        AgentTaskStatus.FAILED -> "FAILED" to MaterialTheme.colorScheme.errorContainer
        AgentTaskStatus.IDLE -> "IDLE" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StepLogCard(step: AgentStepLog) {
    val icon = when (step.outcome) {
        StepOutcome.SUCCESS -> Icons.Rounded.CheckCircle
        StepOutcome.FAILED -> Icons.Rounded.ErrorOutline
        StepOutcome.WAITING_CONFIRMATION -> Icons.Rounded.Shield
        else -> Icons.Rounded.HourglassTop
    }
    val tint = when (step.outcome) {
        StepOutcome.SUCCESS -> MaterialTheme.colorScheme.tertiary
        StepOutcome.FAILED -> MaterialTheme.colorScheme.error
        StepOutcome.WAITING_CONFIRMATION -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = step.outcome.name, tint = tint)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "#${step.stepNumber} • ${step.toolName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = step.actionSummary,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
