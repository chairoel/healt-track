package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.model.RawBody
import com.mascill.healthtrack.data.model.RawGattConfirmation
import com.mascill.healthtrack.data.model.RawGattMeasurement
import com.mascill.healthtrack.data.util.GIdx
import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.data.util.RIdx
import com.mascill.healthtrack.domain.model.GattResult.Confirmation
import com.mascill.healthtrack.domain.model.GattResult.ScaleGatt
import java.time.Instant

/**
 * Maps a GATT measurement to [ScaleGatt].
 * Composition clamps run only when [RawGattMeasurement.body] is present.
 * Muscle is recalculated for [GattConst.CMD_LIVE] and [GattConst.CMD_HISTORY].
 */
fun RawGattMeasurement.toScaleGatt(): ScaleGatt {
    val (kg, scaleWeight) = weightKilograms(weightHigh, weightLow, property, cloud = false)
    val weightKg = storedWeight(kg)
    val recalculate = command == GattConst.CMD_LIVE || command == GattConst.CMD_HISTORY
    val values = body?.toComposition(weightKg, recalculate) ?: Composition()
    return ScaleGatt(
        version = version,
        command = command,
        lockFlag = lockFlag,
        property = property and GattConst.LOW_BYTE,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        history = history,
        axunge = values.axunge,
        water = values.water,
        muscle = values.muscle,
        bmr = values.bmr,
        visceralFat = values.visceral,
        oriVisceralFat = values.ori,
        bone = values.bone,
        roleId = roleId,
        weighingDate = weighingDateEpoch?.let(Instant::ofEpochSecond),
    )
}

/**
 * Maps a GATT role match to [Confirmation].
 */
fun RawGattConfirmation.toGattConfirmation(): Confirmation {
    val (kg, scaleWeight) = weightKilograms(weightHigh, weightLow, property, cloud = false)
    return Confirmation(
        version = version,
        command = command,
        property = property and GattConst.LOW_BYTE,
        weightKg = storedWeight(kg),
        scaleWeight = scaleWeight,
        matchedRoles = matchedRoles,
    )
}

/**
 * Maps a successful native GATT measurement array to [ScaleGatt].
 * The scale text is built in Kotlin. The caller rejects a native error code before this mapping.
 */
internal fun DoubleArray.toScaleGatt(): ScaleGatt {
    val wHigh = this[GIdx.W_HIGH].toInt()
    val wLow = this[GIdx.W_LOW].toInt()
    val prop = this[GIdx.PROPERTY].toInt()
    val (_, scaleWeight) = weightKilograms(wHigh, wLow, prop, cloud = false)
    return ScaleGatt(
        version = this[GIdx.VERSION].toInt(),
        command = if (this[GIdx.HAS_CMD].toInt() != GattConst.FLAG_OFF) this[GIdx.COMMAND].toInt() else null,
        lockFlag = this[GIdx.LOCK_FLAG].toInt(),
        property = this[GIdx.PROPERTY].toInt(),
        weightKg = this[GIdx.WEIGHT_KG],
        scaleWeight = scaleWeight,
        history = this[GIdx.HISTORY].toInt() != GattConst.FLAG_OFF,
        axunge = this[GIdx.AXUNGE],
        water = this[GIdx.WATER],
        muscle = this[GIdx.MUSCLE],
        bmr = this[GIdx.BMR],
        visceralFat = this[GIdx.VISCERAL],
        oriVisceralFat = this[GIdx.ORI_VISCERAL],
        bone = this[GIdx.BONE],
        roleId = this[GIdx.ROLE_ID].toInt(),
        weighingDate = if (this[GIdx.HAS_DATE].toInt() != GattConst.FLAG_OFF) {
            Instant.ofEpochSecond(this[GIdx.DATE_EPOCH].toLong())
        } else {
            null
        },
    )
}

/**
 * Maps a successful native role-match array to [Confirmation].
 * Role ids start at [RIdx.ROLES_START] and run for [RIdx.ROLE_COUNT] entries.
 * The caller rejects a native error code before this mapping.
 */
internal fun DoubleArray.toGattConfirmation(): Confirmation {
    val wHigh = this[RIdx.W_HIGH].toInt()
    val wLow = this[RIdx.W_LOW].toInt()
    val prop = this[RIdx.PROPERTY].toInt()
    val (_, scaleWeight) = weightKilograms(wHigh, wLow, prop, cloud = false)
    val roleCount = this[RIdx.ROLE_COUNT].toInt()
    val roles = ArrayList<Int>(roleCount)
    repeat(roleCount) { i ->
        roles.add(this[RIdx.ROLES_START + i].toInt())
    }
    return Confirmation(
        version = this[RIdx.VERSION].toInt(),
        command = this[RIdx.COMMAND].toInt(),
        property = prop and GattConst.LOW_BYTE,
        weightKg = storedWeight(this[RIdx.WEIGHT_KG]),
        scaleWeight = scaleWeight,
        matchedRoles = roles,
    )
}

/**
 * Keeps the same binary rounding as multiplying a weight by [GattConst.TENTHS] and dividing it back.
 */
private fun storedWeight(kg: Double): Double = (kg * GattConst.TENTHS) / GattConst.TENTHS

