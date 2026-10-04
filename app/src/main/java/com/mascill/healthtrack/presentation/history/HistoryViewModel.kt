package com.mascill.healthtrack.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.presentation.history.model.HistoryUIState
import com.mascill.healthtrack.util.AppDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lists stored measurements, newest first.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val measurementRepository: MeasurementRepository,
    private val dispatcher: AppDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUIState())
    val uiState: StateFlow<HistoryUIState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    /**
     * Reloads the measurement list.
     */
    fun loadHistory() {
        viewModelScope.launch(dispatcher.io) {
            _uiState.update { it.copy(isLoading = true) }
            val items = measurementRepository.list()
            _uiState.update { it.copy(isLoading = false, items = items) }
        }
    }
}
