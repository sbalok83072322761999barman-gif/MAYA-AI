package com.example.workers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import java.util.concurrent.TimeUnit

class MayaReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_REMINDER_ID, 0L)
        val title = inputData.getString(KEY_TITLE) ?: "Maya AI Reminder"
        val isAlarm = inputData.getBoolean(KEY_IS_ALARM, false)
        postReminderNotification(applicationContext, id, title, isAlarm)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "reminder_title"
        const val KEY_IS_ALARM = "is_alarm"
        private const val CHANNEL_ID = "maya_reminders"

        fun postReminderNotification(context: Context, id: Long, title: String, isAlarm: Boolean) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Maya Reminders & Alarms",
                        if (isAlarm) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val openIntent = PendingIntent.getActivity(
                context,
                id.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_maya_tile)
                .setContentTitle(if (isAlarm) "Maya Alarm" else "Maya Reminder")
                .setContentText(title)
                .setPriority(if (isAlarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
            nm.notify(5000 + id.toInt(), notif)
        }
    }
}

class MayaReminderScheduler(private val context: Context) {
    private val appContext = context.applicationContext

    fun canScheduleExactAlarms(): Boolean {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    fun scheduleReminder(id: Long, title: String, triggerAtMillis: Long, isAlarm: Boolean) {
        val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val data = Data.Builder()
            .putLong(MayaReminderWorker.KEY_REMINDER_ID, id)
            .putString(MayaReminderWorker.KEY_TITLE, title)
            .putBoolean(MayaReminderWorker.KEY_IS_ALARM, isAlarm)
            .build()

        val req = OneTimeWorkRequestBuilder<MayaReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("maya_reminder_$id")
            .build()
        WorkManager.getInstance(appContext).enqueue(req)
    }

    fun cancelReminder(id: Long) {
        WorkManager.getInstance(appContext).cancelAllWorkByTag("maya_reminder_$id")
    }
}
