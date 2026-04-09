package com.wayne.musicdeck.utils

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Adds a tactile "Bouncy" scale animation to any View when it is pressed.
 * This makes buttons feel more mechanical and responsive.
 */
fun View.setupBouncyPress() {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(100)
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(4f))
                    .start()
            }
        }
        false // Don't consume so click listeners still work
    }
}
