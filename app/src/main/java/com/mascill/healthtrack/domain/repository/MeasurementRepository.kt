package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.StoredMeasurement

/**
 * Stores finished measurements in Room.
 * [list] returns the newest row first.
 */
interface MeasurementRepository {
    /**
     * Persists [result] and returns the stored row id.
     */
    suspend fun save(result: MeasurementResult): Int

    /**
     * Stored measurements, greatest id first.
     */
    suspend fun list(): List<StoredMeasurement>
}
