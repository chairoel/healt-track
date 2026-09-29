package com.mascill.healthtrack.data.repository

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.util.BrIdx
import com.mascill.healthtrack.data.util.bits
import com.mascill.healthtrack.data.util.bytesToInt
import com.mascill.healthtrack.data.util.digitPlaces
import com.mascill.healthtrack.data.util.f32
import com.mascill.healthtrack.data.util.mapWeight
import com.mascill.healthtrack.data.util.scaleText
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.data.util.unit
import com.mascill.healthtrack.data.util.unitNameFromCode
import com.mascill.healthtrack.domain.model.EncryptedBroadcastError
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.ScaleBroadcast
import com.mascill.healthtrack.domain.repository.ScaleBroadcastRepository
import javax.inject.Inject

class ScaleBroadcastRepositoryImpl @Inject constructor() : ScaleBroadcastRepository {
    override fun scalePayload(record: ByteArray): ByteArray? = readScalePayload(record)

    /**
     * Rebuild a 16-bit little-endian manufacturer id plus the remaining payload.
     * A `companyId` outside 0..65535 follows `int.to_bytes(2, "little")` and is rejected.
     */
    override fun payloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? {
        if (NativeProtocol.isLoaded) {
            return NativeProtocol.nativePayloadFromManufacturer(companyId, remainder)
        }
        return ktPayloadFromManufacturer(companyId, remainder)
    }

    override fun parseBroadcast(frame: ByteArray, mac: String): ScaleBroadcast {
        if (NativeProtocol.isLoaded) {
            return nativeParseBroadcast(frame, mac)
        }
        return ktParseBroadcast(frame, mac)
    }
}

/**
 * Parse weight bytes from a protocol frame.
 *
 * @param high High byte of weight
 * @param low Low byte of weight
 * @param prop Property byte containing unit and decimal info
 * @param cloud True if cloud protocol format
 * @param cloudV4 True if cloud V4 format
 * @return Pair of (weightKg as Float, scaleText)
 */
private fun parseWeight(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
    cloudV4: Boolean = false,
): Pair<Float, String> = mapWeight(high, low, prop, cloud, cloudV4)

private val scaleHeaders = intArrayOf(0xCA, 0xC0)

/**
 * First manufacturer payload that starts with `0xCA` or `0xC0`.
 * `record` is a raw scan record: length, type, then data.
 */
private fun readScalePayload(record: ByteArray): ByteArray? {
    if (NativeProtocol.isLoaded) {
        return NativeProtocol.nativeScalePayload(record)
    }
    return ktScalePayload(record)
}

/* ── Kotlin fallback (host JUnit) ── */

private fun ktScalePayload(record: ByteArray): ByteArray? {
    var index = 0
    while (index < record.size) {
        val length = record.u8(index)
        index += 1
        if (length == 0 || index + length > record.size) {
            return null
        }
        val block = record.copyOfRange(index, index + length)
        index += length
        if (block.isEmpty() || block.u8(0) != 0xFF || block.size < 2) {
            continue
        }
        val payload = block.copyOfRange(1, block.size)
        if (payload.u8(0) in scaleHeaders) {
            return payload
        }
    }
    return null
}

private fun ktPayloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray? {
    if (companyId !in 0..0xFFFF) {
        throw IllegalArgumentException("manufacturer id does not fit in 16 bits")
    }
    val payload = byteArrayOf(
        (companyId and 0xFF).toByte(),
        ((companyId ushr 8) and 0xFF).toByte(),
    ) + remainder
    if (payload.u8(0) in scaleHeaders) {
        return payload
    }
    return null
}

private fun lockBit(prop: Int): Boolean = bits(prop)[7] == '1'

private fun deviceTypeV2(prop: Int): Int = if (bits(prop)[2] == '0') 0 else 2

private fun div10(raw: Int): Double = f32(f32(raw.toDouble()) / f32(10.0))

private fun cloudProperty(prop: Int): Int {
    val unitName = unit(prop)
    val places = digitPlaces(prop, cloud = true, cloudV4 = false)
    val prefix = when (unitName) {
        "jin" -> "00001"
        "lb" -> "00010"
        "st" -> "00011"
        else -> "00000"
    }
    val suffix = when (places) {
        1 -> "001"
        2 -> "101"
        else -> "011"
    }
    return (prefix + suffix).toInt(2)
}

