package com.wayne.musicdeck.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Battery-efficient motion detector that identifies intentional pocket/hand shakes to trigger actions.
 * Only registers with SensorManager when actively started (during playback).
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isListening = false

    private var shakeTimestamp: Long = 0
    private var shakeCount = 0
    private var lastDirectionChangeTime: Long = 0

    companion object {
        // Threshold in G's (1G = earth gravity ~9.8 m/s^2)
        // 2.5G requires a deliberate, crisp shake gesture, avoiding accidental triggers while walking.
        private const val SHAKE_THRESHOLD_GRAVITY = 2.4f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_COOLDOWN_MS = 1400
    }

    fun start() {
        if (isListening || accelerometer == null || sensorManager == null) return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        shakeCount = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Calculate g-force vector magnitude
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            
            // Ignore events too close to the last shake trigger (cooldown)
            if (shakeTimestamp + SHAKE_COOLDOWN_MS > now) {
                return
            }

            // Reset shake count if too much time passed between directional spikes
            if (lastDirectionChangeTime + SHAKE_SLOP_TIME_MS < now) {
                shakeCount = 0
            }

            lastDirectionChangeTime = now
            shakeCount++

            // Require at least 2 rapid acceleration peaks/reversals to prevent false positives from walking
            if (shakeCount >= 2) {
                shakeTimestamp = now
                shakeCount = 0
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
