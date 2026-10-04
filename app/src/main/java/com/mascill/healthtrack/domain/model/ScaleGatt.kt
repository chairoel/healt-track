package com.mascill.healthtrack.domain.model

import java.time.Instant

/**
 * One parsed GATT frame: a body measurement or a role confirmation.
 */
sealed interface GattResult {
    /**
     * Role ids the scale matched for this weighing.
     */
    data class Confirmation(
        val version: Int,
        val command: Int,
        val property: Int,
        val weightKg: Double,
        val scaleWeight: String,
        val matchedRoles: List<Int>,
    ) : GattResult

    /**
     * One GATT body measurement.
     * Composition fields are scale bytes, not the stored report.
     */
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
}
