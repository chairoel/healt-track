package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.BleDataSource
import com.mascill.healthtrack.data.model.BleConnectEvent
import com.mascill.healthtrack.data.model.BleScanEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Platform scan and GATT for one scale.
 * Collects [BleDataSource] flows into [ScaleRadioEvent]. This class does not apply them to a measurement.
 */
@Singleton
class ScaleRadioRepositoryImpl @Inject constructor(
    private val ble: BleDataSource,
    private val transmissions: ScaleTransmissionRepository,
) : ScaleRadioRepository {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var scanJob: Job? = null
    private var connectJob: Job? = null
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
        cancelCollectors()
    }

    override fun startScan(): Boolean {
        val generation = currentGeneration()
        val radioEvents = ble.startScan() ?: return false
        val previousJob = scanJob
        scanJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            radioEvents.collect { event ->
                when (event) {
                    is BleScanEvent.Advertisement -> acceptAdvertisement(generation, event)
                    BleScanEvent.Failed -> trySend(generation, ScaleScanFailed)
                }
            }
        }
        previousJob?.cancel()
        return true
    }

    override fun stopScan() {
        ble.stopScan()
        scanJob?.cancel()
        scanJob = null
    }

    override fun connect(address: String): Int {
        val generation = currentGeneration()
        val epoch = synchronized(lock) {
            connectEpoch += 1
            connectEpoch
        }
        val radioEvents = ble.connect(address)
        if (radioEvents == null) {
            trySend(generation, ScaleConnectFailed(epoch))
            return epoch
        }
        val previousJob = connectJob
        connectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            radioEvents.collect { event ->
                sendIfLive(generation, epoch, event.toScaleEvent(epoch))
            }
        }
        previousJob?.cancel()
        return epoch
    }

    override fun disconnect() {
        synchronized(lock) { connectEpoch += 1 }
        ble.disconnect()
        connectJob?.cancel()
        connectJob = null
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
        cancelCollectors()
    }

    private fun acceptAdvertisement(generation: Int, event: BleScanEvent.Advertisement) {
        for (block in event.manufacturers) {
            val payload = try {
                transmissions.payloadFromManufacturer(block.companyId, block.remainder.toByteArray())
            } catch (error: IllegalArgumentException) {
                null
            }
            if (payload != null) {
                trySend(generation, ScaleAdvertisement(event.address, payload, event.connectable))
                return
            }
        }
        val record = event.record?.toByteArray() ?: return
        val payload = transmissions.scalePayload(record) ?: return
        trySend(generation, ScaleAdvertisement(event.address, payload, event.connectable))
    }

    private fun cancelCollectors() {
        scanJob?.cancel()
        scanJob = null
        connectJob?.cancel()
        connectJob = null
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

private fun BleConnectEvent.toScaleEvent(epoch: Int): ScaleRadioEvent = when (this) {
    is BleConnectEvent.Notification -> ScaleNotification(epoch, value.toByteArray())
    BleConnectEvent.NotifyReady -> ScaleNotifyReady(epoch)
    BleConnectEvent.LinkLost -> ScaleLinkLost(epoch)
    BleConnectEvent.NotifyMissing -> ScaleNotifyMissing(epoch)
    BleConnectEvent.Failed -> ScaleConnectFailed(epoch)
}
