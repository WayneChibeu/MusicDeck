package com.wayne.musicdeck.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.wayne.musicdeck.R
import com.wayne.musicdeck.utils.NetworkUtils
import com.wayne.musicdeck.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class UpdateManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_OWNER = "WayneChibeu"
        private const val GITHUB_REPO = "MusicDeck"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val RAW_UPDATE_URL =
            "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/app_update.json"
        private const val WEB_LATEST_RELEASE_URL =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 Hours
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Check if enough time has elapsed to perform a silent auto-check.
     */
    fun shouldAutoCheck(): Boolean {
        if (!settingsManager.isAutoUpdateEnabled) return false
        if (!NetworkUtils.isOnline(context)) return false
        val lastCheck = settingsManager.lastUpdateCheckTime
        val now = System.currentTimeMillis()
        return (now - lastCheck) > AUTO_CHECK_INTERVAL_MS
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "2.10.0"
        } catch (e: Exception) {
            "2.10.0"
        }
    }

    /**
     * Query latest release with multi-tier fallback:
     * 1. Fastly Raw CDN (app_update.json) - Zero rate limits, instantaneous.
     * 2. GitHub Releases REST API (api.github.com) - Full asset metadata.
     * 3. Web release 302 redirect header - Rate-limit-free HTTP redirect inspection.
     */
    suspend fun checkForUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val currentVer = getCurrentVersionName()

        // Tier 1: Try Fastly Raw CDN metadata (rate-limit free)
        try {
            val rawRequest = Request.Builder()
                .url(RAW_UPDATE_URL)
                .header("User-Agent", "MusicDeck-App/$currentVer")
                .header("Cache-Control", "no-cache")
                .build()

            val rawResponse = httpClient.newCall(rawRequest).execute()
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body?.string()
                if (!rawBody.isNullOrBlank()) {
                    val metadata = gson.fromJson(rawBody, AppUpdateMetadata::class.java)
                    val latestVer = metadata.versionName.removePrefix("v").trim()
                    val isNewer = compareVersions(latestVer, currentVer) > 0
                    settingsManager.lastUpdateCheckTime = System.currentTimeMillis()

                    val downloadUrl = metadata.downloadUrl
                        ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v$latestVer/MusicDeck-v$latestVer.apk"
                    val htmlUrl = metadata.htmlUrl
                        ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/v$latestVer"

                    return@withContext Result.success(
                        UpdateInfo(
                            isUpdateAvailable = isNewer,
                            currentVersion = currentVer,
                            latestVersion = metadata.versionName,
                            releaseTitle = metadata.releaseTitle ?: "MusicDeck v$latestVer",
                            releaseNotes = metadata.releaseNotes ?: "Bug fixes and performance improvements.",
                            downloadUrl = downloadUrl,
                            apkSize = metadata.apkSize,
                            htmlUrl = htmlUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "RAW CDN update check failed, falling back to GitHub API", e)
        }

        // Tier 2: Try GitHub Releases REST API
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("User-Agent", "MusicDeck-App/$currentVer")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrBlank()) {
                    val release = gson.fromJson(responseBody, GitHubRelease::class.java)
                    settingsManager.lastUpdateCheckTime = System.currentTimeMillis()

                    val latestVer = release.tagName.removePrefix("v").trim()
                    val isNewer = compareVersions(latestVer, currentVer) > 0
                    val apkAsset = selectBestApkAsset(release.assets ?: emptyList())

                    return@withContext Result.success(
                        UpdateInfo(
                            isUpdateAvailable = isNewer,
                            currentVersion = currentVer,
                            latestVersion = release.tagName,
                            releaseTitle = release.name ?: release.tagName,
                            releaseNotes = release.body ?: "Bug fixes and performance improvements.",
                            downloadUrl = apkAsset?.browserDownloadUrl,
                            apkSize = apkAsset?.size ?: 0L,
                            htmlUrl = release.htmlUrl
                        )
                    )
                }
            } else if (response.code != 403) {
                Log.w(TAG, "GitHub API returned status ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub API update check failed, falling back to Web redirect", e)
        }

        // Tier 3: Query GitHub web release redirect (bypasses REST API 403 rate limits)
        try {
            val noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val webRequest = Request.Builder()
                .url(WEB_LATEST_RELEASE_URL)
                .head()
                .header("User-Agent", "MusicDeck-App/$currentVer")
                .build()

            val webResponse = noRedirectClient.newCall(webRequest).execute()
            val location = webResponse.header("Location")
            if (!location.isNullOrBlank() && location.contains("/tag/")) {
                val tag = location.substringAfterLast("/tag/").trim()
                val latestVer = tag.removePrefix("v").trim()
                val isNewer = compareVersions(latestVer, currentVer) > 0
                settingsManager.lastUpdateCheckTime = System.currentTimeMillis()

                return@withContext Result.success(
                    UpdateInfo(
                        isUpdateAvailable = isNewer,
                        currentVersion = currentVer,
                        latestVersion = tag,
                        releaseTitle = "MusicDeck $tag",
                        releaseNotes = "A new update ($tag) is available on GitHub.",
                        downloadUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$tag/MusicDeck-$tag.apk",
                        apkSize = 0L,
                        htmlUrl = location
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Web redirect fallback failed", e)
        }

        Result.failure(Exception("Unable to check for updates. Please check your internet connection."))
    }

    /**
     * Selects the most appropriate APK asset based on device ABI or universal build.
     */
    private fun selectBestApkAsset(assets: List<GitHubAsset>): GitHubAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val supportedAbis = Build.SUPPORTED_ABIS
        for (abi in supportedAbis) {
            val match = apkAssets.find { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }

        // Fallback to universal or first apk
        return apkAssets.find { it.name.contains("universal", ignoreCase = true) } ?: apkAssets.first()
    }

    /**
     * Compare two version strings (e.g. "2.10.0" vs "2.9.13").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val cleanV1 = v1.removePrefix("v").split("-")[0].trim()
        val cleanV2 = v2.removePrefix("v").split("-")[0].trim()

        val parts1 = cleanV1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = cleanV2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    /**
     * Download the APK file from GitHub Releases directly to app external files dir.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            
            // Clean older APKs
            updateDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") && file.name != targetFileName) {
                    file.delete()
                }
            }

            val apkFile = File(updateDir, targetFileName)
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else -1
                        withContext(Dispatchers.Main) {
                            onProgress(percent, totalRead, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            Result.failure(e)
        }
    }

    /**
     * Launch Android system package installer for the downloaded APK using FileProvider.
     */
    fun installApk(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        activity,
                        "Please allow MusicDeck to install updates",
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                    return
                }
            }

            val authority = "${activity.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(activity, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(activity, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shows a polished Material Dialog for the update with in-app download and installation.
     */
    fun showUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_available, null)
        val tvVersion = dialogView.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvNotes = dialogView.findViewById<TextView>(R.id.tvUpdateNotes)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressUpdateDownload)
        val tvProgressPercent = dialogView.findViewById<TextView>(R.id.tvUpdateProgressPercent)

        tvVersion.text = updateInfo.releaseTitle
        tvNotes.text = updateInfo.releaseNotes

        var downloadJob: Job? = null
        var downloadedApkFile: File? = null

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Update Now", null) // Custom listener below to prevent auto-dismiss
            .setNegativeButton("Later") { d, _ ->
                downloadJob?.cancel()
                d.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            val updateBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            updateBtn.setOnClickListener {
                if (downloadedApkFile != null && downloadedApkFile!!.exists()) {
                    installApk(activity, downloadedApkFile!!)
                    return@setOnClickListener
                }

                val downloadUrl = updateInfo.downloadUrl
                if (downloadUrl.isNullOrEmpty()) {
                    // Fallback to browser if no direct APK asset
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl))
                    activity.startActivity(browserIntent)
                    dialog.dismiss()
                    return@setOnClickListener
                }

                // Start In-App Download
                updateBtn.isEnabled = false
                updateBtn.text = "Downloading..."
                progressBar.visibility = View.VISIBLE
                tvProgressPercent.visibility = View.VISIBLE
                progressBar.isIndeterminate = false
                progressBar.progress = 0

                val fileName = "MusicDeck-${updateInfo.latestVersion}.apk"
                downloadJob = CoroutineScope(Dispatchers.Main).launch {
                    val result = downloadApk(downloadUrl, fileName) { percent, read, _ ->
                        if (percent >= 0) {
                            progressBar.progress = percent
                            tvProgressPercent.text = "$percent%"
                            updateBtn.text = "Downloading ($percent%)"
                        } else {
                            val mbRead = read / (1024 * 1024)
                            tvProgressPercent.text = "${mbRead}MB"
                        }
                    }

                    result.onSuccess { file ->
                        downloadedApkFile = file
                        progressBar.progress = 100
                        tvProgressPercent.text = "Download complete!"
                        updateBtn.isEnabled = true
                        updateBtn.text = "Install Now"
                        installApk(activity, file)
                    }.onFailure { err ->
                        progressBar.visibility = View.GONE
                        tvProgressPercent.visibility = View.GONE
                        updateBtn.isEnabled = true
                        updateBtn.text = "Retry"
                        Toast.makeText(activity, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }
}
