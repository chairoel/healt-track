package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.model.RawGattConfirmation
import com.mascill.healthtrack.data.model.RawGattMeasurement
import com.mascill.healthtrack.data.util.weightKilograms
import com.mascill.healthtrack.domain.model.GattConfirmation
import com.mascill.healthtrack.domain.model.ScaleGatt
import java.time.Instant

/**
 * Mapping [RawGattMeasurement] data model to [ScaleGatt] domain model.
 */
fun RawGattMeasurement?.toScaleGatt(): ScaleGatt {
    val raw = this
    val (weightKg, scaleWeight) = raw.toGattWeight()
    val composition = if (raw != null && raw.lockFlag > 0) raw.toComposition(weightKg) else null
    return ScaleGatt(
        version = raw?.version ?: 0,
        command = raw?.command,
        lockFlag = raw?.lockFlag ?: 0,
        property = raw?.property?.and(0xFF) ?: 0,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        history = raw?.history ?: false,
        axunge = composition?.axunge ?: 0.0,
        water = composition?.water ?: 0.0,
        muscle = composition?.muscle ?: 0.0,
        bmr = composition?.bmr ?: 0.0,
        visceralFat = composition?.visceral ?: 0.0,
        oriVisceralFat = composition?.ori ?: 0.0,
        bone = composition?.bone ?: 0.0,
        roleId = raw?.roleId ?: 0,
        weighingDate = raw?.weighingDateEpoch?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
    )
}

/**
 * Mapping [RawGattConfirmation] data model to [GattConfirmation] domain model.
 */
fun RawGattConfirmation?.toGattConfirmation(): GattConfirmation {
    val (weightKg, scaleWeight) = this.toGattWeight()
    return GattConfirmation(
        version = this?.version ?: 0,
        command = this?.command ?: 0,
        property = this?.property?.and(0xFF) ?: 0,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        matchedRoles = this?.matchedRoles.orEmpty(),
    )
}

private fun RawGattMeasurement?.toGattWeight(): Pair<Double, String> {
    val raw = this ?: return 0.0 to ""
    val (kg, scaleWeight) = weightKilograms(raw.weightHigh, raw.weightLow, raw.property, cloud = false)
    return storedWeight(kg) to scaleWeight
}

private fun RawGattConfirmation?.toGattWeight(): Pair<Double, String> {
    val raw = this ?: return 0.0 to ""
    val (kg, scaleWeight) = weightKilograms(raw.weightHigh, raw.weightLow, raw.property, cloud = false)
    return storedWeight(kg) to scaleWeight
}

private fun storedWeight(kg: Double): Double = (kg * 10.0) / 10.0

private fun RawGattMeasurement.toComposition(weightKg: Double): Composition {
    val (visceral, ori) = setVisceral(visceralRaw)
    val baseMuscle = setMuscle(muscleRaw)
    val muscle = if (command == 18 || command == 19) {
        recalculateMuscle(baseMuscle, weightKg)
    } else {
        baseMuscle
    }
    return Composition(
        axunge = setAxunge(axungeRaw),
        water = setWater(waterRaw),
        muscle = muscle,
        bmr = setBmr(bmrRaw),
        visceral = visceral,
        ori = ori,
        bone = setBone(boneRaw),
    )
}

private fun setAxunge(raw: Int): Double {
    if (raw >= 65535) return 0.0
    val value = raw / 10.0
    return if (value in 5.0..45.0) value else 0.0
}

private fun setWater(raw: Int): Double {
    val value = raw / 10.0
    return if (value >= 100.0 || value < 20.0 || value > 85.0) 0.0 else value
}

private fun setMuscle(raw: Int): Double {
    val value = raw / 10.0
    return if (value >= 100.0) 0.0 else value
}

private fun setBmr(raw: Int): Double {
    if (raw >= 65535) return 0.0
    return if (raw >= 5000) raw / 10.0 else raw.toDouble()
}

private fun setVisceral(raw: Int): Pair<Double, Double> {
    var shown = raw / 10.0
    if (shown >= 100.0 || shown > 59.0) {
        shown = 0.0
    }
    return shown to raw.toDouble()
}

private fun setBone(raw: Int): Double {
    if (raw >= 65535) return 0.0
    val value = raw / 10.0
    return when {
        value < 1.0 -> 0.0
        value <= 4.0 -> value
        else -> 4.0
    }
}

private fun recalculateMuscle(muscle: Double, weightKg: Double): Double {
    val ratio = when {
        weightKg == 0.0 && muscle == 0.0 -> Double.NaN
        weightKg == 0.0 -> if (muscle > 0.0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        else -> (muscle / weightKg) * 100.0
    }
    val value = if (ratio >= 50.0) {
        if (muscle >= 50.0) 50.0 else muscle
    } else {
        ratio
    }
    val raw = when {
        value.isNaN() -> 0
        value.isInfinite() -> throw ArithmeticException("int overflow")
        else -> (value * 10.0).toInt()
    }
    return setMuscle(raw)
}

private data class Composition(
    val axunge: Double,
    val water: Double,
    val muscle: Double,
    val bmr: Double,
    val visceral: Double,
    val ori: Double,
    val bone: Double,
)
