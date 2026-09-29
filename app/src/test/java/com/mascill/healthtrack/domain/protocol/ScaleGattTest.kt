package com.mascill.healthtrack.domain.protocol

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun propGatt(lock: Int = 1, digit: String = "10", unit: String = "kg"): Int {
    val unitBits = mapOf("kg" to "00", "jin" to "01", "lb" to "10", "st" to "11").getValue(unit)
    val digitBits = mapOf("int" to "01", "10" to "00", "100" to "10").getValue(digit)
    return "000$unitBits$digitBits$lock".toInt(2)
}

private fun u16(value: Int): Pair<Int, Int> = ((value shr 8) and 0xFF) to (value and 0xFF)

private fun v16(
    prop: Int,
    weight: Pair<Int, Int> = 0x02 to 0xC1,
    axunge: Int = 225,
    water: Int = 550,
    muscle: Int = 400,
    bmr: Int = 1500,
    visceral: Int = 80,
    bone: Int = 25,
): ByteArray {
    val fatBytes = u16(axunge)
    val waterBytes = u16(water)
    val muscleBytes = u16(muscle)
    val metabolismBytes = u16(bmr)
    val visceralBytes = u16(visceral)
    return byteArrayOf(
        0xCA.toByte(),
        16,
        0,
        0,
        prop.toByte(),
        weight.first.toByte(),
        weight.second.toByte(),
        fatBytes.first.toByte(),
        fatBytes.second.toByte(),
        waterBytes.first.toByte(),
        waterBytes.second.toByte(),
        muscleBytes.first.toByte(),
        muscleBytes.second.toByte(),
        metabolismBytes.first.toByte(),
        metabolismBytes.second.toByte(),
        visceralBytes.first.toByte(),
        visceralBytes.second.toByte(),
        (bone and 0xFF).toByte(),
    )
}

private fun v17(command: Int, prop: Int, weight: Pair<Int, Int> = 0x02 to 0xC1): ByteArray {
    return byteArrayOf(
        0xCA.toByte(),
        17,
        0,
        command.toByte(),
        0,
        weight.first.toByte(),
        weight.second.toByte(),
        0,
        0,
        0,
        0,
        prop.toByte(),
    )
}

class GattV16Test {
    @Test
    fun lockedStoresPacketValues() {
        val parsed = parseGatt(v16(propGatt())) as ScaleGatt
        assertEquals(16, parsed.version)
        assertNull(parsed.command)
        assertEquals(1, parsed.lockFlag)
        assertEquals(propGatt(), parsed.property)
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals("70.5", parsed.scaleWeight)
        assertFalse(parsed.history)
        assertEquals(22.5, parsed.axunge, 0.0)
        assertEquals(55.0, parsed.water, 0.0)
        assertEquals(40.0, parsed.muscle, 0.0)
        assertEquals(1500.0, parsed.bmr, 0.0)
        assertEquals(8.0, parsed.visceralFat, 0.0)
        assertEquals(80.0, parsed.oriVisceralFat, 0.0)
        assertEquals(2.5, parsed.bone, 0.0)
        assertEquals(0, parsed.roleId)
        assertNull(parsed.weighingDate)
    }

    @Test
    fun muscleNotRecalculatedFromWeight() {
        val parsed = parseGatt(v16(propGatt(), muscle = 400)) as ScaleGatt
        assertEquals(40.0, parsed.muscle, 0.0)
        assertNotEquals((40.0 / parsed.weightKg) * 100.0, parsed.muscle, 0.0)
    }

    @Test
    fun unlockedWeightOnly() {
        val parsed = parseGatt(v16(propGatt(lock = 0))) as ScaleGatt
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals(0, parsed.lockFlag)
        assertEquals(0.0, parsed.axunge, 0.0)
        assertEquals(0.0, parsed.water, 0.0)
        assertEquals(0.0, parsed.muscle, 0.0)
        assertEquals(0.0, parsed.bmr, 0.0)
        assertEquals(0.0, parsed.visceralFat, 0.0)
        assertEquals(0.0, parsed.oriVisceralFat, 0.0)
        assertEquals(0.0, parsed.bone, 0.0)
    }

