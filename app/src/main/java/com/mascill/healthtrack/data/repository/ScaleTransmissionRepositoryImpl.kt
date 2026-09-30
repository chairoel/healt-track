package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.repository.transmission.transmissionFromNative
import com.mascill.healthtrack.data.repository.transmission.ktPayloadFromManufacturer
import com.mascill.healthtrack.data.repository.transmission.ktScalePayload
import com.mascill.healthtrack.data.repository.transmission.parseTransmissionKotlin
import com.mascill.healthtrack.domain.model.ScaleTransmission
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository
import javax.inject.Inject

/**
 * Reads scale advertisement records and transmission frames.
 *
 * Android runs the readers in `libscale_protocol.so`. The JVM host, where that library
 * does not load, runs the Kotlin readers under `data.repository.transmission`.
 * [NativeProtocol.isLoaded] selects the path.
 */
class ScaleTransmissionRepositoryImpl @Inject constructor() : ScaleTransmissionRepository {
    override fun scalePayload(record: ByteArray): ByteArray? {
        if (NativeProtocol.isLoaded) {
            return NativeProtocol.nativeScalePayload(record)
        }
        return ktScalePayload(record)
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
}
