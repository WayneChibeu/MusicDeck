/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Bauer Binaural Crossfeed (BS2B).
 *
 * Implements an audiophile head-related transfer function (HRTF) crossfeed filter
 * based on Benjamin Bauer's acoustic head-shadow model and Boris Mikhaylov's BS2B.
 * Relieves extreme headphone isolation and listener fatigue by recreating the natural
 * acoustic soundstage of nearfield studio monitor loudspeakers.
 */

#ifndef MUSICDECK_BAUER_CROSSFEED_H
#define MUSICDECK_BAUER_CROSSFEED_H

#include <cmath>
#include <algorithm>
#include <cstdint>

namespace musicdeck {

enum class CrossfeedPreset {
    SUBTLE_MEIER = 1,   // 650 Hz cutoff, -9.5 dB feed (Jan Meier curve: subtle, wide)
    STANDARD_BAUER = 2, // 700 Hz cutoff, -6.0 dB feed (Bauer BS2B: rock, jazz, classical)
    STUDIO_CHUMOY = 3   // 700 Hz cutoff, -4.5 dB feed (Chu Moy curve: full control room)
};

class BauerCrossfeed {
public:
    BauerCrossfeed() {
        computeCoefficients();
    }

    void setSampleRate(int sampleRate) {
        if (sampleRate > 8000 && sampleRate != mSampleRate) {
            mSampleRate = sampleRate;
            computeCoefficients();
            reset();
        }
    }

    void setStrength(float strength) {
        // Normalized 0.0f (bypassed) to 1.0f (full binaural crossfeed)
        mStrength = std::clamp(strength, 0.0f, 1.0f);
    }

    float getStrength() const {
        return mStrength;
    }

    void setPreset(CrossfeedPreset preset) {
        if (mPreset != preset) {
            mPreset = preset;
            computeCoefficients();
        }
    }

    void setPreset(int mode) {
        switch (mode) {
            case 1: setPreset(CrossfeedPreset::SUBTLE_MEIER); break;
            case 3: setPreset(CrossfeedPreset::STUDIO_CHUMOY); break;
            case 2:
            default: setPreset(CrossfeedPreset::STANDARD_BAUER); break;
        }
    }

    void reset() {
        mStateDirL = 0.0f;
        mStateDirR = 0.0f;
        mStateCrossL = 0.0f;
        mStateCrossR = 0.0f;
    }

    /**
     * Processes a single stereo pair in-place.
     */
    inline void processSample(float& left, float& right) {
        if (mStrength < 0.001f) return;

        const float inL = left;
        const float inR = right;

        // Direct path filtering (high-boost shelf)
        const float dirL = mB0Dir * inL + mStateDirL;
        mStateDirL = mB1Dir * inL + mA1 * dirL;

        const float dirR = mB0Dir * inR + mStateDirR;
        mStateDirR = mB1Dir * inR + mA1 * dirR;

        // Crossfeed path filtering (low-pass acoustic shadow)
        const float crossL = mB0Cross * inL + mStateCrossL;
        mStateCrossL = mB1Cross * inL + mA1 * crossL;

        const float crossR = mB0Cross * inR + mStateCrossR;
        mStateCrossR = mB1Cross * inR + mA1 * crossR;

        // Binaural synthesis: direct ear signal + acoustic shadow of opposite channel
        const float outL = dirL + crossR;
        const float outR = dirR + crossL;

        // Wet/Dry mix
        left = (1.0f - mStrength) * inL + mStrength * outL;
        right = (1.0f - mStrength) * inR + mStrength * outR;
    }

private:
    void computeCoefficients() {
        float fc = 700.0f;
        float gainDb = -6.0f;

        switch (mPreset) {
            case CrossfeedPreset::SUBTLE_MEIER:
                fc = 650.0f;
                gainDb = -9.5f;
                break;
            case CrossfeedPreset::STUDIO_CHUMOY:
                fc = 700.0f;
                gainDb = -4.5f;
                break;
            case CrossfeedPreset::STANDARD_BAUER:
            default:
                fc = 700.0f;
                gainDb = -6.0f;
                break;
        }

        const float g = std::pow(10.0f, gainDb / 20.0f);
        constexpr float pi = 3.14159265358979323846f;
        const float K = std::tan(pi * fc / static_cast<float>(mSampleRate));

        // Shared denominator pole
        mA1 = (1.0f - K) / (1.0f + K);

        // Direct path filter coefficients (high-boost compensation)
        mB0Dir = (1.0f + g * K) / (1.0f + K);
        mB1Dir = -(1.0f - g * K) / (1.0f + K);

        // Crossfeed path filter coefficients (low-pass acoustic head shadow)
        mB0Cross = (g * K) / (1.0f + K);
        mB1Cross = mB0Cross;
    }

    int mSampleRate = 48000;
    float mStrength = 0.0f;
    CrossfeedPreset mPreset = CrossfeedPreset::STANDARD_BAUER;

    // Filter coefficients
    float mA1 = 0.0f;
    float mB0Dir = 1.0f;
    float mB1Dir = 0.0f;
    float mB0Cross = 0.0f;
    float mB1Cross = 0.0f;

    // Filter history states (Transposed Direct Form II)
    float mStateDirL = 0.0f;
    float mStateDirR = 0.0f;
    float mStateCrossL = 0.0f;
    float mStateCrossR = 0.0f;
};

} // namespace musicdeck

#endif // MUSICDECK_BAUER_CROSSFEED_H
