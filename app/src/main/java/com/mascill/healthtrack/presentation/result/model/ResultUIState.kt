package com.mascill.healthtrack.presentation.result.model

import com.mascill.healthtrack.presentation.common.model.AppError

/**
 * One finished measurement, already rounded for the screen.
 */
data class ResultUIState(
    val isLoading: Boolean = true,
    val weightText: String? = null,
    val rows: List<ResultRow> = emptyList(),
    val broadcastHex: String = "",
    val gattHex: String = "",
    val error: AppError? = null,
)

/**
 * One index row. [value] is already formatted.
 */
data class ResultRow(
    val label: String,
    val value: String,
)
