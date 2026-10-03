package com.mascill.healthtrack.domain.model

/**
 * Library-level fields from the v235 formula, before the stored report narrows them.
 */
data class BiasV235Data(
    val bfp: Double,
    val slm: Double,
    val bwp: Double,
    val bmc: Double,
    val vfr: Double,
    val pp: Double,
    val smm: Double,
    val bmi: Double,
    val sbw: Double,
    val mc: Double,
    val wc: Double,
    val fc: Double,
    val bmr: Double,
    val ma: Int,
    val sbc: Int,
)

/**
 * Return code and optional [data] from the v235 library call.
 * A non-zero [code] leaves [data] empty.
 */
data class BiasV235Result(
    val code: Int,
    val data: BiasV235Data?,
)
