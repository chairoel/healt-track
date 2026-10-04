package com.mascill.healthtrack.data.datasource

import android.Manifest
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
import androidx.core.util.size
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
 * [BleDataSource] on the phone radio.
 */
@Singleton
class BleDataSourceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BleDataSource {
    private val lock = Any()
    private val devices = HashMap<String, BluetoothDevice>()
    private var scanToken = 0
    private var gattToken = 0
    private var scannerCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var scanHandlers: ScanHandlers? = null

    @Volatile
    private var connectHandlers: ConnectHandlers? = null

    override fun bluetoothEnabled(): Boolean {
        return try {
            adapter()?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan(
        onManufacturer: (String, Int, ByteArray, Boolean) -> Boolean,
        onRecord: (String, ByteArray, Boolean) -> Unit,
        onScanFailed: () -> Unit,
    ): Boolean {
        if (!bluetoothEnabled() || !canScan()) {
            return false
        }
        stopScan()
        val radio = adapter()?.bluetoothLeScanner ?: return false
        val token = synchronized(lock) { scanToken }
        scanHandlers = ScanHandlers(onManufacturer, onRecord, onScanFailed)
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
                scanHandlers?.onScanFailed?.invoke()
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
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        val previous = synchronized(lock) {
            scanToken += 1
            scannerCallback.also { scannerCallback = null }
        }
        scanHandlers = null
        if (previous == null) {
            return
        }
        val radio = adapter()?.bluetoothLeScanner ?: return
        try {
            radio.stopScan(previous)
        } catch (_: SecurityException) {
            // Permission is already gone.
        } catch (_: IllegalStateException) {
            // The adapter was switched off.
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun connect(
        address: String,
        onNotification: (ByteArray) -> Unit,
        onNotifyReady: () -> Unit,
        onLinkLost: () -> Unit,
        onNotifyMissing: () -> Unit,
        onFailed: () -> Unit,
    ): Boolean {
        if (!bluetoothEnabled() || !canConnectRadio()) {
            return false
        }
        disconnect()
        val device = findDevice(address) ?: return false
        val token = synchronized(lock) { gattToken }
        connectHandlers = ConnectHandlers(
            onNotification,
            onNotifyReady,
            onLinkLost,
            onNotifyMissing,
            onFailed,
        )
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
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        val previous = synchronized(lock) {
            gattToken += 1
            connectHandlers = null
            gatt.also { gatt = null }
        }
        if (previous == null) {
            return
        }
        try {
            previous.disconnect()
        } catch (_: SecurityException) {
            // Permission is already gone.
        }
        try {
            previous.close()
        } catch (_: SecurityException) {
            // The client is already closed.
        }
    }

    override fun stop() {
        stopScan()
        disconnect()
        synchronized(lock) { devices.clear() }
    }

    private fun handleResult(token: Int, result: ScanResult) {
        if (!scanLive(token)) {
            return
        }
        val handlers = scanHandlers ?: return
        val record = result.scanRecord ?: return
        val address = try {
            result.device.address
        } catch (_: SecurityException) {
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
            for (index in 0 until sparse.size) {
                val remainder = sparse.valueAt(index)?.copyOf() ?: continue
                if (handlers.onManufacturer(address, sparse.keyAt(index), remainder, connectable)) {
                    accepted = true
                    break
                }
            }
        }
        if (!accepted) {
            val raw = record.bytes?.copyOf() ?: return
            handlers.onRecord(address, raw, connectable)
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
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun adapter(): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        return manager.adapter
    }

    private fun canScan(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            granted(Manifest.permission.BLUETOOTH) &&
                granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun canConnectRadio(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.BLUETOOTH)
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
                    } catch (_: SecurityException) {
                        false
                    }
                    if (!started) {
                        connectHandlers?.onFailed?.invoke()
                    }
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectHandlers?.onLinkLost?.invoke()
                    } else {
                        connectHandlers?.onFailed?.invoke()
                    }
                }
                status != BluetoothGatt.GATT_SUCCESS -> connectHandlers?.onFailed?.invoke()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!live()) {
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectHandlers?.onFailed?.invoke()
                return
            }
            val rx = gatt.getService(CHIPSEA_SERVICE)?.getCharacteristic(CHIPSEA_RX)
            if (rx == null) {
                connectHandlers?.onNotifyMissing?.invoke()
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
                connectHandlers?.onNotifyReady?.invoke()
            } else {
                connectHandlers?.onFailed?.invoke()
            }
        }

        private fun enableNotify(gatt: BluetoothGatt, rx: BluetoothGattCharacteristic) {
            val value = when {
                rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                rx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> {
                    connectHandlers?.onNotifyMissing?.invoke()
                    return
                }
            }
            val enabled = try {
                gatt.setCharacteristicNotification(rx, true)
            } catch (_: SecurityException) {
                false
            }
            if (!enabled) {
                connectHandlers?.onFailed?.invoke()
                return
            }
            val descriptor = rx.getDescriptor(CLIENT_CONFIG)
            if (descriptor == null) {
                connectHandlers?.onNotifyMissing?.invoke()
                return
            }
            if (!writeConfig(gatt, descriptor, value)) {
                connectHandlers?.onFailed?.invoke()
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
            } catch (_: SecurityException) {
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
            connectHandlers?.onNotification?.invoke(value.copyOf())
        }

        private fun live(): Boolean {
            return synchronized(lock) { token == gattToken }
        }
    }

    private class ScanHandlers(
        val onManufacturer: (String, Int, ByteArray, Boolean) -> Boolean,
        val onRecord: (String, ByteArray, Boolean) -> Unit,
        val onScanFailed: () -> Unit,
    )

    private class ConnectHandlers(
        val onNotification: (ByteArray) -> Unit,
        val onNotifyReady: () -> Unit,
        val onLinkLost: () -> Unit,
        val onNotifyMissing: () -> Unit,
        val onFailed: () -> Unit,
    )
}
