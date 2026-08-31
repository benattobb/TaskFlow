package com.example.taskflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.taskflow.R
import com.example.taskflow.VoiceCaptureActivity

class VoiceTaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_voice)
            WidgetStyle.applySurfacePadding(views, R.id.widget_voice_root)
            val pending = PendingIntent.getActivity(context, id, Intent(context, VoiceCaptureActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_voice_root, pending)
            views.setOnClickPendingIntent(R.id.widget_voice_button, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
