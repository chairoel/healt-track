package com.mascill.healthtrack.domain.model

/**
 * Screen values for one v235 report, already rounded half up.
 * Later screens print these with a decimal comma and do not round again.
 */
data class MeasurementDisplay(
    val bmi: Double,
    val bodyFatPercent: Double,
    val fatMassKg: Double,
    val skeletalMuscleRatio: Double,
    val skeletalMuscleKg: Double,
    val muscleRatio: Double,
    val muscleMassKg: Double,
    val waterPercent: Double,
    val waterMassKg: Double,
    val visceralFat: Double,
    val boneKg: Double,
    val metabolism: Double,
    val proteinPercent: Double,
    val obesityPercent: Double,
    val bodyAge: Double,
    val leanWeightKg: Double,
    val score: Int,
)
