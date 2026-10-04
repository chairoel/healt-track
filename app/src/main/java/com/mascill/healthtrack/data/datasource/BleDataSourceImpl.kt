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
import com.mascill.healthtrack.data.model.BleConnectEvent
import com.mascill.healthtrack.data.model.BleManufacturerBlock
import com.mascill.healthtrack.data.model.BleScanEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion

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
    private var scanEvents: Channel<BleScanEvent>? = null
    private var gatt: BluetoothGatt? = null
    private var connectEvents: Channel<BleConnectEvent>? = null

    override fun bluetoothEnabled(): Boolean {
        return try {
            adapter()?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan(): Flow<BleScanEvent>? {
        if (!bluetoothEnabled() || !canScan()) {
            return null
        }
        stopScan()
        val radio = adapter()?.bluetoothLeScanner ?: return null
        val events = Channel<BleScanEvent>(Channel.UNLIMITED)
        val token = synchronized(lock) { scanToken }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(token, result, events)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> handleResult(token, result, events) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (errorCode == SCAN_FAILED_ALREADY_STARTED || !scanLive(token)) {
                    return
                }
                events.trySend(BleScanEvent.Failed)
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return try {
            radio.startScan(null, settings, callback)
            val kept = synchronized(lock) {
                if (token != scanToken) {
                    radio.stopScan(callback)
                    false
                } else {
                    scannerCallback = callback
                    scanEvents = events
                    true
                }
            }
            if (!kept) {
                events.close()
                null
            } else {
                events.consumeAsFlow().onCompletion { stopOwningScan(token) }
            }
        } catch (_: SecurityException) {
            events.close()
            null
        } catch (_: IllegalStateException) {
            events.close()
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        val previousCallback: ScanCallback?
        val previousEvents: Channel<BleScanEvent>?
        synchronized(lock) {
            scanToken += 1
            previousCallback = scannerCallback
            scannerCallback = null
            previousEvents = scanEvents
            scanEvents = null
        }
        previousEvents?.close()
        if (previousCallback == null) {
            return
        }
        val radio = adapter()?.bluetoothLeScanner ?: return
        try {
            radio.stopScan(previousCallback)
        } catch (_: SecurityException) {
            // Permission is already gone.
        } catch (_: IllegalStateException) {
            // The adapter was switched off.
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun connect(address: String): Flow<BleConnectEvent>? {
        if (!bluetoothEnabled() || !canConnectRadio()) {
            return null
        }
        disconnect()
        val device = findDevice(address) ?: return null
        val events = Channel<BleConnectEvent>(Channel.UNLIMITED)
        val token = synchronized(lock) { gattToken }
        val callback = GattCallback(token, events)
        return try {
            val created = device.connectGatt(
                context,
                AUTO_CONNECT,
                callback,
                BluetoothDevice.TRANSPORT_LE,
            )
            if (created == null) {
                events.close()
                return null
            }
            val kept = synchronized(lock) {
                if (token != gattToken) {
                    created.close()
                    false
                } else {
                    gatt = created
                    connectEvents = events
                    true
                }
            }
            if (!kept) {
                events.close()
                null
            } else {
                events.consumeAsFlow().onCompletion { disconnectIfOwner(token) }
            }
        } catch (_: SecurityException) {
            events.close()
            null
        } catch (_: IllegalArgumentException) {
            events.close()
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        val previousClient: BluetoothGatt?
        val previousEvents: Channel<BleConnectEvent>?
        synchronized(lock) {
            gattToken += 1
            previousEvents = connectEvents
            connectEvents = null
            previousClient = gatt
            gatt = null
        }
        previousEvents?.close()
        if (previousClient == null) {
            return
        }
        try {
            previousClient.disconnect()
        } catch (_: SecurityException) {
            // Permission is already gone.
        }
        try {
            previousClient.close()
        } catch (_: SecurityException) {
            // The client is already closed.
        }
    }

    override fun stop() {
        stopScan()
        disconnect()
        synchronized(lock) { devices.clear() }
    }

    private fun handleResult(token: Int, result: ScanResult, events: Channel<BleScanEvent>) {
        if (!scanLive(token)) {
            return
        }
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
        val blocks = buildList {
            val sparse = record.manufacturerSpecificData ?: return@buildList
            for (index in 0 until sparse.size) {
                val remainder = sparse.valueAt(index)?.toList() ?: continue
                add(BleManufacturerBlock(sparse.keyAt(index), remainder))
            }
        }
        val raw = record.bytes?.toList()
        if (blocks.isEmpty() && raw == null) {
            return
        }
        events.trySend(
            BleScanEvent.Advertisement(
                address = address,
                connectable = result.isConnectable,
                manufacturers = blocks,
                record = raw,
            ),
        )
    }

    private fun stopOwningScan(token: Int) {
        if (!scanLive(token)) {
            return
        }
        stopScan()
    }

    private fun disconnectIfOwner(token: Int) {
        val owns = synchronized(lock) { token == gattToken }
        if (owns) {
            disconnect()
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
        private val events: Channel<BleConnectEvent>,
    ) : BluetoothGattCallback() {
        private fun emit(event: BleConnectEvent) {
            if (!live()) {
                return
            }
            events.trySend(event)
        }

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
                        emit(BleConnectEvent.Failed)
                    }
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        emit(BleConnectEvent.LinkLost)
                    } else {
                        emit(BleConnectEvent.Failed)
                    }
                }
                status != BluetoothGatt.GATT_SUCCESS -> emit(BleConnectEvent.Failed)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!live()) {
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit(BleConnectEvent.Failed)
                return
            }
            val rx = gatt.getService(CHIPSEA_SERVICE)?.getCharacteristic(CHIPSEA_RX)
            if (rx == null) {
                emit(BleConnectEvent.NotifyMissing)
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
                emit(BleConnectEvent.NotifyReady)
            } else {
                emit(BleConnectEvent.Failed)
            }
        }

        private fun enableNotify(gatt: BluetoothGatt, rx: BluetoothGattCharacteristic) {
            val value = when {
                rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                rx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> {
                    emit(BleConnectEvent.NotifyMissing)
                    return
                }
            }
            val enabled = try {
                gatt.setCharacteristicNotification(rx, true)
            } catch (_: SecurityException) {
                false
            }
            if (!enabled) {
                emit(BleConnectEvent.Failed)
                return
            }
            val descriptor = rx.getDescriptor(CLIENT_CONFIG)
            if (descriptor == null) {
                emit(BleConnectEvent.NotifyMissing)
                return
            }
            if (!writeConfig(gatt, descriptor, value)) {
                emit(BleConnectEvent.Failed)
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
            emit(BleConnectEvent.Notification(value.toList()))
        }

        private fun live(): Boolean {
            return synchronized(lock) { token == gattToken }
        }
    }
}
