package com.wayne.musicdeck.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

object WaveformExtractor {

    private const val BUCKET_COUNT = 100
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, IntArray>()
    private val mmkv by lazy { MMKV.mmkvWithID("audio_waveforms") }

    /**
     * Extracts or retrieves cached peak amplitude buckets (0..100) for the given audio track.
     */
    suspend fun getWaveform(context: Context, filePath: String): IntArray = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext generatePlaceholderWaveform()

        // 1. Memory Cache
        memoryCache[filePath]?.let { return@withContext it }

        // 2. MMKV Disk Cache
        val cacheKey = "wf_" + filePath.hashCode()
        val cachedString = mmkv.decodeString(cacheKey, null)
        if (!cachedString.isNullOrEmpty()) {
            val parsed = cachedString.split(",").mapNotNull { it.toIntOrNull() }.toIntArray()
            if (parsed.size == BUCKET_COUNT) {
                memoryCache[filePath] = parsed
                return@withContext parsed
            }
        }

        // 3. Extract Real PCM Amplitudes via MediaExtractor + MediaCodec
        val file = File(filePath)
        if (!file.exists() || file.length() < 1024) {
            val fallback = generatePlaceholderWaveform(filePath.hashCode())
            memoryCache[filePath] = fallback
            return@withContext fallback
        }

        val extracted = try {
            decodeAudioPeaks(filePath)
        } catch (e: Exception) {
            null
        }

        val result = extracted ?: generatePlaceholderWaveform(filePath.hashCode())
        
        // Cache result
        memoryCache[filePath] = result
        try {
            mmkv.encode(cacheKey, result.joinToString(","))
        } catch (_: Exception) {}

        return@withContext result
    }

    private fun decodeAudioPeaks(filePath: String): IntArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(filePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val rawPeaks = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            val timeoutUs = 5000L

            var framesProcessed = 0
            val maxFrames = 1500 // Cap to ensure high speed extraction

            while (!isEos && framesProcessed < maxFrames) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        // Sample peak amplitude from 16-bit PCM buffer
                        var maxPeak = 0
                        val shortBuffer = outputBuffer.asShortBuffer()
                        val step = max(1, shortBuffer.remaining() / 16)
                        while (shortBuffer.hasRemaining()) {
                            val sample = abs(shortBuffer.get().toInt())
                            if (sample > maxPeak) maxPeak = sample
                            if (shortBuffer.remaining() > step) {
                                shortBuffer.position(shortBuffer.position() + step)
                            }
                        }
                        rawPeaks.add(maxPeak.toFloat())
                        framesProcessed++
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                }
            }

            if (rawPeaks.isEmpty()) return null

            // Normalize and downsample into exactly BUCKET_COUNT bins
            val buckets = IntArray(BUCKET_COUNT)
            val windowSize = rawPeaks.size.toFloat() / BUCKET_COUNT
            val maxObserved = rawPeaks.maxOrNull()?.takeIf { it > 0f } ?: 32767f

            for (i in 0 until BUCKET_COUNT) {
                val start = (i * windowSize).toInt().coerceIn(0, rawPeaks.size - 1)
                val end = ((i + 1) * windowSize).toInt().coerceIn(start + 1, rawPeaks.size)
                var peakInBin = 0f
                for (j in start until end) {
                    if (rawPeaks[j] > peakInBin) peakInBin = rawPeaks[j]
                }
                val normalized = ((peakInBin / maxObserved) * 85f + 15f).toInt().coerceIn(15, 100)
                buckets[i] = normalized
            }

            return buckets
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Smooth harmonic waveform fallback when track is protected or decoding fails.
     */
    fun generatePlaceholderWaveform(seed: Int = 42): IntArray {
        val random = java.util.Random(seed.toLong())
        val result = IntArray(BUCKET_COUNT)
        var current = 40f
        for (i in 0 until BUCKET_COUNT) {
            // Apply musical envelope curve (softer at ends, dynamic in middle)
            val progress = i.toFloat() / BUCKET_COUNT
            val envelope = (Math.sin(progress * Math.PI) * 45f + 25f).toFloat()
            val noise = (random.nextFloat() - 0.5f) * 20f
            current = (current * 0.7f + (envelope + noise) * 0.3f).coerceIn(15f, 100f)
            result[i] = current.toInt()
        }
        return result
    }
}
