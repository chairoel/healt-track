package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.local.MeasurementDao
import com.mascill.healthtrack.data.mapper.toEntity
import com.mascill.healthtrack.data.mapper.toStoredMeasurement
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.util.AppDispatcher
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Inserts each finished measurement and lists rows with the greatest id first.
 */
class MeasurementRepositoryImpl @Inject constructor(
    private val dao: MeasurementDao,
    private val dispatcher: AppDispatcher,
) : MeasurementRepository {
    override fun save(result: MeasurementResult): Int = runBlocking(dispatcher.io) {
        dao.insert(result.toEntity()).toInt()
    }

    override fun list(): List<StoredMeasurement> = runBlocking(dispatcher.io) {
        dao.listNewestFirst().map { it.toStoredMeasurement() }
    }
}
