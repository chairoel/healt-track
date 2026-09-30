package com.mascill.healthtrack.data.util

/** Index layout of the transmission `DoubleArray` from `nativeParseTransmission`. */
internal object TxIdx {
    const val ERROR = 0
    const val HEADER = 1
    const val VERSION = 2
    const val WEIGHT_KG = 3
    const val HAS_IMPEDANCE = 4
    const val IMPEDANCE = 5
    const val PRODUCT_ID = 6
    const val DEVICE_TYPE = 7
    const val CMD_ID = 8
    const val LOCKED = 9
    const val SEQ = 10
    const val PROPERTY = 11
    const val SW_MODE = 12
    const val W_HIGH = 13
    const val W_LOW = 14
    const val W_PLACES = 15
    const val W_UNIT = 16
    const val W_SCALED = 17
}
