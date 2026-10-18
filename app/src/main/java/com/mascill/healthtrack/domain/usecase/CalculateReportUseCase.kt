package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.FormulaOutcome
import com.mascill.healthtrack.domain.model.V235Report
import com.mascill.healthtrack.util.CsBiasV235
import com.mascill.healthtrack.util.CsBiasVA019
import com.mascill.healthtrack.util.MultiFrequencyReport

private const val SINGLE_IMPEDANCE = 1
private const val TEN_IMPEDANCES = 10
private const val FORMULA_V235 = "v235"
private const val FORMULA_VA019 = "vA019"

/**
 * Picks the report formula for one weight and its impedance values.
 * One ohm uses v235. Ten ohms use vA019. Any other count is rejected.
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
            TEN_IMPEDANCES -> {
                val parsed = CsBiasVA019.parseMultiFrequency(
                    sex = body.sex,
                    age = body.age,
                    heightCm = body.heightCm,
                    weightKg = weightKg,
                    impedanceOhm = impedanceOhm,
                )
                return FormulaOutcome(
                    formula = FORMULA_VA019,
                    code = parsed.result,
                    report = parsed.data?.toStoredReport(),
                )
            }
            else -> throw IllegalArgumentException("report impedance must be 1 or 10 values")
        }
    }

    private fun MultiFrequencyReport.toStoredReport(): V235Report {
        return V235Report(
            bfp = bfp,
            slm = slm,
            slp = slp,
            bwp = bwp,
            bmc = bmc,
            vfl = vfl,
            pp = pp,
            smm = smm,
            swt = swt,
            mc = mc,
            wc = wc,
            fc = fc,
            bmr = bmr,
            ma = ma,
            sbc = sbc,
        )
    }
}
