package com.mascill.healthtrack.data.repository.gatt

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.util.GIdx
import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.data.util.RIdx
import com.mascill.healthtrack.data.util.weightKilograms
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.GattConfirmation
import com.mascill.healthtrack.domain.model.ScaleGatt
import java.time.Instant

/**
 * Maps a native GATT measurement array to [ScaleGatt].
 * The scale text is built in Kotlin. A non-zero error code is reported by the caller.
 */
internal fun gattFromNative(r: DoubleArray): ScaleGatt {
    val wHigh = r[GIdx.W_HIGH].toInt()
    val wLow = r[GIdx.W_LOW].toInt()
    val prop = r[GIdx.PROPERTY].toInt()
    val (_, scaleWeight) = weightKilograms(wHigh, wLow, prop, cloud = false)
    return ScaleGatt(
        version = r[GIdx.VERSION].toInt(),
        command = if (r[GIdx.HAS_CMD].toInt() != 0) r[GIdx.COMMAND].toInt() else null,
        lockFlag = r[GIdx.LOCK_FLAG].toInt(),
        property = r[GIdx.PROPERTY].toInt(),
        weightKg = r[GIdx.WEIGHT_KG],
        scaleWeight = scaleWeight,
        history = r[GIdx.HISTORY].toInt() != 0,
        axunge = r[GIdx.AXUNGE],
        water = r[GIdx.WATER],
        muscle = r[GIdx.MUSCLE],
        bmr = r[GIdx.BMR],
        visceralFat = r[GIdx.VISCERAL],
        oriVisceralFat = r[GIdx.ORI_VISCERAL],
        bone = r[GIdx.BONE],
        roleId = r[GIdx.ROLE_ID].toInt(),
        weighingDate = if (r[GIdx.HAS_DATE].toInt() != 0) {
            Instant.ofEpochSecond(r[GIdx.DATE_EPOCH].toLong())
        } else null,
    )
}

/**
 * Maps a native role-match array to [GattConfirmation].
 * Role ids start at [RIdx.ROLES_START] and run for [RIdx.ROLE_COUNT] entries.
 */
internal fun rolesFromNative(r: DoubleArray): GattConfirmation {
    val wHigh = r[RIdx.W_HIGH].toInt()
    val wLow = r[RIdx.W_LOW].toInt()
    val prop = r[RIdx.PROPERTY].toInt()
    val (_, scaleWeight) = weightKilograms(wHigh, wLow, prop, cloud = false)
    val roleCount = r[RIdx.ROLE_COUNT].toInt()
    val roles = ArrayList<Int>(roleCount)
    for (i in 0 until roleCount) {
        roles.add(r[RIdx.ROLES_START + i].toInt())
    }
    return GattConfirmation(
        version = r[RIdx.VERSION].toInt(),
        command = r[RIdx.COMMAND].toInt(),
        property = prop and GattConst.LOW_BYTE,
        weightKg = storedWeight(r[RIdx.WEIGHT_KG]),
        scaleWeight = scaleWeight,
        matchedRoles = roles,
    )
}

/**
 * Native reader for an assembled command-18 or command-19 body.
 * A non-zero error code means the body is too short.
 */
internal fun nativeParseAssembled(body: ByteArray, command: Int): ScaleGatt {
    val r = NativeProtocol.nativeParseGattAssembled(body, command)
    val errorCode = r[GIdx.ERROR].toInt()
    if (errorCode != GattConst.ERR_OK) throw FrameFormatError("assembled GATT is too short")
    return gattFromNative(r)
}

/**
 * Native reader for GATT version 16.
 * A non-zero error code means the frame is too short.
 */
internal fun nativeParseV16(frame: ByteArray): ScaleGatt {
    val r = NativeProtocol.nativeParseGattV16(frame)
    val errorCode = r[GIdx.ERROR].toInt()
    if (errorCode != GattConst.ERR_OK) throw FrameFormatError("GATT frame is too short")
    return gattFromNative(r)
}

/**
 * Native reader for an assembled command-20 role list.
 * A non-zero error code means the body is too short.
 */
internal fun nativeParseRoles(body: ByteArray): GattConfirmation {
    val r = NativeProtocol.nativeParseGattRoles(body)
    val errorCode = r[RIdx.ERROR].toInt()
    if (errorCode != GattConst.ERR_OK) throw FrameFormatError("assembled GATT roles are too short")
    return rolesFromNative(r)
}

/**
 * Native reader for GATT version 17 commands 0 and 1.
 * A non-zero error code means the frame is too short.
 */
internal fun nativeParseV17Weight(frame: ByteArray, command: Int): ScaleGatt {
    val r = NativeProtocol.nativeParseGattV17Weight(frame, command)
    val errorCode = r[GIdx.ERROR].toInt()
    if (errorCode != GattConst.ERR_OK) throw FrameFormatError("GATT frame is too short")
    return gattFromNative(r)
}
