package com.mascill.healthtrack.data.datasource.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mascill.healthtrack.data.datasource.local.dao.MeasurementDao
import com.mascill.healthtrack.data.datasource.local.dao.BodyDao
import com.mascill.healthtrack.data.model.entity.BodyEntity
import com.mascill.healthtrack.data.model.entity.MeasurementEntity

/**
 * Local tables for body profiles and finished measurements.
 */
@Database(
    entities = [BodyEntity::class, MeasurementEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HealthTrackDatabase : RoomDatabase() {
    /**
     * Body profile table.
     */
    abstract fun bodyDao(): BodyDao

    /**
     * Finished measurement table.
     */
    abstract fun measurementDao(): MeasurementDao
}