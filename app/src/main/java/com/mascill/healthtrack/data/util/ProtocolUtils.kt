package com.mascill.healthtrack.data.util

import java.math.BigDecimal
import java.math.RoundingMode

private const val LOW_BYTE = 0xFF
private const val BYTE_SHIFT = 8
private const val BIT_COUNT = 8
private const val HIGH_BIT = 7
private const val LOW_BIT = 0
private const val BIT_MASK = 1
private const val INT_ABSENT = 0

// ── Byte utilities ──

fun Byte.u8(): Int = toInt() and LOW_BYTE

fun ByteArray.u8(index: Int): Int = this[index].u8()

fun bytesToInt(raw: ByteArray): Int {
    if (raw.isEmpty()) {
        return INT_ABSENT
    }
    var value = INT_ABSENT
    for (byte in raw) {
        value = (value shl BYTE_SHIFT) or byte.u8()
    }
    return value
}

internal fun bits(value: Int): String {
    val byte = value and LOW_BYTE
    return buildString(BIT_COUNT) {
        for (shift in HIGH_BIT downTo LOW_BIT) {
            append((byte shr shift) and BIT_MASK)
        }
    }
}

// ── Float32 emulation ──

fun f32(value: Double): Double = value.toFloat().toDouble()

fun roundHalfUp(value: Double, places: Int, asFloat32: Boolean): Double {
    val number = BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()
    return if (asFloat32) f32(number) else number
}

fun javaFloatText(value: Double): String = value.toFloat().toString()
