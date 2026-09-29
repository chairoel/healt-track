package com.mascill.healthtrack.domain.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OkokAdvertisementTest {
    @Test
    fun payloadAfterFlags() {
        val record = record(frame, byteArrayOf(0x02, 0x01, 0x06))
        assertArrayEquals(frame, okokPayload(record))
    }

    @Test
    fun uuidBeforeManufacturerStillTaken() {
        val record = record(frame, byteArrayOf(0x03, 0x02, 0xF0.toByte(), 0xFF.toByte()))
        assertArrayEquals(frame, okokPayload(record))
    }

    @Test
    fun otherManufacturerSkipped() {
        val other = byteArrayOf(0x04, 0xFF.toByte(), 0x01, 0x02, 0x03)
        val record = other + record(frame)
        assertArrayEquals(frame, okokPayload(record))
    }

    @Test
    fun notOkok() {
        assertNull(okokPayload(byteArrayOf(0x04, 0xFF.toByte(), 0x01, 0x02, 0x03)))
    }

    @Test
    fun truncatedRecord() {
        assertNull(okokPayload(byteArrayOf(0x05, 0xFF.toByte(), 0xC0.toByte())))
    }

    @Test
    fun littleEndianManufacturerId() {
        val company = frame.u8(0) or (frame.u8(1) shl 8)
        assertArrayEquals(frame, payloadFromManufacturer(company, frame.copyOfRange(2, frame.size)))
        assertNull(payloadFromManufacturer(0x0001, byteArrayOf(0x02, 0x03)))
    }

    private fun record(payload: ByteArray, prefix: ByteArray = byteArrayOf()): ByteArray {
        return prefix + byteArrayOf((payload.size + 1).toByte(), 0xFF.toByte()) + payload
    }

    private companion object {
        val frame = byteArrayOf(
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
