package com.example.taskflow.google

import android.content.Context
import com.example.taskflow.data.CapturedTask
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.security.MessageDigest

/** Creates the task and an owned event on the signed-in user's primary calendar. */
class GoogleTaskSync(private val context: Context) {
    suspend fun sync(task: CapturedTask): SyncResult = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext SyncResult.NotConnected
        val scope = "oauth2:https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/calendar.events"
        val token = GoogleAuthUtil.getToken(context, account, scope)
        // Google Tasks stores a due date but silently discards the time component.
        // Keep the time visible in its list UI and preserve an exact timed Calendar event.
        val visibleTime = task.dueTime?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        val recurrenceLabel = task.recurrence?.label
        val googleTaskTitle = buildString {
            append(task.title)
            visibleTime?.let { append(" · ").append(it) }
            recurrenceLabel?.let { append(" · ").append(it) }
        }
        // A previous run may have created the Google Task but failed on Calendar.
        // Reuse that TaskFlow entry when retrying instead of creating a duplicate.
        val taskId = findExistingTaskId(token, googleTaskTitle) ?: post(
            "https://tasks.googleapis.com/tasks/v1/lists/@default/tasks", token,
            JSONObject().apply {
                put("title", googleTaskTitle)
                val notes = listOfNotNull(
                    visibleTime?.let { "Scheduled time: $it" },
                    recurrenceLabel?.let { "Repeats: $it" },
                    "Created by TaskFlow."
                ).joinToString("\n")
                put("notes", notes)
                task.dueDate?.let { put("due", it.atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }
            }
        ).getString("id")
        val eventId = task.dueDate?.let { date ->
            val calendarEventId = calendarEventId(taskId)
            try {
                post("https://www.googleapis.com/calendar/v3/calendars/primary/events", token, JSONObject().apply {
                    // A deterministic ID makes retrying safe if the prior request succeeded but its response was lost.
                    put("id", calendarEventId)
                    put("summary", task.title)
                    put("description", "Created by TaskFlow. Google Task: $taskId")
                    put("extendedProperties", JSONObject().put("private", JSONObject().put("taskFlowGoogleTaskId", taskId)))
                    task.recurrence?.let { put("recurrence", JSONArray().put(it.calendarRule())) }
                    if (task.dueTime == null) {
                        put("start", JSONObject().put("date", date.toString()))
                        put("end", JSONObject().put("date", date.plusDays(1).toString()))
                    } else {
                        val zone = ZoneId.systemDefault()
                        val start = date.atTime(task.dueTime).atZone(zone)
                        put("start", JSONObject().put("dateTime", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).put("timeZone", zone.id))
                        put("end", JSONObject().put("dateTime", start.plusMinutes(30).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).put("timeZone", zone.id))
                    }
                }).getString("id")
            } catch (failure: GoogleApiException) {
                if (failure.statusCode == HttpURLConnection.HTTP_CONFLICT) calendarEventId else throw failure
            }
        }
        SyncResult.Success(taskId, eventId)
    }

    private fun post(url: String, token: String, body: JSONObject): JSONObject {
        val connection = openGoogleConnection(url, token)
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true
            val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw GoogleApiException("Google sync", connection.responseCode, errorDetail(response))
            JSONObject(response)
        } finally { connection.disconnect() }
    }

    private fun findExistingTaskId(token: String, title: String): String? {
        val connection = openGoogleConnection(
            "https://tasks.googleapis.com/tasks/v1/lists/@default/tasks?showCompleted=false&maxResults=100",
            token
        )
        return try {
            connection.requestMethod = "GET"
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw GoogleApiException("Google Task lookup", connection.responseCode, errorDetail(response))
            JSONObject(response).optJSONArray("items")
                ?.let { items ->
                    (0 until items.length())
                        .map { index -> items.getJSONObject(index) }
                        .firstOrNull { item ->
                            item.optString("title") == title && item.optString("notes").contains("Created by TaskFlow.")
                        }
                        ?.optString("id")
                }
        } finally { connection.disconnect() }
    }

    private fun openGoogleConnection(url: String, token: String): HttpURLConnection {
        val endpoint = URL(url)
        require(endpoint.protocol == "https" && endpoint.host in allowedGoogleHosts) { "Unexpected Google API endpoint." }
        return (endpoint.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun errorDetail(response: String): String {
        val message = runCatching { JSONObject(response).getJSONObject("error").optString("message") }
            .getOrDefault(response)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(400)
        return message.ifBlank { "No details returned." }
    }

    private fun calendarEventId(taskId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(taskId.toByteArray(StandardCharsets.UTF_8))
        // Calendar event IDs are base32hex: lowercase a-v and digits 0-9 only.
        // "taskflow" contains a `w`, so use a valid deterministic prefix instead.
        return "taskflov" + hash.take(20).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        val allowedGoogleHosts = setOf("tasks.googleapis.com", "www.googleapis.com")
    }
}

private class GoogleApiException(operation: String, val statusCode: Int, detail: String) :
    IllegalStateException("$operation failed ($statusCode): $detail")

sealed interface SyncResult {
    data object NotConnected : SyncResult
    data class Success(val googleTaskId: String, val calendarEventId: String?) : SyncResult
}
