/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Media3 AudioProcessor Pipeline.
 */

package com.wayne.musicdeck.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Media3 AudioProcessor that routes audio through the MusicDeck C++ DSP engine.
 * Transparently applies studio-grade 32-bit float EQ, bass boost, and limiting.
 */
class NativeAudioProcessor : BaseAudioProcessor() {

    init {
        NativeAudioEngine.init()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We support stereo 16-bit PCM and 32-bit float PCM
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        if (inputAudioFormat.channelCount != 2) {
            // Stereo required for our spatial and dual-channel biquad DSP
            return AudioProcessor.AudioFormat.NOT_SET
        }

        NativeAudioEngine.setSampleRate(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Allocate or reuse direct buffer in output
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        // Process audio in-place through C++ DSP
        NativeAudioEngine.processBuffer(
            buffer = outputBuffer,
            offset = 0,
            length = remaining,
            encoding = inputAudioFormat.encoding
        )
    }

    override fun onFlush() {
        super.onFlush()
        NativeAudioEngine.reset()
    }

    override fun onReset() {
        super.onReset()
        NativeAudioEngine.reset()
    }
}
