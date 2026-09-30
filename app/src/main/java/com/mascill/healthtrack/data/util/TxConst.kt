package com.mascill.healthtrack.data.util

/**
 * Layout of a scale transmission frame.
 *
 * Offsets are named per header. The same index is a different field on `0xCA`, `0xC0`, and `0xFF`.
 * Lengths are the first byte after the field they measure.
 * Error codes match `SP_ERR_*` in `scale_protocol.h`.
 */
internal object TxConst {
    const val HEADER = 0

    const val HEADER_CA = 0xCA
    const val HEADER_C0 = 0xC0
    const val HEADER_FF = 0xFF

    const val VERSION_C0 = 1
    const val VERSION_16 = 16
    const val VERSION_17 = 17
    const val VERSION_V20 = 32

    const val DEVICE_NONE = 0
    const val DEVICE_CLOUD = 1
    const val DEVICE_BODY = 2

    const val CMD_OPEN = 0
    const val CMD_LOCKED = 1

    const val LOW_BYTE = 0xFF
    const val ENCRYPTED = 0x80
    const val LOCK_BIT = 7
    const val DEVICE_TYPE_BIT = 2
    const val TENTHS = 10.0

    const val ERR_EMPTY = 1
    const val ERR_UNKNOWN = 2
    const val ERR_SHORT = 3
    const val ERR_ENCRYPTED = 4

    const val TEXT_MISSING = 0
    const val TEXT_EMPTY = 2

    const val AD_MANUFACTURER = 0xFF
    const val AD_TYPE = 0
    const val AD_DATA = 1
    const val MIN_AD_BLOCK = 2
    const val MANUFACTURER_ID_MAX = 0xFFFF
    const val HIGH_BYTE_SHIFT = 8

    const val ONE_DECIMAL = 1
    const val TWO_DECIMALS = 2
    const val CLOUD_UNIT_KG = 0b00000
    const val CLOUD_UNIT_JIN = 0b00001
    const val CLOUD_UNIT_LB = 0b00010
    const val CLOUD_UNIT_ST = 0b00011
    const val CLOUD_PLACES_ONE = 0b001
    const val CLOUD_PLACES_TWO = 0b101
    const val CLOUD_PLACES_OTHER = 0b011
    const val CLOUD_PLACES_SHIFT = 3
    const val CLOUD_FORCED_PROPERTY = 4
    const val CLOUD_SENTINEL_ID = 997264800
    const val CLOUD_PRODUCT_SPECIAL = 1_104_000_001

    val CLOUD_SENTINEL = byteArrayOf(
        0xFA.toByte(),
        0xFB.toByte(),
        0xFC.toByte(),
        0xFD.toByte(),
    )

    const val CA_VERSION = 1
    const val CA_MIN = CA_VERSION + 1
    const val CA_V16_DEVICE = 3
    const val CA_V16_PROPERTY = 4
    const val CA_V17_PROPERTY = 3
    const val CA_V17_DEVICE = 4
    const val CA_LEGACY_MIN = CA_V16_PROPERTY + 1
    const val CA_WEIGHT_HIGH = 5
    const val CA_WEIGHT_LOW = 6
    const val CA_PRODUCT = 7
    const val CA_PRODUCT_LEN = 4
    const val CA_BODY_MIN = CA_PRODUCT + CA_PRODUCT_LEN
    const val CA_RAW_WEIGHT = 8
    const val CA_RAW_WEIGHT_LEN = 2
    const val CA_V16_RAW_MIN = CA_RAW_WEIGHT + CA_RAW_WEIGHT_LEN

    const val CA_V20_PRODUCT = 3
    const val CA_V20_PRODUCT_LEN = 4
    const val CA_V20_DEVICE = 7
    const val CA_V20_PROPERTY = 8
    const val CA_V20_SEQ = 9
    const val CA_V20_WEIGHT_HIGH = 10
    const val CA_V20_WEIGHT_LOW = 11
    const val CA_V20_IMPEDANCE = 12
    const val CA_V20_IMPEDANCE_LEN = 2
    const val CA_V20_MIN = CA_V20_IMPEDANCE + CA_V20_IMPEDANCE_LEN

    const val C0_SEQ = 1
    const val C0_WEIGHT_HIGH = 2
    const val C0_WEIGHT_LOW = 3
    const val C0_IMPEDANCE = 4
    const val C0_IMPEDANCE_LEN = 2
    const val C0_PRODUCT = 6
    const val C0_PRODUCT_LEN = 2
    const val C0_PROPERTY = 8
    const val C0_MIN = C0_PROPERTY + 1

    const val FF_VERSION = 2
    const val FF_PROPERTY = 3
    const val FF_WEIGHT_LOW = 4
    const val FF_WEIGHT_HIGH = 5
    const val FF_PRODUCT = 6
    const val FF_PRODUCT_LEN = 4
    const val FF_MIN = FF_PRODUCT + FF_PRODUCT_LEN
}
