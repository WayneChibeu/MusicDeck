package com.wayne.musicdeck.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class WaveformSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSeekListener: ((Float) -> Unit)? = null
    var onStartTouch: (() -> Unit)? = null
    var onStopTouch: (() -> Unit)? = null
    
    private val wavePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        color = 0xFF555555.toInt() // Inactive color
    }

    private val activePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        color = 0xFF7F58FF.toInt() // Active color (Violet)
    }

    // Fake amplitudes
    private val amplitudes = IntArray(60) { (20..100).random() }
    
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
        
    fun setProgressPercent(percent: Int) {
        progress = percent / 100f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / (amplitudes.size * 1.5f)
        val gap = barWidth * 0.5f
        
        var currentX = 0f
        
        // Center vertically
        val centerY = height / 2f
        
        amplitudes.forEachIndexed { index, amp ->
            val barHeight = (amp / 100f) * height
            val startY = centerY - (barHeight / 2f)
            val endY = centerY + (barHeight / 2f)
            
            val isPassed = (index.toFloat() / amplitudes.size) <= progress
            
            val paint = if (isPassed) activePaint else wavePaint
            
            canvas.drawRoundRect(
                currentX, startY, currentX + barWidth, endY,
                barWidth / 2f, barWidth / 2f,
                paint
            )
            
            currentX += barWidth + gap
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                onStartTouch?.invoke()
                progress = event.x / width
                onSeekListener?.invoke(progress)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                progress = event.x / width
                onSeekListener?.invoke(progress)
                return true
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                onStopTouch?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
