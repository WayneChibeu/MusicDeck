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
        private const val USER_AGENT = "MusicDeck/2.5.0 (https://github.com/WayneChibeu/MusicDeck)"
        private const val MAX_RETRIES = 3
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
        // Clean up messy metadata (e.g., "Artist - Song (feat. X)" with unknown artist)
        val (cleanTrack, cleanArtist) = cleanupMetadata(trackName, artistName)
        
        Log.d(TAG, "Original: track='$trackName', artist='$artistName'")
        Log.d(TAG, "Cleaned: track='$cleanTrack', artist='$cleanArtist'")
        
        // Retry logic for network errors
        var lastError: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                // First try the GET endpoint for exact match (faster)
                val exactResult = tryExactMatch(cleanTrack, cleanArtist, albumName, durationSeconds)
                if (exactResult is LyricsResult.Success) {
                    return@withContext exactResult
                }
                
                // Fallback to search endpoint
                val searchResult = trySearch(cleanTrack, cleanArtist)
                if (searchResult !is LyricsResult.Error) {
                    return@withContext searchResult
                }
                
                // If search returned an error, save it and retry
                lastError = Exception((searchResult as LyricsResult.Error).message)
                
            } catch (e: Exception) {
                Log.w(TAG, "Attempt $attempt failed: ${e.message}")
                lastError = e
                
                // Wait a bit before retry (exponential backoff)
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep((attempt * 500).toLong())
                    } catch (_: InterruptedException) {}
                }
            }
        }
        
        // All retries failed
        Log.e(TAG, "All $MAX_RETRIES attempts failed", lastError)
        val errorMsg = when {
            lastError?.message?.contains("end of stream", ignoreCase = true) == true -> 
                "Network error - please check your connection"
            lastError?.message?.contains("timeout", ignoreCase = true) == true -> 
                "Connection timed out - try again"
            else -> lastError?.message ?: "Network error"
        }
        LyricsResult.Error(errorMsg)
    }
    
    /**
     * Clean up messy metadata for better search results.
     * Handles cases like:
     * - Title: "Alan Walker - Darkside (feat Au-Ra)" with Artist: "<unknown>"
     * - Extracts actual track name and artist from the title
     */
    private fun cleanupMetadata(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()
        
        // Check if artist is unknown/empty
        val isArtistUnknown = artist.isBlank() || 
            artist.equals("<unknown>", ignoreCase = true) ||
            artist.equals("unknown", ignoreCase = true) ||
            artist.equals("unknown artist", ignoreCase = true)
        
        // Try to extract artist from title if format is "Artist - Song"
        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2) {
                val potentialArtist = parts[0].trim()
                val potentialTitle = parts[1].trim()
                
                // If artist is unknown, use the extracted artist
                if (isArtistUnknown && potentialArtist.isNotBlank()) {
                    artist = potentialArtist
                    title = potentialTitle
                }
            }
        }
        
        // Remove featuring info from title for cleaner search
        // Matches: (feat. X), (feat X), (ft. X), (ft X), [feat. X], etc.
        title = title.replace(Regex("\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?)\\s*.+[\\)\\]]", RegexOption.IGNORE_CASE), "")
        
        // Also remove featuring at the end without parentheses
        title = title.replace(Regex("\\s+(feat\\.?|ft\\.?)\\s+.+$", RegexOption.IGNORE_CASE), "")
        
        // Clean up any double spaces
        title = title.replace(Regex("\\s+"), " ").trim()
        artist = artist.replace(Regex("\\s+"), " ").trim()
        
        return Pair(title, artist)
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
                
                // Validate JSON before parsing
                if (!response.trimStart().startsWith("{")) {
                    Log.w(TAG, "Exact match returned non-JSON response")
                    connection.disconnect()
                    return LyricsResult.NotFound
                }
                
                val json = try {
                    JSONObject(response)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse exact match JSON", e)
                    connection.disconnect()
                    return LyricsResult.NotFound
                }
                
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
                
                // Validate JSON array before parsing
                if (!response.trimStart().startsWith("[")) {
                    Log.w(TAG, "Search returned non-JSON response")
                    connection.disconnect()
                    return LyricsResult.NotFound
                }
                
                val results = try {
                    JSONArray(response)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse search JSON", e)
                    connection.disconnect()
                    return LyricsResult.NotFound
                }
                
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
