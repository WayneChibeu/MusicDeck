/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Studio-Grade Schroeder-Freeverb Algorithmic Reverb.
 */

#ifndef MUSICDECK_FREEVERB_H
#define MUSICDECK_FREEVERB_H

#include <vector>
#include <cmath>
#include <algorithm>

namespace musicdeck {

// Tuning constants scaled for 44.1kHz base sample rate
constexpr int NUM_COMBS = 8;
constexpr int NUM_ALLPASS = 4;
constexpr int STEREO_SPREAD = 23;

constexpr int COMB_TUNING_L[NUM_COMBS] = { 1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617 };
constexpr int ALLPASS_TUNING_L[NUM_ALLPASS] = { 556, 441, 341, 225 };

/**
 * Single-channel feedback Comb filter with internal low-pass damping.
 */
class CombFilter {
public:
    CombFilter() = default;

    void setBufferSize(int size) {
        mBuffer.assign(size > 0 ? size : 1, 0.0f);
        mBufferSize = static_cast<int>(mBuffer.size());
        mBufferIndex = 0;
        mFilterStore = 0.0f;
    }

    void reset() {
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
        mBufferIndex = 0;
        mFilterStore = 0.0f;
    }

    inline float process(float input, float feedback, float damp) {
        float output = mBuffer[mBufferIndex];
        // Anti-denormal protection
        mFilterStore = (output * (1.0f - damp)) + (mFilterStore * damp);
        mBuffer[mBufferIndex] = input + (mFilterStore * feedback);
        if (++mBufferIndex >= mBufferSize) {
            mBufferIndex = 0;
        }
        return output;
    }

private:
    std::vector<float> mBuffer;
    int mBufferSize = 1;
    int mBufferIndex = 0;
    float mFilterStore = 0.0f;
};

/**
 * Single-channel Schroeder All-pass filter for diffusion.
 */
class AllpassFilter {
public:
    AllpassFilter() = default;

    void setBufferSize(int size) {
        mBuffer.assign(size > 0 ? size : 1, 0.0f);
        mBufferSize = static_cast<int>(mBuffer.size());
        mBufferIndex = 0;
    }

    void reset() {
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
        mBufferIndex = 0;
    }

    inline float process(float input) {
        float bufOut = mBuffer[mBufferIndex];
        float feedback = 0.5f;
        mBuffer[mBufferIndex] = input + (bufOut * feedback);
        if (++mBufferIndex >= mBufferSize) {
            mBufferIndex = 0;
        }
        return -input + bufOut;
    }

private:
    std::vector<float> mBuffer;
    int mBufferSize = 1;
    int mBufferIndex = 0;
};

/**
 * Studio-grade stereo Schroeder-Freeverb algorithmic reverberator.
 * Delivers lush, spacious atmospheric reverberation for Slowed + Reverb.
 */
class Freeverb {
public:
    Freeverb() {
        setSampleRate(44100);
        updateParameters();
    }

    void setSampleRate(int sampleRate) {
        if (sampleRate <= 0) return;
        mSampleRate = sampleRate;
        float scale = static_cast<float>(sampleRate) / 44100.0f;

        for (int i = 0; i < NUM_COMBS; ++i) {
            int sizeL = static_cast<int>(COMB_TUNING_L[i] * scale);
            int sizeR = static_cast<int>((COMB_TUNING_L[i] + STEREO_SPREAD) * scale);
            mCombsL[i].setBufferSize(sizeL);
            mCombsR[i].setBufferSize(sizeR);
        }

        for (int i = 0; i < NUM_ALLPASS; ++i) {
            int sizeL = static_cast<int>(ALLPASS_TUNING_L[i] * scale);
            int sizeR = static_cast<int>((ALLPASS_TUNING_L[i] + STEREO_SPREAD) * scale);
            mAllpassesL[i].setBufferSize(sizeL);
            mAllpassesR[i].setBufferSize(sizeR);
        }
        updateLowCut();
        reset();
    }

    void setEnabled(bool enabled) {
        mEnabled = enabled;
        if (!enabled) reset();
    }

    bool isEnabled() const {
        return mEnabled && (mWetLevel > 0.001f);
    }

