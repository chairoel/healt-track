package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.ScaleBroadcast

/**
 * Domain entry to scale advertisement, broadcast, and GATT frames.
 */
interface ScaleProtocolRepository {
    fun scalePayload(record: ByteArray): ByteArray?

    fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray?

    fun parseBroadcast(frame: ByteArray, mac: String = ""): ScaleBroadcast

    fun parseGatt(frame: ByteArray): GattResult

    /**
     * A reader that keeps GATT packet buffers across calls.
     * [parseGatt] starts a new buffer on every frame.
     */
    fun createGattReader(): GattFrameReader
}

interface GattFrameReader {
    fun parse(frame: ByteArray): GattResult?
}
