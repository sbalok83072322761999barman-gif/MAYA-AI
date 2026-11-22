package com.example.automation

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.local.CapturedNotificationEntity
import com.example.data.local.MayaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationIntelligenceService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Cache direct-reply action if present
        sbn.notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }?.let { act ->
            replyActions[sbn.key] = act
        }

        scope.launch {
            val dao = MayaDatabase.getInstance(applicationContext).notificationDao()
            val duplicate = dao.findDuplicate(sbn.key, text) != null
            val appLabel = try {
                val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(ai).toString()
            } catch (_: Exception) {
                sbn.packageName
            }
            dao.insert(
                CapturedNotificationEntity(
                    notificationKey = sbn.key,
                    packageName = sbn.packageName,
                    appName = appLabel,
                    title = title,
                    text = text,
                    isDuplicate = duplicate,
                    timestamp = sbn.postTime
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val replyActions = ConcurrentHashMap<String, Notification.Action>()

        fun hasReplyAction(key: String): Boolean = replyActions.containsKey(key)

        fun sendInlineReply(context: android.content.Context, key: String, replyText: String): Boolean {
            val action = replyActions[key] ?: return false
            val inputs = action.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()
            for (input in inputs) {
                bundle.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(inputs, intent, bundle)
            return try {
                action.actionIntent.send(context, 0, intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
