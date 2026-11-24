package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.SettingsRepository
import com.example.service.voice.VoiceAssistantForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts the Voice Control AI foreground service after device reboot or app update
 * whenever the user has enabled both the Master Switch and Auto-Start on Boot.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(context).settingsFlow.first()
                if (settings.masterEnabled && settings.autoStartOnBoot &&
                    (settings.wakeWordEnabled || settings.overlayEnabled)
                ) {
                    VoiceAssistantForegroundService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
