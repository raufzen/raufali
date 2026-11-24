package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.PermissionStatusItem
import com.example.domain.model.PermissionType

/**
 * Step-by-step first-launch permission onboarding:
 * - Page 0: Welcome + Hindi/English/Hinglish language selector
 * - Pages 1..10: One permission per screen with illustration, bilingual WHY explanation,
 *   direct launcher/Settings button, and automatic re-check on ON_RESUME.
 */
@Composable
fun OnboardingScreen(
    language: AppLanguage,
    permissionStatuses: List<PermissionStatusItem>,
    runtimePermissionFor: (PermissionType) -> String?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenSettings: (PermissionType) -> Unit,
    onRefreshPermissions: () -> Unit,
    onFinishOnboarding: () -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)
    val permissions = PermissionType.entries
    val totalSteps = permissions.size + 1 // Welcome + 10 permissions
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    // Automatically re-check permission statuses whenever user returns from Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Progress header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (stepIndex == 0) strings.appTitle else "Step $stepIndex / ${permissions.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onFinishOnboarding,
                    modifier = Modifier.testTag("onboarding_skip_all")
                ) {
                    Text(strings.skipForNow)
                }
            }

            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = stepIndex,
                modifier = Modifier.weight(1f),
                label = "onboarding_step"
            ) { currentStep ->
                if (currentStep == 0) {
                    WelcomeOnboardingStep(
                        language = language,
                        onSelectLanguage = onSelectLanguage
                    )
                } else {
                    val permType = permissions[currentStep - 1]
                    val isGranted = permissionStatuses.firstOrNull { it.type == permType }?.isGranted == true
                    PermissionOnboardingStep(
                        type = permType,
                        stepNumber = currentStep,
                        isGranted = isGranted,
                        language = language,
                        onRequest = {
                            val runtimePerm = runtimePermissionFor(permType)
                            if (runtimePerm != null) {
                                runtimeLauncher.launch(runtimePerm)
                            } else {
                                onOpenSettings(permType)
                            }
                        },
                        onOpenSettings = { onOpenSettings(permType) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { stepIndex-- },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("onboarding_back_btn")
                    ) {
                        Text(if (language == AppLanguage.HINDI) "पीछे" else "Back")
                    }
                }
                Button(
                    onClick = {
                        if (stepIndex < totalSteps - 1) {
                            stepIndex++
                        } else {
                            onFinishOnboarding()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("onboarding_next_btn")
                ) {
                    val btnLabel = when {
                        stepIndex == 0 -> strings.getStarted
                        stepIndex == totalSteps - 1 -> strings.finishSetup
                        else -> strings.nextStep
                    }
                    Text(btnLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WelcomeOnboardingStep(
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_onboarding_voice_ai_1795491426092),
            contentDescription = "Voice Control AI illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        Text(
            text = strings.onboardingWelcomeTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = strings.onboardingWelcomeBody,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose Language / भाषा चुनें",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.nativeLabel) },
                            modifier = Modifier.testTag("onboarding_lang_${lang.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionOnboardingStep(
    type: PermissionType,
    stepNumber: Int,
    isGranted: Boolean,
    language: AppLanguage,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)
    val bannerRes = when {
        stepNumber % 3 == 1 -> R.drawable.img_onboarding_accessibility_1795491453264
        stepNumber % 3 == 2 -> R.drawable.img_onboarding_voice_ai_1795491426092
        else -> R.drawable.img_privacy_shield_1795491456259
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = bannerRes),
                contentDescription = type.titleEn,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForPermission(type),
                        contentDescription = type.titleEn,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (language == AppLanguage.HINDI) type.titleHi else type.titleEn,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isGranted) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Granted",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (isGranted) strings.granted else strings.notGranted,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isGranted) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
        }

        // Bilingual Hindi + English explanation cards
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Why it is needed (English)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = type.whyEn,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "यह क्यों ज़रूरी है (हिन्दी)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = type.whyHi,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!isGranted) {
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_grant_${type.name.lowercase()}")
            ) {
                Text(
                    text = if (language == AppLanguage.HINDI) {
                        "${type.titleHi} चालू करें"
                    } else {
                        "Grant ${type.titleEn}"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(strings.openSettings)
            }
        }
    }
}

fun iconForPermission(type: PermissionType): ImageVector = when (type) {
    PermissionType.ACCESSIBILITY -> Icons.Rounded.AccessibilityNew
    PermissionType.MICROPHONE -> Icons.Rounded.Mic
    PermissionType.OVERLAY -> Icons.Rounded.Layers
    PermissionType.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
    PermissionType.CONTACTS -> Icons.Rounded.Contacts
    PermissionType.PHONE_CALL -> Icons.Rounded.Call
    PermissionType.SMS -> Icons.Rounded.Sms
    PermissionType.BATTERY_OPTIMIZATION -> Icons.Rounded.BatteryChargingFull
    PermissionType.WRITE_SETTINGS -> Icons.Rounded.SettingsApplications
    PermissionType.BLUETOOTH -> Icons.Rounded.Bluetooth
}
