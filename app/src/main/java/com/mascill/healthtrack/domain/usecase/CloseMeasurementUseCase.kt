package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementContext
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.repository.MeasurementRepository

/**
 * Returns a context that stores the first report whose code is 0.
 * Impedance comes from the locked transmission. Weight comes from the locked live GATT
 * frame, or from that transmission when no GATT weight is stored.
 * A missing [repository] skips save and leaves [MeasurementSession.storedId] null.
 */
class CloseMeasurementUseCase(
    private val calculateReport: CalculateReportUseCase = CalculateReportUseCase(),
    private val repository: MeasurementRepository? = null,
) {

    /**
     * Returns [context] until both impedance and weight are present.
     * A later frame does not replace a stored report.
     */
    suspend operator fun invoke(context: MeasurementContext): MeasurementContext {
        if (context.session.result != null || context.session.impedanceOhm == null) {
            return context
        }
        val (weightKg, scaleText) = weightNumbers(context) ?: return context
        val ohms = context.session.impedanceOhm ?: return context
        val outcome = calculateReport(context.session.body, weightKg, listOf(ohms))
        val rated = context.copy(
            session = context.session.copy(
                formula = outcome.formula,
                code = outcome.code,
            ),
        )
        val report = outcome.report
        if (outcome.code != 0 || report == null) {
            return rated
        }
        val finished = MeasurementResult(
            mac = context.mac,
            productId = context.productId,
            weightKg = weightKg,
            scaleWeight = scaleText,
            impedanceOhm = listOf(ohms),
            formula = outcome.formula,
            code = outcome.code,
            report = report,
        )
        return rated.copy(
            session = rated.session.copy(
                storedId = repository?.save(finished),
                result = finished,
            ),
        )
    }

    private fun weightNumbers(context: MeasurementContext): Pair<Double, String>? {
        val gatt = context.gattWeight
        if (gatt != null) {
            return gatt.weightKg to gatt.scaleWeight
        }
        val broadcast = context.broadcastWeight ?: return null
        return broadcast.weightKg.toDouble() to (broadcast.scaleWeight ?: "")
    }
}
