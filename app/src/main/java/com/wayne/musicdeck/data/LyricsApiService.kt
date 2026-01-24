package com.wayne.musicdeck.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Service for fetching synced lyrics from LRCLIB (lrclib.net)
 * Free API, no authentication required
 */
class LyricsApiService {
    
    companion object {
        private const val TAG = "LyricsApiService"
        private const val BASE_URL = "https://lrclib.net/api"
        private const val USER_AGENT = "MusicDeck/2.4.6 (https://github.com/WayneChibeu/MusicDeck)"
    }
    
    /**
     * Search for synced lyrics by track name and artist
     * @return LRC format lyrics string, or null if not found
     */
    suspend fun fetchSyncedLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // First try the GET endpoint for exact match (faster)
            val exactResult = tryExactMatch(trackName, artistName, albumName, durationSeconds)
            if (exactResult is LyricsResult.Success) {
                return@withContext exactResult
            }
            
            // Fallback to search endpoint
            val searchResult = trySearch(trackName, artistName)
            return@withContext searchResult
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics", e)
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Try exact match using GET /api/get endpoint
     */
    private fun tryExactMatch(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSeconds: Int?
    ): LyricsResult {
        try {
            val params = buildString {
                append("track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
                append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
                if (!albumName.isNullOrBlank()) {
                    append("&album_name=").append(URLEncoder.encode(albumName, "UTF-8"))
                }
                if (durationSeconds != null && durationSeconds > 0) {
                    append("&duration=").append(durationSeconds)
                }
            }
            
            val url = URL("$BASE_URL/get?$params")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                // Prefer synced lyrics, fallback to plain
                val syncedLyrics = json.optString("syncedLyrics", "")
                val plainLyrics = json.optString("plainLyrics", "")
                
                return when {
                    syncedLyrics.isNotBlank() -> LyricsResult.Success(
                        syncedLyrics,
                        isSynced = true
                    )
                    plainLyrics.isNotBlank() -> LyricsResult.Success(
                        plainLyrics,
                        isSynced = false
                    )
                    else -> LyricsResult.NotFound
                }
            }
            
            connection.disconnect()
            return LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.d(TAG, "Exact match failed, will try search", e)
            return LyricsResult.NotFound
        }
    }
    
    /**
     * Search using GET /api/search endpoint
     */
    private fun trySearch(trackName: String, artistName: String): LyricsResult {
        try {
            // Clean up search query - combine track and artist
            val query = "$trackName $artistName"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            
            val url = URL("$BASE_URL/search?q=$encodedQuery")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONArray(response)
                
                if (results.length() > 0) {
                    // Find best match with synced lyrics
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val syncedLyrics = item.optString("syncedLyrics", "")
                        
                        if (syncedLyrics.isNotBlank()) {
                            return LyricsResult.Success(syncedLyrics, isSynced = true)
                        }
                    }
                    
                    // If no synced lyrics, try plain lyrics from first result
                    val firstResult = results.getJSONObject(0)
                    val plainLyrics = firstResult.optString("plainLyrics", "")
                    
                    if (plainLyrics.isNotBlank()) {
                        return LyricsResult.Success(plainLyrics, isSynced = false)
                    }
                }
            }
            
            connection.disconnect()
            return LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            return LyricsResult.Error(e.message ?: "Search failed")
        }
    }
}

/**
 * Result wrapper for lyrics fetching
 */
sealed class LyricsResult {
    data class Success(val lyrics: String, val isSynced: Boolean) : LyricsResult()
    object NotFound : LyricsResult()
    data class Error(val message: String) : LyricsResult()
}
