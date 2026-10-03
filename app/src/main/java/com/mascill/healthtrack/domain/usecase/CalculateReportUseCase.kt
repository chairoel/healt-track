package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.FormulaOutcome
import com.mascill.healthtrack.util.CsBiasV235

private const val SINGLE_IMPEDANCE = 1
private const val TEN_IMPEDANCES = 10
private const val FORMULA_V235 = "v235"

/**
 * Picks the report formula for one weight and its impedance values.
 * One ohm uses v235. Ten ohms stay closed until vA019. Any other count is rejected.
 */
class CalculateReportUseCase {
    /**
     * Returns the formula name, code, and report. A failed formula leaves the report empty.
     */
    operator fun invoke(
        body: BodyData,
        weightKg: Double,
        impedanceOhm: List<Double>,
    ): FormulaOutcome {
        when (impedanceOhm.size) {
            SINGLE_IMPEDANCE -> {
                val parsed = CsBiasV235.parseDefaultZ(
                    sex = body.sex,
                    age = body.age,
                    heightCm = body.heightCm,
                    weightKg = weightKg,
                    impedanceOhm = impedanceOhm[0],
                )
                return FormulaOutcome(
                    formula = FORMULA_V235,
                    code = parsed.code,
                    report = parsed.report,
                )
            }
            TEN_IMPEDANCES -> error("vA019 is not available")
            else -> throw IllegalArgumentException("report impedance must be 1 or 10 values")
        }
    }
}
