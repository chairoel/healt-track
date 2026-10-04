package com.mascill.healthtrack.data.mapper

import com.mascill.healthtrack.data.datasource.local.utcTimestamp
import com.mascill.healthtrack.data.model.StoredReport
import com.mascill.healthtrack.data.model.entity.MeasurementEntity
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.model.V235Report
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val storedJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Room row for a new [MeasurementResult]. Impedance and the report are JSON text.
 */
internal fun MeasurementResult.toEntity(createdAt: String = utcTimestamp()): MeasurementEntity =
    MeasurementEntity(
        createdAt = createdAt,
        mac = mac,
        productId = productId,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = storedJson.encodeToString(
            ListSerializer(Double.serializer()),
            impedanceOhm,
        ),
        formula = formula,
        code = code,
        report = storedJson.encodeToString(StoredReport.serializer(), report.toStoredReport()),
    )

/**
 * Domain row for [this] stored measurement.
 */
internal fun MeasurementEntity.toStoredMeasurement(): StoredMeasurement = StoredMeasurement(
    id = id.toInt(),
    createdAt = createdAt,
    result = MeasurementResult(
        mac = mac,
        productId = productId,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = storedJson.decodeFromString(
            ListSerializer(Double.serializer()),
            impedanceOhm,
        ),
        formula = formula,
        code = code,
        report = storedJson.decodeFromString(StoredReport.serializer(), report).toReport(),
    ),
)

private fun V235Report.toStoredReport(): StoredReport = StoredReport(
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

private fun StoredReport.toReport(): V235Report = V235Report(
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
