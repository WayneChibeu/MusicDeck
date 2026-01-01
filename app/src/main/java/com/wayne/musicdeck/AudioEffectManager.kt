package com.wayne.musicdeck

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

object AudioEffectManager {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var audioSessionId: Int = 0
    private const val PREFS_NAME = "eq_prefs"

    fun initialize(sessionId: Int, context: Context) {
        if (audioSessionId == sessionId && equalizer != null) return // Already initialized

        release()
        audioSessionId = sessionId

        try {
            // Initialize Equalizer
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
            }

            // Initialize BassBoost
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = true
            }

            // Restore settings immediately
            restoreSettings(context)

        } catch (e: Exception) {
            Log.e("AudioEffectManager", "Failed to initialize audio effects", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        bassBoost = null
        audioSessionId = 0
    }

    fun getEqualizer(): Equalizer? = equalizer
    fun getBassBoost(): BassBoost? = bassBoost

    private fun restoreSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Restore Enabled State
        val isEnabled = prefs.getBoolean("eq_enabled", true)
        equalizer?.enabled = isEnabled
        bassBoost?.enabled = isEnabled

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
    }

    fun setEqEnabled(enabled: Boolean, context: Context) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
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
}
