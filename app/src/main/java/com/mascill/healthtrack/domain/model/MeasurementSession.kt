package com.mascill.healthtrack.domain.model

/**
 * One measurement in progress. The first report with code 0 is the one that is kept.
 */
class MeasurementSession(
    val body: BodyData,
) {
    var transmission: ScaleTransmission? = null
        internal set

    var impedanceOhm: Double? = null
        internal set

    var code: Int? = null
        internal set

    var formula: String? = null
        internal set

    var result: MeasurementResult? = null
        internal set

    /**
     * Row id from [com.mascill.healthtrack.domain.repository.MeasurementRepository.save].
     * Null until a report with code 0 is stored.
     */
    var storedId: Int? = null
        internal set

    internal var mac: String = ""
    internal var productId: Int = 0
    internal var gattWeight: ScaleGatt? = null
    internal var broadcastWeight: ScaleTransmission? = null

    /**
     * Kilograms from the locked live GATT frame, or null when that frame has not arrived.
     */
    val weightKg: Double?
        get() = gattWeight?.weightKg
}
