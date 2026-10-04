package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.ScaleAdvertisement
import com.mascill.healthtrack.domain.model.ScaleConnectFailed
import com.mascill.healthtrack.domain.model.ScaleLinkLost
import com.mascill.healthtrack.domain.model.ScaleNotification
import com.mascill.healthtrack.domain.model.ScaleNotifyMissing
import com.mascill.healthtrack.domain.model.ScaleNotifyReady
import com.mascill.healthtrack.domain.model.ScaleRadioEvent
import com.mascill.healthtrack.domain.model.ScaleScanFailed
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import com.mascill.healthtrack.domain.repository.ScaleGattRepository
import com.mascill.healthtrack.domain.repository.ScaleRadioRepository
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import com.mascill.healthtrack.util.ElapsedClock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_LIMIT_MS = 120_000L
private const val CONNECT_LIMIT_MS = 15_000L
private const val MAX_ATTEMPTS = 3
private const val MAX_ROUNDS = 2
private const val CLOSED_EPOCH = -1

/**
 * Runs one scale session on [radio] and applies frames through the accept use cases.
 * [radio] is stopped before `connectGatt`. Scanning and the connection do not run together.
 */
class MeasureScale(
    private val radio: ScaleRadioRepository,
    private val transmissions: ScaleTransmissionRepository,
    private val gattFrames: ScaleGattRepository,
    private val acceptTransmission: AcceptTransmissionUseCase,
    private val acceptGatt: AcceptGattUseCase,
    private val applyBroadcastWeight: ApplyBroadcastWeightUseCase,
    private val measurementState: MeasurementStateUseCase,
    private val clock: ElapsedClock,
) : MeasureScaleUseCase {
    override fun invoke(body: BodyData): Flow<ScaleSessionStatus> = flow {
        if (!radio.bluetoothEnabled()) {
            emit(ScaleSessionStatus(bluetoothOff = true, heightCm = body.heightCm))
            return@flow
        }
        gattFrames.reset()
        val session = MeasurementSession(body)
        val frames = ScaleFrames(
            session = session,
            transmissions = transmissions,
            gattFrames = gattFrames,
            acceptTransmission = acceptTransmission,
            acceptGatt = acceptGatt,
            applyBroadcastWeight = applyBroadcastWeight,
        )
        val run = Run(
            session = session,
            frames = frames,
            heightCm = body.heightCm,
        )
        try {
            radio.begin()
            emit(snapshot(run, running = true))
            runSession(run)
            emit(snapshot(run, running = false))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            run.scanFailed = true
            emit(snapshot(run, running = false))
        } finally {
            radio.end()
        }
    }

    private suspend fun FlowCollector<ScaleSessionStatus>.runSession(run: Run) {
        if (!radio.startScan()) {
            run.scanFailed = true
            return
        }
        val startedAt = clock.millis()
        while (coroutineContext.isActive) {
            coroutineContext.ensureActive()
            val now = clock.millis()
            val sessionDeadline = startedAt + SESSION_LIMIT_MS
            if (now >= sessionDeadline) {
                break
            }
            val connectDeadline = run.connectDeadlineMs
            if (connectDeadline != null && now >= connectDeadline) {
                onConnectClock(run)
                emit(snapshot(run, running = true))
                if (run.session.result != null) {
                    break
                }
                continue
            }
            val nextWake = connectDeadline?.let { minOf(it, sessionDeadline) } ?: sessionDeadline
            val waitMs = (nextWake - now).coerceAtLeast(1L)
            val event = withTimeoutOrNull(waitMs) { radio.awaitEvent() } ?: continue
            val finished = handle(run, event)
            emit(snapshot(run, running = true))
            if (finished) {
                break
            }
        }
    }

    private fun handle(run: Run, event: ScaleRadioEvent): Boolean {
        when (event) {
            is ScaleAdvertisement -> onAdvertisement(run, event)
            is ScaleNotification -> if (event.epoch == run.connectEpoch) {
                run.frames.onGatt(event.value)
            }
            is ScaleNotifyReady -> if (event.epoch == run.connectEpoch) {
                onReady(run)
            }
            is ScaleLinkLost -> if (event.epoch == run.connectEpoch) {
                failAttempt(run)
            }
            is ScaleNotifyMissing -> if (event.epoch == run.connectEpoch) {
                endRound(run)
            }
            is ScaleConnectFailed -> if (event.epoch == run.connectEpoch) {
                failAttempt(run)
            }
            ScaleScanFailed -> run.scanFailed = true
        }
        return run.session.result != null
    }

    private fun onAdvertisement(run: Run, event: ScaleAdvertisement) {
        run.frames.onAdvertisement(event.address, event.payload)
        run.frames.closeFromBroadcast(event.connectable, run.linkActive)
        if (run.session.result != null || run.linkActive || run.round >= MAX_ROUNDS) {
            return
        }
        if (!event.connectable || !run.frames.canConnect(event.address)) {
            return
        }
        beginConnect(run, event.address)
    }

    private fun beginConnect(run: Run, address: String) {
        run.targetAddress = address
        run.linkActive = true
        run.attempt = 1
        run.phase = Phase.Connecting
        run.connectDeadlineMs = clock.millis() + CONNECT_LIMIT_MS
        radio.stopScan()
        openGatt(run)
    }

    private fun openGatt(run: Run) {
        run.connectEpoch = radio.connect(run.targetAddress)
    }

    private fun onReady(run: Run) {
        run.phase = Phase.Notified
        run.connectDeadlineMs = null
        if (!radio.startScan()) {
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
        if (run.session.result != null) {
            return
        }
        if (run.attempt < MAX_ATTEMPTS) {
            run.attempt += 1
            run.phase = Phase.Connecting
            run.connectDeadlineMs = clock.millis() + CONNECT_LIMIT_MS
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
        if (run.round < MAX_ROUNDS && run.session.result == null) {
            if (!radio.startScan()) {
                run.scanFailed = true
            }
        }
    }

    private fun invalidate(run: Run) {
        run.connectEpoch = CLOSED_EPOCH
        radio.disconnect()
    }

    private fun snapshot(run: Run, running: Boolean): ScaleSessionStatus {
        return ScaleSessionStatus(
            running = running,
            stateLabel = measurementState(run.session),
            liveWeightText = run.frames.liveWeightText,
            encrypted = run.frames.encrypted,
            connectionTimedOut = run.connectionTimedOut,
            scanFailed = run.scanFailed,
            measurementId = run.session.storedId,
            heightCm = run.heightCm,
            broadcastHex = run.frames.broadcastHex,
            gattHex = run.frames.gattHex,
        )
    }

    private class Run(
        val session: MeasurementSession,
        val frames: ScaleFrames,
        val heightCm: Double,
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
