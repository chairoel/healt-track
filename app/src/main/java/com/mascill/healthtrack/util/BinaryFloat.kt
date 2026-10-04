package com.mascill.healthtrack.util

import java.math.BigDecimal

private const val ZERO = 0.0

/**
 * Widens a binary32 rounding of [value] back to [Double].
 * Formula steps that Python stores as float32 use this.
 */
internal fun f32(value: Double): Double {
    val binary32 = value.toFloat()
    return binary32.toDouble()
}

/**
 * Fused multiply-add of [factor], [multiplicand], and [addend], rounded once to [Double].
 *
 * The exact product is a [BigDecimal] because [Math.fma] requires API 33 and minSdk is 28.
 * Any NaN argument returns NaN. Infinity times zero returns NaN.
 * When [addend] is zero and either factor is zero, the sign follows the floating-point sum.
 */
internal fun fma(factor: Double, multiplicand: Double, addend: Double): Double {
    val factorIsNaN = factor.isNaN()
    val multiplicandIsNaN = multiplicand.isNaN()
    val addendIsNaN = addend.isNaN()

    if (factorIsNaN || multiplicandIsNaN || addendIsNaN) {
        return Double.NaN
    }

    val factorIsInfinite = factor.isInfinite()
    val multiplicandIsInfinite = multiplicand.isInfinite()
    val addendIsInfinite = addend.isInfinite()

    if (factorIsInfinite || multiplicandIsInfinite || addendIsInfinite) {
        val factorIsZero = factor == ZERO
        val multiplicandIsZero = multiplicand == ZERO

        val infiniteTimesZero = (factorIsInfinite && multiplicandIsZero) ||
            (multiplicandIsInfinite && factorIsZero)

        if (infiniteTimesZero) {
            return Double.NaN
        }

        val product = factor * multiplicand
        val finiteOperandsOverflowed = product.isInfinite() &&
            !factorIsInfinite &&
            !multiplicandIsInfinite
        if (finiteOperandsOverflowed) {
            return addend
        }

        val infiniteSum = product + addend
        return infiniteSum
    }

    val exactFactor = BigDecimal(factor)
    val exactMultiplicand = BigDecimal(multiplicand)
    val exactProduct = exactFactor.multiply(exactMultiplicand)
    val addendIsZero = addend == ZERO
    if (addendIsZero) {
        val factorIsZero = factor == ZERO
        val multiplicandIsZero = multiplicand == ZERO
        if (factorIsZero || multiplicandIsZero) {
            val signedZero = factor * multiplicand + addend
            return signedZero
        }
        return exactProduct.toDouble()
    }

    val exactAddend = BigDecimal(addend)
    val exactSum = exactProduct.add(exactAddend)
    return exactSum.toDouble()
}
