/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native Radix-2 FFT Spectrum Analyzer.
 */

#ifndef MUSICDECK_FFT_PROCESSOR_H
#define MUSICDECK_FFT_PROCESSOR_H

#include <cmath>
#include <vector>
#include <complex>
#include <mutex>
#include <algorithm>

namespace musicdeck {

constexpr int FFT_SIZE = 512;
constexpr int NUM_SPECTRUM_BANDS = 32;

/**
 * High-performance Radix-2 Cooley-Tukey FFT with Hann windowing,
 * logarithmic perceptual band mapping, and ballistic temporal smoothing.
 */
class FftProcessor {
public:
    FftProcessor() {
        // Precompute Hann window and bit-reversal lookup
        for (int i = 0; i < FFT_SIZE; ++i) {
            mWindow[i] = 0.5f * (1.0f - std::cos(2.0f * static_cast<float>(M_PI) * static_cast<float>(i) / static_cast<float>(FFT_SIZE - 1)));
            mBitRev[i] = reverseBits(i, FFT_LOG2);
        }

        for (int i = 0; i < NUM_SPECTRUM_BANDS; ++i) {
            mSmoothedBands[i] = 0.0f;
            mExportBands[i] = 0.0f;
        }

        setupLogFrequencyBins();
    }

    /**
     * Pushes mono audio samples into the circular FFT buffer.
     * Triggers FFT calculation with 50% overlap for fluid 60-120 FPS tracking.
     */
    void pushSamples(const float* monoSamples, int numSamples) {
        for (int i = 0; i < numSamples; ++i) {
            mInputBuffer[mInputPos] = monoSamples[i];
            mInputPos = (mInputPos + 1) % FFT_SIZE;
            mHopCounter++;

            // Process every FFT_SIZE / 2 samples (50% overlap: ~170 FPS at 44.1kHz)
            if (mHopCounter >= FFT_HOP) {
                mHopCounter = 0;
                executeFft();
            }
        }
    }

    /**
     * Thread-safe snapshot of the latest 32 normalized spectrum bands [0.0f .. 1.0f].
     */
    void getBands(float* outBands, int numBands) {
        int count = std::min(numBands, NUM_SPECTRUM_BANDS);
        std::lock_guard<std::mutex> lock(mMutex);
        for (int i = 0; i < count; ++i) {
            outBands[i] = mExportBands[i];
        }
    }

    /**
     * Resets visualizer state and clears all frequency bins.
     */
    void reset() {
        std::lock_guard<std::mutex> lock(mMutex);
        mInputPos = 0;
        mHopCounter = 0;
        for (int i = 0; i < NUM_SPECTRUM_BANDS; ++i) {
            mSmoothedBands[i] = 0.0f;
            mExportBands[i] = 0.0f;
        }
    }

private:
    static constexpr int FFT_LOG2 = 9; // 2^9 = 512
    static constexpr int FFT_HOP = 256; // 50% overlap

    float mInputBuffer[FFT_SIZE] = { 0.0f };
    int mInputPos = 0;
    int mHopCounter = 0;

    float mWindow[FFT_SIZE];
    int mBitRev[FFT_SIZE];

    // Logarithmic bin start and end index mappings
    int mBinStart[NUM_SPECTRUM_BANDS];
    int mBinEnd[NUM_SPECTRUM_BANDS];

    float mSmoothedBands[NUM_SPECTRUM_BANDS];
    float mExportBands[NUM_SPECTRUM_BANDS];
    std::mutex mMutex;

    static int reverseBits(int x, int bits) {
        int y = 0;
        for (int i = 0; i < bits; ++i) {
            y = (y << 1) | (x & 1);
            x >>= 1;
        }
        return y;
    }

    void setupLogFrequencyBins() {
        // Human hearing is logarithmic (30 Hz to 16 kHz)
        // At 44.1kHz with 512 FFT size, bin width = 44100 / 512 = 86.13 Hz
        // Total usable bins = 256
        const float minFreq = 30.0f;
        const float maxFreq = 16000.0f;
        const float sampleRate = 44100.0f;
        const float binWidth = sampleRate / static_cast<float>(FFT_SIZE);

        for (int i = 0; i < NUM_SPECTRUM_BANDS; ++i) {
            float fStart = minFreq * std::pow(maxFreq / minFreq, static_cast<float>(i) / static_cast<float>(NUM_SPECTRUM_BANDS));
            float fEnd = minFreq * std::pow(maxFreq / minFreq, static_cast<float>(i + 1) / static_cast<float>(NUM_SPECTRUM_BANDS));

            int bStart = static_cast<int>(std::floor(fStart / binWidth));
            int bEnd = static_cast<int>(std::ceil(fEnd / binWidth));

            if (bStart < 1) bStart = 1; // skip DC offset bin 0
            if (bEnd <= bStart) bEnd = bStart + 1;
            if (bEnd > (FFT_SIZE / 2)) bEnd = FFT_SIZE / 2;

            mBinStart[i] = bStart;
            mBinEnd[i] = bEnd;
        }
    }

