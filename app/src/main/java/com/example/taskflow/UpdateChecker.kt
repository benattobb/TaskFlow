package com.example.taskflow

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val versionName: String, val releaseUrl: String)

/**
 * A small, cached check against TaskFlow's public GitHub releases.
 * No device identifier, account, or analytics data is sent to GitHub.
 */
object UpdateChecker {
    private const val preferencesName = "taskflow_update_check"
    private const val lastCheckKey = "last_check_at"
    private const val checkIntervalMillis = 24L * 60 * 60 * 1000
    private const val latestReleaseUrl = "https://api.github.com/repos/benattobb/TaskFlow/releases/latest"

    suspend fun findAvailable(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - preferences.getLong(lastCheckKey, 0) < checkIntervalMillis) {
            return@withContext null
        }
        runCatching {
            val endpoint = URL(latestReleaseUrl)
            require(endpoint.protocol == "https" && endpoint.host == "api.github.com") { "Unexpected update endpoint." }
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "TaskFlow-Android")
                if (connection.responseCode !in 200..299) return@runCatching null
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                preferences.edit().putLong(lastCheckKey, System.currentTimeMillis()).apply()
                val version = release.optString("tag_name").removePrefix("v")
                val page = release.optString("html_url")
                if (version.isNotBlank() && page.startsWith("https://github.com/benattobb/TaskFlow/releases/") && isNewer(version, installedVersion(context))) {
                    AppUpdate(version, page)
                } else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    internal fun isNewer(remote: String, installed: String): Boolean {
        val remoteParts = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val installedParts = installed.split('.').map { it.toIntOrNull() ?: 0 }
        val length = maxOf(remoteParts.size, installedParts.size)
        return (0 until length).firstOrNull { index ->
            (remoteParts.getOrElse(index) { 0 }) != (installedParts.getOrElse(index) { 0 })
        }?.let { index -> remoteParts.getOrElse(index) { 0 } > installedParts.getOrElse(index) { 0 } } ?: false
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
