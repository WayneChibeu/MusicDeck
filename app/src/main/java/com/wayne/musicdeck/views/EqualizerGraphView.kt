/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Interactive Frequency Response EQ Graph.
 */

package com.wayne.musicdeck.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.wayne.musicdeck.audio.NativeAudioEngine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-performance, hardware-accelerated studio-grade parametric EQ frequency response view.
 * 
 * Computes exact cascaded 2nd-order biquadratic filter transfer functions |H(e^jw)|
 * across the audible spectrum (20 Hz - 20 kHz) and provides bidirectional interactive
 * dragging of 5 parametric band nodes with real-time haptics, dynamic gradient shading,
 * floating frequency/gain tooltips, and background RTA (Real-Time Analyzer) spectrum glow.
 */
class EqualizerGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    companion object {
        const val NUM_BANDS = 5
        val BAND_FREQUENCIES = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        val BAND_Q = floatArrayOf(1.1f, 1.0f, 1.0f, 1.0f, 1.1f)
        const val MIN_FREQ = 20f
        const val MAX_FREQ = 20000f
        const val MIN_DB = -15f
        const val MAX_DB = 15f
        private const val CURVE_POINTS = 160
        private const val SAMPLE_RATE = 44100f
        private const val TOUCH_SLOP_RADIUS_DP = 40f
    }

    // Callback invoked when a band gain is altered by user drag on the graph
    var onBandGainChanged: ((bandIndex: Int, gainDb: Float, progress: Int) -> Unit)? = null

    // Live band gains in decibels (-15 dB to +15 dB)
    private val bandGains = FloatArray(NUM_BANDS) { 0f }
    private var isEqEnabled = true

    // Interactive dragging state
    private var activeDraggedBand = -1
    private var lastHapticGainDb = 0f
    private var touchDownTime = 0L

    // Geometry caches
    private val contentRect = RectF()
    private val curveFreqs = FloatArray(CURVE_POINTS)
    private val curvePointsX = FloatArray(CURVE_POINTS)
    private val curvePointsY = FloatArray(CURVE_POINTS)
    private val nodePositionsX = FloatArray(NUM_BANDS)
    private val nodePositionsY = FloatArray(NUM_BANDS)

    // Precomputed paths
    private val curvePath = Path()
    private val fillPath = Path()
    private val gridPath = Path()
    private val zeroLinePath = Path()

    // Real-Time Analyzer (RTA) Spectrum state
    private val rtaBins = FloatArray(32)
    private val smoothedRtaBins = FloatArray(32)
    private var isRtaActive = false
    private var isChoreographerScheduled = false
    private val rtaPath = Path()

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#12141C")
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#1AFFFFFF")
    }

    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
        color = Color.parseColor("#38FFFFFF")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(9.5f)
        color = Color.parseColor("#66FFFFFF")
        textAlign = Paint.Align.CENTER
    }

    private val dbLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(8.5f)
        color = Color.parseColor("#4DFFFFFF")
        textAlign = Paint.Align.RIGHT
    }

    private val curveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#A78BFA")
    }

    private val curveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rtaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#228A70D6")
    }

    private val nodeOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val nodeCenterDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#12141C")
    }

    private val badgeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E6202434")
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#55FFFFFF")
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spToPx(11f)
        color = Color.WHITE
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private var primaryColor = Color.parseColor("#A78BFA")
    private var accentColor = Color.parseColor("#22D3EE")

    init {
        // Resolve theme colors if available
        val tv = android.util.TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)) {
            primaryColor = tv.data
        }
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, tv, true)) {
            accentColor = tv.data
        }

        // Precompute logarithmically spaced frequencies across 20 Hz .. 20 kHz,
        // explicitly including the 5 band frequencies so the curve passes through every node
        val baseFreqs = mutableListOf<Float>()
        val logMin = log10(MIN_FREQ.toDouble())
        val logMax = log10(MAX_FREQ.toDouble())
        val numSamplePoints = CURVE_POINTS - NUM_BANDS

        for (i in 0 until numSamplePoints) {
            val ratio = i.toDouble() / (numSamplePoints - 1)
            baseFreqs.add(10.0.pow(logMin + ratio * (logMax - logMin)).toFloat())
        }
        for (f in BAND_FREQUENCIES) {
            baseFreqs.add(f)
        }
        baseFreqs.sort()

        for (i in 0 until CURVE_POINTS) {
            curveFreqs[i] = baseFreqs[i]
        }

        updateColorPalette(primaryColor, accentColor)
    }

    fun setThemeColors(primary: Int, accent: Int) {
        primaryColor = primary
        accentColor = accent
        updateColorPalette(primary, accent)
        updateGradients()
        invalidate()
    }

    private fun updateColorPalette(primary: Int, accent: Int) {
        curveStrokePaint.color = primary
        nodeInnerPaint.color = primary
        rtaPaint.color = Color.argb(35, Color.red(accent), Color.green(accent), Color.blue(accent))
    }

    /**
     * Sets the gain for a single band without animating.
     */
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until NUM_BANDS) return
        val clamped = gainDb.coerceIn(MIN_DB, MAX_DB)
        if (abs(bandGains[bandIndex] - clamped) > 0.01f) {
            bandGains[bandIndex] = clamped
            recomputeCurve()
            invalidate()
        }
    }

    /**
     * Sets all 5 band gains simultaneously.
     */
    fun setAllGains(gains: FloatArray) {
        for (i in 0 until minOf(gains.size, NUM_BANDS)) {
            bandGains[i] = gains[i].coerceIn(MIN_DB, MAX_DB)
        }
        recomputeCurve()
        invalidate()
    }

    /**
     * Smoothly animates the curve to a target set of band gains (e.g. preset selection).
     */
    fun animateToGains(targetGains: FloatArray, durationMs: Long = 280L) {
        val startGains = bandGains.clone()
        val safeTargets = FloatArray(NUM_BANDS) { i ->
            if (i < targetGains.size) targetGains[i].coerceIn(MIN_DB, MAX_DB) else 0f
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                for (i in 0 until NUM_BANDS) {
                    bandGains[i] = startGains[i] + fraction * (safeTargets[i] - startGains[i])
                }
                recomputeCurve()
                invalidate()
            }
            start()
        }
    }

    /**
     * Toggles equalizer enabled/bypass state.
     */
    fun setEqEnabled(enabled: Boolean) {
        if (isEqEnabled != enabled) {
            isEqEnabled = enabled
            recomputeCurve()
            invalidate()
        }
    }

    /**
     * Starts or stops the Real-Time Analyzer (RTA) background spectrum loop.
     */
    fun setRtaActive(active: Boolean) {
        isRtaActive = active
        if (active && !isChoreographerScheduled) {
            isChoreographerScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRtaActive || !isAttachedToWindow) {
            isChoreographerScheduled = false
            return
        }

        // Query FFT visualizer bins from native audio engine
        NativeAudioEngine.getVisualizerBins(rtaBins)

        // Exponential smoothing for background RTA
        for (i in rtaBins.indices) {
            val target = rtaBins[i]
            if (target > smoothedRtaBins[i]) {
                smoothedRtaBins[i] = smoothedRtaBins[i] * 0.4f + target * 0.6f
            } else {
                smoothedRtaBins[i] = smoothedRtaBins[i] * 0.88f + target * 0.12f
            }
        }

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isRtaActive && !isChoreographerScheduled) {
            isChoreographerScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isChoreographerScheduled = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val paddingHorizontal = dpToPx(16f)
        val paddingTop = dpToPx(14f)
        val paddingBottom = dpToPx(22f)

        contentRect.set(
            paddingHorizontal,
            paddingTop,
            w.toFloat() - paddingHorizontal,
            h.toFloat() - paddingBottom
        )

        // Precompute X pixel locations for all curve points
        val logMin = log10(MIN_FREQ.toDouble())
        val logMax = log10(MAX_FREQ.toDouble())
        val logRange = logMax - logMin

        for (i in 0 until CURVE_POINTS) {
            val f = curveFreqs[i].toDouble()
            val ratio = (log10(f) - logMin) / logRange
            curvePointsX[i] = contentRect.left + (ratio * contentRect.width()).toFloat()
        }

        // Precompute X pixel locations for the 5 interactive band nodes
        for (i in 0 until NUM_BANDS) {
            val f = BAND_FREQUENCIES[i].toDouble()
            val ratio = (log10(f) - logMin) / logRange
            nodePositionsX[i] = contentRect.left + (ratio * contentRect.width()).toFloat()
        }

        buildGridPaths()
        updateGradients()
        recomputeCurve()
    }

    private fun updateGradients() {
        if (contentRect.width() <= 0f || contentRect.height() <= 0f) return

        val topColor = Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        val midColor = Color.argb(30, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        val bottomColor = Color.TRANSPARENT

        curveFillPaint.shader = LinearGradient(
            0f, contentRect.top,
            0f, contentRect.bottom,
            intArrayOf(topColor, midColor, bottomColor),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun buildGridPaths() {
        gridPath.reset()
        zeroLinePath.reset()

        // Horizontal dB grid lines (+15, +10, +5, 0, -5, -10, -15)
        val dbSteps = floatArrayOf(15f, 10f, 5f, 0f, -5f, -10f, -15f)
        for (db in dbSteps) {
            val y = gainToY(db)
            if (abs(db) < 0.1f) {
                zeroLinePath.moveTo(contentRect.left, y)
                zeroLinePath.lineTo(contentRect.right, y)
            } else {
                gridPath.moveTo(contentRect.left, y)
                gridPath.lineTo(contentRect.right, y)
            }
        }

        // Vertical frequency grid lines
        val markerFreqs = floatArrayOf(50f, 100f, 250f, 500f, 1000f, 2500f, 5000f, 10000f)
        val logMin = log10(MIN_FREQ.toDouble())
        val logMax = log10(MAX_FREQ.toDouble())
        val logRange = logMax - logMin

        for (f in markerFreqs) {
            val ratio = (log10(f.toDouble()) - logMin) / logRange
            val x = contentRect.left + (ratio * contentRect.width()).toFloat()
            gridPath.moveTo(x, contentRect.top)
            gridPath.lineTo(x, contentRect.bottom)
        }
    }

    /**
     * Evaluates total dB response across the 5 peaking filters at frequency f.
     */
    private fun evaluateTotalDbAt(
        f: Float,
        b0: DoubleArray,
        b1: DoubleArray,
        b2: DoubleArray,
        a1: DoubleArray,
        a2: DoubleArray
    ): Double {
        val omega = 2.0 * Math.PI * f / SAMPLE_RATE
        val cos1 = cos(omega)
        val sin1 = sin(omega)
        val cos2 = cos(2.0 * omega)
        val sin2 = sin(2.0 * omega)

        var totalDb = 0.0
        for (b in 0 until NUM_BANDS) {
            val numR = b0[b] + b1[b] * cos1 + b2[b] * cos2
            val numI = -(b1[b] * sin1 + b2[b] * sin2)
            val denR = 1.0 + a1[b] * cos1 + a2[b] * cos2
            val denI = -(a1[b] * sin1 + a2[b] * sin2)

            val magSquared = (numR * numR + numI * numI) / (denR * denR + denI * denI)
            totalDb += 10.0 * log10(magSquared.coerceAtLeast(1e-10))
        }
        return totalDb
    }

    /**
     * Calculates the composite frequency response curve across all biquad filters.
     */
    private fun recomputeCurve() {
        if (contentRect.width() <= 0f || contentRect.height() <= 0f) return

        // 1. Prepare Biquad coefficients for the 5 peaking filters
        val b0 = DoubleArray(NUM_BANDS)
        val b1 = DoubleArray(NUM_BANDS)
        val b2 = DoubleArray(NUM_BANDS)
        val a1 = DoubleArray(NUM_BANDS)
        val a2 = DoubleArray(NUM_BANDS)

        for (i in 0 until NUM_BANDS) {
            val gainDb = if (isEqEnabled) bandGains[i] else 0f
            val f0 = BAND_FREQUENCIES[i]
            val q = BAND_Q[i]

            val A = 10.0.pow(gainDb / 40.0)
            val omega = 2.0 * Math.PI * f0 / SAMPLE_RATE
            val sinOmega = sin(omega)
            val cosOmega = cos(omega)
            val alpha = sinOmega / (2.0 * q)

            val num0 = 1.0 + alpha * A
            val num1 = -2.0 * cosOmega
            val num2 = 1.0 - alpha * A
            val den0 = 1.0 + alpha / A
            val den1 = -2.0 * cosOmega
            val den2 = 1.0 - alpha / A

            // Normalize by den0
            b0[i] = num0 / den0
            b1[i] = num1 / den0
            b2[i] = num2 / den0
            a1[i] = den1 / den0
            a2[i] = den2 / den0
        }

        // 2. Evaluate combined frequency response for each of the curve points
        for (p in 0 until CURVE_POINTS) {
            val totalDb = if (isEqEnabled) {
                evaluateTotalDbAt(curveFreqs[p], b0, b1, b2, a1, a2)
            } else {
                0.0
            }
            curvePointsY[p] = gainToY(totalDb.toFloat().coerceIn(MIN_DB - 2f, MAX_DB + 2f))
        }

        // 3. Update Y coordinates for the 5 interactive nodes, ensuring they are EXACTLY on the curve
        for (i in 0 until NUM_BANDS) {
            val db = if (isEqEnabled) {
                evaluateTotalDbAt(BAND_FREQUENCIES[i], b0, b1, b2, a1, a2)
            } else {
                0.0
            }
            nodePositionsY[i] = gainToY(db.toFloat().coerceIn(MIN_DB - 2f, MAX_DB + 2f))
        }

        // 4. Build smooth curve and fill paths
        curvePath.reset()
        fillPath.reset()

        curvePath.moveTo(curvePointsX[0], curvePointsY[0])
        for (p in 1 until CURVE_POINTS) {
            curvePath.lineTo(curvePointsX[p], curvePointsY[p])
        }

        // Build fill path down to zero baseline
        fillPath.set(curvePath)
        val zeroY = gainToY(0f)
        fillPath.lineTo(curvePointsX[CURVE_POINTS - 1], zeroY)
        fillPath.lineTo(curvePointsX[0], zeroY)
        fillPath.close()
    }

    private fun gainToY(gainDb: Float): Float {
        val normalized = (MAX_DB - gainDb) / (MAX_DB - MIN_DB)
        return contentRect.top + normalized * contentRect.height()
    }

    private fun yToGain(y: Float): Float {
        val normalized = (y - contentRect.top) / contentRect.height()
        val gain = MAX_DB - normalized * (MAX_DB - MIN_DB)
        return gain.coerceIn(MIN_DB, MAX_DB)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Draw rounded background card
        val cornerRadius = dpToPx(14f)
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, backgroundPaint)

        // 2. Draw grid lines
        canvas.drawPath(gridPath, gridLinePaint)
        canvas.drawPath(zeroLinePath, zeroLinePaint)

        // 3. Draw frequency & dB text markers
        drawGridLabels(canvas)

        // 4. Draw background Real-Time Analyzer (RTA) audio spectrum
        drawRtaSpectrum(canvas)

        // 5. Draw neon curve gradient fill & glowing stroke
        if (isEqEnabled) {
            canvas.drawPath(fillPath, curveFillPaint)
            curveStrokePaint.alpha = 255
        } else {
            curveStrokePaint.alpha = 75
        }
        canvas.drawPath(curvePath, curveStrokePaint)

        // 6. Draw the 5 draggable frequency nodes (anchored directly on the curve)
        drawNodes(canvas)

        // 7. Draw floating tooltip badge if actively dragging
        if (activeDraggedBand in 0 until NUM_BANDS) {
            drawActiveTooltip(canvas, activeDraggedBand)
        }
    }

    private fun drawGridLabels(canvas: Canvas) {
        // Frequency labels along the bottom
        val labelFreqs = floatArrayOf(100f, 1000f, 10000f)
        val labelNames = arrayOf("100 Hz", "1 kHz", "10 kHz")
        val logMin = log10(MIN_FREQ.toDouble())
        val logMax = log10(MAX_FREQ.toDouble())
        val logRange = logMax - logMin
        val labelY = height - dpToPx(7f)

        for (i in labelFreqs.indices) {
            val ratio = (log10(labelFreqs[i].toDouble()) - logMin) / logRange
            val x = contentRect.left + (ratio * contentRect.width()).toFloat()
            canvas.drawText(labelNames[i], x, labelY, labelPaint)
        }

        // Decibel labels on the left margin
        val leftX = contentRect.left - dpToPx(3f)
        canvas.drawText("+15", leftX, gainToY(15f) + dpToPx(3f), dbLabelPaint)
        canvas.drawText("0", leftX, gainToY(0f) + dpToPx(3f), dbLabelPaint)
        canvas.drawText("-15", leftX, gainToY(-15f) + dpToPx(3f), dbLabelPaint)
    }

    private fun drawRtaSpectrum(canvas: Canvas) {
        if (!isRtaActive) return

        rtaPath.reset()
        val numBars = smoothedRtaBins.size
        val maxPeakHeight = contentRect.height() * 0.85f

        var started = false
        val logMin = log10(MIN_FREQ.toDouble())
        val logMax = log10(MAX_FREQ.toDouble())
        val logRange = logMax - logMin

        for (i in 0 until numBars) {
            // Bin perceptual frequency approximation
            val freq = 30.0 * 2.0.pow(i * 0.28)
            if (freq > MAX_FREQ) break
            val ratio = (log10(freq) - logMin) / logRange
            val x = contentRect.left + (ratio * contentRect.width()).toFloat()
            val mag = smoothedRtaBins[i].coerceIn(0f, 1f)
            val y = contentRect.bottom - (mag * maxPeakHeight)

            if (!started) {
                rtaPath.moveTo(x, y)
                started = true
            } else {
                rtaPath.lineTo(x, y)
            }
        }

        if (started) {
            rtaPath.lineTo(contentRect.right, contentRect.bottom)
            rtaPath.lineTo(contentRect.left, contentRect.bottom)
            rtaPath.close()
            canvas.drawPath(rtaPath, rtaPaint)
        }
    }

    private fun drawNodes(canvas: Canvas) {
        val outerRadius = dpToPx(13f)
        val innerRadius = dpToPx(6.5f)
        val centerDotRadius = dpToPx(2.5f)

        val activeOuterRadius = dpToPx(18f)
        val activeInnerRadius = dpToPx(8f)

        for (i in 0 until NUM_BANDS) {
            val x = nodePositionsX[i]
            val y = nodePositionsY[i]
            val isActive = (i == activeDraggedBand)

            // Outer glow ring
            nodeOuterPaint.color = if (isActive) {
                Color.argb(70, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
            } else {
                Color.argb(35, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
            }

            val rOuter = if (isActive) activeOuterRadius else outerRadius
            val rInner = if (isActive) activeInnerRadius else innerRadius

            canvas.drawCircle(x, y, rOuter, nodeOuterPaint)
            canvas.drawCircle(x, y, rInner, nodeInnerPaint)
            canvas.drawCircle(x, y, centerDotRadius, nodeCenterDotPaint)
        }
    }

    private fun drawActiveTooltip(canvas: Canvas, bandIndex: Int) {
        val x = nodePositionsX[bandIndex]
        val nodeY = nodePositionsY[bandIndex]
        val gain = bandGains[bandIndex]
        val freq = BAND_FREQUENCIES[bandIndex]

        val freqText = if (freq >= 1000f) {
            String.format("%.1f kHz", freq / 1000f)
        } else {
            "${freq.toInt()} Hz"
        }

        val gainText = if (gain > 0f) {
            String.format("+%.1f dB", gain)
        } else {
            String.format("%.1f dB", gain)
        }

        val text = "$freqText  $gainText"
        val textWidth = badgeTextPaint.measureText(text)
        val badgeW = textWidth + dpToPx(22f)
        val badgeH = dpToPx(24f)

        // Float above the node handle; if too close to top, place below node
        var badgeY = nodeY - dpToPx(28f)
        if (badgeY - badgeH / 2f < contentRect.top) {
            badgeY = nodeY + dpToPx(28f)
        }

        val badgeLeft = (x - badgeW / 2f).coerceIn(contentRect.left, contentRect.right - badgeW)
        val badgeRect = RectF(badgeLeft, badgeY - badgeH / 2f, badgeLeft + badgeW, badgeY + badgeH / 2f)

        val badgeRadius = dpToPx(12f)
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBackgroundPaint)
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeStrokePaint)
        canvas.drawText(text, badgeRect.centerX(), badgeRect.centerY() + dpToPx(4f), badgeTextPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y
                touchDownTime = SystemClock.uptimeMillis()

                // Find nearest band node within touch radius
                var bestBand = -1
                var bestDistanceSq = Float.MAX_VALUE
                val touchRadiusSq = dpToPx(TOUCH_SLOP_RADIUS_DP) * dpToPx(TOUCH_SLOP_RADIUS_DP)

                for (i in 0 until NUM_BANDS) {
                    val dx = touchX - nodePositionsX[i]
                    val dy = touchY - nodePositionsY[i]
                    val distSq = dx * dx + dy * dy
                    if (distSq < touchRadiusSq && distSq < bestDistanceSq) {
                        bestDistanceSq = distSq
                        bestBand = i
                    }
                }

                if (bestBand != -1) {
                    activeDraggedBand = bestBand
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateDraggedBand(touchY)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeDraggedBand != -1) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateDraggedBand(event.y)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDraggedBand != -1) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    activeDraggedBand = -1
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateDraggedBand(touchY: Float) {
        val newGain = yToGain(touchY)
        val roundedGain = (newGain * 10f).roundToInt() / 10f
        bandGains[activeDraggedBand] = roundedGain

        // Haptic feedback snap when crossing 0 dB
        if ((lastHapticGainDb < -0.3f && roundedGain >= 0f) || (lastHapticGainDb > 0.3f && roundedGain <= 0f)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastHapticGainDb = roundedGain

        // Convert gain dB to 0..100 progress (50 = neutral 0 dB, step = 0.3 dB)
        val progress = ((roundedGain / 0.3f) + 50f).roundToInt().coerceIn(0, 100)

        // Notify listener
        onBandGainChanged?.invoke(activeDraggedBand, roundedGain, progress)

        recomputeCurve()
        invalidate()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
