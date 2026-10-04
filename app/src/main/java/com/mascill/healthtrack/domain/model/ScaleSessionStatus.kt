package com.mascill.healthtrack.domain.model

/**
 * Snapshot of one Bluetooth measurement.
 * [stateLabel] comes from [com.mascill.healthtrack.domain.usecase.MeasurementStateUseCase].
 * [measurementId] is set when the report has been stored.
 */
data class ScaleSessionStatus(
    val running: Boolean = false,
    val stateLabel: String = "",
    val liveWeightText: String? = null,
    val encrypted: Boolean = false,
    val connectionTimedOut: Boolean = false,
    val scanFailed: Boolean = false,
    val bluetoothOff: Boolean = false,
    val measurementId: Int? = null,
    val heightCm: Double = 0.0,
    val broadcastHex: String = "",
    val gattHex: String = "",
)
