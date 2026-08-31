package com.example.taskflow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FocusTask(
    val id: String,
    val title: String,
    val isSuspended: Boolean,
    val createdAt: Long
)

/** Keeps the user's focus list on-device so it survives closing the app. */
object FocusTaskStore {
    private const val preferencesName = "taskflow_focus"
    private const val tasksKey = "tasks"

    fun load(context: Context): List<FocusTask> = runCatching {
        val raw = preferences(context).getString(tasksKey, "[]") ?: "[]"
        val items = JSONArray(raw)
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    FocusTask(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        isSuspended = item.optBoolean("isSuspended"),
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, title: String): List<FocusTask> = save(
        context,
        listOf(FocusTask(UUID.randomUUID().toString(), title, false, System.currentTimeMillis())) + load(context)
    )

    fun setSuspended(context: Context, id: String, suspended: Boolean): List<FocusTask> = save(
        context,
        load(context).map { if (it.id == id) it.copy(isSuspended = suspended) else it }
    )

    fun remove(context: Context, id: String): List<FocusTask> = save(
        context,
        load(context).filterNot { it.id == id }
    )

    private fun save(context: Context, tasks: List<FocusTask>): List<FocusTask> {
        val json = JSONArray()
        tasks.forEach { task ->
            json.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("isSuspended", task.isSuspended)
                    put("createdAt", task.createdAt)
                }
            )
        }
        preferences(context).edit().putString(tasksKey, json.toString()).apply()
        return tasks
    }

    private fun preferences(context: Context) = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
}
