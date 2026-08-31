package com.example.taskflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.taskflow.QuickTypeActivity
import com.example.taskflow.R

/** Compact 2x1 launcher affordance for typing a task without opening the dashboard. */
class QuickTypeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
    }

    private fun views(context: Context) = RemoteViews(context.packageName, R.layout.widget_type).apply {
        WidgetStyle.applySurfacePadding(this, R.id.widget_type_root)
        val openCapture = PendingIntent.getActivity(
            context,
            2,
            Intent(context, QuickTypeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_type_root, openCapture)
    }
}
