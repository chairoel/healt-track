package com.mascill.healthtrack.data.protocol

import com.mascill.healthtrack.data.mapper.weightKilograms
import com.mascill.healthtrack.data.util.GIdx
import com.mascill.healthtrack.data.util.RIdx
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.GattConfirmation
import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.IncompleteGatt
import com.mascill.healthtrack.domain.model.ScaleGatt
import com.mascill.healthtrack.domain.model.UnsupportedGatt
import java.time.Instant

private fun need(frame: ByteArray, size: Int) {
    if (frame.size < size) {
        throw FrameFormatError("GATT frame is too short")
    }
}

private fun javaByte(value: Int): Int {
    val unsigned = value and 0xFF
    return if (unsigned >= 128) unsigned - 256 else unsigned
}

private fun bytesToIntSafe(raw: ByteArray): Int {
    if (raw.isEmpty()) {
        return 0
    }
    var value = 0L
    for (byte in raw) {
        value = (value shl 8) or (byte.u8().toLong())
    }
    if (value > 0x7FFFFFFF) {
        return 0
    }
    return value.toInt()
}

private fun sliceInt(frame: ByteArray, start: Int, size: Int): Int {
    if (size <= 0 || frame.size < start + size) {
        return 0
    }
    return bytesToIntSafe(frame.copyOfRange(start, start + size))
}

private fun splitPack(raw: Int): Pair<Int, Int> {
    val signed = javaByte(raw)
    val low = javaByte((signed shl 4) and 0xFF)
    val total = javaByte(low shr 4)
    val number = javaByte(signed shr 4)
    return total to number
}

private fun storedWeight(kg: Double): Double = (kg * 10.0) / 10.0

private fun setAxunge(raw: Int): Double {
    if (raw >= 65535.0) {
        return 0.0
    }
    val value = raw / 10.0
    if (value >= 5.0 && value <= 45.0) {
        return value
    }
    return 0.0
}

private fun setWater(raw: Int): Double {
    val value = raw / 10.0
    if (value >= 100.0 || value < 20.0 || value > 85.0) {
        return 0.0
    }
    return value
}

private fun setMuscle(raw: Int): Double {
    val value = raw / 10.0
    if (value >= 100.0) {
        return 0.0
    }
    return value
}

private fun setBmr(raw: Int): Double {
    if (raw >= 65535.0) {
        return 0.0
    }
    if (raw >= 5000.0) {
        return raw / 10.0
    }
    return raw.toDouble()
}

private fun setVisceral(raw: Int): Pair<Double, Double> {
    var shown = raw / 10.0
    if (shown >= 100.0 || shown > 59.0) {
        shown = 0.0
    }
    return shown to raw.toDouble()
}

private fun setBone(raw: Int): Double {
    if (raw >= 65535.0) {
        return 0.0
    }
    val value = raw / 10.0
    if (value >= 1.0) {
        if (value <= 4.0) {
            return value
        }
        return 4.0
    }
    return 0.0
}

