package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jiroskop Eksen Seçimi (X, Y, Z), Tersine Çevirme, 900° Kilit ve
 * 16-Buton HID Rapor paketini doğrulayan birim testleri.
 */
class ExampleUnitTest {

    @Test
    fun testAxisSelectionAndInversion() {
        val engine = GyroMathEngine(lockAngle = 900.0f)

        // 1. Z Ekseni Testi (Varsayılan)
        engine.activeAxis = GyroMathEngine.Axis.Z
        engine.process(0f, 0f, 0f, 1_000_000_000L)
        var outZ = engine.process(0f, 0f, 0f, 1_000_000_000L)
        var t = 1_000_000_000L
        for (i in 1..20) {
            t += 20_000_000L
            outZ = engine.process(0f, 0f, 5.0f, t)
        }
        assertTrue(outZ.effectiveAngle > 0f)

        // 2. X Ekseni Seçimi
        engine.calibrateCenter()
        engine.activeAxis = GyroMathEngine.Axis.X
        engine.process(0f, 0f, 0f, 1_000_000_000L)
        var outX = engine.process(0f, 0f, 0f, 1_000_000_000L)
        t = 1_000_000_000L
        for (i in 1..20) {
            t += 20_000_000L
            outX = engine.process(5.0f, 0f, 0f, t)
        }
        assertTrue(outX.effectiveAngle > 0f)

        // 3. Y Ekseni ve Yön Tersine Çevirme (Inversion) Testi
        engine.calibrateCenter()
        engine.activeAxis = GyroMathEngine.Axis.Y
        engine.isInverted = true
        engine.process(0f, 0f, 0f, 1_000_000_000L)
        var outY = engine.process(0f, 0f, 0f, 1_000_000_000L)
        t = 1_000_000_000L
        for (i in 1..20) {
            t += 20_000_000L
            outY = engine.process(0f, 5.0f, 0f, t)
        }
        assertTrue(outY.effectiveAngle < 0f) // Ters çevrildiği için negatif olmalı
    }

    @Test
    fun testCenterCalibration() {
        val engine = GyroMathEngine(lockAngle = 900.0f)
        engine.activeAxis = GyroMathEngine.Axis.Z
        engine.process(0f, 0f, 1.5f, 1_000_000_000L)
        engine.process(0f, 0f, 1.5f, 1_020_000_000L)

        // Kalibrasyon sıfırlama
        engine.calibrateCenter()
        val resetOutput = engine.process(0f, 0f, 0.0f, 1_040_000_000L)

        assertEquals(0.0f, resetOutput.totalRawAngle, 0.001f)
        assertEquals(0.0f, resetOutput.filteredAngle, 0.001f)
        assertEquals(0.toShort(), resetOutput.analogStickX)
    }

    @Test
    fun testDeadzoneSuppression() {
        val engine = GyroMathEngine(lockAngle = 900.0f, deadzoneDegrees = 1.5f)
        engine.activeAxis = GyroMathEngine.Axis.Z
        engine.process(0f, 0f, 0.0f, 1_000_000_000L)
        val output = engine.process(0f, 0f, 0.5f, 1_020_000_000L)

        assertTrue(kotlin.math.abs(output.filteredAngle) < 1.5f)
        assertEquals(0.0f, output.effectiveAngle, 0.001f)
        assertEquals(0.toShort(), output.analogStickX)
    }

    @Test
    fun testLockAngleClamping900Degrees() {
        val engine = GyroMathEngine(lockAngle = 900.0f) // ±450°
        engine.activeAxis = GyroMathEngine.Axis.Z
        engine.process(0f, 0f, 0.0f, 1_000_000_000L)

        var lastOutput = engine.process(0f, 0f, 0.0f, 1_000_000_000L)
        var time = 1_000_000_000L
        for (i in 1..200) {
            time += 20_000_000L
            lastOutput = engine.process(0f, 0f, 10.0f, time)
        }

        // +450° ve 32767 limitine kilitlenmeli
        assertEquals(450.0f, lastOutput.effectiveAngle, 1.0f)
        assertEquals(32767.toShort(), lastOutput.analogStickX)
    }

    @Test
    fun testLockAngleClampingNegativeFullLock() {
        val engine = GyroMathEngine(lockAngle = 900.0f) // ±450°
        engine.activeAxis = GyroMathEngine.Axis.Z
        engine.process(0f, 0f, 0.0f, 1_000_000_000L)

        var lastOutput = engine.process(0f, 0f, 0.0f, 1_000_000_000L)
        var time = 1_000_000_000L
        for (i in 1..200) {
            time += 20_000_000L
            lastOutput = engine.process(0f, 0f, -10.0f, time)
        }

        // -450° ve -32768 limitine kilitlenmeli
        assertEquals(-450.0f, lastOutput.effectiveAngle, 1.0f)
        assertEquals((-32768).toShort(), lastOutput.analogStickX)
    }
}
