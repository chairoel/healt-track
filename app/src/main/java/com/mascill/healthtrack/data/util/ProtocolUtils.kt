package com.mascill.healthtrack.data.util

import java.math.BigDecimal
import java.math.RoundingMode

// ── Byte utilities ──

fun Byte.u8(): Int = toInt() and 0xFF

fun ByteArray.u8(index: Int): Int = this[index].u8()

fun bytesToInt(raw: ByteArray): Int {
    if (raw.isEmpty()) {
        return 0
    }
    var value = 0
    for (byte in raw) {
        value = (value shl 8) or byte.u8()
    }
    return value
}

internal fun bits(value: Int): String {
    val byte = value and 0xFF
    return buildString(8) {
        for (shift in 7 downTo 0) {
            append((byte shr shift) and 1)
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
