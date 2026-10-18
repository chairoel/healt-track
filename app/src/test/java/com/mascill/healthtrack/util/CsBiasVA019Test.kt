package com.mascill.healthtrack.util

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsBiasVA019Test {
    @Test
    fun valuesMatchTheLibrary() {
        golden.forEach { (sample, digest, fields) ->
            val execution = CsBiasVA019.execute(
                sample.sex,
                sample.age,
                sample.height10,
                sample.weight10,
                sample.mode,
                sample.impedance,
            )
            assertEquals(sample.toString(), 0, execution.code)
            assertEquals(sample.toString(), digest, sha256(execution.raw))
            val result = CsBiasVA019.csBiasVA019(
                sample.sex,
                sample.age,
                sample.height10,
                sample.weight10,
                sample.mode,
                sample.impedance,
            )
            assertEquals(sample.toString(), 0, result.result)
            val got = values(result.data!!)
            assertEquals(fields.size, got.size)
            fields.indices.forEach { index ->
                assertEquals("$sample[$index]", fields[index], got[index], 0.0)
            }
        }
    }

    @Test
    fun errorCodesStayInOrder() {
        assertEquals(1, CsBiasVA019.csBiasVA019(1, 30, 1700, 700, 0, null).result)
        assertNull(CsBiasVA019.csBiasVA019(1, 30, 1700, 700, 0, null).data)
        assertEquals(2, CsBiasVA019.csBiasVA019(1, 30, 1700, 199, 0, z).result)
        assertEquals(2, CsBiasVA019.csBiasVA019(1, 30, 899, 199, 0, z).result)
        assertEquals(3, CsBiasVA019.csBiasVA019(1, 30, 899, 700, 0, z).result)
        assertEquals(3, CsBiasVA019.csBiasVA019(1, 30, 2201, 700, 0, z).result)
        assertEquals(4, CsBiasVA019.csBiasVA019(1, 5, 1700, 700, 0, z).result)
        assertEquals(4, CsBiasVA019.csBiasVA019(1, 100, 1700, 700, 0, z).result)
        assertEquals(5, CsBiasVA019.csBiasVA019(2, 30, 1700, 700, 0, z).result)
        assertEquals(6, CsBiasVA019.csBiasVA019(1, 30, 1700, 700, 0, bad).result)
        assertNull(CsBiasVA019.csBiasVA019(1, 30, 1700, 199, 0, z).data)
    }

    @Test
    fun modeZeroRegression() {
        regression.forEach { (index, expected) ->
            val got = CsBiasVA019.metricRegression(index, seed, 1, 1700, 700, z)
            assertEquals(index.toString(), expected, got, 0.0)
        }
    }

    @Test
    fun javaReportNarrowsFloat() {
        val report = CsBiasVA019.parseMultiFrequency(
            1,
            30,
            170.06,
            70.04,
            z.map { it / 10.0 },
        )
        assertEquals(0, report.result)
        val data = report.data!!
        assertEquals(40.79999923706055, data.bfp, 0.0)
        assertEquals(1266, data.bmr)
        assertEquals(13.0, data.vfl, 0.0)
        assertEquals(23.1, data.sfm, 0.0)
        assertEquals(60, data.sbc)
        assertEquals(63.099998474121094, data.swt, 0.0)
    }
}

private data class Sample(
    val sex: Int,
    val age: Int,
    val height10: Int,
    val weight10: Int,
    val mode: Int,
    val impedance: List<Int>,
)

private fun values(data: BiasVA019Data): List<Double> {
    return listOf(
        data.bfp,
        data.slm,
        data.bmi,
        data.standardWeight,
        data.bmr.toDouble(),
        data.vfl.toDouble(),
        data.metabolicAge.toDouble(),
        data.bodyScore.toDouble(),
        data.laSlm,
        data.trBfm,
        data.sfm,
        data.obesityDegree,
    )
}

private fun sha256(raw: ByteArray): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(raw)
    return hash.joinToString("") { byte -> "%02x".format(byte) }
}

private val z = listOf(5000, 4800, 300, 4500, 4600, 5100, 4900, 280, 4400, 4700)
private val edge = listOf(1000, 7500, 100, 1000, 7500, 7500, 1000, 650, 7500, 1000)
private val bad = listOf(999, 4800, 300, 4500, 4600, 5100, 4900, 280, 4400, 4700)
private val zeros = List(10) { 0 }

