/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native C++ Audio Processing Pipeline.
 */

#include "EqualizerEngine.h"
#include <algorithm>
#include <cmath>

namespace musicdeck {

EqualizerEngine::EqualizerEngine() {
    updateFilters_locked();
    updateHeadroom_locked();
}

void EqualizerEngine::setSampleRate(int sampleRate) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (sampleRate > 0 && mSampleRate != sampleRate) {
        mSampleRate = sampleRate;
        mLimiter.setSampleRate(sampleRate);
        updateFilters_locked();
        reset();
    }
}

void EqualizerEngine::setBandGain(int band, float gainDb) {
    if (band < 0 || band >= NUM_EQ_BANDS) return;
    std::lock_guard<std::mutex> lock(mMutex);
    mBandGains[band] = gainDb;
    
    // Reconfigure only the modified band
    mEqFiltersL[band].configure(FilterType::PeakingEQ, mBandFrequencies[band], static_cast<float>(mSampleRate), gainDb, mBandQ[band]);
    mEqFiltersR[band].configure(FilterType::PeakingEQ, mBandFrequencies[band], static_cast<float>(mSampleRate), gainDb, mBandQ[band]);
    updateHeadroom_locked();
}

void EqualizerEngine::setBassBoostStrength(float strength) {
    std::lock_guard<std::mutex> lock(mMutex);
    // Strength is 0.0 to 1.0. Map to 0.0dB .. +12.0dB boost
    float clamped = std::max(0.0f, std::min(1.0f, strength));
    mBassBoostGainDb = clamped * 12.0f;

    // Musical warmth low-shelf at 80 Hz with Q = 0.85f
    mBassFilterL.configure(FilterType::LowShelf, 80.0f, static_cast<float>(mSampleRate), mBassBoostGainDb, 0.85f);
    mBassFilterR.configure(FilterType::LowShelf, 80.0f, static_cast<float>(mSampleRate), mBassBoostGainDb, 0.85f);
    updateHeadroom_locked();
}

void EqualizerEngine::setVolumeBoost(float gainDb) {
    std::lock_guard<std::mutex> lock(mMutex);
    mVolumeBoostDb = std::max(0.0f, std::min(12.0f, gainDb));
    updateHeadroom_locked();
}

void EqualizerEngine::setVirtualizerStrength(float strength) {
    std::lock_guard<std::mutex> lock(mMutex);
    mVirtualizerStrength = std::max(0.0f, std::min(1.0f, strength));
}

void EqualizerEngine::setEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(mMutex);
    mEnabled = enabled;
}

void EqualizerEngine::reset() {
    for (int i = 0; i < NUM_EQ_BANDS; ++i) {
        mEqFiltersL[i].reset();
        mEqFiltersR[i].reset();
    }
    mBassFilterL.reset();
    mBassFilterR.reset();
    mLimiter.reset();
    mCurrentHeadroomLinear = mTargetHeadroomLinear;
    mPrevLeft = 0.0f;
    mPrevRight = 0.0f;
}

void EqualizerEngine::updateFilters_locked() {
    float sr = static_cast<float>(mSampleRate);
    for (int i = 0; i < NUM_EQ_BANDS; ++i) {
        mEqFiltersL[i].configure(FilterType::PeakingEQ, mBandFrequencies[i], sr, mBandGains[i], mBandQ[i]);
        mEqFiltersR[i].configure(FilterType::PeakingEQ, mBandFrequencies[i], sr, mBandGains[i], mBandQ[i]);
    }
    mBassFilterL.configure(FilterType::LowShelf, 80.0f, sr, mBassBoostGainDb, 0.85f);
    mBassFilterR.configure(FilterType::LowShelf, 80.0f, sr, mBassBoostGainDb, 0.85f);
}