    @Test
    fun lockedWithoutCompositionBytes() {
        val prop = propGatt()
        val frame = byteArrayOf(0xCA.toByte(), 16, 0, 0, prop.toByte(), 0x02, 0xC1.toByte())
        val parsed = parseGatt(frame) as ScaleGatt
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals(0.0, parsed.axunge, 0.0)
        assertEquals(0.0, parsed.oriVisceralFat, 0.0)
        assertEquals(0.0, parsed.bone, 0.0)
    }

    @Test
    fun narrowingLimits() {
        val cases = listOf(
            Triple("axunge", 49, 0.0) to "axunge",
            Triple("axunge", 50, 5.0) to "axunge",
            Triple("axunge", 450, 45.0) to "axunge",
            Triple("axunge", 451, 0.0) to "axunge",
            Triple("axunge", 65535, 0.0) to "axunge",
            Triple("water", 199, 0.0) to "water",
            Triple("water", 200, 20.0) to "water",
            Triple("water", 850, 85.0) to "water",
            Triple("water", 851, 0.0) to "water",
            Triple("water", 1000, 0.0) to "water",
            Triple("muscle", 999, 99.9) to "muscle",
            Triple("muscle", 1000, 0.0) to "muscle",
            Triple("bmr", 4999, 4999.0) to "bmr",
            Triple("bmr", 5000, 500.0) to "bmr",
            Triple("bmr", 65535, 0.0) to "bmr",
            Triple("visceral", 590, 59.0) to "visceralFat",
            Triple("visceral", 591, 0.0) to "visceralFat",
            Triple("visceral", 25600, 0.0) to "visceralFat",
            Triple("bone", 9, 0.0) to "bone",
            Triple("bone", 10, 1.0) to "bone",
            Triple("bone", 40, 4.0) to "bone",
            Triple("bone", 41, 4.0) to "bone",
            Triple("bone", 255, 4.0) to "bone",
        )
        for ((sample, attr) in cases) {
            val (field, raw, expected) = sample
            val frame = when (field) {
                "axunge" -> v16(propGatt(), axunge = raw)
                "water" -> v16(propGatt(), water = raw)
                "muscle" -> v16(propGatt(), muscle = raw)
                "bmr" -> v16(propGatt(), bmr = raw)
                "visceral" -> v16(propGatt(), visceral = raw)
                "bone" -> v16(propGatt(), bone = raw)
                else -> error(field)
            }
            val parsed = parseGatt(frame) as ScaleGatt
            val actual = when (attr) {
                "axunge" -> parsed.axunge
                "water" -> parsed.water
                "muscle" -> parsed.muscle
                "bmr" -> parsed.bmr
                "visceralFat" -> parsed.visceralFat
                "bone" -> parsed.bone
                else -> error(attr)
            }
            assertEquals("$field $raw", expected, actual, 0.0)
        }
    }

    @Test
    fun rawVisceralFatKept() {
        val parsed = parseGatt(v16(propGatt(), visceral = 25600)) as ScaleGatt
        assertEquals(0.0, parsed.visceralFat, 0.0)
        assertEquals(25600.0, parsed.oriVisceralFat, 0.0)
    }
}

class GattV17Test {
    @Test
    fun command1WeightWithoutComposition() {
        val parsed = parseGatt(v17(1, propGatt(lock = 0))) as ScaleGatt
        assertEquals(17, parsed.version)
        assertEquals(1, parsed.command)
        assertEquals(1, parsed.lockFlag)
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals("70.5", parsed.scaleWeight)
        assertFalse(parsed.history)
        assertEquals(0.0, parsed.axunge, 0.0)
        assertEquals(0.0, parsed.oriVisceralFat, 0.0)
    }

