package com.mascill.healthtrack.data.protocol

import com.mascill.healthtrack.data.mapper.mapWeight
import com.mascill.healthtrack.data.mapper.weightKilograms

/**
 * Parse weight bytes from protocol frame.
 * Delegates to WeightMapper for unit conversion.
 *
 * @param high High byte of weight
 * @param low Low byte of weight
 * @param prop Property byte containing unit and decimal info
 * @param cloud True if cloud protocol format
 * @param cloudV4 True if cloud V4 format
 * @return Pair of (weightKg as Float, scaleText)
 */
fun parseWeight(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
    cloudV4: Boolean = false,
): Pair<Float, String> = mapWeight(high, low, prop, cloud, cloudV4)

/**
 * Internal function to get weight in kilograms with double precision.
 * Used by GATT parser for body composition calculations.
 */
internal fun weightKilogramsDouble(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
): Pair<Double, String> = weightKilograms(high, low, prop, cloud, cloudV4 = false)
