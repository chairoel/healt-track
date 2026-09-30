package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Kotlin fallback for a transmission frame.
 * `0xCA`, `0xC0`, and `0xFF` each have their own reader.
 */
internal fun parseTransmissionKotlin(frame: ByteArray, mac: String): ScaleTransmission {
    if (frame.isEmpty()) {
        throw FrameFormatError("transmission is empty")
    }
    return when (frame.u8(TxConst.HEADER)) {
        TxConst.HEADER_CA -> parseCa(frame, mac)
        TxConst.HEADER_FF -> parseFf(frame, mac)
        TxConst.HEADER_C0 -> parseC0(frame, mac)
        else -> throw FrameFormatError("unknown transmission header")
    }
}
