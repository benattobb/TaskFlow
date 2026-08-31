package com.example.taskflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.taskflow.MainActivity
import com.example.taskflow.R

class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            manager.updateAppWidget(component, views(context))
        }

        private fun views(context: Context) = RemoteViews(context.packageName, R.layout.task_widget).apply {
            WidgetStyle.applySurfacePadding(this, R.id.widget_capture_root)
            setTextViewText(R.id.widget_next_task, "Tap to add or speak a task")
            val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_next_task, pending)
            val voicePending = PendingIntent.getActivity(context, 1, Intent(context, com.example.taskflow.VoiceCaptureActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_voice_button, voicePending)
        }
    }
}
