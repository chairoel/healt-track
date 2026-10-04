package com.mascill.healthtrack.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mascill.healthtrack.data.datasource.local.entity.MeasurementEntity

/**
 * Reads and inserts finished measurements.
 */
@Dao
interface MeasurementDao {
    /**
     * Inserts [entity] and returns the new row id.
     */
    @Insert
    suspend fun insert(entity: MeasurementEntity): Long

    /**
     * All rows, greatest id first.
     */
    @Query("SELECT * FROM measurement ORDER BY id DESC")
    suspend fun listNewestFirst(): List<MeasurementEntity>
}