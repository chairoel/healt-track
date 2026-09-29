package com.mascill.healthtrack.data.model

/**
 * Raw parsed broadcast data before mapping to domain model.
 * Contains byte values directly from protocol, not yet converted.
 */
data class RawBroadcast(
    val header: Int,
    val version: Int,
    val weightHigh: Int,
    val weightLow: Int,
    val property: Int,
    val impedanceRaw: Int?,
    val productId: Int,
    val deviceType: Int,
    val cmdId: Int,
    val seq: Int,
    val locked: Boolean,
)

/**
 * Raw parsed GATT measurement data before mapping to domain model.
 */
data class RawGattMeasurement(
    val version: Int,
    val command: Int?,
    val lockFlag: Int,
    val property: Int,
    val weightHigh: Int,
    val weightLow: Int,
    val history: Boolean,
    val axungeRaw: Int,
    val waterRaw: Int,
    val muscleRaw: Int,
    val bmrRaw: Int,
    val visceralRaw: Int,
    val boneRaw: Int,
    val roleId: Int,
    val weighingDateEpoch: Long?,
)

/**
 * Raw parsed GATT confirmation (role matching) data.
 */
data class RawGattConfirmation(
    val version: Int,
    val command: Int,
    val property: Int,
    val weightHigh: Int,
    val weightLow: Int,
    val matchedRoles: List<Int>,
)

/**
 * Sealed interface for raw GATT parse results.
 */
sealed interface RawGattResult

@JvmInline
value class RawMeasurement(val data: RawGattMeasurement) : RawGattResult

@JvmInline
value class RawConfirmation(val data: RawGattConfirmation) : RawGattResult
