package com.wayne.musicdeck.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

class LyricsRepository(private val context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)
    
    private val lyricsApiService = LyricsApiService()
    
    companion object {
        private const val TAG = "LyricsRepository"
    }
    
    /**
     * Save lyric file path for a specific song
     */
    fun saveLyricPath(filePath: String, lyricPath: String) {
        prefs.edit().putString(filePath, lyricPath).apply()
    }
    
    /**
     * Get lyric file path for a song
     */
    fun getLyricPath(filePath: String): String? {
        return prefs.getString(filePath, null)
    }
    
    /**
     * Remove lyric file for a song
     */
    fun removeLyricPath(filePath: String) {
        prefs.edit().remove(filePath).apply()
    }
    
    /**
     * Check if a song has lyrics (checks internal cache AND local storage side-by-side)
     */
    fun hasLyrics(filePath: String): Boolean {
        if (prefs.contains(filePath)) return true
        return findLocalLrcFile(filePath) != null
    }

    /**
     * Look for a .lrc file in the same directory as the song file.
     */
    fun findLocalLrcFile(songPath: String): String? {
        return try {
            val songFile = File(songPath)
            if (!songFile.exists()) return null
            
            val parent = songFile.parentFile ?: return null
            val songName = songFile.nameWithoutExtension
            
            // Check for EXACT name match (e.g., song.mp3 -> song.lrc)
            val lrcFile = File(parent, "$songName.lrc")
            if (lrcFile.exists()) return lrcFile.absolutePath
            
            // Case-insensitive check if exact fails
            val sibs = parent.listFiles() ?: return null
            sibs.find { 
                it.extension.equals("lrc", ignoreCase = true) && 
                it.nameWithoutExtension.equals(songName, ignoreCase = true) 
            }?.absolutePath
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Fetch lyrics from LRCLIB API and save locally
     * @return FetchResult indicating success/failure
     */
    suspend fun fetchAndSaveLyrics(
        songId: Long,
        trackName: String,
        artistName: String,
        filePath: String, // Use path for stabilization
        albumName: String? = null,
        durationMs: Long? = null
    ): FetchResult {
        Log.d(TAG, "Fetching lyrics for: $trackName by $artistName")
        
        val durationSeconds = durationMs?.let { (it / 1000).toInt() }
        
        return when (val result = lyricsApiService.fetchSyncedLyrics(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSeconds = durationSeconds
        )) {
            is LyricsResult.Success -> {
                try {
                    // Save lyrics to internal storage
                    val fileName = "lyrics_${songId}.lrc"
                    val file = File(context.filesDir, fileName)
                    
                    if (result.isSynced) {
                        // Already in LRC format, save directly
                        file.writeText(result.lyrics)
                    } else {
                        // Convert plain text to simple display format
                        // Plain lyrics don't have timestamps, but we can still display them
                        val formattedLyrics = result.lyrics.lines()
                            .filter { it.isNotBlank() }
                            .joinToString("\n") { "[00:00.00]$it" }
                        file.writeText(formattedLyrics)
                    }
                    
                    // Save path to prefs
                    saveLyricPath(filePath, file.absolutePath)
                    
                    Log.d(TAG, "Lyrics saved successfully (synced: ${result.isSynced})")
                    FetchResult.Success(isSynced = result.isSynced)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save lyrics file", e)
                    FetchResult.Error("Failed to save: ${e.message}")
                }
            }
            is LyricsResult.NotFound -> {
                Log.d(TAG, "Lyrics not found")
                FetchResult.NotFound
            }
            is LyricsResult.Error -> {
                Log.e(TAG, "API error: ${result.message}")
                FetchResult.Error(result.message)
            }
        }
    }
    
    /**
     * Parse .lrc file and return list of lyric lines with timestamps
     */
    fun parseLrcFile(filePath: String): List<LyricLine> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        
        val lyrics = mutableListOf<LyricLine>()
        
        try {
            val fileLines = file.readLines()
            
            fileLines.forEach { line ->
                // More flexible regex to handle various LRC formats:
                // [m:ss.xx], [mm:ss.xx], [mm:ss.xxx], [m:ss:xx], etc.
                val regex = "\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\](.*)".toRegex()
                val match = regex.find(line)
                
                if (match != null) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0
                    val fraction = match.groupValues[3]
                    val text = match.groupValues[4].trim()
                    
                    // Handle both .xx (centiseconds) and .xxx (milliseconds) formats
                    val fractionMs = when (fraction.length) {
                        2 -> fraction.toLongOrNull()?.times(10) ?: 0  // centiseconds
                        3 -> fraction.toLongOrNull() ?: 0              // milliseconds
                        else -> fraction.toLongOrNull() ?: 0
                    }
                    
                    val timeMs = (minutes * 60 + seconds) * 1000 + fractionMs
                    lyrics.add(LyricLine(timeMs, text))
                }
            }
            
            // If no timed lyrics found, try to load as plain text
            if (lyrics.isEmpty() && fileLines.isNotEmpty()) {
                var timeOffset = 0L
                fileLines.filter { it.isNotBlank() && !it.startsWith("[") }.forEach { text ->
                    lyrics.add(LyricLine(timeOffset, text.trim()))
                    timeOffset += 3000 // 3 seconds per line as fallback
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return lyrics.sortedBy { it.timeMs }
    }
}

/**
 * Represents a single line of lyrics with its timestamp
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Result of fetching lyrics from API
 */
sealed class FetchResult {
    data class Success(val isSynced: Boolean) : FetchResult()
    object NotFound : FetchResult()
    data class Error(val message: String) : FetchResult()
}
