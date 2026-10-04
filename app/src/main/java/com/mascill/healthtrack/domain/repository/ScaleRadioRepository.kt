package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.ScaleRadioEvent
import kotlinx.coroutines.flow.Flow

/**
 * Bluetooth radio for one scale.
 * Scanning and GATT stay behind this interface. The caller applies measurement frames.
 *
 * [events] is the Flow that emits radio events for the active session opened by [begin].
 * Cancelling the collection of [events] does not stop the session; call [end] to do so.
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
     * [com.mascill.healthtrack.domain.model.ScaleRadioEvent.ConnectFailed] with that epoch.
     */
    fun connect(address: String): Int

    /**
     * Drops the current GATT client.
     * Callbacks from that client are ignored.
     */
    fun disconnect()

    /**
     * Flow of events from the session opened by [begin].
     * Each [begin] replaces the previous stream; stale collectors receive completion.
     */
    val events: Flow<ScaleRadioEvent>

    /**
     * Stops scanning, closes GATT, and closes the event stream.
     */
    fun end()
}
