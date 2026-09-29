package com.mascill.healthtrack.domain.model

import java.time.Instant

sealed interface GattResult

data class GattConfirmation(
    val version: Int,
    val command: Int,
    val property: Int,
    val weightKg: Double,
    val scaleWeight: String,
    val matchedRoles: List<Int>,
) : GattResult

data class ScaleGatt(
    val version: Int,
    val command: Int?,
    val lockFlag: Int,
    val property: Int,
    val weightKg: Double,
    val scaleWeight: String,
    val history: Boolean,
    val axunge: Double,
    val water: Double,
    val muscle: Double,
    val bmr: Double,
    val visceralFat: Double,
    val oriVisceralFat: Double,
    val bone: Double,
    val roleId: Int = 0,
    val weighingDate: Instant? = null,
) : GattResult
