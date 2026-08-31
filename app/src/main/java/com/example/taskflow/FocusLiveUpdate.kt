package com.example.taskflow

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * An ongoing, user-started focus card. Recent Pixel/Android versions may promote
 * this on supported system surfaces; older versions show it as a normal notification.
 */
object FocusLiveUpdate {
    private const val channelId = "focus_live_update"
    private const val notificationId = 70

    fun show(context: Context, tasks: List<FocusTask>) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val activeTask = tasks.firstOrNull { !it.isSuspended }
        if (activeTask == null) {
            stop(context)
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Active task", NotificationManager.IMPORTANCE_DEFAULT))
        val suspend = PendingIntent.getBroadcast(
            context,
            activeTask.id.hashCode(),
            Intent(context, FocusActionReceiver::class.java).apply {
                action = FocusActionReceiver.actionSuspend
                putExtra(FocusActionReceiver.extraTaskId, activeTask.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(activeTask.title)
            .setContentText("Focus in progress")
            .setSubText("Focus")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Suspend", suspend)
        if (Build.VERSION.SDK_INT >= 37) {
            builder.setStyle(
                NotificationCompat.MetricStyle()
                    .addMetric(
                        NotificationCompat.Metric(
                            NotificationCompat.Metric.FixedText("NOW"),
                            "Focus",
                            NotificationCompat.SEMANTIC_STYLE_SAFE
                        )
                    )
                    .addMetric(
                        NotificationCompat.Metric(
                            NotificationCompat.Metric.FixedInt(tasks.count { !it.isSuspended }),
                            "Active",
                            NotificationCompat.SEMANTIC_STYLE_INFO
                        )
                    )
                    .setCriticalMetric(0)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(activeTask.title))
        }
        val notification = builder.build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun stop(context: Context) = NotificationManagerCompat.from(context).cancel(notificationId)
}
