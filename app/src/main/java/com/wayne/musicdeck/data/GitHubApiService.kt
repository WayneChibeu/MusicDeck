package com.wayne.musicdeck.data

import com.wayne.musicdeck.data.model.GitHubRelease
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

interface GitHubApiService {
    @Headers(
        "Accept: application/vnd.github.v3+json",
        "User-Agent: MusicDeck-Android-App"
    )
    @GET("repos/WayneChibeu/MusicDeck/releases/latest")
    suspend fun getLatestRelease(): Response<GitHubRelease>
}
