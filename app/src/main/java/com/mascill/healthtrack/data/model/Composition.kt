package com.mascill.healthtrack.data.model

/**
 * Body-composition fields after the scale's display clamps.
 */
internal data class Composition(
    val axunge: Double = 0.0,
    val water: Double = 0.0,
    val muscle: Double = 0.0,
    val bmr: Double = 0.0,
    val visceral: Double = 0.0,
    val ori: Double = 0.0,
    val bone: Double = 0.0,
)
