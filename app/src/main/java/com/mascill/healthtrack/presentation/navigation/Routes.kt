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
 * One measurement that just finished.
 * [heightCm] is the profile used for the indexes. Hex is empty when that frame did not arrive.
 * Opening an older row from history is a later phase.
 */
@Serializable
data class ResultRoute(
    val measurementId: Int,
    val heightCm: Double = 0.0,
    val broadcastHex: String = "",
    val gattHex: String = "",
) : NavKey
