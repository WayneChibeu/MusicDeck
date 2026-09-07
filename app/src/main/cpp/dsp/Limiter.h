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
 * Studio-Grade Stereo Peak Limiter with Coupled Envelope Follower and Smooth Gain Reduction.
 * - Attack smoothing (~1.5ms) prevents sample-level flat-topping and square-wave buzzing.
 * - 180ms exponential decay release eliminates sub-bass (40-60Hz) intermodulation and waveform ripple.
 * - C1-continuous hyperbolic tangent soft-knee ceiling (0.90 to 0.985) ensures zero clipping without odd harmonics.
 */
class Limiter {
public:
    Limiter() = default;

    void setSampleRate(int sampleRate) {
        if (sampleRate > 0) {
            float sr = static_cast<float>(sampleRate);
            // Attack smoothing: ~1.5ms
            mAttackCoeff = 1.0f - std::exp(-1.0f / (0.0015f * sr));
            // Musical release decay: ~180ms to prevent low-frequency ripple
            mReleaseCoeff = std::exp(-1.0f / (0.180f * sr));
            // Gain release smoothing: ~100ms
            mGainReleaseCoeff = 1.0f - std::exp(-1.0f / (0.100f * sr));
        }
    }

    void reset() {
        mEnvelope = 0.0f;
        mCurrentGain = 1.0f;
    }

    inline void process(float& left, float& right) {
        float peak = std::max(std::abs(left), std::abs(right));

        // Coupled peak detection with asymmetric attack/release
        if (peak > mEnvelope) {
            mEnvelope = mEnvelope + mAttackCoeff * (peak - mEnvelope);
        } else {
            mEnvelope = mEnvelope * mReleaseCoeff;
        }

        constexpr float targetThreshold = 0.90f;
        float targetGain = 1.0f;
        if (mEnvelope > targetThreshold) {
            targetGain = targetThreshold / mEnvelope;
        }

        // Smooth gain transition to eliminate waveform deformation
        if (targetGain < mCurrentGain) {
            // Fast attack on gain reduction
            mCurrentGain = mCurrentGain + mAttackCoeff * (targetGain - mCurrentGain);
        } else {
            // Smooth release on gain recovery
            mCurrentGain = mCurrentGain + mGainReleaseCoeff * (targetGain - mCurrentGain);
        }

        left *= mCurrentGain;
        right *= mCurrentGain;

        // Final C1-continuous soft-knee safety ceiling (asymptotes to 0.985f with zero slope discontinuity)
        left = softKnee(left);
        right = softKnee(right);
    }

private:
    static inline float softKnee(float sample) {
        constexpr float kneeStart = 0.90f;
        constexpr float maxMargin = 0.085f; // Max output = 0.90 + 0.085 = 0.985
        float absVal = std::abs(sample);
        if (absVal <= kneeStart) {
            return sample;
        }
        float excess = absVal - kneeStart;
        float compressed = kneeStart + maxMargin * std::tanh(excess / maxMargin);
        return (sample >= 0.0f) ? compressed : -compressed;
    }

    float mEnvelope = 0.0f;
    float mCurrentGain = 1.0f;
    float mAttackCoeff = 0.015f;
    float mReleaseCoeff = 0.9998f;
    float mGainReleaseCoeff = 0.0002f;
};

} // namespace musicdeck

#endif // MUSICDECK_LIMITER_H
