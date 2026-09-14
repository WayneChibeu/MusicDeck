/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Native 60 FPS Spectrum Visualizer View.
 */

package com.wayne.musicdeck.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.wayne.musicdeck.audio.NativeAudioEngine
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance hardware-accelerated 60-120 FPS audio spectrum visualizer.
 * Renders 32 logarithmic frequency bars with dynamic album-art gradients,
 * floating peak caps, and ballistics synced to the native C++ DSP FFT engine.
 */
class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val NUM_BANDS = 32
        private const val MIN_BAR_HEIGHT_DP = 2.5f
        private const val BAR_CORNER_RADIUS_DP = 3f
        private const val PEAK_CAP_HEIGHT_DP = 2f
        private const val PEAK_HOLD_FRAMES = 8
        private const val PEAK_FALL_SPEED_DP = 1.2f
    }

    private val density = resources.displayMetrics.density
    private val minBarHeightPx = MIN_BAR_HEIGHT_DP * density
    private val barCornerRadiusPx = BAR_CORNER_RADIUS_DP * density
    private val peakCapHeightPx = PEAK_CAP_HEIGHT_DP * density
    private val peakFallSpeedPx = PEAK_FALL_SPEED_DP * density

    // Audio Bins from Native C++
    private val rawBins = FloatArray(NUM_BANDS)
    private val displayHeights = FloatArray(NUM_BANDS)
    private val peakHeights = FloatArray(NUM_BANDS)
    private val peakHoldTimers = IntArray(NUM_BANDS)

    // Drawing resources
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barRect = RectF()
    private val peakRect = RectF()

    // Dynamic Colors
    private var colorBottom = Color.parseColor("#806750A4")
    private var colorTop = Color.parseColor("#FFD0BCFF")
    private var colorPeak = Color.WHITE

    private var gradientShader: LinearGradient? = null

    // State & Animation
    private var isAnimating = false
    private var isPlaying = false
    private var isVisualizerEnabled = true

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating || !isAttachedToWindow) return

            updateFramePhysics()
            invalidate()

            // Continue animation if playing or if bars are still decaying down
            if (isPlaying || hasActiveEnergy()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                isAnimating = false
            }
        }
    }

    init {
        // Ensure hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Sets whether audio playback is actively playing.
     */
    fun setPlaying(playing: Boolean) {
        if (isPlaying != playing) {
            isPlaying = playing
            if (isPlaying && isVisualizerEnabled) {
                startAnimation()
            }
        }
    }

    /**
     * Toggles whether the visualizer is enabled by user preference.
     */
    fun setVisualizerEnabled(enabled: Boolean) {
        isVisualizerEnabled = enabled
        visibility = if (enabled) VISIBLE else GONE
        if (enabled && isPlaying) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    /**
     * Updates dynamic theme colors from album artwork palette.
     */
    fun setThemeColors(@ColorInt primaryColor: Int, @ColorInt secondaryColor: Int) {
        // Deep vibrant bottom to luminous radiant top
        colorBottom = ColorUtils.setAlphaComponent(primaryColor, 180)
        colorTop = secondaryColor
        colorPeak = ColorUtils.blendARGB(secondaryColor, Color.WHITE, 0.4f)

        peakPaint.color = colorPeak
        rebuildGradient()
        invalidate()
    }

    private fun rebuildGradient() {
        if (height > 0) {
            gradientShader = LinearGradient(
                0f, height.toFloat(),
                0f, 0f,
                intArrayOf(colorBottom, colorTop),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradientShader
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradient()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying && isVisualizerEnabled) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun startAnimation() {
        if (!isAnimating && isAttachedToWindow && isVisualizerEnabled) {
            isAnimating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    private fun stopAnimation() {
        if (isAnimating) {
            isAnimating = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    private fun hasActiveEnergy(): Boolean {
        for (i in 0 until NUM_BANDS) {
            if (displayHeights[i] > minBarHeightPx * 1.05f || peakHeights[i] > minBarHeightPx * 1.05f) {
                return true
            }
        }
        return false
    }

    private fun updateFramePhysics() {
        if (isPlaying && isVisualizerEnabled) {
            // Fetch live 32-band spectrum magnitudes from native C++ DSP
            NativeAudioEngine.getVisualizerBins(rawBins)
        } else {
            // Paused: fade raw bins to zero
            for (i in 0 until NUM_BANDS) {
                rawBins[i] = 0.0f
            }
        }

        val availableHeight = max(10f, height.toFloat() - peakCapHeightPx - 2f)

        for (i in 0 until NUM_BANDS) {
            val targetHeight = (rawBins[i] * availableHeight).coerceIn(minBarHeightPx, availableHeight)

            // Smooth spring attack & decay
            if (targetHeight > displayHeights[i]) {
                displayHeights[i] += 0.55f * (targetHeight - displayHeights[i])
            } else {
                displayHeights[i] += 0.16f * (targetHeight - displayHeights[i])
            }

            // Floating Peak Cap Physics
            if (displayHeights[i] >= peakHeights[i]) {
                peakHeights[i] = displayHeights[i]
                peakHoldTimers[i] = PEAK_HOLD_FRAMES
            } else {
                if (peakHoldTimers[i] > 0) {
                    peakHoldTimers[i]--
                } else {
                    peakHeights[i] = max(minBarHeightPx, peakHeights[i] - peakFallSpeedPx)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisualizerEnabled || width <= 0 || height <= 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Calculate responsive bar width and spacing
        val totalSpacingRatio = 0.35f
        val slotWidth = viewWidth / NUM_BANDS
        val barWidth = slotWidth * (1f - totalSpacingRatio)
        val spacing = slotWidth * totalSpacingRatio

        for (i in 0 until NUM_BANDS) {
            val left = i * slotWidth + (spacing / 2f)
            val right = left + barWidth
            val barH = displayHeights[i]
            val top = viewHeight - barH
            val bottom = viewHeight

            // Draw rounded spectrum bar
            barRect.set(left, top, right, bottom)
            canvas.drawRoundRect(barRect, barCornerRadiusPx, barCornerRadiusPx, barPaint)

            // Draw floating peak cap dot/bar
            val peakH = peakHeights[i]
            if (peakH > minBarHeightPx * 1.2f) {
                val peakTop = viewHeight - peakH - peakCapHeightPx - 1f
                val peakBottom = peakTop + peakCapHeightPx
                peakRect.set(left, peakTop, right, peakBottom)
                canvas.drawRoundRect(peakRect, peakCapHeightPx / 2f, peakCapHeightPx / 2f, peakPaint)
            }
        }
    }
}
