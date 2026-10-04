package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementContext
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Returns a context that keeps one transmission. A locked frame with impedance replaces the
 * session impedance until a report has succeeded. An unlocked frame does not.
 */
class AcceptTransmissionUseCase(
    private val closeMeasurement: CloseMeasurementUseCase,
) {
    suspend operator fun invoke(context: MeasurementContext, frame: ScaleTransmission): MeasurementContext {
        val kept = context.copy(session = context.session.copy(transmission = frame))
        val ohms = frame.impedanceOhm
        if (kept.session.result == null && frame.locked && ohms != null) {
            return closeMeasurement(
                kept.copy(
                    session = kept.session.copy(impedanceOhm = ohms.toDouble()),
                    mac = frame.mac,
                    productId = frame.productId,
                ),
            )
        }
        return kept
    }
}
