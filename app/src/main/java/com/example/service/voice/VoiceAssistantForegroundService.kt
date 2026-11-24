package com.example.service.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.VoiceControlApp
import com.example.domain.parser.GlobalNavAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent microphone Foreground Service that:
 * - Keeps wake-word detection active ("Hey Assistant", configurable)
 * - Hosts the draggable Floating Mic Overlay over other apps
 * - Displays live assistant status in a persistent notification with Talk & Stop actions
 */
class VoiceAssistantForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayController: FloatingOverlayController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForegroundSafely(getString(R.string.app_name), "Ready for voice commands")

        val container = (application as VoiceControlApp).container

        overlayController = FloatingOverlayController(
            context = this,
            onTapMic = {
                container.commandDispatcher.toggleVoiceListening()
            },
            onQuickAction = { action ->
                serviceScope.launch {
                    when (action) {
                        FloatingOverlayController.QuickOverlayAction.HOME ->
                            container.commandDispatcher.executeDirectGlobal(GlobalNavAction.HOME)
                        FloatingOverlayController.QuickOverlayAction.BACK ->
                            container.commandDispatcher.executeDirectGlobal(GlobalNavAction.BACK)
                        FloatingOverlayController.QuickOverlayAction.SCREENSHOT ->
                            container.commandDispatcher.executeDirectGlobal(GlobalNavAction.SCREENSHOT)
                        FloatingOverlayController.QuickOverlayAction.STOP_TASK ->
                            container.commandDispatcher.stopActiveTask()
                        FloatingOverlayController.QuickOverlayAction.OPEN_APP -> {
                            val launchIntent = Intent(this@VoiceAssistantForegroundService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(launchIntent)
                        }
                        FloatingOverlayController.QuickOverlayAction.HIDE_OVERLAY -> {
                            container.settingsRepository.setOverlayEnabled(false)
                            overlayController?.hide()
                        }
                    }
                }
            }
        )

        // React to assistant state changes for overlay visuals + notification text
        serviceScope.launch {
            container.commandDispatcher.assistantState.collectLatest { state ->
                overlayController?.updateState(state)
                val statusText = when (state) {
                    com.example.domain.model.AssistantState.IDLE -> "Listening for wake word or tap"
                    com.example.domain.model.AssistantState.LISTENING -> "Listening to your command…"
                    com.example.domain.model.AssistantState.THINKING -> "AI Agent executing task…"
                    com.example.domain.model.AssistantState.SPEAKING -> "Speaking response…"
                    com.example.domain.model.AssistantState.CONFIRMING -> "Waiting for confirmation…"
                    com.example.domain.model.AssistantState.ERROR -> "Check assistant status"
                }
                updateNotification(getString(R.string.app_name), statusText)
            }
        }

        // React to settings changes (master switch, overlay, wake word)
        serviceScope.launch {
            container.settingsRepository.settingsFlow.collectLatest { settings ->
                if (!settings.masterEnabled) {
                    overlayController?.hide()
                    container.speechRecognizer.stopListening()
                    stopSelf()
                    return@collectLatest
                }

                if (settings.overlayEnabled) {
                    overlayController?.show()
                } else {
                    overlayController?.hide()
                }

                if (settings.wakeWordEnabled &&
                    container.permissionChecker.isGranted(com.example.domain.model.PermissionType.MICROPHONE)
                ) {
                    container.speechRecognizer.startWakeWordLoop(
                        wakeWord = settings.wakeWord,
                        localeTag = settings.appLanguage.localeTag
                    ) { trailingCommand ->
                        if (!trailingCommand.isNullOrBlank()) {
                            container.commandDispatcher.submitTextCommand(trailingCommand)
                        } else {
                            container.commandDispatcher.toggleVoiceListening()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as VoiceControlApp).container
        when (intent?.action) {
            ACTION_TALK -> container.commandDispatcher.toggleVoiceListening()
            ACTION_STOP_TASK -> container.commandDispatcher.stopActiveTask()
            ACTION_STOP_SERVICE -> {
                overlayController?.hide()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayController?.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely(title: String, body: String) {
        val notification = buildNotification(title, body)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, body))
    }

    private fun buildNotification(title: String, body: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val talkIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceAssistantForegroundService::class.java).setAction(ACTION_TALK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VoiceAssistantForegroundService::class.java).setAction(ACTION_STOP_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ASSISTANT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Talk", talkIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ASSISTANT,
            getString(R.string.notification_channel_assistant_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_assistant_desc)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ASSISTANT = "voice_control_assistant_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_TALK = "com.example.action.TALK"
        const val ACTION_STOP_TASK = "com.example.action.STOP_TASK"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java)
                .setAction(ACTION_STOP_SERVICE)
            context.startService(intent)
        }
    }
}
