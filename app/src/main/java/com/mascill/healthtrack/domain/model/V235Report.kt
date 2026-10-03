package com.mascill.healthtrack.domain.model

/**
 * Stored single-frequency report. Visceral fat is [vfl].
 */
data class V235Report(
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

/**
 * Return code and optional [report] after the stored-report narrowing.
 */
data class V235ReportResult(
    val code: Int,
    val report: V235Report?,
)
