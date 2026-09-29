package com.mascill.healthtrack.domain.model

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
