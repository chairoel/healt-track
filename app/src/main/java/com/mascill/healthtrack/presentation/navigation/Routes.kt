package com.mascill.healthtrack.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Body profile tab.
 */
@Serializable
data object ProfileRoute : NavKey

/**
 * Measurement session tab. Bluetooth is attached in a later phase.
 */
@Serializable
data object SessionRoute : NavKey

/**
 * Stored measurement list.
 */
@Serializable
data object HistoryRoute : NavKey

/**
 * One stored or just-finished measurement.
 * The screen content is completed when results are shown.
 */
@Serializable
data class ResultRoute(val measurementId: Int) : NavKey
