package com.mascill.healthtrack.presentation.history.model

import com.mascill.healthtrack.domain.model.StoredMeasurement

/**
 * Measurement list. Empty until a row is stored.
 */
data class HistoryUIState(
    val isLoading: Boolean = true,
    val items: List<StoredMeasurement> = emptyList(),
)
