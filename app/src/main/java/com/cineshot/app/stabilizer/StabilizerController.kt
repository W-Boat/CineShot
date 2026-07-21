package com.cineshot.app.stabilizer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.cineshot.app.gl.VirtualViewport

/**
 * Virtual gimbal stabiliser (EIS).
 *
 * Reads the rotation-vector sensor, isolates hand-shake jitter via EMA
 * low-pass filtering, and counter-steers the viewport offset/roll to
 * cancel the shake within the available crop margin (4K→screen).
 *
 * **Strength presets**
 *   OFF      — no stabilisation.
 *   NATURAL  — light smoothing; retains organic handheld feel.
 *   STRONG   — heavy smoothing; near-tripod lock.
 */
class StabilizerController(
    private val sensorManager: SensorManager,
    private val viewportConsumer: (VirtualViewport) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "StabilizerController"

        // Crop margin in UV space (±25% = 4K→1080p headroom)
        private const val MAX_OFFSET_UV = 0.20f
        private const val MAX_ROLL_RAD = 0.08f

        // Approximate angular FoV for a typical phone main camera
        private const val HFOV_DEG = 65f
        private const val VFOV_DEG = 48f
    }

    enum class Strength(
        val label: String,
        val timeConstant: Float,   // seconds
        val gain: Float            // 0=none, 1=full compensation
    ) {
        OFF("关", 0f, 0f),
        NATURAL("呼吸感", 0.25f, 0.55f),
        STRONG("强稳定", 0.70f, 0.92f)
    }

    // ── State ─────────────────────────────────────────────────────────

    private val tracker = OrientationTracker()
    private var currentStrength = Strength.OFF
    private var isRegistered = false

    // ── Public API ────────────────────────────────────────────────────

    /** Start listening to rotation-vector sensor. */
    fun start() {
        if (isRegistered) return

        // Pick the best rotation sensor available.
        val sensor: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (sensor == null) {
            Log.w(TAG, "No rotation-vector sensor available — EIS disabled")
            return
        }

        sensorManager.registerListener(
            this, sensor,
            SensorManager.SENSOR_DELAY_GAME // ≈ 20 ms (50 Hz)
        )
        isRegistered = true
        Log.d(TAG, "Sensor registered: ${sensor.name}")
    }

    /** Stop listening and reset filters. */
    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        tracker.reset()
        Log.d(TAG, "Sensor unregistered")
    }

    /** Cycle through strength presets and return the new one. */
    fun cycleStrength(): Strength {
        val values = Strength.entries
        val nextIdx = (values.indexOf(currentStrength) + 1) % values.size
        currentStrength = values[nextIdx]
        tracker.setTimeConstant(currentStrength.timeConstant)
        if (currentStrength == Strength.OFF) {
            // Snap viewport back to neutral offset/roll immediately.
            viewportConsumer(VirtualViewport(offsetX = 0f, offsetY = 0f, roll = 0f))
        }
        Log.d(TAG, "Strength → ${currentStrength.label}")
        return currentStrength
    }

    /** Current strength preset (read-only). */
    val strength: Strength get() = currentStrength

    // ── SensorEventListener ────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (currentStrength == Strength.OFF) return

        tracker.onSensorEvent(event)

        val gain = currentStrength.gain

        // Convert angular jitter (radians) → UV offset.
        // A 1° shake at 65° HFOV ≈ 1/65 ≈ 1.5% of frame width.
        val degToUV = { deg: Float, fovDeg: Float ->
            (deg / fovDeg).coerceIn(-MAX_OFFSET_UV, MAX_OFFSET_UV)
        }

        val offsetXDeg = Math.toDegrees(tracker.yawJitter.toDouble()).toFloat()
        val offsetYDeg = Math.toDegrees(tracker.pitchJitter.toDouble()).toFloat()
        val rollCompensation = (tracker.rollJitter * gain)
            .coerceIn(-MAX_ROLL_RAD, MAX_ROLL_RAD)

        val offsetX = -degToUV(offsetXDeg * gain, HFOV_DEG)
        val offsetY = -degToUV(offsetYDeg * gain, VFOV_DEG)

        viewportConsumer(
            VirtualViewport(
                offsetX = offsetX,
                offsetY = offsetY,
                roll = rollCompensation
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
