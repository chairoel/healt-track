package com.mascill.healthtrack.domain.model

import com.mascill.healthtrack.domain.usecase.DisplayMeasurementUseCase
import com.mascill.healthtrack.util.CsBiasV235
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementDisplayTest {
    private val displayMeasurement = DisplayMeasurementUseCase()

    @Test
    fun maleVectorMatchesTheScreenTable() {
        val report = CsBiasV235.parseDefaultZ(1, 29, 166.0, 68.7, 600.0).report!!
        assertEquals(23.07963752746582, report.bfp, 0.0)
        assertEquals(26.2417049407959, report.smm, 0.0)
        assertEquals(72.75165557861328, report.slp, 0.0)
        assertEquals(49.980384826660156, report.slm, 0.0)
        assertEquals(54.229583740234375, report.bwp, 0.0)
        assertEquals(2.7662100791931152, report.bmc, 0.0)
        assertEquals(18.53265953063965, report.pp, 0.0)
        assertEquals(60.20000076293945, report.swt, 0.0)
        assertEquals(10.0, report.vfl, 0.0)
        assertEquals(1502, report.bmr)
        assertEquals(36, report.ma)
        assertEquals(61, report.sbc)
        val display = displayMeasurement(report, weightKg = 68.7, heightCm = 166.0)
        assertEquals(24.9, display.bmi, 0.0)
        assertEquals(23.1, display.bodyFatPercent, 0.0)
        assertEquals(15.9, display.fatMassKg, 0.0)
        assertEquals(38.2, display.skeletalMuscleRatio, 0.0)
        assertEquals(26.2, display.skeletalMuscleKg, 0.0)
        assertEquals(72.8, display.muscleRatio, 0.0)
        assertEquals(50.0, display.muscleMassKg, 0.0)
        assertEquals(54.2, display.waterPercent, 0.0)
        assertEquals(37.3, display.waterMassKg, 0.0)
        assertEquals(10.0, display.visceralFat, 0.0)
        assertEquals(2.77, display.boneKg, 0.0)
        assertEquals(1502.0, display.metabolism, 0.0)
        assertEquals(18.5, display.proteinPercent, 0.0)
        assertEquals(14.1, display.obesityPercent, 0.0)
        assertEquals(36.0, display.bodyAge, 0.0)
        assertEquals(52.84, display.leanWeightKg, 0.0)
        assertEquals(61, display.score)
    }

    @Test
    fun femaleFatOnTheSameVectorIs348() {
        val report = CsBiasV235.parseDefaultZ(0, 29, 166.0, 68.7, 600.0).report!!
        assertEquals(34.7588005065918, report.bfp, 0.0)
        assertEquals(34.8, displayMeasurement(report, weightKg = 68.7, heightCm = 166.0).bodyFatPercent, 0.0)
    }
}
