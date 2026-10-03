package com.mascill.healthtrack.domain.model

import com.mascill.healthtrack.data.di.DataModule
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.usecase.AcceptGattUseCase
import com.mascill.healthtrack.domain.usecase.AcceptTransmissionUseCase
import com.mascill.healthtrack.domain.usecase.ApplyBroadcastWeightUseCase
import com.mascill.healthtrack.domain.usecase.CalculateReportUseCase
import com.mascill.healthtrack.domain.usecase.CloseMeasurementUseCase
import com.mascill.healthtrack.domain.usecase.MeasurementStateUseCase
import com.mascill.healthtrack.util.CsBiasV235
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementSessionTest {
    private val body = BodyData(170.0, 30, 1)
    private val transmissions = ScaleTransmissionRepositoryImpl(DataModule.scaleHeaders())
    private val gattFrames = ScaleGattRepositoryImpl(DataModule.gattParser())
    private val closeMeasurement = CloseMeasurementUseCase()
    private val acceptTransmission = AcceptTransmissionUseCase(closeMeasurement)
    private val applyBroadcastWeight = ApplyBroadcastWeightUseCase(closeMeasurement)
    private val acceptGattFrame = AcceptGattUseCase(closeMeasurement)
    private val measurementState = MeasurementStateUseCase()

    @Test
    fun broadcastWeightWhenGattIsMissing() {
        val session = MeasurementSession(body)
        session.acceptBroadcast(broadcastC0(), "AA:BB:CC:DD:EE:FF")
        assertNull(session.result)
        assertTrue(applyBroadcastWeight(session))
        assertEquals(70.5, session.result!!.weightKg, 0.0)
        assertEquals(listOf(500.0), session.result!!.impedanceOhm)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 70.5, 500.0).report,
            session.result!!.report,
        )
        session.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertEquals(70.5, session.result!!.weightKg, 0.0)
    }

    @Test
    fun gattWeightStaysWhenItArrivedFirst() {
        val session = MeasurementSession(body)
        session.acceptBroadcast(broadcastC0(), "AA")
        session.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertTrue(applyBroadcastWeight(session))
        assertEquals(65.5, session.result!!.weightKg, 0.0)
    }

    @Test
    fun gattWeightAndBroadcastImpedance() {
        val session = MeasurementSession(body)
        session.acceptBroadcast(broadcastC0(), "AA:BB:CC:DD:EE:FF")
        val parsed = session.acceptGatt(v17(1, 0x02 to 0x8F, prop())) as ScaleGatt
        val report = CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0)
        assertEquals(65.5, parsed.weightKg, 0.0)
        assertEquals("selesai", measurementState(session))
        val result = session.result!!
        assertEquals(65.5, result.weightKg, 0.0)
        assertEquals("65.5", result.scaleWeight)
        assertEquals(listOf(500.0), result.impedanceOhm)
        assertEquals("AA:BB:CC:DD:EE:FF", result.mac)
        assertEquals(1, result.productId)
        assertEquals("v235", result.formula)
        assertEquals(report.report, result.report)
        assertEquals(70.5f, session.transmission!!.weightKg, 0f)
    }

    @Test
    fun gattBeforeBroadcast() {
        val session = MeasurementSession(body)
        val waiting = session.acceptGatt(v17(0, 0x02 to 0x8F, prop())) as ScaleGatt
        assertEquals(0, waiting.lockFlag)
        assertNull(session.weightKg)
        assertEquals("menunggu impedansi", measurementState(session))
        session.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertEquals("menunggu impedansi", measurementState(session))
        session.acceptBroadcast(broadcastC0(), "AA")
        assertEquals(65.5, session.result!!.weightKg, 0.0)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0).report,
            session.result!!.report,
        )
    }

    @Test
    fun packetCompositionDoesNotChangeFat() {
        val first = MeasurementSession(body)
        val second = MeasurementSession(body)
        for ((session, axunge) in listOf(first to 225, second to 400)) {
            session.acceptBroadcast(broadcastC0())
            session.acceptGatt(v16(prop(), 0x02 to 0x8F, axunge))
        }
        assertEquals(first.result!!.report, second.result!!.report)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0).report!!.bfp,
            first.result!!.report.bfp,
            0.0,
        )
    }

    @Test
    fun broadcastWithoutImpedanceKeepsWaiting() {
        val session = MeasurementSession(body)
        val frame = bytes(0xCA, 17, 0, 0x21, 2, 0x02, 0xC1, 0, 0, 1, 0)
        session.acceptBroadcast(frame)
        session.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertNull(session.result)
        assertEquals("menunggu impedansi", measurementState(session))
    }

    @Test
    fun unlockedBroadcastDoesNotReplaceImpedance() {
        val session = MeasurementSession(body)
        session.acceptBroadcast(broadcastC0())
        val unlocked = bytes(0xCA, 0x20, 0, 0, 0, 0, 42, 0, 0x20, 9, 0x02, 0xC1, 0x13, 0x88)
        session.acceptBroadcast(unlocked)
        assertEquals(500.0, session.impedanceOhm)
        session.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertEquals(listOf(500.0), session.result!!.impedanceOhm)
    }

    @Test
    fun rolesAndHistoryAreNotTheSessionWeight() {
        val session = MeasurementSession(body)
        session.acceptBroadcast(broadcastC0())
        val property = prop()
        val roles = session.acceptGatt(
            bytes(0xCA, 17, 13, 20, 0x11, 0x02, 0xC1, property, 0, 0, 0, 1, 0, 0, 0, 2),
        )
        assertTrue(roles is GattConfirmation)
        assertEquals("menunggu berat", measurementState(session))
        assertTrue((session.acceptGatt(history(property)) as ScaleGatt).history)
        assertNull(session.result)
        session.acceptGatt(v17(1, 0x02 to 0x8F, property))
        assertEquals(65.5, session.result!!.weightKg, 0.0)
    }

    @Test
    fun failedFormulaThenTheNextImpedance() {
        val session = MeasurementSession(body)
        session.acceptBroadcast(broadcastC0(0x03 to 0xE8))
        session.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertNull(session.result)
        assertEquals(-6, session.code)
        assertEquals("rumus -6", measurementState(session))
        session.acceptBroadcast(broadcastC0())
        assertEquals(listOf(500.0), session.result!!.impedanceOhm)
        assertEquals(0, session.code)
    }

    @Test
    fun firstSuccessStays() {
        val store = MemoryMeasurementRepository()
        val close = CloseMeasurementUseCase(repository = store)
        val acceptTransmission = AcceptTransmissionUseCase(close)
        val acceptGatt = AcceptGattUseCase(close)
        val session = MeasurementSession(body)
        acceptTransmission(session, transmissions.parseTransmission(broadcastC0(), ""))
        acceptGatt(session, gattFrames.parseGatt(v17(1, 0x02 to 0x8F, prop())))
        acceptGatt(session, gattFrames.parseGatt(v17(1, 0x03 to 0x20, prop())))
        assertEquals(65.5, session.result!!.weightKg, 0.0)
        assertEquals(1, store.saved.size)
        assertEquals(session.result!!.report.bfp, store.saved[0].report.bfp, 0.0)
    }

    private fun MeasurementSession.acceptBroadcast(frame: ByteArray, mac: String = "") {
        acceptTransmission(this, transmissions.parseTransmission(frame, mac))
    }

    private fun MeasurementSession.acceptGatt(frame: ByteArray): GattResult {
        return acceptGattFrame(this, gattFrames.parseGatt(frame))
    }
}

