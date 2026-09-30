package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.util.Constants
import com.mascill.healthtrack.data.util.bits
import com.mascill.healthtrack.data.util.bytesToInt
import com.mascill.healthtrack.data.util.f32
import com.mascill.healthtrack.data.util.javaFloatText
import com.mascill.healthtrack.data.util.roundHalfUp

/**
 * Extract unit name from property byte.
 */
internal fun unit(prop: Int): String {
    return when (bits(prop).substring(3, 5)) {
        "01" -> "jin"
        "10" -> "lb"
        "11" -> "st"
        else -> "kg"
    }
}

/**
 * Extract decimal places from property byte.
 */
internal fun digitPlaces(prop: Int, cloud: Boolean, cloudV4: Boolean): Int {
    if (cloudV4) {
        return when (bits(prop).substring(1, 3)) {
            "01" -> 0
            "10" -> 1
            else -> 2
        }
    }
    val pair = bits(prop).substring(5, 7)
    if (cloud) {
        return when (pair) {
            "01" -> 0
            "10" -> 1
            else -> 2
        }
    }
    return when (pair) {
        "01" -> 0
        "10" -> 2
        else -> 1
    }
}

internal fun unitNameFromCode(code: Int): String = when (code) {
    1 -> "jin"
    2 -> "lb"
    3 -> "st"
    else -> "kg"
}

internal fun scaleText(high: Int, low: Int, scaled: Double, places: Int, unit: String): String {
    if (unit == "st") {
        val pounds = f32((low and 0xFF).toDouble() / 10.0)
        return "${high and 0xFF}:${javaFloatText(pounds)}"
    }
    if (places == 0) {
        return f32(scaled).toInt().toString()
    }
    val shown = roundHalfUp(scaled, places, asFloat32 = true)
    return javaFloatText(shown)
}

/**
 * Convert weight bytes to kilograms and display text.
 * This handles unit conversion (kg, jin, lb, st) and decimal places.
 *
 * @param high High byte of weight
 * @param low Low byte of weight
 * @param prop Property byte containing unit and decimal info
 * @param cloud True if cloud protocol format
 * @param cloudV4 True if cloud V4 format
 * @return Pair of (weightKg, scaleText)
 */
fun weightKilograms(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
    cloudV4: Boolean = false,
): Pair<Double, String> {
    if (NativeProtocol.isLoaded) {
        return nativeWeightKilograms(high, low, prop, cloud, cloudV4)
    }
    return ktWeightKilograms(high, low, prop, cloud, cloudV4)
}

private fun nativeWeightKilograms(
    high: Int, low: Int, prop: Int, cloud: Boolean, cloudV4: Boolean,
): Pair<Double, String> {
    val r = NativeProtocol.nativeWeightKilograms(high, low, prop, cloud, cloudV4)
    val kg = r[0]
    val places = r[1].toInt()
    val unitCode = r[2].toInt()
    val scaled = r[4]
    val unitName = unitNameFromCode(unitCode)
    val text = scaleText(high, low, scaled, places, unitName)
    return kg to text
}

private fun ktWeightKilograms(
    high: Int, low: Int, prop: Int, cloud: Boolean, cloudV4: Boolean,
): Pair<Double, String> {
    val places = digitPlaces(prop, cloud = cloud, cloudV4 = cloudV4)
    val raw = bytesToInt(byteArrayOf((high and 0xFF).toByte(), (low and 0xFF).toByte()))
    var scaled = when (places) {
        0 -> f32(raw.toDouble())
        1 -> f32(f32(raw.toDouble()) / f32(10.0))
        else -> f32(f32(raw.toDouble()) / f32(100.0))
    }
    scaled = roundHalfUp(scaled, places, asFloat32 = true)
    val unitName = unit(prop)
    val text = scaleText(high, low, scaled, places, unitName)
    var kg = when (unitName) {
        "jin" -> f32(scaled * f32(0.5))
        "lb" -> f32(scaled * Constants.LB_PER_KG)
        "st" -> {
            val stones = text.substringBefore(":")
            val pounds = text.substringAfter(":", "")
            val totalLb = try {
                (stones.toInt() * 14) + pounds.toDouble()
            } catch (error: NumberFormatException) {
                0.0
            }
            roundHalfUp(f32(f32(totalLb) * Constants.LB_PER_KG), 3, asFloat32 = true)
        }
        else -> scaled
    }
    kg = roundHalfUp(kg, places, asFloat32 = false)
    return kg to text
}

/**
 * Parse weight bytes returning Float for transmission compatibility.
 */
fun mapWeight(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
    cloudV4: Boolean = false,
): Pair<Float, String> {
    val (kg, text) = weightKilograms(high, low, prop, cloud, cloudV4)
    return kg.toFloat() to text
}
