package com.wayne.musicdeck.data

import android.content.Context
import android.content.SharedPreferences

class CustomCoverRepository(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("custom_covers", Context.MODE_PRIVATE)
    
    /**
     * Save custom cover file path for a specific song
     * @param songId The unique song ID
     * @param filePath Absolute path to the custom cover image in internal storage
     */
    fun saveCustomCover(songId: Long, filePath: String) {
        prefs.edit().putString(songId.toString(), filePath).apply()
    }
    
    /**
     * Get custom cover file path for a song
     * @param songId The unique song ID
     * @return File path if exists, null otherwise
     */
    fun getCustomCover(songId: Long): String? {
        return prefs.getString(songId.toString(), null)
    }
    
    /**
     * Remove custom cover for a song
     * @param songId The unique song ID
     */
    fun removeCustomCover(songId: Long) {
        prefs.edit().remove(songId.toString()).apply()
    }
    
    /**
     * Check if a song has a custom cover
     */
    fun hasCustomCover(songId: Long): Boolean {
        return prefs.contains(songId.toString())
    }
}
