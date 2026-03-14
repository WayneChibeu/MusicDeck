package com.wayne.musicdeck.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import com.wayne.musicdeck.R

class StadiumVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var visualizer: Visualizer? = null
    private var fftData: ByteArray? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.colorNeon)
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val barCount = 32
    private val barWidths = FloatArray(barCount)
    private val smoothedMagnitudes = FloatArray(barCount)

    fun setAudioSessionId(sessionId: Int) {
        release()
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, samplingRate: Int) {
                        fftData = data
                        invalidate()
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = fftData ?: return
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val barGap = w / barCount
        val barWidth = barGap * 0.6f

        for (i in 0 until barCount) {
            // Indexing FFT data: [real0, imag0, real1, imag1, ...]
            // Index 0 and 1 are special (DC and Nyquist)
            val index = i * 2 + 2
            if (index + 1 < data.size) {
                val real = data[index].toFloat()
                val imag = data[index + 1].toFloat()
                val magnitude = Math.hypot(real.toDouble(), imag.toDouble()).toFloat()
                
                // Simple easing/smoothing
                smoothedMagnitudes[i] = smoothedMagnitudes[i] * 0.8f + magnitude * 0.2f
                
                val barHeight = (smoothedMagnitudes[i] / 128f) * h
                val left = i * barGap + (barGap - barWidth) / 2
                val top = h - barHeight
                
                canvas.drawRoundRect(left, top, left + barWidth, h, 8f, 8f, paint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
