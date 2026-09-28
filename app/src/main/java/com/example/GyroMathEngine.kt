package com.example

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/**
 * Düşük Gecikmeli Jiroskop Entegrasyon ve Sensör Füzyon Motoru.
 *
 * X, Y ve Z eksenleri arasında seçim imkânı sunar, yön tersine çevirmeyi (invert) destekler,
 * sürekli entegrasyon ile direksiyon açısını hesaplar, anti-windup kilit mekanizması
 * ve merkez ölü bölge (deadzone) uygulayarak standart 16-bit HID analog değerine dönüştürür.
 */
class GyroMathEngine(
    var lockAngle: Float = 900.0f,
    private val alpha: Float = 0.20f,
    private val deadzoneDegrees: Float = 1.5f
) {

    enum class Axis(val label: String) {
        X("X Ekseni"),
        Y("Y Ekseni"),
        Z("Z Ekseni (Önerilen)")
    }

    data class Output(
        val totalRawAngle: Float,
        val filteredAngle: Float,
        val effectiveAngle: Float,
        val analogStickX: Short,
        val lockAngle: Float,
        val activeAxis: Axis,
        val isInverted: Boolean
    )

    var activeAxis: Axis = Axis.Z
    var isInverted: Boolean = false

    private var lastTimestampNs: Long = 0L
    private var totalAngle: Float = 0.0f
    private var filteredAngle: Float = 0.0f

    /**
     * Direksiyon açısını o anki pozisyonda 0.0° olarak sıfırlar (Merkez Kalibrasyonu).
     */
    fun calibrateCenter() {
        totalAngle = 0.0f
        filteredAngle = 0.0f
        lastTimestampNs = 0L
    }

    /**
     * Maksimum kilit açısını günceller (Örn: 180°, 540°, 900°, 1080°).
     */
    fun setLockAngleDegree(newLock: Float) {
        if (newLock > 0f) {
            lockAngle = newLock
        }
    }

    /**
     * Sensörden gelen 3 eksenli (X, Y, Z) açısal hız verilerini işler.
     *
     * @param gyroX Rad/s X ekseni
     * @param gyroY Rad/s Y ekseni
     * @param gyroZ Rad/s Z ekseni
     * @param timestampNs Donanım sensör zaman damgası (nanosaniye)
     */
    fun process(gyroX: Float, gyroY: Float, gyroZ: Float, timestampNs: Long): Output {
        val dtSeconds = if (lastTimestampNs > 0L && timestampNs > lastTimestampNs) {
            val delta = (timestampNs - lastTimestampNs) * 1e-9f
            if (delta > 0.1f) 0.0f else delta
        } else {
            0.0f
        }
        lastTimestampNs = timestampNs

        // Seçilen eksene göre açısal hızı al
        val rawVelocity = when (activeAxis) {
            Axis.X -> gyroX
            Axis.Y -> gyroY
            Axis.Z -> gyroZ
        }

        // Yön tersine çevirme kontrolü
        val directedVelocity = if (isInverted) -rawVelocity else rawVelocity

        // Açısal entegrasyon: deltaAngle = omega * dt * (180 / PI)
        val deltaDegrees = directedVelocity * dtSeconds * (180.0f / PI.toFloat())
        val halfLock = lockAngle / 2.0f

        // Anti-windup: Entegratörün kilit açısını aşmasını engeller
        totalAngle = (totalAngle + deltaDegrees).coerceIn(-halfLock, halfLock)

        // Üstel Alçak Geçiren Filtre
        filteredAngle = (alpha * totalAngle) + ((1.0f - alpha) * filteredAngle)
        val clampedFiltered = filteredAngle.coerceIn(-halfLock, halfLock)

        // Merkez Ölü Bölge (Deadzone) ve Kilit Doygunluğu
        val effectiveAngle = if (abs(clampedFiltered) <= deadzoneDegrees) {
            0.0f
        } else if (abs(clampedFiltered) >= halfLock - 0.1f) {
            sign(clampedFiltered) * halfLock
        } else {
            val s = sign(clampedFiltered)
            val magnitude = abs(clampedFiltered)
            val scaled = ((magnitude - deadzoneDegrees) / (halfLock - deadzoneDegrees)) * halfLock
            (s * scaled).coerceIn(-halfLock, halfLock)
        }

        // Sol Analog Çubuk X-Ekseni aralığına (-32768 ile +32767) haritalama
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
            lockAngle = lockAngle,
            activeAxis = activeAxis,
            isInverted = isInverted
        )
    }
}
