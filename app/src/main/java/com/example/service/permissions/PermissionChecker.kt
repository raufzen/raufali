package com.example.service.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.domain.model.OemAutostartGuide
import com.example.domain.model.PermissionStatusItem
import com.example.domain.model.PermissionType
import com.example.service.accessibility.VoiceControlAccessibilityService

/**
 * Checks live status of all 10 permissions and builds exact Android Settings & OEM Autostart intents.
 */
class PermissionChecker(private val context: Context) {

    fun isGranted(type: PermissionType): Boolean = when (type) {
        PermissionType.ACCESSIBILITY -> isAccessibilityServiceEnabled()
        PermissionType.MICROPHONE -> hasRuntimePermission(Manifest.permission.RECORD_AUDIO)
        PermissionType.OVERLAY -> Settings.canDrawOverlays(context)
        PermissionType.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        PermissionType.CONTACTS -> hasRuntimePermission(Manifest.permission.READ_CONTACTS)
        PermissionType.PHONE_CALL -> hasRuntimePermission(Manifest.permission.CALL_PHONE)
        PermissionType.SMS -> hasRuntimePermission(Manifest.permission.SEND_SMS)
        PermissionType.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations()
        PermissionType.WRITE_SETTINGS -> Settings.System.canWrite(context)
        PermissionType.BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasRuntimePermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    fun getAllStatuses(): List<PermissionStatusItem> {
        return PermissionType.entries.map { type ->
            PermissionStatusItem(type = type, isGranted = isGranted(type))
        }
    }

    fun runtimePermissionString(type: PermissionType): String? = when (type) {
        PermissionType.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        PermissionType.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
        PermissionType.CONTACTS -> Manifest.permission.READ_CONTACTS
        PermissionType.PHONE_CALL -> Manifest.permission.CALL_PHONE
        PermissionType.SMS -> Manifest.permission.SEND_SMS
        PermissionType.BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else null
        else -> null
    }

    fun buildSettingsIntent(type: PermissionType): Intent {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val intent = when (type) {
            PermissionType.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            PermissionType.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            PermissionType.BATTERY_OPTIMIZATION -> Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                pkgUri
            )
            PermissionType.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            PermissionType.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openPermissionSettings(type: PermissionType): Boolean {
        return runCatching {
            context.startActivity(buildSettingsIntent(type))
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(context, VoiceControlAccessibilityService::class.java)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        if (enabledServices.any {
                val id = it.resolveInfo?.serviceInfo?.let { si -> ComponentName(si.packageName, si.name) }
                id == expectedComponent
            }
        ) {
            return true
        }
        val enabledStr = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledStr.contains(expectedComponent.flattenToString(), ignoreCase = true) ||
            enabledStr.contains(expectedComponent.flattenToShortString(), ignoreCase = true)
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun hasRuntimePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun detectedManufacturer(): String = Build.MANUFACTURER.orEmpty()

    fun getOemAutostartGuides(): List<OemAutostartGuide> = listOf(
        OemAutostartGuide(
            manufacturerKey = "xiaomi",
            brandTitle = "Xiaomi / Redmi / POCO (MIUI & HyperOS)",
            stepsEn = "1. Open Security > Permissions > Background Autostart > enable Voice Control AI.\n2. App Info > Battery Saver > select 'No restrictions'.\n3. Lock the app in Recents view.",
            stepsHi = "1. Security > Permissions > Background Autostart में Voice Control AI चालू करें।\n2. App Info > Battery Saver में 'No restrictions' चुनें।\n3. Recent Apps में इस ऐप को लॉक करें।"
        ),
        OemAutostartGuide(
            manufacturerKey = "oppo",
            brandTitle = "Oppo / Realme / OnePlus (ColorOS)",
            stepsEn = "1. Settings > Apps > Auto-launch > turn ON Voice Control AI.\n2. Settings > Battery > App Battery Management > allow background activity.",
            stepsHi = "1. Settings > Apps > Auto-launch में Voice Control AI को ON करें।\n2. Settings > Battery > App Battery Management में बैकग्राउंड अनुमति दें।"
        ),
        OemAutostartGuide(
            manufacturerKey = "vivo",
            brandTitle = "Vivo / iQOO (FuntouchOS)",
            stepsEn = "1. iManager > App Manager > Autostart Manager > enable Voice Control AI.\n2. Settings > Battery > Background Power Consumption > allow high background usage.",
            stepsHi = "1. iManager > App Manager > Autostart Manager में Voice Control AI चालू करें।\n2. Battery > Background Power Consumption में बैकग्राउंड अनुमति दें।"
        ),
        OemAutostartGuide(
            manufacturerKey = "samsung",
            brandTitle = "Samsung (One UI)",
            stepsEn = "1. Settings > Battery > Background usage limits > add Voice Control AI to 'Never auto sleeping apps'.\n2. App Info > Battery > select 'Unrestricted'.",
            stepsHi = "1. Settings > Battery > Background usage limits में 'Never auto sleeping apps' में जोड़ें।\n2. App Info > Battery में 'Unrestricted' चुनें।"
        )
    )

    fun openOemAutostartSettings(manufacturerKey: String): Boolean {
        val candidates = when (manufacturerKey.lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            "oppo", "realme", "oneplus" -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity")
            )
            "vivo", "iqoo" -> listOf(
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            )
            "samsung" -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            else -> emptyList()
        }

        for (component in candidates) {
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return true
            }
        }
        return openPermissionSettings(PermissionType.BATTERY_OPTIMIZATION)
    }
}
