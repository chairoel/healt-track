package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.MeasurementResult

/**
 * Stores one finished measurement.
 * Room implements this later. Until then, [com.mascill.healthtrack.data.di.RepositoryModule]
 * exposes this type as an empty optional. Tests in this phase use an in-memory stand-in.
 */
interface MeasurementRepository {
    /**
     * Persists [result] and returns the stored row id.
     */
    fun save(result: MeasurementResult): Int
}