    @Test
    fun command0StillReadsWeight() {
        val parsed = parseGatt(v17(0, propGatt(lock = 1, unit = "jin"), 0x00 to 0x8C)) as ScaleGatt
        assertEquals(0, parsed.command)
        assertEquals(0, parsed.lockFlag)
        assertEquals(7.0, parsed.weightKg, 0.0)
        assertEquals("14.0", parsed.scaleWeight)
        assertEquals(propGatt(lock = 1, unit = "jin"), parsed.property)
    }

    @Test
    fun propertyAtOffset11() {
        val parsed = parseGatt(v17(1, propGatt(unit = "jin"), 0x00 to 0x8C)) as ScaleGatt
        assertEquals(7.0, parsed.weightKg, 0.0)
        assertEquals("14.0", parsed.scaleWeight)
    }
}

class GattRejectedTest {
    @Test
    fun invalidFormat() {
        assertThrows(FrameFormatError::class.java) {
            parseGatt(byteArrayOf())
        }
        assertThrows(FrameFormatError::class.java) {
            parseGatt(byteArrayOf(0xC0.toByte(), 16, 0, 0, 1, 2, 3))
        }
        assertThrows(FrameFormatError::class.java) {
            parseGatt(byteArrayOf(0xCA.toByte(), 16, 0, 0, 1, 2))
        }
        assertThrows(FrameFormatError::class.java) {
            parseGatt(byteArrayOf(0xCA.toByte(), 17, 0, 1, 0, 2, 0xC1.toByte(), 0, 0, 0, 0))
        }
    }

    @Test
    fun outsideScope() {
        assertThrows(UnsupportedGatt::class.java) {
            parseGatt(byteArrayOf(0xCA.toByte(), 32, 0))
        }
        assertThrows(UnsupportedGatt::class.java) {
            parseGatt(v17(21, propGatt()))
        }
    }

    @Test
    fun invalidCommand18Payload() {
        assertThrows(FrameFormatError::class.java) {
            parseGatt(v17(18, propGatt()))
        }
        assertThrows(FrameFormatError::class.java) {
            parseGatt(v17(19, propGatt()))
        }
    }
}

private fun toBytesBig(value: Long, size: Int): ByteArray {
    val out = ByteArray(size)
    var remaining = value
    for (index in size - 1 downTo 0) {
        out[index] = (remaining and 0xFF).toByte()
        remaining = remaining ushr 8
    }
    return out
}

private fun body(
    prop: Int,
    weight: Pair<Int, Int> = 0x03 to 0x20,
    role: Long = 0x01020304,
    whenEpoch: Long = 1_700_000_000,
    muscle: Int = 100,
    axunge: Int = 225,
    water: Int = 550,
    bmr: Int = 1500,
    visceral: Int = 80,
    bone: Int = 25,
): ByteArray {
    return toBytesBig(whenEpoch, 4) +
        byteArrayOf(prop.toByte(), 0) +
        toBytesBig(role, 4) +
        byteArrayOf(weight.first.toByte(), weight.second.toByte()) +
        toBytesBig(axunge.toLong(), 2) +
        toBytesBig(water.toLong(), 2) +
        toBytesBig(muscle.toLong(), 2) +
        toBytesBig(bmr.toLong(), 2) +
        toBytesBig(visceral.toLong(), 2) +
        byteArrayOf((bone and 0xFF).toByte())
}

private fun packets(command: Int, payload: ByteArray, sizes: IntArray): List<ByteArray> {
    val frames = ArrayList<ByteArray>(sizes.size)
    var offset = 0
    val total = sizes.size
    for (ordinal in sizes.indices) {
        val size = sizes[ordinal]
        val number = ordinal + 1
        val chunk = payload.copyOfRange(offset, offset + size)
        offset += size
        val index = ((number and 0x0F) shl 4) or (total and 0x0F)
        frames.add(
            byteArrayOf(
                0xCA.toByte(),
                17,
                (size + 2).toByte(),
                command.toByte(),
                index.toByte(),
            ) + chunk,
        )
    }
    return frames
}