private const val seed = 24.221453287197235

private val regression = listOf(
    0 to 40.827466294992256,
    2 to 31.961128943334767,
    9 to 1.9140253167195729,
    19 to 17.409626510834112,
)

private val golden = listOf(
    Triple(
        Sample(1, 30, 1700, 700, 0, z),
        "51ab4151c9678d97e69aaf5b41fc5f18b678018ec564634256a8a2bc428ab952",
        listOf(40.8, 38.9, 24.2, 63.0, 1265.0, 13.0, 36.0, 60.0, 2.0, 14.0, 23.1, 11.1),
    ),
    Triple(
        Sample(0, 30, 1700, 700, 0, z),
        "02114f46b0f578cc35efbf15a9e824fd984545d01a35b1cd046dbe448e47d32e",
        listOf(44.6, 36.2, 24.2, 60.0, 1207.0, 14.0, 35.0, 61.0, 2.0, 15.2, 25.6, 16.7),
    ),
    Triple(
        Sample(1, 6, 1200, 400, 0, z),
        "d2223a0b6f359561c2348800d4d1cf784aa3f21bfa78cd496bdf83ac7727b76d",
        listOf(52.2, 18.1, 27.8, 30.6, 783.0, 15.0, 6.0, 70.0, 0.8, 9.8, 16.9, 30.7),
    ),
    Triple(
        Sample(1, 99, 1500, 600, 0, z),
        "8289f59325cb14b1dd7926050d875bc1d71d607da7349223b0a79bdcc12226ee",
        listOf(48.3, 29.1, 26.7, 49.0, 1040.0, 14.0, 100.0, 60.0, 1.5, 15.1, 23.2, 22.4),
    ),
    Triple(
        Sample(1, 30, 1700, 200, 0, z),
        "b7ce80157aea2a23f933c8425182b0bf3badd5fce5a4052f49133e229676255f",
        listOf(3.0, 17.5, 6.9, 63.0, 789.0, 1.0, 36.0, 60.0, 1.0, 0.2, 0.6, 0.4),
    ),
    Triple(
        Sample(1, 40, 1750, 1501, 0, z),
        "a190f25f9e03ee0323e53cd998e39829fff3399e7e64a30d9d4e6283f72b8fec",
        listOf(55.0, 63.9, 49.0, 66.5, 1829.0, 38.0, 46.0, 60.0, 3.5, 43.8, 66.0, 125.7),
    ),
    Triple(
        Sample(0, 40, 2200, 900, 0, z),
        "2f803dd48062db052af6d38bfa5c54757ade36710bc1668f3f25d813ab46869b",
        listOf(24.1, 64.3, 18.6, 102.9, 1845.0, 9.0, 40.0, 73.0, 3.3, 10.2, 18.7, 0.4),
    ),
    Triple(
        Sample(0, 25, 1650, 620, 0, edge),
        "f8158425c13a8141df1f42089d969aaaf214eaf99f13ef6a46d5f8b4bd555f1e",
        listOf(34.1, 38.1, 22.8, 57.0, 1252.0, 4.0, 24.0, 74.0, 2.2, 10.0, 19.0, 8.8),
    ),
    Triple(
        Sample(1, 30, 1700, 700, 1, z),
        "d1b0d94d68e4f6ac3a7316cb6e04cfa3e1c1f10ca6f777970503a5c7b7b7d8db",
        listOf(0.0, 0.0, 24.2, 63.0, 0.0, 0.0, 18.0, 60.0, 0.0, 0.0, 0.0, 11.1),
    ),
    Triple(
        Sample(1, 30, 1700, 700, 1, zeros),
        "d1b0d94d68e4f6ac3a7316cb6e04cfa3e1c1f10ca6f777970503a5c7b7b7d8db",
        listOf(0.0, 0.0, 24.2, 63.0, 0.0, 0.0, 18.0, 60.0, 0.0, 0.0, 0.0, 11.1),
    ),
    Triple(
        Sample(1, 30, 1700, 700, 1, bad),
        "d1b0d94d68e4f6ac3a7316cb6e04cfa3e1c1f10ca6f777970503a5c7b7b7d8db",
        listOf(0.0, 0.0, 24.2, 63.0, 0.0, 0.0, 18.0, 60.0, 0.0, 0.0, 0.0, 11.1),
    ),
)
