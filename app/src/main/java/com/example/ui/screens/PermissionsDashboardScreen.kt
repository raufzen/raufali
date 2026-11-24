package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.i18n.LocalizedStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.OemAutostartGuide
import com.example.domain.model.PermissionStatusItem
import com.example.domain.model.PermissionType

@Composable
fun PermissionsDashboardScreen(
    language: AppLanguage,
    statuses: List<PermissionStatusItem>,
    detectedManufacturer: String,
    oemGuides: List<OemAutostartGuide>,
    runtimePermissionFor: (PermissionType) -> String?,
    onBack: () -> Unit,
    onOpenSettings: (PermissionType) -> Unit,
    onOpenOemAutostart: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val strings = LocalizedStrings.forLanguage(language)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    val grantedCount = statuses.count { it.isGranted }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("permissions_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = strings.permissionsTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$grantedCount of ${statuses.size} permissions active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(statuses, key = { it.type.name }) { item ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = iconForPermission(item.type),
                            contentDescription = null,
                            tint = if (item.isGranted) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (language == AppLanguage.HINDI) item.type.titleHi else item.type.titleEn,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (language == AppLanguage.HINDI) item.type.whyHi else item.type.whyEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.isGranted) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = strings.granted,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Button(
                                onClick = {
                                    val runtime = runtimePermissionFor(item.type)
                                    if (runtime != null) runtimeLauncher.launch(runtime)
                                    else onOpenSettings(item.type)
                                },
                                modifier = Modifier.testTag("perm_fix_${item.type.name.lowercase()}")
                            ) {
                                Text(strings.fixNow)
                            }
                        }
                    }
                }
            }

            // OEM Autostart Reliability Guide (Xiaomi, Oppo, Vivo, Samsung)
            item {
                Text(
                    text = strings.oemAutostartTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (detectedManufacturer.isNotBlank()) {
                    Text(
                        text = "Detected device brand: $detectedManufacturer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(oemGuides, key = { it.manufacturerKey }) { guide ->
                val isCurrentBrand = detectedManufacturer.contains(guide.manufacturerKey, ignoreCase = true)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentBrand) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Smartphone, contentDescription = null)
                            Text(guide.brandTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (isCurrentBrand) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                                    Text(
                                        text = "Your Phone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (language == AppLanguage.HINDI) guide.stepsHi else guide.stepsEn,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { onOpenOemAutostart(guide.manufacturerKey) },
                            modifier = Modifier.testTag("oem_open_${guide.manufacturerKey}")
                        ) {
                            Text("Open ${guide.manufacturerKey.replaceFirstChar { it.uppercase() }} Autostart Settings")
                        }
                    }
                }
            }
        }
    }
}
