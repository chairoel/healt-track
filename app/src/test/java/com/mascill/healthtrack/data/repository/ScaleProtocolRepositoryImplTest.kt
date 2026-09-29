package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.protocol.parseBroadcast
import com.mascill.healthtrack.data.protocol.parseGatt
import com.mascill.healthtrack.data.protocol.scalePayload
import com.mascill.healthtrack.domain.model.IncompleteGatt
import com.mascill.healthtrack.domain.model.ScaleGatt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ScaleProtocolRepositoryImplTest {
    private val repository = ScaleProtocolRepositoryImpl()

    @Test
    fun broadcastMatchesParser() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val parsed = repository.parseBroadcast(lockedFrame, mac)
        assertEquals(parseBroadcast(lockedFrame, mac), parsed)
        assertEquals(70.5f, parsed.weightKg, 0f)
    }

    @Test
    fun advertisementMatchesParser() {
        val record = byteArrayOf((lockedFrame.size + 1).toByte(), 0xFF.toByte()) + lockedFrame
        assertArrayEquals(scalePayload(record), repository.scalePayload(record))
    }

    @Test
    fun singleGattFrameMatchesParser() {
        val frame = byteArrayOf(0xCA.toByte(), 16, 0, 0, 0x01, 0x02, 0xC1.toByte())
        val parsed = repository.parseGatt(frame) as ScaleGatt
        assertEquals(parseGatt(frame), parsed)
        assertEquals(70.5, parsed.weightKg, 0.0)
    }

    @Test
    fun gattReaderKeepsAnIncompletePacket() {
        val payload = ByteArray(23)
        val frame = byteArrayOf(0xCA.toByte(), 17, (payload.size + 2).toByte(), 18, 0x18) + payload
        val reader = repository.createGattReader()
        assertNull(reader.parse(frame))
        assertThrows(IncompleteGatt::class.java) {
            repository.parseGatt(frame)
        }
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
