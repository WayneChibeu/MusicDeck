package com.wayne.musicdeck.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.wayne.musicdeck.R

class FastScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = arrayOf(
        "★", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", 
        "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    )
    
    private val paint = Paint().apply {
        color = Color.GRAY
        isAntiAlias = true
        textSize = 30f // Will be scaled
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private var letterHeight = 0f
    private var listener: OnFastScrollListener? = null
    
    interface OnFastScrollListener {
        fun onLetterSelected(letter: String)
    }
    
    fun setListener(listener: OnFastScrollListener) {
        this.listener = listener
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        letterHeight = h.toFloat() / letters.size
        paint.textSize = letterHeight * 0.75f 
        // Cap max text size so it doesn't look huge on large screens
        if (paint.textSize > 40f * resources.displayMetrics.density) {
             paint.textSize = 40f * resources.displayMetrics.density
        }
    }
    
    private var selectedIndex = -1
    private var activeColor = Color.CYAN // Default fallback
    
    init {
        // Resolve primary color
        val typedValue = android.util.TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
            activeColor = typedValue.data
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val widthCenter = width / 2f
        
        for (i in letters.indices) {
            paint.color = if (i == selectedIndex) activeColor else Color.GRAY
            // Make selected letter slightly larger/bolder?
            if (i == selectedIndex) {
                 paint.typeface = Typeface.DEFAULT_BOLD
            } else {
                 paint.typeface = Typeface.DEFAULT
            }
            
            val yPos = letterHeight * (i + 1) - letterHeight / 4 // Adjust baseline
            canvas.drawText(letters[i], widthCenter - paint.measureText(letters[i]) / 2, yPos, paint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = (event.y / height * letters.size).toInt()
                if (index in letters.indices) {
                    if (selectedIndex != index) {
                        selectedIndex = index
                        listener?.onLetterSelected(letters[index])
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                        invalidate()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    fun setTextColor(color: Int) {
        paint.color = color
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
