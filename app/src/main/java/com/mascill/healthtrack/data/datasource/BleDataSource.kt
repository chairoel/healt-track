package com.mascill.healthtrack.data.datasource

import com.mascill.healthtrack.data.model.BleConnectEvent
import com.mascill.healthtrack.data.model.BleScanEvent
import kotlinx.coroutines.flow.Flow

/**
 * Platform scan and GATT for one scale session.
 * Tests mock this type. [BleDataSourceImpl] talks to the radio.
 * [connect] uses autoConnect false so scanning and `connectGatt` are not left running together.
 * Callers stop the scan before [connect] and collect [startScan] again after [BleConnectEvent.NotifyReady].
 */
interface BleDataSource {
    /**
     * True when the radio exists and is switched on.
     */
    fun bluetoothEnabled(): Boolean

    /**
     * Starts a low-latency scan and returns its events.
     * The scan is already running when this returns. Collect the flow to receive results.
     * Null when Bluetooth is off or scan permission is missing. The scan that was already
     * running is left in place in that case.
     * A started scan replaces the previous one. Cancelling the returned flow stops that scan.
     * The caller stops the scan before [connect].
     */
    fun startScan(): Flow<BleScanEvent>?

    /**
     * Stops the active scan. A result that belongs to this scan is ignored afterwards.
     */
    fun stopScan()

    /**
     * Connects [address] with autoConnect false and returns its events.
     * Null when Bluetooth is off, the connect permission is missing, or the device is unknown.
     * A missing permission leaves the current GATT client in place. An unknown device drops it.
     * Cancelling the returned flow drops that client.
     * [BleConnectEvent.NotifyReady] is emitted when RX notify is enabled.
     */
    fun connect(address: String): Flow<BleConnectEvent>?

    /**
     * Drops the current GATT client. Events from that client are ignored.
     */
    fun disconnect()

    /**
     * Stops scanning, closes GATT, and forgets devices seen in this session.
     */
    fun stop()
}
