package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementContext

/**
 * Returns a context closed from the locked transmission weight when no GATT weight is stored.
 * A GATT weight that is already stored stays.
 */
class ApplyBroadcastWeightUseCase(
    private val closeMeasurement: CloseMeasurementUseCase,
) {
    suspend operator fun invoke(context: MeasurementContext): MeasurementContext {
        if (context.session.result != null || context.gattWeight != null) {
            return context
        }
        val frame = context.session.transmission
        if (
            frame == null ||
            !frame.locked ||
            frame.weightKg <= 0f ||
            context.session.impedanceOhm == null
        ) {
            return context
        }
        return closeMeasurement(context.copy(broadcastWeight = frame))
    }
}
