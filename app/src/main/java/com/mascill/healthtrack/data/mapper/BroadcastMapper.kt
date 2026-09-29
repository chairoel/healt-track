package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.model.RawBroadcast
import com.mascill.healthtrack.data.util.digitPlaces
import com.mascill.healthtrack.data.util.f32
import com.mascill.healthtrack.data.util.mapWeight
import com.mascill.healthtrack.data.util.unit
import com.mascill.healthtrack.domain.model.ScaleBroadcast

/**
 * Mapping [RawBroadcast] data model to [ScaleBroadcast] domain model.
 */
fun RawBroadcast?.toScaleBroadcast(mac: String = ""): ScaleBroadcast {
    val (weightKg, scaleWeight) = this.toBroadcastWeight(cloud = false, cloudV4 = false)
    return ScaleBroadcast(
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
        property = this?.property?.and(0xFF) ?: 0,
    )
}

/**
 * Mapping [RawBroadcast] data model to [ScaleBroadcast] for the cloud frame.
 */
fun RawBroadcast?.toCloudScaleBroadcast(mac: String = "", cloudV4: Boolean = false): ScaleBroadcast {
    val (weightKg, scaleWeight) = this.toBroadcastWeight(cloud = !cloudV4, cloudV4 = cloudV4)
    return ScaleBroadcast(
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

private fun RawBroadcast?.toBroadcastWeight(cloud: Boolean, cloudV4: Boolean): Pair<Float, String?> {
    val raw = this ?: return 0f to null
    if (!cloud && !cloudV4 && raw.weightHigh == 0 && raw.weightLow == 0) {
        return 0f to null
    }
    return mapWeight(raw.weightHigh, raw.weightLow, raw.property, cloud = cloud, cloudV4 = cloudV4)
}

private fun div10(raw: Int): Double = f32(f32(raw.toDouble()) / f32(10.0))

private fun cloudProperty(prop: Int): Int {
    val unitName = unit(prop)
    val places = digitPlaces(prop, cloud = true, cloudV4 = false)
    val prefix = when (unitName) {
        "jin" -> "00001"
        "lb" -> "00010"
        "st" -> "00011"
        else -> "00000"
    }
    val suffix = when (places) {
        1 -> "001"
        2 -> "101"
        else -> "011"
    }
    return (prefix + suffix).toInt(2)
}
