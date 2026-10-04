package com.mascill.healthtrack.presentation.session

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.repository.BodyRepository
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
        val viewModel = SessionViewModel(FakeBodyRepository(latest = null), appDispatcher)
        advanceUntilIdle()

        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasProfile)
        assertFalse(state.isMeasuring)
        assertFalse(state.isFlowOpen)
        assertEquals("Simpan profil tubuh sebelum mengukur.", state.message)
    }

    @Test
    fun startWithProfileOpensFlowWithoutMeasuring() = runTest(testDispatcher) {
        val viewModel = SessionViewModel(
            FakeBodyRepository(latest = BodyData(heightCm = 166.0, age = 29, sex = 1)),
            appDispatcher,
        )
        advanceUntilIdle()

        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasProfile)
        assertFalse(state.isMeasuring)
        assertTrue(state.isFlowOpen)
        assertNull(state.message)
    }
}

private class FakeBodyRepository(
    private val latest: BodyData?,
) : BodyRepository {
    override fun save(body: BodyData): Int = 1

    override fun latest(): BodyData? = latest
}
