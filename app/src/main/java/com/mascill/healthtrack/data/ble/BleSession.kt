package com.mascill.healthtrack.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chipsea notify characteristic. The session reads this and does not write the TX characteristic
 * `0000fff2-0000-1000-8000-00805f9b34fb`.
 */
private val CHIPSEA_SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val CHIPSEA_RX: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private const val AUTO_CONNECT = false

/**
 * Delivers one manufacturer block from a scan result.
 * Return true when the block is a scale payload so later blocks in that result are skipped.
 */
interface ScanSink {
    /**
     * [companyId] plus [remainder] from one manufacturer AD structure.
     */
    fun onManufacturer(
        address: String,
        companyId: Int,
        remainder: ByteArray,
        connectable: Boolean,
    ): Boolean

    /**
     * Raw advertisement bytes when no manufacturer block was accepted.
     */
    fun onRecord(address: String, record: ByteArray, connectable: Boolean)

    /**
     * The scanner reported a failure other than an already-started scan.
     */
    fun onScanFailed()
}

/**
 * GATT events for one connection attempt.
 * Callbacks may arrive on a binder thread.
 */
interface ConnectSink {
    /**
     * A copied RX notification.
     */
    fun onNotification(value: ByteArray)

    /**
     * RX notify is enabled.
     */
    fun onNotifyReady()

    /**
     * The link dropped after a clean disconnect.
     */
    fun onLinkLost()

    /**
     * The Chipsea service or RX characteristic is absent.
     */
    fun onNotifyMissing()

    /**
     * Connect, discovery, or the notify descriptor failed.
     */
    fun onFailed()
}

/**
 * Platform scan and GATT for one scale session.
 * [connect] uses [AUTO_CONNECT] so scanning and `connectGatt` are not left running together.
 * Callers stop the scan before [connect] and start it again after [ConnectSink.onNotifyReady].
 */
