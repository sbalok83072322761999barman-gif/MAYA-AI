package com.example.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.services.MayaAssistantService

class MayaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.maya_widget_layout)
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val openVoice = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).putExtra("start_route", "voice"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_voice_btn, openVoice)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Android 14–16 restricts starting microphone foreground services directly from BOOT_COMPLETED.
            // Post a standby notification so the user can launch Maya Voice with one tap.
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel("maya_standby", "Maya Standby", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val openIntent = PendingIntent.getActivity(
                context, 200,
                Intent(context, MainActivity::class.java).putExtra("start_route", "voice"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, "maya_standby")
                .setSmallIcon(R.drawable.ic_maya_tile)
                .setContentTitle("Maya AI Ready")
                .setContentText("Tap to activate Voice / Standby mode (Android 16 privacy compliant).")
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
            nm.notify(4010, notif)
        }
    }
}

class MayaNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MayaAssistantService.ACTION_STOP) {
            context.startService(Intent(context, MayaAssistantService::class.java).setAction(MayaAssistantService.ACTION_STOP))
        }
    }
}
