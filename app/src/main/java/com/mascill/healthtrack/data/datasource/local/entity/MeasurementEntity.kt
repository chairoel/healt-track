package com.mascill.healthtrack.data.datasource.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One finished measurement.
 * [impedanceOhm] and [report] are JSON text.
 */
@Entity(tableName = "measurement")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    val mac: String,
    @ColumnInfo(name = "product_id")
    val productId: Int,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,
    @ColumnInfo(name = "scale_weight")
    val scaleWeight: String,
    @ColumnInfo(name = "impedance_ohm")
    val impedanceOhm: String,
    val formula: String,
    val code: Int,
    val report: String,
    @ColumnInfo(name = "height_cm")
    val heightCm: Double? = null,
    val age: Int? = null,
)
