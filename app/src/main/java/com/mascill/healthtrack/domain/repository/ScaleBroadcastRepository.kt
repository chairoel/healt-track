package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.ScaleBroadcast

/**
 * Domain entry to scale advertisement records and broadcast frames.
 */
interface ScaleBroadcastRepository {
    fun scalePayload(record: ByteArray): ByteArray?

    fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray?

    fun parseBroadcast(frame: ByteArray, mac: String = ""): ScaleBroadcast
}
