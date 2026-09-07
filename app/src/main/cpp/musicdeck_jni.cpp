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

static std::unique_ptr<musicdeck::EqualizerEngine> sEngine;

extern "C" {

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeInit(JNIEnv* env, jobject thiz) {
    if (!sEngine) {
        sEngine = std::make_unique<musicdeck::EqualizerEngine>();
        LOGI("Native Audio Engine initialized successfully");
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetSampleRate(JNIEnv* env, jobject thiz, jint sampleRate) {
    if (sEngine) {
        sEngine->setSampleRate(sampleRate);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetBandGain(JNIEnv* env, jobject thiz, jint band, jfloat gainDb) {
    if (sEngine) {
        sEngine->setBandGain(band, gainDb);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetBassBoost(JNIEnv* env, jobject thiz, jfloat strength) {
    if (sEngine) {
        sEngine->setBassBoostStrength(strength);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetVirtualizer(JNIEnv* env, jobject thiz, jfloat strength) {
    if (sEngine) {
        sEngine->setVirtualizerStrength(strength);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeSetEnabled(JNIEnv* env, jobject thiz, jboolean enabled) {
    if (sEngine) {
        sEngine->setEnabled(enabled);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeReset(JNIEnv* env, jobject thiz) {
    if (sEngine) {
        sEngine->reset();
    }
}

/**
 * Zero-copy in-place audio frame processing via Direct ByteBuffer.
 * @param byteBuffer Direct buffer allocated by Media3 audio processor
 * @param offset Byte offset
 * @param length Total bytes to process
 * @param encoding 2 = ENCODING_PCM_16BIT, 4 = ENCODING_PCM_FLOAT
 */
JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeProcessBuffer(
        JNIEnv* env,
        jobject thiz,
        jobject byteBuffer,
        jint offset,
        jint length,
        jint encoding) {
    if (!sEngine) return;

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
        sEngine->process(floatPtr, numFrames);
    } else {
        // ENCODING_PCM_16BIT (16-bit PCM, 2 bytes per sample, 2 channels = 4 bytes per frame)
        int16_t* int16Ptr = reinterpret_cast<int16_t*>(ptr);
        int numFrames = length / (sizeof(int16_t) * 2);
        sEngine->process(int16Ptr, numFrames);
    }
}

JNIEXPORT void JNICALL
Java_com_wayne_musicdeck_audio_NativeAudioEngine_nativeRelease(JNIEnv* env, jobject thiz) {
    sEngine.reset();
    LOGI("Native Audio Engine released");
}

} // extern "C"
