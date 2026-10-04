package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.ScaleSessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * One Bluetooth measurement.
 * Collecting the flow scans and connects. Cancelling the collection stops the radio.
 * When Bluetooth is off, the flow emits one status with [ScaleSessionStatus.bluetoothOff] and completes.
 */
fun interface MeasureScaleUseCase {
    /**
     * Emits status for [body] until the session ends or the collection is cancelled.
     */
    operator fun invoke(body: BodyData): Flow<ScaleSessionStatus>
}