private fun need(frame: ByteArray, size: Int) {
    if (frame.size < size) {
        throw FrameFormatError("broadcast is too short")
    }
}

private fun fromWeight(
    frame: ByteArray,
    mac: String,
    version: Int,
    deviceType: Int,
    cmdId: Int,
    prop: Int,
    weightKg: Float,
    scaleWeight: String?,
    impedanceOhm: Float?,
    productId: Int,
    seq: Int,
    locked: Boolean,
): ScaleBroadcast {
    return ScaleBroadcast(
        header = frame.u8(0),
        version = version,
        mac = mac,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = impedanceOhm,
        productId = productId,
        deviceType = deviceType,
        cmdId = cmdId,
        locked = locked,
        seq = seq,
        property = prop and 0xFF,
    )
}

private fun parseCa(frame: ByteArray, mac: String): ScaleBroadcast {
    need(frame, 2)
    if (frame.u8(1) == 32) {
        return parseCaV20(frame, mac)
    }
    return parseCaLegacy(frame, mac)
}

private fun parseCaLegacy(frame: ByteArray, mac: String): ScaleBroadcast {
    need(frame, 5)
    val version = frame.u8(1)
    var deviceType = 0
    var prop = 0
    var cmdId = 0
    if (version == 16) {
        deviceType = frame.u8(3)
        prop = frame.u8(4)
        cmdId = prop
    } else if (version == 17) {
        prop = frame.u8(3)
        deviceType = frame.u8(4)
    }
    if (deviceType != 0) {
        need(frame, 11)
        val (weightKg, scaleWeight) = parseWeight(frame.u8(5), frame.u8(6), prop)
        return fromWeight(
            frame,
            mac,
            version = version,
            deviceType = deviceType,
            cmdId = if (lockBit(prop)) 1 else 0,
            prop = prop,
            weightKg = weightKg,
            scaleWeight = scaleWeight,
            impedanceOhm = null,
            productId = bytesToInt(frame.copyOfRange(7, 11)),
            seq = 0,
            locked = lockBit(prop),
        )
    }
    if (version == 16) {
        need(frame, 10)
        return fromWeight(
            frame,
            mac,
            version = version,
            deviceType = 0,
            cmdId = cmdId,
            prop = prop,
            weightKg = div10(bytesToInt(frame.copyOfRange(8, 10))).toFloat(),
            scaleWeight = null,
            impedanceOhm = null,
            productId = 0,
            seq = 0,
            locked = lockBit(prop),
        )
    }
    if (version != 17) {
        return fromWeight(
            frame,
            mac,
            version = version,
            deviceType = 0,
            cmdId = 0,
            prop = 0,
            weightKg = 0f,
            scaleWeight = null,
            impedanceOhm = null,
            productId = 0,
            seq = 0,
            locked = false,
        )
    }
    need(frame, 11)
    val (weightKg, scaleWeight) = parseWeight(frame.u8(5), frame.u8(6), prop)
    return fromWeight(
        frame,
        mac,
        version = 17,
        deviceType = 0,
        cmdId = if (lockBit(prop)) 1 else 0,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = null,
        productId = bytesToInt(frame.copyOfRange(7, 11)),
        seq = 0,
        locked = lockBit(prop),
    )
}

private fun parseCaV20(frame: ByteArray, mac: String): ScaleBroadcast {
    need(frame, 14)
    val prop = frame.u8(8)
    val locked = lockBit(prop)
    val deviceType = if (frame.u8(7) == 0) 0 else 2
    val weightKg: Float
    val scaleWeight: String
    val impedance: Float
    val seq: Int
    if (locked) {
        val parsed = parseWeight(frame.u8(10), frame.u8(11), prop)
        weightKg = parsed.first
        scaleWeight = parsed.second
        impedance = div10(bytesToInt(frame.copyOfRange(12, 14))).toFloat()
        seq = frame.u8(9)
    } else {
        weightKg = 0f
        scaleWeight = ""
        impedance = 0f
        seq = 0
    }
    return fromWeight(
        frame,
        mac,
        version = 32,
        deviceType = deviceType,
        cmdId = if (locked) 1 else 0,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = impedance,
        productId = bytesToInt(frame.copyOfRange(3, 7)),
        seq = seq,
        locked = locked,
    )
}

