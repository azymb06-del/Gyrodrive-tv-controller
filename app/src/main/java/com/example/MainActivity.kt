package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener, BluetoothController.Listener {

    private lateinit var gyroEngine: GyroMathEngine
    private lateinit var bluetoothController: BluetoothController

    private var sensorManager: SensorManager? = null
    private var gyroscopeSensor: Sensor? = null
    private var vibrator: Vibrator? = null

    // UI Elemanları
    private lateinit var topBar: View
    private lateinit var btnScanConnect: MaterialButton
    private lateinit var btnCalibrateCenter: MaterialButton
    private lateinit var btnHandbrake: MaterialButton
    private lateinit var sliderLockAngle: Slider
    private lateinit var tvLockLabel: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var centerCockpit: View
    private lateinit var ivSteeringWheel: ImageView
    private lateinit var tvAngleReadout: TextView
    private lateinit var steeringProgressIndicator: LinearProgressIndicator
    private lateinit var tvTelemetryDetails: TextView
    private lateinit var btnBrakePedal: View
    private lateinit var btnThrottlePedal: View

    // Giriş Kontrol Durumları
    private var isThrottlePressed: Boolean = false
    private var isBrakePressed: Boolean = false
    private var isHandbrakeActive: Boolean = false
    private var latestStickX: Short = 0

    // TV Arama Diyaloğu Bileşenleri
    private var deviceDialog: BottomSheetDialog? = null
    private var deviceAdapter: DeviceAdapter? = null
    private var dialogProgressBar: ProgressBar? = null
    private var dialogStatusText: TextView? = null

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

        // Yatay modu zorunlu kıl ve ekranı açık tut
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        initEngines()
        bindViews()
        applyProgrammaticStyling()
        setupListeners()
        setupSensors()
        setupVibrator()
        checkAndRequestPermissions()
    }

    private fun initEngines() {
        gyroEngine = GyroMathEngine(lockAngle = 900.0f, alpha = 0.18f, deadzoneDegrees = 1.5f)
        bluetoothController = BluetoothController(this)
        bluetoothController.listener = this
    }

    private fun bindViews() {
        topBar = findViewById(R.id.topBar)
        btnScanConnect = findViewById(R.id.btnScanConnect)
        btnCalibrateCenter = findViewById(R.id.btnCalibrateCenter)
        btnHandbrake = findViewById(R.id.btnHandbrake)
        sliderLockAngle = findViewById(R.id.sliderLockAngle)
        tvLockLabel = findViewById(R.id.tvLockLabel)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        centerCockpit = findViewById(R.id.centerCockpit)
        ivSteeringWheel = findViewById(R.id.ivSteeringWheel)
        tvAngleReadout = findViewById(R.id.tvAngleReadout)
        steeringProgressIndicator = findViewById(R.id.steeringProgressIndicator)
        tvTelemetryDetails = findViewById(R.id.tvTelemetryDetails)
        btnBrakePedal = findViewById(R.id.btnBrakePedal)
        btnThrottlePedal = findViewById(R.id.btnThrottlePedal)
    }

    private fun applyProgrammaticStyling() {
        // Kart arka planlarını bağımsız GradientDrawable olarak ata
        topBar.background = createRoundedBox(Color.parseColor("#151D2C"), Color.parseColor("#334155"), 24f)
        centerCockpit.background = createRoundedBox(Color.parseColor("#151D2C"), Color.parseColor("#334155"), 28f)
        tvConnectionStatus.background = createRoundedBox(Color.parseColor("#101827"), Color.parseColor("#334155"), 16f)

        btnBrakePedal.background = createRoundedBox(Color.parseColor("#2A1015"), Color.parseColor("#FF1744"), 32f)
        btnThrottlePedal.background = createRoundedBox(Color.parseColor("#0C2517"), Color.parseColor("#00E676"), 32f)

        // Bellekte profesyonel yarış direksiyonu bitmap'i çiz
        ivSteeringWheel.setImageBitmap(generateSteeringWheelBitmap())
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

    private fun generateSteeringWheelBitmap(): Bitmap {
        val size = 280
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        val center = size / 2f
        val radius = center - 20f

        // Dış Direksiyon Çemberi
        canvas.drawCircle(center, center, radius, rimPaint)

        // Merkez Göbek
        val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 32f, hubPaint)
        val hubStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(center, center, 32f, hubStroke)

        // Direksiyon Kolları (Spokes)
        val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawLine(center - radius + 10f, center, center - 32f, center, spokePaint)
        canvas.drawLine(center + 32f, center, center + radius - 10f, center, spokePaint)
        canvas.drawLine(center, center + 32f, center, center + radius - 10f, spokePaint)

        // Üst Merkez Turuncu İşaretçisi (Top Dead Center Marker)
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF9100")
            style = Paint.Style.FILL
        }
        canvas.drawRect(center - 8f, center - radius - 10f, center + 8f, center - radius + 10f, markerPaint)

        return bitmap
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // TV Tara ve Bağlan Butonu
        btnScanConnect.setOnClickListener {
            if (bluetoothController.hasRequiredPermissions()) {
                showDeviceBottomSheet()
            } else {
                checkAndRequestPermissions()
            }
        }

        // Merkez Kalibrasyon Butonu
        btnCalibrateCenter.setOnClickListener {
            gyroEngine.calibrateCenter()
            vibrate(40)
            updateSteeringUI(0.0f, 0.0f, 0)
            Toast.makeText(this, "Direksiyon Açısı Sıfırlandı (0.0°)", Toast.LENGTH_SHORT).show()
            transmitInputReport()
        }

        // El Freni Butonu (Toggle)
        btnHandbrake.setOnClickListener {
            isHandbrakeActive = !isHandbrakeActive
            vibrate(if (isHandbrakeActive) 60 else 30)
            updateHandbrakeUI()
            transmitInputReport()
        }

        // Kilit Açısı Kaydırıcısı (180°, 540°, 900°)
        sliderLockAngle.addOnChangeListener { _, value, _ ->
            val lockAngle = when (value.toInt()) {
                0 -> 180.0f
                1 -> 540.0f
                else -> 900.0f
            }
            gyroEngine.setLockAngleDegree(lockAngle)
            val half = (lockAngle / 2.0f).toInt()
            tvLockLabel.text = "Lock: ${lockAngle.toInt()}° (±${half}°)"
            vibrate(15)
        }

        // Sol Dokunmatik Alan: Fren Pedalı
        btnBrakePedal.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBrakePressed = true
                    btnBrakePedal.background = createRoundedBox(Color.parseColor("#D50000"), Color.WHITE, 32f)
                    vibrate(25)
                    transmitInputReport()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isBrakePressed = false
                    btnBrakePedal.background = createRoundedBox(Color.parseColor("#2A1015"), Color.parseColor("#FF1744"), 32f)
                    transmitInputReport()
                    true
                }
                else -> false
            }
        }

        // Sağ Dokunmatik Alan: Gaz Pedalı
        btnThrottlePedal.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isThrottlePressed = true
                    btnThrottlePedal.background = createRoundedBox(Color.parseColor("#00C853"), Color.WHITE, 32f)
                    vibrate(25)
                    transmitInputReport()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isThrottlePressed = false
                    btnThrottlePedal.background = createRoundedBox(Color.parseColor("#0C2517"), Color.parseColor("#00E676"), 32f)
                    transmitInputReport()
                    true
                }
                else -> false
            }
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }

    private fun updateHandbrakeUI() {
        if (isHandbrakeActive) {
            btnHandbrake.setBackgroundColor(Color.parseColor("#FF9100"))
            btnHandbrake.setTextColor(Color.parseColor("#0B0F19"))
        } else {
            btnHandbrake.setBackgroundColor(Color.parseColor("#26FF9100"))
            btnHandbrake.setTextColor(Color.parseColor("#FF9100"))
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
    }

    override fun onResume() {
        super.onResume()
        gyroscopeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
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

        val gyroX = event.values[0]
        val result = gyroEngine.process(gyroX, event.timestamp)

        latestStickX = result.analogStickX

        updateSteeringUI(result.effectiveAngle, result.lockAngle, result.analogStickX)
        transmitInputReport()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateSteeringUI(angle: Float, lock: Float, stickX: Short) {
        tvAngleReadout.text = String.format(Locale.US, "%+.1f°", angle)
        ivSteeringWheel.rotation = -angle

        val halfLock = lock / 2.0f
        val ratio = (angle / halfLock).coerceIn(-1.0f, 1.0f)
        val progressVal = ((ratio + 1.0f) * 500.0f).toInt().coerceIn(0, 1000)
        steeringProgressIndicator.progress = progressVal

        tvTelemetryDetails.text = String.format(
            Locale.US,
            "Stick X: %-6d | Lock: ±%.0f° | HID: %s",
            stickX,
            halfLock,
            if (bluetoothController.connectedDevice != null) "STREAMING" else "IDLE"
        )
    }

    private fun transmitInputReport() {
        bluetoothController.sendInputReport(
            stickX = latestStickX,
            throttle = isThrottlePressed,
            brake = isBrakePressed,
            handbrake = isHandbrakeActive
        )
    }

    override fun onAppStatusChanged(registered: Boolean) {
        runOnUiThread {
            if (registered) {
                tvConnectionStatus.text = "HID Ready"
                tvConnectionStatus.setTextColor(Color.parseColor("#FFB300"))
            } else {
                tvConnectionStatus.text = "Status: Disconnected"
                tvConnectionStatus.setTextColor(Color.parseColor("#EF4444"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        runOnUiThread {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val deviceName = try {
                        device?.name ?: device?.address ?: "Android TV"
                    } catch (_: SecurityException) {
                        "Android TV"
                    }
                    tvConnectionStatus.text = "Connected: $deviceName"
                    tvConnectionStatus.setTextColor(Color.parseColor("#00E676"))
                    Toast.makeText(this, "Bağlandı: $deviceName", Toast.LENGTH_SHORT).show()
                    vibrate(100)
                    deviceDialog?.dismiss()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tvConnectionStatus.text = "Status: Disconnected"
                    tvConnectionStatus.setTextColor(Color.parseColor("#EF4444"))
                    Toast.makeText(this, "Bağlantı kesildi.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDeviceDiscovered(device: BluetoothDevice) {
        runOnUiThread {
            deviceAdapter?.addDevice(device)
            dialogStatusText?.text = "Bulunan Cihazlar:"
        }
    }

    override fun onDiscoveryStateChanged(isScanning: Boolean) {
        runOnUiThread {
            dialogProgressBar?.visibility = if (isScanning) View.VISIBLE else View.GONE
            dialogStatusText?.text = if (isScanning) "Android TV aranıyor..." else "Bulunan Cihazlar:"
        }
    }

    // ==========================================
    // PROGRAMMATIK VE BAĞIMSIZ BOTTOM SHEET
    // ==========================================
    @SuppressLint("MissingPermission")
    private fun showDeviceBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val density = resources.displayMetrics.density

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#151D2C"))
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (24 * density).toInt())
        }

        // Sürükleme Tutamacı
        val handle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (4 * density).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
            background = createRoundedBox(Color.parseColor("#334155"), Color.TRANSPARENT, 2f)
        }
        rootLayout.addView(handle)

        // Başlık Çubuğu
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = "Android TV / Alıcı Seçin"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        headerBar.addView(tvTitle)

        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                marginEnd = (12 * density).toInt()
            }
            indeterminateDrawable?.setTint(Color.parseColor("#00E5FF"))
            visibility = View.VISIBLE
        }
        headerBar.addView(pb)
        dialogProgressBar = pb

        val btnRescan = MaterialButton(this).apply {
            text = "Tara"
            setTextColor(Color.parseColor("#00E5FF"))
            setBackgroundColor(Color.TRANSPARENT)
        }
        headerBar.addView(btnRescan)
        rootLayout.addView(headerBar)

        val statusText = TextView(this).apply {
            text = "Android TV aranıyor..."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        rootLayout.addView(statusText)
        dialogStatusText = statusText

        val rvDevices = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (220 * density).toInt())
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        val adapter = DeviceAdapter { selectedDevice ->
            val devName = try {
                selectedDevice.name ?: selectedDevice.address
            } catch (_: SecurityException) {
                selectedDevice.address
            }
            Toast.makeText(this, "Bağlanılıyor: $devName...", Toast.LENGTH_SHORT).show()
            bluetoothController.connect(selectedDevice)
        }
        rvDevices.adapter = adapter
        deviceAdapter = adapter
        rootLayout.addView(rvDevices)

        // Eşleşmiş cihazları yükle
        val bonded = bluetoothController.getBondedDevices()
        adapter.setDevices(bonded)

        btnRescan.setOnClickListener {
            adapter.clear()
            adapter.setDevices(bluetoothController.getBondedDevices())
            bluetoothController.startDiscovery()
        }

        bluetoothController.startDiscovery()

        dialog.setOnDismissListener {
            bluetoothController.stopDiscovery()
            deviceDialog = null
        }

        dialog.setContentView(rootLayout)
        deviceDialog = dialog
        dialog.show()
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
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            val icon = TextView(context).apply {
                text = "📺"
                textSize = 22f
            }
            container.addView(icon)

            val textCol = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }

            val name = TextView(context).apply {
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            textCol.addView(name)

            val address = TextView(context).apply {
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }
            textCol.addView(address)
            container.addView(textCol)

            val bond = TextView(context).apply {
                textSize = 12f
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

            holder.bondText.text = if (isBonded) "Eşleşti" else "Bulundu"
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
