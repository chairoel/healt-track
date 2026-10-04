package com.mascill.healthtrack.presentation.session.model

/**
 * Session tab before Bluetooth is connected.
 * [isMeasuring] stays false until a profile exists and a later phase starts the scale.
 */
data class SessionUIState(
    val hasProfile: Boolean = false,
    val isMeasuring: Boolean = false,
    val isFlowOpen: Boolean = false,
    val message: String? = null,
)
