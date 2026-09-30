package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.mapWeight
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Transmission header `0xCA`.
 * Version byte 32 is the v2.0 layout. Every other version uses the legacy layout.
 */
internal fun parseCa(frame: ByteArray, mac: String): ScaleTransmission {
    need(frame, TxConst.CA_MIN)
    if (frame.u8(TxConst.CA_VERSION) == TxConst.VERSION_V20) {
        return parseCaV20(frame, mac)
    }
    return parseCaLegacy(frame, mac)
}

/**
 * `0xCA` versions other than 32.
 *
 * Version 16 stores the device type at byte 3 and the property at byte 4.
 * Version 17 stores the property at byte 3 and the device type at byte 4.
 * A non-zero device type, and version 17 with device type 0, read the weight at bytes 5..6
 * and the product id at bytes 7..10.
 * Version 16 with device type 0 reads a raw weight at bytes 8..9 and divides it by 10.
 * Any other version returns an empty reading.
 */
private fun parseCaLegacy(frame: ByteArray, mac: String): ScaleTransmission {
    need(frame, TxConst.CA_LEGACY_MIN)
    val version = frame.u8(TxConst.CA_VERSION)
    var deviceType = TxConst.DEVICE_NONE
    var prop = 0
    var cmdId = TxConst.CMD_OPEN
    if (version == TxConst.VERSION_16) {
        deviceType = frame.u8(TxConst.CA_V16_DEVICE)
        prop = frame.u8(TxConst.CA_V16_PROPERTY)
        cmdId = prop
    } else if (version == TxConst.VERSION_17) {
        prop = frame.u8(TxConst.CA_V17_PROPERTY)
        deviceType = frame.u8(TxConst.CA_V17_DEVICE)
    }
    if (deviceType != TxConst.DEVICE_NONE) {
        need(frame, TxConst.CA_BODY_MIN)
        val (weightKg, scaleWeight) = mapWeight(
            frame.u8(TxConst.CA_WEIGHT_HIGH),
            frame.u8(TxConst.CA_WEIGHT_LOW),
            prop,
        )
        return fromWeight(
            frame,
            mac,
            version = version,
            deviceType = deviceType,
            cmdId = if (lockBit(prop)) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
            prop = prop,
            weightKg = weightKg,
            scaleWeight = scaleWeight,
            impedanceOhm = null,
            productId = frame.frameInt(TxConst.CA_PRODUCT, TxConst.CA_PRODUCT_LEN),
            seq = 0,
            locked = lockBit(prop),
        )
    }
    if (version == TxConst.VERSION_16) {
        need(frame, TxConst.CA_V16_RAW_MIN)
        return fromWeight(
            frame,
            mac,
            version = version,
            deviceType = TxConst.DEVICE_NONE,
            cmdId = cmdId,
            prop = prop,
            weightKg = div10(frame.frameInt(TxConst.CA_RAW_WEIGHT, TxConst.CA_RAW_WEIGHT_LEN)).toFloat(),
            scaleWeight = null,
            impedanceOhm = null,
            productId = 0,
            seq = 0,
            locked = lockBit(prop),
        )
    }
    if (version != TxConst.VERSION_17) {
        return fromWeight(
            frame,
            mac,
            version = version,
            deviceType = TxConst.DEVICE_NONE,
            cmdId = TxConst.CMD_OPEN,
            prop = 0,
            weightKg = 0f,
            scaleWeight = null,
            impedanceOhm = null,
            productId = 0,
            seq = 0,
            locked = false,
        )
    }
    need(frame, TxConst.CA_BODY_MIN)
    val (weightKg, scaleWeight) = mapWeight(
        frame.u8(TxConst.CA_WEIGHT_HIGH),
        frame.u8(TxConst.CA_WEIGHT_LOW),
        prop,
    )
    return fromWeight(
        frame,
        mac,
        version = TxConst.VERSION_17,
        deviceType = TxConst.DEVICE_NONE,
        cmdId = if (lockBit(prop)) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = null,
        productId = frame.frameInt(TxConst.CA_PRODUCT, TxConst.CA_PRODUCT_LEN),
        seq = 0,
        locked = lockBit(prop),
    )
}

/**
 * `0xCA` version 32.
 * Byte 7 selects the device type. The property is byte 8.
 * A locked frame reads the weight at bytes 10..11, the sequence at byte 9,
 * and the impedance at bytes 12..13. An unlocked frame returns an empty weight.
 * The product id is bytes 3..6.
 */
private fun parseCaV20(frame: ByteArray, mac: String): ScaleTransmission {
    need(frame, TxConst.CA_V20_MIN)
    val prop = frame.u8(TxConst.CA_V20_PROPERTY)
    val locked = lockBit(prop)
    val deviceType = if (frame.u8(TxConst.CA_V20_DEVICE) == TxConst.DEVICE_NONE) {
        TxConst.DEVICE_NONE
    } else {
        TxConst.DEVICE_BODY
    }
    val weightKg: Float
    val scaleWeight: String
    val impedance: Float
    val seq: Int
    if (locked) {
        val parsed = mapWeight(
            frame.u8(TxConst.CA_V20_WEIGHT_HIGH),
            frame.u8(TxConst.CA_V20_WEIGHT_LOW),
            prop,
        )
        weightKg = parsed.first
        scaleWeight = parsed.second
        impedance = div10(frame.frameInt(TxConst.CA_V20_IMPEDANCE, TxConst.CA_V20_IMPEDANCE_LEN)).toFloat()
        seq = frame.u8(TxConst.CA_V20_SEQ)
    } else {
        weightKg = 0f
        scaleWeight = ""
        impedance = 0f
        seq = 0
    }
    return fromWeight(
        frame,
        mac,
        version = TxConst.VERSION_V20,
        deviceType = deviceType,
        cmdId = if (locked) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = impedance,
        productId = frame.frameInt(TxConst.CA_V20_PRODUCT, TxConst.CA_V20_PRODUCT_LEN),
        seq = seq,
        locked = locked,
    )
}
