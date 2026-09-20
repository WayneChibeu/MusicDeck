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
        
    var isCrossfadeEnabled: Boolean
        get() = kv.decodeBool("crossfade_enabled", false)
        set(value) { kv.encode("crossfade_enabled", value) }

    var crossfadeDurationSeconds: Int
        get() = kv.decodeInt("crossfade_duration_seconds", 4).coerceIn(1, 12)
        set(value) { kv.encode("crossfade_duration_seconds", value.coerceIn(1, 12)) }
        
    var isInsightsEnabled: Boolean
        get() = kv.decodeBool("insights_enabled", true)
        set(value) { kv.encode("insights_enabled", value) }
        
    var isSmartPlaylistsEnabled: Boolean
        get() = kv.decodeBool("smart_playlists_enabled", true)
        set(value) { kv.encode("smart_playlists_enabled", value) }
        
    var isPlaylistCollageEnabled: Boolean
        get() = kv.decodeBool("playlist_collage_enabled", true)
        set(value) { kv.encode("playlist_collage_enabled", value) }
        
    var isSongNotesEnabled: Boolean
        get() = kv.decodeBool("song_notes_enabled", true)
        set(value) { kv.encode("song_notes_enabled", value) }
        
    var isShakeToShuffleEnabled: Boolean
        get() = kv.decodeBool("shake_to_shuffle_enabled", true)
        set(value) { kv.encode("shake_to_shuffle_enabled", value) }
        
    var isEarbudComboShuffleEnabled: Boolean
        get() = kv.decodeBool("earbud_combo_shuffle_enabled", true)
        set(value) { kv.encode("earbud_combo_shuffle_enabled", value) }
        
    var isAlbumPulsingEnabled: Boolean
        get() = kv.decodeBool("album_pulsing_enabled", true)
        set(value) { kv.encode("album_pulsing_enabled", value) }

    var isShuffleEnabled: Boolean
        get() = kv.decodeBool("shuffle_enabled", false)
        set(value) { kv.encode("shuffle_enabled", value) }

    var repeatMode: Int
        get() = kv.decodeInt("repeat_mode", 2) // Default 2 = REPEAT_MODE_ALL
        set(value) { kv.encode("repeat_mode", value) }

    var skipMobileDataLyricsWarning: Boolean
        get() = kv.decodeBool("skip_mobile_data_lyrics_warning", false)
        set(value) { kv.encode("skip_mobile_data_lyrics_warning", value) }

    var lastUpdateCheckTime: Long
        get() = kv.decodeLong("last_update_check_time", 0L)
        set(value) { kv.encode("last_update_check_time", value) }

    var isAutoUpdateEnabled: Boolean
        get() = kv.decodeBool("auto_update_enabled", true)
        set(value) { kv.encode("auto_update_enabled", value) }

    var isSoundCheckEnabled: Boolean
        get() = kv.decodeBool("sound_check_enabled", false)
        set(value) { kv.encode("sound_check_enabled", value) }

    var isVisualizerEnabled: Boolean
        get() = kv.decodeBool("visualizer_enabled", true)
        set(value) { kv.encode("visualizer_enabled", value) }

    var isAbLoopEnabled: Boolean
        get() = kv.decodeBool("ab_loop_enabled", true)
        set(value) { kv.encode("ab_loop_enabled", value) }

    var isAudioBadgeEnabled: Boolean
        get() = kv.decodeBool("audio_badge_enabled", false) // Default off to keep player uncluttered, accessible via 3-dots
        set(value) { kv.encode("audio_badge_enabled", value) }

    var isWaveformScrubBarEnabled: Boolean
        get() = kv.decodeBool("waveform_scrub_bar_enabled", true)
        set(value) { kv.encode("waveform_scrub_bar_enabled", value) }

    var playbackTempo: Float
        get() = kv.decodeFloat("playback_tempo", 1.0f).coerceIn(0.5f, 2.0f)
        set(value) { kv.encode("playback_tempo", value.coerceIn(0.5f, 2.0f)) }

    var playbackPitchSemitones: Float
        get() = kv.decodeFloat("playback_pitch_semitones", 0.0f).coerceIn(-12.0f, 12.0f)
        set(value) { kv.encode("playback_pitch_semitones", value.coerceIn(-12.0f, 12.0f)) }

    var isReverbEnabled: Boolean
        get() = kv.decodeBool("reverb_enabled", false)
        set(value) { kv.encode("reverb_enabled", value) }

    var reverbRoomSize: Float
        get() = kv.decodeFloat("reverb_room_size", 0.75f).coerceIn(0.0f, 1.0f)
        set(value) { kv.encode("reverb_room_size", value.coerceIn(0.0f, 1.0f)) }

    var reverbDamping: Float
        get() = kv.decodeFloat("reverb_damping", 0.40f).coerceIn(0.0f, 1.0f)
        set(value) { kv.encode("reverb_damping", value.coerceIn(0.0f, 1.0f)) }

    var reverbWetLevel: Float
        get() = kv.decodeFloat("reverb_wet_level", 0.35f).coerceIn(0.0f, 1.0f)
        set(value) { kv.encode("reverb_wet_level", value.coerceIn(0.0f, 1.0f)) }

    var tempoPitchPreset: String
        get() = kv.decodeString("tempo_pitch_preset", "Normal") ?: "Normal"
        set(value) { kv.encode("tempo_pitch_preset", value) }

    companion object {
        const val USB_DAC_MODE_PURE_DIRECT = "pure_direct"
        const val USB_DAC_MODE_DECKACOUSTIX_DSP = "deckacoustix_dsp"
    }

    var isUsbDacPassthroughEnabled: Boolean
        get() = kv.decodeBool("usb_dac_passthrough_enabled", true)
        set(value) { kv.encode("usb_dac_passthrough_enabled", value) }

    var usbDacMode: String
        get() = kv.decodeString("usb_dac_mode", USB_DAC_MODE_PURE_DIRECT) ?: USB_DAC_MODE_PURE_DIRECT
        set(value) { kv.encode("usb_dac_mode", value) }
}

