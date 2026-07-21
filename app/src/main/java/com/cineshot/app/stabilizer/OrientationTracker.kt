package com.cineshot.app.stabilizer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log

/**
 * Tracks device orientation from the rotation-vector sensor and computes
 * the residual jitter (raw − smoothed) for EIS compensation.
 *
 * Uses an exponential moving-average (EMA) low-pass filter per Euler axis.
 * The time-constant dictates how fast the "desired" smooth orientation
 * tracks the raw reading — larger → more stable.
 */
class OrientationTracker {

    companion object {
        private const val TAG = "OrientationTracker"
    }

    // Raw Euler angles (radians) from the last sensor event.
    private var rawPitch = 0f
    private var rawRoll = 0f
    private var rawYaw = 0f

    // EMA-smoothed angles — the "intended" camera orientation.
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var smoothYaw = 0f

    private var pitchFilter = EMAFilter(0.3f)
    private var rollFilter = EMAFilter(0.3f)
    private var yawFilter = EMAFilter(0.3f)

    /** Jitter = raw − smooth (radians). Positive pitch = device tilted up. */
    val pitchJitter: Float get() = rawPitch - smoothPitch
    val rollJitter: Float get() = rawRoll - smoothRoll
    val yawJitter: Float get() = rawYaw - smoothYaw

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Update time-constants for the EMA filters.
     *
     * @param tcSeconds  Time-constant in seconds.  Larger = heavier smoothing.
     *                   Typical range: 0.1 (light) … 1.0 (very stable).
     */
    fun setTimeConstant(tcSeconds: Float) {
        pitchFilter = EMAFilter(tcSeconds)
        rollFilter = EMAFilter(tcSeconds)
        yawFilter = EMAFilter(tcSeconds)
        Log.d(TAG, "Time constant set to ${tcSeconds}s")
    }

    /**
     * Feed a new [SensorEvent] from TYPE_ROTATION_VECTOR or
     * TYPE_GAME_ROTATION_VECTOR.
     *
     * Call on the sensor event thread.
     */
    fun onSensorEvent(event: SensorEvent) {
        val vector = event.values.clone() // defensive copy

        // rotation-vector → matrix → Euler
        SensorManager.getRotationMatrixFromVector(rotationMatrix, vector)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // orientation[] = [azimuth(yaw), pitch, roll]
        rawYaw = orientation[0]
        rawPitch = orientation[1]
        rawRoll = orientation[2]

        // dt from sensor timestamp (nanoseconds → seconds)
        val dt = (event.timestamp / 1_000_000_000f).let { t ->
            // Approximate: since we don't store last timestamp here,
            // assume ~16ms for 60 Hz sensor.  A more precise implementation
            // would track lastEventTime.
            0.016f
        }

        smoothYaw = yawFilter.filter(rawYaw, dt)
        smoothPitch = pitchFilter.filter(rawPitch, dt)
        smoothRoll = rollFilter.filter(rawRoll, dt)
    }

    /** Reset all filters to current raw values. */
    fun reset() {
        pitchFilter.reset(rawPitch)
        rollFilter.reset(rawRoll)
        yawFilter.reset(rawYaw)
        smoothPitch = rawPitch
        smoothRoll = rawRoll
        smoothYaw = rawYaw
    }
}

// ── Exponential Moving Average ────────────────────────────────────────

private class EMAFilter(private val timeConstant: Float) {
    private var value = 0f
    private var initialized = false

    /**
     * @param raw  New sample.
     * @param dt   Seconds since last sample.
     * @return     Filtered value.
     */
    fun filter(raw: Float, dt: Float): Float {
        if (!initialized) {
            value = raw
            initialized = true
            return value
        }
        val alpha = if (timeConstant <= 0f) 1f else dt / (dt + timeConstant)
        value += alpha * (raw - value)
        return value
    }

    fun reset(raw: Float) {
        value = raw
        initialized = true
    }
}
