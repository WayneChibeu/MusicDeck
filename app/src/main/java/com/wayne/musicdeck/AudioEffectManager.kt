package com.wayne.musicdeck

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.wayne.musicdeck.audio.NativeAudioEngine

object AudioEffectManager {
    private const val TAG = "AudioEffectManager"
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null
    private var audioSessionId: Int = 0
    private const val PREFS_NAME = "eq_prefs"
    
    // Store last error for user feedback
    var lastInitError: String? = null
        private set

    fun initialize(sessionId: Int, context: Context) {
        // Initialize native C++ audio engine
        NativeAudioEngine.init()

        if (audioSessionId == sessionId && (equalizer != null || NativeAudioEngine.isLibraryLoaded)) {
            restoreSettings(context)
            return // Already initialized
        }

        release()
        audioSessionId = sessionId
        lastInitError = null

        try {
            // Keep platform effects in disabled/bypassed state.
            // They are retained only for reflection/capability queries if needed,
            // while our native C++ DSP pipeline has 100% exclusive processing authority.
            equalizer = Equalizer(0, sessionId).apply { enabled = false }
            bassBoost = BassBoost(0, sessionId).apply { enabled = false }
            
            loudnessEnhancer = try {
                LoudnessEnhancer(sessionId).apply { enabled = false }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer session $sessionId failed", e)
                null
            }

            virtualizer = try {
                Virtualizer(0, sessionId).apply { enabled = false }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer session $sessionId failed", e)
                null
            }

            restoreSettings(context)
            Log.d(TAG, "Initialized native audio DSP with session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Session $sessionId failed, trying global fallback", e)
            
            // Fallback: try global audio output (session ID 0), keeping disabled
            try {
                equalizer = Equalizer(0, 0).apply { enabled = false }
                bassBoost = BassBoost(0, 0).apply { enabled = false }
                loudnessEnhancer = try { LoudnessEnhancer(0).apply { enabled = false } } catch (e2: Exception) { null }
                virtualizer = try { Virtualizer(0, 0).apply { enabled = false } } catch (e2: Exception) { null }
                audioSessionId = 0
                restoreSettings(context)
                Log.d(TAG, "Initialized with global session (fallback)")
            } catch (e2: Exception) {
                Log.e(TAG, "Platform audio effects unavailable, relying purely on native DSP engine", e2)
                equalizer = null
                bassBoost = null
                loudnessEnhancer = null
                virtualizer = null
                restoreSettings(context)
            }
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            loudnessEnhancer?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        virtualizer = null
        audioSessionId = 0
    }
    
    fun isInitialized(): Boolean = NativeAudioEngine.isLibraryLoaded || equalizer != null
    
    fun isEqEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("eq_enabled", true)
    }
    
    /**
     * Check if the device supports audio effects.
     * MusicDeck uses an in-house C++ DSP engine, which is always supported across all hardware.
     */
    fun isSupported(context: Context): Boolean = true
    
    fun getEqualizer(): Equalizer? = equalizer
    fun getBassBoost(): BassBoost? = bassBoost
    fun getLoudnessEnhancer(): LoudnessEnhancer? = loudnessEnhancer
    fun getVirtualizer(): Virtualizer? = virtualizer

    private fun restoreSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Restore Enabled State in Native DSP
        val isEnabled = prefs.getBoolean("eq_enabled", true)
        NativeAudioEngine.setEnabled(isEnabled)
        
        // Ensure platform effects remain completely disabled (bypassed) to prevent OEM double-boosting
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { bassBoost?.enabled = false } catch (_: Exception) {}
        try { loudnessEnhancer?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.enabled = false } catch (_: Exception) {}

        // Restore EQ Bands into Native C++ DSP Engine
        for (i in 0 until 5) {
            val savedProgress = prefs.getInt("eq_band_$i", 50)
            val gainDb = (savedProgress - 50) * 0.3f
            NativeAudioEngine.setBandGain(i, gainDb)
        }

        // Restore Bass Boost into Native C++ DSP Engine
        val bassProgress = prefs.getInt("bass_boost_strength", 0)
        NativeAudioEngine.setBassBoost(bassProgress / 1000f)

        // Restore Volume Boost into Native C++ DSP Engine
        val gainmB = prefs.getInt("volume_boost_gain", 0)
        NativeAudioEngine.setVolumeBoost(gainmB / 100f)

        // Restore Virtualizer (3D Audio) into Native C++ DSP Engine
        val virtStrength = prefs.getInt("virtualizer_strength", 0)
        NativeAudioEngine.setVirtualizer(virtStrength / 1000f)

        // Restore Headphone Crossfeed (Bauer Binaural DSP) into Native C++ Engine
        val crossStrength = prefs.getInt("crossfeed_strength", 0)
        val crossMode = prefs.getInt("crossfeed_mode", 2)
        NativeAudioEngine.setCrossfeedMode(crossMode)
        NativeAudioEngine.setCrossfeedStrength(crossStrength / 1000f)

        // Restore Karaoke Mode into Native C++ DSP Engine
        val isKaraoke = prefs.getBoolean("karaoke_enabled", false)
        NativeAudioEngine.setKaraokeEnabled(isKaraoke)

