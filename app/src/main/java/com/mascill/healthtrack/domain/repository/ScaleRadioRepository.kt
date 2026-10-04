package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.ScaleRadioEvent

/**
 * Bluetooth radio for one scale.
 * Scanning and GATT stay behind this interface. The caller applies measurement frames.
 */
interface ScaleRadioRepository {
    /**
     * True when the radio exists and is switched on.
     */
    fun bluetoothEnabled(): Boolean

    /**
     * Drops a previous radio session and opens a new event stream.
     */
    fun begin()

    /**
     * Starts a low-latency scan.
     * Returns false when Bluetooth is off or scan permission is missing.
     */
    fun startScan(): Boolean

    /**
     * Stops the active scan.
     */
    fun stopScan()

    /**
     * Connects [address].
     * Returns the epoch of this attempt. A refused connect is also reported as
     * [com.mascill.healthtrack.domain.model.ScaleConnectFailed] with that epoch.
     */
    fun connect(address: String): Int

    /**
     * Drops the current GATT client.
     * Callbacks from that client are ignored.
     */
    fun disconnect()

    /**
     * Suspends until the next event from the stream opened by [begin].
     */
    suspend fun awaitEvent(): ScaleRadioEvent

    /**
     * Stops scanning, closes GATT, and closes the event stream.
     */
    fun end()
}
