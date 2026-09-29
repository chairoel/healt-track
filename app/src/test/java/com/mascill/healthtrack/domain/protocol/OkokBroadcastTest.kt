package com.mascill.healthtrack.domain.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun weightProperty(lock: Int = 1, digit: String = "10", unit: String = "kg", fat: Boolean = false): Int {
    val unitBits = mapOf("kg" to "00", "jin" to "01", "lb" to "10", "st" to "11").getValue(unit)
    val digitBits = mapOf("int" to "01", "10" to "00", "100" to "10").getValue(digit)
    val bits = "00${if (fat) 1 else 0}$unitBits$digitBits$lock"
    return bits.toInt(2)
}

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

class ParseWeightTest {
    @Test
    fun oneDecimalKilogram() {
        val (kg, text) = parseWeight(0x02, 0xC1, weightProperty())
        assertEquals(70.5f, kg, 0f)
        assertEquals("70.5", text)
    }

    @Test
    fun jinDividedByTwo() {
        val (kg, text) = parseWeight(0x00, 0x8C, weightProperty(unit = "jin"))
        assertEquals(7.0f, kg, 0f)
        assertEquals("14.0", text)
    }

    @Test
    fun wholePounds() {
        val (kg, text) = parseWeight(0x00, 0x64, weightProperty(digit = "int", unit = "lb"))
        assertEquals(45.0f, kg, 0f)
        assertEquals("100", text)
    }

    @Test
    fun stoneThroughFloat32() {
        val (kg, text) = parseWeight(1, 0, weightProperty(unit = "st"))
        assertEquals("1:0.0", text)
        assertEquals(6.3f, kg, 0f)
    }
}

class BroadcastTest {
    @Test
    fun lockedFatC0() {
        val frame = bytes(0xC0, 7, 0x02, 0xC1, 0x13, 0x88, 0x00, 0x01, 0x21)
        val parsed = parseBroadcast(frame, "AA:BB:CC:DD:EE:FF")
        assertEquals(0xC0, parsed.header)
        assertEquals(1, parsed.version)
        assertEquals("AA:BB:CC:DD:EE:FF", parsed.mac)
        assertEquals(70.5f, parsed.weightKg, 0f)
        assertEquals("70.5", parsed.scaleWeight)
        assertEquals(500.0f, parsed.impedanceOhm)
        assertEquals(1, parsed.productId)
        assertEquals(2, parsed.deviceType)
        assertEquals(7, parsed.seq)
        assertTrue(parsed.locked)
        assertEquals(1, parsed.cmdId)
    }

    @Test
    fun encryptedC0Rejected() {
        val shortFrame = bytes(0xC0, 1, 0, 0, 0x80, 0, 0, 0, 0x21)
        val longFrame = shortFrame + ByteArray(6)
        assertThrows(EncryptedBroadcastError::class.java) {
            parseBroadcast(shortFrame)
        }
        assertThrows(EncryptedBroadcastError::class.java) {
            parseBroadcast(longFrame)
        }
    }

    @Test
    fun lockedCaVersion32() {
        val frame = bytes(0xCA, 0x20, 0, 0, 0, 0, 42, 1, 0x21, 3, 0x02, 0xC1, 0x13, 0x88)
        val parsed = parseBroadcast(frame)
        assertEquals(32, parsed.version)
        assertEquals(42, parsed.productId)
        assertEquals(2, parsed.deviceType)
        assertEquals(70.5f, parsed.weightKg, 0f)
        assertEquals(500.0f, parsed.impedanceOhm)
        assertEquals(3, parsed.seq)
        assertTrue(parsed.locked)
    }

    @Test
    fun unlockedCaVersion32() {
        val frame = bytes(0xCA, 0x20, 0, 0, 0, 0, 42, 0, 0x20, 9, 0x02, 0xC1, 0x13, 0x88)
        val parsed = parseBroadcast(frame)
        assertEquals(0.0f, parsed.weightKg, 0f)
        assertEquals("", parsed.scaleWeight)
        assertEquals(0.0f, parsed.impedanceOhm)
        assertEquals(0, parsed.seq)
        assertFalse(parsed.locked)
        assertEquals(0, parsed.deviceType)
    }

    @Test
    fun caVersion17() {
        val frame = bytes(0xCA, 17, 0, 0x21, 2, 0x02, 0xC1, 0, 0, 1, 0)
        val parsed = parseBroadcast(frame)
        assertEquals(70.5f, parsed.weightKg, 0f)
        assertEquals(256, parsed.productId)
        assertEquals(2, parsed.deviceType)
        assertNull(parsed.impedanceOhm)
        assertTrue(parsed.locked)
    }

    @Test
    fun caVersion16BodyScale() {
        val frame = bytes(0xCA, 16, 0, 0, 0x21, 0, 0, 0, 0x02, 0x8F)
        val parsed = parseBroadcast(frame)
        assertEquals(65.5f, parsed.weightKg, 0f)
        assertNull(parsed.scaleWeight)
        assertEquals(0, parsed.productId)
        assertEquals(0x21, parsed.cmdId)
        assertEquals(0, parsed.deviceType)
        assertTrue(parsed.locked)
    }

    @Test
    fun otherCaVersionEmpty() {
        val parsed = parseBroadcast(bytes(0xCA, 1, 0, 0, 0))
        assertEquals(0.0f, parsed.weightKg, 0f)
        assertEquals(1, parsed.version)
    }

    @Test
    fun cloud() {
        val frame = bytes(0xFF, 0, 1, 0x04, 0xC1, 0x02, 0, 0, 0, 2)
        val parsed = parseBroadcast(frame)
        assertEquals(1, parsed.deviceType)
        assertEquals(70.5f, parsed.weightKg, 0f)
        assertEquals(2, parsed.productId)
        assertEquals(1, parsed.property)
        assertEquals(0, parsed.cmdId)
        assertNull(parsed.impedanceOhm)
    }

    @Test
    fun specialCloudProduct() {
        val frame = bytes(0xFF, 0, 1, 0, 0xE8, 0x03, 0xFA, 0xFB, 0xFC, 0xFD)
        val parsed = parseBroadcast(frame)
        assertEquals(997264800, parsed.productId)
        assertEquals(100.0f, parsed.weightKg, 0f)
        assertEquals("100.0", parsed.scaleWeight)
        assertEquals(1, parsed.property)
    }

    @Test
    fun rejected() {
        assertThrows(FrameFormatError::class.java) {
            parseBroadcast(byteArrayOf())
        }
        assertThrows(FrameFormatError::class.java) {
            parseBroadcast(bytes(0xAB))
        }
        assertThrows(FrameFormatError::class.java) {
            parseBroadcast(ByteArray(8))
        }
    }
}
