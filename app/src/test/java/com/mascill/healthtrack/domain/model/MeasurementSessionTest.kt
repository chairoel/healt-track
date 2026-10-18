package com.mascill.healthtrack.domain.model

import com.mascill.healthtrack.data.di.DataModule
import com.mascill.healthtrack.data.repository.ScaleGattRepositoryImpl
import com.mascill.healthtrack.data.repository.ScaleTransmissionRepositoryImpl
import com.mascill.healthtrack.domain.model.GattResult.Confirmation
import com.mascill.healthtrack.domain.model.GattResult.ScaleGatt
import com.mascill.healthtrack.domain.repository.MeasurementRepository
import com.mascill.healthtrack.domain.usecase.AcceptGattUseCase
import com.mascill.healthtrack.domain.usecase.AcceptTransmissionUseCase
import com.mascill.healthtrack.domain.usecase.ApplyBroadcastWeightUseCase
import com.mascill.healthtrack.domain.usecase.CalculateReportUseCase
import com.mascill.healthtrack.domain.usecase.CloseMeasurementUseCase
import com.mascill.healthtrack.domain.usecase.MeasurementStateUseCase
import com.mascill.healthtrack.util.CsBiasV235
import com.mascill.healthtrack.util.CsBiasVA019
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun broadcastWeightWhenGattIsMissing() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = ctx.acceptBroadcast(broadcastC0(), "AA:BB:CC:DD:EE:FF")
        assertNull(ctx.session.result)
        ctx = applyBroadcastWeight(ctx)
        assertEquals(70.5, ctx.session.result!!.weightKg, 0.0)
        assertEquals(listOf(500.0), ctx.session.result!!.impedanceOhm)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 70.5, 500.0).report,
            ctx.session.result!!.report,
        )
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertEquals(70.5, ctx.session.result!!.weightKg, 0.0)
    }

    @Test
    fun gattWeightStaysWhenItArrivedFirst() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = ctx.acceptBroadcast(broadcastC0(), "AA")
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        ctx = applyBroadcastWeight(ctx)
        assertEquals(65.5, ctx.session.result!!.weightKg, 0.0)
    }

    @Test
    fun gattWeightAndBroadcastImpedance() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = ctx.acceptBroadcast(broadcastC0(), "AA:BB:CC:DD:EE:FF")
        val gatt = v17(1, 0x02 to 0x8F, prop())
        val parsed = gattFrames.parseGatt(gatt) as ScaleGatt
        ctx = ctx.acceptGatt(gatt)
        val report = CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0)
        assertEquals(65.5, parsed.weightKg, 0.0)
        assertEquals("selesai", measurementState(ctx))
        val result = ctx.session.result!!
        assertEquals(65.5, result.weightKg, 0.0)
        assertEquals("65.5", result.scaleWeight)
        assertEquals(listOf(500.0), result.impedanceOhm)
        assertEquals("AA:BB:CC:DD:EE:FF", result.mac)
        assertEquals(1, result.productId)
        assertEquals("v235", result.formula)
        assertEquals(report.report, result.report)
        assertEquals(70.5f, ctx.session.transmission!!.weightKg, 0f)
    }

    @Test
    fun gattBeforeBroadcast() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        val unlocked = v17(0, 0x02 to 0x8F, prop())
        val waiting = gattFrames.parseGatt(unlocked) as ScaleGatt
        assertEquals(0, waiting.lockFlag)
        ctx = ctx.acceptGatt(unlocked)
        assertNull(ctx.session.weightKg)
        assertEquals("menunggu impedansi", measurementState(ctx))
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertEquals("menunggu impedansi", measurementState(ctx))
        ctx = ctx.acceptBroadcast(broadcastC0(), "AA")
        assertEquals(65.5, ctx.session.result!!.weightKg, 0.0)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0).report,
            ctx.session.result!!.report,
        )
    }

    @Test
    fun packetCompositionDoesNotChangeFat() = runTest {
        var first = MeasurementContext(MeasurementSession(body))
        var second = MeasurementContext(MeasurementSession(body))
        first = first
            .acceptBroadcast(broadcastC0())
            .acceptGatt(v16(prop(), 0x02 to 0x8F, 225))
        second = second
            .acceptBroadcast(broadcastC0())
            .acceptGatt(v16(prop(), 0x02 to 0x8F, 400))
        assertEquals(first.session.result!!.report, second.session.result!!.report)
        assertEquals(
            CsBiasV235.parseDefaultZ(1, 30, 170.0, 65.5, 500.0).report!!.bfp,
            first.session.result!!.report.bfp,
            0.0,
        )
    }

    @Test
    fun broadcastWithoutImpedanceKeepsWaiting() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        val frame = bytes(0xCA, 17, 0, 0x21, 2, 0x02, 0xC1, 0, 0, 1, 0)
        ctx = ctx.acceptBroadcast(frame)
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertNull(ctx.session.result)
        assertEquals("menunggu impedansi", measurementState(ctx))
    }

    @Test
    fun unlockedBroadcastDoesNotReplaceImpedance() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = ctx.acceptBroadcast(broadcastC0())
        val unlocked = bytes(0xCA, 0x20, 0, 0, 0, 0, 42, 0, 0x20, 9, 0x02, 0xC1, 0x13, 0x88)
        ctx = ctx.acceptBroadcast(unlocked)
        assertEquals(500.0, ctx.session.impedanceOhm)
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertEquals(listOf(500.0), ctx.session.result!!.impedanceOhm)
    }

    @Test
    fun rolesAndHistoryAreNotTheSessionWeight() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = ctx.acceptBroadcast(broadcastC0())
        val property = prop()
        val roleBytes = bytes(0xCA, 17, 13, 20, 0x11, 0x02, 0xC1, property, 0, 0, 0, 1, 0, 0, 0, 2)
        val roles = gattFrames.parseGatt(roleBytes)
        ctx = ctx.acceptGatt(roleBytes)
        assertTrue(roles is Confirmation)
        assertEquals("menunggu berat", measurementState(ctx))
        val historyFrame = history(property)
        assertTrue((gattFrames.parseGatt(historyFrame) as ScaleGatt).history)
        ctx = ctx.acceptGatt(historyFrame)
        assertNull(ctx.session.result)
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, property))
        assertEquals(65.5, ctx.session.result!!.weightKg, 0.0)
    }

    @Test
    fun failedFormulaThenTheNextImpedance() = runTest {
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = ctx.acceptBroadcast(broadcastC0(0x03 to 0xE8))
        ctx = ctx.acceptGatt(v17(1, 0x02 to 0x8F, prop()))
        assertNull(ctx.session.result)
        assertEquals(-6, ctx.session.code)
        assertEquals("rumus -6", measurementState(ctx))
        ctx = ctx.acceptBroadcast(broadcastC0())
        assertEquals(listOf(500.0), ctx.session.result!!.impedanceOhm)
        assertEquals(0, ctx.session.code)
    }

    @Test
    fun firstSuccessStays() = runTest {
        val store = MemoryMeasurementRepository()
        val close = CloseMeasurementUseCase(repository = store)
        val acceptTransmission = AcceptTransmissionUseCase(close)
        val acceptGatt = AcceptGattUseCase(close)
        var ctx = MeasurementContext(MeasurementSession(body))
        ctx = acceptTransmission(ctx, transmissions.parseTransmission(broadcastC0(), ""))
        ctx = acceptGatt(ctx, gattFrames.parseGatt(v17(1, 0x02 to 0x8F, prop())))
        ctx = acceptGatt(ctx, gattFrames.parseGatt(v17(1, 0x03 to 0x20, prop())))
        assertEquals(65.5, ctx.session.result!!.weightKg, 0.0)
        assertEquals(1, ctx.session.storedId)
        assertEquals(1, store.saved.size)
        assertEquals(ctx.session.result!!.report.bfp, store.saved[0].report.bfp, 0.0)
    }

    private suspend fun MeasurementContext.acceptBroadcast(
        frame: ByteArray,
        mac: String = "",
    ): MeasurementContext {
        return acceptTransmission(this, transmissions.parseTransmission(frame, mac))
    }

    private suspend fun MeasurementContext.acceptGatt(frame: ByteArray): MeasurementContext {
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
    fun tenImpedancesSelectVA019() {
        val ohms = listOf(5000, 4800, 300, 4500, 4600, 5100, 4900, 280, 4400, 4700)
            .map { it / 10.0 }
        val outcome = calculateReport(body, 70.0, ohms)
        val direct = CsBiasVA019.parseMultiFrequency(body.sex, body.age, body.heightCm, 70.0, ohms)
        assertEquals("vA019", outcome.formula)
        assertEquals(0, outcome.code)
        val data = direct.data!!
        assertEquals(
            V235Report(
                bfp = data.bfp,
                slm = data.slm,
                slp = data.slp,
                bwp = data.bwp,
                bmc = data.bmc,
                vfl = data.vfl,
                pp = data.pp,
                smm = data.smm,
                swt = data.swt,
                mc = data.mc,
                wc = data.wc,
                fc = data.fc,
                bmr = data.bmr,
                ma = data.ma,
                sbc = data.sbc,
            ),
            outcome.report,
        )
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

    override suspend fun save(result: MeasurementResult): Int {
        saved += result
        return saved.size
    }

    override suspend fun list(): List<StoredMeasurement> =
        saved.mapIndexed { index, result ->
            StoredMeasurement(
                id = index + 1,
                createdAt = "",
                result = result,
            )
        }.asReversed()
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
