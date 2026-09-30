package com.mascill.healthtrack.data.repository.gatt

import com.mascill.healthtrack.data.model.Composition
import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.domain.model.ScaleGatt
import java.time.Instant

/**
 * Keeps the same binary rounding as multiplying a weight by 10 and dividing it back.
 */
internal fun storedWeight(kg: Double): Double = (kg * GattConst.TENTHS) / GattConst.TENTHS

/**
 * Body fat percent. Raw values at or above 65535, and percents outside 5..45, become 0.
 */
internal fun setAxunge(raw: Int): Double {
    if (raw >= GattConst.RAW_ABSENT) {
        return 0.0
    }
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.AXUNGE_MIN && value <= GattConst.AXUNGE_MAX) {
        return value
    }
    return 0.0
}

/**
 * Body water percent. Values outside 20..85, and values at or above 100, become 0.
 */
internal fun setWater(raw: Int): Double {
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.FULL_PERCENT || value < GattConst.WATER_MIN || value > GattConst.WATER_MAX) {
        return 0.0
    }
    return value
}

/**
 * Muscle percent. A raw percent at or above 100 becomes 0.
 */
internal fun setMuscle(raw: Int): Double {
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.FULL_PERCENT) {
        return 0.0
    }
    return value
}

/**
 * Basal metabolic rate. Raw values at or above 65535 become 0.
 * Raw values at or above 5000 are divided by 10.
 */
internal fun setBmr(raw: Int): Double {
    if (raw >= GattConst.RAW_ABSENT) {
        return 0.0
    }
    if (raw >= GattConst.BMR_DIVIDED) {
        return raw / GattConst.TENTHS
    }
    return raw.toDouble()
}

/**
 * Visceral fat. The first value is the clamped display percent.
 * The second value is the raw integer kept as [ScaleGatt.oriVisceralFat].
 * Display percents above 59, and percents at or above 100, become 0.
 */
internal fun setVisceral(raw: Int): Pair<Double, Double> {
    var shown = raw / GattConst.TENTHS
    if (shown >= GattConst.FULL_PERCENT || shown > GattConst.VISCERAL_MAX) {
        shown = 0.0
    }
    return shown to raw.toDouble()
}

/**
 * Bone mass in kilograms. Raw values at or above 65535 become 0.
 * Values below 1 become 0. Values above 4 are capped at 4.
 */
internal fun setBone(raw: Int): Double {
    if (raw >= GattConst.RAW_ABSENT) {
        return 0.0
    }
    val value = raw / GattConst.TENTHS
    if (value >= GattConst.BONE_MIN) {
        if (value <= GattConst.BONE_MAX) {
            return value
        }
        return GattConst.BONE_MAX
    }
    return 0.0
}

/**
 * Muscle percent for an assembled measurement, derived from muscle mass and body weight.
 * A ratio at or above 50 keeps the muscle mass, capped at 50, and passes it through [setMuscle].
 */
internal fun recalculateMuscle(muscle: Double, weightKg: Double): Double {
    val ratio = when {
        weightKg == 0.0 && muscle == 0.0 -> Double.NaN
        weightKg == 0.0 -> if (muscle > 0.0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        else -> (muscle / weightKg) * GattConst.FULL_PERCENT
    }
    val value = if (ratio >= GattConst.MUSCLE_RATIO_CAP) {
        if (muscle >= GattConst.MUSCLE_RATIO_CAP) GattConst.MUSCLE_RATIO_CAP else muscle
    } else {
        ratio
    }
    val raw = when {
        value.isNaN() -> 0
        value.isInfinite() -> throw ArithmeticException("int overflow")
        else -> (value * GattConst.TENTHS).toInt()
    }
    return setMuscle(raw)
}

/**
 * Composition block on a version-16 frame.
 * Fat, water, muscle, BMR, and visceral fat are 2-byte fields from byte 7.
 * Bone is one byte at offset 17.
 */
internal fun composition(frame: ByteArray): Composition {
    val (visceral, ori) = setVisceral(sliceInt(frame, GattConst.V16_VISCERAL, GattConst.FIELD_LEN))
    return Composition(
        axunge = setAxunge(sliceInt(frame, GattConst.V16_AXUNGE, GattConst.FIELD_LEN)),
        water = setWater(sliceInt(frame, GattConst.V16_WATER, GattConst.FIELD_LEN)),
        muscle = setMuscle(sliceInt(frame, GattConst.V16_MUSCLE, GattConst.FIELD_LEN)),
        bmr = setBmr(sliceInt(frame, GattConst.V16_BMR, GattConst.FIELD_LEN)),
        visceral = visceral,
        ori = ori,
        bone = setBone(sliceInt(frame, GattConst.V16_BONE, GattConst.BONE_LEN)),
    )
}

/**
 * Domain measurement. A missing [composition] stores zeros for every composition field.
 */
internal fun result(
    version: Int,
    command: Int?,
    lockFlag: Int,
    prop: Int,
    weightKg: Double,
    scaleWeight: String,
    composition: Composition?,
    history: Boolean = false,
    roleId: Int = 0,
    weighingDate: Instant? = null,
): ScaleGatt {
    val values = composition ?: Composition()
    return ScaleGatt(
        version = version,
        command = command,
        lockFlag = lockFlag,
        property = prop and GattConst.LOW_BYTE,
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
        weighingDate = weighingDate,
    )
}
