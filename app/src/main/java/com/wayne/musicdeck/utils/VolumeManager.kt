package com.wayne.musicdeck.utils

import androidx.media3.common.Player
import kotlinx.coroutines.*

/**
 * Handles smooth volume transitions for a Media3 Player.
 */
class VolumeManager(private val player: Player, private val scope: CoroutineScope) {
    private var fadeJob: Job? = null

    /**
     * Fades the volume out over the specified duration.
     * @param durationMs The total time for the fade-out.
     * @param onComplete Callback invoked when the fade-out is finished.
     */
    fun fadeOut(durationMs: Long, onComplete: () -> Unit) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startVolume = player.volume
            if (startVolume <= 0f) {
                onComplete()
                return@launch
            }

            val steps = 20
            val interval = durationMs / steps
            val volumeStep = startVolume / steps

            for (i in 1..steps) {
                delay(interval)
                // Linear ramp down
                val nextVolume = (startVolume - (i * volumeStep)).coerceAtLeast(0f)
                player.volume = nextVolume
            }
            player.volume = 0f
            onComplete()
        }
    }

    /**
     * Immediately resets the volume to full (1.0f) and cancels any active fade.
     */
    fun resetVolume() {
        fadeJob?.cancel()
        player.volume = 1.0f
    }
}
