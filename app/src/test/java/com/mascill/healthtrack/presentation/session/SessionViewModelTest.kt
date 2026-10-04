package com.mascill.healthtrack.presentation.session

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.domain.usecase.MeasureScaleUseCase
import com.mascill.healthtrack.presentation.session.model.FinishedSession
import com.mascill.healthtrack.util.AppDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        val viewModel = viewModel(latest = null)
        advanceUntilIdle()

        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasProfile)
        assertFalse(state.isMeasuring)
        assertFalse(state.isFlowOpen)
        assertEquals("Simpan profil tubuh sebelum mengukur.", state.error?.message)
    }

    @Test
    fun startWithProfileRequestsPermissionWithoutMeasuring() = runTest(testDispatcher) {
        val measure = FakeMeasureScale()
        val viewModel = viewModel(latest = profile(), measure = measure)
        advanceUntilIdle()

        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasProfile)
        assertFalse(state.isMeasuring)
        assertTrue(state.isFlowOpen)
        assertNull(state.error)
        assertEquals(1, state.permissionRequestId)
        assertNull(measure.started)
    }

    @Test
    fun grantedPermissionStartsMeasuring() = runTest(testDispatcher) {
        val measure = FakeMeasureScale(
            status = ScaleSessionStatus(running = true, stateLabel = "menunggu impedansi"),
        )
        val viewModel = viewModel(latest = profile(), measure = measure)
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isMeasuring)
        assertEquals("menunggu impedansi", state.stateLabel)
        assertEquals(profile(), measure.started)
        viewModel.onHostStop()
    }

    @Test
    fun bluetoothOffShowsMessage() = runTest(testDispatcher) {
        val measure = FakeMeasureScale(bluetoothOn = false)
        val viewModel = viewModel(latest = profile(), measure = measure)
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
        val measure = FakeMeasureScale(
            status = ScaleSessionStatus(
                running = true,
                stateLabel = "menunggu berat",
                connectionTimedOut = true,
            ),
        )
        val viewModel = viewModel(latest = profile(), measure = measure)
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        advanceUntilIdle()

        assertEquals(
            "Waktu sambungan habis. Tutup aplikasi OKOK di ponsel bila timbangan sedang tersambung di sana.",
            viewModel.uiState.value.error?.message,
        )
        viewModel.onHostStop()
    }

    @Test
    fun storedResultOpensTheResultScreen() = runTest(testDispatcher) {
        val measure = FakeMeasureScale(
            status = ScaleSessionStatus(
                running = true,
                stateLabel = "selesai",
                measurementId = 7,
                heightCm = 166.0,
                broadcastHex = "c0",
                gattHex = "ca",
            ),
        )
        val viewModel = viewModel(latest = profile(), measure = measure)
        val opened = mutableListOf<FinishedSession>()
        val collectJob = launch { viewModel.openResult.collect { opened += it } }
        advanceUntilIdle()
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onPermissionsGranted()
        advanceUntilIdle()

        assertEquals(
            FinishedSession(
                measurementId = 7,
                heightCm = 166.0,
                broadcastHex = "c0",
                gattHex = "ca",
            ),
            opened.single(),
        )
        collectJob.cancel()
        viewModel.onHostStop()
    }

    private fun viewModel(
        latest: BodyData?,
        measure: MeasureScaleUseCase = FakeMeasureScale(),
    ): SessionViewModel {
        return SessionViewModel(
            bodyRepository = FakeBodyRepository(latest),
            measure = measure,
            dispatcher = appDispatcher,
        )
    }

    private fun profile(): BodyData = BodyData(heightCm = 166.0, age = 29, sex = 1)
}

private class FakeMeasureScale(
    var bluetoothOn: Boolean = true,
    var status: ScaleSessionStatus = ScaleSessionStatus(),
) : MeasureScaleUseCase {
    var started: BodyData? = null

    override fun invoke(body: BodyData): Flow<ScaleSessionStatus> = flow {
        started = body
        if (!bluetoothOn) {
            emit(ScaleSessionStatus(bluetoothOff = true, heightCm = body.heightCm))
            return@flow
        }
        emit(status)
    }
}

private class FakeBodyRepository(
    private val latest: BodyData?,
) : BodyRepository {
    override suspend fun save(body: BodyData): Int = 1

    override suspend fun latest(): BodyData? = latest
}
