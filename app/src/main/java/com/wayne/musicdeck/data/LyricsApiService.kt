package com.wayne.musicdeck.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Service for fetching synced lyrics from LRCLIB (lrclib.net)
 * Free API, no authentication required
 */
class LyricsApiService {
    
    companion object {
        private const val TAG = "LyricsApiService"
        private const val BASE_URL = "https://lrclib.net/api/"
        private const val USER_AGENT = "MusicDeck/2.5.0 (https://github.com/WayneChibeu/MusicDeck)"
        private const val MAX_RETRIES = 2
    }
    
    private val api: LrclibApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
            
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        api = retrofit.create(LrclibApi::class.java)
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
        
        Log.d(TAG, "Fetching lyrics for: '$cleanTrack' by '$cleanArtist'")
        
        var lastError: Exception? = null
        
        for (attempt in 0..MAX_RETRIES) {
            try {
                // 1. Try exact match (GET /get)
                val exactResponse = api.getLyrics(cleanTrack, cleanArtist, albumName, durationSeconds)
                if (exactResponse.isSuccessful && exactResponse.body() != null) {
                    val body = exactResponse.body()!!
                    val synced = body.syncedLyrics
                    val plain = body.plainLyrics
                    
                    if (!synced.isNullOrBlank()) {
                        return@withContext LyricsResult.Success(synced, isSynced = true)
                    } else if (!plain.isNullOrBlank()) {
                        return@withContext LyricsResult.Success(plain, isSynced = false)
                    }
                }
                
                // 2. Fallback to search (GET /search)
                // Combine track and artist for better search results
                val query = "$cleanTrack $cleanArtist"
                val searchResponse = api.searchLyrics(query)
                
                if (searchResponse.isSuccessful && searchResponse.body() != null) {
                    val results = searchResponse.body()!!
                    
                    // Filter and find best match
                    // First priority: Synced lyrics
                    val bestSynced = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                    if (bestSynced != null) {
                        return@withContext LyricsResult.Success(bestSynced.syncedLyrics!!, isSynced = true)
                    }
                    
                    // Second priority: Plain lyrics from first result
                    val firstPlain = results.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                    if (firstPlain != null) {
                         return@withContext LyricsResult.Success(firstPlain.plainLyrics!!, isSynced = false)
                    }
                }
                
                // If we got here, we found nothing on this attempt, but no exception occurred.
                // It's a "Not Found", not a network error, so strictly speaking we don't need to retry unless we think the API is flaky.
                // But let's assume if both failed, it's just not there.
                return@withContext LyricsResult.NotFound
                
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(500L * (attempt + 1))
                }
            }
        }
        
        Log.e(TAG, "All attempts failed", lastError)
        return@withContext LyricsResult.Error(lastError?.message ?: "Unknown network error")
    }
    
    /**
     * Clean up messy metadata for better search results.
     */
    private fun cleanupMetadata(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()
        
        // Remove file extension if present (common in raw filenames)
        if (title.endsWith(".mp3", ignoreCase = true) || title.endsWith(".flac", ignoreCase = true)) {
            title = title.substringBeforeLast(".")
        }
        
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
        
        // Remove common garbage
        title = title.replace(Regex("\\(Official Video\\)", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("\\(Official Audio\\)", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("\\(Lyrics\\)", RegexOption.IGNORE_CASE), "")
        
        // Clean up any double spaces
        title = title.replace(Regex("\\s+"), " ").trim()
        artist = artist.replace(Regex("\\s+"), " ").trim()
        
        return Pair(title, artist)
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
