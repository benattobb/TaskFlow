package com.example.taskflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.taskflow.FocusTaskStore
import com.example.taskflow.MainActivity
import com.example.taskflow.R

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, views(context, id))
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, FocusWidgetProvider::class.java)
            manager.updateAppWidget(component, views(context, 0))
        }

        private fun views(context: Context, id: Int): RemoteViews {
            val active = FocusTaskStore.load(context).firstOrNull { !it.isSuspended }
            val requestCode = if (id == 0) 4 else id
            return RemoteViews(context.packageName, R.layout.widget_focus).apply {
                WidgetStyle.applySurfacePadding(this, R.id.widget_focus_root)
                setTextViewText(R.id.widget_focus_text, active?.title ?: "No active focus")
                val pending = PendingIntent.getActivity(context, requestCode, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_focus_root, pending)
                setOnClickPendingIntent(R.id.widget_focus_text, pending)
            }
        }
    }
}