class GattAssemblyTest {
    @Test
    fun command18SinglePacket() {
        val payload = body(propGatt(lock = 0), muscle = 100)
        val parsed = parseGatt(packets(18, payload, intArrayOf(payload.size))[0]) as ScaleGatt
        assertEquals(18, parsed.command)
        assertEquals(1, parsed.lockFlag)
        assertFalse(parsed.history)
        assertNull(parsed.weighingDate)
        assertEquals(0x01020304, parsed.roleId)
        assertEquals(80.0, parsed.weightKg, 0.0)
        assertEquals("80.0", parsed.scaleWeight)
        assertEquals(22.5, parsed.axunge, 0.0)
        assertEquals(55.0, parsed.water, 0.0)
        assertEquals(12.5, parsed.muscle, 0.0)
        assertEquals(1500.0, parsed.bmr, 0.0)
        assertEquals(8.0, parsed.visceralFat, 0.0)
        assertEquals(80.0, parsed.oriVisceralFat, 0.0)
        assertEquals(2.5, parsed.bone, 0.0)
    }

    @Test
    fun muscleKeptWhenPercentNotBelow50() {
        val parsed = parseGatt(packets(18, body(propGatt(lock = 0), muscle = 400), intArrayOf(23))[0]) as ScaleGatt
        assertEquals(40.0, parsed.muscle, 0.0)
    }

    @Test
    fun muscleCappedAt50() {
        val parsed = parseGatt(packets(18, body(propGatt(lock = 0), muscle = 600), intArrayOf(23))[0]) as ScaleGatt
        assertEquals(50.0, parsed.muscle, 0.0)
    }

    @Test
    fun muscleTruncatedToTenths() {
        val parsed = parseGatt(
            packets(18, body(propGatt(lock = 0), 0x02 to 0xC1, muscle = 200), intArrayOf(23))[0],
        ) as ScaleGatt
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals(28.3, parsed.muscle, 0.0)
    }

