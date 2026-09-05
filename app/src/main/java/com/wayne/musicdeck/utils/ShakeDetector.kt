package com.wayne.musicdeck.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Intelligent, pocket-safe motion detector that identifies intentional hand shakes to trigger actions.
 *
 * Safety Mechanisms:
 * 1. Pocket Guard: Listens to the device proximity sensor. When the phone is in a pocket, bag,
 *    or face-down, motion events are immediately ignored, preventing walking misfires.
 * 2. Multi-Axis Jerk & Direction Reversals: Requires rapid acceleration reversals (back-and-forth wrist motion)
 *    within a tight window, filtering out smooth rhythmic movement like footsteps or car rides.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var isListening = false

    // Pocket guard state
    private var isCoveredInPocket = false

    // Motion tracking state
    private var lastShakeTimestamp: Long = 0
    private var directionChangeCount = 0
    private var firstDirectionChangeTime: Long = 0
    private var lastDirectionChangeTime: Long = 0

    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var prevDirection = 0 // -1 or 1

    companion object {
        // High enough to filter ordinary movement, low enough to trigger with a deliberate wrist shake
        private const val SHAKE_THRESHOLD_G = 2.8f
        // Minimum jerk (rate of change of acceleration) between sensor events to count as a vigorous motion
        private const val MIN_JERK_THRESHOLD = 1.3f
        // Time window in which direction reversals must occur
        private const val SHAKE_WINDOW_MS = 650L
        // Cooldown between valid shake triggers
        private const val SHAKE_COOLDOWN_MS = 1800L
        // Proximity distance threshold (typically < 4cm or < maxRange indicates covered)
        private const val POCKET_PROXIMITY_THRESHOLD_CM = 4.0f
    }

    fun start() {
        if (isListening || sensorManager == null) return

        // Register proximity sensor for Pocket Guard
        proximitySensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Register accelerometer for gesture detection
        accelerometer?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        isListening = true
        resetTracking()
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        isCoveredInPocket = false
        resetTracking()
    }

    private fun resetTracking() {
        directionChangeCount = 0
        firstDirectionChangeTime = 0
        lastDirectionChangeTime = 0
        prevDirection = 0
        prevX = 0f
        prevY = 0f
        prevZ = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // 1. Handle Proximity Sensor (Pocket Guard)
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: POCKET_PROXIMITY_THRESHOLD_CM
            isCoveredInPocket = distance < maxRange && distance < POCKET_PROXIMITY_THRESHOLD_CM
            if (isCoveredInPocket) {
                resetTracking()
            }
            return
        }

        // 2. Handle Accelerometer
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // If the phone is inside a pocket, ignore all motion completely
            if (isCoveredInPocket) return

            val now = System.currentTimeMillis()
            if (lastShakeTimestamp + SHAKE_COOLDOWN_MS > now) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            // Calculate change in acceleration (jerk)
            val dX = gX - prevX
            val dY = gY - prevY
            val dZ = gZ - prevZ
            val jerk = sqrt((dX * dX + dY * dY + dZ * dZ).toDouble()).toFloat()

            // Update previous coordinates
            prevX = gX
            prevY = gY
            prevZ = gZ

            // Check if acceleration and jerk cross deliberate shake thresholds
            if (gForce > SHAKE_THRESHOLD_G && jerk > MIN_JERK_THRESHOLD) {
                // Find dominant axis of rapid change
                val dominantDelta = when {
                    abs(dX) >= abs(dY) && abs(dX) >= abs(dZ) -> dX
                    abs(dY) >= abs(dX) && abs(dY) >= abs(dZ) -> dY
                    else -> dZ
                }
                val currentDirection = if (dominantDelta > 0) 1 else -1

                // Check for direction reversal (wrist flicked back the other way)
                if (prevDirection != 0 && currentDirection != prevDirection) {
                    if (directionChangeCount == 0) {
                        firstDirectionChangeTime = now
                    }

                    // Expire old tracking window if too much time passed
                    if (now - firstDirectionChangeTime > SHAKE_WINDOW_MS) {
                        directionChangeCount = 1
                        firstDirectionChangeTime = now
                    } else {
                        directionChangeCount++
                    }

                    lastDirectionChangeTime = now

                    // Require at least 3 rapid reversals (e.g. Left -> Right -> Left -> Right)
                    if (directionChangeCount >= 3) {
                        lastShakeTimestamp = now
                        resetTracking()
                        onShake()
                    }
                }

                prevDirection = currentDirection
            } else {
                // Decay tracking if motion stopped or window expired
                if (now - lastDirectionChangeTime > SHAKE_WINDOW_MS) {
                    resetTracking()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
