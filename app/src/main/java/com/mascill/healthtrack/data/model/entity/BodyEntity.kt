package com.mascill.healthtrack.data.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved body profile. A new save inserts another row.
 */
@Entity(tableName = "body")
data class BodyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "height_cm")
    val heightCm: Double,
    @ColumnInfo(name = "age")
    val age: Int,
    @ColumnInfo(name = "sex")
    val sex: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
)
