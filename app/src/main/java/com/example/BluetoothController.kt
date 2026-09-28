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
 * Controller managing Bluetooth HID (Human Interface Device) emulation and device discovery.
 *
 * Implements Android's BluetoothHidDevice profile to register a Gamepad HID profile,
 * establish connections with Android TV / Google TV / Set-Top Boxes,
 * and stream ultra-low-latency reports (Steering X-Axis, Throttle, Brake, Handbrake).
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

    // Current HID State
    private var currentStickX: Short = 0
    private var currentThrottle: Boolean = false
    private var currentBrake: Boolean = false
    private var currentHandbrake: Boolean = false

    private var isReceiverRegistered: Boolean = false

    companion object {
        /**
         * Standard USB/Bluetooth HID Gamepad Report Descriptor.
         *
         * Report structure:
         * - 2 Bytes: 16-bit signed X-Axis (Steering wheel position, -32768 to +32767)
         * - 1 Byte:  8 Buttons:
         *            Bit 0: Button 1 (Throttle / Button A)
         *            Bit 1: Button 2 (Brake / Button B)
         *            Bit 2: Button 3 (Handbrake / Button X)
         *            Bits 3-7: Reserved (0)
         */
        val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),        // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x05.toByte(),        // USAGE (Game Pad)
            0xA1.toByte(), 0x01.toByte(),        // COLLECTION (Application)
            0x09.toByte(), 0x01.toByte(),        //   USAGE (Pointer)
            0xA1.toByte(), 0x00.toByte(),        //   COLLECTION (Physical)

            // 16-bit X-Axis for Steering (-32768 to 32767)
            0x09.toByte(), 0x30.toByte(),        //     USAGE (X)
            0x16.toByte(), 0x00.toByte(), 0x80.toByte(), // LOGICAL_MINIMUM (-32768)
            0x26.toByte(), 0xFF.toByte(), 0x7F.toByte(), // LOGICAL_MAXIMUM (32767)
            0x75.toByte(), 0x10.toByte(),        //     REPORT_SIZE (16)
            0x95.toByte(), 0x01.toByte(),        //     REPORT_COUNT (1)
            0x81.toByte(), 0x02.toByte(),        //     INPUT (Data,Var,Abs)
            0xC0.toByte(),                       //   END_COLLECTION

            // Buttons 1..8 (1 bit each)
            0x05.toByte(), 0x09.toByte(),        //   USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(),        //   USAGE_MINIMUM (Button 1)
            0x29.toByte(), 0x08.toByte(),        //   USAGE_MAXIMUM (Button 8)
            0x15.toByte(), 0x00.toByte(),        //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(),        //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(),        //   REPORT_SIZE (1)
            0x95.toByte(), 0x08.toByte(),        //   REPORT_COUNT (8)
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

    /**
     * Checks if all required runtime Bluetooth permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Initializes Bluetooth profile proxy and broadcast receivers.
     */
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

    /**
     * Registers the application as a HID Gamepad device profile with the Android Bluetooth stack.
     */
    @SuppressLint("MissingPermission")
    fun registerHidApp() {
        if (!hasRequiredPermissions()) return
        val currentHid = hidDevice ?: return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Gyro Steering Wheel",
            "900-Degree Gyro Gamepad for Android TV",
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

    /**
     * Initiates real-time discovery of nearby Bluetooth devices.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (!hasRequiredPermissions()) return false
        val adapter = bluetoothAdapter ?: return false
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        return adapter.startDiscovery()
    }

    /**
     * Stops current discovery.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!hasRequiredPermissions()) return
        bluetoothAdapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
    }

    /**
     * Returns the set of bonded (paired) Bluetooth devices.
     */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        if (!hasRequiredPermissions()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Connects HID profile to target Bluetooth device (e.g. Android TV).
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        if (!hasRequiredPermissions()) return false
        stopDiscovery()
        return hidDevice?.connect(device) ?: false
    }

    /**
     * Disconnects active HID connection.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!hasRequiredPermissions()) return
        val target = connectedDevice ?: return
        hidDevice?.disconnect(target)
    }

    /**
     * Constructs the 3-byte Gamepad HID report:
     * - Byte 0: Steering X-Axis Low Byte
     * - Byte 1: Steering X-Axis High Byte
     * - Byte 2: Button Bitmask (Throttle, Brake, Handbrake)
     */
    fun buildReportPayload(): ByteArray {
        var buttons: Byte = 0
        if (currentThrottle) buttons = (buttons.toInt() or 0x01).toByte()
        if (currentBrake) buttons = (buttons.toInt() or 0x02).toByte()
        if (currentHandbrake) buttons = (buttons.toInt() or 0x04).toByte()

        val xLow = (currentStickX.toInt() and 0xFF).toByte()
        val xHigh = ((currentStickX.toInt() shr 8) and 0xFF).toByte()

        return byteArrayOf(xLow, xHigh, buttons)
    }

    /**
     * Transmits an updated HID input report to the connected Android TV.
     *
     * @param stickX 16-bit steering stick value (-32768 to +32767).
     * @param throttle Throttle pedal state (Button 1 / A).
     * @param brake Brake pedal state (Button 2 / B).
     * @param handbrake Handbrake state (Button 3 / X).
     * @return True if report was sent to Bluetooth stack, false otherwise.
     */
    @SuppressLint("MissingPermission")
    fun sendInputReport(
        stickX: Short,
        throttle: Boolean,
        brake: Boolean,
        handbrake: Boolean
    ): Boolean {
        currentStickX = stickX
        currentThrottle = throttle
        currentBrake = brake
        currentHandbrake = handbrake

        val dev = connectedDevice ?: return false
        val hid = hidDevice ?: return false
        if (!hasRequiredPermissions()) return false

        val payload = buildReportPayload()
        return hid.sendReport(dev, 0, payload)
    }

    /**
     * Cleans up resources, receivers, and closes profile proxy.
     */
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
