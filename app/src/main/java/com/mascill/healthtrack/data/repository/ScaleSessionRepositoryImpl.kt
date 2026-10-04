package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.ble.BleSession
import com.mascill.healthtrack.data.ble.ConnectSink
import com.mascill.healthtrack.data.ble.ScanSink
import com.mascill.healthtrack.data.ble.SessionListener
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import com.mascill.healthtrack.domain.repository.ScaleGattRepository
import com.mascill.healthtrack.domain.repository.ScaleSessionRepository
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import com.mascill.healthtrack.domain.usecase.AcceptGattUseCase
import com.mascill.healthtrack.domain.usecase.AcceptTransmissionUseCase
import com.mascill.healthtrack.domain.usecase.ApplyBroadcastWeightUseCase
import com.mascill.healthtrack.domain.usecase.MeasurementStateUseCase
import com.mascill.healthtrack.util.AppDispatcher
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_LIMIT_MS = 120_000L
private const val CONNECT_LIMIT_MS = 15_000L
private const val MAX_ATTEMPTS = 3
private const val MAX_ROUNDS = 2

/**
 * Runs one scale session: scan, then GATT, then scan again after RX notify is on.
 * [BleSession] is stopped before `connectGatt`. The two do not run together.
 */
@Singleton
class ScaleSessionRepositoryImpl @Inject constructor(
    private val ble: BleSession,
    private val transmissions: ScaleTransmissionRepository,
    private val gattFrames: ScaleGattRepository,
    private val acceptTransmission: AcceptTransmissionUseCase,
    private val acceptGatt: AcceptGattUseCase,
    private val applyBroadcastWeight: ApplyBroadcastWeightUseCase,
    private val measurementState: MeasurementStateUseCase,
    dispatcher: AppDispatcher,
) : ScaleSessionRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher.main)
    private val status = MutableStateFlow(ScaleSessionStatus())
    private var sessionToken = 0
    private var sessionJob: Job? = null

    override fun observe(): Flow<ScaleSessionStatus> = status

    override fun start(body: BodyData): Boolean {
        if (!ble.bluetoothEnabled()) {
            status.value = ScaleSessionStatus(bluetoothOff = true, heightCm = body.heightCm)
            return false
        }
        sessionToken += 1
        val token = sessionToken
        sessionJob?.cancel()
        sessionJob = null
        ble.stop()
        gattFrames.reset()
        val measurement = MeasurementSession(body)
        val listener = SessionListener(
            session = measurement,
            transmissions = transmissions,
            gattFrames = gattFrames,
            acceptTransmission = acceptTransmission,
            acceptGatt = acceptGatt,
            applyBroadcastWeight = applyBroadcastWeight,
        )
        val run = Run(
            token = token,
            measurement = measurement,
            listener = listener,
            heightCm = body.heightCm,
        )
        publish(run, running = true)
        sessionJob = scope.launch {
            try {
                runSession(run)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                run.scanFailed = true
            } finally {
                run.events.close()
                if (run.token == sessionToken) {
                    ble.stop()
                    publish(run, running = false)
                }
            }
        }
        return true
    }

    override fun stop() {
        sessionToken += 1
        sessionJob?.cancel()
        sessionJob = null
        ble.stop()
        status.value = status.value.copy(running = false)
    }

    private suspend fun runSession(run: Run) {
        if (run.token != sessionToken) {
            return
        }
        if (!ble.startScan(scanSink(run))) {
            run.scanFailed = true
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        while (coroutineContext.isActive && run.token == sessionToken) {
            coroutineContext.ensureActive()
            val now = SystemClock.elapsedRealtime()
            val sessionDeadline = startedAt + SESSION_LIMIT_MS
            if (now >= sessionDeadline) {
                break
            }
            val connectDeadline = run.connectDeadlineMs
            if (connectDeadline != null && now >= connectDeadline) {
                onConnectClock(run)
                continue
            }
            val nextWake = connectDeadline?.let { minOf(it, sessionDeadline) } ?: sessionDeadline
            val waitMs = (nextWake - now).coerceAtLeast(1L)
            val event = withTimeoutOrNull(waitMs) { run.events.receive() } ?: continue
            if (handle(run, event)) {
                break
            }
        }
    }

    private fun handle(run: Run, event: BleEvent): Boolean {
        if (run.token != sessionToken) {
            return true
        }
        when (event) {
            is Advertisement -> onAdvertisement(run, event)
            is Notification -> if (event.epoch == run.connectEpoch) {
                run.listener.onGatt(event.value)
            }
            is NotifyReady -> if (event.epoch == run.connectEpoch) {
                onReady(run)
            }
            is LinkLost -> if (event.epoch == run.connectEpoch) {
                failAttempt(run)
            }
            is NotifyMissing -> if (event.epoch == run.connectEpoch) {
                endRound(run)
            }
            is ConnectFailed -> if (event.epoch == run.connectEpoch) {
                failAttempt(run)
            }
            ScanFailed -> run.scanFailed = true
        }
        publish(run, running = true)
        return run.measurement.result != null
    }

    private fun onAdvertisement(run: Run, event: Advertisement) {
        run.listener.onAdvertisement(event.address, event.payload)
        run.listener.closeFromBroadcast(event.connectable, run.linkActive)
        if (run.measurement.result != null || run.linkActive || run.round >= MAX_ROUNDS) {
            return
        }
        if (!event.connectable || !run.listener.canConnect(event.address)) {
            return
        }
        beginConnect(run, event.address)
    }

    private fun beginConnect(run: Run, address: String) {
        run.targetAddress = address
        run.linkActive = true
        run.attempt = 1
        run.phase = Phase.Connecting
        run.connectDeadlineMs = SystemClock.elapsedRealtime() + CONNECT_LIMIT_MS
        ble.stopScan()
        openGatt(run)
    }

    private fun openGatt(run: Run) {
        run.connectEpoch += 1
        val epoch = run.connectEpoch
        val started = ble.connect(run.targetAddress, connectSink(run, epoch))
        if (!started) {
            run.events.trySend(ConnectFailed(epoch))
        }
    }

    private fun onReady(run: Run) {
        run.phase = Phase.Notified
        run.connectDeadlineMs = null
        if (!ble.startScan(scanSink(run))) {
            run.scanFailed = true
        }
    }

    private fun onConnectClock(run: Run) {
        if (run.phase != Phase.Connecting) {
            run.connectDeadlineMs = null
            return
        }
        run.connectDeadlineMs = null
        invalidate(run)
        run.connectionTimedOut = true
        failAttempt(run)
    }

    private fun failAttempt(run: Run) {
        if (run.token != sessionToken || run.measurement.result != null) {
            return
        }
        if (run.attempt < MAX_ATTEMPTS) {
            run.attempt += 1
            run.phase = Phase.Connecting
            run.connectDeadlineMs = SystemClock.elapsedRealtime() + CONNECT_LIMIT_MS
            openGatt(run)
        } else {
            endRound(run)
        }
    }

    private fun endRound(run: Run) {
        invalidate(run)
        run.round += 1
        run.linkActive = false
        run.phase = Phase.Scanning
        run.connectDeadlineMs = null
        if (run.round < MAX_ROUNDS && run.measurement.result == null && run.token == sessionToken) {
            if (!ble.startScan(scanSink(run))) {
                run.scanFailed = true
            }
        }
    }

    private fun invalidate(run: Run) {
        run.connectEpoch += 1
        ble.disconnect()
    }

    private fun publish(run: Run, running: Boolean) {
        if (run.token != sessionToken) {
            return
        }
        status.value = ScaleSessionStatus(
            running = running,
            stateLabel = measurementState(run.measurement),
            liveWeightText = run.listener.liveWeightText,
            encrypted = run.listener.encrypted,
            connectionTimedOut = run.connectionTimedOut,
            scanFailed = run.scanFailed,
            measurementId = run.measurement.storedId,
            heightCm = run.heightCm,
            broadcastHex = run.listener.broadcastHex,
            gattHex = run.listener.gattHex,
        )
    }

    private fun scanSink(run: Run): ScanSink = object : ScanSink {
        override fun onManufacturer(
            address: String,
            companyId: Int,
            remainder: ByteArray,
            connectable: Boolean,
        ): Boolean {
            if (run.token != sessionToken) {
                return false
            }
            val payload = try {
                transmissions.payloadFromManufacturer(companyId, remainder)
            } catch (error: IllegalArgumentException) {
                null
            } ?: return false
            run.events.trySend(Advertisement(address, payload, connectable))
            return true
        }

        override fun onRecord(address: String, record: ByteArray, connectable: Boolean) {
            if (run.token != sessionToken) {
                return
            }
            val payload = transmissions.scalePayload(record) ?: return
            run.events.trySend(Advertisement(address, payload, connectable))
        }

        override fun onScanFailed() {
            run.events.trySend(ScanFailed)
        }
    }

    private fun connectSink(run: Run, epoch: Int): ConnectSink = object : ConnectSink {
        override fun onNotification(value: ByteArray) {
            run.events.trySend(Notification(epoch, value))
        }

        override fun onNotifyReady() {
            run.events.trySend(NotifyReady(epoch))
        }

        override fun onLinkLost() {
            run.events.trySend(LinkLost(epoch))
        }

        override fun onNotifyMissing() {
            run.events.trySend(NotifyMissing(epoch))
        }

        override fun onFailed() {
            run.events.trySend(ConnectFailed(epoch))
        }
    }

    private class Run(
        val token: Int,
        val measurement: MeasurementSession,
        val listener: SessionListener,
        val heightCm: Double,
        val events: Channel<BleEvent> = Channel(Channel.UNLIMITED),
        var round: Int = 0,
        var attempt: Int = 0,
        var linkActive: Boolean = false,
        var phase: Phase = Phase.Scanning,
        var connectionTimedOut: Boolean = false,
        var scanFailed: Boolean = false,
        var connectDeadlineMs: Long? = null,
        var connectEpoch: Int = 0,
        var targetAddress: String = "",
    )

    private enum class Phase {
        Scanning,
        Connecting,
        Notified,
    }
}

private sealed interface BleEvent

private class Advertisement(
    val address: String,
    val payload: ByteArray,
    val connectable: Boolean,
) : BleEvent

private class Notification(
    val epoch: Int,
    val value: ByteArray,
) : BleEvent

private class NotifyReady(val epoch: Int) : BleEvent

private class LinkLost(val epoch: Int) : BleEvent

private class NotifyMissing(val epoch: Int) : BleEvent

private class ConnectFailed(val epoch: Int) : BleEvent

private data object ScanFailed : BleEvent
