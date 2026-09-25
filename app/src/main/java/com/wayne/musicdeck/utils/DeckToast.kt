package com.wayne.musicdeck.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.color.MaterialColors
import com.wayne.musicdeck.R
import java.lang.ref.WeakReference

/**
 * DeckToast - Unified solid matte floating capsule HUD for MusicDeck.
 * Replaces standard Android system toasts with modern in-app feedback.
 */
object DeckToast {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentToastRef: WeakReference<View>? = null
    private var dismissRunnable: Runnable? = null

    fun show(
        view: View?,
        message: String,
        @DrawableRes iconRes: Int? = R.drawable.ic_music_note,
        isLong: Boolean = false,
        bottomOffsetDp: Int = 80
    ) {
        if (view == null || message.isBlank()) return
        val context = view.context ?: return
        val activity = findActivity(context)
        
        mainHandler.post {
            // Prefer the dialog or sheet root if inside a dialog, fallback to activity content
            val parent = findBestParent(view, activity) ?: return@post
            displayToast(parent, context, message, iconRes, isLong, bottomOffsetDp)
        }
    }

    fun show(
        context: Context?,
        message: String,
        @DrawableRes iconRes: Int? = R.drawable.ic_music_note,
        isLong: Boolean = false,
        bottomOffsetDp: Int = 80
    ) {
        if (context == null || message.isBlank()) return
        val activity = findActivity(context) ?: return
        
        mainHandler.post {
            val parent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post
            displayToast(parent, activity, message, iconRes, isLong, bottomOffsetDp)
        }
    }

    private fun displayToast(
        parent: ViewGroup,
        context: Context,
        message: String,
        @DrawableRes iconRes: Int?,
        isLong: Boolean,
        bottomOffsetDp: Int
    ) {
        // Cancel any pending dismissal
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }

        // If existing view is already attached to this parent, update in-place with a quick bump
        val existingView = currentToastRef?.get()
        if (existingView != null && existingView.parent == parent) {
            updateContent(existingView, message, iconRes, context)
            existingView.animate().cancel()
            existingView.alpha = 1f
            existingView.scaleX = 1f
            existingView.scaleY = 1f
            scheduleDismiss(existingView, parent, isLong)
            return
        }

        // Clean up any old toast
        existingView?.let { (it.parent as? ViewGroup)?.removeView(it) }

        // Inflate layout
        val toastView = LayoutInflater.from(context).inflate(R.layout.layout_deck_toast, parent, false)
        updateContent(toastView, message, iconRes, context)

        val density = context.resources.displayMetrics.density
        val marginPx = (bottomOffsetDp * density).toInt()

        val params = when (parent) {
            is FrameLayout -> FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = marginPx
            }
            is androidx.coordinatorlayout.widget.CoordinatorLayout -> androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = marginPx
            }
            else -> ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = marginPx
            }
        }

        toastView.layoutParams = params
        toastView.alpha = 0f
        toastView.scaleX = 0.82f
        toastView.scaleY = 0.82f

        parent.addView(toastView)
        currentToastRef = WeakReference(toastView)

        toastView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.25f))
            .start()

        scheduleDismiss(toastView, parent, isLong)
    }

    private fun updateContent(
        view: View,
        message: String,
        @DrawableRes iconRes: Int?,
        context: Context
    ) {
        val tv = view.findViewById<TextView>(R.id.tvToastText)
        val iv = view.findViewById<ImageView>(R.id.ivToastIcon)

        tv.text = message

        if (iconRes != null && iconRes != 0) {
            iv.visibility = View.VISIBLE
            iv.setImageResource(iconRes)

            val accentColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorPrimary,
                Color.parseColor("#FF3B30")
            )
            iv.backgroundTintList = ColorStateList.valueOf(accentColor)
        } else {
            iv.visibility = View.GONE
        }
    }

    private fun scheduleDismiss(view: View, parent: ViewGroup, isLong: Boolean) {
        val durationMs = if (isLong) 2800L else 1600L
        val runnable = Runnable {
            view.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    parent.removeView(view)
                    if (currentToastRef?.get() == view) {
                        currentToastRef = null
                    }
                }
                .start()
        }
        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, durationMs)
    }

    private fun findBestParent(view: View, activity: Activity?): ViewGroup? {
        val dialogRoot = view.rootView as? ViewGroup
        if (dialogRoot != null && dialogRoot != activity?.window?.decorView) {
            // Inside a dialog / bottom sheet
            val frame = dialogRoot.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)
                ?: dialogRoot.findViewById(android.R.id.content)
                ?: dialogRoot
            return frame
        }
        return activity?.findViewById(android.R.id.content) ?: dialogRoot
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
