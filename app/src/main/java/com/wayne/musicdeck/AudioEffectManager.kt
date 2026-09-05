package com.wayne.musicdeck

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

object AudioEffectManager {
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
        if (audioSessionId == sessionId && equalizer != null) return // Already initialized

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
                Log.w("AudioEffectManager", "LoudnessEnhancer session $sessionId failed", e)
                null
            }

            virtualizer = try {
                Virtualizer(0, sessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w("AudioEffectManager", "Virtualizer session $sessionId failed", e)
                null
            }

            restoreSettings(context)
            Log.d("AudioEffectManager", "Initialized audio effects with session $sessionId")
        } catch (e: Exception) {
            Log.w("AudioEffectManager", "Session $sessionId failed, trying global fallback", e)
            
            // Fallback: try global audio output (session ID 0)
            try {
                equalizer = Equalizer(0, 0).apply { enabled = true }
                bassBoost = BassBoost(0, 0).apply { enabled = true }
                loudnessEnhancer = try { LoudnessEnhancer(0).apply { enabled = true } } catch (e2: Exception) { null }
                virtualizer = try { Virtualizer(0, 0).apply { enabled = true } } catch (e2: Exception) { null }
                audioSessionId = 0
                restoreSettings(context)
                Log.d("AudioEffectManager", "Initialized with global session (fallback)")
            } catch (e2: Exception) {
                Log.e("AudioEffectManager", "All audio effects unavailable", e2)
                lastInitError = "Audio effects not supported. Try restarting your phone."
                equalizer = null
                bassBoost = null
                loudnessEnhancer = null
                virtualizer = null
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
    
    fun isInitialized(): Boolean = equalizer != null
    
    /**
     * Check if the device supports audio effects (Equalizer).
     */
    fun isSupported(context: Context): Boolean {
        if (equalizer != null) return true
        
        return try {
            val testEq = Equalizer(0, 0)
            testEq.release()
            true
        } catch (e: Exception) {
            Log.d("AudioEffectManager", "Device does not support Equalizer: ${e.message}")
            false
        }
    }
    
    fun getEqualizer(): Equalizer? = equalizer
    fun getBassBoost(): BassBoost? = bassBoost
    fun getLoudnessEnhancer(): LoudnessEnhancer? = loudnessEnhancer
    fun getVirtualizer(): Virtualizer? = virtualizer

    private fun restoreSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Restore Enabled State
        val isEnabled = prefs.getBoolean("eq_enabled", true)
        equalizer?.enabled = isEnabled
        bassBoost?.enabled = isEnabled
        loudnessEnhancer?.enabled = isEnabled
        virtualizer?.enabled = isEnabled

        // Restore EQ Bands
        equalizer?.let { eq ->
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            val range = maxLevel - minLevel
            
            if (prefs.contains("eq_band_0")) {
                for (i in 0 until eq.numberOfBands) {
                    val savedProgress = prefs.getInt("eq_band_$i", 50)
                    val level = (minLevel + (savedProgress * range / 100)).toShort()
                    eq.setBandLevel(i.toShort(), level)
                }
            }
        }

        // Restore Bass Boost
        bassBoost?.let { bb ->
            if (bb.strengthSupported) {
                val strength = prefs.getInt("bass_boost_strength", 0).toShort()
                bb.setStrength(strength)
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
        virtualizer?.let { virt ->
            if (virt.strengthSupported) {
                val strength = prefs.getInt("virtualizer_strength", 0).toShort()
                try {
                    virt.setStrength(strength)
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
        equalizer?.let { eq ->
            val maxLevel = eq.bandLevelRange[1]
            if (eq.numberOfBands >= 1) eq.setBandLevel(0, maxLevel)
            if (eq.numberOfBands >= 2) eq.setBandLevel(1, maxLevel)
        }
        bassBoost?.let { bb ->
            if (bb.strengthSupported) {
                bb.setStrength(1000)
            }
        }
    }

    fun setEqEnabled(enabled: Boolean, context: Context) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        loudnessEnhancer?.enabled = enabled
        virtualizer?.enabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("eq_enabled", enabled)
            .apply()
    }

    fun setBandLevel(band: Short, progress: Int, context: Context) {
        equalizer?.let { eq ->
            val minLevel = eq.bandLevelRange[0]
            val range = eq.bandLevelRange[1] - minLevel
            val level = (minLevel + (progress * range / 100)).toShort()
            eq.setBandLevel(band, level)
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("eq_band_$band", progress)
                .apply()
        }
    }
    
    fun setBassBoostStrength(progress: Int, context: Context) {
        bassBoost?.let { bb ->
            try {
                bb.setStrength(progress.toShort())
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("bass_boost_strength", progress)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setVolumeBoostGain(gainmB: Int, context: Context) {
        loudnessEnhancer?.let { le ->
            try {
                le.setTargetGain(gainmB)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("volume_boost_gain", gainmB)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getSavedVolumeBoostGain(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("volume_boost_gain", 0)
    }

    fun setVirtualizerStrength(strength: Int, context: Context) {
        virtualizer?.let { virt ->
            try {
                if (virt.strengthSupported) {
                    virt.setStrength(strength.toShort())
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("virtualizer_strength", strength)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
