package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.model.RawImpedance
import com.mascill.healthtrack.data.model.RawTransmission
import com.mascill.healthtrack.data.model.RawWeight
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.bits
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.EncryptedTransmissionError

/**
 * Transmission header [TxConst.HEADER_C0].
 * [TxConst.ENCRYPTED] on [TxConst.C0_IMPEDANCE] marks an encrypted frame and is rejected.
 * The weight is [TxConst.C0_WEIGHT_HIGH]..[TxConst.C0_WEIGHT_LOW].
 * The impedance is [TxConst.C0_IMPEDANCE] for [TxConst.C0_IMPEDANCE_LEN] bytes.
 * The product id is [TxConst.C0_PRODUCT], the sequence is [TxConst.C0_SEQ], and the property is [TxConst.C0_PROPERTY].
 */
internal fun parseC0(frame: ByteArray): RawTransmission {
    need(frame, TxConst.C0_MIN)
    if (frame.u8(TxConst.C0_IMPEDANCE) and TxConst.ENCRYPTED != TxConst.FLAG_OFF) {
        throw EncryptedTransmissionError("encrypted transmission is outside the first release")
    }
    val prop = frame.u8(TxConst.C0_PROPERTY)
    return RawTransmission(
        header = frame.u8(TxConst.HEADER),
        version = TxConst.VERSION_C0,
        weight = RawWeight.Bytes(
            frame.u8(TxConst.C0_WEIGHT_HIGH),
            frame.u8(TxConst.C0_WEIGHT_LOW),
        ),
        impedance = RawImpedance.Tenths(
            frame.frameInt(
                TxConst.C0_IMPEDANCE,
                TxConst.C0_IMPEDANCE_LEN
            )
        ),
        property = prop,
        productId = frame.frameInt(TxConst.C0_PRODUCT, TxConst.C0_PRODUCT_LEN),
        deviceType = deviceTypeV2(prop),
        cmdId = if (lockBit(prop)) TxConst.CMD_LOCKED else TxConst.CMD_OPEN,
        seq = frame.u8(TxConst.C0_SEQ),
        locked = lockBit(prop),
    )
}

/**
 * Device type from property bit [TxConst.DEVICE_TYPE_BIT].
 * [TxConst.BIT_CLEAR] is [TxConst.DEVICE_NONE]. [TxConst.BIT_SET] is [TxConst.DEVICE_BODY].
 */
private fun deviceTypeV2(prop: Int): Int {
    return if (bits(prop)[TxConst.DEVICE_TYPE_BIT] == TxConst.BIT_CLEAR) {
        TxConst.DEVICE_NONE
    } else {
        TxConst.DEVICE_BODY
    }
}