@Singleton
class BleSession @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private val devices = HashMap<String, BluetoothDevice>()
    private var scanToken = 0
    private var gattToken = 0
    private var scannerCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var scanSink: ScanSink? = null

    @Volatile
    private var connectSink: ConnectSink? = null

    /**
     * True when the radio exists and is switched on.
     */
    fun bluetoothEnabled(): Boolean {
        return try {
            adapter()?.isEnabled == true
        } catch (error: SecurityException) {
            false
        }
    }

    /**
     * Starts a low-latency scan. Stops a scan that is already running.
     * Returns false when Bluetooth is off or scan permission is missing.
     */
    @SuppressLint("MissingPermission")
    fun startScan(sink: ScanSink): Boolean {
        if (!bluetoothEnabled() || !canScan()) {
            return false
        }
        stopScan()
        val radio = adapter()?.bluetoothLeScanner ?: return false
        val token = synchronized(lock) { scanToken }
        scanSink = sink
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(token, result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> handleResult(token, result) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                    return
                }
                if (!scanLive(token)) {
                    return
                }
                scanSink?.onScanFailed()
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return try {
            radio.startScan(null, settings, callback)
            synchronized(lock) {
                if (token != scanToken) {
                    radio.stopScan(callback)
                    false
                } else {
                    scannerCallback = callback
                    true
                }
            }
        } catch (error: SecurityException) {
            false
        } catch (error: IllegalStateException) {
            false
        }
    }

    /**
     * Stops the active scan. A callback that belongs to this scan is ignored afterwards.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        val previous = synchronized(lock) {
            scanToken += 1
            scannerCallback.also { scannerCallback = null }
        }
        scanSink = null
        if (previous == null) {
            return
        }
        val radio = adapter()?.bluetoothLeScanner ?: return
        try {
            radio.stopScan(previous)
        } catch (error: SecurityException) {
            // Permission is already gone.
        } catch (error: IllegalStateException) {
            // The adapter was switched off.
        }
    }

    /**
     * Connects [address] with [AUTO_CONNECT].
     * Returns false when the device or the connect permission is missing.
     * Later failures are reported through [sink].
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun connect(address: String, sink: ConnectSink): Boolean {
        if (!bluetoothEnabled() || !canConnectRadio()) {
            return false
        }
        disconnect()
        val device = findDevice(address) ?: return false
        val token = synchronized(lock) { gattToken }
        connectSink = sink
        val callback = GattCallback(token)
        return try {
            val created = device.connectGatt(
                context,
                AUTO_CONNECT,
                callback,
                BluetoothDevice.TRANSPORT_LE,
            ) ?: return false
            synchronized(lock) {
                if (token != gattToken) {
                    created.close()
                    false
                } else {
                    gatt = created
                    true
                }
            }
        } catch (error: SecurityException) {
            false
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    /**
     * Drops the current GATT client. Callbacks from that client are ignored.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        val previous = synchronized(lock) {
            gattToken += 1
            connectSink = null
            gatt.also { gatt = null }
        }
        if (previous == null) {
            return
        }
        try {
            previous.disconnect()
        } catch (error: SecurityException) {
            // Permission is already gone.
        }
        try {
            previous.close()
        } catch (error: SecurityException) {
            // The client is already closed.
        }
    }

    /**
     * Stops scanning, closes GATT, and forgets devices seen in this session.
     */
    fun stop() {
        stopScan()
        disconnect()
        synchronized(lock) { devices.clear() }
    }

    private fun handleResult(token: Int, result: ScanResult) {
        if (!scanLive(token)) {
            return
        }
        val sink = scanSink ?: return
        val record = result.scanRecord ?: return
        val address = try {
            result.device.address
        } catch (error: SecurityException) {
            return
        }
        if (address.isNullOrEmpty()) {
            return
        }
        synchronized(lock) { devices[address] = result.device }
        val connectable = result.isConnectable
        val sparse = record.manufacturerSpecificData
        var accepted = false
        if (sparse != null) {
            for (index in 0 until sparse.size()) {
                val remainder = sparse.valueAt(index)?.copyOf() ?: continue
                if (sink.onManufacturer(address, sparse.keyAt(index), remainder, connectable)) {
                    accepted = true
                    break
                }
            }
        }
        if (!accepted) {
            val raw = record.bytes?.copyOf() ?: return
            sink.onRecord(address, raw, connectable)
        }
    }

    private fun scanLive(token: Int): Boolean {
        return synchronized(lock) { token == scanToken }
    }

    private fun findDevice(address: String): BluetoothDevice? {
        val cached = synchronized(lock) {
            devices.entries.firstOrNull { it.key.equals(address, ignoreCase = true) }?.value
        }
        if (cached != null) {
            return cached
        }
        val radio = adapter() ?: return null
        return try {
            radio.getRemoteDevice(address)
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun adapter(): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        return manager.adapter
    }

    private fun canScan(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(android.Manifest.permission.BLUETOOTH_SCAN)
        } else {
            granted(android.Manifest.permission.BLUETOOTH) &&
                granted(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun canConnectRadio(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(android.Manifest.permission.BLUETOOTH)
        }
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private inner class GattCallback(
        private val token: Int,
    ) : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!live()) {
                return
            }
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    val started = try {
                        gatt.discoverServices()
                    } catch (error: SecurityException) {
                        false
                    }
                    if (!started) {
                        connectSink?.onFailed()
                    }
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectSink?.onLinkLost()
                    } else {
                        connectSink?.onFailed()
                    }
                }
                status != BluetoothGatt.GATT_SUCCESS -> connectSink?.onFailed()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!live()) {
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectSink?.onFailed()
                return
            }
            val rx = gatt.getService(CHIPSEA_SERVICE)?.getCharacteristic(CHIPSEA_RX)
            if (rx == null) {
                connectSink?.onNotifyMissing()
                return
            }
            enableNotify(gatt, rx)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Use the ByteArray overload from API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return
            }
            val value = characteristic.value ?: return
            deliver(gatt, characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            deliver(gatt, characteristic, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (!live() || descriptor.uuid != CLIENT_CONFIG) {
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectSink?.onNotifyReady()
            } else {
                connectSink?.onFailed()
            }
        }

        private fun enableNotify(gatt: BluetoothGatt, rx: BluetoothGattCharacteristic) {
            val value = when {
                rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                rx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> {
                    connectSink?.onNotifyMissing()
                    return
                }
            }
            val enabled = try {
                gatt.setCharacteristicNotification(rx, true)
            } catch (error: SecurityException) {
                false
            }
            if (!enabled) {
                connectSink?.onFailed()
                return
            }
            val descriptor = rx.getDescriptor(CLIENT_CONFIG)
            if (descriptor == null) {
                connectSink?.onNotifyMissing()
                return
            }
            if (!writeConfig(gatt, descriptor, value)) {
                connectSink?.onFailed()
            }
        }

        @SuppressLint("MissingPermission")
        private fun writeConfig(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            value: ByteArray,
        ): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } catch (error: SecurityException) {
                false
            }
        }

        private fun deliver(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (!live() || characteristic.uuid != CHIPSEA_RX) {
                return
            }
            connectSink?.onNotification(value.copyOf())
        }

        private fun live(): Boolean {
            return synchronized(lock) { token == gattToken }
        }
    }
}
