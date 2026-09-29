package com.mascill.healthtrack.domain.protocol

private val scaleHeaders = intArrayOf(0xCA, 0xC0)

/**
 * First manufacturer payload that starts with `0xCA` or `0xC0`.
 * `record` is a raw scan record: length, type, then data.
 */
fun scalePayload(record: ByteArray): ByteArray? {
    var index = 0
    while (index < record.size) {
        val length = record.u8(index)
        index += 1
        if (length == 0 || index + length > record.size) {
            return null
        }
        val block = record.copyOfRange(index, index + length)
        index += length
        if (block.isEmpty() || block.u8(0) != 0xFF || block.size < 2) {
            continue
        }
        val payload = block.copyOfRange(1, block.size)
        if (payload.u8(0) in scaleHeaders) {
            return payload
        }
    }
    return null
}

/**
 * Rebuild a 16-bit little-endian manufacturer id plus the remaining payload.
 * A `companyId` outside 0..65535 follows `int.to_bytes(2, "little")` and is rejected.
 */
fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? {
    if (companyId !in 0..0xFFFF) {
        throw IllegalArgumentException("manufacturer id does not fit in 16 bits")
    }
    val payload = byteArrayOf(
        (companyId and 0xFF).toByte(),
        ((companyId ushr 8) and 0xFF).toByte(),
    ) + remainder
    if (payload.u8(0) in scaleHeaders) {
        return payload
    }
    return null
}
