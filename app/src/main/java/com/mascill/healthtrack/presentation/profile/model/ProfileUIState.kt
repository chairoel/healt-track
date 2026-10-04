package com.mascill.healthtrack.presentation.profile.model

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.presentation.common.model.AppError

/**
 * Profile form and the newest saved row.
 */
data class ProfileUIState(
    val heightCm: String = "",
    val age: String = "",
    val sex: Int? = null,
    val saved: BodyData? = null,
    val isSaving: Boolean = false,
    val error: AppError? = null,
)
