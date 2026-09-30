package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.u8

/**
 * Manufacturer payloads that belong to a scale frame.
 * A scan record is a sequence of blocks: length, type, then data.
 */
private val scaleHeaders = intArrayOf(TxConst.HEADER_CA, TxConst.HEADER_C0)

/**
 * First manufacturer block in [record] whose payload starts with `0xCA` or `0xC0`.
 * A truncated block or a zero length ends the scan with no payload.
 */
internal fun ktScalePayload(record: ByteArray): ByteArray? {
    var index = 0
    while (index < record.size) {
        val length = record.u8(index)
        index += 1
        if (length == 0 || index + length > record.size) {
            return null
        }
        val block = record.copyOfRange(index, index + length)
        index += length
        if (block.isEmpty() || block.u8(TxConst.AD_TYPE) != TxConst.AD_MANUFACTURER || block.size < TxConst.MIN_AD_BLOCK) {
            continue
        }
        val payload = block.copyOfRange(TxConst.AD_DATA, block.size)
        if (payload.u8(TxConst.HEADER) in scaleHeaders) {
            return payload
        }
    }
    return null
}

/**
 * Little-endian manufacturer id plus [remainder].
 * An id outside 0..65535 is rejected. The result is returned only when it starts with `0xCA` or `0xC0`.
 */
internal fun ktPayloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? {
    if (companyId !in 0..TxConst.MANUFACTURER_ID_MAX) {
        throw IllegalArgumentException("manufacturer id does not fit in 16 bits")
    }
    val payload = byteArrayOf(
        (companyId and TxConst.LOW_BYTE).toByte(),
        ((companyId ushr TxConst.HIGH_BYTE_SHIFT) and TxConst.LOW_BYTE).toByte(),
    ) + remainder
    if (payload.u8(TxConst.HEADER) in scaleHeaders) {
        return payload
    }
    return null
}
