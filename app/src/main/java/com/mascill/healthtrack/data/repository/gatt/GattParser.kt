package com.mascill.healthtrack.data.repository.gatt

import com.mascill.healthtrack.data.util.GattConst
import com.mascill.healthtrack.data.util.u8
import com.mascill.healthtrack.domain.model.FrameFormatError
import com.mascill.healthtrack.domain.model.GattConfirmation
import com.mascill.healthtrack.domain.model.GattResult
import com.mascill.healthtrack.domain.model.ScaleGatt
import com.mascill.healthtrack.domain.model.UnsupportedGatt

/**
 * Reassembles one GATT session.
 *
 * Version [GattConst.VERSION_16] and version-[GattConst.VERSION_17] commands [GattConst.CMD_UNLOCKED]
 * and [GattConst.CMD_LOCKED] are complete in a single frame.
 * [GattConst.CMD_LIVE] and [GattConst.CMD_HISTORY] collect packets until the packet count matches.
 * [GattConst.CMD_ROLES] appends role bytes. A frame that still needs more packets returns null.
 * [com.mascill.healthtrack.data.di.DataModule] supplies the instance kept by
 * [com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl.parse].
 * [com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl.parseGatt] builds a new one per call.
 */
class GattParser {
    private var live: MutableMap<Int, ByteArray>? = mutableMapOf()
    private var history: MutableMap<Int, ByteArray>? = mutableMapOf()
    private var roles: ByteArray? = byteArrayOf()

    /**
     * Reads one frame. Returns null while a split command is still missing packets.
     */
    fun parse(frame: ByteArray): GattResult? {
        if (frame.isEmpty()) {
            throw FrameFormatError("GATT frame is empty")
        }
        if (frame.u8(GattConst.HEADER) != GattConst.HEADER_CA) {
            throw FrameFormatError("GATT header is not 0xCA")
        }
        need(frame, GattConst.MIN_HEADER)
        val version = frame.u8(GattConst.VERSION)
        if (version == GattConst.VERSION_16) {
            return parseV16(frame)
        }
        if (version != GattConst.VERSION_17) {
            throw UnsupportedGatt("GATT version is outside this parser")
        }
        need(frame, GattConst.MIN_COMMAND)
        val command = frame.u8(GattConst.COMMAND)
        if (command == GattConst.CMD_UNLOCKED || command == GattConst.CMD_LOCKED) {
            return parseV17Weight(frame, command)
        }
        if (command == GattConst.CMD_LIVE || command == GattConst.CMD_HISTORY) {
            return absorb(frame, command)
        }
        if (command == GattConst.CMD_ROLES) {
            return absorbRoles(frame)
        }
        throw UnsupportedGatt("GATT command is outside this parser")
    }

    /**
     * Stores one piece of [GattConst.CMD_LIVE] or [GattConst.CMD_HISTORY].
     * Packet number [GattConst.FIRST_PACKET] clears the previous buffer. The assembled body is parsed only when
     * the last packet has arrived and every number from [GattConst.FIRST_PACKET] to the total is present.
     */
    private fun absorb(frame: ByteArray, command: Int): ScaleGatt? {
        need(frame, GattConst.MIN_PACKET)
        val payloadLen = javaByte(frame.u8(GattConst.LENGTH)) - GattConst.LENGTH_OVERHEAD
        if (payloadLen < GattConst.EMPTY_LEN) {
            throw FrameFormatError("GATT payload length is not valid")
        }
        need(frame, GattConst.PAYLOAD + payloadLen)
        val payload = frame.copyOfRange(GattConst.PAYLOAD, GattConst.PAYLOAD + payloadLen)
        val (total, number) = splitPack(frame.u8(GattConst.PACK))
        val current = if (command == GattConst.CMD_LIVE) live else history
        val store: MutableMap<Int, ByteArray> = if (number == GattConst.FIRST_PACKET) {
            (current ?: mutableMapOf()).also { it.clear() }
        } else {
            current ?: throw FrameFormatError("GATT buffer is empty")
        }
        store.remove(number)
        store[number] = payload
        if (command == GattConst.CMD_LIVE) {
            live = store
        } else {
            history = store
        }
        if (total != number) {
            return null
        }
        if (store.size != total) {
            store.clear()
            return null
        }
        val parsed = parseAssembled(concatPackets(store), command)
        if (command == GattConst.CMD_LIVE) {
            live = null
        } else {
            history = null
        }
        return parsed
    }

    /**
     * Appends one piece of [GattConst.CMD_ROLES]. The role list is parsed when the last packet arrives.
     */
    private fun absorbRoles(frame: ByteArray): GattConfirmation? {
        need(frame, GattConst.MIN_PACKET)
        val payloadLen = javaByte(frame.u8(GattConst.LENGTH)) - GattConst.LENGTH_OVERHEAD
        val payload = if (payloadLen > GattConst.EMPTY_LEN) {
            need(frame, GattConst.PAYLOAD + payloadLen)
            frame.copyOfRange(GattConst.PAYLOAD, GattConst.PAYLOAD + payloadLen)
        } else {
            ByteArray(GattConst.EMPTY_LEN)
        }
        val (total, number) = splitPack(frame.u8(GattConst.PACK))
        val buffer = (roles ?: ByteArray(GattConst.EMPTY_LEN)) + payload
        roles = buffer
        if (total != number) {
            return null
        }
        if (buffer.size < GattConst.ROLE_MIN) {
            throw FrameFormatError("assembled GATT roles are too short")
        }
        val parsed = parseRoles(buffer)
        roles = null
        return parsed
    }
}
