package com.mascill.healthtrack.presentation.history

import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.model.V235Report
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.util.AppDispatcher
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
class HistoryViewModelTest {
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
    fun loadHistoryKeepsRepositoryOrder() = runTest(testDispatcher) {
        val rows = listOf(stored(id = 2), stored(id = 1))
        val viewModel = HistoryViewModel(
            measurementRepository = FixedMeasurementRepository(rows),
            dispatcher = appDispatcher,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(2, 1), state.items.map { it.id })
    }
}

private fun stored(id: Int): StoredMeasurement = StoredMeasurement(
    id = id,
    createdAt = "2026-10-04T00:00:00Z",
    result = MeasurementResult(
        mac = "AA:BB:CC:DD:EE:FF",
        productId = 1,
        weightKg = 68.7,
        scaleWeight = "68,7",
        impedanceOhm = listOf(600.0),
        formula = "v235",
        code = 0,
        report = V235Report(
            bfp = 23.1,
            slm = 50.0,
            slp = 72.8,
            bwp = 54.2,
            bmc = 2.77,
            vfl = 10.0,
            pp = 18.5,
            smm = 26.2,
            swt = 60.2,
            mc = 1.0,
            wc = 1.0,
            fc = 1.0,
            bmr = 1502,
            ma = 36,
            sbc = 61,
        ),
    ),
)

private class FixedMeasurementRepository(
    private val rows: List<StoredMeasurement>,
) : MeasurementRepository {
    override fun save(result: MeasurementResult): Int = 1

    override fun list(): List<StoredMeasurement> = rows
}
