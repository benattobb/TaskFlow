package com.example.taskflow

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String,
    val sha256: String?
)

/**
 * A small, cached check against TaskFlow's public GitHub releases.
 * No device identifier, account, or analytics data is sent to GitHub.
 */
object UpdateChecker {
    private const val preferencesName = "taskflow_update_check"
    private const val lastCheckKey = "last_check_at"
    private const val cachedVersionKey = "latest_version"
    private const val cachedReleaseKey = "latest_release_url"
    private const val cachedApkKey = "latest_apk_url"
    private const val cachedDigestKey = "latest_apk_sha256"
    private const val checkIntervalMillis = 24L * 60 * 60 * 1000
    private const val latestReleaseUrl = "https://api.github.com/repos/benattobb/TaskFlow/releases/latest"

    suspend fun findAvailable(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        // GitHub sideloads use the original package identity; Play builds must use Play's updater.
        if (context.packageName != "com.example.taskflow") return@withContext null
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - preferences.getLong(lastCheckKey, 0) < checkIntervalMillis) {
            val version = preferences.getString(cachedVersionKey, null).orEmpty()
            val page = preferences.getString(cachedReleaseKey, null).orEmpty()
            val apk = preferences.getString(cachedApkKey, null).orEmpty()
            val digest = preferences.getString(cachedDigestKey, null)
            return@withContext version.takeIf {
                it.isNotBlank() && page.startsWith("https://github.com/benattobb/TaskFlow/releases/") &&
                    isReleaseApk(apk) &&
                    isNewer(it, installedVersion(context))
            }?.let { AppUpdate(it, page, apk, digest) }
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
                val apkAsset = selectApkAsset(release.optJSONArray("assets"), context.packageName)
                val apk = apkAsset?.optString("browser_download_url").orEmpty()
                val digest = apkAsset?.optString("digest")?.removePrefix("sha256:")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                if (version.isNotBlank() && page.startsWith("https://github.com/benattobb/TaskFlow/releases/") &&
                    isReleaseApk(apk) && isNewer(version, installedVersion(context))) {
                    preferences.edit().putString(cachedVersionKey, version).putString(cachedReleaseKey, page)
                        .putString(cachedApkKey, apk).putString(cachedDigestKey, digest).apply()
                    AppUpdate(version, page, apk, digest)
                } else {
                    preferences.edit().remove(cachedVersionKey).remove(cachedReleaseKey).remove(cachedApkKey).remove(cachedDigestKey).apply()
                    null
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun selectApkAsset(assets: org.json.JSONArray?, packageName: String): JSONObject? {
        val candidates = (0 until (assets?.length() ?: 0)).mapNotNull { index -> assets?.optJSONObject(index) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val wantedMarkers = when (packageName) {
            "com.example.taskflow" -> listOf("legacy", "github")
            "com.benattobb.taskflow" -> listOf("play", "modern")
            else -> return null
        }
        val named = candidates.filter { asset -> wantedMarkers.any { marker -> asset.optString("name").lowercase().contains(marker) } }
        return when {
            named.size == 1 -> named.single()
            candidates.size == 1 -> candidates.single()
            else -> null
        }
    }

    private fun isReleaseApk(value: String): Boolean =
        value.startsWith("https://github.com/benattobb/TaskFlow/releases/download/") && value.endsWith(".apk", ignoreCase = true)

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
