package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.ScaleGatt

private const val LOCKED = 1

/**
 * Stores a locked live GATT frame as the session weight when no report has succeeded.
 * Composition fields on the frame are not the report. History frames are ignored.
 */
class AcceptGattUseCase(
    private val closeMeasurement: CloseMeasurementUseCase,
) {
    operator fun invoke(session: MeasurementSession, parsed: GattResult): GattResult {
        if (
            session.result == null &&
            parsed is ScaleGatt &&
            parsed.lockFlag == LOCKED &&
            !parsed.history
        ) {
            session.gattWeight = parsed
            closeMeasurement(session)
        }
        return parsed
    }
}
