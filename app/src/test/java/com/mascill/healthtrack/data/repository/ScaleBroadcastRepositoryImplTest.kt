package com.mascill.healthtrack.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleBroadcastRepositoryImplTest {
    private val repository = ScaleBroadcastRepositoryImpl()

    @Test
    fun broadcastReadsLockedKilograms() {
        val parsed = repository.parseBroadcast(lockedFrame, "AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", parsed.mac)
        assertEquals(70.5f, parsed.weightKg, 0f)
    }

    @Test
    fun advertisementReadsManufacturerPayload() {
        val record = byteArrayOf((lockedFrame.size + 1).toByte(), 0xFF.toByte()) + lockedFrame
        assertArrayEquals(lockedFrame, repository.scalePayload(record))
    }

    private companion object {
        val lockedFrame = byteArrayOf(
            0xC0.toByte(),
            0x07,
            0x02,
            0xC1.toByte(),
            0x13,
            0x88.toByte(),
            0x00,
            0x01,
            0x21,
        )
    }
}

