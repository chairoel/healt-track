package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.model.RawImpedance
import com.mascill.healthtrack.data.model.RawTransmission
import com.mascill.healthtrack.data.model.RawWeight
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.u8

/**
 * Transmission header [TxConst.HEADER_FF], the cloud frame.
 * The version is [TxConst.FF_VERSION] and the property is [TxConst.FF_PROPERTY].
 * The product id is [TxConst.FF_PRODUCT] for [TxConst.FF_PRODUCT_LEN] bytes.
 * [TxConst.CLOUD_SENTINEL] becomes product id [TxConst.CLOUD_SENTINEL_ID] with [TxConst.CLOUD_FORCED_PROPERTY].
 * [TxConst.CLOUD_PRODUCT_SPECIAL] also forces that property.
 * [TxConst.VERSION_16] and [TxConst.VERSION_17] are cloud v4: the weight high byte is [TxConst.FF_WEIGHT_HIGH]
 * and the low byte is [TxConst.FF_WEIGHT_LOW].
 * Older cloud versions use the same byte order with the cloud decimal layout.
 * The stored property is rebuilt by the mapper. Impedance is absent.
 */
internal fun parseFf(frame: ByteArray): RawTransmission {
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
    return RawTransmission(
        header = frame.u8(TxConst.HEADER),
        version = version,
        weight = RawWeight.Bytes(
            frame.u8(TxConst.FF_WEIGHT_HIGH),
            frame.u8(TxConst.FF_WEIGHT_LOW),
            cloud = !cloudV4,
            cloudV4 = cloudV4,
        ),
        impedance = RawImpedance.Absent,
        property = prop,
        useCloudProperty = true,
        productId = product,
        deviceType = TxConst.DEVICE_CLOUD,
        cmdId = TxConst.CMD_OPEN,
        seq = TxConst.NO_SEQ,
        locked = false,
    )
}
