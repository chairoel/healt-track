package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.GattResult.ScaleGatt
import com.mascill.healthtrack.domain.model.MeasurementContext

private const val LOCKED = 1

/**
 * Returns a context that stores a locked live GATT frame as the weight when no report has succeeded.
 * Composition fields on the frame are not the report. History frames are ignored.
 */
class AcceptGattUseCase(
    private val closeMeasurement: CloseMeasurementUseCase,
) {
    suspend operator fun invoke(context: MeasurementContext, parsed: GattResult): MeasurementContext {
        if (
            context.session.result == null &&
            parsed is ScaleGatt &&
            parsed.lockFlag == LOCKED &&
            !parsed.history
        ) {
            return closeMeasurement(
                context.copy(
                    session = context.session.copy(weightKg = parsed.weightKg),
                    gattWeight = parsed,
                ),
            )
        }
        return context
    }
}