private fun parseC0(frame: ByteArray, mac: String): ScaleBroadcast {
    need(frame, 9)
    if (frame.u8(4) and 0x80 != 0) {
        throw EncryptedBroadcastError("encrypted 0xC0 broadcast is outside the first release")
    }
    val prop = frame.u8(8)
    val (weightKg, scaleWeight) = parseWeight(frame.u8(2), frame.u8(3), prop)
    return fromWeight(
        frame,
        mac,
        version = 1,
        deviceType = deviceTypeV2(prop),
        cmdId = if (lockBit(prop)) 1 else 0,
        prop = prop,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = div10(bytesToInt(frame.copyOfRange(4, 6))).toFloat(),
        productId = bytesToInt(frame.copyOfRange(6, 8)),
        seq = frame.u8(1),
        locked = lockBit(prop),
    )
}

private fun parseFf(frame: ByteArray, mac: String): ScaleBroadcast {
    need(frame, 10)
    val version = frame.u8(2)
    var prop = frame.u8(3)
    var product = bytesToInt(frame.copyOfRange(6, 10))
    if (frame.copyOfRange(6, 10)
            .contentEquals(byteArrayOf(0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte()))
    ) {
        product = 997264800
        prop = 4
    } else if (product == 1104000001) {
        prop = 4
    }
    val cloudV4 = version == 16 || version == 17
    val (weightKg, scaleWeight) = parseWeight(
        frame.u8(5),
        frame.u8(4),
        prop,
        cloud = !cloudV4,
        cloudV4 = cloudV4,
    )
    return fromWeight(
        frame,
        mac,
        version = version,
        deviceType = 1,
        cmdId = 0,
        prop = cloudProperty(prop),
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = null,
        productId = product,
        seq = 0,
        locked = false,
    )
}

private fun nativeParseBroadcast(frame: ByteArray, mac: String): ScaleBroadcast {
    val r = NativeProtocol.nativeParseBroadcast(frame)
    val errorCode = r[BrIdx.ERROR].toInt()
    when (errorCode) {
        1 -> throw FrameFormatError("broadcast is empty")
        2 -> throw FrameFormatError("unknown broadcast header")
        3 -> throw FrameFormatError("broadcast is too short")
        4 -> throw EncryptedBroadcastError("encrypted 0xC0 broadcast is outside the first release")
    }
    val swMode = r[BrIdx.SW_MODE].toInt()
    val scaleWeight = when (swMode) {
        0 -> null
        2 -> ""
        else -> {
            val wHigh = r[BrIdx.W_HIGH].toInt()
            val wLow = r[BrIdx.W_LOW].toInt()
            val wPlaces = r[BrIdx.W_PLACES].toInt()
            val wUnit = r[BrIdx.W_UNIT].toInt()
            val wScaled = r[BrIdx.W_SCALED]
            scaleText(wHigh, wLow, wScaled, wPlaces, unitNameFromCode(wUnit))
        }
    }
    val hasImpedance = r[BrIdx.HAS_IMPEDANCE].toInt() != 0
    return ScaleBroadcast(
        header = r[BrIdx.HEADER].toInt(),
        version = r[BrIdx.VERSION].toInt(),
        mac = mac,
        weightKg = r[BrIdx.WEIGHT_KG].toFloat(),
        scaleWeight = scaleWeight,
        impedanceOhm = if (hasImpedance) r[BrIdx.IMPEDANCE].toFloat() else null,
        productId = r[BrIdx.PRODUCT_ID].toInt(),
        deviceType = r[BrIdx.DEVICE_TYPE].toInt(),
        cmdId = r[BrIdx.CMD_ID].toInt(),
        locked = r[BrIdx.LOCKED].toInt() != 0,
        seq = r[BrIdx.SEQ].toInt(),
        property = r[BrIdx.PROPERTY].toInt(),
    )
}

private fun ktParseBroadcast(frame: ByteArray, mac: String): ScaleBroadcast {
    if (frame.isEmpty()) {
        throw FrameFormatError("broadcast is empty")
    }
    return when (frame.u8(0)) {
        0xCA -> parseCa(frame, mac)
        0xFF -> parseFf(frame, mac)
        0xC0 -> parseC0(frame, mac)
        else -> throw FrameFormatError("unknown broadcast header")
    }
}
