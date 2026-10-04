package com.mascill.healthtrack.presentation.session.model

/**
 * A measurement that just finished and should open the result screen.
 */
data class FinishedSession(
    val measurementId: Int,
    val heightCm: Double,
    val broadcastHex: String,
    val gattHex: String,
)
