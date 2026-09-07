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
            // Try with specific audio session ID first
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            
            loudnessEnhancer = try {
                LoudnessEnhancer(sessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer session $sessionId failed", e)
                null
            }

            virtualizer = try {
                Virtualizer(0, sessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer session $sessionId failed", e)
                null
            }

            restoreSettings(context)
            Log.d(TAG, "Initialized audio effects with session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Session $sessionId failed, trying global fallback", e)
            
            // Fallback: try global audio output (session ID 0)
            try {
                equalizer = Equalizer(0, 0).apply { enabled = true }
                bassBoost = BassBoost(0, 0).apply { enabled = true }
                loudnessEnhancer = try { LoudnessEnhancer(0).apply { enabled = true } } catch (e2: Exception) { null }
                virtualizer = try { Virtualizer(0, 0).apply { enabled = true } } catch (e2: Exception) { null }
                audioSessionId = 0
                restoreSettings(context)
                Log.d(TAG, "Initialized with global session (fallback)")
            } catch (e2: Exception) {
                Log.e(TAG, "Platform audio effects unavailable, relying on native DSP engine", e2)
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
    
    /**
     * Check if the device supports audio effects.
     * MusicDeck v3.0.0 uses an in-house C++ DSP engine, which is always supported.
     */
    fun isSupported(context: Context): Boolean = true
    
    fun getEqualizer(): Equalizer? = equalizer
    fun getBassBoost(): BassBoost? = bassBoost
    fun getLoudnessEnhancer(): LoudnessEnhancer? = loudnessEnhancer
    fun getVirtualizer(): Virtualizer? = virtualizer

    private fun restoreSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Restore Enabled State
        val isEnabled = prefs.getBoolean("eq_enabled", true)
        NativeAudioEngine.setEnabled(isEnabled)
        try { equalizer?.enabled = isEnabled } catch (_: Exception) {}
        try { bassBoost?.enabled = isEnabled } catch (_: Exception) {}
        try { loudnessEnhancer?.enabled = isEnabled } catch (_: Exception) {}
        try { virtualizer?.enabled = isEnabled } catch (_: Exception) {}

        // Restore EQ Bands into Native C++ DSP Engine
        for (i in 0 until 5) {
            val savedProgress = prefs.getInt("eq_band_$i", 50)
            val gainDb = (savedProgress - 50) * 0.3f
            NativeAudioEngine.setBandGain(i, gainDb)
        }

        // Also sync platform Equalizer if available
        equalizer?.let { eq ->
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            val range = maxLevel - minLevel
            
            for (i in 0 until eq.numberOfBands) {
                val savedProgress = prefs.getInt("eq_band_$i", 50)
                val level = (minLevel + (savedProgress * range / 100)).toShort()
                try {
                    eq.setBandLevel(i.toShort(), level)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Restore Bass Boost
        val bassProgress = prefs.getInt("bass_boost_strength", 0)
        NativeAudioEngine.setBassBoost(bassProgress / 1000f)
        bassBoost?.let { bb ->
            if (bb.strengthSupported) {
                try {
                    bb.setStrength(bassProgress.toShort())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Restore Volume Boost (LoudnessEnhancer)
        loudnessEnhancer?.let { le ->
            val gain = prefs.getInt("volume_boost_gain", 0)
            try {
                le.setTargetGain(gain)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Restore Virtualizer (3D Audio)
        val virtStrength = prefs.getInt("virtualizer_strength", 0)
        NativeAudioEngine.setVirtualizer(virtStrength / 1000f)
        virtualizer?.let { virt ->
            if (virt.strengthSupported) {
                try {
                    virt.setStrength(virtStrength.toShort())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Restore Extreme Bass
        if (prefs.getBoolean("extreme_bass_enabled", false)) {
            applyExtremeBass()
        }
    }

    fun applyExtremeBass() {
        // Native C++ DSP: Boost lowest 2 bands to +15.0 dB and max bass shelf
        NativeAudioEngine.setBandGain(0, 15.0f)
        NativeAudioEngine.setBandGain(1, 15.0f)
        NativeAudioEngine.setBassBoost(1.0f)

        // Sync platform effects if present
        equalizer?.let { eq ->
            val maxLevel = eq.bandLevelRange[1]
            try {
                if (eq.numberOfBands >= 1) eq.setBandLevel(0, maxLevel)
                if (eq.numberOfBands >= 2) eq.setBandLevel(1, maxLevel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bassBoost?.let { bb ->
            if (bb.strengthSupported) {
                try {
                    bb.setStrength(1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setEqEnabled(enabled: Boolean, context: Context) {
        NativeAudioEngine.setEnabled(enabled)
        try { equalizer?.enabled = enabled } catch (_: Exception) {}
        try { bassBoost?.enabled = enabled } catch (_: Exception) {}
        try { loudnessEnhancer?.enabled = enabled } catch (_: Exception) {}
        try { virtualizer?.enabled = enabled } catch (_: Exception) {}

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("eq_enabled", enabled)
            .apply()
    }

    fun setBandLevel(band: Short, progress: Int, context: Context) {
        // Translate 0..100 slider value (50 = neutral) to dB: -15.0 dB to +15.0 dB
        val gainDb = (progress - 50) * 0.3f
        NativeAudioEngine.setBandGain(band.toInt(), gainDb)

        equalizer?.let { eq ->
            val minLevel = eq.bandLevelRange[0]
            val range = eq.bandLevelRange[1] - minLevel
            val level = (minLevel + (progress * range / 100)).toShort()
            try {
                eq.setBandLevel(band, level)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("eq_band_$band", progress)
            .apply()
    }
    
    fun setBassBoostStrength(progress: Int, context: Context) {
        // Translate 0..1000 to normalized float 0.0f .. 1.0f
        NativeAudioEngine.setBassBoost(progress / 1000f)

        bassBoost?.let { bb ->
            try {
                bb.setStrength(progress.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("bass_boost_strength", progress)
            .apply()
    }

    fun setVolumeBoostGain(gainmB: Int, context: Context, saveToPrefs: Boolean = true) {
        loudnessEnhancer?.let { le ->
            try {
                le.setTargetGain(gainmB)
                if (saveToPrefs) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt("volume_boost_gain", gainmB)
                        .apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getSavedVolumeBoostGain(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("volume_boost_gain", 0)
    }

    fun setVirtualizerStrength(strength: Int, context: Context, saveToPrefs: Boolean = true) {
        // Translate 0..1000 to normalized float 0.0f .. 1.0f
        NativeAudioEngine.setVirtualizer(strength / 1000f)

        virtualizer?.let { virt ->
            try {
                if (virt.strengthSupported) {
                    virt.setStrength(strength.toShort())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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
}
