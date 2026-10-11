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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Checks the public GitHub release feed without sending device or account data. */
class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
        val update = UpdateChecker.findAvailable(applicationContext) ?: return Result.success()
        val preferences = applicationContext.getSharedPreferences("taskflow_update_check", Context.MODE_PRIVATE)
        if (preferences.getString("notified_version", null) == update.versionName) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = NotificationManagerCompat.from(applicationContext)
        val channelId = "taskflow_updates"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifies you when a newer TaskFlow release is available."
                }
            )
        }
        val openTaskFlow = PendingIntent.getActivity(
            applicationContext, update.versionName.hashCode(),
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            2106,
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_update_notification)
                .setContentTitle("TaskFlow " + update.versionName + " is ready")
                .setContentText("Tap to review and install it in TaskFlow.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("A new TaskFlow release is available. Open TaskFlow to download and verify it, then Android will ask whether to install it."))
                .setContentIntent(openTaskFlow)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
        preferences.edit().putString("notified_version", update.versionName).apply()
        Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val periodicName = "taskflow_release_check"
        private const val firstCheckName = "taskflow_first_release_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                firstCheckName, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(constraints)
                    .setInitialDelay(2, TimeUnit.MINUTES).build()
            )
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                periodicName, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints).build()
            )
        }
    }
}
