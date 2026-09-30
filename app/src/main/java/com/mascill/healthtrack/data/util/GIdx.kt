package com.mascill.healthtrack.data.util

/** Index layout of the GATT measurement `DoubleArray` from the native GATT parsers. */
internal object GIdx {
    const val ERROR = 0
    const val VERSION = 1
    const val HAS_CMD = 2
    const val COMMAND = 3
    const val LOCK_FLAG = 4
    const val PROPERTY = 5
    const val WEIGHT_KG = 6
    const val HISTORY = 7
    const val AXUNGE = 8
    const val WATER = 9
    const val MUSCLE = 10
    const val BMR = 11
    const val VISCERAL = 12
    const val ORI_VISCERAL = 13
    const val BONE = 14
    const val ROLE_ID = 15
    const val HAS_DATE = 16
    const val DATE_EPOCH = 17
    const val W_HIGH = 18
    const val W_LOW = 19
    const val W_PLACES = 20
    const val W_UNIT = 21
}
