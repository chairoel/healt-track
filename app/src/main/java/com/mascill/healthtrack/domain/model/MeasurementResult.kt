package com.mascill.healthtrack.domain.model

/**
 * One finished measurement. [report] comes from the formula, not from GATT composition bytes.
 */
data class MeasurementResult(
    val mac: String,
    val productId: Int,
    val weightKg: Double,
    val scaleWeight: String,
    val impedanceOhm: List<Double>,
    val formula: String,
    val code: Int,
    val report: V235Report,
)

/**
 * Formula name, return code, and report for one weight and its impedance values.
 * A failed formula keeps [code] and leaves [report] empty.
 */
data class FormulaOutcome(
    val formula: String,
    val code: Int,
    val report: V235Report?,
)
