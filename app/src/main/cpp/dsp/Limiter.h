/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native C++ Audio Processing Pipeline.
 */

#ifndef MUSICDECK_LIMITER_H
#define MUSICDECK_LIMITER_H

#include <cmath>
#include <algorithm>

namespace musicdeck {

/**
 * Studio-Grade Soft-Knee Peak Limiter & Audio Safety Guard.
 * Transparently prevents digital clipping distortion when extreme EQ or Bass Boost is applied.
 * Below threshold (0.95), audio is 100% untouched bit-for-bit.
 * Above threshold, audio is smoothly compressed with an analog-modeled soft-knee curve.
 */
class Limiter {
public:
    Limiter() = default;

    inline float process(float sample) {
        constexpr float threshold = 0.95f;
        constexpr float range = 1.0f - threshold;

        float absVal = std::abs(sample);
        if (absVal <= threshold) {
            return sample;
        }

        // Soft-knee analog saturation compression
        float excess = (absVal - threshold) / range;
        float compressed = threshold + range * std::tanh(excess);
        
        // Restore sign and clamp to safe [-1.0f, +1.0f]
        float result = (sample >= 0.0f) ? compressed : -compressed;
        return std::max(-1.0f, std::min(1.0f, result));
    }
};

} // namespace musicdeck

#endif // MUSICDECK_LIMITER_H
