package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementDisplay
import com.mascill.healthtrack.domain.model.V235Report
import java.math.BigDecimal
import java.math.RoundingMode

private const val ONE_DECIMAL = 1
private const val TWO_DECIMALS = 2
private const val CM_TO_M_FACTOR = 10000.0
private const val PERCENT = 100.0

/**
 * Builds the rounded screen rows for one v235 report.
 * Fat mass used for lean weight is the unrounded product.
 */
class DisplayMeasurementUseCase {
    /**
     * Rows for [report] at [weightKg] and [heightCm].
     */
    operator fun invoke(
        report: V235Report,
        weightKg: Double,
        heightCm: Double,
    ): MeasurementDisplay {
        val bmi = weightKg / (heightCm * heightCm) * CM_TO_M_FACTOR
        val fatMassKg = report.bfp * weightKg / PERCENT
        val waterMassKg = report.bwp * weightKg / PERCENT
        val obesityPercent = (weightKg - report.swt) / report.swt * PERCENT
        return MeasurementDisplay(
            bmi = roundHalfUp(bmi, ONE_DECIMAL),
            bodyFatPercent = roundHalfUp(report.bfp, ONE_DECIMAL),
            fatMassKg = roundHalfUp(fatMassKg, ONE_DECIMAL),
            skeletalMuscleRatio = roundHalfUp(report.smm / weightKg * PERCENT, ONE_DECIMAL),
            skeletalMuscleKg = roundHalfUp(report.smm, ONE_DECIMAL),
            muscleRatio = roundHalfUp(report.slp, ONE_DECIMAL),
            muscleMassKg = roundHalfUp(report.slm, ONE_DECIMAL),
            waterPercent = roundHalfUp(report.bwp, ONE_DECIMAL),
            waterMassKg = roundHalfUp(waterMassKg, ONE_DECIMAL),
            visceralFat = roundHalfUp(report.vfl, ONE_DECIMAL),
            boneKg = roundHalfUp(report.bmc, TWO_DECIMALS),
            metabolism = roundHalfUp(report.bmr.toDouble(), ONE_DECIMAL),
            proteinPercent = roundHalfUp(report.pp, ONE_DECIMAL),
            obesityPercent = roundHalfUp(obesityPercent, ONE_DECIMAL),
            bodyAge = roundHalfUp(report.ma.toDouble(), ONE_DECIMAL),
            leanWeightKg = roundHalfUp(weightKg - fatMassKg, TWO_DECIMALS),
            score = report.sbc,
        )
    }
}

private fun roundHalfUp(value: Double, places: Int): Double {
    return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).toDouble()
}
