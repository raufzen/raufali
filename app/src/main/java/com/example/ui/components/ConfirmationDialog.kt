package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.ConfirmationRequest

/**
 * High-contrast Safety Confirmation dialog shown whenever a sensitive action
 * (payment, uninstall, reset, delete, password, or message send) is triggered.
 * Responds to both touch buttons AND spoken "Yes/Haan" or "No/Cancel".
 */
@Composable
fun SensitiveConfirmationDialog(
    request: ConfirmationRequest,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)
    val categoryTitle = if (language == AppLanguage.ENGLISH) {
        request.category.titleEn
    } else {
        request.category.titleHi
    }
    val promptText = if (language == AppLanguage.ENGLISH) request.promptEn else request.promptHi

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = "Safety Shield",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = categoryTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodyLarge
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = request.actionSummary,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    text = if (language == AppLanguage.ENGLISH) {
                        "Voice confirmation: say \"Yes\" to confirm or \"Cancel\" to abort."
                    } else {
                        "आवाज़ से पुष्टि: \"हाँ\" बोलें या रद्द करने के लिए \"नहीं\" बोलें।"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_dialog_cancel")
                ) {
                    Text(strings.cancelAction)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_dialog_approve")
                ) {
                    Text(strings.confirmAction)
                }
            }
        }
    )
}
