package com.example.taskflow

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads only the official release asset and checks it before passing it to Android's installer. */
object UpdateInstaller {
    private const val maxApkBytes = 150L * 1024 * 1024
    private val allowedHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    suspend fun downloadAndVerify(
        context: Context,
        update: AppUpdate,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(update.apkUrl.startsWith("https://github.com/benattobb/TaskFlow/releases/download/")) {
            "This update is not from the official TaskFlow release."
        }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(directory, "TaskFlow-${update.versionName}.apk")
        apk.delete()
        var connection: HttpURLConnection? = null
        try {
            var url = URL(update.apkUrl)
            repeat(6) {
                require(url.protocol == "https" && url.host in allowedHosts) { "The download redirected to an untrusted host." }
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "TaskFlow-Android")
                }
                val response = connection!!.responseCode
                if (response in 300..399) {
                    val location = connection!!.getHeaderField("Location") ?: error("The update download could not be followed.")
                    connection!!.disconnect()
                    connection = null
                    url = URL(url, location)
                } else {
                    require(response in 200..299) { "GitHub could not provide this update (HTTP $response)." }
                    val length = connection!!.contentLengthLong
                    require(length in 1..maxApkBytes) { "The update file size is invalid." }
                    var total = 0L
                    var lastReported = -1
                    val digest = MessageDigest.getInstance("SHA-256")
                    connection!!.inputStream.use { input ->
                        apk.outputStream().buffered().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= maxApkBytes) { "The update file is too large." }
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                val percent = ((total * 100) / length).toInt().coerceIn(0, 100)
                                if (percent != lastReported) {
                                    lastReported = percent
                                    withContext(Dispatchers.Main.immediate) { onProgress(percent) }
                                }
                            }
                        }
                    }
                    require(total == length) { "The update download was incomplete." }
                    update.sha256?.let { expected ->
                        require(digest.digest().joinToString("") { byte -> "%02x".format(byte) }.equals(expected, true)) {
                            "The downloaded update failed its SHA-256 check."
                        }
                    }
                    verifyPackage(context, apk, update.versionName)
                    return@withContext apk
                }
            }
            error("Too many redirects while downloading the update.")
        } catch (error: Exception) {
            apk.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, apk: File, releaseVersion: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("The downloaded file is not a valid Android app.")
        require(candidate.packageName == context.packageName) { "This release is for a different TaskFlow edition." }
        require(versionCode(candidate) > versionCode(installedPackageInfo(context, flags))) {
            "This update is not newer than the installed version."
        }
        val expectedVersion = releaseVersion.split('.').map { it.toIntOrNull() ?: 0 }
        val apkVersion = candidate.versionName.orEmpty().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        require(apkVersion == expectedVersion) { "The release version and APK version do not match." }
        val installed = installedPackageInfo(context, flags)
        val installedSigners = signerDigests(installed)
        val candidateSigners = signerDigests(candidate)
        require(installedSigners.isNotEmpty() && installedSigners.any { it in candidateSigners }) {
            "The update signature does not match the installed TaskFlow app."
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(context: Context, flags: Int): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, flags)

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun uriFor(context: Context, apk: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
}
