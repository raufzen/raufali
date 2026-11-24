package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppSettings
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage

@Composable
fun PrivacyAboutScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleConfirmSensitive: (Boolean) -> Unit,
    onResetOnboarding: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = strings.privacyTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Image(
                    painter = painterResource(id = R.drawable.img_privacy_shield_1795491456259),
                    contentDescription = "Privacy & Security Shield",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            // Master On/Off + Sensitive Confirmation Toggles
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Master On / Off Switch", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Immediately pauses wake-word listening, floating overlay, and AI actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.masterEnabled,
                                onCheckedChange = onToggleMaster,
                                modifier = Modifier.testTag("privacy_master_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Confirm Sensitive Actions", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Always require both voice AND on-screen confirmation before payments, uninstalls, resets, deletions, password changes, or sending messages.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.confirmSensitiveActions,
                                onCheckedChange = onToggleConfirmSensitive,
                                modifier = Modifier.testTag("privacy_confirm_switch")
                            )
                        }
                    }
                }
            }

            item {
                PrivacyPillarCard(
                    icon = Icons.Rounded.VisibilityOff,
                    title = if (settings.appLanguage == AppLanguage.HINDI) {
                        "स्क्रीन डेटा कभी सेव या लॉग नहीं होता"
                    } else {
                        "Zero Screen Logging & Password Masking"
                    },
                    body = if (settings.appLanguage == AppLanguage.HINDI) {
                        "पासवर्ड फ़ील्ड, OTP और कार्ड नंबर हमेशा छिपा दिए जाते हैं। केवल सक्रिय AI टास्क के दौरान ही न्यूनतम स्क्रीन ट्री आपकी अपनी जेमिनी API की पर भेजा जाता है।"
                    } else {
                        "Password fields are skipped completely and card numbers/OTPs are masked. Screen content is NEVER logged or stored in Room. Only the minimal sanitized UI tree is sent directly to your own Gemini API key during an active AI task."
                    }
                )
            }

            item {
                PrivacyPillarCard(
                    icon = Icons.Rounded.Lock,
                    title = "Hardware-Backed API Key Encryption",
                    body = "Your Gemini API key is encrypted on-device with Android Keystore AES-256-GCM and never transmitted anywhere except directly to Google's Gemini REST API."
                )
            }

            item {
                PrivacyPillarCard(
                    icon = Icons.Rounded.Shield,
                    title = "Accessibility Service Disclosure",
                    body = "Voice Control AI uses Android's AccessibilityService API exclusively to read visible interactive buttons and execute user-initiated voice commands (tap, scroll, type, navigate). You can disable it anytime from Permissions Dashboard."
                )
            }

            item {
                OutlinedButton(
                    onClick = onResetOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("reset_onboarding_btn")
                ) {
                    Text("Replay Permission Onboarding Wizard")
                }
            }
        }
    }
}

@Composable
private fun PrivacyPillarCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
