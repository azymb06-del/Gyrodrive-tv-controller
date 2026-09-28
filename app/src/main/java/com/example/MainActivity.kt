package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

/**
 * Ana Menü, Bluetooth Cihaz Seçici, Jiroskop Eksen Seçimi (X, Y, Z),
 * Özelleştirilebilir Hazır Tuş Profilleri ve Titreşim Motorlu Sürüş Kokpiti.
 */
class MainActivity : AppCompatActivity(), SensorEventListener, BluetoothController.Listener {

    // Çekirdek Motorlar
    private lateinit var gyroEngine: GyroMathEngine
    private lateinit var bluetoothController: BluetoothController
    private var sensorManager: SensorManager? = null
    private var gyroscopeSensor: Sensor? = null
    private var vibrator: Vibrator? = null
    private lateinit var prefs: SharedPreferences

    enum class AppTheme(
        val id: String,
        val displayName: String,
        val rootBg: String,
        val topBarBg: String,
        val cardBg: String,
        val stroke: String,
        val primaryAccent: String,
        val secondaryAccent: String,
        val telemetryAccent: String,
        val textPrimary: String,
        val textMuted: String,
        val wheelRim: String,
        val wheelHub: String,
        val wheelMarker: String
    ) {
        ORIGINAL(
            id = "ORIGINAL",
            displayName = "⚡ Orijinal Neon",
            rootBg = "#0B0F19",
            topBarBg = "#151D2C",
            cardBg = "#151D2C",
            stroke = "#334155",
            primaryAccent = "#00E5FF",
            secondaryAccent = "#00E676",
            telemetryAccent = "#FF9100",
            textPrimary = "#F8FAFC",
            textMuted = "#94A3B8",
            wheelRim = "#00E5FF",
            wheelHub = "#1E293B",
            wheelMarker = "#FF9100"
        ),
        AMOLED_BLACK(
            id = "AMOLED_BLACK",
            displayName = "🌑 Göz Yormayan Siyah",
            rootBg = "#000000",
            topBarBg = "#0A0A0A",
            cardBg = "#0E0E0E",
            stroke = "#2A2A2A",
            primaryAccent = "#EF4444",
            secondaryAccent = "#D4D4D8",
            telemetryAccent = "#FF5722",
            textPrimary = "#E4E4E7",
            textMuted = "#71717A",
            wheelRim = "#DC2626",
            wheelHub = "#18181B",
            wheelMarker = "#F4F4F5"
        ),
        CRIMSON_CARBON(
            id = "CRIMSON_CARBON",
            displayName = "🏎️ Karbon Kırmızı (GT)",
            rootBg = "#0E1015",
            topBarBg = "#181A20",
            cardBg = "#181A20",
            stroke = "#571B27",
            primaryAccent = "#FF1744",
            secondaryAccent = "#FFD700",
            telemetryAccent = "#FFD700",
            textPrimary = "#FFFFFF",
            textMuted = "#A1A1AA",
            wheelRim = "#FF1744",
            wheelHub = "#27272A",
            wheelMarker = "#FFD700"
        )
    }

    private var currentTheme: AppTheme = AppTheme.ORIGINAL

    // Ekran Kapsayıcıları
    private lateinit var rootContainer: View
    private lateinit var menuTopBar: View
    private lateinit var viewMainMenu: View
    private lateinit var viewCockpit: View

    // Tema Seçici Bileşenleri
    private lateinit var cardTheme: View
    private lateinit var tvThemeHeader: TextView
    private lateinit var tvThemeActiveBadge: TextView
    private lateinit var tvThemeDescription: TextView
    private lateinit var groupTheme: MaterialButtonToggleGroup
    private lateinit var btnThemeOriginal: MaterialButton
    private lateinit var btnThemeAmoled: MaterialButton
    private lateinit var btnThemeCrimson: MaterialButton

    // Ana Menü Bileşenleri
    private lateinit var tvMenuConnectionBadge: TextView
    private lateinit var btnMenuLocalMode: MaterialButton
    private lateinit var btnLaunchCockpit: MaterialButton
    private lateinit var pbMenuScanning: ProgressBar
    private lateinit var btnMenuScan: MaterialButton
    private lateinit var tvMenuScanStatus: TextView
    private lateinit var rvMenuDevices: RecyclerView
    private lateinit var tvMenuNoDevices: TextView
    private lateinit var groupAxis: MaterialButtonToggleGroup
    private lateinit var btnAxisX: MaterialButton
    private lateinit var btnAxisY: MaterialButton
    private lateinit var btnAxisZ: MaterialButton
    private lateinit var switchInvertAxis: SwitchMaterial
    private lateinit var tvMenuLiveAngle: TextView
    private lateinit var btnMenuCalibrate: MaterialButton
    private lateinit var tvMenuLockLabel: TextView
    private lateinit var sliderMenuLock: Slider
    private lateinit var groupProfiles: MaterialButtonToggleGroup
    private lateinit var tvProfileDescription: TextView
    private lateinit var chipGroupButtons: ChipGroup
    private lateinit var switchVibration: SwitchMaterial
    private lateinit var switchLockVibration: SwitchMaterial
    private lateinit var switchPedalVibration: SwitchMaterial

    // Yerel Mod (Karton Direksiyon) Bileşenleri
    private lateinit var cardLocalMode: View
    private lateinit var tvLocalModeStatusBadge: TextView
    private lateinit var tvOverlayPermissionStatus: TextView
    private lateinit var btnGrantOverlayPerm: MaterialButton
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnOpenAccessibilitySettings: MaterialButton
    private lateinit var btnStartLocalMode: MaterialButton

    // Kartlar (Stil için)
    private lateinit var cardBluetooth: View
    private lateinit var cardGyro: View
    private lateinit var cardProfiles: View
    private lateinit var cardVibration: View

    // Kokpit Bileşenleri
    private lateinit var cockpitTopBar: View
    private lateinit var btnBackToMenu: MaterialButton
    private lateinit var btnCockpitCalibrate: MaterialButton
    private lateinit var tvCockpitAxisBadge: TextView
    private lateinit var tvCockpitConnectionStatus: TextView
    private lateinit var containerCockpitExtraButtons: LinearLayout
    private lateinit var btnCockpitBrake: View
    private lateinit var btnCockpitThrottle: View
    private lateinit var btnPaddleShiftDown: MaterialButton
    private lateinit var btnPaddleShiftUp: MaterialButton
    private lateinit var cockpitCenterPanel: View
    private lateinit var ivCockpitWheel: ImageView
    private lateinit var tvCockpitAngle: TextView
    private lateinit var cockpitProgressIndicator: LinearProgressIndicator
    private lateinit var tvCockpitTelemetry: TextView
    private lateinit var rowCockpitQuickActions: LinearLayout