private fun recalculateMuscle(muscle: Double, weightKg: Double): Double {
    val ratio = when {
        weightKg == 0.0 && muscle == 0.0 -> Double.NaN
        weightKg == 0.0 -> if (muscle > 0.0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        else -> (muscle / weightKg) * 100.0
    }
    val value = if (ratio >= 50.0) {
        if (muscle >= 50.0) 50.0 else muscle
    } else {
        ratio
    }
    val raw = when {
        value.isNaN() -> 0
        value.isInfinite() -> throw ArithmeticException("int overflow")
        else -> (value * 10.0).toInt()
    }
    return setMuscle(raw)
}

private data class Composition(
    val axunge: Double,
    val water: Double,
    val muscle: Double,
    val bmr: Double,
    val visceral: Double,
    val ori: Double,
    val bone: Double,
)

private fun composition(frame: ByteArray): Composition {
    val (visceral, ori) = setVisceral(sliceInt(frame, 15, 2))
    return Composition(
        axunge = setAxunge(sliceInt(frame, 7, 2)),
        water = setWater(sliceInt(frame, 9, 2)),
        muscle = setMuscle(sliceInt(frame, 11, 2)),
        bmr = setBmr(sliceInt(frame, 13, 2)),
        visceral = visceral,
        ori = ori,
        bone = setBone(sliceInt(frame, 17, 1)),
    )
}

private fun result(
    version: Int,
    command: Int?,
    lockFlag: Int,
    prop: Int,
    weightKg: Double,
    scaleWeight: String,
    composition: Composition?,
    history: Boolean = false,
    roleId: Int = 0,
    weighingDate: Instant? = null,
): ScaleGatt {
    val values = composition ?: Composition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    return ScaleGatt(
        version = version,
        command = command,
        lockFlag = lockFlag,
        property = prop and 0xFF,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        history = history,
        axunge = values.axunge,
        water = values.water,
        muscle = values.muscle,
        bmr = values.bmr,
        visceralFat = values.visceral,
        oriVisceralFat = values.ori,
        bone = values.bone,
        roleId = roleId,
        weighingDate = weighingDate,
    )
}

private fun gattFromNative(r: DoubleArray): ScaleGatt {
    val wHigh = r[GIdx.W_HIGH].toInt()
    val wLow = r[GIdx.W_LOW].toInt()
    val prop = r[GIdx.PROPERTY].toInt()
    val (_, scaleWeight) = weightKilograms(wHigh, wLow, prop, cloud = false)
    return ScaleGatt(
        version = r[GIdx.VERSION].toInt(),
        command = if (r[GIdx.HAS_CMD].toInt() != 0) r[GIdx.COMMAND].toInt() else null,
        lockFlag = r[GIdx.LOCK_FLAG].toInt(),
        property = r[GIdx.PROPERTY].toInt(),
        weightKg = r[GIdx.WEIGHT_KG],
        scaleWeight = scaleWeight,
        history = r[GIdx.HISTORY].toInt() != 0,
        axunge = r[GIdx.AXUNGE],
        water = r[GIdx.WATER],
        muscle = r[GIdx.MUSCLE],
        bmr = r[GIdx.BMR],
        visceralFat = r[GIdx.VISCERAL],
        oriVisceralFat = r[GIdx.ORI_VISCERAL],
        bone = r[GIdx.BONE],
        roleId = r[GIdx.ROLE_ID].toInt(),
        weighingDate = if (r[GIdx.HAS_DATE].toInt() != 0) {
            Instant.ofEpochSecond(r[GIdx.DATE_EPOCH].toLong())
        } else null,
    )
}

private fun rolesFromNative(r: DoubleArray): GattConfirmation {
    val wHigh = r[RIdx.W_HIGH].toInt()
    val wLow = r[RIdx.W_LOW].toInt()
    val prop = r[RIdx.PROPERTY].toInt()
    val (_, scaleWeight) = weightKilograms(wHigh, wLow, prop, cloud = false)
    val roleCount = r[RIdx.ROLE_COUNT].toInt()
    val roles = ArrayList<Int>(roleCount)
    for (i in 0 until roleCount) {
        roles.add(r[RIdx.ROLES_START + i].toInt())
    }
    return GattConfirmation(
        version = r[RIdx.VERSION].toInt(),
        command = r[RIdx.COMMAND].toInt(),
        property = prop and 0xFF,
        weightKg = storedWeight(r[RIdx.WEIGHT_KG]),
        scaleWeight = scaleWeight,
        matchedRoles = roles,
    )
}

private fun concatPackets(store: Map<Int, ByteArray>): ByteArray {
    val parts = ArrayList<ByteArray>(store.size)
    for (index in 1..store.size) {
        val key = if (index < 128) index else index - 256
        val piece = store[key] ?: throw FrameFormatError("GATT packet is missing")
        parts.add(piece)
    }
    var total = 0
    for (part in parts) {
        total += part.size
    }
    val joined = ByteArray(total)
    var offset = 0
    for (part in parts) {
        part.copyInto(joined, offset)
        offset += part.size
    }
    return joined
}

private fun parseAssembled(body: ByteArray, command: Int): ScaleGatt {
    if (NativeProtocol.isLoaded) {
        return nativeParseAssembled(body, command)
    }
    return ktParseAssembled(body, command)
}

private fun nativeParseAssembled(body: ByteArray, command: Int): ScaleGatt {
    val r = NativeProtocol.nativeParseGattAssembled(body, command)
    val errorCode = r[GIdx.ERROR].toInt()
    if (errorCode != 0) throw FrameFormatError("assembled GATT is too short")
    return gattFromNative(r)
}

private fun ktParseAssembled(body: ByteArray, command: Int): ScaleGatt {
    if (body.size < 12) {
        throw FrameFormatError("assembled GATT is too short")
    }
    val prop = body.u8(4)
    val (kg, scaleWeight) = weightKilograms(body.u8(10), body.u8(11), prop, cloud = false)
    val weightKg = storedWeight(kg)
    val muscle = recalculateMuscle(setMuscle(sliceInt(body, 16, 2)), weightKg)
    val (visceral, ori) = setVisceral(sliceInt(body, 20, 2))
    val weighingDate = if (command == 19) {
        Instant.ofEpochSecond(sliceInt(body, 0, 4).toLong())
    } else {
        null
    }
    return result(
        version = 17,
        command = command,
        lockFlag = 1,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        composition = Composition(
            axunge = setAxunge(sliceInt(body, 12, 2)),
            water = setWater(sliceInt(body, 14, 2)),
            muscle = muscle,
            bmr = setBmr(sliceInt(body, 18, 2)),
            visceral = visceral,
            ori = ori,
            bone = setBone(sliceInt(body, 22, 1)),
        ),
        history = command == 19,
        roleId = sliceInt(body, 6, 4),
        weighingDate = weighingDate,
    )
}

private fun parseV16(frame: ByteArray): ScaleGatt {
    if (NativeProtocol.isLoaded) {
        return nativeParseV16(frame)
    }
    return ktParseV16(frame)
}

private fun nativeParseV16(frame: ByteArray): ScaleGatt {
    val r = NativeProtocol.nativeParseGattV16(frame)
    val errorCode = r[GIdx.ERROR].toInt()
    if (errorCode != 0) throw FrameFormatError("GATT frame is too short")
    return gattFromNative(r)
}

private fun ktParseV16(frame: ByteArray): ScaleGatt {
    need(frame, 7)
    val prop = frame.u8(4)
    val lockFlag = prop and 1
    val (kg, scaleWeight) = weightKilograms(frame.u8(5), frame.u8(6), prop, cloud = false)
    val composition = if (lockFlag > 0) composition(frame) else null
    return result(
        version = 16,
        command = null,
        lockFlag = lockFlag,
        prop = prop,
        weightKg = storedWeight(kg),
        scaleWeight = scaleWeight,
        composition = composition,
    )
}

private fun parseRoles(body: ByteArray): GattConfirmation {
    if (NativeProtocol.isLoaded) {
        return nativeParseRoles(body)
    }
    return ktParseRoles(body)
}

private fun nativeParseRoles(body: ByteArray): GattConfirmation {
    val r = NativeProtocol.nativeParseGattRoles(body)
    val errorCode = r[RIdx.ERROR].toInt()
    if (errorCode != 0) throw FrameFormatError("assembled GATT roles are too short")
    return rolesFromNative(r)
}

private fun ktParseRoles(body: ByteArray): GattConfirmation {
    if (body.size < 3) {
        throw FrameFormatError("assembled GATT roles are too short")
    }
    val prop = body.u8(2)
    val (kg, scaleWeight) = weightKilograms(body.u8(0), body.u8(1), prop, cloud = false)
    val roles = ArrayList<Int>()
    var index = 3
    while (index < body.size) {
        roles.add(sliceInt(body, index, 4))
        index += 4
    }
    return GattConfirmation(
        version = 17,
        command = 20,
        property = prop and 0xFF,
        weightKg = storedWeight(kg),
        scaleWeight = scaleWeight,
        matchedRoles = roles,
    )
}

private fun parseV17Weight(frame: ByteArray, command: Int): ScaleGatt {
    if (NativeProtocol.isLoaded) {
        return nativeParseV17Weight(frame, command)
    }
    return ktParseV17Weight(frame, command)
}

private fun nativeParseV17Weight(frame: ByteArray, command: Int): ScaleGatt {
    val r = NativeProtocol.nativeParseGattV17Weight(frame, command)
    val errorCode = r[GIdx.ERROR].toInt()
    if (errorCode != 0) throw FrameFormatError("GATT frame is too short")
    return gattFromNative(r)
}

private fun ktParseV17Weight(frame: ByteArray, command: Int): ScaleGatt {
    need(frame, 12)
    val prop = frame.u8(11)
    val (kg, scaleWeight) = weightKilograms(frame.u8(5), frame.u8(6), prop, cloud = false)
    return result(
        version = 17,
        command = command,
        lockFlag = command,
        prop = prop,
        weightKg = storedWeight(kg),
        scaleWeight = scaleWeight,
        composition = null,
    )
}

class GattParser {
    private var live: MutableMap<Int, ByteArray>? = mutableMapOf()
    private var history: MutableMap<Int, ByteArray>? = mutableMapOf()
    private var roles: ByteArray? = byteArrayOf()

    fun parse(frame: ByteArray): GattResult? {
        if (frame.isEmpty()) {
            throw FrameFormatError("GATT frame is empty")
        }
        if (frame.u8(0) != 0xCA) {
            throw FrameFormatError("GATT header is not 0xCA")
        }
        need(frame, 3)
        val version = frame.u8(1)
        if (version == 16) {
            return parseV16(frame)
        }
        if (version != 17) {
            throw UnsupportedGatt("GATT version is outside this parser")
        }
        need(frame, 4)
        val command = frame.u8(3)
        if (command == 0 || command == 1) {
            return parseV17Weight(frame, command)
        }
        if (command == 18 || command == 19) {
            return absorb(frame, command)
        }
        if (command == 20) {
            return absorbRoles(frame)
        }
        throw UnsupportedGatt("GATT version 17 command is outside this parser")
    }

    private fun absorb(frame: ByteArray, command: Int): ScaleGatt? {
        need(frame, 5)
        val payloadLen = javaByte(frame.u8(2)) - 2
        if (payloadLen < 0) {
            throw FrameFormatError("GATT payload length is not valid")
        }
        need(frame, 5 + payloadLen)
        val payload = frame.copyOfRange(5, 5 + payloadLen)
        val (total, number) = splitPack(frame.u8(4))
        val current = if (command == 18) live else history
        val store: MutableMap<Int, ByteArray> = if (number == 1) {
            (current ?: mutableMapOf()).also { it.clear() }
        } else {
            current ?: throw FrameFormatError("GATT buffer is empty")
        }
        store.remove(number)
        store[number] = payload
        if (command == 18) {
            live = store
        } else {
            history = store
        }
        if (total != number) {
            return null
        }
        if (store.size != total) {
            store.clear()
            return null
        }
        val parsed = parseAssembled(concatPackets(store), command)
        if (command == 18) {
            live = null
        } else {
            history = null
        }
        return parsed
    }

    private fun absorbRoles(frame: ByteArray): GattConfirmation? {
        need(frame, 5)
        val payloadLen = javaByte(frame.u8(2)) - 2
        val payload = if (payloadLen > 0) {
            need(frame, 5 + payloadLen)
            frame.copyOfRange(5, 5 + payloadLen)
        } else {
            ByteArray(0)
        }
        val (total, number) = splitPack(frame.u8(4))
        val buffer = (roles ?: ByteArray(0)) + payload
        roles = buffer
        if (total != number) {
            return null
        }
        if (buffer.size < 3) {
            throw FrameFormatError("assembled GATT roles are too short")
        }
        val parsed = parseRoles(buffer)
        roles = null
        return parsed
    }
}

fun parseGatt(frame: ByteArray): GattResult {
    val parsed = GattParser().parse(frame) ?: throw IncompleteGatt("GATT packet is incomplete")
    return parsed
}
