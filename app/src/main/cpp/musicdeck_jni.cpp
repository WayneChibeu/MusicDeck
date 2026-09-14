/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - JNI Direct Buffer Bridge.
 */

#include <jni.h>
#include <android/log.h>
#include <memory>
#include "dsp/EqualizerEngine.h"

#define TAG "MusicDeckJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static constexpr int MAX_ENGINES = 2;
static std::unique_ptr<musicdeck::EqualizerEngine> sEngines[MAX_ENGINES];

extern "C" {

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeInit(JNIEnv* env, jobject thiz) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (!sEngines[i]) {
            sEngines[i] = std::make_unique<musicdeck::EqualizerEngine>();
        }
    }
    LOGI("Native Audio Engine initialized successfully (%d instances)", MAX_ENGINES);
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetSampleRate(JNIEnv* env, jobject thiz, jint sampleRate) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setSampleRate(sampleRate);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetBandGain(JNIEnv* env, jobject thiz, jint band, jfloat gainDb) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setBandGain(band, gainDb);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetBassBoost(JNIEnv* env, jobject thiz, jfloat strength) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setBassBoostStrength(strength);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetVolumeBoost(JNIEnv* env, jobject thiz, jfloat gainDb) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setVolumeBoost(gainDb);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetVirtualizer(JNIEnv* env, jobject thiz, jfloat strength) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setVirtualizerStrength(strength);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetKaraokeEnabled(JNIEnv* env, jobject thiz, jboolean enabled) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setKaraokeEnabled(enabled);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetEnabled(JNIEnv* env, jobject thiz, jboolean enabled) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        if (sEngines[i]) sEngines[i]->setEnabled(enabled);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeReset(JNIEnv* env, jobject thiz, jint engineId) {
    if (engineId >= 0 && engineId < MAX_ENGINES) {
        if (sEngines[engineId]) sEngines[engineId]->reset();
    } else {
        for (int i = 0; i < MAX_ENGINES; ++i) {
            if (sEngines[i]) sEngines[i]->reset();
        }
    }
}

/**
 * Zero-copy in-place audio frame processing via Direct ByteBuffer.
 * @param engineId 0 for primary player, 1 for secondary crossfade player
 * @param byteBuffer Direct buffer allocated by Media3 audio processor
 * @param offset Byte offset
 * @param length Total bytes to process
 * @param encoding 2 = ENCODING_PCM_16BIT, 4 = ENCODING_PCM_FLOAT
 */
JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeProcessBuffer(
        JNIEnv* env,
        jobject thiz,
        jint engineId,
        jobject byteBuffer,
        jint offset,
        jint length,
        jint encoding) {
    int idx = (engineId >= 0 && engineId < MAX_ENGINES) ? engineId : 0;
    if (!sEngines[idx]) return;

    void* rawAddress = env->GetDirectBufferAddress(byteBuffer);
    if (!rawAddress) {
        LOGE("Failed to get DirectBufferAddress");
        return;
    }

    uint8_t* ptr = static_cast<uint8_t*>(rawAddress) + offset;

    if (encoding == 4) {
        // ENCODING_PCM_FLOAT (32-bit float, 4 bytes per sample, 2 channels = 8 bytes per frame)
        float* floatPtr = reinterpret_cast<float*>(ptr);
        int numFrames = length / (sizeof(float) * 2);
        sEngines[idx]->process(floatPtr, numFrames);
    } else {
        // ENCODING_PCM_16BIT (16-bit PCM, 2 bytes per sample, 2 channels = 4 bytes per frame)
        int16_t* int16Ptr = reinterpret_cast<int16_t*>(ptr);
        int numFrames = length / (sizeof(int16_t) * 2);
        sEngines[idx]->process(int16Ptr, numFrames);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeGetVisualizerBins(
        JNIEnv* env,
        jobject thiz,
        jfloatArray outArray) {
    if (!outArray) return;
    jsize len = env->GetArrayLength(outArray);
    if (len <= 0) return;

    float tempBinsA[musicdeck::NUM_SPECTRUM_BANDS] = { 0.0f };
    float tempBinsB[musicdeck::NUM_SPECTRUM_BANDS] = { 0.0f };

    if (sEngines[0]) sEngines[0]->getVisualizerBins(tempBinsA, musicdeck::NUM_SPECTRUM_BANDS);
    if (sEngines[1]) sEngines[1]->getVisualizerBins(tempBinsB, musicdeck::NUM_SPECTRUM_BANDS);

    float combined[musicdeck::NUM_SPECTRUM_BANDS];
    int count = std::min(static_cast<int>(len), musicdeck::NUM_SPECTRUM_BANDS);
    for (int i = 0; i < count; ++i) {
        combined[i] = std::max(tempBinsA[i], tempBinsB[i]);
    }

    env->SetFloatArrayRegion(outArray, 0, count, combined);
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeRelease(JNIEnv* env, jobject thiz) {
    for (int i = 0; i < MAX_ENGINES; ++i) {
        sEngines[i].reset();
    }
    LOGI("Native Audio Engines released");
}

} // extern "C"

