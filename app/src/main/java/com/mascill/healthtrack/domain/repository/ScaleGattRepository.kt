package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.GattResult

/**
 * Domain entry to GATT frames.
 * [parseGatt] starts a new buffer on every frame.
 * [parse] keeps packet buffers on this instance across calls.
 */
interface ScaleGattRepository {
    fun parseGatt(frame: ByteArray): GattResult

    fun parse(frame: ByteArray): GattResult?
}
