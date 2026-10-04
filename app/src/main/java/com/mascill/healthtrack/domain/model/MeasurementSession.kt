package com.mascill.healthtrack.domain.model

/**
 * One measurement in progress. The first report with code 0 is the one that is kept.
 * Presentation layer consumes this class. Internal BLE details live in [MeasurementContext].
 */
data class MeasurementSession(
    val body: BodyData,
    val transmission: ScaleTransmission? = null,
    val impedanceOhm: Double? = null,
    val code: Int? = null,
    val formula: String? = null,
    val result: MeasurementResult? = null,
    /**
     * Row id from [com.mascill.healthtrack.domain.repository.MeasurementRepository.save].
     * Null until a report with code 0 is stored.
     */
    val storedId: Int? = null,
    /**
     * Kilograms from the locked live GATT frame, or null when that frame has not arrived.
     */
    val weightKg: Double? = null,
)

/**
 * Internal state for domain/data layer. Wraps [MeasurementSession] with BLE implementation details.
 * A frame replaces this value through [copy]. Callers keep the context that comes back.
 */
data class MeasurementContext(
    val session: MeasurementSession,
    val mac: String = "",
    val productId: Int = 0,
    val gattWeight: GattResult.ScaleGatt? = null,
    val broadcastWeight: ScaleTransmission? = null,
)