    // Bluetooth Cihaz Listesi Adaptörü
    private lateinit var deviceAdapter: DeviceAdapter

    // Giriş ve Tuş Bitmask Yönetimi
    private var activeButtonsBitmask: Int = 0
    private var currentActiveButtonsMask: Int = 0
    private var latestStickX: Short = 0
    private var lastHitLockLimit: Boolean = false

    // Titreşim Ayarları
    private var isVibrationEnabled: Boolean = true
    private var isLockVibrationEnabled: Boolean = true
    private var isPedalVibrationEnabled: Boolean = true

    // Profil Tanımları
    data class PresetProfile(
        val id: String,
        val name: String,
        val description: String,
        val buttonMask: Int
    )

    private val profiles = listOf(
        PresetProfile(
            id = "RACING",
            name = "Yarış Standart",
            description = "Yarış Standart: Gaz (A), Fren (B), El Freni (X), Nitro (Y), Vitesler (LB/RB), Menü (Start), Harita (Select).",
            buttonMask = BluetoothController.BTN_A or BluetoothController.BTN_B or
                    BluetoothController.BTN_X or BluetoothController.BTN_Y or
                    BluetoothController.BTN_LB or BluetoothController.BTN_RB or
                    BluetoothController.BTN_START or BluetoothController.BTN_SELECT
        ),
        PresetProfile(
            id = "SIM_PRO",
            name = "Simülasyon / Pro",
            description = "Simülasyon / Pro: Gaz (A), Fren (B), El Freni (X), Nitro/DRS (Y), Kulakçık Vitesler (LB/RB), Debriyaj (LT), Menü (Start), Kamera (Select).",
            buttonMask = BluetoothController.BTN_A or BluetoothController.BTN_B or
                    BluetoothController.BTN_X or BluetoothController.BTN_Y or
                    BluetoothController.BTN_LB or BluetoothController.BTN_RB or
                    BluetoothController.BTN_LT or BluetoothController.BTN_START or BluetoothController.BTN_SELECT
        ),
        PresetProfile(
            id = "ARCADE",
            name = "Arcade / Kart",
            description = "Arcade / Mario Kart: Gaz (A), Fren (B), Drift (RB), Korna/Eşya (X), Nitro (Y), Menü (Start).",
            buttonMask = BluetoothController.BTN_A or BluetoothController.BTN_B or
                    BluetoothController.BTN_X or BluetoothController.BTN_Y or
                    BluetoothController.BTN_RB or BluetoothController.BTN_START
        ),
        PresetProfile(
            id = "CUSTOM",
            name = "Özel Profil",
            description = "Özel Profil: İstediğiniz tuşları açıp kapatabilirsiniz.",
            buttonMask = 0
        )
    )

    private var activeProfileId: String = "RACING"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth ve Konum izinleri gereklidir.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("gyro_wheel_prefs", Context.MODE_PRIVATE)

