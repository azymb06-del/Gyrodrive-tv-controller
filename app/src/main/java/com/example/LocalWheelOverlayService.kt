package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Oyun açıkken ekran üzerinde duran şeffaf gaz, fren ve merkezleme katmanını (Overlay UI)
 * ve arka planda sürekli jiroskop dönüş entegrasyonunu yöneten Ön Plan Servisi (Foreground Service).
 */
class LocalWheelOverlayService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "local_wheel_overlay_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.ACTION_STOP_LOCAL_MODE"

        var isRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var sensorManager: SensorManager? = null
    private var gyroscopeSensor: Sensor? = null
    private var vibrator: Vibrator? = null
    private lateinit var prefs: SharedPreferences

    private lateinit var gyroEngine: GyroMathEngine

    // Ekrana eklenen şeffaf arayüz görünümleri
    private var hudView: View? = null
    private var brakePedalView: View? = null
    private var throttlePedalView: View? = null

    private var tvHudAngle: TextView? = null

    // Ekran Boyutları ve Direksiyon Enjeksiyon Noktası
    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080
    private var steeringCenterX: Float = 420f
    private var steeringCenterY: Float = 760f

    private var isVibrationEnabled: Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        prefs = getSharedPreferences("gyro_wheel_prefs", Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        calculateScreenDimensions()
        initEngines()
        setupSensors()
        setupVibrator()
        startForegroundServiceNotification()

        if (Settings.canDrawOverlays(this)) {
            createOverlayControls()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun calculateScreenDimensions() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        // Mobil yarış oyunlarında (Truckers of Europe vb.) sanal direksiyon genelde sol alt çeyrektedir
        steeringCenterX = screenWidth * 0.22f
        steeringCenterY = screenHeight * 0.72f
    }

    private fun initEngines() {
        val lockAngle = prefs.getFloat("lock_angle", 900.0f)
        val axisOrdinal = prefs.getInt("selected_axis", 2)
        val invert = prefs.getBoolean("invert_axis", false)
        isVibrationEnabled = prefs.getBoolean("vibration_enabled", true)

        gyroEngine = GyroMathEngine(lockAngle = lockAngle, alpha = 0.20f, deadzoneDegrees = 1.5f).apply {
            activeAxis = GyroMathEngine.Axis.values().getOrElse(axisOrdinal) { GyroMathEngine.Axis.Z }
            isInverted = invert
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyroscopeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun setupVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrate(durationMs: Long) {
        if (!isVibrationEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelName = "Yerel Direksiyon Modu"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Karton direksiyon yerel oyun katmanı aktif bildirimi"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, LocalWheelOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pStop = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gyro Wheel: Yerel Direksiyon Modu Aktif")
            .setContentText("Karton direksiyon ve ekran üstü katman çalışıyor. Dokununca uygulamaya döner.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kapat", pStop)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ==============================================================
    // ŞEFFAF KATMAN (OVERLAY UI) BİLEŞENLERİNİN OLUŞTURULMASI
    // ==============================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayControls() {
        val density = resources.displayMetrics.density

        val themeName = prefs.getString("app_theme", "ORIGINAL") ?: "ORIGINAL"
        val hudBg = when (themeName) {
            "AMOLED_BLACK" -> Color.parseColor("#EE000000")
            "CRIMSON_CARBON" -> Color.parseColor("#E612141B")
            else -> Color.parseColor("#CC151D2C")
        }
        val hudStroke = when (themeName) {
            "AMOLED_BLACK" -> Color.parseColor("#EF4444")
            "CRIMSON_CARBON" -> Color.parseColor("#FF1744")
            else -> Color.parseColor("#00E5FF")
        }
        val hudTextColor = when (themeName) {
            "AMOLED_BLACK" -> Color.parseColor("#EF4444")
            "CRIMSON_CARBON" -> Color.parseColor("#FFD700")
            else -> Color.parseColor("#00E5FF")
        }

        // 1. MERKEZ ÜST HUD KAPSAYICISI (Açı Bilgisi + 0° Kalibrasyon + Kapatma)
        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (16 * density).toInt()
        }

        val hudLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createPillBackground(hudBg, hudStroke, 16f)
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
        }

        tvHudAngle = TextView(this).apply {
            text = "0.0°"
            setTextColor(hudTextColor)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((4 * density).toInt(), 0, (10 * density).toInt(), 0)
        }
        hudLayout.addView(tvHudAngle)

        // 0° Sıfırla Butonu
        val btnCalibrate = TextView(this).apply {
            text = "🎯 0° SIFIRLA"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = createPillBackground(Color.parseColor("#334155"), Color.TRANSPARENT, 10f)
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                gyroEngine.calibrateCenter()
                vibrate(40)
            }
        }
        hudLayout.addView(btnCalibrate)

        // Kapat (X) Butonu
        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((12 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                stopSelf()
            }
        }
        hudLayout.addView(btnClose)

        makeDraggable(hudLayout, hudParams)
        windowManager?.addView(hudLayout, hudParams)
        hudView = hudLayout

        // 2. ŞEFFAF SOL FREN PEDALI (Draggable / Yarı Saydam)
        val brakeParams = WindowManager.LayoutParams(
            (110 * density).toInt(),
            (160 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = (20 * density).toInt()
            y = (20 * density).toInt()
        }

        val brakeLayout = FrameLayout(this).apply {
            background = createPillBackground(Color.parseColor("#66FF1744"), Color.parseColor("#FF1744"), 24f)
        }

        val brakeText = TextView(this).apply {
            text = "🛑\nFREN"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        brakeLayout.addView(brakeText)

        brakeLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = createPillBackground(Color.parseColor("#CCFF1744"), Color.WHITE, 24f)
                    vibrate(30)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = createPillBackground(Color.parseColor("#66FF1744"), Color.parseColor("#FF1744"), 24f)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(brakeLayout, brakeParams)
        brakePedalView = brakeLayout

        // 3. ŞEFFAF SAĞ GAZ PEDALI (Draggable / Yarı Saydam)
        val throttleParams = WindowManager.LayoutParams(
            (110 * density).toInt(),
            (160 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (20 * density).toInt()
            y = (20 * density).toInt()
        }

        val throttleLayout = FrameLayout(this).apply {
            background = createPillBackground(Color.parseColor("#6600E676"), Color.parseColor("#00E676"), 24f)
        }

        val throttleText = TextView(this).apply {
            text = "⚡\nGAZ"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        throttleLayout.addView(throttleText)

        throttleLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = createPillBackground(Color.parseColor("#CC00E676"), Color.WHITE, 24f)
                    vibrate(30)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = createPillBackground(Color.parseColor("#6600E676"), Color.parseColor("#00E676"), 24f)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(throttleLayout, throttleParams)
        throttlePedalView = throttleLayout
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(view, params)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun createPillBackground(bgColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(bgColor)
            if (strokeColor != Color.TRANSPARENT) {
                setStroke((1.5f * density).toInt(), strokeColor)
            }
        }
    }

    // ==============================================================
    // SENSÖR VE DOKUNMATİK ENJEKSİYON (INPUT INJECTION)
    // ==============================================================
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // Jiroskop verisini işle
        val result = gyroEngine.process(
            gyroX = event.values[0],
            gyroY = event.values[1],
            gyroZ = event.values[2],
            timestampNs = event.timestamp
        )

        // HUD üzerindeki canlı açıyı güncelle
        val angleText = String.format(Locale.US, "%+.1f°", result.effectiveAngle)
        tvHudAngle?.text = angleText

        // Erişilebilirlik Servisi üzerinden Truckers of Europe 3 gibi oyunlara dokunmatik hareket enjekte et
        val halfLock = result.lockAngle / 2.0f
        val normalizedRatio = (result.effectiveAngle / halfLock).coerceIn(-1.0f, 1.0f)

        LocalSteeringAccessibilityService.instance?.injectSteeringGesture(
            normalizedRatio = normalizedRatio,
            centerX = steeringCenterX,
            centerY = steeringCenterY,
            radiusPx = screenWidth * 0.12f
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        isRunning = false
        sensorManager?.unregisterListener(this)

        hudView?.let { windowManager?.removeView(it) }
        brakePedalView?.let { windowManager?.removeView(it) }
        throttlePedalView?.let { windowManager?.removeView(it) }

        super.onDestroy()
    }
}
