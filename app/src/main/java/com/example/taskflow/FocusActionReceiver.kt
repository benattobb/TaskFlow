package com.example.taskflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskflow.widget.FocusWidgetProvider

/** Handles the suspend action directly from the active-focus notification. */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != actionSuspend) return
        val taskId = intent.getStringExtra(extraTaskId) ?: return
        val tasks = FocusTaskStore.setSuspended(context, taskId, true)
        FocusLiveUpdate.show(context, tasks)
        FocusWidgetProvider.refresh(context)
    }

    companion object {
        const val actionSuspend = "com.example.taskflow.SUSPEND_FOCUS"
        const val extraTaskId = "focusTaskId"
    }
}
