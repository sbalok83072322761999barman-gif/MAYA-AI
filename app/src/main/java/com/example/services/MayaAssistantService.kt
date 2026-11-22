package com.example.services

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
import com.example.MainActivity
import com.example.MayaApplication
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MayaAssistantService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAssistant()
                return START_NOT_STICKY
            }
            ACTION_MUTE -> {
                val app = application as? MayaApplication
                app?.container?.audioEngine?.stopCapture()
                app?.container?.wakeWordEngine?.stop()
                _isRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val driving = intent?.getBooleanExtra(EXTRA_DRIVING_MODE, false) ?: false
        createChannel()
        val notif = buildNotification(driving)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val app = application as? MayaApplication
        val container = app?.container
        if (container != null) {
            container.audioEngine.startCapture()
            container.wakeWordEngine.start()
        }
        _isRunning.value = true
        return START_STICKY
    }

    private fun stopAssistant() {
        val app = application as? MayaApplication
        app?.container?.audioEngine?.stopCapture()
        app?.container?.wakeWordEngine?.stop()
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Maya Voice & Listening", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(driving: Boolean): Notification {
        val stopIntent = PendingIntent.getService(
            this, 10,
            Intent(this, MayaAssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 11,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (driving) "Maya Driving Mode Active" else "Maya Listening Active"
        val body = if (driving) "Hands-free voice & wake word running. Tap Stop anytime."
        else "Microphone active for Hey Maya / voice commands."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_maya_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop Mic", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_ASSISTANT"
        const val ACTION_STOP = "com.example.action.STOP_ASSISTANT"
        const val ACTION_MUTE = "com.example.action.MUTE_ASSISTANT"
        const val EXTRA_DRIVING_MODE = "extra_driving"
        private const val CHANNEL_ID = "maya_assistant_mic"
        private const val NOTIF_ID = 4001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
