package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.MeasurementSession

private const val STATE_DONE = "selesai"
private const val STATE_WAIT_IMPEDANCE = "menunggu impedansi"
private const val STATE_WAIT_WEIGHT = "menunggu berat"

/**
 * `selesai` after a successful report, `menunggu impedansi` or `menunggu berat`
 * while an input is missing, otherwise `rumus` plus the formula code.
 */
class MeasurementStateUseCase {
    operator fun invoke(session: MeasurementSession): String {
        if (session.result != null) {
            return STATE_DONE
        }
        if (session.impedanceOhm == null) {
            return STATE_WAIT_IMPEDANCE
        }
        if (session.gattWeight == null && session.broadcastWeight == null) {
            return STATE_WAIT_WEIGHT
        }
        return "rumus ${session.code}"
    }
}
