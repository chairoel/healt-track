package com.mascill.healthtrack.data.util

/**
 * Layout of a GATT frame and the clamps applied to body composition.
 *
 * Offsets are named per layout. Version 16, a version-17 weight frame, an assembled
 * body, and a role list do not share field positions.
 * Lengths are the first byte after the field they measure.
 */
internal object GattConst {
    const val HEADER = 0
    const val HEADER_CA = 0xCA

    const val VERSION = 1
    const val LENGTH = 2
    const val COMMAND = 3
    const val PACK = 4
    const val PAYLOAD = PACK + 1

    const val MIN_HEADER = LENGTH + 1
    const val MIN_COMMAND = COMMAND + 1
    const val MIN_PACKET = PAYLOAD

    const val VERSION_16 = 16
    const val VERSION_17 = 17

    const val CMD_UNLOCKED = 0
    const val CMD_LOCKED = 1
    const val CMD_LIVE = 18
    const val CMD_HISTORY = 19
    const val CMD_ROLES = 20

    const val LENGTH_OVERHEAD = 2
    const val FIRST_PACKET = 1
    const val NIBBLE_SHIFT = 4
    const val BYTE_SHIFT = 8
    const val LOW_BYTE = 0xFF
    const val SIGNED_BYTE = 128
    const val BYTE_SPAN = 256

    const val ERR_OK = 0
    const val FLAG_OFF = 0
    const val INT_ABSENT = 0
    const val EMPTY_LEN = 0

    const val LOCK_MASK = 1

    const val FIELD_LEN = 2
    const val BONE_LEN = 1
    const val ID_LEN = 4

    const val V16_PROPERTY = 4
    const val V16_WEIGHT_HIGH = 5
    const val V16_WEIGHT_LOW = 6
    const val V16_MIN = V16_WEIGHT_LOW + 1
    const val V16_AXUNGE = 7
    const val V16_WATER = V16_AXUNGE + FIELD_LEN
    const val V16_MUSCLE = V16_WATER + FIELD_LEN
    const val V16_BMR = V16_MUSCLE + FIELD_LEN
    const val V16_VISCERAL = V16_BMR + FIELD_LEN
    const val V16_BONE = V16_VISCERAL + FIELD_LEN

    const val V17_WEIGHT_HIGH = 5
    const val V17_WEIGHT_LOW = 6
    const val V17_PROPERTY = 11
    const val V17_WEIGHT_MIN = V17_PROPERTY + 1

    const val BODY_DATE = 0
    const val BODY_DATE_LEN = 4
    const val BODY_PROPERTY = 4
    const val BODY_ROLE = 6
    const val BODY_WEIGHT_HIGH = 10
    const val BODY_WEIGHT_LOW = 11
    const val BODY_MIN = BODY_WEIGHT_LOW + 1
    const val BODY_AXUNGE = 12
    const val BODY_WATER = BODY_AXUNGE + FIELD_LEN
    const val BODY_MUSCLE = BODY_WATER + FIELD_LEN
    const val BODY_BMR = BODY_MUSCLE + FIELD_LEN
    const val BODY_VISCERAL = BODY_BMR + FIELD_LEN
    const val BODY_BONE = BODY_VISCERAL + FIELD_LEN

    const val ROLE_WEIGHT_HIGH = 0
    const val ROLE_WEIGHT_LOW = 1
    const val ROLE_PROPERTY = 2
    const val ROLE_LIST = ROLE_PROPERTY + 1
    const val ROLE_MIN = ROLE_LIST

    const val RAW_ABSENT = 65535
    const val TENTHS = 10.0
    const val FULL_PERCENT = 100.0
    const val AXUNGE_MIN = 5.0
    const val AXUNGE_MAX = 45.0
    const val WATER_MIN = 20.0
    const val WATER_MAX = 85.0
    const val BMR_DIVIDED = 5000
    const val VISCERAL_MAX = 59.0
    const val BONE_MIN = 1.0
    const val BONE_MAX = 4.0
    const val MUSCLE_RATIO_CAP = 50.0
    const val CLEARED = 0.0
}
