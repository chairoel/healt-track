package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.data.di.DataModule
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.data.repository.gatt.GattParser
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.ScaleRadioEvent
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.repository.ScaleRadioRepository
import com.mascill.healthtrack.util.AppDispatcher
import com.mascill.healthtrack.util.ElapsedClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasureScaleUseCaseTest {
    private val body = BodyData(170.0, 30, 1)
    private val testDispatcher = StandardTestDispatcher()
    private val appDispatcher = AppDispatcher(
        io = testDispatcher,
        main = testDispatcher,
        default = testDispatcher,
    )

    @Test
    fun bluetoothOffEmitsStatusWithoutRadio() = runTest(testDispatcher) {
        val radio = FakeRadio(enabled = false)
        val statuses = measure(radio, ElapsedClock { 0L }).invoke(body).toList()
        assertEquals(0, radio.begins)
        assertEquals(0, radio.ends)
    }

    @Test
    fun nonConnectableBroadcastStoresWeight() = runTest(testDispatcher) {
        val radio = FakeRadio()
        radio.send(ScaleRadioEvent.Advertisement("AA", broadcast().toList(), connectable = false))
        val statuses = measure(radio, ElapsedClock { testScheduler.currentTime }).invoke(body).toList()
        val finished = statuses.last()
        assertEquals(1, finished.measurementId)
        assertTrue(finished.broadcastHex.isNotEmpty())
        assertEquals("", finished.gattHex)
        assertNull(radio.connected)
        assertEquals(1, radio.ends)
    }

    private fun measure(radio: FakeRadio, clock: ElapsedClock): MeasureScaleUseCase {
        val close = CloseMeasurementUseCase(repository = OneShotMeasurementRepository())
        val transmissions = ScaleTransmissionRepositoryImpl(DataModule.scaleHeaders())
        val gatt = ScaleGattRepositoryImpl(GattParser())
        return MeasureScaleUseCase(
            radio = radio,
            transmissions = transmissions,
            gattFrames = gatt,
            acceptTransmission = AcceptTransmissionUseCase(close),
            acceptGatt = AcceptGattUseCase(close),
            applyBroadcastWeight = ApplyBroadcastWeightUseCase(close),
            measurementState = MeasurementStateUseCase(),
            clock = clock,
            dispatcher = appDispatcher,
        )
    }
}

private class FakeRadio(
    private val enabled: Boolean = true,
) : ScaleRadioRepository {
    var begins = 0
    var ends = 0
    var connected: String? = null
    private val incoming = Channel<ScaleRadioEvent>(Channel.UNLIMITED)

    override fun bluetoothEnabled(): Boolean = enabled

    override fun begin() {
        begins += 1
    }

    override fun startScan(): Boolean = true

    override fun stopScan() = Unit

    override fun connect(address: String): Int {
        connected = address
        return 1
    }

    override fun disconnect() = Unit

    override val events: Flow<ScaleRadioEvent>
        get() = incoming.receiveAsFlow()

    override fun end() {
        ends += 1
        incoming.close()
    }

    fun send(event: ScaleRadioEvent) {
        incoming.trySend(event)
    }
}

private class OneShotMeasurementRepository : MeasurementRepository {
    override suspend fun save(result: MeasurementResult): Int = 1

    override suspend fun list(): List<StoredMeasurement> = emptyList()
}

private fun broadcast(): ByteArray {
    return byteArrayOf(0xC0.toByte(), 7, 0x02, 0xC1.toByte(), 0x13, 0x88.toByte(), 0x00, 0x01, 1)
}
