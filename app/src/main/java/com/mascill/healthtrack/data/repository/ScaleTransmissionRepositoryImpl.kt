package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.di.ScaleFrameHeaders
import com.mascill.healthtrack.data.mapper.toScaleTransmission
import com.mascill.healthtrack.data.repository.transmission.parseC0
import com.mascill.healthtrack.data.repository.transmission.parseCa
import com.mascill.healthtrack.data.repository.transmission.parseFf
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.TxIdx
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.EncryptedTransmissionError
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.ScaleTransmission
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import javax.inject.Inject

/**
 * Reads scale advertisement records and transmission frames.
 *
 * Android runs the readers in `libscale_protocol.so`. The JVM host, where that library
 * does not load, reads frames in `data.repository.transmission`.
 * Both paths build [ScaleTransmission] in `data.mapper`.
 * The advertisement fallback stays on this class. [NativeProtocol.isLoaded] selects the path.
 */
class ScaleTransmissionRepositoryImpl @Inject constructor(
    @param:ScaleFrameHeaders private val scaleHeaders: IntArray,
) : ScaleTransmissionRepository {
    override fun scalePayload(record: ByteArray): ByteArray? {
        if (NativeProtocol.isLoaded) {
            return NativeProtocol.nativeScalePayload(record)
        }
        return scalePayloadKotlin(record)
    }

    /**
     * Rebuilds a 16-bit little-endian manufacturer id plus [remainder].
     * A [companyId] outside 0..65535 follows `int.to_bytes(2, "little")` and is rejected.
     */
    override fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? {
        if (NativeProtocol.isLoaded) {
            return NativeProtocol.nativePayloadFromManufacturer(companyId, remainder)
        }
        return ktPayloadFromManufacturer(companyId, remainder)
    }

    override fun parseTransmission(frame: ByteArray, mac: String): ScaleTransmission {
        if (NativeProtocol.isLoaded) {
            return transmissionFromNative(frame, mac)
        }
        return parseTransmissionKotlin(frame, mac)
    }


    /**
     * First manufacturer block in [record] whose payload starts with a header in [scaleHeaders].
     * A truncated block or a length of [TxConst.AD_END] ends the scan with no payload.
     */
    private fun scalePayloadKotlin(record: ByteArray): ByteArray? {
        var index = TxConst.AD_START
        while (index < record.size) {
            val length = record.u8(index)
            index += TxConst.AD_LENGTH_BYTES
            if (length == TxConst.AD_END || index + length > record.size) {
                return null
            }
            val block = record.copyOfRange(index, index + length)
            index += length
            if (block.isEmpty()
                || block.u8(TxConst.AD_TYPE) != TxConst.AD_MANUFACTURER
                || block.size < TxConst.MIN_AD_BLOCK
            ) {
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
     * An id outside [TxConst.MANUFACTURER_ID_MIN]..[TxConst.MANUFACTURER_ID_MAX] is rejected.
     * The result is returned only when its header is in [scaleHeaders].
     */
    private fun ktPayloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? {
        if (companyId !in TxConst.MANUFACTURER_ID_MIN..TxConst.MANUFACTURER_ID_MAX) {
            throw IllegalArgumentException("manufacturer id does not fit in two bytes")
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


    /**
     * Rejects a native error, then maps the remaining doubles to [ScaleTransmission].
     *
     * Error codes are [TxConst.ERR_EMPTY], [TxConst.ERR_UNKNOWN], [TxConst.ERR_SHORT], and [TxConst.ERR_ENCRYPTED].
     */
    private fun transmissionFromNative(frame: ByteArray, mac: String): ScaleTransmission {
        val values = NativeProtocol.nativeParseTransmission(frame)
        when (values[TxIdx.ERROR].toInt()) {
            TxConst.ERR_EMPTY -> throw FrameFormatError("transmission is empty")
            TxConst.ERR_UNKNOWN -> throw FrameFormatError("unknown transmission header")
            TxConst.ERR_SHORT -> throw FrameFormatError("transmission is too short")
            TxConst.ERR_ENCRYPTED -> throw EncryptedTransmissionError("encrypted transmission is outside the first release")
        }
        return values.toScaleTransmission(mac)
    }

    /**
     * Kotlin fallback for a transmission frame.
     * [TxConst.HEADER_CA], [TxConst.HEADER_C0], and [TxConst.HEADER_FF] each have their own reader.
     */
    private fun parseTransmissionKotlin(frame: ByteArray, mac: String): ScaleTransmission {
        if (frame.isEmpty()) {
            throw FrameFormatError("transmission is empty")
        }
        val raw = when (frame.u8(TxConst.HEADER)) {
            TxConst.HEADER_CA -> parseCa(frame)
            TxConst.HEADER_FF -> parseFf(frame)
            TxConst.HEADER_C0 -> parseC0(frame)
            else -> throw FrameFormatError("unknown transmission header")
        }
        return raw.toScaleTransmission(mac)
    }
}
