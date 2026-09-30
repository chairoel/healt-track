package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.bits
import com.mascill.healthtrack.data.util.mapWeight
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.EncryptedTransmissionError
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Transmission header `0xC0`.
 * Bit 7 of byte 4 marks an encrypted frame and is rejected.
 * The weight is bytes 2..3, the impedance is bytes 4..5 divided by 10,
 * the product id is bytes 6..7, the sequence is byte 1, and the property is byte 8.
 */
internal fun parseC0(frame: ByteArray, mac: String): ScaleTransmission {
    need(frame, TxConst.C0_MIN)
    if (frame.u8(TxConst.C0_IMPEDANCE) and TxConst.ENCRYPTED != 0) {
        throw EncryptedTransmissionError("encrypted 0xC0 transmission is outside the first release")
    }
    val prop = frame.u8(TxConst.C0_PROPERTY)
    val (weightKg, scaleWeight) = mapWeight(
        frame.u8(TxConst.C0_WEIGHT_HIGH),
        frame.u8(TxConst.C0_WEIGHT_LOW),
        prop,
    )
    return fromWeight(
        frame,
        mac,
        version = TxConst.VERSION_C0,
        deviceType = deviceTypeV2(prop),
        cmdId = if (lockBit(prop)) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = div10(frame.frameInt(TxConst.C0_IMPEDANCE, TxConst.C0_IMPEDANCE_LEN)).toFloat(),
        productId = frame.frameInt(TxConst.C0_PRODUCT, TxConst.C0_PRODUCT_LEN),
        seq = frame.u8(TxConst.C0_SEQ),
        locked = lockBit(prop),
    )
}

/**
 * Device type from property bit 2. A clear bit is type 0. A set bit is type 2.
 */
private fun deviceTypeV2(prop: Int): Int =
    if (bits(prop)[TxConst.DEVICE_TYPE_BIT] == '0') TxConst.DEVICE_NONE else TxConst.DEVICE_BODY