    /**
     * Sets reverb parameters.
     * @param roomSize Range 0.0f to 1.0f (mapped to feedback 0.7 .. 0.98)
     * @param damping Range 0.0f to 1.0f (HF absorption)
     * @param wetLevel Range 0.0f to 1.0f (wet signal mix)
     */
    void setParams(float roomSize, float damping, float wetLevel) {
        mRoomSize = std::max(0.0f, std::min(1.0f, roomSize));
        mDamping = std::max(0.0f, std::min(1.0f, damping));
        mWetLevel = std::max(0.0f, std::min(1.0f, wetLevel));
        updateParameters();
    }

    void reset() {
        mHpPrevX = 0.0f;
        mHpPrevY = 0.0f;
        for (int i = 0; i < NUM_COMBS; ++i) {
            mCombsL[i].reset();
            mCombsR[i].reset();
        }
        for (int i = 0; i < NUM_ALLPASS; ++i) {
            mAllpassesL[i].reset();
            mAllpassesR[i].reset();
        }
    }

    /**
     * Processes a single stereo frame in place.
     */
    inline void processSample(float& left, float& right) {
        if (!isEnabled()) return;

        // Input attenuation and 160 Hz Low-Cut (High-Pass) filter to strip sub-bass 808
        // frequencies from the reverb tank, completely eliminating low-end gurgling/beating.
        float rawMono = (left + right) * 0.015f;
        float inMono = mHpAlpha * (mHpPrevY + rawMono - mHpPrevX);
        mHpPrevX = rawMono;
        mHpPrevY = inMono;

        float outL = 0.0f;
        float outR = 0.0f;

        // Parallel Comb Filters
        for (int c = 0; c < NUM_COMBS; ++c) {
            outL += mCombsL[c].process(inMono, mFeedbackVal, mDampVal);
            outR += mCombsR[c].process(inMono, mFeedbackVal, mDampVal);
        }

        // Cascaded All-pass Diffusion Filters
        for (int a = 0; a < NUM_ALLPASS; ++a) {
            outL = mAllpassesL[a].process(outL);
            outR = mAllpassesR[a].process(outR);
        }

        float inL = left;
        float inR = right;
        left = (inL * mDry) + (outL * mWet1) + (outR * mWet2);
        right = (inR * mDry) + (outR * mWet1) + (outL * mWet2);
    }

    /**
     * Processes interleaved stereo audio in place.
     * [L, R, L, R, ...]
     */
    void process(float* buffer, int numFrames) {
        if (!isEnabled() || buffer == nullptr) return;

        for (int i = 0; i < numFrames; ++i) {
            processSample(buffer[i * 2], buffer[i * 2 + 1]);
        }
    }

private:
    void updateParameters() {
        mFeedbackVal = 0.70f + (mRoomSize * 0.28f); // 0.70 to 0.98 feedback
        mDampVal = mDamping * 0.45f;
        float wet = mWetLevel * 0.45f; // Scale to maintain headroom
        mDry = 1.0f - (mWetLevel * 0.25f);
        float width = 1.0f; // Full stereo width
        mWet1 = wet * (width / 2.0f + 0.5f);
        mWet2 = wet * ((1.0f - width) / 2.0f);
    }

    void updateLowCut() {
        // 1st-order High-Pass (Low-Cut) filter at fc = 160.0 Hz
        // alpha = rc / (rc + dt) where rc = 1.0 / (2 * pi * fc) and dt = 1.0 / fs
        float fc = 160.0f;
        float dt = 1.0f / static_cast<float>(mSampleRate > 0 ? mSampleRate : 44100);
        float rc = 1.0f / (2.0f * 3.14159265358979323846f * fc);
        mHpAlpha = rc / (rc + dt);
    }

    int mSampleRate = 44100;
    bool mEnabled = false;
    float mRoomSize = 0.75f;
    float mDamping = 0.40f;
    float mWetLevel = 0.0f;

    float mFeedbackVal = 0.85f;
    float mDampVal = 0.20f;
    float mDry = 1.0f;
    float mWet1 = 0.0f;
    float mWet2 = 0.0f;

    float mHpAlpha = 0.97f;
    float mHpPrevX = 0.0f;
    float mHpPrevY = 0.0f;

    CombFilter mCombsL[NUM_COMBS];
    CombFilter mCombsR[NUM_COMBS];
    AllpassFilter mAllpassesL[NUM_ALLPASS];
    AllpassFilter mAllpassesR[NUM_ALLPASS];
};

} // namespace musicdeck

#endif // MUSICDECK_FREEVERB_H
