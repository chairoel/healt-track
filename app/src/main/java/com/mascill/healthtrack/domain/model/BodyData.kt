package com.mascill.healthtrack.domain.model

/**
 * Height, age, and sex required by a body-composition formula.
 * Sex is 0 for female and 1 for male.
 */
data class BodyData(
    val heightCm: Double,
    val age: Int,
    val sex: Int,
) {
    init {
        require(sex == 0 || sex == 1) { "sex must be 0 or 1" }
    }
}
