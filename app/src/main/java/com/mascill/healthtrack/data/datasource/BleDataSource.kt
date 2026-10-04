package com.mascill.healthtrack.data.datasource

/**
 * Platform scan and GATT for one scale session.
 * Tests mock this type. [BleDataSourceImpl] talks to the radio.
 * [connect] uses autoConnect false so scanning and `connectGatt` are not left running together.
 * Callers stop the scan before [connect] and start it again after [onNotifyReady].
 */
interface BleDataSource {
    /**
     * True when the radio exists and is switched on.
     */
    fun bluetoothEnabled(): Boolean

    /**
     * Starts a low-latency scan. Stops a scan that is already running.
     * Returns false when Bluetooth is off or scan permission is missing.
     *
     * [onManufacturer] receives one manufacturer block. Return true when that block is a scale
     * payload so later blocks in the same result are skipped.
     * [onRecord] receives the raw advertisement when no manufacturer block was accepted.
     * [onScanFailed] runs when the scanner reports a failure other than an already-started scan.
     */
    fun startScan(
        onManufacturer: (
            address: String,
            companyId: Int,
            remainder: ByteArray,
            connectable: Boolean,
        ) -> Boolean,
        onRecord: (address: String, record: ByteArray, connectable: Boolean) -> Unit,
        onScanFailed: () -> Unit,
    ): Boolean

    /**
     * Stops the active scan. A callback that belongs to this scan is ignored after wards.
     */
    fun stopScan()

    /**
     * Connects [address] with autoConnect false.
     * Returns false when the device or the connect permission is missing.
     * Later failures are reported through [onFailed].
     * [onNotifyReady] runs when RX notify is enabled.
     */
    fun connect(
        address: String,
        onNotification: (value: ByteArray) -> Unit,
        onNotifyReady: () -> Unit,
        onLinkLost: () -> Unit,
        onNotifyMissing: () -> Unit,
        onFailed: () -> Unit,
    ): Boolean

    /**
     * Drops the current GATT client. Callbacks from that client are ignored.
     */
    fun disconnect()

    /**
     * Stops scanning, closes GATT, and forgets devices seen in this session.
     */
    fun stop()
}
