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
 * Version 16 and version-17 commands 0 and 1 are complete in a single frame.
 * Commands 18 and 19 collect live and history packets until the packet count matches.
 * Command 20 appends role bytes. A frame that still needs more packets returns null.
 * [com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl.parse] keeps one instance.
 * [com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl.parseGatt] builds a new one per call.
 */
internal class GattParser {
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
        throw UnsupportedGatt("GATT version 17 command is outside this parser")
    }

    /**
     * Stores one piece of command 18 or 19.
     * Packet number 1 clears the previous buffer. The assembled body is parsed only when
     * the last packet has arrived and every number from 1 to the total is present.
     */
    private fun absorb(frame: ByteArray, command: Int): ScaleGatt? {
        need(frame, GattConst.MIN_PACKET)
        val payloadLen = javaByte(frame.u8(GattConst.LENGTH)) - GattConst.LENGTH_OVERHEAD
        if (payloadLen < 0) {
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
     * Appends one piece of command 20. The role list is parsed when the last packet arrives.
     */
    private fun absorbRoles(frame: ByteArray): GattConfirmation? {
        need(frame, GattConst.MIN_PACKET)
        val payloadLen = javaByte(frame.u8(GattConst.LENGTH)) - GattConst.LENGTH_OVERHEAD
        val payload = if (payloadLen > 0) {
            need(frame, GattConst.PAYLOAD + payloadLen)
            frame.copyOfRange(GattConst.PAYLOAD, GattConst.PAYLOAD + payloadLen)
        } else {
            ByteArray(0)
        }
        val (total, number) = splitPack(frame.u8(GattConst.PACK))
        val buffer = (roles ?: ByteArray(0)) + payload
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
