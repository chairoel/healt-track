package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.protocol.GattParser
import com.mascill.healthtrack.data.protocol.parseBroadcast as parseBroadcastFrame
import com.mascill.healthtrack.data.protocol.parseGatt as parseGattFrame
import com.mascill.healthtrack.data.protocol.payloadFromManufacturer as payloadFromManufacturerFrame
import com.mascill.healthtrack.data.protocol.scalePayload as scalePayloadFrame
import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.ScaleBroadcast
import com.mascill.healthtrack.domain.repository.GattFrameReader
import com.mascill.healthtrack.domain.repository.ScaleProtocolRepository

class ScaleProtocolRepositoryImpl : ScaleProtocolRepository {
    override fun scalePayload(record: ByteArray): ByteArray? = scalePayloadFrame(record)

    override fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? =
        payloadFromManufacturerFrame(companyId, remainder)

    override fun parseBroadcast(frame: ByteArray, mac: String): ScaleBroadcast =
        parseBroadcastFrame(frame, mac)

    override fun parseGatt(frame: ByteArray): GattResult = parseGattFrame(frame)

    override fun createGattReader(): GattFrameReader = GattFrameReaderImpl()
}

private class GattFrameReaderImpl : GattFrameReader {
    private val parser = GattParser()

    override fun parse(frame: ByteArray): GattResult? = parser.parse(frame)
}
