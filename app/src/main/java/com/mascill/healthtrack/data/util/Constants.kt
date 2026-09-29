package com.mascill.healthtrack.data.util

object Constants {
    /**
     * Pounds in one kilogram. Passed through float32 first so the value
     * matches the scale protocol arithmetic.
     */
    const val LB_PER_KG: Double = 0.4535924.toFloat().toDouble()
}
