package com.wayne.musicdeck.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class WaveformSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSeekListener: ((Float) -> Unit)? = null
    var onStartTouch: (() -> Unit)? = null
    var onStopTouch: (() -> Unit)? = null
    
    private val wavePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0x33FFFFFF // Inactive translucent gray
    }

    private val activePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0xFF7F58FF.toInt() // Active theme color (Violet)
    }

    var activeColor: Int
        get() = activePaint.color
        set(value) {
            activePaint.color = value
            invalidate()
        }

    var inactiveColor: Int
        get() = wavePaint.color
        set(value) {
            wavePaint.color = value
            invalidate()
        }

    private var amplitudes: IntArray = IntArray(80) { (20..80).random() }
    
    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }
        
    fun setProgressPercent(percent: Int) {
        progress = percent / 100f
    }

    fun setAmplitudes(data: IntArray) {
        if (data.isNotEmpty()) {
            amplitudes = data
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty() || width <= 0 || height <= 0) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        val count = amplitudes.size
        
        // Total slots = count bars + (count - 1) gaps
        // gap = 0.4 * barWidth => total = count * 1.4 - 0.4
        val barWidth = width / (count * 1.4f)
        val gap = barWidth * 0.4f
        val cornerRadius = barWidth / 2f
        val centerY = height / 2f
        val minBarHeight = 4f * resources.displayMetrics.density
        
        var currentX = 0f
        
        for (i in 0 until count) {
            val amp = amplitudes[i].coerceIn(10, 100)
            val barHeight = max(minBarHeight, (amp / 100f) * (height - 4f))
            val startY = centerY - (barHeight / 2f)
            val endY = centerY + (barHeight / 2f)
            
            val isPassed = (i.toFloat() / count) <= progress
            val paint = if (isPassed) activePaint else wavePaint
            
            canvas.drawRoundRect(
                currentX, startY, currentX + barWidth, endY,
                cornerRadius, cornerRadius,
                paint
            )
            
            currentX += barWidth + gap
        }
    }

    private var isDragging = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onStartTouch?.invoke()
                progress = (event.x / width).coerceIn(0f, 1f)
                onSeekListener?.invoke(progress)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    progress = (event.x / width).coerceIn(0f, 1f)
                    onSeekListener?.invoke(progress)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    progress = (event.x / width).coerceIn(0f, 1f)
                    onSeekListener?.invoke(progress)
                    onStopTouch?.invoke()
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    onStopTouch?.invoke()
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
