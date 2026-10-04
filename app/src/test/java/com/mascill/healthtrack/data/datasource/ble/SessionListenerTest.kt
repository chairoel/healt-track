package com.mascill.healthtrack.data.datasource.ble

import com.mascill.healthtrack.data.di.DataModule
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.data.repository.gatt.GattParser
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.MeasurementSession
import com.mascill.healthtrack.domain.model.StoredMeasurement
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.usecase.AcceptGattUseCase
import com.mascill.healthtrack.domain.usecase.AcceptTransmissionUseCase
import com.mascill.healthtrack.domain.usecase.ApplyBroadcastWeightUseCase
import com.mascill.healthtrack.domain.usecase.CloseMeasurementUseCase
import com.mascill.healthtrack.util.CsBiasV235
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListenerTest {
    private val body = BodyData(170.0, 30, 1)

    @Test
    fun advertisementThenGattClosesSession() {
        val session = MeasurementSession(body)
        val store = MemoryMeasurementRepository()
        val listener = listener(session, store)
        val broadcast = broadcast()
        val gatt = gattFrame()
        val line = listener.onAdvertisement("AA:BB:CC:DD:EE:FF", broadcast)
        assertNotNull(line)
        assertTrue(line!!.contains("500"))
        assertTrue(listener.ready)
        assertEquals("GATT kunci 1 65.5 kg", listener.onGatt(gatt))
        assertEquals(65.5, session.result!!.weightKg, 0.0)
        assertEquals(listOf(500.0), session.result!!.impedanceOhm)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0).report,
            session.result!!.report,
        )
        assertEquals(broadcast.hex(), listener.broadcastHex)
        assertEquals(gatt.hex(), listener.gattHex)
        assertEquals(1, session.storedId)
    }

    @Test
    fun otherDeviceIgnored() {
        val session = MeasurementSession(body)
        val listener = listener(session, mac = "AA")
        assertNull(listener.onAdvertisement("BB", broadcast()))
        assertFalse(listener.ready)
        assertNotNull(listener.onAdvertisement("aa", broadcast()))
        assertEquals("AA", listener.address)
    }

    @Test
    fun connectionAllowedWhenBroadcastIsRead() {
        val session = MeasurementSession(body)
        val listener = listener(session)
        val unlocked = bytes(0xC0, 7, 0x02, 0xC1, 0x13, 0x88, 0x00, 0x01, 0)
        listener.onAdvertisement("AA", unlocked)
        assertTrue(listener.canConnect("AA"))
        assertFalse(listener.canConnect("BB"))
        assertEquals("", listener.broadcastHex)
    }

    @Test
    fun nonConnectableBroadcastClosesWeight() {
        val session = MeasurementSession(body)
        val listener = listener(session)
        listener.onAdvertisement("AA", broadcast())
        assertNull(listener.closeFromBroadcast(connectable = true, connecting = false))
        assertNull(session.result)
        assertEquals(
            "berat dari siaran terkunci",
            listener.closeFromBroadcast(connectable = false, connecting = false),
        )
        assertEquals(session.transmission!!.weightKg.toDouble(), session.result!!.weightKg, 0.0)
        assertNull(listener.closeFromBroadcast(connectable = false, connecting = false))
    }

    @Test
    fun repeatedBroadcastIsQuiet() {
        val session = MeasurementSession(body)
        val listener = listener(session)
        assertNotNull(listener.onAdvertisement("AA", broadcast()))
        assertNull(listener.onAdvertisement("AA", broadcast()))
    }

    @Test
    fun encryptedBroadcastReportedOnce() {
        val session = MeasurementSession(body)
        val listener = listener(session)
        val frame = bytes(0xC0, 1, 0, 0, 0x80, 0, 0, 0, 0x21)
        val first = listener.onAdvertisement("AA", frame)
        assertNotNull(first)
        assertTrue(first!!.contains("siaran terenkripsi"))
        assertNull(listener.onAdvertisement("AA", frame))
        assertTrue(listener.ready)
        assertTrue(listener.encrypted)
        assertNull(session.result)
    }

    private fun listener(
        session: MeasurementSession,
        store: MeasurementRepository? = null,
        mac: String = "",
    ): SessionListener {
        val close = CloseMeasurementUseCase(repository = store)
        val transmissions = ScaleTransmissionRepositoryImpl(DataModule.scaleHeaders())
        val gatt = ScaleGattRepositoryImpl(GattParser())
        return SessionListener(
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

private class MemoryMeasurementRepository : MeasurementRepository {
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

private fun ByteArray.hex(): String {
    return joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
