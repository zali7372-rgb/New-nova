package com.nova.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nova.assistant.NovaApplication
import com.nova.assistant.R
import com.nova.assistant.system.voice.VoiceState
import com.nova.assistant.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Real Android foreground service that keeps the wake-word listening loop
 * alive while the user has enabled "continuous listening" in Settings.
 *
 * Per spec section 18/6: this does NOT claim to guarantee true always-on
 * background listening forever - Android's battery optimizations, Doze mode,
 * and OEM task killers can still stop it. What it DOES guarantee is: while
 * running, it is a real, user-visible foreground service (persistent
 * notification, as Android requires), it recovers from transient
 * SpeechRecognizer errors by restarting the listening loop, and if it is
 * killed by the OS, the notification disappears and NOVA's UI state honestly
 * reflects "not listening" rather than pretending to still be active.
 */
class NovaListeningService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(serviceJob)
    private var restartAttempts = 0

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification("NOVA figyeli az ébresztőszót…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val voiceManager = (application as? NovaApplication)?.voiceManager
        if (voiceManager == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            voiceManager.state.collect { state ->
                updateNotification(state)
                if (state == VoiceState.ERROR) {
                    handleRecoverableError(voiceManager)
                }
            }
        }

        voiceManager.startListening()
        return START_STICKY
    }

    private fun handleRecoverableError(voiceManager: com.nova.assistant.system.voice.VoiceManager) {
        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            updateNotification(VoiceState.ERROR, forceMessage = "NOVA leállt hibák miatt. Koppints az újraindításhoz.")
            stopSelf()
            return
        }
        restartAttempts++
        voiceManager.startListening()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(state: VoiceState, forceMessage: String? = null) {
        val text = forceMessage ?: when (state) {
            VoiceState.LISTENING_FOR_WAKE -> "NOVA figyeli az ébresztőszót…"
            VoiceState.LISTENING -> "NOVA hallgat…"
            VoiceState.THINKING -> "NOVA gondolkodik…"
            VoiceState.SPEAKING -> "NOVA válaszol…"
            VoiceState.ERROR -> "Hiba történt a hangfelismerésben. Koppints az újraindításhoz."
            VoiceState.UNAVAILABLE -> "A hangfelismerés nem elérhető ezen az eszközön."
            VoiceState.IDLE -> "NOVA készenlétben."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String): Notification {
        ensureChannel()
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_nova_status)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NOVA hallgatási állapot",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mutatja, amikor NOVA a háttérben figyeli az ébresztőszót."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "nova_listening_channel"
        private const val MAX_RESTART_ATTEMPTS = 3
    }
}
