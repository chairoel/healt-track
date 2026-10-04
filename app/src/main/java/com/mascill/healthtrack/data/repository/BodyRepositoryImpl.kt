package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.local.dao.BodyDao
import com.mascill.healthtrack.data.mapper.toBody
import com.mascill.healthtrack.data.mapper.toEntity
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.repository.BodyRepository
import com.mascill.healthtrack.util.AppDispatcher
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Inserts each profile as a new `body` row and reads the greatest id.
 */
class BodyRepositoryImpl @Inject constructor(
    private val dao: BodyDao,
    private val dispatcher: AppDispatcher,
) : BodyRepository {
    override fun save(body: BodyData): Int = runBlocking(dispatcher.io) {
        dao.insert(body.toEntity()).toInt()
    }

    override fun latest(): BodyData? = runBlocking(dispatcher.io) {
        dao.latest()?.toBody()
    }
}
