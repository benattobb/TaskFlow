package com.example.taskflow.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LinkedTask(
    val taskId: String,
    val title: String,
    val calendarEventId: String? = null,
    val calendarUrl: String? = null,
    val calendarPending: Boolean = false,
    val completed: Boolean = false
)

/** Private, account-scoped history. Never stores OAuth tokens. */
object LinkedTaskStore {
    private fun preferences(context: Context) = context.getSharedPreferences("linked_tasks", Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context, account: String): List<LinkedTask> {
        val items = runCatching { JSONArray(preferences(context).getString(account, "[]")) }.getOrDefault(JSONArray())
        return (0 until items.length()).mapNotNull { index ->
            runCatching {
                val item = items.getJSONObject(index)
                LinkedTask(item.getString("taskId"), item.getString("title"),
                    item.optString("eventId").takeIf { it.isNotBlank() },
                    item.optString("eventUrl").takeIf { it.isNotBlank() },
                    item.optBoolean("pending"), item.optBoolean("completed"))
            }.getOrNull()
        }
    }

    @Synchronized
    fun save(context: Context, account: String, task: LinkedTask) {
        val items = listOf(task) + load(context, account).filterNot { it.taskId == task.taskId }
        val json = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().put("taskId", item.taskId).put("title", item.title)
                    .put("eventId", item.calendarEventId.orEmpty()).put("eventUrl", item.calendarUrl.orEmpty())
                    .put("pending", item.calendarPending).put("completed", item.completed))
            }
        }
        check(preferences(context).edit().putString(account, json.toString()).commit()) { "Could not save task history." }
    }

    fun createsCalendarEvents(context: Context): Boolean = preferences(context).getBoolean("create_calendar", true)

    fun setCreatesCalendarEvents(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("create_calendar", enabled).apply()
    }
}
