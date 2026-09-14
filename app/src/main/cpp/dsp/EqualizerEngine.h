/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native C++ Audio Processing Pipeline.
 */

#ifndef MUSICDECK_EQUALIZER_ENGINE_H
#define MUSICDECK_EQUALIZER_ENGINE_H

#include <vector>
#include <mutex>
#include "BiquadFilter.h"
#include "Limiter.h"
#include "FftProcessor.h"
#include "Freeverb.h"

namespace musicdeck {

constexpr int NUM_EQ_BANDS = 5;

/**
 * Multi-band Native Audio Processing Engine.
 * Manages 5-band parametric biquad EQ, resonant sub-bass shelf,
 * dynamic auto-headroom compensation, stereo spatializer, and studio peak limiter.
 */
class EqualizerEngine {
public:
    EqualizerEngine();
    ~EqualizerEngine() = default;

    /**
     * Initializes or updates sample rate and recomputes all filter coefficients.
     */
    void setSampleRate(int sampleRate);

    /**
     * Sets the gain for a specific EQ band.
     * @param band Index from 0 to 4.
     * @param gainDb Gain in decibels, typically -15.0dB to +15.0dB.
     */
    void setBandGain(int band, float gainDb);

    /**
     * Sets the Bass Boost strength.
     * @param strength Normalized value from 0.0f to 1.0f (or 0 to 1000).
     */
    void setBassBoostStrength(float strength);

    /**
     * Sets digital Volume Boost gain in decibels.
     * @param gainDb Gain in decibels (e.g. 0.0dB to +12.0dB).
     */
    void setVolumeBoost(float gainDb);

    /**
     * Enables or disables real-time Karaoke mode (center-channel vocal suppression).
     */
    void setKaraokeEnabled(bool enabled);

    /**
     * Sets the Virtualizer (Spatial Soundstage) strength.
     * @param strength Normalized value from 0.0f to 1.0f.
     */
    void setVirtualizerStrength(float strength);

    /**
     * Configures atmospheric reverb parameters for Slowed + Reverb.
     * @param enabled Enable or bypass reverb.
     * @param roomSize Room size (0.0f to 1.0f).
     * @param damping High-frequency absorption (0.0f to 1.0f).
     * @param wetLevel Wet signal mix level (0.0f to 1.0f).
     */
    void setReverbParams(bool enabled, float roomSize, float damping, float wetLevel);

    /**
     * Enables or bypasses the equalizer processing.
     */
    void setEnabled(bool enabled);

    /**
     * Clears filter delay lines to prevent pops when seeking or transitioning tracks.
     */
    void reset();

    /**
     * Processes interleaved stereo 32-bit floating point audio in place.
     * @param buffer Array of interleaved samples [L, R, L, R, ...]
     * @param numFrames Number of stereo frames (total floats = numFrames * 2)
     */
    void process(float* buffer, int numFrames);

    /**
     * Processes interleaved stereo 16-bit PCM audio in place.
     * @param buffer Array of interleaved 16-bit samples [L, R, L, R, ...]
     * @param numFrames Number of stereo frames
     */
    void process(int16_t* buffer, int numFrames);

    /**
     * Retrieves the latest normalized 32-band spectrum magnitude array.
     */
    void getVisualizerBins(float* outBins, int numBins);

private:
    void updateFilters_locked();
    void updateHeadroom_locked();

    int mSampleRate = 44100;
    bool mEnabled = true;

    // Filter frequencies for the 5 bands
    const float mBandFrequencies[NUM_EQ_BANDS] = { 60.0f, 230.0f, 910.0f, 3600.0f, 14000.0f };
    const float mBandQ[NUM_EQ_BANDS] = { 1.1f, 1.0f, 1.0f, 1.0f, 1.1f };

    float mBandGains[NUM_EQ_BANDS] = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };
    float mBassBoostGainDb = 0.0f;
    float mVolumeBoostDb = 0.0f;
    float mVirtualizerStrength = 0.0f;
    bool mKaraokeEnabled = false;

    // Dynamic Headroom Compensation
    float mTargetHeadroomLinear = 1.0f;
    float mCurrentHeadroomLinear = 1.0f;

    // Stereo Biquad Filters for the 5 bands
    BiquadFilter mEqFiltersL[NUM_EQ_BANDS];
    BiquadFilter mEqFiltersR[NUM_EQ_BANDS];

    // Stereo Bass Boost Low-Shelf Filters
    BiquadFilter mBassFilterL;
    BiquadFilter mBassFilterR;

    // Real-Time Karaoke Dual-Crossover Vocal Suppression Filters (Sub-Bass & High-End Air)
    BiquadFilter mKaraokeFilterMidLP;
    BiquadFilter mKaraokeFilterMidHP;

    // Output Coupled Stereo Limiter
    Limiter mLimiter;

    // Spatializer delay line history
    float mPrevLeft = 0.0f;
    float mPrevRight = 0.0f;

    // Atmospheric Algorithmic Reverb (Freeverb)
    Freeverb mReverb;

    // Real-Time 60 FPS FFT Spectrum Analyzer
    FftProcessor mFftProcessor;
    std::vector<float> mMonoBuffer;
    std::vector<float> mTempFloatBuffer;

    std::mutex mMutex;
};

} // namespace musicdeck

#endif // MUSICDECK_EQUALIZER_ENGINE_H
