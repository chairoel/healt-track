package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.repository.MeasurementRepository

/**
 * Stores the first report whose code is 0.
 * Impedance comes from the locked transmission. Weight comes from the locked live GATT
 * frame, or from that transmission when no GATT weight is stored.
 * A missing [repository] skips save and leaves [MeasurementSession.storedId] null.
 */
class CloseMeasurementUseCase(
    private val calculateReport: CalculateReportUseCase = CalculateReportUseCase(),
    private val repository: MeasurementRepository? = null,
) {

    /**
     * Leaves [session] unchanged until both impedance and weight are present.
     * A later frame does not replace a stored report.
     */
    operator fun invoke(session: MeasurementSession) {
        if (session.result != null || session.impedanceOhm == null) {
            return
        }
        val (weightKg, scaleText) = weightNumbers(session) ?: return
        val ohms = session.impedanceOhm ?: return
        val outcome = calculateReport(session.body, weightKg, listOf(ohms))
        session.formula = outcome.formula
        session.code = outcome.code
        val report = outcome.report
        if (outcome.code != 0 || report == null) {
            return
        }
        val finished = MeasurementResult(
            mac = session.mac,
            productId = session.productId,
            weightKg = weightKg,
            scaleWeight = scaleText,
            impedanceOhm = listOf(ohms),
            formula = outcome.formula,
            code = outcome.code,
            report = report,
        )
        session.storedId = repository?.save(finished)
        session.result = finished
    }

    private fun weightNumbers(session: MeasurementSession): Pair<Double, String>? {
        val gatt = session.gattWeight
        if (gatt != null) {
            return gatt.weightKg to gatt.scaleWeight
        }
        val broadcast = session.broadcastWeight ?: return null
        return broadcast.weightKg.toDouble() to (broadcast.scaleWeight ?: "")
    }
}
