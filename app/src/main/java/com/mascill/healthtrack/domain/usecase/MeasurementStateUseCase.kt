package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementContext

private const val STATE_DONE = "selesai"
private const val STATE_WAIT_IMPEDANCE = "menunggu impedansi"
private const val STATE_WAIT_WEIGHT = "menunggu berat"

/**
 * `selesai` after a successful report, `menunggu impedansi` or `menunggu berat`
 * while an input is missing, otherwise `rumus` plus the formula code.
 */
class MeasurementStateUseCase {
    operator fun invoke(context: MeasurementContext): String {
        if (context.session.result != null) {
            return STATE_DONE
        }
        if (context.session.impedanceOhm == null) {
            return STATE_WAIT_IMPEDANCE
        }
        if (context.gattWeight == null && context.broadcastWeight == null) {
            return STATE_WAIT_WEIGHT
        }
        return "rumus ${context.session.code}"
    }
}
