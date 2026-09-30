package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.mapWeight
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Transmission header `0xFF`, the cloud frame.
 * The version is byte 2 and the property is byte 3. The product id is bytes 6..9.
 * Product bytes `FA FB FC FD` become product id 997264800 with property 4.
 * Product id 1104000001 also forces property 4.
 * Versions 16 and 17 are cloud v4: the weight high byte is byte 5 and the low byte is byte 4.
 * Older cloud versions use the same byte order with the cloud decimal layout.
 * The stored property is [cloudProperty], not the raw byte. Impedance is absent.
 */
internal fun parseFf(frame: ByteArray, mac: String): ScaleTransmission {
    need(frame, TxConst.FF_MIN)
    val version = frame.u8(TxConst.FF_VERSION)
    var prop = frame.u8(TxConst.FF_PROPERTY)
    var product = frame.frameInt(TxConst.FF_PRODUCT, TxConst.FF_PRODUCT_LEN)
    if (frame.copyOfRange(TxConst.FF_PRODUCT, TxConst.FF_PRODUCT + TxConst.FF_PRODUCT_LEN)
            .contentEquals(TxConst.CLOUD_SENTINEL)
    ) {
        product = TxConst.CLOUD_SENTINEL_ID
        prop = TxConst.CLOUD_FORCED_PROPERTY
    } else if (product == TxConst.CLOUD_PRODUCT_SPECIAL) {
        prop = TxConst.CLOUD_FORCED_PROPERTY
    }
    val cloudV4 = version == TxConst.VERSION_16 || version == TxConst.VERSION_17
    val (weightKg, scaleWeight) = mapWeight(
        frame.u8(TxConst.FF_WEIGHT_HIGH),
        frame.u8(TxConst.FF_WEIGHT_LOW),
        prop,
        cloud = !cloudV4,
        cloudV4 = cloudV4,
    )
    return fromWeight(
        frame,
        mac,
        version = version,
        deviceType = TxConst.DEVICE_CLOUD,
        cmdId = TxConst.CMD_OPEN,
        prop = cloudProperty(prop),
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = null,
        productId = product,
        seq = 0,
        locked = false,
    )
}
