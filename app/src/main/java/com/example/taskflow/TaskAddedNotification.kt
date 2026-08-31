package com.example.taskflow

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.taskflow.data.CapturedTask

/** Confirmation for a hands-free capture, with an optional path back to editing. */
object TaskAddedNotification {
    private const val channelId = "voice_capture_complete"
    private const val notificationId = 71

    fun showAdded(context: Context, task: CapturedTask) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val edit = PendingIntent.getActivity(
            context,
            task.sourceText.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(VoiceCaptureActivity.extraCapturedText, task.sourceText)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("Task added")
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.title}\nAdded to Google Tasks${if (task.dueDate != null) " and Calendar" else ""}."))
            .setAutoCancel(true)
            .setContentIntent(edit)
            .addAction(android.R.drawable.ic_menu_edit, "Edit", edit)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showNeedsSetup(context: Context, task: CapturedTask) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val edit = PendingIntent.getActivity(
            context,
            task.sourceText.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(VoiceCaptureActivity.extraCapturedText, task.sourceText)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Connect Google to add this task")
            .setContentText(task.title)
            .setAutoCancel(true)
            .setContentIntent(edit)
            .addAction(android.R.drawable.ic_menu_edit, "Open and connect", edit)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showFailed(context: Context, task: CapturedTask) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val edit = editIntent(context, task)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Task wasn't added")
            .setContentText(task.title)
            .setAutoCancel(true)
            .setContentIntent(edit)
            .addAction(android.R.drawable.ic_menu_edit, "Edit", edit)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Voice task confirmations", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun editIntent(context: Context, task: CapturedTask) = PendingIntent.getActivity(
        context,
        task.sourceText.hashCode(),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(VoiceCaptureActivity.extraCapturedText, task.sourceText)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
