package com.mascill.healthtrack.data.repository.gatt

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.mapper.toGattConfirmation
import com.mascill.healthtrack.data.mapper.toScaleGatt
import com.mascill.healthtrack.data.model.RawBody
import com.mascill.healthtrack.data.model.RawGattConfirmation
import com.mascill.healthtrack.data.model.RawGattMeasurement
import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.GattResult.Confirmation
import com.mascill.healthtrack.domain.model.GattResult.ScaleGatt

/**
 * Assembled body of command 18 (live) or 19 (history).
 * The property is byte 4. The weight is bytes 10..11.
 * Fat, water, muscle, BMR, visceral fat, and bone follow at bytes 12, 14, 16, 18, 20, and 22.
 * The role id is bytes 6..9. Command 19 also reads the weighing time from bytes 0..3.
 * Muscle is recalculated from the muscle mass and the weight.
 */
internal fun parseAssembled(body: ByteArray, command: Int): ScaleGatt {
    if (NativeProtocol.isLoaded) {
        return nativeParseAssembled(body, command)
    }
    return readAssembled(body, command).toScaleGatt()
}

private fun readAssembled(body: ByteArray, command: Int): RawGattMeasurement {
    if (body.size < GattConst.BODY_MIN) {
        throw FrameFormatError("assembled GATT is too short")
    }
    val prop = body.u8(GattConst.BODY_PROPERTY)
    val weighingDateEpoch = if (command == GattConst.CMD_HISTORY) {
        sliceInt(body, GattConst.BODY_DATE, GattConst.BODY_DATE_LEN).toLong()
    } else {
        null
    }
    return RawGattMeasurement(
        version = GattConst.VERSION_17,
        command = command,
        lockFlag = GattConst.CMD_LOCKED,
        property = prop,
        weightHigh = body.u8(GattConst.BODY_WEIGHT_HIGH),
        weightLow = body.u8(GattConst.BODY_WEIGHT_LOW),
        history = command == GattConst.CMD_HISTORY,
        body = RawBody(
            axunge = sliceInt(body, GattConst.BODY_AXUNGE, GattConst.FIELD_LEN),
            water = sliceInt(body, GattConst.BODY_WATER, GattConst.FIELD_LEN),
            muscle = sliceInt(body, GattConst.BODY_MUSCLE, GattConst.FIELD_LEN),
            bmr = sliceInt(body, GattConst.BODY_BMR, GattConst.FIELD_LEN),
            visceral = sliceInt(body, GattConst.BODY_VISCERAL, GattConst.FIELD_LEN),
            bone = sliceInt(body, GattConst.BODY_BONE, GattConst.BONE_LEN),
        ),
        roleId = sliceInt(body, GattConst.BODY_ROLE, GattConst.ID_LEN),
        weighingDateEpoch = weighingDateEpoch,
    )
}

/**
 * GATT version 16. The property is byte 4 and its low bit is the lock flag.
 * The weight is bytes 5..6. Composition bytes are read only when the lock flag is set.
 */
internal fun parseV16(frame: ByteArray): ScaleGatt {
    if (NativeProtocol.isLoaded) {
        return nativeParseV16(frame)
    }
    return readV16(frame).toScaleGatt()
}

private fun readV16(frame: ByteArray): RawGattMeasurement {
    need(frame, GattConst.V16_MIN)
    val prop = frame.u8(GattConst.V16_PROPERTY)
    val lockFlag = prop and GattConst.LOCK_MASK
    val body = if (lockFlag == GattConst.CMD_LOCKED) {
        RawBody(
            axunge = sliceInt(frame, GattConst.V16_AXUNGE, GattConst.FIELD_LEN),
            water = sliceInt(frame, GattConst.V16_WATER, GattConst.FIELD_LEN),
            muscle = sliceInt(frame, GattConst.V16_MUSCLE, GattConst.FIELD_LEN),
            bmr = sliceInt(frame, GattConst.V16_BMR, GattConst.FIELD_LEN),
            visceral = sliceInt(frame, GattConst.V16_VISCERAL, GattConst.FIELD_LEN),
            bone = sliceInt(frame, GattConst.V16_BONE, GattConst.BONE_LEN),
        )
    } else {
        null
    }
    return RawGattMeasurement(
        version = GattConst.VERSION_16,
        command = null,
        lockFlag = lockFlag,
        property = prop,
        weightHigh = frame.u8(GattConst.V16_WEIGHT_HIGH),
        weightLow = frame.u8(GattConst.V16_WEIGHT_LOW),
        body = body,
    )
}

/**
 * Assembled command 20. The weight is bytes 0..1 and the property is byte 2.
 * Each following role id is 4 bytes.
 */
internal fun parseRoles(body: ByteArray): Confirmation {
    if (NativeProtocol.isLoaded) {
        return nativeParseRoles(body)
    }
    return readRoles(body).toGattConfirmation()
}

private fun readRoles(body: ByteArray): RawGattConfirmation {
    if (body.size < GattConst.ROLE_MIN) {
        throw FrameFormatError("assembled GATT roles are too short")
    }
    val prop = body.u8(GattConst.ROLE_PROPERTY)
    val roles = ArrayList<Int>()
    var index = GattConst.ROLE_LIST
    while (index < body.size) {
        roles.add(sliceInt(body, index, GattConst.ID_LEN))
        index += GattConst.ID_LEN
    }
    return RawGattConfirmation(
        version = GattConst.VERSION_17,
        command = GattConst.CMD_ROLES,
        property = prop,
        weightHigh = body.u8(GattConst.ROLE_WEIGHT_HIGH),
        weightLow = body.u8(GattConst.ROLE_WEIGHT_LOW),
        matchedRoles = roles,
    )
}

/**
 * GATT version 17 commands 0 and 1, a weight without composition.
 * The weight is bytes 5..6 and the property is byte 11. The command is stored as the lock flag.
 */
internal fun parseV17Weight(frame: ByteArray, command: Int): ScaleGatt {
    if (NativeProtocol.isLoaded) {
        return nativeParseV17Weight(frame, command)
    }
    return readV17Weight(frame, command).toScaleGatt()
}

private fun readV17Weight(frame: ByteArray, command: Int): RawGattMeasurement {
    need(frame, GattConst.V17_WEIGHT_MIN)
    val prop = frame.u8(GattConst.V17_PROPERTY)
    return RawGattMeasurement(
        version = GattConst.VERSION_17,
        command = command,
        lockFlag = command,
        property = prop,
        weightHigh = frame.u8(GattConst.V17_WEIGHT_HIGH),
        weightLow = frame.u8(GattConst.V17_WEIGHT_LOW),
    )
}
