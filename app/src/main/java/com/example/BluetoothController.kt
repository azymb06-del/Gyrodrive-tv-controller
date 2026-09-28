package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bluetooth HID (Human Interface Device) emülasyonu ve cihaz keşif yöneticisi.
 *
 * Standart 16 butonlu USB/Bluetooth Gamepad rapor protokolünü yönetir.
 */
class BluetoothController(private val context: Context) {

    interface Listener {
        fun onAppStatusChanged(registered: Boolean)
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onDeviceDiscovered(device: BluetoothDevice)
        fun onDiscoveryStateChanged(isScanning: Boolean)
    }

    var listener: Listener? = null

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    var hidDevice: BluetoothHidDevice? = null
        private set
    var connectedDevice: BluetoothDevice? = null
        private set
    var isAppRegistered: Boolean = false
        private set

    // Anlık Giriş Durumları
    private var currentStickX: Short = 0
    private var currentButtonsMask: Int = 0

    private var isReceiverRegistered: Boolean = false

    companion object {
        // Standart Gamepad Buton Maskeleri (16 Tuş)
        const val BTN_A = 1 shl 0          // Buton 1 / A (Gaz)
        const val BTN_B = 1 shl 1          // Buton 2 / B (Fren)
        const val BTN_X = 1 shl 2          // Buton 3 / X (El Freni)
        const val BTN_Y = 1 shl 3          // Buton 4 / Y (Nitro/Boost)
        const val BTN_LB = 1 shl 4         // Buton 5 / Sol Kulakçık (Vites Düşür)
        const val BTN_RB = 1 shl 5         // Buton 6 / Sağ Kulakçık (Vites Yükselt)
        const val BTN_LT = 1 shl 6         // Buton 7 / Sol Tetik (Debriyaj)
        const val BTN_RT = 1 shl 7         // Buton 8 / Sağ Tetik
        const val BTN_SELECT = 1 shl 8     // Buton 9 / Select (Kamera/Harita)
        const val BTN_START = 1 shl 9      // Buton 10 / Start (Menü/Duraklat)
        const val BTN_DPAD_UP = 1 shl 10   // Buton 11 / D-Pad Yukarı
        const val BTN_DPAD_DOWN = 1 shl 11 // Buton 12 / D-Pad Aşağı
        const val BTN_DPAD_LEFT = 1 shl 12 // Buton 13 / D-Pad Sol
        const val BTN_DPAD_RIGHT = 1 shl 13// Buton 14 / D-Pad Sağ
        const val BTN_L3 = 1 shl 14        // Buton 15 / Sol Çubuk Tık
        const val BTN_R3 = 1 shl 15        // Buton 16 / Sağ Çubuk Tık

        /**
         * Standart USB/Bluetooth HID Gamepad Rapor Tanımlayıcısı (16-bit X-Axis + 16 Buton).
         */
        val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),        // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x05.toByte(),        // USAGE (Game Pad)
            0xA1.toByte(), 0x01.toByte(),        // COLLECTION (Application)
            0x09.toByte(), 0x01.toByte(),        //   USAGE (Pointer)
            0xA1.toByte(), 0x00.toByte(),        //   COLLECTION (Physical)

            // 16-bit X-Ekseni (-32768 ile +32767)
            0x09.toByte(), 0x30.toByte(),        //     USAGE (X)
            0x16.toByte(), 0x00.toByte(), 0x80.toByte(), // LOGICAL_MINIMUM (-32768)
            0x26.toByte(), 0xFF.toByte(), 0x7F.toByte(), // LOGICAL_MAXIMUM (32767)
            0x75.toByte(), 0x10.toByte(),        //     REPORT_SIZE (16)
            0x95.toByte(), 0x01.toByte(),        //     REPORT_COUNT (1)
            0x81.toByte(), 0x02.toByte(),        //     INPUT (Data,Var,Abs)
            0xC0.toByte(),                       //   END_COLLECTION

