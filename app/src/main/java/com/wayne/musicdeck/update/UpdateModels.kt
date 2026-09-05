package com.wayne.musicdeck.update

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    @SerializedName("name")
    val name: String?,
    @SerializedName("body")
    val body: String?,
    @SerializedName("html_url")
    val htmlUrl: String,
    @SerializedName("prerelease")
    val prerelease: Boolean = false,
    @SerializedName("draft")
    val draft: Boolean = false,
    @SerializedName("published_at")
    val publishedAt: String?,
    @SerializedName("assets")
    val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,
    @SerializedName("content_type")
    val contentType: String?
)

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val apkSize: Long,
    val htmlUrl: String
)
