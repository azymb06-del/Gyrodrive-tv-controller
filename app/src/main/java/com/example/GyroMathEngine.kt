package com.example

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/**
 * Low-Latency Gyroscope Integration and Sensor Fusion Engine.
 *
 * Integrates Gyroscope angular velocity around the steering axis over delta-time (dt),
 * applies an exponential low-pass filter, enforces a deadzone around zero,
 * clamps within user-selectable wheel locks (180°, 540°, 900°), and maps the
 * steering angle to standard HID Gamepad 16-bit signed integer range (-32768 to +32767).
 */
class GyroMathEngine(
    var lockAngle: Float = 900.0f,
    private val alpha: Float = 0.18f,
    private val deadzoneDegrees: Float = 1.5f
) {

    data class Output(
        val totalRawAngle: Float,
        val filteredAngle: Float,
        val effectiveAngle: Float,
        val analogStickX: Short,
        val lockAngle: Float
    )

    private var lastTimestampNs: Long = 0L
    private var totalAngle: Float = 0.0f
    private var filteredAngle: Float = 0.0f

    /**
     * Resets the accumulated steering angle to zero (center calibration).
     */
    fun calibrateCenter() {
        totalAngle = 0.0f
        filteredAngle = 0.0f
        lastTimestampNs = 0L
    }

    /**
     * Updates the maximum steering lock angle (e.g. 180°, 540°, 900°).
     */
    fun setLockAngleDegree(newLock: Float) {
        if (newLock > 0f) {
            lockAngle = newLock
        }
    }

    /**
     * Processes an incoming gyroscope event on the steering axis.
     *
     * @param gyroXRadPerSec Angular velocity in radians per second (Sensor.TYPE_GYROSCOPE X-axis).
     * @param timestampNs Event timestamp in nanoseconds from hardware sensor event.
     * @return Output containing the integrated angles and converted 16-bit analog stick value.
     */
    fun process(gyroXRadPerSec: Float, timestampNs: Long): Output {
        val dtSeconds = if (lastTimestampNs > 0L && timestampNs > lastTimestampNs) {
            val delta = (timestampNs - lastTimestampNs) * 1e-9f
            // Reject anomalous delta time jumps (e.g. when app resumes)
            if (delta > 0.1f) 0.0f else delta
        } else {
            0.0f
        }
        lastTimestampNs = timestampNs

        // Continuous steering integration: totalAngle += gyroX_rad_per_sec * dt_seconds * (180.0 / PI)
        val deltaDegrees = gyroXRadPerSec * dtSeconds * (180.0f / PI.toFloat())
        val halfLock = lockAngle / 2.0f

        // Anti-windup: clamp continuous integrated angle to mechanical lock stops
        totalAngle = (totalAngle + deltaDegrees).coerceIn(-halfLock, halfLock)

        // Exponential Low-Pass Filter: filteredAngle = alpha * rawAngle + (1 - alpha) * prevAngle
        filteredAngle = (alpha * totalAngle) + ((1.0f - alpha) * filteredAngle)
        val clampedFiltered = filteredAngle.coerceIn(-halfLock, halfLock)

        // Center Deadzone computation & Lock-stop saturation
        val effectiveAngle = if (abs(clampedFiltered) <= deadzoneDegrees) {
            0.0f
        } else if (abs(clampedFiltered) >= halfLock - 0.1f) {
            // Saturated at mechanical lock stops (-halfLock to +halfLock)
            sign(clampedFiltered) * halfLock
        } else {
            val s = sign(clampedFiltered)
            val magnitude = abs(clampedFiltered)
            val scaled = ((magnitude - deadzoneDegrees) / (halfLock - deadzoneDegrees)) * halfLock
            (s * scaled).coerceIn(-halfLock, halfLock)
        }

        // Map clamped angle to Left Analog Stick X-Axis integer range (-32768 to +32767)
        val normalized = (effectiveAngle / halfLock).coerceIn(-1.0f, 1.0f)
        val rawStickInt = if (normalized >= 1.0f) {
            32767
        } else if (normalized <= -1.0f) {
            -32768
        } else if (normalized >= 0f) {
            (normalized * 32767.0f).toInt().coerceIn(0, 32767)
        } else {
            (normalized * 32768.0f).toInt().coerceIn(-32768, 0)
        }
        val analogStickX = rawStickInt.toShort()

        return Output(
            totalRawAngle = totalAngle,
            filteredAngle = filteredAngle,
            effectiveAngle = effectiveAngle,
            analogStickX = analogStickX,
            lockAngle = lockAngle
        )
    }
}