            // 16 Adet Buton (1 bit her biri = 2 Bayt)
            0x05.toByte(), 0x09.toByte(),        //   USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(),        //   USAGE_MINIMUM (Button 1)
            0x29.toByte(), 0x10.toByte(),        //   USAGE_MAXIMUM (Button 16)
            0x15.toByte(), 0x00.toByte(),        //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(),        //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(),        //   REPORT_SIZE (1)
            0x95.toByte(), 0x10.toByte(),        //   REPORT_COUNT (16)
            0x81.toByte(), 0x02.toByte(),        //   INPUT (Data,Var,Abs)

            0xC0.toByte()                        // END_COLLECTION
        )
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                isAppRegistered = false
                connectedDevice = null
                listener?.onConnectionStateChanged(null, BluetoothProfile.STATE_DISCONNECTED)
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            listener?.onAppStatusChanged(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice == device) {
                        connectedDevice = null
                    }
                }
            }
            listener?.onConnectionStateChanged(device, state)
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                val report = buildReportPayload()
                hidDevice?.replyReport(device, type, id, report)
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        listener?.onDeviceDiscovered(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    listener?.onDiscoveryStateChanged(true)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    listener?.onDiscoveryStateChanged(false)
                }
            }
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun initialize() {
        registerDiscoveryReceiver()
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerDiscoveryReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)
            isReceiverRegistered = true
        }
    }

    @SuppressLint("MissingPermission")
    fun registerHidApp() {
        if (!hasRequiredPermissions()) return
        val currentHid = hidDevice ?: return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Gyro Steering Wheel Pro",
            "900-Degree Customizable Gyro Gamepad for Android TV",
            "AIStudio",
            0x08.toByte(), // Subclass: Gamepad
            HID_REPORT_DESCRIPTOR
        )

        currentHid.registerApp(
            sdpSettings,
            null,
            null,
            ContextCompat.getMainExecutor(context),
            hidCallback
        )
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (!hasRequiredPermissions()) return false
        val adapter = bluetoothAdapter ?: return false
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        return adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!hasRequiredPermissions()) return
        bluetoothAdapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        if (!hasRequiredPermissions()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        if (!hasRequiredPermissions()) return false
        stopDiscovery()
        return hidDevice?.connect(device) ?: false
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!hasRequiredPermissions()) return
        val target = connectedDevice ?: return
        hidDevice?.disconnect(target)
    }

    /**
     * 4 Baytlık HID Rapor Paketini Oluşturur:
     * - Bayt 0: Direksiyon X-Ekseni Düşük Bayt
     * - Bayt 1: Direksiyon X-Ekseni Yüksek Bayt
     * - Bayt 2: Butonlar 1..8 (A, B, X, Y, LB, RB, LT, RT)
     * - Bayt 3: Butonlar 9..16 (Select, Start, Dpad, L3, R3)
     */
    fun buildReportPayload(): ByteArray {
        val xLow = (currentStickX.toInt() and 0xFF).toByte()
        val xHigh = ((currentStickX.toInt() shr 8) and 0xFF).toByte()
        val btnLow = (currentButtonsMask and 0xFF).toByte()
        val btnHigh = ((currentButtonsMask shr 8) and 0xFF).toByte()

        return byteArrayOf(xLow, xHigh, btnLow, btnHigh)
    }

    /**
     * HID giriş raporunu bağlı cihaza düşük gecikmeyle iletir.
     */
    @SuppressLint("MissingPermission")
    fun sendInputReport(stickX: Short, buttonsBitmask: Int): Boolean {
        currentStickX = stickX
        currentButtonsMask = buttonsBitmask

        val dev = connectedDevice ?: return false
        val hid = hidDevice ?: return false
        if (!hasRequiredPermissions()) return false

        val payload = buildReportPayload()
        return hid.sendReport(dev, 0, payload)
    }

    @SuppressLint("MissingPermission")
    fun destroy() {
        stopDiscovery()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        if (hasRequiredPermissions() && isAppRegistered) {
            hidDevice?.unregisterApp()
        }
        if (hidDevice != null) {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            hidDevice = null
        }
    }
}
