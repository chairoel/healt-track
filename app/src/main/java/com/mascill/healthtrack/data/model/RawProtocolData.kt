package com.mascill.healthtrack.data.model

/**
 * Transmission fields taken off a frame, before they become a domain reading.
 *
 * [weight] and [impedance] stay separate because `0xCA`, `0xC0`, and `0xFF` do not share
 * one conversion. [property] is the byte used for that conversion. [useCloudProperty] stores
 * the rebuilt cloud property instead of this byte.
 */
data class RawTransmission(
    val header: Int,
    val version: Int,
    val weight: RawWeight,
    val impedance: RawImpedance,
    val property: Int,
    val useCloudProperty: Boolean = false,
    val productId: Int,
    val deviceType: Int,
    val cmdId: Int,
    val seq: Int,
    val locked: Boolean,
)

/** How a transmission frame carries its weight. */
sealed interface RawWeight {
    /** High and low bytes, converted with the frame property. */
    data class Bytes(
        val high: Int,
        val low: Int,
        val cloud: Boolean = false,
        val cloudV4: Boolean = false,
    ) : RawWeight

    /** One integer divided by ten. The scale text is absent. */
    data class Tenths(val raw: Int) : RawWeight

    /** No weight and no scale text. */
    data object None : RawWeight

    /** No weight, with an empty scale text. */
    data object Blank : RawWeight
}

/** How a transmission frame carries impedance. */
sealed interface RawImpedance {
    data object Absent : RawImpedance
    data object Zero : RawImpedance
    data class Tenths(val raw: Int) : RawImpedance
}

/**
 * Body-composition integers sliced from a GATT frame, before display clamps.
 */
data class RawBody(
    val axunge: Int,
    val water: Int,
    val muscle: Int,
    val bmr: Int,
    val visceral: Int,
    val bone: Int,
)

/**
 * GATT measurement fields before mapping to the domain model.
 * A null [body] means this frame has no composition block.
 */
data class RawGattMeasurement(
    val version: Int,
    val command: Int?,
    val lockFlag: Int,
    val property: Int,
    val weightHigh: Int,
    val weightLow: Int,
    val history: Boolean = false,
    val body: RawBody? = null,
    val roleId: Int = 0,
    val weighingDateEpoch: Long? = null,
)

/**
 * GATT role-match fields before mapping to the domain model.
 */
data class RawGattConfirmation(
    val version: Int,
    val command: Int,
    val property: Int,
    val weightHigh: Int,
    val weightLow: Int,
    val matchedRoles: List<Int>,
)