    @Test
    fun command19TwoPackets() {
        val payload = body(propGatt(lock = 0), muscle = 100, visceral = 25600)
        val frames = packets(19, payload, intArrayOf(14, 9))
        val parser = GattParser()
        assertNull(parser.parse(frames[0]))
        val parsed = parser.parse(frames[1]) as ScaleGatt
        assertEquals(19, parsed.command)
        assertTrue(parsed.history)
        assertEquals(1, parsed.lockFlag)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), parsed.weighingDate)
        assertEquals(0x01020304, parsed.roleId)
        assertEquals(12.5, parsed.muscle, 0.0)
        assertEquals(0.0, parsed.visceralFat, 0.0)
        assertEquals(25600.0, parsed.oriVisceralFat, 0.0)
    }

    @Test
    fun lastPacketFirstClearsBuffer() {
        val frames = packets(18, body(propGatt(lock = 0)), intArrayOf(14, 9))
        val parser = GattParser()
        assertNull(parser.parse(frames[1]))
        assertNull(parser.parse(frames[0]))
        val parsed = parser.parse(frames[1]) as ScaleGatt
        assertEquals(0x01020304, parsed.roleId)
    }

    @Test
    fun packet1RestartsAssembly() {
        val firstAssembly = packets(18, body(propGatt(lock = 0), role = 1), intArrayOf(14, 9))
        val secondAssembly = packets(18, body(propGatt(lock = 0), role = 2), intArrayOf(14, 9))
        val parser = GattParser()
        assertNull(parser.parse(firstAssembly[0]))
        assertNull(parser.parse(secondAssembly[0]))
        val parsed = parser.parse(secondAssembly[1]) as ScaleGatt
        assertEquals(2, parsed.roleId)
    }

    @Test
    fun liveAndHistoryBuffersStaySeparate() {
        val liveFrame = packets(18, body(propGatt(lock = 0), role = 7), intArrayOf(23))[0]
        val historyFrames = packets(19, body(propGatt(lock = 0), role = 9), intArrayOf(14, 9))
        val parser = GattParser()
        assertNull(parser.parse(historyFrames[0]))
        val parsedLive = parser.parse(liveFrame) as ScaleGatt
        assertEquals(7, parsedLive.roleId)
        assertFalse(parsedLive.history)
        val parsedHistory = parser.parse(historyFrames[1]) as ScaleGatt
        assertEquals(9, parsedHistory.roleId)
        assertTrue(parsedHistory.history)
    }

    @Test
    fun nonFirstPacketRejectedAfterComplete() {
        val parser = GattParser()
        parser.parse(packets(18, body(propGatt(lock = 0)), intArrayOf(23))[0])
        val stray = packets(18, body(propGatt(lock = 0)), intArrayOf(14, 9))[1]
        assertThrows(FrameFormatError::class.java) {
            parser.parse(stray)
        }
    }

    @Test
    fun nibble8DoesNotComplete() {
        val payload = body(propGatt(lock = 0))
        val frame = byteArrayOf(0xCA.toByte(), 17, (payload.size + 2).toByte(), 18, 0x18) + payload
        assertNull(GattParser().parse(frame))
        assertThrows(IncompleteGatt::class.java) {
            parseGatt(frame)
        }
    }

    @Test
    fun roleIdAndTimeAboveInt() {
        val payload = body(propGatt(lock = 0), role = 0x80000000, whenEpoch = 0x80000000)
        val parsed = parseGatt(packets(19, payload, intArrayOf(23))[0]) as ScaleGatt
        assertEquals(0, parsed.roleId)
        assertEquals(Instant.ofEpochSecond(0), parsed.weighingDate)
    }

    @Test
    fun shortAssembly() {
        val frame = byteArrayOf(0xCA.toByte(), 17, 6, 18, 0x11, 1, 2, 3, 4)
        assertThrows(FrameFormatError::class.java) {
            parseGatt(frame)
        }
    }
}

private fun roleFrame(index: Int, payload: ByteArray): ByteArray {
    return byteArrayOf(0xCA.toByte(), 17, (payload.size + 2).toByte(), 20, index.toByte()) + payload
}

class GattRoleTest {
    @Test
    fun singlePacketRoleList() {
        val prop = propGatt()
        val payload = byteArrayOf(
            0x02,
            0xC1.toByte(),
            prop.toByte(),
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x02,
        )
        val parsed = parseGatt(roleFrame(0x11, payload)) as GattConfirmation
        assertEquals(17, parsed.version)
        assertEquals(20, parsed.command)
        assertEquals(prop, parsed.property)
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals("70.5", parsed.scaleWeight)
        assertEquals(listOf(1, 2), parsed.matchedRoles)
    }

    @Test
    fun twoPacketsKeepArrivalOrder() {
        val prop = propGatt(unit = "jin")
        val payload = byteArrayOf(0x00, 0x8C.toByte(), prop.toByte()) + toBytesBig(7, 4) + toBytesBig(9, 4)
        val frames = packets(20, payload, intArrayOf(7, 4))
        val parser = GattParser()
        assertNull(parser.parse(frames[0]))
        val parsed = parser.parse(frames[1]) as GattConfirmation
        assertEquals(7.0, parsed.weightKg, 0.0)
        assertEquals("14.0", parsed.scaleWeight)
        assertEquals(listOf(7, 9), parsed.matchedRoles)
    }

