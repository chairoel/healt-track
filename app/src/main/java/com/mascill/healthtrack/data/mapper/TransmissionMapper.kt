package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.model.RawTransmission
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.digitPlaces
import com.mascill.healthtrack.data.util.f32
import com.mascill.healthtrack.data.util.mapWeight
import com.mascill.healthtrack.data.util.unit
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Mapping [RawTransmission] data model to [ScaleTransmission] domain model.
 */
fun RawTransmission?.toScaleTransmission(mac: String = ""): ScaleTransmission {
    val (weightKg, scaleWeight) = this.toTransmissionWeight(cloud = false, cloudV4 = false)
    return ScaleTransmission(
        header = this?.header ?: 0,
        version = this?.version ?: 0,
        mac = mac,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = this?.impedanceRaw?.let { div10(it).toFloat() },
        productId = this?.productId ?: 0,
        deviceType = this?.deviceType ?: 0,
        cmdId = this?.cmdId ?: 0,
        locked = this?.locked ?: false,
        seq = this?.seq ?: 0,
        property = this?.property?.and(TxConst.LOW_BYTE) ?: 0,
    )
}

/**
 * Mapping [RawTransmission] data model to [ScaleTransmission] for the cloud frame.
 */
fun RawTransmission?.toCloudScaleTransmission(mac: String = "", cloudV4: Boolean = false): ScaleTransmission {
    val (weightKg, scaleWeight) = this.toTransmissionWeight(cloud = !cloudV4, cloudV4 = cloudV4)
    return ScaleTransmission(
        header = this?.header ?: 0,
        version = this?.version ?: 0,
        mac = mac,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = null,
        productId = this?.productId ?: 0,
        deviceType = this?.deviceType ?: 0,
        cmdId = this?.cmdId ?: 0,
        locked = this?.locked ?: false,
        seq = this?.seq ?: 0,
        property = this?.property?.let(::cloudProperty) ?: 0,
    )
}

private fun RawTransmission?.toTransmissionWeight(cloud: Boolean, cloudV4: Boolean): Pair<Float, String?> {
    val raw = this ?: return 0f to null
    if (!cloud && !cloudV4 && raw.weightHigh == 0 && raw.weightLow == 0) {
        return 0f to null
    }
    return mapWeight(raw.weightHigh, raw.weightLow, raw.property, cloud = cloud, cloudV4 = cloudV4)
}

private fun div10(raw: Int): Double = f32(f32(raw.toDouble()) / f32(TxConst.TENTHS))

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
