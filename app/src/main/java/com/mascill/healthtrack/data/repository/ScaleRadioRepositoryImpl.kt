package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.BleDataSource
import com.mascill.healthtrack.data.model.BleConnectEvent
import com.mascill.healthtrack.data.model.BleScanEvent
import com.mascill.healthtrack.domain.model.ScaleRadioEvent
import com.mascill.healthtrack.domain.repository.ScaleRadioRepository
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import com.mascill.healthtrack.util.AppDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Platform scan and GATT for one scale.
 * Collects [BleDataSource] flows into [ScaleRadioEvent]. This class does not apply them to a measurement.
 */
@Singleton
class ScaleRadioRepositoryImpl @Inject constructor(
    private val ble: BleDataSource,
    private val transmissions: ScaleTransmissionRepository,
    dispatcher: AppDispatcher,
) : ScaleRadioRepository {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher.default)
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var sessionGeneration = 0
    private var connectEpoch = 0
    private var eventChannel = Channel<ScaleRadioEvent>(Channel.UNLIMITED)

    override val events: Flow<ScaleRadioEvent>
        get() = synchronized(lock) { eventChannel }.receiveAsFlow()

    override fun bluetoothEnabled(): Boolean = ble.bluetoothEnabled()

    override fun begin() {
        val previous = synchronized(lock) {
            sessionGeneration += 1
            connectEpoch = 0
            eventChannel.also { eventChannel = Channel(Channel.UNLIMITED) }
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
                    BleScanEvent.Failed -> trySend(generation, ScaleRadioEvent.ScanFailed)
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
            trySend(generation, ScaleRadioEvent.ConnectFailed(epoch))
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

    override fun end() {
        val previous = synchronized(lock) {
            sessionGeneration += 1
            connectEpoch += 1
            eventChannel.also { eventChannel = Channel(Channel.UNLIMITED) }
        }
        previous.close()
        ble.stop()
        cancelCollectors()
    }

    private fun acceptAdvertisement(generation: Int, event: BleScanEvent.Advertisement) {
        for (block in event.manufacturers) {
            val payload = try {
                transmissions.payloadFromManufacturer(block.companyId, block.remainder.toByteArray())
            } catch (_: IllegalArgumentException) {
                null
            }
            if (payload != null) {
                trySend(generation, ScaleRadioEvent.Advertisement(event.address, payload.toList(), event.connectable))
                return
            }
        }
        val record = event.record?.toByteArray() ?: return
        val payload = transmissions.scalePayload(record) ?: return
        trySend(generation, ScaleRadioEvent.Advertisement(event.address, payload.toList(), event.connectable))
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
            eventChannel
        }
        channel.trySend(event)
    }

    private fun epochLive(epoch: Int): Boolean {
        return synchronized(lock) { epoch == connectEpoch }
    }

    private fun currentGeneration(): Int = synchronized(lock) { sessionGeneration }
}

private fun BleConnectEvent.toScaleEvent(epoch: Int): ScaleRadioEvent = when (this) {
    is BleConnectEvent.Notification -> ScaleRadioEvent.Notification(epoch, value)
    BleConnectEvent.NotifyReady -> ScaleRadioEvent.NotifyReady(epoch)
    BleConnectEvent.LinkLost -> ScaleRadioEvent.LinkLost(epoch)
    BleConnectEvent.NotifyMissing -> ScaleRadioEvent.NotifyMissing(epoch)
    BleConnectEvent.Failed -> ScaleRadioEvent.ConnectFailed(epoch)
}
