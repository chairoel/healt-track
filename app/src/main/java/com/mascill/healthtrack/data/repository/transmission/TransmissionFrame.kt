package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.bits
import com.mascill.healthtrack.data.util.bytesToInt
import com.mascill.healthtrack.data.util.digitPlaces
import com.mascill.healthtrack.data.util.f32
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.data.util.unit
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Rejects a transmission shorter than [size].
 */
internal fun need(frame: ByteArray, size: Int) {
    if (frame.size < size) {
        throw FrameFormatError("transmission is too short")
    }
}

/**
 * Property bit 7 is set once the scale has locked the weight.
 */
internal fun lockBit(prop: Int): Boolean = bits(prop)[TxConst.LOCK_BIT] == '1'

/**
 * Divides a raw integer by 10 using float32 rounding.
 */
internal fun div10(raw: Int): Double = f32(f32(raw.toDouble()) / f32(TxConst.TENTHS))

/**
 * Big-endian integer at [offset] for [length] bytes.
 */
internal fun ByteArray.frameInt(offset: Int, length: Int): Int =
    bytesToInt(copyOfRange(offset, offset + length))

/**
 * Rebuilds the property byte stored for a cloud frame from its unit and decimal places.
 */
internal fun cloudProperty(prop: Int): Int {
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

/**
 * Builds the domain reading from fields already taken off a frame.
 * [prop] is stored as its low 8 bits. [mac] comes from the scan result, not the frame.
 */
internal fun fromWeight(
    frame: ByteArray,
    mac: String,
    version: Int,
    deviceType: Int,
    cmdId: Int,
    prop: Int,
    weightKg: Float,
    scaleWeight: String?,
    impedanceOhm: Float?,
    productId: Int,
    seq: Int,
    locked: Boolean,
): ScaleTransmission {
    return ScaleTransmission(
        header = frame.u8(TxConst.HEADER),
        version = version,
        mac = mac,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = impedanceOhm,
        productId = productId,
        deviceType = deviceType,
        cmdId = cmdId,
        locked = locked,
        seq = seq,
        property = prop and TxConst.LOW_BYTE,
    )
}
