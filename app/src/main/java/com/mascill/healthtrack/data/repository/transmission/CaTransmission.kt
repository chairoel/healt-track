package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.model.RawImpedance
import com.mascill.healthtrack.data.model.RawTransmission
import com.mascill.healthtrack.data.model.RawWeight
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.u8

/**
 * Transmission header [TxConst.HEADER_CA].
 * [TxConst.VERSION_V20] is the v2.0 layout. Every other version uses the legacy layout.
 */
internal fun parseCa(frame: ByteArray): RawTransmission {
    need(frame, TxConst.CA_MIN)
    if (frame.u8(TxConst.CA_VERSION) == TxConst.VERSION_V20) {
        return parseCaV20(frame)
    }
    return parseCaLegacy(frame)
}

/**
 * [TxConst.HEADER_CA] versions other than [TxConst.VERSION_V20].
 *
 * [TxConst.VERSION_16] stores the device type at [TxConst.CA_V16_DEVICE] and the property at [TxConst.CA_V16_PROPERTY].
 * [TxConst.VERSION_17] stores the property at [TxConst.CA_V17_PROPERTY] and the device type at [TxConst.CA_V17_DEVICE].
 * A device type other than [TxConst.DEVICE_NONE], and [TxConst.VERSION_17] with [TxConst.DEVICE_NONE], read the weight
 * at [TxConst.CA_WEIGHT_HIGH]..[TxConst.CA_WEIGHT_LOW] and the product id at [TxConst.CA_PRODUCT].
 * [TxConst.VERSION_16] with [TxConst.DEVICE_NONE] reads a raw weight at [TxConst.CA_RAW_WEIGHT].
 * Any other version returns an empty reading.
 */
private fun parseCaLegacy(frame: ByteArray): RawTransmission {
    need(frame, TxConst.CA_LEGACY_MIN)
    val version = frame.u8(TxConst.CA_VERSION)
    var deviceType = TxConst.DEVICE_NONE
    var prop = TxConst.NO_PROPERTY
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
        return reading(
            frame,
            version = version,
            deviceType = deviceType,
            cmdId = if (lockBit(prop)) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
            prop = prop,
            weight = bytes(frame, TxConst.CA_WEIGHT_HIGH, TxConst.CA_WEIGHT_LOW),
            productId = frame.frameInt(TxConst.CA_PRODUCT, TxConst.CA_PRODUCT_LEN),
            locked = lockBit(prop),
        )
    }
    if (version == TxConst.VERSION_16) {
        need(frame, TxConst.CA_V16_RAW_MIN)
        return reading(
            frame,
            version = version,
            deviceType = TxConst.DEVICE_NONE,
            cmdId = cmdId,
            prop = prop,
            weight = RawWeight.Tenths(frame.frameInt(TxConst.CA_RAW_WEIGHT, TxConst.CA_RAW_WEIGHT_LEN)),
            productId = TxConst.NO_PRODUCT,
            locked = lockBit(prop),
        )
    }
    if (version != TxConst.VERSION_17) {
        return reading(
            frame,
            version = version,
            deviceType = TxConst.DEVICE_NONE,
            cmdId = TxConst.CMD_OPEN,
            prop = TxConst.NO_PROPERTY,
            weight = RawWeight.None,
            productId = TxConst.NO_PRODUCT,
            locked = false,
        )
    }
    need(frame, TxConst.CA_BODY_MIN)
    return reading(
        frame,
        version = TxConst.VERSION_17,
        deviceType = TxConst.DEVICE_NONE,
        cmdId = if (lockBit(prop)) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
        prop = prop,
        weight = bytes(frame, TxConst.CA_WEIGHT_HIGH, TxConst.CA_WEIGHT_LOW),
        productId = frame.frameInt(TxConst.CA_PRODUCT, TxConst.CA_PRODUCT_LEN),
        locked = lockBit(prop),
    )
}

/**
 * [TxConst.HEADER_CA] version [TxConst.VERSION_V20].
 * [TxConst.CA_V20_DEVICE] selects the device type. The property is [TxConst.CA_V20_PROPERTY].
 * A locked frame reads the weight at [TxConst.CA_V20_WEIGHT_HIGH]..[TxConst.CA_V20_WEIGHT_LOW],
 * the sequence at [TxConst.CA_V20_SEQ], and the impedance at [TxConst.CA_V20_IMPEDANCE].
 * An unlocked frame returns an empty weight and zero impedance.
 * The product id is [TxConst.CA_V20_PRODUCT].
 */
private fun parseCaV20(frame: ByteArray): RawTransmission {
    need(frame, TxConst.CA_V20_MIN)
    val prop = frame.u8(TxConst.CA_V20_PROPERTY)
    val locked = lockBit(prop)
    val deviceType = if (frame.u8(TxConst.CA_V20_DEVICE) == TxConst.DEVICE_NONE) {
        TxConst.DEVICE_NONE
    } else {
        TxConst.DEVICE_BODY
    }
    if (!locked) {
        return reading(
            frame,
            version = TxConst.VERSION_V20,
            deviceType = deviceType,
            cmdId = TxConst.CMD_OPEN,
            prop = prop,
            weight = RawWeight.Blank,
            impedance = RawImpedance.Zero,
            productId = frame.frameInt(TxConst.CA_V20_PRODUCT, TxConst.CA_V20_PRODUCT_LEN),
            seq = TxConst.NO_SEQ,
            locked = false,
        )
    }
    return reading(
        frame,
        version = TxConst.VERSION_V20,
        deviceType = deviceType,
        cmdId = TxConst.CMD_LOCKED,
        prop = prop,
        weight = bytes(frame, TxConst.CA_V20_WEIGHT_HIGH, TxConst.CA_V20_WEIGHT_LOW),
        impedance = RawImpedance.Tenths(frame.frameInt(TxConst.CA_V20_IMPEDANCE, TxConst.CA_V20_IMPEDANCE_LEN)),
        productId = frame.frameInt(TxConst.CA_V20_PRODUCT, TxConst.CA_V20_PRODUCT_LEN),
        seq = frame.u8(TxConst.CA_V20_SEQ),
        locked = true,
    )
}

private fun bytes(frame: ByteArray, high: Int, low: Int): RawWeight.Bytes =
    RawWeight.Bytes(frame.u8(high), frame.u8(low))

private fun reading(
    frame: ByteArray,
    version: Int,
    deviceType: Int,
    cmdId: Int,
    prop: Int,
    weight: RawWeight,
    productId: Int,
    locked: Boolean,
    impedance: RawImpedance = RawImpedance.Absent,
    seq: Int = TxConst.NO_SEQ,
): RawTransmission = RawTransmission(
    header = frame.u8(TxConst.HEADER),
    version = version,
    weight = weight,
    impedance = impedance,
    property = prop,
    productId = productId,
    deviceType = deviceType,
    cmdId = cmdId,
    seq = seq,
    locked = locked,
)
