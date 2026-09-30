package com.mascill.healthtrack.data.repository.gatt

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.mapper.toGattConfirmation
import com.mascill.healthtrack.data.mapper.toScaleGatt
import com.mascill.healthtrack.data.util.GIdx
import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.data.util.RIdx
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.GattConfirmation
import com.mascill.healthtrack.domain.model.ScaleGatt

/**
 * Native reader for an assembled [GattConst.CMD_LIVE] or [GattConst.CMD_HISTORY] body.
 * An error code other than [GattConst.ERR_OK] means the body is too short.
 */
internal fun nativeParseAssembled(body: ByteArray, command: Int): ScaleGatt {
    val values = NativeProtocol.nativeParseGattAssembled(body, command)
    if (values[GIdx.ERROR].toInt() != GattConst.ERR_OK) {
        throw FrameFormatError("assembled GATT is too short")
    }
    return values.toScaleGatt()
}

/**
 * Native reader for GATT version [GattConst.VERSION_16].
 * An error code other than [GattConst.ERR_OK] means the frame is too short.
 */
internal fun nativeParseV16(frame: ByteArray): ScaleGatt {
    val values = NativeProtocol.nativeParseGattV16(frame)
    if (values[GIdx.ERROR].toInt() != GattConst.ERR_OK) {
        throw FrameFormatError("GATT frame is too short")
    }
    return values.toScaleGatt()
}

/**
 * Native reader for an assembled [GattConst.CMD_ROLES] list.
 * An error code other than [GattConst.ERR_OK] means the body is too short.
 */
internal fun nativeParseRoles(body: ByteArray): GattConfirmation {
    val values = NativeProtocol.nativeParseGattRoles(body)
    if (values[RIdx.ERROR].toInt() != GattConst.ERR_OK) {
        throw FrameFormatError("assembled GATT roles are too short")
    }
    return values.toGattConfirmation()
}

/**
 * Native reader for GATT version [GattConst.VERSION_17], commands [GattConst.CMD_UNLOCKED] and [GattConst.CMD_LOCKED].
 * An error code other than [GattConst.ERR_OK] means the frame is too short.
 */
internal fun nativeParseV17Weight(frame: ByteArray, command: Int): ScaleGatt {
    val values = NativeProtocol.nativeParseGattV17Weight(frame, command)
    if (values[GIdx.ERROR].toInt() != GattConst.ERR_OK) {
        throw FrameFormatError("GATT frame is too short")
    }
    return values.toScaleGatt()
}
