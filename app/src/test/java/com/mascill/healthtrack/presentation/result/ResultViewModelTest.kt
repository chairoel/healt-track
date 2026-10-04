package com.mascill.healthtrack.presentation.result

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.usecase.DisplayMeasurementUseCase
import com.mascill.healthtrack.util.AppDispatcher
import com.mascill.healthtrack.util.CsBiasV235
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {
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
    fun maleVectorShowsRoundedIndexesAndHex() = runTest(testDispatcher) {
        val report = CsBiasV235.parseDefaultZ(1, 29, 166.0, 68.7, 600.0).report!!
        val stored = StoredMeasurement(
            id = 4,
            createdAt = "2026-10-04T00:00:00Z",
            result = MeasurementResult(
                mac = "AA:BB:CC:DD:EE:FF",
                productId = 1,
                weightKg = 68.7,
                scaleWeight = "68.7",
                impedanceOhm = listOf(600.0),
                formula = "v235",
                code = 0,
                report = report,
            ),
        )
        val viewModel = ResultViewModel(
            measurementRepository = FixedMeasurementRepository(listOf(stored)),
            bodyRepository = EmptyBodyRepository(),
            displayMeasurement = DisplayMeasurementUseCase(),
            dispatcher = appDispatcher,
        )

        viewModel.load(
            measurementId = 4,
            heightCm = 166.0,
            broadcastHex = "c007",
            gattHex = "ca11",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("68.7", state.weightText)
        assertEquals("2,77", state.rows.first { it.label == "Tulang (kg)" }.value)
        assertEquals("1502,0", state.rows.first { it.label == "Metabolisme" }.value)
        assertEquals("23,1", state.rows.first { it.label == "Lemak" }.value)
        assertEquals("c007", state.broadcastHex)
        assertEquals("ca11", state.gattHex)
    }

    @Test
    fun missingRowShowsError() = runTest(testDispatcher) {
        val viewModel = ResultViewModel(
            measurementRepository = FixedMeasurementRepository(emptyList()),
            bodyRepository = EmptyBodyRepository(),
            displayMeasurement = DisplayMeasurementUseCase(),
            dispatcher = appDispatcher,
        )

        viewModel.load(measurementId = 9, heightCm = 166.0, broadcastHex = "", gattHex = "")
        advanceUntilIdle()

        assertEquals("Hasil tidak ditemukan.", viewModel.uiState.value.error?.message)
    }
}

private class FixedMeasurementRepository(
    private val rows: List<StoredMeasurement>,
) : MeasurementRepository {
    override fun save(result: MeasurementResult): Int = 1

    override fun list(): List<StoredMeasurement> = rows
}

private class EmptyBodyRepository : BodyRepository {
    override fun save(body: BodyData): Int = 1

    override fun latest(): BodyData? = null
}
