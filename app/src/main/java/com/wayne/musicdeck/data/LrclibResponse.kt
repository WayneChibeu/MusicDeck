package com.wayne.musicdeck.data

import com.google.gson.annotations.SerializedName

data class LrclibResponse(
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?
)
