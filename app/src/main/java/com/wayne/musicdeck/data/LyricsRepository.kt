package com.wayne.musicdeck.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class LyricsRepository(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)
    
    /**
     * Save lyric file path for a specific song
     */
    fun saveLyricPath(songId: Long, filePath: String) {
        prefs.edit().putString(songId.toString(), filePath).apply()
    }
    
    /**
     * Get lyric file path for a song
     */
    fun getLyricPath(songId: Long): String? {
        return prefs.getString(songId.toString(), null)
    }
    
    /**
     * Remove lyric file for a song
     */
    fun removeLyricPath(songId: Long) {
        prefs.edit().remove(songId.toString()).apply()
    }
    
    /**
     * Check if a song has lyrics
     */
    fun hasLyrics(songId: Long): Boolean {
        return prefs.contains(songId.toString())
    }
    
    /**
     * Parse .lrc file and return list of lyric lines with timestamps
     */
    fun parseLrcFile(filePath: String): List<LyricLine> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        
        val lyrics = mutableListOf<LyricLine>()
        
        try {
            file.readLines().forEach { line ->
                // Parse [mm:ss.xx] format
                val regex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)".toRegex()
                val match = regex.find(line)
                
                if (match != null) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0
                    val centiseconds = match.groupValues[3].toLongOrNull() ?: 0
                    val text = match.groupValues[4].trim()
                    
                    val timeMs = (minutes * 60 + seconds) * 1000 + centiseconds * 10
                    lyrics.add(LyricLine(timeMs, text))
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
