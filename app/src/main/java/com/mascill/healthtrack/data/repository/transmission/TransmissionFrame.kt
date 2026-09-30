package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.bits
import com.mascill.healthtrack.data.util.bytesToInt
import com.mascill.healthtrack.domain.model.FrameFormatError

/**
 * Rejects a transmission shorter than [size].
 */
internal fun need(frame: ByteArray, size: Int) {
    if (frame.size < size) {
        throw FrameFormatError("transmission is too short")
    }
}

/**
 * Property bit [TxConst.LOCK_BIT] is set once the scale has locked the weight.
 */
internal fun lockBit(prop: Int): Boolean = bits(prop)[TxConst.LOCK_BIT] == TxConst.BIT_SET

/**
 * Big-endian integer at [offset] for [length] bytes.
 */
internal fun ByteArray.frameInt(offset: Int, length: Int): Int =
    bytesToInt(copyOfRange(offset, offset + length))