void EqualizerEngine::updateHeadroom_locked() {
    float maxBoostDb = 0.0f;
    for (int i = 0; i < NUM_EQ_BANDS; ++i) {
        if (mBandGains[i] > maxBoostDb) {
            maxBoostDb = mBandGains[i];
        }
    }
    if (mBassBoostGainDb > maxBoostDb) {
        maxBoostDb = mBassBoostGainDb;
    }

    // Auto-headroom trim: -0.6 * maxBoostDb prevents filter saturation while preserving perceived punch
    float trimDb = (maxBoostDb > 0.05f) ? (-0.6f * maxBoostDb) : 0.0f;
    float netDb = trimDb + mVolumeBoostDb;

    mTargetHeadroomLinear = std::pow(10.0f, netDb / 20.0f);
}

void EqualizerEngine::process(float* buffer, int numFrames) {
    if (!mEnabled || buffer == nullptr || numFrames <= 0) return;

    std::lock_guard<std::mutex> lock(mMutex);

    for (int i = 0; i < numFrames; ++i) {
        // Smooth headroom slew to prevent slider zipper noise
        mCurrentHeadroomLinear += 0.002f * (mTargetHeadroomLinear - mCurrentHeadroomLinear);

        float left = buffer[i * 2] * mCurrentHeadroomLinear;
        float right = buffer[i * 2 + 1] * mCurrentHeadroomLinear;

        // 1. Spatializer / Virtualizer (Interaural crossfeed & subtle phase widening)
        if (mVirtualizerStrength > 0.001f) {
            float diff = (left - right) * (mVirtualizerStrength * 0.35f);
            left += diff;
            right -= diff;
        }

        // 2. Resonant Bass Boost (Low-shelf)
        if (mBassBoostGainDb > 0.05f) {
            left = mBassFilterL.process(left);
            right = mBassFilterR.process(right);
        }

        // 3. Multi-Band Parametric Equalizer (5 bands cascaded)
        for (int b = 0; b < NUM_EQ_BANDS; ++b) {
            left = mEqFiltersL[b].process(left);
            right = mEqFiltersR[b].process(right);
        }

        // 4. Studio Coupled Peak Limiter (Prevents all digital clipping transparently)
        mLimiter.process(left, right);

        buffer[i * 2] = left;
        buffer[i * 2 + 1] = right;
    }
}

void EqualizerEngine::process(int16_t* buffer, int numFrames) {
    if (!mEnabled || buffer == nullptr || numFrames <= 0) return;

    constexpr float int16ToFloat = 1.0f / 32768.0f;
    constexpr float floatToInt16 = 32767.0f;

    std::lock_guard<std::mutex> lock(mMutex);

    for (int i = 0; i < numFrames; ++i) {
        mCurrentHeadroomLinear += 0.002f * (mTargetHeadroomLinear - mCurrentHeadroomLinear);

        float left = (static_cast<float>(buffer[i * 2]) * int16ToFloat) * mCurrentHeadroomLinear;
        float right = (static_cast<float>(buffer[i * 2 + 1]) * int16ToFloat) * mCurrentHeadroomLinear;

        // 1. Spatializer / Virtualizer
        if (mVirtualizerStrength > 0.001f) {
            float diff = (left - right) * (mVirtualizerStrength * 0.35f);
            left += diff;
            right -= diff;
        }

        // 2. Resonant Bass Boost
        if (mBassBoostGainDb > 0.05f) {
            left = mBassFilterL.process(left);
            right = mBassFilterR.process(right);
        }

        // 3. Multi-Band Parametric Equalizer
        for (int b = 0; b < NUM_EQ_BANDS; ++b) {
            left = mEqFiltersL[b].process(left);
            right = mEqFiltersR[b].process(right);
        }

        // 4. Studio Coupled Peak Limiter
        mLimiter.process(left, right);

        // 5. Convert back to int16 with safety clamping
        int32_t outL = static_cast<int32_t>(left * floatToInt16);
        int32_t outR = static_cast<int32_t>(right * floatToInt16);

        buffer[i * 2] = static_cast<int16_t>(std::max(-32768, std::min(32767, outL)));
        buffer[i * 2 + 1] = static_cast<int16_t>(std::max(-32768, std::min(32767, outR)));
    }
}

} // namespace musicdeck