class CalculateReportTest {
    private val body = BodyData(170.0, 30, 1)
    private val calculateReport = CalculateReportUseCase()

    @Test
    fun sexOutsideZeroAndOneRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BodyData(170.0, 30, 2)
        }
    }

    @Test
    fun tenImpedancesStayClosed() {
        assertThrows(IllegalStateException::class.java) {
            calculateReport(body, 70.0, List(10) { 500.0 })
        }
    }

    @Test
    fun otherImpedanceCountsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            calculateReport(body, 70.0, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculateReport(body, 70.0, listOf(1.0, 2.0))
        }
    }
}

private class MemoryMeasurementRepository : MeasurementRepository {
    val saved = mutableListOf<MeasurementResult>()

    override fun save(result: MeasurementResult): Int {
        saved += result
        return saved.size
    }
}

private fun prop(lock: Int = 1): Int = "0000000$lock".toInt(2)

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun v17(command: Int, weight: Pair<Int, Int>, property: Int): ByteArray {
    return bytes(0xCA, 17, 0, command, 0, weight.first, weight.second, 0, 0, 0, 0, property)
}

private fun v16(property: Int, weight: Pair<Int, Int>, axunge: Int): ByteArray {
    return bytes(0xCA, 16, 0, 0, property, weight.first, weight.second, axunge shr 8, axunge and 0xFF)
}

private fun broadcastC0(impedance: Pair<Int, Int> = 0x13 to 0x88): ByteArray {
    return bytes(0xC0, 7, 0x02, 0xC1, impedance.first, impedance.second, 0x00, 0x01, prop())
}

private fun history(property: Int): ByteArray {
    val stamp = 1_700_000_000
    val whenBytes = byteArrayOf(
        (stamp ushr 24 and 0xFF).toByte(),
        (stamp ushr 16 and 0xFF).toByte(),
        (stamp ushr 8 and 0xFF).toByte(),
        (stamp and 0xFF).toByte(),
    )
    val rest = bytes(property, 0, 0, 0, 0, 1, 0x03, 0x20, 0, 225, 2, 0x26, 0, 100, 5, 220, 0, 80, 25)
    val body = whenBytes + rest
    return bytes(0xCA, 17, body.size + 2, 19, 0x11) + body
}
