package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.util.utcTimestamp
import com.mascill.healthtrack.data.datasource.local.entity.BodyEntity
import com.mascill.healthtrack.domain.model.BodyData

/**
 * Room row for a new [BodyData]. The id is assigned on insert.
 */
internal fun BodyData.toEntity(createdAt: String = utcTimestamp()): BodyEntity = BodyEntity(
    heightCm = heightCm,
    age = age,
    sex = sex,
    createdAt = createdAt,
)

/**
 * Profile stored in [this] row.
 */
internal fun BodyEntity.toBody(): BodyData = BodyData(
    heightCm = heightCm,
    age = age,
    sex = sex,
)
