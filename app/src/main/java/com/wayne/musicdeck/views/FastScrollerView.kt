package com.wayne.musicdeck.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

class FastScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = arrayOf(
        "★", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", 
        "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    )
    
    private val textPaint = Paint().apply {
        color = 0x8AFFFFFF.toInt() // 54% white
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val trackPaint = Paint().apply {
        color = 0x12FFFFFF.toInt() // Subtle glass pill track
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val trackRect = RectF()
    private var letterHeight = 0f
    private var listener: OnFastScrollListener? = null
    private var bubbleView: TextView? = null
    
    private var selectedIndex = -1
    private var activeColor = Color.parseColor("#FF6D00") // Fallback
    
    interface OnFastScrollListener {
        fun onLetterSelected(letter: String)
    }
    
    fun setListener(listener: OnFastScrollListener) {
        this.listener = listener
    }

    fun attachBubble(bubble: TextView) {
        this.bubbleView = bubble
    }
    
    init {
        val typedValue = android.util.TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
            activeColor = typedValue.data
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = 4f * resources.displayMetrics.density
        trackRect.set(pad, pad, w.toFloat() - pad, h.toFloat() - pad)
        
        letterHeight = (h - pad * 2) / letters.size
        textPaint.textSize = (letterHeight * 0.72f).coerceIn(18f, 32f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw subtle pill track behind letters
        val trackRadius = trackRect.width() / 2f
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)
        
        val widthCenter = width / 2f
        val topOffset = trackRect.top
        
        for (i in letters.indices) {
            val isSelected = i == selectedIndex
            textPaint.color = if (isSelected) activeColor else 0x80FFFFFF.toInt()
            textPaint.textSize = if (isSelected) (letterHeight * 0.95f).coerceIn(22f, 38f) else (letterHeight * 0.72f).coerceIn(18f, 32f)
            textPaint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            
            val yPos = topOffset + letterHeight * (i + 1) - letterHeight / 3.5f
            val xPos = widthCenter - textPaint.measureText(letters[i]) / 2f
            canvas.drawText(letters[i], xPos, yPos, textPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val effectiveY = (event.y - trackRect.top).coerceIn(0f, trackRect.height())
                val index = (effectiveY / letterHeight).toInt().coerceIn(0, letters.size - 1)
                
                if (selectedIndex != index) {
                    selectedIndex = index
                    val selectedLetter = letters[index]
                    listener?.onLetterSelected(selectedLetter)
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                    
                    // Update and position floating preview bubble
                    bubbleView?.let { bubble ->
                        bubble.text = selectedLetter
                        val targetY = this.top + trackRect.top + (index * letterHeight) - (bubble.height / 2f) + (letterHeight / 2f)
                        bubble.y = targetY.coerceIn(0f, (parent as? View)?.height?.toFloat() ?: targetY)
                        
                        if (bubble.visibility != View.VISIBLE) {
                            bubble.visibility = View.VISIBLE
                            bubble.alpha = 0f
                            bubble.scaleX = 0.6f
                            bubble.scaleY = 0.6f
                            bubble.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120)
                                .start()
                        }
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Fade out floating bubble smoothly
                bubbleView?.animate()
                    ?.alpha(0f)
                    ?.scaleX(0.7f)
                    ?.scaleY(0.7f)
                    ?.setDuration(180)
                    ?.withEndAction {
                        bubbleView?.visibility = View.GONE
                    }
                    ?.start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }
    
    fun setActiveLetter(letter: String) {
        val index = letters.indexOfFirst { it.equals(letter, ignoreCase = true) }
        if (index != -1 && index != selectedIndex) {
            selectedIndex = index
            invalidate()
        }
    }
}

