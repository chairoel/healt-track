package com.mascill.healthtrack.domain.model

/**
 * One row from the measurement table.
 * [createdAt] is an ISO-8601 UTC timestamp.
 */
data class StoredMeasurement(
    val id: Int,
    val createdAt: String,
    val result: MeasurementResult,
)
