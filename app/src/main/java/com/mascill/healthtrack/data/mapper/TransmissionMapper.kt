package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.model.RawImpedance
import com.mascill.healthtrack.data.model.RawTransmission
import com.mascill.healthtrack.data.model.RawWeight
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.TxIdx
import com.mascill.healthtrack.data.util.f32
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Maps transmission fields to [ScaleTransmission].
 * [mac] is the scan address. It is not part of the frame.
 */
fun RawTransmission.toScaleTransmission(mac: String = ""): ScaleTransmission {
    val (weightKg, scaleWeight) = toKilograms()
    val storedProperty = if (useCloudProperty) cloudProperty(property) else property
    return ScaleTransmission(
        header = header,
        version = version,
        mac = mac,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = impedance.toOhm(),
        productId = productId,
        deviceType = deviceType,
        cmdId = cmdId,
        locked = locked,
        seq = seq,
        property = storedProperty and TxConst.LOW_BYTE,
    )
}

/**
 * Maps a successful native transmission array to [ScaleTransmission].
 *
 * Scale-text mode [TxConst.TEXT_MISSING] is a missing text, [TxConst.TEXT_EMPTY] is an empty string,
 * and any other mode builds the text in Kotlin from the numeric pieces. [mac] is the scan address.
 * The caller rejects a native error code before this mapping.
 */
internal fun DoubleArray.toScaleTransmission(mac: String): ScaleTransmission {
    val swMode = this[TxIdx.SW_MODE].toInt()
    val scaleWeight = when (swMode) {
        TxConst.TEXT_MISSING -> null
        TxConst.TEXT_EMPTY -> ""
        else -> {
            val wHigh = this[TxIdx.W_HIGH].toInt()
            val wLow = this[TxIdx.W_LOW].toInt()
            val wPlaces = this[TxIdx.W_PLACES].toInt()
            val wUnit = this[TxIdx.W_UNIT].toInt()
            val wScaled = this[TxIdx.W_SCALED]
            scaleText(wHigh, wLow, wScaled, wPlaces, unitNameFromCode(wUnit))
        }
    }
    val hasImpedance = this[TxIdx.HAS_IMPEDANCE].toInt() != TxConst.FLAG_OFF
    return ScaleTransmission(
        header = this[TxIdx.HEADER].toInt(),
        version = this[TxIdx.VERSION].toInt(),
        mac = mac,
        weightKg = this[TxIdx.WEIGHT_KG].toFloat(),
        scaleWeight = scaleWeight,
        impedanceOhm = if (hasImpedance) this[TxIdx.IMPEDANCE].toFloat() else null,
        productId = this[TxIdx.PRODUCT_ID].toInt(),
        deviceType = this[TxIdx.DEVICE_TYPE].toInt(),
        cmdId = this[TxIdx.CMD_ID].toInt(),
        locked = this[TxIdx.LOCKED].toInt() != TxConst.FLAG_OFF,
        seq = this[TxIdx.SEQ].toInt(),
        property = this[TxIdx.PROPERTY].toInt(),
    )
}

private fun RawTransmission.toKilograms(): Pair<Float, String?> = when (val source = weight) {
    is RawWeight.Bytes -> mapWeight(
        source.high,
        source.low,
        property,
        cloud = source.cloud,
        cloudV4 = source.cloudV4,
    )
    is RawWeight.Tenths -> div10(source.raw).toFloat() to null
    RawWeight.None -> TxConst.NO_WEIGHT to null
    RawWeight.Blank -> TxConst.NO_WEIGHT to ""
}

private fun RawImpedance.toOhm(): Float? = when (this) {
    RawImpedance.Absent -> null
    RawImpedance.Zero -> TxConst.NO_IMPEDANCE
    is RawImpedance.Tenths -> div10(raw).toFloat()
}

private fun div10(raw: Int): Double = f32(f32(raw.toDouble()) / f32(TxConst.TENTHS))

/**
 * Rebuilds the property byte stored for a cloud frame from its unit and decimal places.
 */
private fun cloudProperty(prop: Int): Int {
    val unitName = unit(prop)
    val places = digitPlaces(prop, cloud = true, cloudV4 = false)
    val prefix = when (unitName) {
        "jin" -> TxConst.CLOUD_UNIT_JIN
        "lb" -> TxConst.CLOUD_UNIT_LB
        "st" -> TxConst.CLOUD_UNIT_ST
        else -> TxConst.CLOUD_UNIT_KG
    }
    val suffix = when (places) {
        TxConst.ONE_DECIMAL -> TxConst.CLOUD_PLACES_ONE
        TxConst.TWO_DECIMALS -> TxConst.CLOUD_PLACES_TWO
        else -> TxConst.CLOUD_PLACES_OTHER
    }
    return (prefix shl TxConst.CLOUD_PLACES_SHIFT) or suffix
}