private fun RawBody.toComposition(weightKg: Double, recalculate: Boolean): Composition {
    val (shown, ori) = setVisceral(visceral)
    val baseMuscle = setMuscle(muscle)
    return Composition(
        axunge = setAxunge(axunge),
        water = setWater(water),
        muscle = if (recalculate) recalculateMuscle(baseMuscle, weightKg) else baseMuscle,
        bmr = setBmr(bmr),
        visceral = shown,
        ori = ori,
        bone = setBone(bone),
    )
}

/**
 * Body fat percent. Raw values at or above [GattConst.RAW_ABSENT], and percents
 * outside [GattConst.AXUNGE_MIN]..[GattConst.AXUNGE_MAX], become [GattConst.CLEARED].
 */
private fun setAxunge(raw: Int): Double {
    if (raw >= GattConst.RAW_ABSENT) {
        return GattConst.CLEARED
    }
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.AXUNGE_MIN && value <= GattConst.AXUNGE_MAX) {
        return value
    }
    return GattConst.CLEARED
}

/**
 * Body water percent. Values outside [GattConst.WATER_MIN]..[GattConst.WATER_MAX],
 * and values at or above [GattConst.FULL_PERCENT], become [GattConst.CLEARED].
 */
private fun setWater(raw: Int): Double {
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.FULL_PERCENT || value < GattConst.WATER_MIN || value > GattConst.WATER_MAX) {
        return GattConst.CLEARED
    }
    return value
}

/**
 * Muscle percent. A raw percent at or above [GattConst.FULL_PERCENT] becomes [GattConst.CLEARED].
 */
private fun setMuscle(raw: Int): Double {
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.FULL_PERCENT) {
        return GattConst.CLEARED
    }
    return value
}

/**
 * Basal metabolic rate. Raw values at or above [GattConst.RAW_ABSENT] become [GattConst.CLEARED].
 * Raw values at or above [GattConst.BMR_DIVIDED] are divided by [GattConst.TENTHS].
 */
private fun setBmr(raw: Int): Double {
    if (raw >= GattConst.RAW_ABSENT) {
        return GattConst.CLEARED
    }
    if (raw >= GattConst.BMR_DIVIDED) {
        return raw / GattConst.TENTHS
    }
    return raw.toDouble()
}

/**
 * Visceral fat. The first value is the clamped display percent.
 * The second value is the raw integer kept as [ScaleGatt.oriVisceralFat].
 * Display percents above [GattConst.VISCERAL_MAX], and percents at or above
 * [GattConst.FULL_PERCENT], become [GattConst.CLEARED].
 */
private fun setVisceral(raw: Int): Pair<Double, Double> {
    var shown = raw / GattConst.TENTHS
    if (shown >= GattConst.FULL_PERCENT || shown > GattConst.VISCERAL_MAX) {
        shown = GattConst.CLEARED
    }
    return shown to raw.toDouble()
}

/**
 * Bone mass in kilograms. Raw values at or above [GattConst.RAW_ABSENT] become [GattConst.CLEARED].
 * Values below [GattConst.BONE_MIN] become [GattConst.CLEARED].
 * Values above [GattConst.BONE_MAX] are capped at [GattConst.BONE_MAX].
 */
private fun setBone(raw: Int): Double {
    if (raw >= GattConst.RAW_ABSENT) {
        return GattConst.CLEARED
    }
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.BONE_MIN) {
        if (value <= GattConst.BONE_MAX) {
            return value
        }
        return GattConst.BONE_MAX
    }
    return GattConst.CLEARED
}

/**
 * Muscle percent for an assembled measurement, derived from muscle mass and body weight.
 * A ratio at or above [GattConst.MUSCLE_RATIO_CAP] keeps the muscle mass, capped at that
 * value, and passes it through [setMuscle].
 */
private fun recalculateMuscle(muscle: Double, weightKg: Double): Double {
    val ratio = when (weightKg) {
        GattConst.CLEARED if muscle == GattConst.CLEARED -> Double.NaN
        GattConst.CLEARED -> if (muscle > GattConst.CLEARED) {
            Double.POSITIVE_INFINITY
        } else {
            Double.NEGATIVE_INFINITY
        }
        else -> (muscle / weightKg) * GattConst.FULL_PERCENT
    }
    val value = if (ratio >= GattConst.MUSCLE_RATIO_CAP) {
        if (muscle >= GattConst.MUSCLE_RATIO_CAP) GattConst.MUSCLE_RATIO_CAP else muscle
    } else {
        ratio
    }
    val raw = when {
        value.isNaN() -> GattConst.CLEARED.toInt()
        value.isInfinite() -> throw ArithmeticException("int overflow")
        else -> (value * GattConst.TENTHS).toInt()
    }
    return setMuscle(raw)
}

/**
 * Clamped composition held only while [RawGattMeasurement] becomes [ScaleGatt].
 * An empty instance is the reading when the frame has no body block.
 */
private data class Composition(
    val axunge: Double = 0.0,
    val water: Double = 0.0,
    val muscle: Double = 0.0,
    val bmr: Double = 0.0,
    val visceral: Double = 0.0,
    val ori: Double = 0.0,
    val bone: Double = 0.0,
)
