package com.mascill.healthtrack.domain.repository

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * One Bluetooth measurement session.
 * The interface has no platform types. Scanning and GATT stay in the data layer.
 */
interface ScaleSessionRepository {
    /**
     * Latest status. Collection stays open for the life of the process.
     */
    fun observe(): Flow<ScaleSessionStatus>

    /**
     * Starts a scan for [body].
     * Returns false when the radio is off. A later [start] replaces the previous session.
     */
    fun start(body: BodyData): Boolean

    /**
     * Stops scanning and disconnects. A stored [ScaleSessionStatus.measurementId] is kept.
     */
    fun stop()
}
