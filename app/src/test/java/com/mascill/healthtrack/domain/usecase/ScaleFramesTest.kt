package com.mascill.healthtrack.domain.usecase

import com.mascill.healthtrack.data.di.DataModule
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.data.repository.gatt.GattParser
import com.mascill.healthtrack.data.util.toHex
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.util.CsBiasV235
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleFramesTest {
    private val body = BodyData(170.0, 30, 1)

    @Test
    fun advertisementThenGattClosesSession() {
        val session = MeasurementSession(body)
        val store = FrameMeasurementRepository()
        val frames = frames(session, store)
        val broadcast = broadcast()
        val gatt = gattFrame()
        val line = frames.onAdvertisement("AA:BB:CC:DD:EE:FF", broadcast)
        assertNotNull(line)
        assertTrue(line!!.contains("500"))
        assertTrue(frames.ready)
        assertEquals("GATT kunci 1 65.5 kg", frames.onGatt(gatt))
        assertEquals(65.5, session.result!!.weightKg, 0.0)
        assertEquals(listOf(500.0), session.result!!.impedanceOhm)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0).report,
            session.result!!.report,
        )
        assertEquals(broadcast.toHex(), frames.broadcastHex)
        assertEquals(gatt.toHex(), frames.gattHex)
        assertEquals(1, session.storedId)
    }

    @Test
    fun otherDeviceIgnored() {
        val session = MeasurementSession(body)
        val frames = frames(session, mac = "AA")
        assertNull(frames.onAdvertisement("BB", broadcast()))
        assertFalse(frames.ready)
        assertNotNull(frames.onAdvertisement("aa", broadcast()))
        assertEquals("AA", frames.address)
    }

    @Test
    fun connectionAllowedWhenBroadcastIsRead() {
        val session = MeasurementSession(body)
        val frames = frames(session)
        val unlocked = bytes(0xC0, 7, 0x02, 0xC1, 0x13, 0x88, 0x00, 0x01, 0)
        frames.onAdvertisement("AA", unlocked)
        assertTrue(frames.canConnect("AA"))
        assertFalse(frames.canConnect("BB"))
        assertEquals("", frames.broadcastHex)
    }

    @Test
    fun nonConnectableBroadcastClosesWeight() {
        val session = MeasurementSession(body)
        val frames = frames(session)
        frames.onAdvertisement("AA", broadcast())
        assertNull(frames.closeFromBroadcast(connectable = true, connecting = false))
        assertNull(session.result)
        assertEquals(
            "berat dari siaran terkunci",
            frames.closeFromBroadcast(connectable = false, connecting = false),
        )
        assertEquals(session.transmission!!.weightKg.toDouble(), session.result!!.weightKg, 0.0)
        assertNull(frames.closeFromBroadcast(connectable = false, connecting = false))
    }

    @Test
    fun repeatedBroadcastIsQuiet() {
        val session = MeasurementSession(body)
        val frames = frames(session)
        assertNotNull(frames.onAdvertisement("AA", broadcast()))
        assertNull(frames.onAdvertisement("AA", broadcast()))
    }

    @Test
    fun encryptedBroadcastReportedOnce() {
        val session = MeasurementSession(body)
        val frames = frames(session)
        val frame = bytes(0xC0, 1, 0, 0, 0x80, 0, 0, 0, 0x21)
        val first = frames.onAdvertisement("AA", frame)
        assertNotNull(first)
        assertTrue(first!!.contains("siaran terenkripsi"))
        assertNull(frames.onAdvertisement("AA", frame))
        assertTrue(frames.ready)
        assertTrue(frames.encrypted)
        assertNull(session.result)
    }

    private fun frames(
        session: MeasurementSession,
        store: MeasurementRepository? = null,
        mac: String = "",
    ): ScaleFrames {
        val close = CloseMeasurementUseCase(repository = store)
        val transmissions = ScaleTransmissionRepositoryImpl(DataModule.scaleHeaders())
        val gatt = ScaleGattRepositoryImpl(GattParser())
        return ScaleFrames(
            session = session,
            transmissions = transmissions,
            gattFrames = gatt,
            acceptTransmission = AcceptTransmissionUseCase(close),
            acceptGatt = AcceptGattUseCase(close),
            applyBroadcastWeight = ApplyBroadcastWeightUseCase(close),
            mac = mac,
        )
    }
}

private class FrameMeasurementRepository : MeasurementRepository {
    val saved = mutableListOf<MeasurementResult>()

    override fun save(result: MeasurementResult): Int {
        saved += result
        return saved.size
    }

    override fun list(): List<StoredMeasurement> = emptyList()
}

private fun broadcast(): ByteArray {
    return bytes(0xC0, 7, 0x02, 0xC1, 0x13, 0x88, 0x00, 0x01, 1)
}

private fun gattFrame(): ByteArray {
    return bytes(0xCA, 17, 0, 1, 0, 0x02, 0x8F, 0, 0, 0, 0, 1)
}

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
