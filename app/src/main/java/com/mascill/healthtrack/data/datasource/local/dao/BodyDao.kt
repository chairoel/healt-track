package com.mascill.healthtrack.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mascill.healthtrack.data.model.entity.BodyEntity

/**
 * Reads and inserts body profiles.
 */
@Dao
interface BodyDao {
    /**
     * Inserts [entity] and returns the new row id.
     */
    @Insert
    fun insert(entity: BodyEntity): Long

    /**
     * The row with the greatest id, or null when the table is empty.
     */
    @Query("SELECT * FROM body ORDER BY id DESC LIMIT 1")
    fun latest(): BodyEntity?
}