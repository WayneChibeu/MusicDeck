package com.wayne.musicdeck.utils

import android.content.Context
import com.tencent.mmkv.MMKV

class SettingsManager(context: Context) {
    private val kv = MMKV.defaultMMKV()
    private val oldPrefs = context.getSharedPreferences("musicdeck_prefs", Context.MODE_PRIVATE)

    init {
        // One-time migration if needed
        if (oldPrefs.all.isNotEmpty() && !kv.containsKey("mmkv_migrated")) {
            kv.importFromSharedPreferences(oldPrefs)
            kv.encode("mmkv_migrated", true)
            // Optionally clear old prefs
            oldPrefs.edit().clear().apply()
        }
    }

    var lastPlayedSongId: Long
        get() = kv.decodeLong("last_song_id", -1)
        set(value) { kv.encode("last_song_id", value) }

    var lastPlayedSongPath: String?
        get() = kv.decodeString("last_song_path", null)
        set(value) { kv.encode("last_song_path", value) }

    var lastPlayedPosition: Long
        get() = kv.decodeLong("last_position", 0L)
        set(value) { kv.encode("last_position", value) }

    fun saveSearchQuery(history: List<String>) {
        kv.encode("search_history", history.joinToString("|||"))
    }

    fun getSearchHistory(): List<String> {
        val str = kv.decodeString("search_history", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split("|||")
    }

    fun clearSearchHistory() {
        kv.removeValueForKey("search_history")
    }

    // Modernization: Theme state management (Seal-ify)
    var isDynamicColorEnabled: Boolean
        get() = kv.decodeBool("dynamic_color_enabled", true)
        set(value) { kv.encode("dynamic_color_enabled", value) }
        
    var isSunsetTransitionEnabled: Boolean
        get() = kv.decodeBool("sunset_transition_enabled", false)
        set(value) { kv.encode("sunset_transition_enabled", value) }
        
    var isFirstLaunch: Boolean
        get() = kv.decodeBool("is_first_launch", true)
        set(value) { kv.encode("is_first_launch", value) }
        
    var userName: String?
        get() = kv.decodeString("user_name", null)
        set(value) { kv.encode("user_name", value) }
        
    var lastPlayedTitle: String
        get() = kv.decodeString("last_title", "Not Playing") ?: "Not Playing"
        set(value) { kv.encode("last_title", value) }
        
    var lastPlayedArtist: String
        get() = kv.decodeString("last_artist", "MusicDeck") ?: "MusicDeck"
        set(value) { kv.encode("last_artist", value) }
    
    // Lyric font size index: 0=Small, 1=Default, 2=Medium, 3=Large, 4=Extra Large
    var lyricFontSizeIndex: Int
        get() = kv.decodeInt("lyric_font_size_index", 1) // Default
        set(value) { kv.encode("lyric_font_size_index", value) }

    var lastPlayedIsFavorite: Boolean
        get() = kv.decodeBool("last_is_favorite", false)
        set(value) { kv.encode("last_is_favorite", value) }
}
