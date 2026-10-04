package com.mascill.healthtrack.data.model

import kotlinx.serialization.Serializable

/**
 * JSON object stored in `measurement.report`.
 * Field names match the stored v235 report.
 */
@Serializable
internal data class StoredReport(
    val bfp: Double,
    val slm: Double,
    val slp: Double,
    val bwp: Double,
    val bmc: Double,
    val vfl: Double,
    val pp: Double,
    val smm: Double,
    val swt: Double,
    val mc: Double,
    val wc: Double,
    val fc: Double,
    val bmr: Int,
    val ma: Int,
    val sbc: Int,
)
