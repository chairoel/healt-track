package com.mascill.healthtrack.util

/**
 * Binary32 round trip and a fused multiply-add for body-composition formulas.
 */
internal fun f32(value: Double): Double = value.toFloat().toDouble()

internal fun fma(a: Double, b: Double, c: Double): Double = Math.fma(a, b, c)
