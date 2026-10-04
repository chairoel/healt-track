package com.mascill.healthtrack.presentation.session.model

import com.mascill.healthtrack.presentation.common.model.AppError

/**
 * Session tab. [isMeasuring] is true only while a profile exists and Bluetooth is running.
 */
data class SessionUIState(
    val hasProfile: Boolean = false,
    val isMeasuring: Boolean = false,
    val isFlowOpen: Boolean = false,
    val permissionRequestId: Int = 0,
    val stateLabel: String? = null,
    val liveWeightText: String? = null,
    val error: AppError? = null,
)
