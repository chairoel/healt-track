package com.mascill.healthtrack.data.repository.gatt

import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.FrameFormatError

/**
 * Rejects a GATT frame shorter than [size].
 */
internal fun need(frame: ByteArray, size: Int) {
    if (frame.size < size) {
        throw FrameFormatError("GATT frame is too short")
    }
}

/**
 * Low 8 bits of [value] read as a signed Java byte.
 */
internal fun javaByte(value: Int): Int {
    val unsigned = value and GattConst.LOW_BYTE
    return if (unsigned >= GattConst.SIGNED_BYTE) unsigned - GattConst.BYTE_SPAN else unsigned
}

/**
 * Big-endian integer, or 0 when the bytes are empty or do not fit in a signed 32-bit value.
 */
private fun bytesToIntSafe(raw: ByteArray): Int {
    if (raw.isEmpty()) {
        return 0
    }
    var value = 0L
    for (byte in raw) {
        value = (value shl GattConst.BYTE_SHIFT) or (byte.u8().toLong())
    }
    if (value > Int.MAX_VALUE) {
        return 0
    }
    return value.toInt()
}

/**
 * Big-endian integer at [start], length [size]. A slice that runs past the frame is 0.
 */
internal fun sliceInt(frame: ByteArray, start: Int, size: Int): Int {
    if (size <= 0 || frame.size < start + size) {
        return 0
    }
    return bytesToIntSafe(frame.copyOfRange(start, start + size))
}

/**
 * Packet total and packet number packed into one byte on commands 18, 19, and 20.
 * The high nibble is the number. The low nibble is the total.
 */
internal fun splitPack(raw: Int): Pair<Int, Int> {
    val signed = javaByte(raw)
    val low = javaByte((signed shl GattConst.NIBBLE_SHIFT) and GattConst.LOW_BYTE)
    val total = javaByte(low shr GattConst.NIBBLE_SHIFT)
    val number = javaByte(signed shr GattConst.NIBBLE_SHIFT)
    return total to number
}

/**
 * Joins packet payloads in number order, starting at 1.
 * A number at or above 128 is stored under its signed-byte key.
 */
internal fun concatPackets(store: Map<Int, ByteArray>): ByteArray {
    val parts = ArrayList<ByteArray>(store.size)
    for (index in GattConst.FIRST_PACKET..store.size) {
        val key = if (index < GattConst.SIGNED_BYTE) index else index - GattConst.BYTE_SPAN
        val piece = store[key] ?: throw FrameFormatError("GATT packet is missing")
        parts.add(piece)
    }
    var total = 0
    for (part in parts) {
        total += part.size
    }
    val joined = ByteArray(total)
    var offset = 0
    for (part in parts) {
        part.copyInto(joined, offset)
        offset += part.size
    }
    return joined
}
