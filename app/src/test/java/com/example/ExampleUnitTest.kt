package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Unit tests verifying low-latency sensor fusion mathematics,
 * 900-degree lock clamping, center deadzone suppression, and HID stick mappings.
 */
class ExampleUnitTest {

    @Test
    fun testCenterCalibration() {
        val engine = GyroMathEngine(lockAngle = 900.0f)
        // Feed sample sensor data
        engine.process(1.5f, 1_000_000_000L)
        engine.process(1.5f, 1_020_000_000L)

        // Calibrate center
        engine.calibrateCenter()
        val resetOutput = engine.process(0.0f, 1_040_000_000L)

        assertEquals(0.0f, resetOutput.totalRawAngle, 0.001f)
        assertEquals(0.0f, resetOutput.filteredAngle, 0.001f)
        assertEquals(0.toShort(), resetOutput.analogStickX)
    }

    @Test
    fun testDeadzoneSuppression() {
        val engine = GyroMathEngine(lockAngle = 900.0f, deadzoneDegrees = 1.5f)
        // Slight movement within 1.5 degrees deadzone
        // dt = 0.02s (20ms), angular velocity = 0.5 rad/s -> ~0.57°
        engine.process(0.0f, 1_000_000_000L)
        val output = engine.process(0.5f, 1_020_000_000L)

        assertTrue(kotlin.math.abs(output.filteredAngle) < 1.5f)
        assertEquals(0.0f, output.effectiveAngle, 0.001f)
        assertEquals(0.toShort(), output.analogStickX)
    }

    @Test
    fun testLockAngleClamping900Degrees() {
        val engine = GyroMathEngine(lockAngle = 900.0f) // ±450°
        engine.process(0.0f, 1_000_000_000L)

        // Simulate intense rotation exceeding 450 degrees
        // dt = 1.0s, angular velocity = 20 rad/s -> ~1145 degrees
        var lastOutput = engine.process(0.0f, 1_000_000_000L)
        var time = 1_000_000_000L
        for (i in 1..200) {
            time += 20_000_000L // 20ms steps
            lastOutput = engine.process(10.0f, time)
        }

        // Must be clamped to +450°
        assertEquals(450.0f, lastOutput.effectiveAngle, 1.0f)
        // Analog stick X must reach full positive limit 32767
        assertEquals(32767.toShort(), lastOutput.analogStickX)
    }

    @Test
    fun testLockAngleClampingNegativeFullLock() {
        val engine = GyroMathEngine(lockAngle = 900.0f) // ±450°
        engine.process(0.0f, 1_000_000_000L)

        var lastOutput = engine.process(0.0f, 1_000_000_000L)
        var time = 1_000_000_000L
        for (i in 1..200) {
            time += 20_000_000L
            lastOutput = engine.process(-10.0f, time)
        }

        // Must be clamped to -450°
        assertEquals(-450.0f, lastOutput.effectiveAngle, 1.0f)
        // Analog stick X must reach full negative limit -32768
        assertEquals((-32768).toShort(), lastOutput.analogStickX)
    }

    @Test
    fun testSelectableLockAngle540Degrees() {
        val engine = GyroMathEngine(lockAngle = 540.0f) // ±270°
        assertEquals(540.0f, engine.lockAngle, 0.001f)

        engine.process(0.0f, 1_000_000_000L)
        var lastOutput = engine.process(0.0f, 1_000_000_000L)
        var time = 1_000_000_000L
        for (i in 1..200) {
            time += 20_000_000L
            lastOutput = engine.process(10.0f, time)
        }

        assertEquals(270.0f, lastOutput.effectiveAngle, 1.0f)
        assertEquals(32767.toShort(), lastOutput.analogStickX)
    }
}
