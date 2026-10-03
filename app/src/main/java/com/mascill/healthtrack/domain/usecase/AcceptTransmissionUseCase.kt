package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Keeps one transmission on the session. A locked frame with impedance replaces the
 * session impedance until a report has succeeded. An unlocked frame does not.
 */
class AcceptTransmissionUseCase(
    private val closeMeasurement: CloseMeasurementUseCase,
) {
    operator fun invoke(session: MeasurementSession, frame: ScaleTransmission): ScaleTransmission {
        session.transmission = frame
        val ohms = frame.impedanceOhm
        if (session.result == null && frame.locked && ohms != null) {
            session.impedanceOhm = ohms.toDouble()
            session.mac = frame.mac
            session.productId = frame.productId
            closeMeasurement(session)
        }
        return frame
    }
}
