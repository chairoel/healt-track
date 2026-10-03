package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementSession

/**
 * Closes from the locked transmission weight when no GATT weight is stored.
 * A GATT weight that is already stored stays.
 */
class ApplyBroadcastWeightUseCase(
    private val closeMeasurement: CloseMeasurementUseCase,
) {
    /**
     * Returns whether [session] now holds a report.
     */
    operator fun invoke(session: MeasurementSession): Boolean {
        if (session.result != null || session.gattWeight != null) {
            return session.result != null
        }
        val frame = session.transmission
        if (
            frame == null ||
            !frame.locked ||
            frame.weightKg <= 0f ||
            session.impedanceOhm == null
        ) {
            return false
        }
        session.broadcastWeight = frame
        closeMeasurement(session)
        return session.result != null
    }
}
