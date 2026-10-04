package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.repository.gatt.GattParser
import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.IncompleteGatt
import com.mascill.healthtrack.domain.repository.ScaleGattRepository
import javax.inject.Inject

/**
 * Reads GATT frames.
 *
 * [parseGatt] builds a new [GattParser] for every frame and rejects a packet that is still incomplete.
 * [parse] keeps the [GattParser] from [com.mascill.healthtrack.data.di.DataModule]
 * so split commands 18, 19, and 20 can finish on a later call.
 * Android parses complete bodies in `libscale_protocol.so`. The JVM host reads frames
 * under `data.repository.gatt`. Both paths build the domain model in `data.mapper`.
 */
class ScaleGattRepositoryImpl @Inject constructor(
    private val parser: GattParser,
) : ScaleGattRepository {
    override fun parseGatt(frame: ByteArray): GattResult {
        val parsed = GattParser().parse(frame) ?: throw IncompleteGatt("GATT packet is incomplete")
        return parsed
    }

    override fun parse(frame: ByteArray): GattResult? = parser.parse(frame)

    override fun reset() {
        parser.reset()
    }
}
