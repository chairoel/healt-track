package com.mascill.healthtrack.data.repository.transmission

import com.mascill.healthtrack.data.datasource.NativeProtocol
import com.mascill.healthtrack.data.util.TxConst
import com.mascill.healthtrack.data.util.TxIdx
import com.mascill.healthtrack.data.util.scaleText
import com.mascill.healthtrack.data.util.unitNameFromCode
import com.mascill.healthtrack.domain.model.EncryptedTransmissionError
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.ScaleTransmission

/**
 * Maps the 18 doubles from [NativeProtocol.nativeParseTransmission] to [ScaleTransmission].
 *
 * Error codes are [TxConst.ERR_EMPTY], [TxConst.ERR_UNKNOWN], [TxConst.ERR_SHORT], and [TxConst.ERR_ENCRYPTED].
 * Scale-text mode 0 is a missing text, mode 2 is an empty string, and any other mode
 * builds the text in Kotlin from the numeric pieces. [mac] is the scan address.
 */
internal fun transmissionFromNative(frame: ByteArray, mac: String): ScaleTransmission {
    val r = NativeProtocol.nativeParseTransmission(frame)
    val errorCode = r[TxIdx.ERROR].toInt()
    when (errorCode) {
        TxConst.ERR_EMPTY -> throw FrameFormatError("transmission is empty")
        TxConst.ERR_UNKNOWN -> throw FrameFormatError("unknown transmission header")
        TxConst.ERR_SHORT -> throw FrameFormatError("transmission is too short")
        TxConst.ERR_ENCRYPTED -> throw EncryptedTransmissionError("encrypted 0xC0 transmission is outside the first release")
    }
    val swMode = r[TxIdx.SW_MODE].toInt()
    val scaleWeight = when (swMode) {
        TxConst.TEXT_MISSING -> null
        TxConst.TEXT_EMPTY -> ""
        else -> {
            val wHigh = r[TxIdx.W_HIGH].toInt()
            val wLow = r[TxIdx.W_LOW].toInt()
            val wPlaces = r[TxIdx.W_PLACES].toInt()
            val wUnit = r[TxIdx.W_UNIT].toInt()
            val wScaled = r[TxIdx.W_SCALED]
            scaleText(wHigh, wLow, wScaled, wPlaces, unitNameFromCode(wUnit))
        }
    }
    val hasImpedance = r[TxIdx.HAS_IMPEDANCE].toInt() != 0
    return ScaleTransmission(
        header = r[TxIdx.HEADER].toInt(),
        version = r[TxIdx.VERSION].toInt(),
        mac = mac,
        weightKg = r[TxIdx.WEIGHT_KG].toFloat(),
        scaleWeight = scaleWeight,
        impedanceOhm = if (hasImpedance) r[TxIdx.IMPEDANCE].toFloat() else null,
        productId = r[TxIdx.PRODUCT_ID].toInt(),
        deviceType = r[TxIdx.DEVICE_TYPE].toInt(),
        cmdId = r[TxIdx.CMD_ID].toInt(),
        locked = r[TxIdx.LOCKED].toInt() != 0,
        seq = r[TxIdx.SEQ].toInt(),
        property = r[TxIdx.PROPERTY].toInt(),
    )
}