    void executeFft() {
        // 1. Copy circular buffer with Hann window into bit-reversed complex array
        float real[FFT_SIZE];
        float imag[FFT_SIZE];

        int readIdx = mInputPos;
        for (int i = 0; i < FFT_SIZE; ++i) {
            int rev = mBitRev[i];
            real[rev] = mInputBuffer[readIdx] * mWindow[i];
            imag[rev] = 0.0f;
            readIdx = (readIdx + 1) % FFT_SIZE;
        }

        // 2. In-place Radix-2 Cooley-Tukey butterfly
        for (int s = 1; s <= FFT_LOG2; ++s) {
            int m = 1 << s;
            int halfM = m >> 1;
            float theta = -2.0f * static_cast<float>(M_PI) / static_cast<float>(m);
            float wRealStep = std::cos(theta);
            float wImagStep = std::sin(theta);

            for (int k = 0; k < FFT_SIZE; k += m) {
                float wReal = 1.0f;
                float wImag = 0.0f;

                for (int j = 0; j < halfM; ++j) {
                    int uIdx = k + j;
                    int tIdx = uIdx + halfM;

                    float tReal = wReal * real[tIdx] - wImag * imag[tIdx];
                    float tImag = wReal * imag[tIdx] + wImag * real[tIdx];

                    real[tIdx] = real[uIdx] - tReal;
                    imag[tIdx] = imag[uIdx] - tImag;
                    real[uIdx] += tReal;
                    imag[uIdx] += tImag;

                    float nextWReal = wReal * wRealStep - wImag * wImagStep;
                    float nextWImag = wReal * wImagStep + wImag * wRealStep;
                    wReal = nextWReal;
                    wImag = nextWImag;
                }
            }
        }

        // 3. Compute magnitude spectrum for the 32 logarithmic bands
        float currentBands[NUM_SPECTRUM_BANDS];

        for (int band = 0; band < NUM_SPECTRUM_BANDS; ++band) {
            float sumMag = 0.0f;
            int count = 0;

            for (int k = mBinStart[band]; k < mBinEnd[band]; ++k) {
                float mag = std::sqrt(real[k] * real[k] + imag[k] * imag[k]);
                sumMag += mag;
                count++;
            }

            float avgMag = (count > 0) ? (sumMag / static_cast<float>(count)) : 0.0f;

            // Logarithmic dynamic range mapping (-45 dB to 0 dB mapped to [0.0, 1.0])
            // Standardizing normalization against unit amplitude window sum
            constexpr float normFactor = 4.0f / static_cast<float>(FFT_SIZE);
            float normalized = avgMag * normFactor;

            // Frequency weighting curve: boost high frequencies slightly to compensate for 1/f pink noise rolloff
            float freqWeight = 1.0f + 0.04f * static_cast<float>(band);
            normalized *= freqWeight;

            // Soft non-linear compression for visually vibrant movements
            float db = 20.0f * std::log10(std::max(normalized, 0.005f)); // -46dB floor
            float val = (db + 46.0f) / 46.0f;
            currentBands[band] = std::clamp(val, 0.0f, 1.0f);
        }

        // 4. Ballistics (Fast Attack, Smooth Decay)
        std::lock_guard<std::mutex> lock(mMutex);
        for (int band = 0; band < NUM_SPECTRUM_BANDS; ++band) {
            float target = currentBands[band];
            if (target > mSmoothedBands[band]) {
                // Fast Attack (instant visual punch on beat hit)
                mSmoothedBands[band] += 0.65f * (target - mSmoothedBands[band]);
            } else {
                // Smooth exponential decay (gravity fall)
                mSmoothedBands[band] += 0.12f * (target - mSmoothedBands[band]);
            }
            mExportBands[band] = mSmoothedBands[band];
        }
    }
};

} // namespace musicdeck

#endif // MUSICDECK_FFT_PROCESSOR_H
