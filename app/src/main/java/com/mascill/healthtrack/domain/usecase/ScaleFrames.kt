package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.domain.model.EncryptedTransmissionError
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.GattConfirmation
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.ScaleGatt
import com.mascill.healthtrack.domain.repository.ScaleGattRepository
import com.mascill.healthtrack.domain.repository.ScaleTransmissionRepository

private const val LOCKED = 1
private const val LOW_BYTE = 0xFF

/**
 * Applies one scale's advertisement payloads and GATT notifications to [session].
 * Parsing goes through [ScaleTransmissionRepository] and [ScaleGattRepository].
 */
internal class ScaleFrames(
    private val session: MeasurementSession,
    private val transmissions: ScaleTransmissionRepository,
    private val gattFrames: ScaleGattRepository,
    private val acceptTransmission: AcceptTransmissionUseCase,
    private val acceptGatt: AcceptGattUseCase,
    private val applyBroadcastWeight: ApplyBroadcastWeightUseCase,
    mac: String = "",
) {
    /**
     * Radio address of the first accepted scale. A preset [mac] is kept as-is.
     */
    var address: String = mac
        private set

    /**
     * True after the first accepted broadcast, including an encrypted one.
     */
    var ready: Boolean = false
        private set

    /**
     * Hex of the locked broadcast that carried impedance, or empty when that frame has not arrived.
     */
    var broadcastHex: String = ""
        private set

    /**
     * Hex of the locked live GATT frame, or empty when that frame has not arrived.
     */
    var gattHex: String = ""
        private set

    /**
     * Display text of the latest live weight, or null before any weight frame.
     */
    var liveWeightText: String? = null
        private set

    /**
     * True after an encrypted broadcast from this scale.
     */
    var encrypted: Boolean = false
        private set

    private val encryptedAddresses = mutableSetOf<String>()
    private var lastBroadcast: BroadcastSign? = null
    private var lastGatt: GattSign? = null
    private var lastGattError: String = ""

    /**
     * Accepts [payload] from [address].
     * Returns a line when the broadcast changed, or null when it is ignored or repeated.
     * Another address is ignored once [address] is set.
     */
    fun onAdvertisement(address: String, payload: ByteArray): String? {
        if (this.address.isNotEmpty() && !address.equals(this.address, ignoreCase = true)) {
            return null
        }
        val parsed = try {
            transmissions.parseTransmission(payload, address)
        } catch (error: EncryptedTransmissionError) {
            attach(address)
            encrypted = true
            if (!encryptedAddresses.add(address.lowercase())) {
                return null
            }
            return "$address siaran terenkripsi"
        } catch (error: FrameFormatError) {
            return null
        }
        acceptTransmission(session, parsed)
        attach(address)
        if (session.result == null && parsed.locked && parsed.impedanceOhm != null) {
            broadcastHex = payload.toHex()
        }
        liveWeightText = parsed.scaleWeight?.takeIf { it.isNotEmpty() } ?: parsed.weightKg.toString()
        val sign = BroadcastSign(parsed.locked, parsed.impedanceOhm, parsed.weightKg)
        if (sign == lastBroadcast) {
            return null
        }
        lastBroadcast = sign
        val lockText = if (parsed.locked) "kunci" else "terbuka"
        val ohmText = parsed.impedanceOhm?.let { "$it ohm" } ?: "tanpa impedansi"
        return "$address siaran $lockText ${parsed.weightKg} kg $ohmText"
    }

    /**
     * Accepts one notify [frame].
     * Returns a line when the frame is new, or null when it is incomplete, repeated, or ignored.
     */
    fun onGatt(frame: ByteArray): String? {
        val hadResult = session.result != null
        val parsed = try {
            gattFrames.parse(frame)
        } catch (error: FrameFormatError) {
            val text = error.message.orEmpty()
            if (text == lastGattError) {
                return null
            }
            lastGattError = text
            return "GATT diabaikan: $text"
        }
        if (parsed == null) {
            return null
        }
        acceptGatt(session, parsed)
        if (parsed is GattConfirmation) {
            return "GATT konfirmasi peran"
        }
        if (parsed !is ScaleGatt) {
            return null
        }
        if (!hadResult && parsed.lockFlag == LOCKED && !parsed.history) {
            gattHex = frame.toHex()
        }
        if (!parsed.history) {
            liveWeightText = parsed.scaleWeight
        }
        val sign = GattSign(parsed.lockFlag, parsed.history, parsed.weightKg)
        if (sign == lastGatt) {
            return null
        }
        lastGatt = sign
        val history = if (parsed.history) " riwayat" else ""
        return "GATT kunci ${parsed.lockFlag}$history ${parsed.scaleWeight} kg"
    }

    /**
     * True when [address] is the scale already accepted by this listener.
     * A locked impedance is not required.
     */
    fun canConnect(address: String): Boolean {
        return ready &&
            this.address.isNotEmpty() &&
            address.equals(this.address, ignoreCase = true)
    }

    /**
     * Closes from the locked broadcast when [connectable] is false and no link is up.
     * Returns `berat dari siaran terkunci` when that close stores a report.
     */
    fun closeFromBroadcast(connectable: Boolean, connecting: Boolean): String? {
        if (connectable || connecting || session.result != null) {
            return null
        }
        if (broadcastHex.isEmpty()) {
            return null
        }
        if (!applyBroadcastWeight(session)) {
            return null
        }
        return "berat dari siaran terkunci"
    }

    private fun attach(address: String) {
        if (this.address.isEmpty()) {
            this.address = address
        }
        ready = true
    }
}

/**
 * Fields that decide whether a broadcast line is new.
 */
private data class BroadcastSign(
    val locked: Boolean,
    val impedanceOhm: Float?,
    val weightKg: Float,
)

/**
 * Fields that decide whether a GATT line is new.
 */
private data class GattSign(
    val lockFlag: Int,
    val history: Boolean,
    val weightKg: Double,
)

/**
 * Lowercase hex of each byte, with no separator.
 */
private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and LOW_BYTE)
}
