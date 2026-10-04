package com.mascill.healthtrack.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryFloatTest {
    @Test
    fun fusedProductDiffersFromTwoRoundings() {
        val fused = fma(0.1, 0.1, -0.01)
        assertEquals(0x3c30a3d70a3d70a4L, fused.toRawBits())
        assertNotEquals(fused.toRawBits(), (0.1 * 0.1 - 0.01).toRawBits())
    }

    @Test
    fun nonFiniteInputsStayDefined() {
        assertTrue(fma(Double.NaN, 1.0, 1.0).isNaN())
        assertTrue(fma(Double.POSITIVE_INFINITY, 0.0, 1.0).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, fma(Double.POSITIVE_INFINITY, 2.0, 1.0), 0.0)
        assertEquals(0.0.toRawBits(), fma(-0.0, 0.0, 0.0).toRawBits())
    }
}