        // Restore Atmospheric Reverb (Freeverb) into Native C++ DSP Engine
        val isReverb = prefs.getBoolean("reverb_enabled", false)
        val roomSize = prefs.getFloat("reverb_room_size", 0.75f)
        val damping = prefs.getFloat("reverb_damping", 0.40f)
        val wetLevel = prefs.getFloat("reverb_wet_level", 0.35f)
        NativeAudioEngine.setReverbParams(isReverb, roomSize, damping, wetLevel)

        // Restore Extreme Bass
        if (prefs.getBoolean("extreme_bass_enabled", false)) {
            applyExtremeBass()
        }
    }

    fun setReverbParams(
        enabled: Boolean,
        roomSize: Float,
        damping: Float,
        wetLevel: Float,
        context: Context,
        saveToPrefs: Boolean = true
    ) {
        NativeAudioEngine.setReverbParams(enabled, roomSize, damping, wetLevel)
        if (saveToPrefs) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("reverb_enabled", enabled)
                .putFloat("reverb_room_size", roomSize)
                .putFloat("reverb_damping", damping)
                .putFloat("reverb_wet_level", wetLevel)
                .apply()
        }
    }

    fun isReverbEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("reverb_enabled", false)
    }

    fun getReverbRoomSize(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("reverb_room_size", 0.75f)
    }

    fun getReverbDamping(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("reverb_damping", 0.40f)
    }

    fun getReverbWetLevel(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("reverb_wet_level", 0.35f)
    }

    fun applyExtremeBass() {
        // Native C++ DSP: Boost lowest 2 bands to +15.0 dB and max bass shelf
        NativeAudioEngine.setBandGain(0, 15.0f)
        NativeAudioEngine.setBandGain(1, 15.0f)
        NativeAudioEngine.setBassBoost(1.0f)
    }

    fun setEqEnabled(enabled: Boolean, context: Context) {
        NativeAudioEngine.setEnabled(enabled)
        // Platform effects remain completely disabled; native DSP has exclusive control
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { bassBoost?.enabled = false } catch (_: Exception) {}
        try { loudnessEnhancer?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.enabled = false } catch (_: Exception) {}

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("eq_enabled", enabled)
            .apply()
    }

    fun setBandLevel(band: Short, progress: Int, context: Context) {
        // Translate 0..100 slider value (50 = neutral) to dB: -15.0 dB to +15.0 dB
        val gainDb = (progress - 50) * 0.3f
        NativeAudioEngine.setBandGain(band.toInt(), gainDb)
        
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("eq_band_$band", progress)
            .apply()
    }
    
    fun setBassBoostStrength(progress: Int, context: Context) {
        // Native C++ DSP handles bass boost cleanly with resonant low-shelf and dynamic headroom
        NativeAudioEngine.setBassBoost(progress / 1000f)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("bass_boost_strength", progress)
            .apply()
    }

    fun getBassBoostStrength(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("bass_boost_strength", 0)
    }

    fun setVolumeBoostGain(gainmB: Int, context: Context, saveToPrefs: Boolean = true) {
        // Translate millibels to decibels: 100 mB = 1.0 dB
        val gainDb = gainmB / 100f
        NativeAudioEngine.setVolumeBoost(gainDb)

        if (saveToPrefs) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("volume_boost_gain", gainmB)
                .apply()
        }
    }

    fun getSavedVolumeBoostGain(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("volume_boost_gain", 0)
    }

    fun setVirtualizerStrength(strength: Int, context: Context, saveToPrefs: Boolean = true) {
        // Translate 0..1000 to normalized float 0.0f .. 1.0f
        NativeAudioEngine.setVirtualizer(strength / 1000f)

        if (saveToPrefs) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("virtualizer_strength", strength)
                .apply()
        }
    }

    fun getSavedVirtualizerStrength(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("virtualizer_strength", 0)
    }

    fun setCrossfeedStrength(strength: Int, context: Context, saveToPrefs: Boolean = true) {
        NativeAudioEngine.setCrossfeedStrength(strength / 1000f)
        if (saveToPrefs) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("crossfeed_strength", strength)
                .apply()
        }
    }

    fun getSavedCrossfeedStrength(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("crossfeed_strength", 0)
    }

    fun setCrossfeedMode(mode: Int, context: Context, saveToPrefs: Boolean = true) {
        NativeAudioEngine.setCrossfeedMode(mode)
        if (saveToPrefs) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("crossfeed_mode", mode)
                .apply()
        }
    }

    fun getSavedCrossfeedMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("crossfeed_mode", 2)
    }
    
    fun savePreset(presetName: String, context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("eq_preset", presetName)
            .apply()
    }
    
    fun getSavedPreset(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("eq_preset", "Flat") ?: "Flat"
    }

    fun setExtremeBassEnabled(enabled: Boolean, context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("extreme_bass_enabled", enabled)
            .apply()
        
        if (enabled) {
            applyExtremeBass()
        } else {
            // Restore normal settings
            restoreSettings(context)
        }
    }

    fun isExtremeBassEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("extreme_bass_enabled", false)
    }

    fun setKaraokeEnabled(enabled: Boolean, context: Context) {
        NativeAudioEngine.setKaraokeEnabled(enabled)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("karaoke_enabled", enabled)
            .apply()
    }

    fun isKaraokeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("karaoke_enabled", false)
    }
}