        initEngines()
        bindViews()
        applyStyling()
        setupListeners()
        setupSensors()
        setupVibrator()
        loadSavedSettings()
        setupBackHandler()
        checkAndRequestPermissions()
    }

    private fun initEngines() {
        gyroEngine = GyroMathEngine(lockAngle = 900.0f, alpha = 0.20f, deadzoneDegrees = 1.5f)
        bluetoothController = BluetoothController(this)
        bluetoothController.listener = this
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.rootContainer)
        menuTopBar = findViewById(R.id.menuTopBar)
        viewMainMenu = findViewById(R.id.viewMainMenu)
        viewCockpit = findViewById(R.id.viewCockpit)

        // Tema Bileşenleri
        cardTheme = findViewById(R.id.cardTheme)
        tvThemeHeader = findViewById(R.id.tvThemeHeader)
        tvThemeActiveBadge = findViewById(R.id.tvThemeActiveBadge)
        tvThemeDescription = findViewById(R.id.tvThemeDescription)
        groupTheme = findViewById(R.id.groupTheme)
        btnThemeOriginal = findViewById(R.id.btnThemeOriginal)
        btnThemeAmoled = findViewById(R.id.btnThemeAmoled)
        btnThemeCrimson = findViewById(R.id.btnThemeCrimson)

        // Ana Menü
        tvMenuConnectionBadge = findViewById(R.id.tvMenuConnectionBadge)
        btnMenuLocalMode = findViewById(R.id.btnMenuLocalMode)
        btnLaunchCockpit = findViewById(R.id.btnLaunchCockpit)
        pbMenuScanning = findViewById(R.id.pbMenuScanning)
        btnMenuScan = findViewById(R.id.btnMenuScan)
        tvMenuScanStatus = findViewById(R.id.tvMenuScanStatus)
        rvMenuDevices = findViewById(R.id.rvMenuDevices)
        tvMenuNoDevices = findViewById(R.id.tvMenuNoDevices)
        groupAxis = findViewById(R.id.groupAxis)
        btnAxisX = findViewById(R.id.btnAxisX)
        btnAxisY = findViewById(R.id.btnAxisY)
        btnAxisZ = findViewById(R.id.btnAxisZ)
        switchInvertAxis = findViewById(R.id.switchInvertAxis)
        tvMenuLiveAngle = findViewById(R.id.tvMenuLiveAngle)
        btnMenuCalibrate = findViewById(R.id.btnMenuCalibrate)
        tvMenuLockLabel = findViewById(R.id.tvMenuLockLabel)
        sliderMenuLock = findViewById(R.id.sliderMenuLock)
        groupProfiles = findViewById(R.id.groupProfiles)
        tvProfileDescription = findViewById(R.id.tvProfileDescription)
        chipGroupButtons = findViewById(R.id.chipGroupButtons)
        switchVibration = findViewById(R.id.switchVibration)
        switchLockVibration = findViewById(R.id.switchLockVibration)
        switchPedalVibration = findViewById(R.id.switchPedalVibration)

        // Yerel Mod (Karton Direksiyon)
        cardLocalMode = findViewById(R.id.cardLocalMode)
        tvLocalModeStatusBadge = findViewById(R.id.tvLocalModeStatusBadge)
        tvOverlayPermissionStatus = findViewById(R.id.tvOverlayPermissionStatus)
        btnGrantOverlayPerm = findViewById(R.id.btnGrantOverlayPerm)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        btnStartLocalMode = findViewById(R.id.btnStartLocalMode)

        cardBluetooth = findViewById(R.id.cardBluetooth)
        cardGyro = findViewById(R.id.cardGyro)
        cardProfiles = findViewById(R.id.cardProfiles)
        cardVibration = findViewById(R.id.cardVibration)

        // Kokpit
        cockpitTopBar = findViewById(R.id.cockpitTopBar)
        btnBackToMenu = findViewById(R.id.btnBackToMenu)
        btnCockpitCalibrate = findViewById(R.id.btnCockpitCalibrate)
        tvCockpitAxisBadge = findViewById(R.id.tvCockpitAxisBadge)
        tvCockpitConnectionStatus = findViewById(R.id.tvCockpitConnectionStatus)
        containerCockpitExtraButtons = findViewById(R.id.containerCockpitExtraButtons)
        btnCockpitBrake = findViewById(R.id.btnCockpitBrake)
        btnCockpitThrottle = findViewById(R.id.btnCockpitThrottle)
        btnPaddleShiftDown = findViewById(R.id.btnPaddleShiftDown)
        btnPaddleShiftUp = findViewById(R.id.btnPaddleShiftUp)
        cockpitCenterPanel = findViewById(R.id.cockpitCenterPanel)
        ivCockpitWheel = findViewById(R.id.ivCockpitWheel)
        tvCockpitAngle = findViewById(R.id.tvCockpitAngle)
        cockpitProgressIndicator = findViewById(R.id.cockpitProgressIndicator)
        tvCockpitTelemetry = findViewById(R.id.tvCockpitTelemetry)
        rowCockpitQuickActions = findViewById(R.id.rowCockpitQuickActions)
    }

    private fun applyStyling() {
        applyTheme(currentTheme)
    }

    fun applyTheme(theme: AppTheme) {
        currentTheme = theme

        val darkBg = Color.parseColor(theme.cardBg)
        val stroke = Color.parseColor(theme.stroke)
        val primaryAccent = Color.parseColor(theme.primaryAccent)

        // Arka Planlar & Kapsayıcılar
        rootContainer.setBackgroundColor(Color.parseColor(theme.rootBg))
        menuTopBar.setBackgroundColor(Color.parseColor(theme.topBarBg))
        cockpitTopBar.background = createRoundedBox(Color.parseColor(theme.topBarBg), stroke, 14f)
        cockpitCenterPanel.background = createRoundedBox(darkBg, stroke, 20f)

        // Kartlar
        cardTheme.background = createRoundedBox(darkBg, primaryAccent, 16f)
        cardLocalMode.background = createRoundedBox(darkBg, Color.parseColor(if (theme == AppTheme.AMOLED_BLACK) "#404040" else "#B45309"), 16f)
        cardBluetooth.background = createRoundedBox(darkBg, stroke, 16f)
        cardGyro.background = createRoundedBox(darkBg, stroke, 16f)
        cardProfiles.background = createRoundedBox(darkBg, stroke, 16f)
        cardVibration.background = createRoundedBox(darkBg, stroke, 16f)

        // Rozetler & Durum Göstergeleri
        tvThemeHeader.setTextColor(primaryAccent)
        tvThemeActiveBadge.text = theme.displayName
        tvThemeActiveBadge.setTextColor(primaryAccent)
        tvThemeActiveBadge.background = createRoundedBox(Color.parseColor(theme.topBarBg), stroke, 10f)

        tvLocalModeStatusBadge.background = createRoundedBox(Color.parseColor(theme.topBarBg), stroke, 10f)
        tvMenuConnectionBadge.background = createRoundedBox(Color.parseColor(theme.topBarBg), stroke, 10f)
        tvCockpitConnectionStatus.background = createRoundedBox(Color.parseColor(theme.topBarBg), stroke, 10f)

        // Vurgular & Buton Renkleri
        tvCockpitAngle.setTextColor(primaryAccent)
        tvMenuLiveAngle.setTextColor(primaryAccent)

        btnLaunchCockpit.backgroundTintList = ColorStateList.valueOf(primaryAccent)
        btnLaunchCockpit.setTextColor(if (theme == AppTheme.AMOLED_BLACK) Color.WHITE else Color.BLACK)

        cockpitProgressIndicator.setIndicatorColor(primaryAccent)
        cockpitProgressIndicator.trackColor = stroke

        // Pedallar (Tema Uyumu)
        val brakeBg = when (theme) {
            AppTheme.AMOLED_BLACK -> Color.parseColor("#200808")
            AppTheme.CRIMSON_CARBON -> Color.parseColor("#3B0D14")
            else -> Color.parseColor("#2A1015")
        }
        val throttleBg = when (theme) {
            AppTheme.AMOLED_BLACK -> Color.parseColor("#081C10")
            AppTheme.CRIMSON_CARBON -> Color.parseColor("#262208")
            else -> Color.parseColor("#0C2517")
        }
        btnCockpitBrake.background = createRoundedBox(brakeBg, Color.parseColor("#FF1744"), 24f)
        btnCockpitThrottle.background = createRoundedBox(throttleBg, Color.parseColor(if (theme == AppTheme.CRIMSON_CARBON) "#FFD700" else "#00E676"), 24f)

        // Direksiyon Görselini Yeniden Çiz
        ivCockpitWheel.setImageBitmap(generateSteeringWheelBitmap(theme))

        prefs.edit().putString("app_theme", theme.name).apply()
    }

    private fun createRoundedBox(bgColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(bgColor)
            setStroke((1.5f * density).toInt(), strokeColor)
        }
    }

    private fun generateSteeringWheelBitmap(theme: AppTheme = currentTheme): Bitmap {
        val size = 260
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.wheelRim)
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        val center = size / 2f
        val radius = center - 18f

        // Dış Direksiyon Çemberi
        canvas.drawCircle(center, center, radius, rimPaint)

        // Göbek
        val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.wheelHub)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 30f, hubPaint)
        val hubStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.wheelRim)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(center, center, 30f, hubStroke)

        // Kollar
        val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.wheelRim)
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawLine(center - radius + 10f, center, center - 30f, center, spokePaint)
        canvas.drawLine(center + 30f, center, center + radius - 10f, center, spokePaint)
        canvas.drawLine(center, center + 30f, center, center + radius - 10f, spokePaint)

        // Üst Merkez İşaretçisi
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.wheelMarker)
            style = Paint.Style.FILL
        }
        canvas.drawRect(center - 8f, center - radius - 10f, center + 8f, center - radius + 10f, markerPaint)

        return bitmap
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // ==========================================
        // TEMA SEÇİMİ DİNLENİCİSİ (3 FARKLI TEMA)
        // ==========================================
        groupTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnThemeOriginal -> applyTheme(AppTheme.ORIGINAL)
                    R.id.btnThemeAmoled -> applyTheme(AppTheme.AMOLED_BLACK)
                    R.id.btnThemeCrimson -> applyTheme(AppTheme.CRIMSON_CARBON)
                }
                vibrate(20)
            }
        }

        // ==========================================
        // EKRAN GEÇİŞLERİ VE YEREL MOD (KARTON DİREKSİYON)
        // ==========================================
        btnMenuLocalMode.setOnClickListener {
            toggleLocalMode()
        }

        btnStartLocalMode.setOnClickListener {
            toggleLocalMode()
        }

        btnGrantOverlayPerm.setOnClickListener {
            requestOverlayPermission()
        }

        btnOpenAccessibilitySettings.setOnClickListener {
            openAccessibilitySettings()
        }

        btnLaunchCockpit.setOnClickListener {
            viewMainMenu.visibility = View.GONE
            viewCockpit.visibility = View.VISIBLE
            updateCockpitLayoutForActiveButtons()
            vibrate(30)
        }

        btnBackToMenu.setOnClickListener {
            viewCockpit.visibility = View.GONE
            viewMainMenu.visibility = View.VISIBLE
            vibrate(20)
        }

        // ==========================================
        // BLUETOOTH CİHAZ LİSTESİ VE TARAMA
        // ==========================================
        deviceAdapter = DeviceAdapter { selectedDevice ->
            val devName = try {
                selectedDevice.name ?: selectedDevice.address
            } catch (_: SecurityException) {
                selectedDevice.address
            }
            Toast.makeText(this, "Bağlanılıyor: $devName...", Toast.LENGTH_SHORT).show()
            bluetoothController.connect(selectedDevice)
        }

        rvMenuDevices.layoutManager = LinearLayoutManager(this)
        rvMenuDevices.adapter = deviceAdapter

        btnMenuScan.setOnClickListener {
            deviceAdapter.clear()
            deviceAdapter.setDevices(bluetoothController.getBondedDevices())
            bluetoothController.startDiscovery()
        }

        // ==========================================
        // JİROSKOP EKSEN SEÇİMİ (X, Y, Z)
        // ==========================================
        groupAxis.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnAxisX -> gyroEngine.activeAxis = GyroMathEngine.Axis.X
                    R.id.btnAxisY -> gyroEngine.activeAxis = GyroMathEngine.Axis.Y
                    R.id.btnAxisZ -> gyroEngine.activeAxis = GyroMathEngine.Axis.Z
                }
                updateAxisUI()
                prefs.edit().putInt("selected_axis", gyroEngine.activeAxis.ordinal).apply()
                vibrate(15)
            }
        }

        switchInvertAxis.setOnCheckedChangeListener { _, isChecked ->
            gyroEngine.isInverted = isChecked
            prefs.edit().putBoolean("invert_axis", isChecked).apply()
            vibrate(15)
        }

        btnMenuCalibrate.setOnClickListener {
            gyroEngine.calibrateCenter()
            vibrate(40)
            Toast.makeText(this, "Merkez Sıfırlandı (0.0°)", Toast.LENGTH_SHORT).show()
        }

        btnCockpitCalibrate.setOnClickListener {
            gyroEngine.calibrateCenter()
            vibrate(40)
            Toast.makeText(this, "Merkez Sıfırlandı (0.0°)", Toast.LENGTH_SHORT).show()
        }

        // Kilit Açısı Slider (180, 540, 900, 1080)
        sliderMenuLock.addOnChangeListener { _, value, _ ->
            val lockAngle = when (value.toInt()) {
                0 -> 180.0f
                1 -> 540.0f
                2 -> 900.0f
                else -> 1080.0f
            }
            gyroEngine.setLockAngleDegree(lockAngle)
            val half = (lockAngle / 2.0f).toInt()
            tvMenuLockLabel.text = "Toplam Direksiyon Kilit Açısı: ${lockAngle.toInt()}° (±${half}°)"
            tvCockpitAxisBadge.text = "Eksen: ${gyroEngine.activeAxis.name} | ${lockAngle.toInt()}°"
            prefs.edit().putFloat("lock_angle", lockAngle).apply()
            vibrate(15)
        }

        // ==========================================
        // HAZIR PROFİLLER & TUŞ ÖZELLEŞTİRME
        // ==========================================
        groupProfiles.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnProfileRacing -> applyProfile("RACING")
                    R.id.btnProfileSim -> applyProfile("SIM_PRO")
                    R.id.btnProfileArcade -> applyProfile("ARCADE")
                    R.id.btnProfileCustom -> applyProfile("CUSTOM")
                }
                vibrate(20)
            }
        }

        setupButtonChipsListener()

        // ==========================================
        // TİTREŞİM VE GERİ BİLDİRİM
        // ==========================================
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            isVibrationEnabled = isChecked
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply()
            if (isChecked) vibrate(40)
        }

        switchLockVibration.setOnCheckedChangeListener { _, isChecked ->
            isLockVibrationEnabled = isChecked
            prefs.edit().putBoolean("lock_vibration_enabled", isChecked).apply()
        }

        switchPedalVibration.setOnCheckedChangeListener { _, isChecked ->
            isPedalVibrationEnabled = isChecked
            prefs.edit().putBoolean("pedal_vibration_enabled", isChecked).apply()
        }

        // ==========================================
        // KOKPİT PEDALLARI VE KULAKÇIK VİTESLER
        // ==========================================
        btnCockpitBrake.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setButtonState(BluetoothController.BTN_B, true)
                    btnCockpitBrake.background = createRoundedBox(Color.parseColor("#D50000"), Color.WHITE, 24f)
                    if (isPedalVibrationEnabled) vibrate(25)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setButtonState(BluetoothController.BTN_B, false)
                    btnCockpitBrake.background = createRoundedBox(Color.parseColor("#2A1015"), Color.parseColor("#FF1744"), 24f)
                    true
                }
                else -> false
            }
        }

        btnCockpitThrottle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setButtonState(BluetoothController.BTN_A, true)
                    btnCockpitThrottle.background = createRoundedBox(Color.parseColor("#00C853"), Color.WHITE, 24f)
                    if (isPedalVibrationEnabled) vibrate(25)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setButtonState(BluetoothController.BTN_A, false)
                    btnCockpitThrottle.background = createRoundedBox(Color.parseColor("#0C2517"), Color.parseColor("#00E676"), 24f)
                    true
                }
                else -> false
            }
        }

        // Kulakçık Vitesler (Paddle Shifters)
        btnPaddleShiftDown.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setButtonState(BluetoothController.BTN_LB, true)
                    if (isPedalVibrationEnabled) vibrate(35)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setButtonState(BluetoothController.BTN_LB, false)
                    true
                }
                else -> false
            }
        }

        btnPaddleShiftUp.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setButtonState(BluetoothController.BTN_RB, true)
                    if (isPedalVibrationEnabled) vibrate(35)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setButtonState(BluetoothController.BTN_RB, false)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewCockpit.visibility == View.VISIBLE) {
                    viewCockpit.visibility = View.GONE
                    viewMainMenu.visibility = View.VISIBLE
                } else {
                    finish()
                }
            }
        })
    }

    private fun loadSavedSettings() {
        val savedThemeName = prefs.getString("app_theme", AppTheme.ORIGINAL.name) ?: AppTheme.ORIGINAL.name
        val savedTheme = try {
            AppTheme.valueOf(savedThemeName)
        } catch (_: Exception) {
            AppTheme.ORIGINAL
        }
        when (savedTheme) {
            AppTheme.ORIGINAL -> groupTheme.check(R.id.btnThemeOriginal)
            AppTheme.AMOLED_BLACK -> groupTheme.check(R.id.btnThemeAmoled)
            AppTheme.CRIMSON_CARBON -> groupTheme.check(R.id.btnThemeCrimson)
        }
        applyTheme(savedTheme)

        val axisOrdinal = prefs.getInt("selected_axis", 2)
        gyroEngine.activeAxis = GyroMathEngine.Axis.values().getOrElse(axisOrdinal) { GyroMathEngine.Axis.Z }
        when (gyroEngine.activeAxis) {
            GyroMathEngine.Axis.X -> groupAxis.check(R.id.btnAxisX)
            GyroMathEngine.Axis.Y -> groupAxis.check(R.id.btnAxisY)
            GyroMathEngine.Axis.Z -> groupAxis.check(R.id.btnAxisZ)
        }

        val invert = prefs.getBoolean("invert_axis", false)
        gyroEngine.isInverted = invert
        switchInvertAxis.isChecked = invert

        val lock = prefs.getFloat("lock_angle", 900.0f)
        gyroEngine.setLockAngleDegree(lock)
        when (lock.toInt()) {
            180 -> sliderMenuLock.value = 0.0f
            540 -> sliderMenuLock.value = 1.0f
            900 -> sliderMenuLock.value = 2.0f
            else -> sliderMenuLock.value = 3.0f
        }
        val half = (lock / 2.0f).toInt()
        tvMenuLockLabel.text = "Toplam Direksiyon Kilit Açısı: ${lock.toInt()}° (±${half}°)"

        isVibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        switchVibration.isChecked = isVibrationEnabled

        isLockVibrationEnabled = prefs.getBoolean("lock_vibration_enabled", true)
        switchLockVibration.isChecked = isLockVibrationEnabled

        isPedalVibrationEnabled = prefs.getBoolean("pedal_vibration_enabled", true)
        switchPedalVibration.isChecked = isPedalVibrationEnabled

        val savedProfile = prefs.getString("active_profile", "RACING") ?: "RACING"
        when (savedProfile) {
            "RACING" -> groupProfiles.check(R.id.btnProfileRacing)
            "SIM_PRO" -> groupProfiles.check(R.id.btnProfileSim)
            "ARCADE" -> groupProfiles.check(R.id.btnProfileArcade)
            else -> groupProfiles.check(R.id.btnProfileCustom)
        }
        applyProfile(savedProfile)
        updateAxisUI()
    }

    private fun updateAxisUI() {
        btnAxisX.setTextColor(if (gyroEngine.activeAxis == GyroMathEngine.Axis.X) Color.parseColor("#00E5FF") else Color.parseColor("#F8FAFC"))
        btnAxisY.setTextColor(if (gyroEngine.activeAxis == GyroMathEngine.Axis.Y) Color.parseColor("#00E5FF") else Color.parseColor("#F8FAFC"))
        btnAxisZ.setTextColor(if (gyroEngine.activeAxis == GyroMathEngine.Axis.Z) Color.parseColor("#00E5FF") else Color.parseColor("#F8FAFC"))
        tvCockpitAxisBadge.text = "Eksen: ${gyroEngine.activeAxis.name} | ${gyroEngine.lockAngle.toInt()}°"
    }

    private fun applyProfile(profileId: String) {
        activeProfileId = profileId
        prefs.edit().putString("active_profile", profileId).apply()
        val profile = profiles.firstOrNull { it.id == profileId } ?: profiles[0]

        tvProfileDescription.text = profile.description

        if (profileId != "CUSTOM") {
            activeButtonsBitmask = profile.buttonMask
            updateChipsFromBitmask(profile.buttonMask)
        } else {
            val savedCustom = prefs.getInt("custom_buttons_mask", profiles[0].buttonMask)
            activeButtonsBitmask = savedCustom
            updateChipsFromBitmask(savedCustom)
        }
        updateCockpitLayoutForActiveButtons()
    }

    private fun updateChipsFromBitmask(mask: Int) {
        findViewById<Chip>(R.id.chipBtnA).isChecked = (mask and BluetoothController.BTN_A) != 0
        findViewById<Chip>(R.id.chipBtnB).isChecked = (mask and BluetoothController.BTN_B) != 0
        findViewById<Chip>(R.id.chipBtnX).isChecked = (mask and BluetoothController.BTN_X) != 0
        findViewById<Chip>(R.id.chipBtnY).isChecked = (mask and BluetoothController.BTN_Y) != 0
        findViewById<Chip>(R.id.chipBtnLB).isChecked = (mask and BluetoothController.BTN_LB) != 0
        findViewById<Chip>(R.id.chipBtnRB).isChecked = (mask and BluetoothController.BTN_RB) != 0
        findViewById<Chip>(R.id.chipBtnLT).isChecked = (mask and BluetoothController.BTN_LT) != 0
        findViewById<Chip>(R.id.chipBtnRT).isChecked = (mask and BluetoothController.BTN_RT) != 0
        findViewById<Chip>(R.id.chipBtnStart).isChecked = (mask and BluetoothController.BTN_START) != 0
        findViewById<Chip>(R.id.chipBtnSelect).isChecked = (mask and BluetoothController.BTN_SELECT) != 0
        findViewById<Chip>(R.id.chipBtnDpadUp).isChecked = (mask and BluetoothController.BTN_DPAD_UP) != 0
        findViewById<Chip>(R.id.chipBtnDpadDown).isChecked = (mask and BluetoothController.BTN_DPAD_DOWN) != 0
        findViewById<Chip>(R.id.chipBtnDpadLeft).isChecked = (mask and BluetoothController.BTN_DPAD_LEFT) != 0
        findViewById<Chip>(R.id.chipBtnDpadRight).isChecked = (mask and BluetoothController.BTN_DPAD_RIGHT) != 0
    }

    private fun setupButtonChipsListener() {
        val chipListener = { _: View, _: Boolean ->
            var newMask = 0
            if (findViewById<Chip>(R.id.chipBtnA).isChecked) newMask = newMask or BluetoothController.BTN_A
            if (findViewById<Chip>(R.id.chipBtnB).isChecked) newMask = newMask or BluetoothController.BTN_B
            if (findViewById<Chip>(R.id.chipBtnX).isChecked) newMask = newMask or BluetoothController.BTN_X
            if (findViewById<Chip>(R.id.chipBtnY).isChecked) newMask = newMask or BluetoothController.BTN_Y
            if (findViewById<Chip>(R.id.chipBtnLB).isChecked) newMask = newMask or BluetoothController.BTN_LB
            if (findViewById<Chip>(R.id.chipBtnRB).isChecked) newMask = newMask or BluetoothController.BTN_RB
            if (findViewById<Chip>(R.id.chipBtnLT).isChecked) newMask = newMask or BluetoothController.BTN_LT
            if (findViewById<Chip>(R.id.chipBtnRT).isChecked) newMask = newMask or BluetoothController.BTN_RT
            if (findViewById<Chip>(R.id.chipBtnStart).isChecked) newMask = newMask or BluetoothController.BTN_START
            if (findViewById<Chip>(R.id.chipBtnSelect).isChecked) newMask = newMask or BluetoothController.BTN_SELECT
            if (findViewById<Chip>(R.id.chipBtnDpadUp).isChecked) newMask = newMask or BluetoothController.BTN_DPAD_UP
            if (findViewById<Chip>(R.id.chipBtnDpadDown).isChecked) newMask = newMask or BluetoothController.BTN_DPAD_DOWN
            if (findViewById<Chip>(R.id.chipBtnDpadLeft).isChecked) newMask = newMask or BluetoothController.BTN_DPAD_LEFT
            if (findViewById<Chip>(R.id.chipBtnDpadRight).isChecked) newMask = newMask or BluetoothController.BTN_DPAD_RIGHT

            activeButtonsBitmask = newMask
            prefs.edit().putInt("custom_buttons_mask", newMask).apply()

            if (activeProfileId != "CUSTOM") {
                groupProfiles.check(R.id.btnProfileCustom)
                activeProfileId = "CUSTOM"
                tvProfileDescription.text = "Özel Profil: İstediğiniz tuşları özelleştiriyorsunuz."
            }
            updateCockpitLayoutForActiveButtons()
        }

        findViewById<Chip>(R.id.chipBtnA).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnB).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnX).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnY).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnLB).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnRB).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnLT).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnRT).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnStart).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnSelect).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnDpadUp).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnDpadDown).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnDpadLeft).setOnCheckedChangeListener(chipListener)
        findViewById<Chip>(R.id.chipBtnDpadRight).setOnCheckedChangeListener(chipListener)
    }

    /**
     * Aktif seçili olan tuşları Kokpit arayüzüne dinamik butonlar olarak ekler.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun updateCockpitLayoutForActiveButtons() {
        val density = resources.displayMetrics.density

        // Kulakçık Vitesler Görünürlüğü (LB / RB)
        val hasLB = (activeButtonsBitmask and BluetoothController.BTN_LB) != 0
        val hasRB = (activeButtonsBitmask and BluetoothController.BTN_RB) != 0
        btnPaddleShiftDown.visibility = if (hasLB) View.VISIBLE else View.GONE
        btnPaddleShiftUp.visibility = if (hasRB) View.VISIBLE else View.GONE

        // Hızlı Aksiyon Tuşları (X / Handbrake, Y / Nitro, LT / Clutch, RT)
        rowCockpitQuickActions.removeAllViews()

        val quickButtons = listOf(
            Triple(BluetoothController.BTN_X, "X (El Freni)", "#FF9100"),
            Triple(BluetoothController.BTN_Y, "Y (Nitro)", "#E040FB"),
            Triple(BluetoothController.BTN_LT, "LT (Debriyaj)", "#38BDF8"),
            Triple(BluetoothController.BTN_RT, "RT (Gaz 2)", "#4ADE80")
        )

        for ((flag, label, colorHex) in quickButtons) {
            if ((activeButtonsBitmask and flag) != 0) {
                val btn = MaterialButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        (38 * density).toInt()
                    ).apply {
                        marginStart = (6 * density).toInt()
                        marginEnd = (6 * density).toInt()
                    }
                    text = label
                    textSize = 11f
                    setTextColor(Color.parseColor(colorHex))
                    background = createRoundedBox(Color.parseColor("#1E293B"), Color.parseColor(colorHex), 10f)
                    setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)

                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                setButtonState(flag, true)
                                if (isPedalVibrationEnabled) vibrate(30)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                setButtonState(flag, false)
                                true
                            }
                            else -> false
                        }
                    }
                }
                rowCockpitQuickActions.addView(btn)
            }
        }

        // Üst Çubuk Ek Tuşları (Start, Select, D-Pad)
        containerCockpitExtraButtons.removeAllViews()

        val extraButtons = listOf(
            Pair(BluetoothController.BTN_START, "START ▶"),
            Pair(BluetoothController.BTN_SELECT, "SELECT ▤"),
            Pair(BluetoothController.BTN_DPAD_UP, "▲"),
            Pair(BluetoothController.BTN_DPAD_DOWN, "▼"),
            Pair(BluetoothController.BTN_DPAD_LEFT, "◀"),
            Pair(BluetoothController.BTN_DPAD_RIGHT, "▶")
        )

        for ((flag, label) in extraButtons) {
            if ((activeButtonsBitmask and flag) != 0) {
                val btn = MaterialButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        (34 * density).toInt()
                    ).apply {
                        marginStart = (4 * density).toInt()
                    }
                    text = label
                    textSize = 10f
                    setTextColor(Color.parseColor("#F8FAFC"))
                    background = createRoundedBox(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 8f)
                    setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)

                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                setButtonState(flag, true)
                                if (isPedalVibrationEnabled) vibrate(20)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                setButtonState(flag, false)
                                true
                            }
                            else -> false
                        }
                    }
                }
                containerCockpitExtraButtons.addView(btn)
            }
        }
    }

    private fun setButtonState(buttonFlag: Int, isPressed: Boolean) {
        currentActiveButtonsMask = if (isPressed) {
            currentActiveButtonsMask or buttonFlag
        } else {
            currentActiveButtonsMask and buttonFlag.inv()
        }
        transmitInputReport()
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroscopeSensor == null) {
            Toast.makeText(this, "Bu cihazda jiroskop sensörü bulunamadı!", Toast.LENGTH_LONG).show()
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

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            initBluetooth()
        }
    }

    private fun initBluetooth() {
        if (bluetoothController.bluetoothAdapter == null || !bluetoothController.bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Lütfen cihazınızın Bluetooth özelliğini açın.", Toast.LENGTH_SHORT).show()
        }
        bluetoothController.initialize()
        val bonded = bluetoothController.getBondedDevices()
        deviceAdapter.setDevices(bonded)
        tvMenuNoDevices.visibility = if (bonded.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        gyroscopeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        refreshLocalModeUI()
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Lütfen 'Diğer uygulamaların üzerinde görüntüleme' iznini açın.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Overlay izni zaten verilmiş.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Yüklü Servisler altından 'Gyro Wheel Direksiyon Servisi'ni etkinleştirin.", Toast.LENGTH_LONG).show()
    }

    private fun toggleLocalMode() {
        vibrate(30)
        if (LocalWheelOverlayService.isRunning) {
            val stopIntent = Intent(this, LocalWheelOverlayService::class.java).apply {
                action = LocalWheelOverlayService.ACTION_STOP
            }
            startService(stopIntent)
            Toast.makeText(this, "Yerel Mod durduruldu.", Toast.LENGTH_SHORT).show()
            window.decorView.postDelayed({ refreshLocalModeUI() }, 300)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ekran üzerinde şeffaf kontroller için Overlay izni gereklidir!", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        if (!LocalSteeringAccessibilityService.isRunning()) {
            Toast.makeText(this, "Oyunlara dokunmatik hareket enjekte etmek için Erişilebilirlik Servisini açmanız gerekir!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val serviceIntent = Intent(this, LocalWheelOverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Yerel Mod Başlatıldı! Oyununuzu açıp karton direksiyona takabilirsiniz.", Toast.LENGTH_LONG).show()

        window.decorView.postDelayed({
            refreshLocalModeUI()
            moveTaskToBack(true)
        }, 500)
    }

    private fun refreshLocalModeUI() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = LocalSteeringAccessibilityService.isRunning()
        val isServiceRunning = LocalWheelOverlayService.isRunning

        if (hasOverlay) {
            tvOverlayPermissionStatus.text = "• Ekran Üzerine Çizim (Overlay): Verildi ✓"
            tvOverlayPermissionStatus.setTextColor(Color.parseColor("#00E676"))
            btnGrantOverlayPerm.visibility = View.GONE
        } else {
            tvOverlayPermissionStatus.text = "• Ekran Üzerine Çizim (Overlay): Gerekli ⚠️"
            tvOverlayPermissionStatus.setTextColor(Color.parseColor("#F59E0B"))
            btnGrantOverlayPerm.visibility = View.VISIBLE
        }

        if (hasAccessibility) {
            tvAccessibilityStatus.text = "• Erişilebilirlik (Input Injection): Aktif ✓"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#00E676"))
            btnOpenAccessibilitySettings.visibility = View.GONE
        } else {
            tvAccessibilityStatus.text = "• Erişilebilirlik (Input Injection): Ayarlardan Açın ⚠️"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#00E5FF"))
            btnOpenAccessibilitySettings.visibility = View.VISIBLE
        }

        if (isServiceRunning) {
            tvLocalModeStatusBadge.text = "ÇALIŞIYOR"
            tvLocalModeStatusBadge.setTextColor(Color.parseColor("#00E676"))
            btnStartLocalMode.text = "🛑 YEREL MODU DURDUR"
            btnStartLocalMode.setBackgroundColor(Color.parseColor("#EF4444"))
            btnMenuLocalMode.text = "🛑 YEREL MOD ÇALIŞIYOR"
        } else {
            tvLocalModeStatusBadge.text = "DURDURULDU"
            tvLocalModeStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
            btnStartLocalMode.text = "📦 YEREL MODU BAŞLAT (ŞEFFAF KATMAN & JİROSKOP)"
            btnStartLocalMode.setBackgroundColor(Color.parseColor("#F59E0B"))
            btnMenuLocalMode.text = "📦 YEREL MOD (KARTON)"
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothController.destroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // 3 Eksen (X, Y, Z) verisini GyroMathEngine'e aktar
        val result = gyroEngine.process(
            gyroX = event.values[0],
            gyroY = event.values[1],
            gyroZ = event.values[2],
            timestampNs = event.timestamp
        )

        latestStickX = result.analogStickX

        // Kilit Sınırına Vurma Titreşimi (Force Feedback Simülasyonu)
        val halfLock = result.lockAngle / 2.0f
        val isAtLockLimit = kotlin.math.abs(result.effectiveAngle) >= (halfLock - 0.5f)
        if (isAtLockLimit && !lastHitLockLimit) {
            if (isLockVibrationEnabled) vibrate(50)
        }
        lastHitLockLimit = isAtLockLimit

        updateSteeringTelemetryUI(result)
        transmitInputReport()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateSteeringTelemetryUI(output: GyroMathEngine.Output) {
        val angleStr = String.format(Locale.US, "%+.1f°", output.effectiveAngle)

        // Ana Menü Test Göstergesi
        tvMenuLiveAngle.text = "Anlık Açı (${output.activeAxis.name}): $angleStr"

        // Kokpit Göstergesi
        tvCockpitAngle.text = angleStr
        ivCockpitWheel.rotation = -output.effectiveAngle

        val halfLock = output.lockAngle / 2.0f
        val ratio = (output.effectiveAngle / halfLock).coerceIn(-1.0f, 1.0f)
        val progressVal = ((ratio + 1.0f) * 500.0f).toInt().coerceIn(0, 1000)
        cockpitProgressIndicator.progress = progressVal

        tvCockpitTelemetry.text = String.format(
            Locale.US,
            "Stick X: %-6d | %s %s | Lock: ±%.0f°",
            output.analogStickX,
            output.activeAxis.label,
            if (output.isInverted) "(-)" else "(+)",
            halfLock
        )
    }

    private fun transmitInputReport() {
        bluetoothController.sendInputReport(
            stickX = latestStickX,
            buttonsBitmask = currentActiveButtonsMask
        )
    }

    // ==========================================
    // BLUETOOTH DİNLENİCİLERİ
    // ==========================================
    override fun onAppStatusChanged(registered: Boolean) {
        runOnUiThread {
            val statusText = if (registered) "HID Hazır" else "Bağlantı Yok"
            val color = Color.parseColor(if (registered) "#FFB300" else "#EF4444")
            tvMenuConnectionBadge.text = statusText
            tvMenuConnectionBadge.setTextColor(color)
            tvCockpitConnectionStatus.text = statusText
            tvCockpitConnectionStatus.setTextColor(color)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        runOnUiThread {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val devName = try {
                        device?.name ?: device?.address ?: "Android TV"
                    } catch (_: SecurityException) {
                        "Android TV"
                    }
                    val text = "Bağlı: $devName"
                    val green = Color.parseColor("#00E676")
                    tvMenuConnectionBadge.text = text
                    tvMenuConnectionBadge.setTextColor(green)
                    tvCockpitConnectionStatus.text = text
                    tvCockpitConnectionStatus.setTextColor(green)
                    Toast.makeText(this, "Bağlantı Kuruldu: $devName", Toast.LENGTH_SHORT).show()
                    vibrate(100)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val text = "Bağlantı Yok"
                    val red = Color.parseColor("#EF4444")
                    tvMenuConnectionBadge.text = text
                    tvMenuConnectionBadge.setTextColor(red)
                    tvCockpitConnectionStatus.text = text
                    tvCockpitConnectionStatus.setTextColor(red)
                    Toast.makeText(this, "Bağlantı kesildi.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDeviceDiscovered(device: BluetoothDevice) {
        runOnUiThread {
            deviceAdapter.addDevice(device)
            tvMenuNoDevices.visibility = View.GONE
            tvMenuScanStatus.text = "Cihazlar Bulunuyor..."
        }
    }

    override fun onDiscoveryStateChanged(isScanning: Boolean) {
        runOnUiThread {
            pbMenuScanning.visibility = if (isScanning) View.VISIBLE else View.GONE
            btnMenuScan.isEnabled = !isScanning
            tvMenuScanStatus.text = if (isScanning) "Android TV ve Bluetooth alıcıları aranıyor..." else "Eşleşmiş ve taranan cihazlar listesi:"
        }
    }

    // ==========================================
    // İÇ ADAPTÖR SINIFI (GÖMÜLÜ)
    // ==========================================
    private class DeviceAdapter(
        private val onDeviceSelected: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        private val devices = mutableListOf<BluetoothDevice>()

        class ViewHolder(
            val container: LinearLayout,
            val nameText: TextView,
            val addressText: TextView,
            val bondText: TextView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val density = context.resources.displayMetrics.density

            val container = LinearLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            val icon = TextView(context).apply {
                text = "📺"
                textSize = 20f
            }
            container.addView(icon)

            val textCol = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }

            val name = TextView(context).apply {
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            textCol.addView(name)

            val address = TextView(context).apply {
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
            }
            textCol.addView(address)
            container.addView(textCol)

            val bond = TextView(context).apply {
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            container.addView(bond)

            return ViewHolder(container, name, address, bond)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            val devName = try {
                device.name ?: "Bilinmeyen Cihaz"
            } catch (_: SecurityException) {
                "Bilinmeyen Cihaz"
            }
            val devAddress = device.address ?: "00:00:00:00:00:00"

            holder.nameText.text = devName
            holder.addressText.text = devAddress

            val isBonded = try {
                device.bondState == BluetoothDevice.BOND_BONDED
            } catch (_: SecurityException) {
                false
            }

            holder.bondText.text = if (isBonded) "Eşleşti ✓" else "Bağlan"
            holder.bondText.setTextColor(Color.parseColor(if (isBonded) "#00E676" else "#00E5FF"))

            holder.container.setOnClickListener {
                onDeviceSelected(device)
            }
        }

        override fun getItemCount(): Int = devices.size

        @SuppressLint("NotifyDataSetChanged")
        fun addDevice(device: BluetoothDevice) {
            if (devices.none { it.address == device.address }) {
                devices.add(device)
                notifyDataSetChanged()
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setDevices(newDevices: List<BluetoothDevice>) {
            devices.clear()
            devices.addAll(newDevices)
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun clear() {
            devices.clear()
            notifyDataSetChanged()
        }
    }
}
