package com.mascill.healthtrack.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Body profile tab.
 */
@Serializable
data object ProfileRoute : NavKey

/**
 * Measurement session tab.
 */
@Serializable
data object SessionRoute : NavKey

/**
 * Stored measurement list.
 */
@Serializable
data object HistoryRoute : NavKey

/**
 * One stored measurement.
 * A session that just finished fills [heightCm], [broadcastHex], and [gattHex].
 * History passes [measurementId] only. The result screen then reads the stored report.
 * Opening this route does not start Bluetooth.
 */
@Serializable
data class ResultRoute(
    val measurementId: Int,
    val heightCm: Double = 0.0,
    val broadcastHex: String = "",
    val gattHex: String = "",
) : NavKey
