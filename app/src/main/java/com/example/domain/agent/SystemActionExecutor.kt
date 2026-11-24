package com.example.domain.agent

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import com.example.domain.model.CommandResult
import com.example.domain.model.PermissionType
import com.example.domain.parser.AppLauncherMatcher
import com.example.domain.parser.BrightnessAction
import com.example.domain.parser.SettingsPage
import com.example.domain.parser.VolumeAction
import com.example.service.permissions.PermissionChecker

/**
 * Executes local Android system actions, hardware toggles, communication intents,
 * and Settings panels with strict permission checks and bilingual results.
 */
class SystemActionExecutor(
    private val context: Context,
    private val appMatcher: AppLauncherMatcher,
    private val permissionChecker: PermissionChecker
) {

    fun openApp(appQuery: String): CommandResult {
        val match = appMatcher.findBestMatch(appQuery)
            ?: return CommandResult.Failed(
                reasonEn = "Could not find an installed app matching \"$appQuery\".",
                reasonHi = "\"$appQuery\" नाम का कोई ऐप इस फोन में नहीं मिला।"
            )

        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return CommandResult.Failed(
                reasonEn = "Found ${match.label}, but it cannot be launched directly.",
                reasonHi = "${match.label} मिला, लेकिन इसे सीधे खोला नहीं जा सका।"
            )

        return runCatching {
            context.startActivity(launchIntent)
            CommandResult.Success(
                messageEn = "Opening ${match.label}.",
                messageHi = "${match.label} खोल रहा हूँ।"
            )
        }.getOrElse {
            CommandResult.Failed(
                reasonEn = "Failed to open ${match.label}: ${it.message}",
                reasonHi = "${match.label} खोलने में समस्या आई।"
            )
        }
    }

    fun callContact(contactQuery: String): CommandResult {
        val trimmed = contactQuery.trim()
        val isPhoneNumber = trimmed.replace(Regex("""[\s\-+()]"""), "").all { it.isDigit() } &&
            trimmed.count { it.isDigit() } >= 3

        val resolvedNumber: String
        val displayLabel: String

        if (isPhoneNumber) {
            resolvedNumber = trimmed
            displayLabel = trimmed
        } else {
            if (!permissionChecker.isGranted(PermissionType.CONTACTS)) {
                return CommandResult.NeedsPermission(
                    permissionType = PermissionType.CONTACTS,
                    messageEn = "Contacts permission is needed to find \"$trimmed\".",
                    messageHi = "\"$trimmed\" का नंबर खोजने के लिए कॉन्टैक्ट्स की अनुमति चाहिए।"
                )
            }
            val found = lookupContactPhone(trimmed)
                ?: return CommandResult.Failed(
                    reasonEn = "No contact named \"$trimmed\" was found.",
                    reasonHi = "\"$trimmed\" नाम का कोई कॉन्टैक्ट नहीं मिला।"
                )
            displayLabel = found.first
            resolvedNumber = found.second
        }

        val hasCallPerm = permissionChecker.isGranted(PermissionType.PHONE_CALL)
        val intentAction = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val callIntent = Intent(intentAction, Uri.parse("tel:${Uri.encode(resolvedNumber)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(callIntent)
            if (hasCallPerm) {
                CommandResult.Success(
                    messageEn = "Calling $displayLabel.",
                    messageHi = "$displayLabel को कॉल लगा रहा हूँ।"
                )
            } else {
                CommandResult.Success(
                    messageEn = "Opened dialer for $displayLabel. Grant Phone permission for direct calls.",
                    messageHi = "$displayLabel का नंबर डायलर में खोल दिया है। सीधे कॉल के लिए फ़ोन अनुमति दें।"
                )
            }
        }.getOrElse {
            CommandResult.Failed(
                reasonEn = "Unable to place call: ${it.message}",
                reasonHi = "कॉल नहीं लग सकी।"
            )
        }
    }

    fun sendSms(recipient: String, message: String): CommandResult {
        if (!permissionChecker.isGranted(PermissionType.SMS)) {
            return CommandResult.NeedsPermission(
                permissionType = PermissionType.SMS,
                messageEn = "SMS permission is needed to send text messages.",
                messageHi = "SMS भेजने के लिए SMS की अनुमति ज़रूरी है।"
            )
        }

        val trimmed = recipient.trim()
        val isPhoneNumber = trimmed.replace(Regex("""[\s\-+()]"""), "").all { it.isDigit() } &&
            trimmed.count { it.isDigit() } >= 3

        val number: String
        val label: String

        if (isPhoneNumber) {
            number = trimmed
            label = trimmed
        } else {
            if (!permissionChecker.isGranted(PermissionType.CONTACTS)) {
                return CommandResult.NeedsPermission(
                    permissionType = PermissionType.CONTACTS,
                    messageEn = "Contacts permission is needed to look up $trimmed.",
                    messageHi = "$trimmed का नंबर खोजने के लिए कॉन्टैक्ट्स अनुमति चाहिए।"
                )
            }
            val found = lookupContactPhone(trimmed)
                ?: return CommandResult.Failed(
                    reasonEn = "Could not find a phone number for \"$trimmed\".",
                    reasonHi = "\"$trimmed\" का फ़ोन नंबर नहीं मिला।"
                )
            label = found.first
            number = found.second
        }

        return runCatching {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(number, null, message, null, null)
            }
            CommandResult.Success(
                messageEn = "SMS sent to $label.",
                messageHi = "$label को SMS भेज दिया गया है।"
            )
        }.getOrElse {
            CommandResult.Failed(
                reasonEn = "Failed to send SMS: ${it.message}",
                reasonHi = "SMS भेजने में त्रुटि हुई।"
            )
        }
    }

    fun setFlashlight(turnOn: Boolean): CommandResult {
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val torchCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return CommandResult.Failed(
                reasonEn = "No flashlight hardware found on this device.",
                reasonHi = "इस फ़ोन में टॉर्च (फ्लैशलाइट) उपलब्ध नहीं है।"
            )
            cm.setTorchMode(torchCameraId, turnOn)
            if (turnOn) {
                CommandResult.Success("Flashlight turned on.", "टॉर्च चालू कर दी गई है।")
            } else {
                CommandResult.Success("Flashlight turned off.", "टॉर्च बंद कर दी गई है।")
            }
        }.getOrElse {
            CommandResult.Failed(
                reasonEn = "Could not toggle flashlight: ${it.message}",
                reasonHi = "टॉर्च चालू या बंद नहीं हो सकी।"
            )
        }
    }

    fun changeVolume(action: VolumeAction, percent: Int?): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val maxVol = am.getStreamMaxVolume(stream).coerceAtLeast(1)

        when (action) {
            VolumeAction.UP -> {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return CommandResult.Success("Volume increased.", "आवाज़ बढ़ा दी गई है।")
            }
            VolumeAction.DOWN -> {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return CommandResult.Success("Volume decreased.", "आवाज़ कम कर दी गई है।")
            }
            VolumeAction.MUTE -> {
                am.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                return CommandResult.Success("Volume muted.", "आवाज़ म्यूट कर दी गई है।")
            }
            VolumeAction.MAX -> {
                am.setStreamVolume(stream, maxVol, AudioManager.FLAG_SHOW_UI)
                return CommandResult.Success("Volume set to maximum.", "आवाज़ पूरी (फुल) कर दी गई है।")
            }
            VolumeAction.SET_PERCENT -> {
                val pct = (percent ?: 50).coerceIn(0, 100)
                val target = ((pct / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
                am.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
                return CommandResult.Success("Volume set to $pct%.", "आवाज़ $pct% पर सेट कर दी गई है।")
            }
        }
    }

    fun changeBrightness(action: BrightnessAction, percent: Int?): CommandResult {
        if (!Settings.System.canWrite(context)) {
            return CommandResult.NeedsPermission(
                permissionType = PermissionType.WRITE_SETTINGS,
                messageEn = "Allow 'Modify System Settings' permission to adjust screen brightness.",
                messageHi = "ब्राइटनेस बदलने के लिए 'सिस्टम सेटिंग्स बदलें' अनुमति चालू करें।"
            )
        }
        return runCatching {
            val cr = context.contentResolver
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val updated = when (action) {
                BrightnessAction.UP -> (current + 50).coerceAtMost(255)
                BrightnessAction.DOWN -> (current - 50).coerceAtLeast(15)
                BrightnessAction.SET_PERCENT -> (((percent ?: 50).coerceIn(5, 100) / 100.0) * 255).toInt()
            }
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, updated)
            val pct = ((updated / 255.0) * 100).toInt()
            CommandResult.Success(
                messageEn = "Brightness set to $pct%.",
                messageHi = "स्क्रीन की रोशनी $pct% पर सेट कर दी गई है।"
            )
        }.getOrElse {
            CommandResult.Failed(
                reasonEn = "Unable to change brightness: ${it.message}",
                reasonHi = "ब्राइटनेस बदलने में समस्या आई।"
            )
        }
    }

    fun setDoNotDisturb(enable: Boolean): CommandResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return CommandResult.Failed(
                reasonEn = "Opened Do Not Disturb access settings. Please allow Voice Control AI.",
                reasonHi = "डू नॉट डिस्टर्ब सेटिंग्स खोली हैं। कृपया Voice Control AI को अनुमति दें।"
            )
        }
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return if (enable) {
            CommandResult.Success("Do Not Disturb enabled.", "डू नॉट डिस्टर्ब चालू कर दिया गया है।")
        } else {
            CommandResult.Success("Do Not Disturb turned off.", "डू नॉट डिस्टर्ब बंद कर दिया गया है।")
        }
    }

    fun openCamera(): CommandResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            CommandResult.Success("Opening camera.", "कैमरा खोल रहा हूँ।")
        }.getOrElse {
            CommandResult.Failed("Could not open camera.", "कैमरा नहीं खुल सका।")
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): CommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            val timeStr = String.format("%02d:%02d", hour, minute)
            CommandResult.Success(
                messageEn = "Setting alarm for $timeStr.",
                messageHi = "$timeStr का अलार्म सेट कर रहा हूँ।"
            )
        }.getOrElse {
            CommandResult.Failed("Could not open alarm app.", "अलार्म ऐप नहीं खुल सका।")
        }
    }

    fun setTimer(durationSeconds: Int, label: String): CommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            CommandResult.Success(
                messageEn = "Starting timer for $durationSeconds seconds.",
                messageHi = "$durationSeconds सेकंड का टाइमर चालू कर रहा हूँ।"
            )
        }.getOrElse {
            CommandResult.Failed("Could not start timer.", "टाइमर चालू नहीं हो सका।")
        }
    }

    fun openSettingsPage(page: SettingsPage): CommandResult {
        val intent = when (page) {
            SettingsPage.WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            SettingsPage.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SettingsPage.MOBILE_DATA -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            }
            SettingsPage.AIRPLANE_MODE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            SettingsPage.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SettingsPage.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
            SettingsPage.BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            SettingsPage.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SettingsPage.APPS -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            SettingsPage.MAIN -> Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(intent)
            val label = page.name.lowercase().replace('_', ' ')
            CommandResult.Success(
                messageEn = "Opened $label settings.",
                messageHi = "$label सेटिंग्स खोल दी गई हैं।"
            )
        }.getOrElse {
            CommandResult.Failed(
                reasonEn = "Could not open settings page.",
                reasonHi = "सेटिंग्स पेज नहीं खुल सका।"
            )
        }
    }

    private fun lookupContactPhone(query: String): Pair<String, String>? {
        val lower = query.lowercase()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        var exactMatch: Pair<String, String>? = null
        var partialMatch: Pair<String, String>? = null

        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val num = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isBlank() || num.isBlank()) continue
                    val lowerName = name.lowercase()
                    if (lowerName == lower) {
                        exactMatch = name to num
                        break
                    }
                    if (partialMatch == null && (lowerName.contains(lower) || lower.contains(lowerName))) {
                        partialMatch = name to num
                    }
                }
            }
        }
        return exactMatch ?: partialMatch
    }
}
