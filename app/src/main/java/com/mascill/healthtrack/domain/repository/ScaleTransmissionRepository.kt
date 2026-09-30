package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Domain entry to scale advertisement records and transmission frames.
 */
interface ScaleTransmissionRepository {
    fun scalePayload(record: ByteArray): ByteArray?

    fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray?

    fun parseTransmission(frame: ByteArray, mac: String = ""): ScaleTransmission
}
