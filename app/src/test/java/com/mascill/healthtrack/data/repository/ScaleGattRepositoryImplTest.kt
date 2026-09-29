package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.domain.model.IncompleteGatt
import com.mascill.healthtrack.domain.model.ScaleGatt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ScaleGattRepositoryImplTest {
    private val repository = ScaleGattRepositoryImpl()

    @Test
    fun singleGattFrameMatchesParser() {
        val frame = byteArrayOf(0xCA.toByte(), 16, 0, 0, 0x01, 0x02, 0xC1.toByte())
        val parsed = repository.parseGatt(frame) as ScaleGatt
        assertEquals(70.5, parsed.weightKg, 0.0)
    }

    @Test
    fun gattReaderKeepsAnIncompletePacket() {
        val payload = ByteArray(23)
        val frame = byteArrayOf(0xCA.toByte(), 17, (payload.size + 2).toByte(), 18, 0x18) + payload
        assertNull(repository.parse(frame))
        assertThrows(IncompleteGatt::class.java) {
            repository.parseGatt(frame)
        }
    }
}
