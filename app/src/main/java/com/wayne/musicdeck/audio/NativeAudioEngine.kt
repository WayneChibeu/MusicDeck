/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native Audio Engine JNI Wrapper.
 */

package com.wayne.musicdeck.audio

import android.util.Log
import java.nio.ByteBuffer

/**
 * MusicDeck Native C++ Audio Engine Wrapper.
 * Bridges Kotlin audio layer to high-performance C++ DSP routines.
 */
object NativeAudioEngine {
    private const val TAG = "NativeAudioEngine"

    var isLibraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("musicdeck_audio")
            isLibraryLoaded = true
            Log.i(TAG, "libmusicdeck_audio.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native audio library", e)
            isLibraryLoaded = false
        }
    }

    fun init() {
        if (isLibraryLoaded) {
            try {
                nativeInit()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing native engine", e)
            }
        }
    }

    fun setSampleRate(sampleRate: Int) {
        if (isLibraryLoaded) {
            try {
                nativeSetSampleRate(sampleRate)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native sample rate", e)
            }
        }
    }

    fun setBandGain(band: Int, gainDb: Float) {
        if (isLibraryLoaded) {
            try {
                nativeSetBandGain(band, gainDb)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native band gain", e)
            }
        }
    }

    fun setBassBoost(strength: Float) {
        if (isLibraryLoaded) {
            try {
                nativeSetBassBoost(strength)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native bass boost", e)
            }
        }
    }

    fun setVolumeBoost(gainDb: Float) {
        if (isLibraryLoaded) {
            try {
                nativeSetVolumeBoost(gainDb)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native volume boost", e)
            }
        }
    }

    fun setVirtualizer(strength: Float) {
        if (isLibraryLoaded) {
            try {
                nativeSetVirtualizer(strength)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native virtualizer", e)
            }
        }
    }

    fun setKaraokeEnabled(enabled: Boolean) {
        if (isLibraryLoaded) {
            try {
                nativeSetKaraokeEnabled(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native karaoke state", e)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (isLibraryLoaded) {
            try {
                nativeSetEnabled(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting native enabled state", e)
            }
        }
    }

    fun reset(engineId: Int = -1) {
        if (isLibraryLoaded) {
            try {
                nativeReset(engineId)
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting native engine", e)
            }
        }
    }

    fun processBuffer(buffer: ByteBuffer, offset: Int, length: Int, encoding: Int, engineId: Int = 0) {
        if (isLibraryLoaded && buffer.isDirect) {
            try {
                nativeProcessBuffer(engineId, buffer, offset, length, encoding)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing native buffer", e)
            }
        }
    }

    fun getVisualizerBins(outBins: FloatArray) {
        if (isLibraryLoaded) {
            try {
                nativeGetVisualizerBins(outBins)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting visualizer bins", e)
            }
        }
    }

    fun release() {
        if (isLibraryLoaded) {
            try {
                nativeRelease()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing native engine", e)
            }
        }
    }

    // Native C++ declarations
    private external fun nativeInit()
    private external fun nativeSetSampleRate(sampleRate: Int)
    private external fun nativeSetBandGain(band: Int, gainDb: Float)
    private external fun nativeSetBassBoost(strength: Float)
    private external fun nativeSetVolumeBoost(gainDb: Float)
    private external fun nativeSetVirtualizer(strength: Float)
    private external fun nativeSetKaraokeEnabled(enabled: Boolean)
    private external fun nativeSetEnabled(enabled: Boolean)
    private external fun nativeReset(engineId: Int)
    private external fun nativeProcessBuffer(engineId: Int, buffer: ByteBuffer, offset: Int, length: Int, encoding: Int)
    private external fun nativeGetVisualizerBins(outArray: FloatArray)
    private external fun nativeRelease()
}

