package com.wayne.musicdeck.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.wayne.musicdeck.data.GitHubApiService
import com.wayne.musicdeck.data.model.GitHubAsset
import com.wayne.musicdeck.data.model.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val GITHUB_BASE_URL = "https://api.github.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }

    sealed class CheckResult {
        data class NewUpdate(val release: GitHubRelease, val apkAsset: GitHubAsset) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    /**
     * Checks GitHub for a newer release than the current app version.
     */
    suspend fun checkForUpdates(currentVersionName: String): CheckResult = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLatestRelease()
            if (!response.isSuccessful || response.body() == null) {
                return@withContext CheckResult.Error("Failed to fetch release info (code ${response.code()})")
            }

            val release = response.body()!!
            val remoteTag = release.tagName.trim()
            
            // Find APK asset
            val apkAsset = release.assets?.firstOrNull { 
                it.name.endsWith(".apk", ignoreCase = true) 
            }

            if (isNewerVersion(remoteTag, currentVersionName)) {
                if (apkAsset != null) {
                    CheckResult.NewUpdate(release, apkAsset)
                } else {
                    CheckResult.Error("New update found (${release.tagName}), but no APK asset is attached.")
                }
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            CheckResult.Error(e.localizedMessage ?: "Unknown network error")
        }
    }

    /**
     * Compares version strings (e.g., "v2.7.2" vs "2.7.1").
     * Returns true if remoteVersion is strictly newer than currentVersion.
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Downloads the APK file from GitHub assets with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        asset: GitHubAsset,
        onProgress: (percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = context.externalCacheDir ?: context.cacheDir
            val targetFile = File(downloadDir, "MusicDeck-v${System.currentTimeMillis()}.apk")
            
            // Clean up any older downloaded APKs in the directory
            downloadDir.listFiles { _, name -> name.startsWith("MusicDeck-v") && name.endsWith(".apk") }
                ?.forEach { it.delete() }

            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("User-Agent", "MusicDeck-Android-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed with HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download response body"))
            val totalBytes = if (asset.size > 0) asset.size else body.contentLength()

            body.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied: Long = 0
                    var read: Int
                    var lastReportedPercent = -1

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read

                        if (totalBytes > 0) {
                            val percent = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                withContext(Dispatchers.Main) {
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                    outputStream.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update APK", e)
            Result.failure(e)
        }
    }

    /**
     * Triggers Android package installer for the downloaded APK.
     */
    fun installApk(activity: Activity, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "Cannot install: APK file does not exist")
                return
            }

            // Check if we need permission to install unknown apps on Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(permissionIntent)
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
        }
    }
}
