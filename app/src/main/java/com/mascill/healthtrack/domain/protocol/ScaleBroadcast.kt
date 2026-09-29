package com.mascill.healthtrack.domain.protocol

import java.math.BigDecimal
import java.math.RoundingMode

internal fun Byte.u8(): Int = toInt() and 0xFF

internal fun ByteArray.u8(index: Int): Int = this[index].u8()

open class FrameFormatError(message: String) : IllegalArgumentException(message)

class EncryptedBroadcastError(message: String) : FrameFormatError(message)

data class ScaleBroadcast(
    val header: Int,
    val version: Int,
    val mac: String,
    val weightKg: Float,
    val scaleWeight: String?,
    val impedanceOhm: Float?,
    val productId: Int,
    val deviceType: Int,
    val cmdId: Int,
    val locked: Boolean,
    val seq: Int,
    val property: Int,
)

private val lbPerKg: Double = 0.4535924.toFloat().toDouble()

private fun f32(value: Double): Double = value.toFloat().toDouble()

private fun bits(value: Int): String {
    val byte = value and 0xFF
    return buildString(8) {
        for (shift in 7 downTo 0) {
            append((byte shr shift) and 1)
        }
    }
}

private fun bytesToInt(raw: ByteArray): Int {
    if (raw.isEmpty()) {
        return 0
    }
    var value = 0
    for (byte in raw) {
        value = (value shl 8) or byte.u8()
    }
    return value
}

private fun roundHalfUp(value: Double, places: Int, asFloat32: Boolean): Double {
    val number = BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()
    return if (asFloat32) f32(number) else number
}

private fun unit(prop: Int): String {
    return when (bits(prop).substring(3, 5)) {
        "01" -> "jin"
        "10" -> "lb"
        "11" -> "st"
        else -> "kg"
    }
}

private fun digitPlaces(prop: Int, cloud: Boolean, cloudV4: Boolean): Int {
    if (cloudV4) {
        return when (bits(prop).substring(1, 3)) {
            "01" -> 0
            "10" -> 1
            else -> 2
        }
    }
    val pair = bits(prop).substring(5, 7)
    if (cloud) {
        return when (pair) {
            "01" -> 0
            "10" -> 1
            else -> 2
        }
    }
    return when (pair) {
        "01" -> 0
        "10" -> 2
        else -> 1
    }
}

private fun lockBit(prop: Int): Boolean = bits(prop)[7] == '1'

private fun deviceTypeV2(prop: Int): Int = if (bits(prop)[2] == '0') 0 else 2

private fun div10(raw: Int): Double = f32(f32(raw.toDouble()) / f32(10.0))

private fun javaFloatText(value: Double): String = java.lang.Float.toString(value.toFloat())

private fun scaleText(high: Int, low: Int, scaled: Double, places: Int, unit: String): String {
    if (unit == "st") {
        val pounds = f32((low and 0xFF).toDouble() / 10.0)
        return "${high and 0xFF}:${javaFloatText(pounds)}"
    }
    if (places == 0) {
        return f32(scaled).toInt().toString()
    }
    val shown = roundHalfUp(scaled, places, asFloat32 = true)
    return javaFloatText(shown)
}

internal fun weightKilograms(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
    cloudV4: Boolean = false,
): Pair<Double, String> {
    val places = digitPlaces(prop, cloud = cloud, cloudV4 = cloudV4)
    val raw = bytesToInt(byteArrayOf((high and 0xFF).toByte(), (low and 0xFF).toByte()))
    var scaled = when (places) {
        0 -> f32(raw.toDouble())
        1 -> f32(f32(raw.toDouble()) / f32(10.0))
        else -> f32(f32(raw.toDouble()) / f32(100.0))
    }
    scaled = roundHalfUp(scaled, places, asFloat32 = true)
    val unitName = unit(prop)
    val text = scaleText(high, low, scaled, places, unitName)
    var kg = when (unitName) {
        "jin" -> f32(scaled * f32(0.5))
        "lb" -> f32(scaled * lbPerKg)
        "st" -> {
            val stones = text.substringBefore(":")
            val pounds = text.substringAfter(":", "")
            val totalLb = try {
                (stones.toInt() * 14) + pounds.toDouble()
            } catch (error: NumberFormatException) {
                0.0
            }
            roundHalfUp(f32(f32(totalLb) * lbPerKg), 3, asFloat32 = true)
        }
        else -> scaled
    }
    kg = roundHalfUp(kg, places, asFloat32 = false)
    return kg to text
}

fun parseWeight(
    high: Int,
    low: Int,
    prop: Int,
    cloud: Boolean = false,
    cloudV4: Boolean = false,
): Pair<Float, String> {
    val (kg, text) = weightKilograms(high, low, prop, cloud, cloudV4)
    return kg.toFloat() to text
}

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
    if (frame.copyOfRange(6, 10).contentEquals(byteArrayOf(0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte()))) {
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

fun parseBroadcast(frame: ByteArray, mac: String = ""): ScaleBroadcast {
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
