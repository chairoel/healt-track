package com.mascill.healthtrack.presentation.session

import com.mascill.healthtrack.data.di.DataModule
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.data.repository.gatt.GattParser
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.ScaleRadioEvent
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.repository.ScaleRadioRepository
import com.mascill.healthtrack.domain.usecase.AcceptGattUseCase
import com.mascill.healthtrack.domain.usecase.AcceptTransmissionUseCase
import com.mascill.healthtrack.domain.usecase.ApplyBroadcastWeightUseCase
import com.mascill.healthtrack.domain.usecase.CloseMeasurementUseCase
import com.mascill.healthtrack.domain.usecase.MeasureScaleUseCase
import com.mascill.healthtrack.domain.usecase.MeasurementStateUseCase
import com.mascill.healthtrack.presentation.session.model.FinishedSession
import com.mascill.healthtrack.util.AppDispatcher
import com.mascill.healthtrack.util.ElapsedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val appDispatcher = AppDispatcher(
        io = testDispatcher,
        main = testDispatcher,
        default = testDispatcher,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startWithoutProfileDoesNotMeasure() = runTest(testDispatcher) {
        val radio = FakeRadio()
        val viewModel = viewModel(latest = null, radio = radio)
        advanceUntilIdle()

        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasProfile)
        assertFalse(state.isMeasuring)
        assertFalse(state.isFlowOpen)
        assertEquals("Simpan profil tubuh sebelum mengukur.", state.error?.message)
        assertEquals(0, radio.begins)
    }

    @Test
    fun startWithProfileRequestsPermissionWithoutMeasuring() = runTest(testDispatcher) {
        val radio = FakeRadio()
        val viewModel = viewModel(latest = profile(), radio = radio)
        advanceUntilIdle()

        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasProfile)
        assertFalse(state.isMeasuring)
        assertTrue(state.isFlowOpen)
        assertNull(state.error)
        assertEquals(1, state.permissionRequestId)
        assertEquals(0, radio.begins)
    }

    @Test
    fun grantedPermissionStartsMeasuring() = runTest(testDispatcher) {
        val radio = FakeRadio()
        val viewModel = viewModel(latest = profile(), radio = radio)
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isMeasuring)
        assertEquals("menunggu impedansi", state.stateLabel)
        assertEquals(1, radio.begins)
        viewModel.onHostStop()
    }

    @Test
    fun bluetoothOffShowsMessage() = runTest(testDispatcher) {
        val viewModel = viewModel(latest = profile(), radio = FakeRadio(enabled = false))
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMeasuring)
        assertEquals("Nyalakan Bluetooth untuk mengukur.", viewModel.uiState.value.error?.message)
    }

    @Test
    fun connectionTimeoutShowsOkokMessage() = runTest(testDispatcher) {
        val radio = FakeRadio()
        radio.send(ScaleRadioEvent.Advertisement("AA", lockedBroadcast().toList(), connectable = true))
        val viewModel = viewModel(latest = profile(), radio = radio)
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        advanceUntilIdle()

        assertEquals(
            "Waktu sambungan habis. Tutup aplikasi OKOK di ponsel bila timbangan sedang tersambung di sana.",
            viewModel.uiState.value.error?.message,
        )
    }

    @Test
    fun storedResultOpensTheResultScreen() = runTest(testDispatcher) {
        val radio = FakeRadio()
        radio.send(ScaleRadioEvent.Advertisement("AA", lockedBroadcast().toList(), connectable = false))
        val viewModel = viewModel(latest = profile(), radio = radio)
        val opened = mutableListOf<FinishedSession>()
        val collectJob = launch { viewModel.openResult.collect { opened += it } }
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        advanceUntilIdle()

        val finished = opened.single()
        assertEquals(1, finished.measurementId)
        assertEquals(166.0, finished.heightCm, 0.0)
        assertTrue(finished.broadcastHex.isNotEmpty())
        assertEquals("", finished.gattHex)
        collectJob.cancel()
    }

    private fun TestScope.viewModel(
        latest: BodyData?,
        radio: FakeRadio,
    ): SessionViewModel {
        return SessionViewModel(
            bodyRepository = FakeBodyRepository(latest),
            measure = measureScale(radio),
            dispatcher = appDispatcher,
        )
    }

    private fun TestScope.measureScale(radio: FakeRadio): MeasureScaleUseCase {
        val close = CloseMeasurementUseCase(repository = OneShotMeasurementRepository())
        return MeasureScaleUseCase(
            radio = radio,
            transmissions = ScaleTransmissionRepositoryImpl(DataModule.scaleHeaders()),
            gattFrames = ScaleGattRepositoryImpl(GattParser()),
            acceptTransmission = AcceptTransmissionUseCase(close),
            acceptGatt = AcceptGattUseCase(close),
            applyBroadcastWeight = ApplyBroadcastWeightUseCase(close),
            measurementState = MeasurementStateUseCase(),
            clock = ElapsedClock { testScheduler.currentTime },
            dispatcher = appDispatcher,
        )
    }

    private fun profile(): BodyData = BodyData(heightCm = 166.0, age = 29, sex = 1)
}

private class FakeRadio(
    private val enabled: Boolean = true,
) : ScaleRadioRepository {
    var begins = 0
    private val incoming = Channel<ScaleRadioEvent>(Channel.UNLIMITED)

    override fun bluetoothEnabled(): Boolean = enabled

    override fun begin() {
        begins += 1
    }

    override fun startScan(): Boolean = true

    override fun stopScan() = Unit

    override fun connect(address: String): Int = 1

    override fun disconnect() = Unit

    override val events: Flow<ScaleRadioEvent>
        get() = incoming.receiveAsFlow()

    override fun end() {
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

private class FakeBodyRepository(
    private val latest: BodyData?,
) : BodyRepository {
    override suspend fun save(body: BodyData): Int = 1

    override suspend fun latest(): BodyData? = latest
}

private fun lockedBroadcast(): ByteArray {
    return byteArrayOf(0xC0.toByte(), 7, 0x02, 0xC1.toByte(), 0x13, 0x88.toByte(), 0x00, 0x01, 1)
}
