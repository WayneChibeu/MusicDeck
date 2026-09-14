package com.wayne.musicdeck.utils

import androidx.media3.common.Player
import kotlinx.coroutines.*

/**
 * Handles smooth volume transitions for a Media3 Player.
 */
class VolumeManager(player: Player, private val scope: CoroutineScope) {
    var player: Player = player
        set(value) {
            fadeJob?.cancel()
            field = value
        }
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

            val steps = (durationMs / 50L).toInt().coerceIn(20, 2000) // 50ms intervals for buttery smooth fades
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
     * Fades the volume IN from 0 to targetVolume over the specified duration.
     * Prevents the jarring "volume burst" when resuming from a cold start.
     * @param durationMs The total time for the fade-in (default 400ms for a snappy but gentle ramp).
     * @param targetVolume The target volume level (default 1.0f).
     */
    fun fadeIn(durationMs: Long = 400, targetVolume: Float = 1.0f) {
        fadeJob?.cancel()
        player.volume = 0f // Start silent
        fadeJob = scope.launch {
            val steps = (durationMs / 25L).toInt().coerceIn(8, 100) // 25ms micro-steps
            val interval = durationMs / steps
            val volumeStep = targetVolume / steps

            for (i in 1..steps) {
                delay(interval)
                val nextVolume = (i * volumeStep).coerceAtMost(targetVolume)
                player.volume = nextVolume
            }
            player.volume = targetVolume
        }
    }

    /**
     * Executes a synchronized equal-power crossfade between an outgoing player and an incoming player.
     * @param outgoingPlayer The player fading out along cos(pi/2 * p).
     * @param incomingPlayer The player fading in along sin(pi/2 * p).
     * @param durationMs Duration of transition in milliseconds.
     * @param onComplete Invoked when crossfade reaches 100%.
     */
    fun crossfade(
        outgoingPlayer: Player,
        incomingPlayer: Player,
        durationMs: Long,
        onComplete: () -> Unit
    ): Job {
        fadeJob?.cancel()
        val job = scope.launch {
            val steps = (durationMs / 25L).toInt().coerceIn(20, 2000) // 25ms micro-steps
            val interval = durationMs / steps

            for (step in 0..steps) {
                val progress = step.toFloat() / steps.toFloat()
                val gainOut = computeEqualPowerGainOut(progress)
                val gainIn = computeEqualPowerGainIn(progress)
                outgoingPlayer.volume = gainOut
                incomingPlayer.volume = gainIn
                delay(interval)
            }
            outgoingPlayer.volume = 0f
            incomingPlayer.volume = 1f
            onComplete()
        }
        fadeJob = job
        return job
    }

    /**
     * Immediately resets the volume to full (1.0f) and cancels any active fade.
     */
    fun resetVolume() {
        fadeJob?.cancel()
        player.volume = 1.0f
    }

    companion object {
        /**
         * Equal-power quarter-sine fade-out curve: cos(pi/2 * progress)
         * Guarantees that cos^2(p) + sin^2(p) == 1.0 (0 dB acoustic power drop).
         */
        fun computeEqualPowerGainOut(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return kotlin.math.cos(Math.PI * 0.5 * p).toFloat().coerceIn(0f, 1f)
        }

        /**
         * Equal-power quarter-sine fade-in curve: sin(pi/2 * progress)
         */
        fun computeEqualPowerGainIn(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return kotlin.math.sin(Math.PI * 0.5 * p).toFloat().coerceIn(0f, 1f)
        }
    }
}