    @Test
    fun packet1DoesNotClearBuffer() {
        val prop = propGatt()
        val parser = GattParser()
        assertNull(parser.parse(roleFrame(0x12, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 1))))
        val parsed = parser.parse(roleFrame(0x11, byteArrayOf(0, 0, 0, 2))) as GattConfirmation
        assertEquals(listOf(1, 2), parsed.matchedRoles)
        assertEquals(70.5, parsed.weightKg, 0.0)
    }

    @Test
    fun lastPacketFirstStillCompletes() {
        val prop = propGatt()
        val parser = GattParser()
        val parsed = parser.parse(roleFrame(0x22, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 4))) as GattConfirmation
        assertEquals(listOf(4), parsed.matchedRoles)
    }

    @Test
    fun zeroIndexAndNibble8Complete() {
        val prop = propGatt()
        val payload = byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 3)
        for (index in intArrayOf(0x00, 0x88)) {
            val parsed = parseGatt(roleFrame(index, payload)) as GattConfirmation
            assertEquals("index $index", listOf(3), parsed.matchedRoles)
        }
    }

    @Test
    fun idThatDoesNotFitAndAboveInt() {
        val prop = propGatt()
        var payload = byteArrayOf(0x02, 0xC1.toByte(), prop.toByte()) + toBytesBig(0x7FFFFFFF, 4)
        payload += toBytesBig(0x80000000, 4) + byteArrayOf(1, 2, 3)
        val parsed = parseGatt(roleFrame(0x11, payload)) as GattConfirmation
        assertEquals(listOf(0x7FFFFFFF, 0, 0), parsed.matchedRoles)
    }

    @Test
    fun withoutRoleId() {
        val prop = propGatt()
        val parsed = parseGatt(roleFrame(0x11, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte()))) as GattConfirmation
        assertEquals(emptyList<Int>(), parsed.matchedRoles)
    }

    @Test
    fun shortAssemblyLeavesBuffer() {
        val prop = propGatt()
        val parser = GattParser()
        assertThrows(FrameFormatError::class.java) {
            parser.parse(roleFrame(0x11, byteArrayOf(0x02, 0xC1.toByte())))
        }
        val parsed = parser.parse(roleFrame(0x11, byteArrayOf(prop.toByte(), 0, 0, 0, 5))) as GattConfirmation
        assertEquals(70.5, parsed.weightKg, 0.0)
        assertEquals(listOf(5), parsed.matchedRoles)
    }

    @Test
    fun shortFrameDoesNotEnterBuffer() {
        val prop = propGatt()
        val parser = GattParser()
        assertThrows(FrameFormatError::class.java) {
            parser.parse(byteArrayOf(0xCA.toByte(), 17, 10, 20, 0x12, 1, 2))
        }
        val parsed = parser.parse(roleFrame(0x11, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 4))) as GattConfirmation
        assertEquals(listOf(4), parsed.matchedRoles)
    }

    @Test
    fun roleBufferSeparateFromMeasurement() {
        val prop = propGatt()
        val parser = GattParser()
        assertNull(parser.parse(roleFrame(0x12, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 8))))
        val liveResult = parser.parse(packets(18, body(propGatt(lock = 0), role = 7), intArrayOf(23))[0]) as ScaleGatt
        assertEquals(7, liveResult.roleId)
        val parsed = parser.parse(roleFrame(0x22, byteArrayOf(0, 0, 0, 6))) as GattConfirmation
        assertEquals(listOf(8, 6), parsed.matchedRoles)
    }

    @Test
    fun bufferEmptyAfterComplete() {
        val prop = propGatt()
        val parser = GattParser()
        parser.parse(roleFrame(0x11, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 1)))
        val parsed = parser.parse(roleFrame(0x11, byteArrayOf(0x02, 0xC1.toByte(), prop.toByte(), 0, 0, 0, 2))) as GattConfirmation
        assertEquals(listOf(2), parsed.matchedRoles)
    }

    @Test
    fun zeroLengthOnEmptyBuffer() {
        assertThrows(FrameFormatError::class.java) {
            parseGatt(byteArrayOf(0xCA.toByte(), 17, 0, 20, 0x11))
        }
    }
}
