package com.mascill.healthtrack.data.datasource

internal object NativeProtocol {
    val isLoaded: Boolean = try {
        System.loadLibrary("scale_protocol")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /* ── Advertisement ── */

    @JvmStatic external fun nativeScalePayload(record: ByteArray): ByteArray?
    @JvmStatic external fun nativePayloadFromManufacturer(companyId: Int, remainder: ByteArray): ByteArray?

    /* ── Weight ── */

    /**
     * Returns [kg, places, unitCode, raw, scaled, high, low].
     */
    @JvmStatic external fun nativeWeightKilograms(
        high: Int, low: Int, prop: Int, cloud: Boolean, cloudV4: Boolean,
    ): DoubleArray

    /* ── Transmission ── */

    /**
     * Returns 18 doubles packed:
     * [0]=errorCode [1]=header [2]=version [3]=weightKg(float)
     * [4]=hasImpedance [5]=impedanceOhm(float) [6]=productId
     * [7]=deviceType [8]=cmdId [9]=locked [10]=seq [11]=property
     * [12]=scaleWeightMode(0=null,1=text,2=empty) [13]=weightHigh
     * [14]=weightLow [15]=weightPlaces [16]=weightUnitCode [17]=weightScaled
     */
    @JvmStatic external fun nativeParseTransmission(frame: ByteArray): DoubleArray

    /* ── GATT ── */

    /**
     * Returns 22 doubles:
     * [0]=errorCode [1]=version [2]=hasCommand [3]=command
     * [4]=lockFlag [5]=property [6]=weightKg [7]=history
     * [8]=axunge [9]=water [10]=muscle [11]=bmr
     * [12]=visceralFat [13]=oriVisceralFat [14]=bone [15]=roleId
     * [16]=hasWeighingDate [17]=weighingDateEpoch
     * [18]=weightHigh [19]=weightLow [20]=weightPlaces [21]=weightUnitCode
     */
    @JvmStatic external fun nativeParseGattV16(frame: ByteArray): DoubleArray
    @JvmStatic external fun nativeParseGattV17Weight(frame: ByteArray, command: Int): DoubleArray
    @JvmStatic external fun nativeParseGattAssembled(body: ByteArray, command: Int): DoubleArray

    /**
     * Returns 11+N doubles:
     * [0]=errorCode [1]=version [2]=command [3]=property [4]=weightKg
     * [5]=weightHigh [6]=weightLow [7]=weightPlaces [8]=weightUnitCode
     * [9]=weightScaled [10]=roleCount [11..]=roleIds
     */
    @JvmStatic external fun nativeParseGattRoles(body: ByteArray): DoubleArray

    /* ── Utility ── */

    @JvmStatic external fun nativeSplitPack(raw: Int): IntArray
    @JvmStatic external fun nativeJavaByte(value: Int): Int
}
