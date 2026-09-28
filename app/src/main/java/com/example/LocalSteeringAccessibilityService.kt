package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Karton direksiyon dönüş açılarını oyunun anlayabileceği sanal dokunmatik (swipe/drag)
 * hareketlerine dönüştüren ve sisteme enjekte eden Erişilebilirlik Servisi (Input Injection).
 *
 * Root yetkisi gerektirmeden standart Android Accessibility Gesture API kullanır.
 */
class LocalSteeringAccessibilityService : AccessibilityService() {

    companion object {
        var instance: LocalSteeringAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private var isDispatching = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Dokunmatik enjeksiyon odaklı olduğu için erişilebilirlik olayları dinlenmez
    }

    override fun onInterrupt() {
        // Servis kesintiye uğradığında çağrılır
    }

    /**
     * Jiroskop dönüş açısını (normalizedRatio: -1.0f sol ... +1.0f sağ)
     * Truckers of Europe 3 gibi oyunların direksiyon alanına sanal dokunmatik kaydırma olarak enjekte eder.
     *
     * @param normalizedRatio -1.0f (Tam Sol) ile +1.0f (Tam Sağ) arası oran.
     * @param centerX Ekrandaki direksiyonun merkez X koordinatı.
     * @param centerY Ekrandaki direksiyonun merkez Y koordinatı.
     * @param radiusPx Direksiyonun dokunmatik çevirme yarıçapı.
     */
    fun injectSteeringGesture(
        normalizedRatio: Float,
        centerX: Float,
        centerY: Float,
        radiusPx: Float = 220f
    ) {
        if (isDispatching) return

        val clampedRatio = normalizedRatio.coerceIn(-1.0f, 1.0f)
        val targetX = centerX + (clampedRatio * radiusPx)
        val targetY = centerY

        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(targetX, targetY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 45L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        isDispatching = true
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                isDispatching = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                isDispatching = false
            }
        }, null)
    }

    /**
     * Ekranda belirtilen koordinata dokunma (basış) enjekte eder.
     */
    fun injectTap(x: Float, y: Float, durationMs: Long = 60L) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
