package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.BleDataSource
import com.mascill.healthtrack.domain.model.ScaleAdvertisement
import com.mascill.healthtrack.domain.model.ScaleConnectFailed
import com.mascill.healthtrack.domain.model.ScaleLinkLost
import com.mascill.healthtrack.domain.model.ScaleNotification
import com.mascill.healthtrack.domain.model.ScaleNotifyMissing
import com.mascill.healthtrack.domain.model.ScaleNotifyReady
import com.mascill.healthtrack.domain.model.ScaleRadioEvent
import com.mascill.healthtrack.domain.model.ScaleScanFailed
import com.mascill.healthtrack.domain.repository.ScaleRadioRepository
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel

/**
 * Platform scan and GATT for one scale.
 * Frames are delivered as [ScaleRadioEvent]. This class does not apply them to a measurement.
 */
@Singleton
class ScaleRadioRepositoryImpl @Inject constructor(
    private val ble: BleDataSource,
    private val transmissions: ScaleTransmissionRepository,
) : ScaleRadioRepository {
    private val lock = Any()
    private var sessionGeneration = 0
    private var connectEpoch = 0
    private var events = Channel<ScaleRadioEvent>(Channel.UNLIMITED)

    override fun bluetoothEnabled(): Boolean = ble.bluetoothEnabled()

    override fun begin() {
        val previous = synchronized(lock) {
            sessionGeneration += 1
            connectEpoch = 0
            events.also { events = Channel(Channel.UNLIMITED) }
        }
        previous.close()
        ble.stop()
    }

    override fun startScan(): Boolean {
        val generation = currentGeneration()
        return ble.startScan(
            onManufacturer = { address, companyId, remainder, connectable ->
                acceptManufacturer(generation, address, companyId, remainder, connectable)
            },
            onRecord = { address, record, connectable ->
                acceptRecord(generation, address, record, connectable)
            },
            onScanFailed = { trySend(generation, ScaleScanFailed) },
        )
    }

    override fun stopScan() {
        ble.stopScan()
    }

    override fun connect(address: String): Int {
        val generation = currentGeneration()
        val epoch = synchronized(lock) {
            connectEpoch += 1
            connectEpoch
        }
        val started = ble.connect(
            address = address,
            onNotification = { value -> sendIfLive(generation, epoch, ScaleNotification(epoch, value)) },
            onNotifyReady = { sendIfLive(generation, epoch, ScaleNotifyReady(epoch)) },
            onLinkLost = { sendIfLive(generation, epoch, ScaleLinkLost(epoch)) },
            onNotifyMissing = { sendIfLive(generation, epoch, ScaleNotifyMissing(epoch)) },
            onFailed = { sendIfLive(generation, epoch, ScaleConnectFailed(epoch)) },
        )
        if (!started) {
            trySend(generation, ScaleConnectFailed(epoch))
        }
        return epoch
    }

    override fun disconnect() {
        synchronized(lock) { connectEpoch += 1 }
        ble.disconnect()
    }

    override suspend fun awaitEvent(): ScaleRadioEvent {
        val channel = synchronized(lock) { events }
        return channel.receive()
    }

    override fun end() {
        val previous = synchronized(lock) {
            sessionGeneration += 1
            connectEpoch += 1
            events.also { events = Channel(Channel.UNLIMITED) }
        }
        previous.close()
        ble.stop()
    }

    private fun acceptManufacturer(
        generation: Int,
        address: String,
        companyId: Int,
        remainder: ByteArray,
        connectable: Boolean,
    ): Boolean {
        val payload = try {
            transmissions.payloadFromManufacturer(companyId, remainder)
        } catch (error: IllegalArgumentException) {
            null
        } ?: return false
        trySend(generation, ScaleAdvertisement(address, payload, connectable))
        return true
    }

    private fun acceptRecord(
        generation: Int,
        address: String,
        record: ByteArray,
        connectable: Boolean,
    ) {
        val payload = transmissions.scalePayload(record) ?: return
        trySend(generation, ScaleAdvertisement(address, payload, connectable))
    }

    private fun sendIfLive(generation: Int, epoch: Int, event: ScaleRadioEvent) {
        if (!epochLive(epoch)) {
            return
        }
        trySend(generation, event)
    }

    private fun trySend(generation: Int, event: ScaleRadioEvent) {
        val channel = synchronized(lock) {
            if (generation != sessionGeneration) {
                return
            }
            events
        }
        channel.trySend(event)
    }

    private fun epochLive(epoch: Int): Boolean {
        return synchronized(lock) { epoch == connectEpoch }
    }

    private fun currentGeneration(): Int = synchronized(lock) { sessionGeneration }
}
