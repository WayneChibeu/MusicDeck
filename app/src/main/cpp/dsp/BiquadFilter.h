/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native C++ Audio Processing Pipeline.
 */

#ifndef MUSICDECK_BIQUAD_FILTER_H
#define MUSICDECK_BIQUAD_FILTER_H

#define _USE_MATH_DEFINES
#include <cmath>

namespace musicdeck {

enum class FilterType {
    PeakingEQ,
    LowShelf,
    HighShelf,
    LowPass,
    HighPass
};

/**
 * High-performance 2nd-order Biquadratic IIR Filter (Direct Form II Transposed).
 * Computes audio sample-by-sample with 64-bit double precision coefficients
 * and 32-bit floating point processing to avoid digital truncation noise.
 */
class BiquadFilter {
public:
    BiquadFilter() = default;

    /**
     * Recalculates filter coefficients using Robert Bristow-Johnson (RBJ) Cookbook equations.
     * @param type The filter type (Peaking, LowShelf, etc.)
     * @param centerFreq Center or cutoff frequency in Hertz (e.g. 60Hz, 1000Hz)
     * @param sampleRate Output sampling rate in Hertz (e.g. 44100Hz, 48000Hz)
     * @param gainDb Gain in decibels (-15.0dB to +15.0dB)
     * @param q Quality factor (resonance width, default ~1.0f)
     */
    void configure(FilterType type, float centerFreq, float sampleRate, float gainDb, float q = 1.0f) {
        if (sampleRate <= 0.0f || centerFreq <= 0.0f) return;

        // Nyquist limit protection: center frequency cannot exceed half the sample rate
        if (centerFreq >= sampleRate * 0.49f) {
            centerFreq = sampleRate * 0.49f;
        }

        double A = std::pow(10.0, gainDb / 40.0);
        double omega = 2.0 * M_PI * centerFreq / sampleRate;
        double sinOmega = std::sin(omega);
        double cosOmega = std::cos(omega);
        double alpha = sinOmega / (2.0 * (q > 0.01f ? q : 0.01f));

        double b0 = 1.0, b1 = 0.0, b2 = 0.0;
        double a0 = 1.0, a1 = 0.0, a2 = 0.0;

        switch (type) {
            case FilterType::PeakingEQ: {
                b0 = 1.0 + alpha * A;
                b1 = -2.0 * cosOmega;
                b2 = 1.0 - alpha * A;
                a0 = 1.0 + alpha / A;
                a1 = -2.0 * cosOmega;
                a2 = 1.0 - alpha / A;
                break;
            }
            case FilterType::LowShelf: {
                double sqrtA = std::sqrt(A);
                double twoSqrtAAlpha = 2.0 * sqrtA * alpha;
                b0 = A * ((A + 1.0) - (A - 1.0) * cosOmega + twoSqrtAAlpha);
                b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosOmega);
                b2 = A * ((A + 1.0) - (A - 1.0) * cosOmega - twoSqrtAAlpha);
                a0 = (A + 1.0) + (A - 1.0) * cosOmega + twoSqrtAAlpha;
                a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosOmega);
                a2 = (A + 1.0) + (A - 1.0) * cosOmega - twoSqrtAAlpha;
                break;
            }
            case FilterType::HighShelf: {
                double sqrtA = std::sqrt(A);
                double twoSqrtAAlpha = 2.0 * sqrtA * alpha;
                b0 = A * ((A + 1.0) + (A - 1.0) * cosOmega + twoSqrtAAlpha);
                b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosOmega);
                b2 = A * ((A + 1.0) + (A - 1.0) * cosOmega - twoSqrtAAlpha);
                a0 = (A + 1.0) - (A - 1.0) * cosOmega + twoSqrtAAlpha;
                a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosOmega);
                a2 = (A + 1.0) - (A - 1.0) * cosOmega - twoSqrtAAlpha;
                break;
            }
            default:
                break;
        }

        // Normalize coefficients so a0 == 1.0
        mB0 = b0 / a0;
        mB1 = b1 / a0;
        mB2 = b2 / a0;
        mA1 = a1 / a0;
        mA2 = a2 / a0;
    }

    /**
     * Resets the internal delay memory (state) to zero.
     * Prevents clicks/pops when seeking or changing songs.
     */
    void reset() {
        mZ1 = 0.0;
        mZ2 = 0.0;
    }

    /**
     * Processes a single audio sample through the Direct Form II Transposed topology.
     * Computes state in 64-bit double precision to eliminate low-frequency quantization noise.
     */
    inline float process(float inSample) {
        double inD = static_cast<double>(inSample);
        double outD = inD * mB0 + mZ1;
        mZ1 = inD * mB1 - outD * mA1 + mZ2;
        mZ2 = inD * mB2 - outD * mA2;
        return static_cast<float>(outD);
    }

private:
    // Normalized 64-bit double precision filter coefficients
    double mB0 = 1.0;
    double mB1 = 0.0;
    double mB2 = 0.0;
    double mA1 = 0.0;
    double mA2 = 0.0;

    // Filter delay memory states (64-bit double precision)
    double mZ1 = 0.0;
    double mZ2 = 0.0;
};

} // namespace musicdeck

#endif // MUSICDECK_BIQUAD_FILTER_H
