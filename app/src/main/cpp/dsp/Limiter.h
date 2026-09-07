/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Studio Peak Limiter.
 */

#ifndef MUSICDECK_LIMITER_H
#define MUSICDECK_LIMITER_H

#include <cmath>
#include <algorithm>

namespace musicdeck {

/**
 * Studio-Grade Peak Limiter with Envelope Follower.
 * Uses instantaneous attack and smooth exponential release decay to scale entire waveform cycles.
 * Prevents digital clipping without waveshaping distortion, odd harmonics, or buzzing artifacts.
 */
class Limiter {
public:
    Limiter() = default;

    void setSampleRate(int sampleRate) {
        if (sampleRate > 0) {
            // Smooth release decay (~80ms release time)
            mReleaseCoeff = std::exp(-1.0f / (0.080f * static_cast<float>(sampleRate)));
        }
    }

    void reset() {
        mEnvelope = 0.0f;
    }

    inline float process(float sample) {
        float absSample = std::abs(sample);
        
        // Instant peak detection (0 attack time to catch every transient)
        if (absSample > mEnvelope) {
            mEnvelope = absSample;
        } else {
            // Smooth exponential release decay to preserve pure waveforms
            mEnvelope = mEnvelope * mReleaseCoeff;
        }

        constexpr float maxThreshold = 0.98f;
        if (mEnvelope > maxThreshold) {
            float gain = maxThreshold / mEnvelope;
            return sample * gain;
        }

        return sample;
    }

private:
    float mEnvelope = 0.0f;
    float mReleaseCoeff = 0.9997f; // Approx 80ms at 44.1kHz
};

} // namespace musicdeck

#endif // MUSICDECK_LIMITER_H
